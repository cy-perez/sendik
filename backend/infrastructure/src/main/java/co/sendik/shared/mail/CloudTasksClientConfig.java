package co.sendik.shared.mail;

import com.google.cloud.tasks.v2.CloudTasksClient;
import java.io.IOException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * El cliente de Cloud Tasks, creado y cerrado por Spring.
 *
 * <p>Estaba dentro de {@link CloudTasksMailTransport}, que ofrecia un constructor que lo
 * creaba y otro que lo recibia para las pruebas. Dos constructores sin anotacion es
 * justamente lo que Spring no sabe resolver, y el 6 de septiembre de 2026 el servicio no
 * arranco al encender la cola por primera vez.
 *
 * <p>Separarlo tambien pone el cierre donde corresponde. Antes lo cerraba un
 * {@code @PreDestroy} del transporte, que cerraba algo que no habia creado en el camino
 * de produccion y que en las pruebas no le pertenecia. Como {@code CloudTasksClient} es
 * {@code AutoCloseable}, Spring deduce {@code close()} y lo cierra al apagar el contexto.
 *
 * <p>Solo existe con la cola encendida, igual que el transporte: en {@code local} el bean
 * no se crea y la libreria no se toca.
 */
@Configuration
@ConditionalOnProperty(prefix = "sendik.mail.queue", name = "enabled", havingValue = "true")
public class CloudTasksClientConfig {

    /**
     * Lee las credenciales del entorno. En Cloud Run son las de la cuenta del servicio,
     * que es la que tiene {@code cloudtasks.enqueuer} sobre la cola.
     */
    @Bean
    CloudTasksClient clienteDeCloudTasks() throws IOException {
        return CloudTasksClient.create();
    }
}
