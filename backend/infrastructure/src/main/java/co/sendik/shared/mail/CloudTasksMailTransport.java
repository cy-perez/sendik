package co.sendik.shared.mail;

import co.sendik.shared.config.MailQueueProperties;
import co.sendik.shared.port.out.MailTransport;
import com.google.cloud.tasks.v2.CloudTasksClient;
import com.google.cloud.tasks.v2.HttpMethod;
import com.google.cloud.tasks.v2.HttpRequest;
import com.google.cloud.tasks.v2.OidcToken;
import com.google.cloud.tasks.v2.QueueName;
import com.google.cloud.tasks.v2.Task;
import com.google.protobuf.ByteString;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Encola el correo en Cloud Tasks en vez de entregarlo aqui mismo (ADR-0031).
 *
 * <p><strong>El problema que resuelve.</strong> El envio ocurria en un hilo propio, de modo
 * que salia despues de responder la peticion. Cloud Run solo asigna CPU mientras se
 * procesa una peticion, asi que en cuanto la respuesta salia el contenedor se congelaba y
 * la llamada al proveedor moria a medias. Con una cola, el envio vuelve a ocurrir
 * **dentro de una peticion** -la que Cloud Tasks hace contra este mismo servicio-, que es
 * la unica condicion bajo la cual hay CPU garantizada.
 *
 * <p><strong>Encolar es sincrono y tiene que serlo.</strong> Es la parte que parece un
 * descuido y no lo es: pasar esta llamada a un hilo aparte para ahorrarle unos
 * milisegundos a la peticion reproduce exactamente el fallo original, porque ese hilo se
 * congela igual. El coste es un residuo de tiempo en el camino en que si hay cuenta, que
 * ADR-0031 acepta por escrito: son decenas de milisegundos frente a los cientos que
 * costaba esperar al proveedor.
 *
 * <p><strong>No implementa {@code MailDelivery}, y es a proposito.</strong> Quien recibe la
 * tarea pide ese otro puerto, que solo implementan los transportes directos. Asi el bucle
 * -encolar una tarea que al entregarse vuelve a encolar- no es que este prohibido: es que
 * no se puede escribir.
 *
 * <p>Como {@link MailTransport} promete no lanzar, un fallo al encolar se registra y se
 * traga. Es la unica ventana en la que un correo se pierde sin remedio, y es mucho mas
 * estrecha que la anterior: antes se perdia cada vez que el contenedor se congelaba, o
 * sea casi siempre.
 */
@Primary
@Component
@ConditionalOnProperty(prefix = "sendik.mail.queue", name = "enabled", havingValue = "true")
public class CloudTasksMailTransport implements MailTransport {

    private static final Logger LOG = LoggerFactory.getLogger(CloudTasksMailTransport.class);

    private final CloudTasksClient cliente;
    private final MailQueueProperties propiedades;
    private final ObjectMapper json;
    private final QueueName cola;

    /**
     * Un solo constructor, y el cliente entra por parametro.
     *
     * <p>Habia dos -uno que creaba el cliente y otro para las pruebas- y ninguno anotado.
     * Con dos constructores y sin anotacion, Spring no elige: busca el vacio, no lo
     * encuentra y no arranca. Se descubrio el 6 de septiembre de 2026, al encender la cola
     * por primera vez, porque este bean no se construye con la cola apagada y ninguna
     * prueba lo construia por Spring.
     *
     * <p>Quien crea y cierra el cliente es {@link CloudTasksClientConfig}, que es de quien
     * es esa responsabilidad: aqui se usa, no se administra.
     */
    public CloudTasksMailTransport(MailQueueProperties propiedades, ObjectMapper json, CloudTasksClient cliente) {
        this.propiedades = propiedades;
        this.json = json;
        this.cliente = cliente;
        this.cola = QueueName.of(propiedades.project(), propiedades.location(), propiedades.queue());
    }

    @Override
    public void enviar(String destinatario, String asunto, String cuerpoHtml) {
        try {
            cliente.createTask(cola, tarea(destinatario, asunto, cuerpoHtml));
        } catch (RuntimeException e) {
            // Sin el destinatario, sin el asunto y sin el cuerpo: el cuerpo lleva el
            // enlace de verificacion, que es una credencial
            // (docs/operacion/datos-personales.md).
            LOG.error(
                    "No se pudo encolar un correo transaccional: {}. El correo se perdio",
                    e.getClass().getName());
        }
    }

    private Task tarea(String destinatario, String asunto, String cuerpoHtml) {
        // El token OIDC lo firma Cloud Tasks con la cuenta de servicio configurada, y es
        // lo unico que separa este endpoint de cualquiera que descubra su direccion.
        OidcToken token = OidcToken.newBuilder()
                .setServiceAccountEmail(propiedades.serviceAccount())
                .setAudience(propiedades.audienciaEfectiva())
                .build();

        String cuerpo = json.writeValueAsString(new MailTaskPayload(destinatario, asunto, cuerpoHtml));

        HttpRequest peticion = HttpRequest.newBuilder()
                .setUrl(propiedades.handlerUrl().toString())
                .setHttpMethod(HttpMethod.POST)
                .putHeaders("Content-Type", "application/json")
                .setOidcToken(token)
                .setBody(ByteString.copyFrom(cuerpo, StandardCharsets.UTF_8))
                .build();

        return Task.newBuilder().setHttpRequest(peticion).build();
    }
}
