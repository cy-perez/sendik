package co.sendik.identity.client;

import co.sendik.shared.port.out.MailDelivery;
import co.sendik.shared.port.out.MailTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * El transporte de desarrollo: en vez de mandar el correo, lo imprime.
 *
 * <p>Salio de {@code ConsoleMailSender} al implementar ADR-0031, por lo mismo que su
 * gemelo de Resend: lo que se encola es el mensaje ya armado, asi que componer y entregar
 * tienen que ser dos piezas separables.
 *
 * <p><strong>Lo que no se movio es el registro por tipo de correo</strong>, que sigue en
 * {@code ConsoleMailSender}. Ahi es donde se imprime el enlace de verificacion entero, y
 * {@code frontend/e2e-completo/correo-de-consola.ts} lo lee de esa linea para recorrer el
 * registro sin buzon: cambiar ese formato deja ciega la suite de extremo a extremo.
 */
@Component("transporteDirectoDeCorreo")
@ConditionalOnProperty(prefix = "sendik.mail", name = "provider", havingValue = "console")
public class ConsoleMailTransport implements MailTransport, MailDelivery {

    private static final Logger LOG = LoggerFactory.getLogger(ConsoleMailTransport.class);

    /**
     * Imprime el asunto y no el cuerpo: el cuerpo es HTML y llena la consola. Quien prueba
     * un correo en desarrollo necesita saber que salio y para quien.
     */
    @Override
    public void enviar(String destinatario, String asunto, String html) {
        LOG.info("""

                ================ CORREO ({}) =================================================
                Para:   {}
                Asunto: {}
                ===============================================================================
                """, "adaptador de consola", destinatario, asunto);
    }

    /** Imprimir nunca falla, asi que la tarea siempre queda terminada. */
    @Override
    public boolean entregar(String destinatario, String asunto, String html) {
        enviar(destinatario, asunto, html);
        return true;
    }
}
