package co.sendik.shared.config;

import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * La cola por la que sale el correo transaccional (ADR-0031).
 *
 * <p>Apagada, el correo se entrega en el hilo de la peticion, que es lo que hace falta en
 * local y en las pruebas: ahi el proveedor es la consola y no hay red que esperar.
 * Encendida, el envio se convierte en una tarea que Cloud Tasks devuelve al propio
 * servicio como una peticion HTTP, que es la unica condicion bajo la cual Cloud Run asigna
 * CPU.
 *
 * @param enabled si el correo se encola. En {@code dev} y {@code prod} va encendida; en
 *     {@code local} no, y esa es toda la diferencia entre los dos caminos
 * @param project proyecto de Google Cloud donde vive la cola
 * @param location region de la cola. No tiene por que coincidir con la del servicio, pero
 *     coincide, y separarlas solo anade una latencia que nadie pidio
 * @param queue nombre de la cola
 * @param handlerUrl direccion absoluta del endpoint que recibe la tarea. Absoluta y no
 *     relativa porque quien hace esa peticion es Cloud Tasks desde fuera, no el servicio
 *     desde dentro
 * @param serviceAccount cuenta de servicio con la que Cloud Tasks firma el token OIDC de
 *     la peticion. Es la misma que el endpoint exige al otro lado: si no coinciden, la
 *     tarea se entrega y el endpoint la rechaza
 * @param audience audiencia que se pide en ese token. Vacia significa la propia
 *     {@code handlerUrl}, que es lo que hace Cloud Tasks por omision
 */
@Validated
@ConfigurationProperties(prefix = "sendik.mail.queue")
public record MailQueueProperties(
        boolean enabled,
        @Nullable String project,
        @Nullable String location,
        @Nullable String queue,
        @Nullable URI handlerUrl,
        @Nullable String serviceAccount,
        @Nullable String audience) {

    /**
     * Lo que falte se descubre al arrancar y no al mandar el primer correo.
     *
     * <p>Es la misma decision que toma {@code ResendMailTransport} con su clave: un fallo
     * de configuracion tiene que impedir arrancar, porque descubrirlo cuando alguien se
     * registra significa que ya se perdio ese registro. Es literalmente como se perdio el
     * primero en {@code dev}.
     */
    public MailQueueProperties {
        if (enabled) {
            exigir(project, "sendik.mail.queue.project (GCP_PROJECT_ID)");
            exigir(location, "sendik.mail.queue.location (MAIL_QUEUE_LOCATION)");
            exigir(queue, "sendik.mail.queue.queue (MAIL_QUEUE_NAME)");
            exigir(serviceAccount, "sendik.mail.queue.service-account (MAIL_QUEUE_SERVICE_ACCOUNT)");
            if (handlerUrl == null) {
                throw new IllegalStateException(faltante("sendik.mail.queue.handler-url (MAIL_QUEUE_HANDLER_URL)"));
            }
        }
    }

    private static void exigir(@NotBlank @Nullable String valor, String nombre) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException(faltante(nombre));
        }
    }

    private static String faltante(String nombre) {
        return "La cola de correo esta encendida y falta " + nombre + ". Definelo, o pon "
                + "MAIL_QUEUE_ENABLED=false para entregar en el hilo de la peticion "
                + "(docs/operacion/configuracion.md, ADR-0031).";
    }

    /** La audiencia efectiva: la configurada, o la propia direccion del endpoint. */
    public String audienciaEfectiva() {
        if (audience != null && !audience.isBlank()) {
            return audience;
        }
        return handlerUrl == null ? "" : handlerUrl.toString();
    }
}
