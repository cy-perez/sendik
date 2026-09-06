package co.sendik.shared.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import org.junit.jupiter.api.Test;

/**
 * Que un fallo de configuracion de la cola impida arrancar, y no se descubra al mandar el
 * primer correo.
 *
 * <p>No es celo: es exactamente como se perdio el primer registro contra {@code dev}. El
 * dominio no estaba verificado en Resend, nada lo dijo al arrancar, y se descubrio cuando
 * una persona real se quedo sin su enlace. Un servicio que arranca sin poder mandar correo
 * miente sobre su estado de salud.
 */
class MailQueuePropertiesTest {

    private static final URI HANDLER = URI.create("https://api-dev.sendik.co/internal/mail/deliveries");
    private static final String CUENTA = "sendik-cola@sendik-col.iam.gserviceaccount.com";

    private static MailQueueProperties completa() {
        return new MailQueueProperties(true, "sendik-col", "us-east1", "correo", HANDLER, CUENTA, null);
    }

    @Test
    void deberia_construirse_cuando_esta_completa() {
        assertThatCode(MailQueuePropertiesTest::completa).doesNotThrowAnyException();
    }

    /** Apagada no se exige nada: es la forma en que arrancan `local` y las pruebas. */
    @Test
    void no_deberia_exigir_nada_cuando_la_cola_esta_apagada() {
        assertThatCode(() -> new MailQueueProperties(false, null, null, null, null, null, null))
                .doesNotThrowAnyException();
    }

    @Test
    void deberia_negarse_a_arrancar_sin_la_cuenta_de_servicio() {
        assertThatThrownBy(() -> new MailQueueProperties(true, "sendik-col", "us-east1", "correo", HANDLER, null, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_QUEUE_SERVICE_ACCOUNT");
    }

    @Test
    void deberia_negarse_a_arrancar_sin_la_direccion_del_endpoint() {
        assertThatThrownBy(() -> new MailQueueProperties(true, "sendik-col", "us-east1", "correo", null, CUENTA, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_QUEUE_HANDLER_URL");
    }

    /** Una variable de entorno sin valor llega como cadena vacia, no como nulo. */
    @Test
    void deberia_tratar_la_cadena_vacia_como_ausente() {
        assertThatThrownBy(() -> new MailQueueProperties(true, "", "us-east1", "correo", HANDLER, CUENTA, null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GCP_PROJECT_ID");
    }

    /**
     * Sin audiencia configurada vale la propia direccion del endpoint, que es lo que Cloud
     * Tasks usa por omision. Si las dos no coinciden, la tarea se entrega y el endpoint la
     * rechaza: un fallo silencioso y caro de diagnosticar.
     */
    @Test
    void deberia_usar_la_direccion_del_endpoint_como_audiencia_por_omision() {
        assertThat(completa().audienciaEfectiva()).isEqualTo(HANDLER.toString());
    }

    @Test
    void deberia_respetar_la_audiencia_explicita() {
        MailQueueProperties propia =
                new MailQueueProperties(true, "sendik-col", "us-east1", "correo", HANDLER, CUENTA, "otra-audiencia");

        assertThat(propia.audienciaEfectiva()).isEqualTo("otra-audiencia");
    }
}
