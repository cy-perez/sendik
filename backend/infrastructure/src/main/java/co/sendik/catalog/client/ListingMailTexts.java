package co.sendik.catalog.client;

import co.sendik.identity.model.UserLocale;
import org.jspecify.annotations.Nullable;

/**
 * Los textos de los tres correos de decision sobre una publicacion. HU-007 criterio 26.
 *
 * <p>Estan aqui y no repartidos entre los dos adaptadores por lo mismo que
 * {@code VerificationMailTexts}: para que el de consola y el de Resend digan lo mismo, y
 * lo que se lee al probar sea lo que recibe una persona.
 *
 * <p><strong>El motivo llega ya traducido y no como enumeracion.</strong> La lista de
 * motivos es de {@code catalog} y este paquete es de {@code identity}: nombrarla aqui
 * ataria un contexto al modelo del otro. Quien la traduce es
 * {@code MailListingNotifier}, que conoce la enumeracion y sabe en que idioma escribe la
 * persona.
 */
final class ListingMailTexts {

    private ListingMailTexts() {}

    static String asuntoDeAprobada(UserLocale idioma) {
        return espanol(idioma) ? "Tu publicación ya está en Sendik" : "Your listing is live on Sendik";
    }

    /**
     * No enlaza a la publicacion ni al catalogo: esas pantallas llegan con su propia
     * historia, y prometer aqui una direccion que todavia no existe deja un enlace roto.
     */
    static String cuerpoDeAprobada(UserLocale idioma, String titulo) {
        String nombre = escapar(titulo);

        return espanol(idioma)
                ? "<p>Revisamos <strong>" + nombre + "</strong> y ya está publicada.</p>"
                        + "<p>Desde ahora cualquiera puede verla en Sendik.</p>"
                : "<p>We reviewed <strong>" + nombre + "</strong> and it is now published.</p>"
                        + "<p>From now on anyone can see it on Sendik.</p>";
    }

    static String asuntoDeRechazada(UserLocale idioma) {
        return espanol(idioma) ? "No pudimos publicar tu producto" : "We could not publish your product";
    }

    /**
     * Dice que se puede corregir y reenviar, porque RN-022 no pone limite de intentos. Es
     * la diferencia con el correo de verificacion rechazada, que si los cuenta.
     */
    static String cuerpoDeRechazada(UserLocale idioma, String titulo, String motivo, @Nullable String nota) {
        String nombre = escapar(titulo);
        String porQue = escapar(motivo);

        return (espanol(idioma)
                        ? "<p>Revisamos <strong>" + nombre + "</strong> y no pudimos publicarla.</p>" + "<p>Motivo: "
                                + porQue + "</p>"
                        : "<p>We reviewed <strong>" + nombre + "</strong> and could not publish it.</p>" + "<p>Reason: "
                                + porQue + "</p>")
                + notaComoParrafo(idioma, nota)
                + (espanol(idioma)
                        ? "<p>Puedes corregirla y volver a enviarla las veces que haga falta. Conserva sus datos y sus fotos.</p>"
                        : "<p>You can fix it and send it again as many times as you need. It keeps its data and photos.</p>");
    }

    static String asuntoDeRetirada(UserLocale idioma) {
        return espanol(idioma) ? "Retiramos tu publicación de Sendik" : "We removed your listing from Sendik";
    }

    /**
     * Dice lo que la persona necesita saber y no adorna: dejo de ser visible y lo decidio
     * Sendik, no ella. Sin esa frase, quien lo reciba no sabe si la borro sin querer.
     */
    static String cuerpoDeRetirada(UserLocale idioma, String titulo, String motivo, @Nullable String nota) {
        String nombre = escapar(titulo);
        String porQue = escapar(motivo);

        return (espanol(idioma)
                        ? "<p>Retiramos <strong>" + nombre + "</strong> y ya no se ve en Sendik.</p>" + "<p>Motivo: "
                                + porQue + "</p>"
                        : "<p>We removed <strong>" + nombre + "</strong> and it is no longer visible on Sendik.</p>"
                                + "<p>Reason: " + porQue + "</p>")
                + notaComoParrafo(idioma, nota)
                + (espanol(idioma)
                        ? "<p>Si crees que fue un error, escríbenos desde la página de contacto.</p>"
                        : "<p>If you think this was a mistake, write to us from the contact page.</p>");
    }

    private static String notaComoParrafo(UserLocale idioma, @Nullable String nota) {
        if (nota == null || nota.isBlank()) {
            return "";
        }
        String texto = escapar(nota);
        return espanol(idioma) ? "<p>Nota del equipo: " + texto + "</p>" : "<p>Note from the team: " + texto + "</p>";
    }

    /**
     * El titulo lo escribe el vendedor y la nota el moderador; el cuerpo es HTML.
     *
     * <p>Sin esto, un titulo con una etiqueta dentro entra tal cual en el correo de otra
     * persona. El dominio guarda los dos como texto plano y el frontend no los interpreta
     * como marcado (caso borde de la historia); un buzon si, asi que aqui se escapa.
     */
    private static String escapar(String texto) {
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static boolean espanol(UserLocale idioma) {
        return idioma == UserLocale.ES;
    }
}
