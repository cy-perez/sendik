package co.sendik.identity.client;

import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.UserLocale;

/**
 * Los textos de los cuatro correos de verificacion de vendedor. HU-002 criterio 10.
 *
 * <p>Estan aqui y no repartidos entre los dos adaptadores para que el de consola y el de
 * Resend digan lo mismo. Con el texto en cada uno, el de desarrollo se separa del de
 * produccion y lo que se lee al probar deja de ser lo que recibe una persona.
 *
 * <p><strong>El motivo del rechazo se traduce aqui y no llega como codigo.</strong> Un
 * correo no tiene quien lo traduzca al abrirlo: no hay Transloco en un buzon. Es la
 * unica copia del catalogo de motivos que existe fuera del frontend, y por eso la
 * enumeracion es la fuente: agregar un motivo sin texto aqui no compila.
 */
final class VerificationMailTexts {

    private VerificationMailTexts() {}

    static String asuntoDeRecibida(UserLocale idioma) {
        return espanol(idioma) ? "Recibimos tu solicitud de verificación" : "We got your verification request";
    }

    static String cuerpoDeRecibida(UserLocale idioma, int diasDeRevision) {
        return espanol(idioma)
                ? "<p>Recibimos tu solicitud para vender en Sendik.</p>" + "<p>La revisamos en máximo "
                        + concordar(diasDeRevision, "día hábil", "días hábiles")
                        + " y te escribimos con el resultado. No hace falta que hagas nada más.</p>"
                : "<p>We got your request to sell on Sendik.</p>" + "<p>We review it in at most "
                        + concordar(diasDeRevision, "business day", "business days")
                        + " and we will write to you with the result. You do not need to do anything else.</p>";
    }

    static String asuntoDeAprobada(UserLocale idioma) {
        return espanol(idioma) ? "Ya eres vendedor verificado en Sendik" : "You are a verified seller on Sendik";
    }

    /**
     * No anuncia lo que se puede hacer ahora ni enlaza a publicar: esa pantalla llega con
     * su propia historia, y prometerla aqui deja un enlace a algo que no existe.
     */
    static String cuerpoDeAprobada(UserLocale idioma) {
        return espanol(idioma)
                ? "<p>Revisamos tu solicitud y quedaste verificado. Tu perfil ya muestra el sello.</p>"
                : "<p>We reviewed your request and you are verified. Your profile now shows the badge.</p>";
    }

    static String asuntoDeRechazada(UserLocale idioma) {
        return espanol(idioma) ? "No pudimos verificar tu cuenta" : "We could not verify your account";
    }

    static String cuerpoDeRechazada(UserLocale idioma, RejectionReason motivo, String nota, int intentosRestantes) {
        String cierre = intentosRestantes > 0
                ? (espanol(idioma)
                        ? "<p>Puedes corregirlo y volver a enviarlo. "
                                + (intentosRestantes == 1 ? "Te queda " : "Te quedan ")
                                + concordar(intentosRestantes, "intento", "intentos")
                                + ".</p>"
                        : "<p>You can fix it and send it again. You have "
                                + concordar(intentosRestantes, "attempt", "attempts")
                                + " left.</p>")
                // En cero no se invita a reintentar: RN-014 no lo permite, y decir "vuelve
                // a intentarlo" cuando el sistema va a negarlo es peor que no decir nada.
                : (espanol(idioma)
                        ? "<p>Ya usaste tus tres intentos. Escríbenos y lo revisamos a mano.</p>"
                        : "<p>You already used your three attempts. Write to us and we will review it by hand.</p>");

        return (espanol(idioma)
                        ? "<p>Revisamos tu solicitud para vender y no pudimos verificarla.</p>" + "<p>Motivo: "
                                + textoDelMotivo(idioma, motivo) + "</p>"
                        : "<p>We reviewed your request to sell and could not verify it.</p>" + "<p>Reason: "
                                + textoDelMotivo(idioma, motivo) + "</p>")
                + notaComoParrafo(idioma, nota)
                + cierre;
    }

    static String asuntoDeRevocada(UserLocale idioma) {
        return espanol(idioma) ? "Tu verificación de vendedor se revocó" : "Your seller verification was revoked";
    }

    /**
     * Dice lo que RN-013 decide y la persona necesita saber: lo publicado sigue visible y
     * no puede publicar mas. Sin esa frase, quien lo reciba no sabe si perdio lo que tenia.
     */
    static String cuerpoDeRevocada(UserLocale idioma, RevocationReason motivo, String nota) {
        return (espanol(idioma)
                        ? "<p>Revocamos tu verificación de vendedor.</p>"
                                + "<p>Motivo: " + textoDeLaRevocacion(idioma, motivo) + "</p>"
                                + "<p>Lo que ya tenías publicado sigue visible, pero no puedes crear publicaciones"
                                + " nuevas hasta volver a verificarte.</p>"
                        : "<p>We revoked your seller verification.</p>"
                                + "<p>Reason: " + textoDeLaRevocacion(idioma, motivo) + "</p>"
                                + "<p>What you already published stays visible, but you cannot create new listings"
                                + " until you get verified again.</p>")
                + notaComoParrafo(idioma, nota);
    }

    /** Los cinco motivos de la lista cerrada, en los dos idiomas. */
    static String textoDelMotivo(UserLocale idioma, RejectionReason motivo) {
        boolean es = espanol(idioma);

        return switch (motivo) {
            case ILLEGIBLE_PHOTOS -> es ? "las fotos no se pueden leer" : "the photos cannot be read";
            case EXPIRED_DOCUMENT -> es ? "el documento está vencido" : "the document has expired";
            case HOLDER_MISMATCH ->
                es
                        ? "el titular de la cuenta no coincide con tu documento"
                        : "the account holder does not match your document";
            case DOCUMENT_ALREADY_VERIFIED ->
                es
                        ? "ese documento ya está verificado en otra cuenta"
                        : "that document is already verified on another account";
            case REQUIREMENTS_NOT_MET ->
                es ? "no cumples los requisitos para vender" : "you do not meet the requirements to sell";
        };
    }

    /**
     * Los cinco motivos de RN-069, en los dos idiomas.
     *
     * <p>Aparte de {@link #textoDelMotivo}, porque son dos listas cerradas distintas y no
     * se mezclan. Y redactados como hechos y no como delitos: ninguno dice fraude ni
     * suplantacion. Este texto es lo que la persona lee en su buzon, asi que es el sitio
     * donde esa decision de RN-069 se nota o se pierde.
     */
    static String textoDeLaRevocacion(UserLocale idioma, RevocationReason motivo) {
        boolean es = espanol(idioma);

        return switch (motivo) {
            case DOCUMENT_NOT_ITS_HOLDER ->
                es
                        ? "el documento verificado no corresponde a quien lo presentó"
                        : "the verified document does not belong to the person who submitted it";
            case BANK_ACCOUNT_NOT_HOLDER ->
                es
                        ? "la cuenta bancaria no es del titular del documento"
                        : "the bank account does not belong to the document holder";
            case REPEATED_PROHIBITED_LISTINGS ->
                es
                        ? "se publicaron productos que no se pueden vender en Sendik, de forma reiterada"
                        : "prohibited items were published repeatedly";
            case HOLDER_REQUEST -> es ? "lo pediste tú" : "you asked for it";
            case REQUIREMENTS_NO_LONGER_MET ->
                es ? "ya no se cumplen los requisitos para vender" : "the requirements to sell are no longer met";
        };
    }

    private static String notaComoParrafo(UserLocale idioma, String nota) {
        if (nota == null || nota.isBlank()) {
            return "";
        }
        // Sin interpretar como HTML: la nota la escribe una persona y va dentro de un
        // correo. Escapar es lo minimo, y el correo es texto que alguien abre en su buzon.
        return (espanol(idioma) ? "<p>Nota de quien revisó: " : "<p>Note from the reviewer: ") + escapar(nota) + "</p>";
    }

    /**
     * Escapa lo que una persona escribio antes de meterlo en el HTML del correo.
     *
     * <p>La nota la escribe quien revisa, que es de la casa, asi que esto no protege de un
     * atacante: protege de un ampersand o de un signo de menor que rompan el mensaje. Se
     * hace igual, porque «el texto lo escribe alguien de confianza» es una suposicion que
     * envejece mal.
     */
    private static String escapar(String texto) {
        return texto.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Concuerda el numero con la palabra que le sigue.
     *
     * <p>Hace falta en los dos sitios donde este archivo escribe una cantidad, y los dos
     * pueden valer uno: RN-014 da tres intentos, asi que quedar uno es el caso corriente, y
     * {@code sendik.verification.review-days} admite cualquier entero positivo. "Te quedan
     * 1 intentos" lo lee una persona a la que acaban de rechazar algo, y es de las cosas
     * que hacen dudar de que al otro lado haya alguien.
     *
     * <p>Dos formas bastan y no se pretende mas: el espanol y el ingles hacen el plural
     * igual para estas cuatro palabras, y una cantidad negativa no llega hasta aqui.
     */
    private static String concordar(int cantidad, String singular, String plural) {
        return cantidad + " " + (cantidad == 1 ? singular : plural);
    }

    private static boolean espanol(UserLocale idioma) {
        return idioma == UserLocale.ES;
    }
}
