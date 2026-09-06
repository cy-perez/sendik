package co.sendik.shared.rest;

import co.sendik.shared.port.out.MailDelivery;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recibe una tarea de la cola y entrega el correo (ADR-0031).
 *
 * <p>Este endpoint es el motivo de toda la ADR. El envio muere si ocurre fuera de una
 * peticion, porque Cloud Run congela el contenedor en cuanto responde; aqui el envio
 * ocurre **durante** una peticion, la que hace Cloud Tasks, y por tanto con CPU.
 *
 * <p>No lo llama ninguna persona y no cuelga de {@code /api/v1}: no es parte del contrato
 * publico, no aparece en {@code contrato-api.md} y no devuelve {@code ProblemDetail},
 * porque quien lee estas respuestas es un servicio de colas que solo mira el codigo de
 * estado.
 *
 * <p><strong>El codigo de estado es la unica forma de hablar con la cola</strong>, y de ahi
 * la diferencia entre los dos:
 *
 * <ul>
 *   <li>{@code 204} da la tarea por terminada y Cloud Tasks la borra. Se responde tanto si
 *       el correo salio como si el proveedor lo rechazo de forma definitiva: en el segundo
 *       caso reintentar durante horas solo sirve para recibir el mismo no.
 *   <li>{@code 503} deja la tarea viva y Cloud Tasks la reintenta con espera creciente. Se
 *       responde ante un fallo transitorio: un corte de red o un 5xx del proveedor.
 * </ul>
 *
 * <p>Quien decide cual de los dos es {@link MailDelivery}, y ese puerto lo implementan
 * solo los transportes directos. El que encola no lo implementa, asi que este endpoint no
 * puede volver a encolar aunque alguien lo intente: no hay metodo que llamar.
 *
 * <p>La autenticacion no esta aqui sino en {@code InternalSecurityConfig}, que exige el
 * token OIDC de la cuenta de servicio de la cola.
 */
@RestController
@RequestMapping("/internal/mail")
public class MailDeliveryController {

    private static final Logger LOG = LoggerFactory.getLogger(MailDeliveryController.class);

    private final MailDelivery entrega;

    public MailDeliveryController(MailDelivery entrega) {
        this.entrega = entrega;
    }

    @PostMapping("/deliveries")
    public ResponseEntity<Void> entregar(@Valid @RequestBody MailDeliveryRequest peticion) {
        if (entrega.entregar(peticion.destinatario(), peticion.asunto(), peticion.cuerpoHtml())) {
            return ResponseEntity.noContent().build();
        }

        // Ni el destinatario ni el asunto ni el cuerpo: el cuerpo lleva el enlace de
        // verificacion, que es una credencial (docs/operacion/datos-personales.md).
        LOG.warn("Fallo transitorio al entregar un correo de la cola. Se devuelve 503 para que se reintente");
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
    }

    /**
     * El cuerpo de la tarea.
     *
     * <p>Es el gemelo de {@code MailTaskPayload}, que vive en {@code infrastructure} y que
     * este modulo no puede ver. Los tres nombres de campo tienen que coincidir porque entre
     * los dos hay JSON, y eso no lo comprueba el compilador: lo comprueba una prueba.
     */
    public record MailDeliveryRequest(
            @NotBlank String destinatario,
            @NotBlank String asunto,
            @NotBlank String cuerpoHtml) {}
}
