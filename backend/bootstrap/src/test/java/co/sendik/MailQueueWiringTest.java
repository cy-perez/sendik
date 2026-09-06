package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.shared.mail.CloudTasksMailTransport;
import co.sendik.shared.port.out.MailTransport;
import com.google.cloud.tasks.v2.CloudTasksClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Con la cola encendida la aplicacion tiene que arrancar entera, que es lo que no
 * comprobaba nadie.
 *
 * <p>Es el gemelo de {@code StorageProviderWiringTest} para ADR-0031, y falta desde que
 * se escribio la cola. La forma con la que corre {@code dev} -{@code enabled=true}- no
 * se levantaba en ninguna parte: {@code local} y las suites van con la cola apagada, y
 * el ensayo del despliegue tambien. **Los dos fallos del 6 de septiembre de 2026 se
 * descubrieron desplegando**, uno por despliegue: primero el transporte, que tenia dos
 * constructores y ninguno anotado; despues la cadena de seguridad, que pedia el
 * {@code JwtDecoder} por tipo cuando con la cola encendida hay dos.
 *
 * <p>Lo que prueba no es el encolado sino la forma del contexto: que los beans de la
 * cola se creen, que sean ellos los elegidos y que la aplicacion levante con los dos
 * decodificadores dentro.
 *
 * <p>El cliente de Cloud Tasks se sustituye por un doble por el mismo motivo que el de
 * Cloud Storage en su prueba: crear el de verdad resuelve credenciales de Google al
 * construir el bean, y la verificacion no puede depender de que la maquina las tenga.
 */
@SpringBootTest(
        properties = {
            "sendik.mail.queue.enabled=true",
            "sendik.mail.queue.project=sendik-col",
            "sendik.mail.queue.location=us-east1",
            "sendik.mail.queue.queue=correo-transaccional",
            "sendik.mail.queue.handler-url=https://api-dev.example.test/internal/mail/deliveries",
            "sendik.mail.queue.service-account=sendik-cola@sendik-col.iam.gserviceaccount.com"
        })
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class MailQueueWiringTest {

    @MockitoBean
    private CloudTasksClient cloudTasks;

    private final MailTransport transporte;
    private final ApplicationContext contexto;

    MailQueueWiringTest(MailTransport transporte, ApplicationContext contexto) {
        this.transporte = transporte;
        this.contexto = contexto;
    }

    @Test
    void deberia_entregar_el_correo_a_la_cola_y_no_al_proveedor() {
        assertThat(transporte).isInstanceOf(CloudTasksMailTransport.class);
    }

    /**
     * El de la sesion y el de Cloud Tasks conviven, y cada cadena pide el suyo por
     * nombre. Que este contexto haya arrancado ya lo demuestra; nombrarlos deja escrito
     * por que la prueba existe.
     */
    @Test
    void deberia_convivir_con_los_dos_decodificadores_de_token() {
        assertThat(contexto.getBeanNamesForType(JwtDecoder.class))
                .containsExactlyInAnyOrder("jwtDecoder", "decodificadorDeCloudTasks");
    }
}
