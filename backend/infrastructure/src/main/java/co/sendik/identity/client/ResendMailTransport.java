package co.sendik.identity.client;

import co.sendik.identity.config.MailProperties;
import co.sendik.shared.port.out.MailDelivery;
import co.sendik.shared.port.out.MailTransport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

/**
 * El POST a Resend, y nada mas (ADR-0012).
 *
 * <p>Salio de {@code ResendMailSender} al implementar ADR-0031. Antes componia el HTML y
 * lo mandaba en la misma clase, y eso no dejaba sitio donde meter la cola: lo que se
 * encola es el mensaje **ya armado**, asi que el encolado tiene que poder ponerse entre
 * las dos mitades. Ahora la composicion vive en {@code ResendMailSender} y manda por
 * {@link MailTransport}, que segun la configuracion es este adaptador o el que encola.
 *
 * <p>Con {@code RestClient} y sin el SDK del proveedor: son dos mensajes y un POST, y una
 * dependencia menos es una dependencia menos que actualizar.
 *
 * <p>Implementa los dos puertos de salida de correo y **la diferencia entre ellos es el
 * motivo de que exista {@link MailDelivery}**: {@code enviar} no lanza y no informa, que
 * es lo que necesita quien manda un correo; {@code entregar} devuelve si hay que
 * reintentar, que es lo que necesita quien recibe la tarea de la cola.
 */
@Component("transporteDirectoDeCorreo")
@ConditionalOnProperty(prefix = "sendik.mail", name = "provider", havingValue = "resend", matchIfMissing = true)
public class ResendMailTransport implements MailTransport, MailDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(ResendMailTransport.class);
    private static final Duration TIEMPO_DE_ESPERA = Duration.ofSeconds(10);

    /**
     * Cuantas veces se intenta un envio, contando el primero.
     *
     * <p><strong>Solo se reintenta lo transitorio</strong>: un corte de red o un 5xx del
     * proveedor. Un 4xx no se reintenta nunca, porque significa que el proveedor entendio
     * la peticion y la rechaza -remitente sin verificar, clave sin permiso-, y mandar tres
     * veces lo mismo solo sirve para recibir tres veces el mismo no.
     *
     * <p><strong>Desde ADR-0031 esto ya no es la ultima linea de defensa.</strong> Lo era
     * cuando el envio ocurria en un hilo que Cloud Run congelaba, y ahi no servia de nada:
     * los tres intentos caian en el mismo hilo congelado. Ahora quien reintenta de verdad
     * es la cola, que sobrevive al contenedor. Estos tres se quedan como lo que siempre
     * debieron ser: un atajo ante un hipo de un segundo, para no dar una vuelta entera por
     * la cola cuando no hace falta.
     */
    private static final int INTENTOS = 3;

    /** Se multiplica por el numero de intento: 300 ms, luego 600 ms. */
    private static final Duration ESPERA_ENTRE_INTENTOS = Duration.ofMillis(300);

    private final RestClient cliente;
    private final MailProperties propiedades;

    public ResendMailTransport(MailProperties propiedades) {
        // La clave se exige aqui y no en MailProperties porque aqui es donde se
        // usa: con el proveedor de consola no hace falta ninguna, y validarla
        // para todos obligaba a inventarse una para arrancar en local. Sigue
        // siendo un fallo de arranque, que es lo que importa: este bean se
        // construye antes de que el servidor atienda la primera peticion.
        if (propiedades.providerApiKey() == null || propiedades.providerApiKey().isBlank()) {
            throw new IllegalStateException("Falta MAIL_PROVIDER_API_KEY y el proveedor de correo es Resend. "
                    + "Define la clave, o pon MAIL_PROVIDER=console para imprimir el enlace "
                    + "en el registro en vez de enviarlo (docs/operacion/configuracion.md).");
        }

        this.propiedades = propiedades;

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory();
        fabrica.setReadTimeout(TIEMPO_DE_ESPERA);

        this.cliente = RestClient.builder()
                .baseUrl(propiedades.apiUrl().toString())
                .requestFactory(fabrica)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + propiedades.providerApiKey())
                .build();
    }

    /**
     * El puerto que usa quien manda un correo. No lanza y no informa: un correo que no
     * sale no puede tumbar la operacion que lo provoco.
     */
    @Override
    public void enviar(String destinatario, String asunto, String html) {
        entregar(destinatario, asunto, html);
    }

    /** El que usa el endpoint de la cola, que si necesita saber si merece otro intento. */
    @Override
    public boolean entregar(String destinatario, String asunto, String html) {
        Map<String, Object> peticion =
                Map.of("from", propiedades.from(), "to", List.of(destinatario), "subject", asunto, "html", html);

        for (int intento = 1; intento <= INTENTOS; intento++) {
            try {
                cliente.post()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(peticion)
                        .retrieve()
                        .toBodilessEntity();
                return true;
            } catch (RestClientResponseException e) {
                // Solo el codigo de estado. El mensaje de esta excepcion incluye parte
                // del cuerpo que devolvio el proveedor, y ese cuerpo puede repetir la
                // direccion de destino (docs/operacion/datos-personales.md).
                int estado = e.getStatusCode().value();

                if (!e.getStatusCode().is5xxServerError()) {
                    // 4xx: el proveedor entendio la peticion y la rechaza. Reintentar
                    // manda tres veces lo mismo para recibir tres veces el mismo no, y
                    // que lo reintente la cola durante horas es todavia peor.
                    LOG.error(
                            "El proveedor rechazo un correo transaccional con estado {}."
                                    + " Es configuracion, no una caida: revisa el remitente,"
                                    + " la clave y la verificacion del dominio"
                                    + " (docs/operacion/entornos.md)",
                            estado);
                    return true;
                }
                if (!esperarAntesDeReintentar(intento, "estado " + estado)) {
                    return false;
                }
            } catch (ResourceAccessException e) {
                // Ni siquiera hubo respuesta: se agoto la espera, o la conexion no se
                // pudo abrir. Es justo lo que un reintento arregla.
                if (!esperarAntesDeReintentar(intento, e.getClass().getSimpleName())) {
                    return false;
                }
            } catch (RuntimeException e) {
                // Sin el asunto, sin el cuerpo y sin el mensaje: el registro no debe
                // llevar el enlace de verificacion, que es una credencial, ni la
                // direccion de nadie.
                LOG.error(
                        "No se pudo enviar un correo transaccional: {}",
                        e.getClass().getName());
                return false;
            }
        }
        return false;
    }

    /**
     * Espera antes del siguiente intento, o se rinde si ya no quedan.
     *
     * @return {@code true} si hay que volver a intentarlo
     */
    private static boolean esperarAntesDeReintentar(int intento, String causa) {
        if (intento == INTENTOS) {
            LOG.error(
                    "No se pudo entregar un correo transaccional tras {} intentos seguidos."
                            + " Ultima causa: {}. Si venia de la cola, se reintentara mas tarde",
                    INTENTOS,
                    causa);
            return false;
        }

        LOG.warn("Fallo transitorio al enviar un correo ({}). Reintento {} de {}", causa, intento + 1, INTENTOS);

        try {
            Thread.sleep(ESPERA_ENTRE_INTENTOS.multipliedBy(intento));
        } catch (InterruptedException e) {
            // Alguien esta apagando el servicio. Se restaura la marca y se deja de
            // insistir: un correo no vale retrasar un apagado.
            Thread.currentThread().interrupt();
            return false;
        }
        return true;
    }
}
