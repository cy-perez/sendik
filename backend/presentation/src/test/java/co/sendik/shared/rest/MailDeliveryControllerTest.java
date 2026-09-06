package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.shared.port.out.MailDelivery;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Lo unico que este endpoint le puede decir a la cola es un codigo de estado, asi que eso
 * es lo que se prueba (ADR-0031).
 *
 * <p>Confundir los dos codigos no rompe ninguna prueba de envio y sin embargo desarma la
 * decision entera: con un 204 ante un fallo transitorio, Cloud Tasks da la tarea por
 * entregada y **no reintenta**, que es justo lo que se compro al elegirlo; con un 503 ante
 * un rechazo definitivo, la reintenta durante horas para recibir el mismo no.
 *
 * <p>La autenticacion no se prueba aqui porque no vive aqui: la exige
 * {@link InternalSecurityConfig} con el token OIDC de la cuenta de servicio.
 */
class MailDeliveryControllerTest {

    /** El JSON exacto que escribe {@code CloudTasksMailTransport}. Ver {@code MailTaskPayloadTest}. */
    private static final String TAREA = """
            {"destinatario":"ana@correo.co","asunto":"Confirma tu correo","cuerpoHtml":"<p>Hola</p>"}
            """;

    private final List<String> entregados = new ArrayList<>();

    private MockMvc conEntrega(boolean resultado) {
        MailDelivery entrega = (destinatario, asunto, cuerpo) -> {
            entregados.add(destinatario);
            return resultado;
        };
        return MockMvcBuilders.standaloneSetup(new MailDeliveryController(entrega))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void deberia_dar_la_tarea_por_terminada_cuando_el_correo_sale() throws Exception {
        conEntrega(true)
                .perform(post("/internal/mail/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TAREA))
                .andExpect(status().isNoContent());

        assertThat(entregados).containsExactly("ana@correo.co");
    }

    /** 503 y no 204: es lo que hace que la cola vuelva a intentarlo mas tarde. */
    @Test
    void deberia_pedir_otro_intento_cuando_el_fallo_es_transitorio() throws Exception {
        conEntrega(false)
                .perform(post("/internal/mail/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(TAREA))
                .andExpect(status().isServiceUnavailable());
    }

    /**
     * Una tarea sin destinatario no se puede entregar nunca, asi que se rechaza al entrar y
     * no se intenta. Que salga 4xx tambien importa: la cola no reintenta lo que no tiene
     * arreglo.
     */
    @Test
    void deberia_rechazar_una_tarea_incompleta_sin_intentar_entregarla() throws Exception {
        conEntrega(true)
                .perform(post("/internal/mail/deliveries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"destinatario\":\"\",\"asunto\":\"Hola\",\"cuerpoHtml\":\"<p>x</p>\"}"))
                .andExpect(status().is4xxClientError());

        assertThat(entregados).isEmpty();
    }
}
