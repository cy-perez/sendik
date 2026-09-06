package co.sendik.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import co.sendik.shared.config.MailQueueProperties;
import co.sendik.shared.port.out.MailTransport;
import com.google.cloud.tasks.v2.CloudTasksClient;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.ObjectMapper;

/**
 * Que Spring sepa construir el transporte, que es lo que no comprobaba nadie.
 *
 * <p>El bean solo existe con la cola encendida, y la cola solo se enciende en Cloud Run:
 * ni el perfil {@code local}, ni las suites, ni el ensayo del despliegue lo construian.
 * Asi, la clase tenia dos constructores y ninguno anotado -que es lo unico que Spring no
 * sabe resolver- y eso no se descubrio hasta que la revision no arranco en {@code dev}.
 *
 * <p>No prueba el encolado: prueba el cableado. El cliente es un doble porque crear uno
 * de verdad pide credenciales de Google, y lo que aqui importa es si el contexto levanta.
 */
class CloudTasksMailTransportWiringTest {

    private static final MailQueueProperties PROPIEDADES = new MailQueueProperties(
            true,
            "sendik-col",
            "us-east1",
            "correo-transaccional",
            URI.create("https://api-dev.example.test/internal/mail/deliveries"),
            "sendik-cola@sendik-col.iam.gserviceaccount.com",
            null);

    private final ApplicationContextRunner contexto = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(CloudTasksClient.class, () -> mock(CloudTasksClient.class))
            .withBean(MailQueueProperties.class, () -> PROPIEDADES)
            .withUserConfiguration(CloudTasksMailTransport.class);

    @Test
    void deberia_construir_el_transporte_cuando_la_cola_esta_encendida() {
        contexto.withPropertyValues("sendik.mail.queue.enabled=true").run(aplicacion -> {
            assertThat(aplicacion).hasNotFailed();
            assertThat(aplicacion).hasSingleBean(CloudTasksMailTransport.class);
            assertThat(aplicacion.getBean(CloudTasksMailTransport.class)).isInstanceOf(MailTransport.class);
        });
    }

    @Test
    void deberia_omitir_el_transporte_cuando_la_cola_esta_apagada() {
        contexto.withPropertyValues("sendik.mail.queue.enabled=false").run(aplicacion -> {
            assertThat(aplicacion).hasNotFailed();
            assertThat(aplicacion).doesNotHaveBean(CloudTasksMailTransport.class);
        });
    }
}
