package co.sendik.identity.client;

import co.sendik.identity.config.VerificationProperties;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.MailSender;
import co.sendik.shared.config.AppProperties;
import co.sendik.shared.port.out.MailTransport;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Arma los correos de {@code identity} y los manda por el transporte (ADR-0012, ADR-0023).
 *
 * <p><strong>Solo compone.</strong> El POST a Resend vivia aqui hasta ADR-0031 y ahora
 * esta en {@code ResendMailTransport}. Se separaron porque lo que se encola es el mensaje
 * ya armado -destinatario, asunto y cuerpo, que es justo la forma de {@link MailTransport}-,
 * y para poder encolar hace falta un sitio entre componer y entregar donde ponerse.
 *
 * <p>Consecuencia practica: esta clase ya no sabe si el correo sale ahora mismo o entra en
 * una cola, y no deberia saberlo. Lo decide la configuracion, eligiendo que adaptador
 * ocupa {@link MailTransport}.
 *
 * <p><strong>Ningun metodo lanza.</strong> Un correo que no sale no debe impedir crear la
 * cuenta: la persona siempre puede pedir el reenvio, y perder el registro entero por una
 * caida del proveedor es peor que llegar tarde.
 */
// Uno solo de los dos compositores esta activo, igual que antes: este arma HTML y el de
// consola imprime. Lo que cambio en ADR-0031 es que ninguno de los dos entrega.
@Component
@ConditionalOnProperty(prefix = "sendik.mail", name = "provider", havingValue = "resend", matchIfMissing = true)
public class ResendMailSender implements MailSender {

    /**
     * Solo horas y minutos, sin nombre de zona ni formato regional: "15:42" se
     * entiende igual en los dos idiomas y no depende de la configuracion regional
     * del servidor.
     */
    private static final DateTimeFormatter HORA = DateTimeFormatter.ofPattern("HH:mm");

    private final VerificationLink enlaces;
    private final VerificationProperties verificacion;
    private final MailTransport transporte;

    /** Para dar la hora de desbloqueo en la zona de operacion y no en UTC. */
    private final ZoneId zona;

    public ResendMailSender(
            VerificationLink enlaces,
            AppProperties app,
            VerificationProperties verificacion,
            MailTransport transporte) {
        this.enlaces = enlaces;
        this.verificacion = verificacion;
        this.transporte = transporte;
        this.zona = app.timeZone();
    }

    @Override
    public void enviarVerificacionDeCorreo(User destinatario, String tokenEnClaro) {
        boolean espanol = destinatario.locale() == UserLocale.ES;
        String enlace = enlaces.para(tokenEnClaro);

        transporte.enviar(
                destinatario.email().value(),
                espanol ? "Confirma tu correo en Sendik" : "Confirm your email on Sendik",
                espanol
                        ? cuerpo(
                                "Confirma tu correo",
                                "Toca el enlace para activar tu cuenta.",
                                enlace,
                                "Confirmar correo")
                        : cuerpo(
                                "Confirm your email",
                                "Tap the link to activate your account.",
                                enlace,
                                "Confirm email"));
    }

    @Override
    public void enviarAvisoDeRegistroConCorreoExistente(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                titular.email().value(),
                espanol ? "Alguien intentó registrarse con tu correo" : "Someone tried to register with your email",
                espanol
                        ? "<p>Alguien intentó crear una cuenta en Sendik con tu correo. "
                                + "No se creó ninguna cuenta nueva y tu sesión no cambió. "
                                + "Si fuiste tú, ya tienes cuenta: entra con tu contraseña.</p>"
                        : "<p>Someone tried to create a Sendik account with your email. "
                                + "No new account was created and your session did not change. "
                                + "If it was you, you already have an account: sign in instead.</p>");
    }

    @Override
    public void enviarAvisoDeCuentaBloqueada(User titular, Instant desbloqueoEn) {
        boolean espanol = titular.locale() == UserLocale.ES;
        String hora = HORA.format(desbloqueoEn.atZone(zona));

        transporte.enviar(
                titular.email().value(),
                espanol ? "Bloqueamos el acceso a tu cuenta" : "We locked access to your account",
                espanol
                        ? "<p>Hubo varios intentos fallidos de entrar a tu cuenta, así que bloqueamos "
                                + "el acceso por seguridad. Puedes volver a intentarlo a partir de las " + hora
                                + ".</p><p>Si no fuiste tú, tu contraseña sigue siendo la misma y nadie entró. "
                                + "Cuando puedas, cámbiala.</p>"
                        : "<p>There were several failed attempts to sign in to your account, so we locked "
                                + "access for safety. You can try again after " + hora
                                + ".</p><p>If this was not you, your password has not changed and nobody got in. "
                                + "Change it when you can.</p>");
    }

    @Override
    public void enviarAvisoDeSesionRevocadaPorSeguridad(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                titular.email().value(),
                espanol ? "Cerramos tus sesiones por seguridad" : "We closed your sessions for safety",
                espanol
                        ? "<p>Detectamos que se reutilizó una credencial de sesión antigua, que es señal "
                                + "de que alguien pudo haberla copiado. Cerramos esa sesión completa.</p>"
                                + "<p>Entra de nuevo con tu contraseña. Si no reconoces esto, cámbiala.</p>"
                        : "<p>We detected an old session credential being reused, which can mean someone "
                                + "copied it. We closed that whole session.</p>"
                                + "<p>Sign in again with your password. If this looks wrong, change it.</p>");
    }

    /** Criterio 18: el enlace dura 30 minutos y se dice en el mensaje. */
    @Override
    public void enviarRestablecimientoDeContrasena(User destinatario, String tokenEnClaro) {
        boolean espanol = destinatario.locale() == UserLocale.ES;
        String enlace = enlaces.paraRestablecer(tokenEnClaro);

        transporte.enviar(
                destinatario.email().value(),
                espanol ? "Restablece tu contraseña en Sendik" : "Reset your Sendik password",
                espanol
                        ? cuerpo(
                                "Restablece tu contraseña",
                                "Pediste cambiar tu contraseña. El enlace sirve una sola vez y vence en 30 "
                                        + "minutos. Si no fuiste tú, ignora este mensaje: tu contraseña no cambia.",
                                enlace,
                                "Poner una contraseña nueva")
                        : cuerpo(
                                "Reset your password",
                                "You asked to change your password. The link works once and expires in 30 "
                                        + "minutes. If this was not you, ignore this message: your password stays "
                                        + "the same.",
                                enlace,
                                "Set a new password"));
    }

    /**
     * Criterio 20. Sin enlace y sin boton a proposito: es un aviso, y un correo de
     * "tu contrasena cambio" con un enlace dentro es exactamente la forma del
     * fraude que la persona deberia aprender a desconfiar.
     */
    @Override
    public void enviarAvisoDeContrasenaCambiada(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                titular.email().value(),
                espanol ? "Tu contraseña cambió" : "Your password changed",
                espanol
                        ? "<p>Tu contraseña de Sendik acaba de cambiar y cerramos todas las sesiones "
                                + "abiertas. Entra de nuevo con la contraseña nueva.</p>"
                                + "<p>Si no fuiste tú, alguien tiene acceso a este correo. Escríbenos de "
                                + "inmediato desde la página de contacto.</p>"
                        : "<p>Your Sendik password has just changed and we closed every open session. "
                                + "Sign in again with the new password.</p>"
                                + "<p>If this was not you, someone has access to this mailbox. Contact us "
                                + "right away from the contact page.</p>");
    }

    /** Criterio 23. Sin enlace: es el ultimo mensaje y no hay nada que abrir. */
    @Override
    public void enviarAvisoDeCuentaCerrada(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                titular.email().value(),
                espanol ? "Tu cuenta de Sendik quedó cerrada" : "Your Sendik account is closed",
                espanol
                        ? "<p>Cerramos tu cuenta y borramos los datos que te identificaban. "
                                + "Este es el último mensaje que te enviamos.</p>"
                                + "<p>Si quieres volver, puedes registrarte de nuevo con este mismo correo.</p>"
                                + "<p>Si no fuiste tú quien lo pidió, escríbenos de inmediato.</p>"
                        : "<p>We closed your account and deleted the data that identified you. "
                                + "This is the last message we will send you.</p>"
                                + "<p>If you want to come back, you can register again with this same address.</p>"
                                + "<p>If you did not ask for this, contact us right away.</p>");
    }

    // --- Verificacion de vendedor. HU-002 criterio 10 ------------------------

    @Override
    public void enviarAvisoDeVerificacionRecibida(User titular) {
        transporte.enviar(
                titular.email().value(),
                VerificationMailTexts.asuntoDeRecibida(titular.locale()),
                VerificationMailTexts.cuerpoDeRecibida(titular.locale(), verificacion.reviewDays()));
    }

    @Override
    public void enviarAvisoDeVerificacionAprobada(User titular) {
        transporte.enviar(
                titular.email().value(),
                VerificationMailTexts.asuntoDeAprobada(titular.locale()),
                VerificationMailTexts.cuerpoDeAprobada(titular.locale()));
    }

    @Override
    public void enviarAvisoDeVerificacionRechazada(
            User titular, RejectionReason motivo, String nota, int intentosRestantes) {
        transporte.enviar(
                titular.email().value(),
                VerificationMailTexts.asuntoDeRechazada(titular.locale()),
                VerificationMailTexts.cuerpoDeRechazada(titular.locale(), motivo, nota, intentosRestantes));
    }

    @Override
    public void enviarAvisoDeVerificacionRevocada(User titular, RevocationReason motivo, String nota) {
        transporte.enviar(
                titular.email().value(),
                VerificationMailTexts.asuntoDeRevocada(titular.locale()),
                VerificationMailTexts.cuerpoDeRevocada(titular.locale(), motivo, nota));
    }

    /** Criterio 21: va a la direccion NUEVA. Solo quien la abra completa el cambio. */
    @Override
    public void enviarConfirmacionDeCorreoNuevo(User titular, Email destino, String tokenEnClaro) {
        boolean espanol = titular.locale() == UserLocale.ES;
        String enlace = enlaces.paraCambioDeCorreo(tokenEnClaro);

        transporte.enviar(
                destino.value(),
                espanol ? "Confirma tu correo nuevo en Sendik" : "Confirm your new Sendik email",
                espanol
                        ? cuerpo(
                                "Confirma tu correo nuevo",
                                "Pediste usar esta dirección en tu cuenta de Sendik. Hasta que abras el "
                                        + "enlace, tu cuenta conserva la anterior.",
                                enlace,
                                "Confirmar este correo")
                        : cuerpo(
                                "Confirm your new email",
                                "You asked to use this address on your Sendik account. Until you open the "
                                        + "link, your account keeps the previous one.",
                                enlace,
                                "Confirm this email"));
    }

    /** Criterio 21: alguien intento mudar una cuenta a un correo que ya tiene otra. */
    @Override
    public void enviarAvisoDeIntentoDeCambioAEsteCorreo(User titular) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                titular.email().value(),
                espanol ? "Alguien intentó usar tu correo" : "Someone tried to use your email",
                espanol
                        ? "<p>Alguien intentó cambiar el correo de otra cuenta de Sendik a esta dirección. "
                                + "No cambiamos nada y tu cuenta sigue igual.</p>"
                                + "<p>Si fuiste tú desde otra cuenta, recuerda que un correo solo puede "
                                + "tener una cuenta.</p>"
                        : "<p>Someone tried to move another Sendik account to this address. We changed "
                                + "nothing and your account is untouched.</p>"
                                + "<p>If that was you from another account, remember one address can only "
                                + "have one account.</p>");
    }

    /** Criterio 21: al correo ANTERIOR, que es quien tiene que enterarse. */
    @Override
    public void enviarAvisoDeCorreoCambiado(User titular, Email anterior) {
        boolean espanol = titular.locale() == UserLocale.ES;

        transporte.enviar(
                anterior.value(),
                espanol ? "El correo de tu cuenta cambió" : "Your account email changed",
                espanol
                        ? "<p>La cuenta de Sendik que usaba esta dirección ahora usa otra. Este es el "
                                + "último mensaje que enviamos aquí.</p>"
                                + "<p>Si no fuiste tú, alguien tiene acceso a tu cuenta. Escríbenos de "
                                + "inmediato desde la página de contacto.</p>"
                        : "<p>The Sendik account that used this address now uses another one. This is the "
                                + "last message we send here.</p>"
                                + "<p>If this was not you, someone has access to your account. Contact us "
                                + "right away from the contact page.</p>");
    }

    private static String cuerpo(String titulo, String texto, String enlace, String etiquetaDelBoton) {
        return "<h1>" + titulo + "</h1><p>" + texto + "</p><p><a href=\"" + enlace + "\">" + etiquetaDelBoton
                + "</a></p>";
    }
}
