package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.model.RejectionReason;
import co.sendik.identity.model.RevocationReason;
import co.sendik.identity.model.UserLocale;
import java.util.EnumSet;
import org.junit.jupiter.api.Test;

/**
 * Los textos de los cuatro correos de verificacion. HU-002 criterio 10.
 *
 * <p>Se prueban porque son la unica copia del catalogo de motivos que existe fuera del
 * frontend: un correo no tiene quien lo traduzca al abrirlo. Y porque lo que dicen o
 * dejan de decir tiene consecuencias —invitar a reintentar cuando RN-014 no lo permite
 * manda a alguien a una negativa—.
 */
class VerificationMailTextsTest {

    @Test
    void deberia_decir_el_plazo_prometido_en_el_aviso_de_recibida() {
        assertThat(VerificationMailTexts.cuerpoDeRecibida(UserLocale.ES, 2)).contains("2 días hábiles");
        assertThat(VerificationMailTexts.cuerpoDeRecibida(UserLocale.EN, 5)).contains("5 business days");
    }

    /**
     * Un dia de plazo tambien concuerda. {@code sendik.verification.review-days} admite
     * cualquier entero positivo, asi que prometer la revision "en maximo 1 dias habiles" no
     * necesita un error para aparecer: basta con cambiar una variable de entorno.
     */
    @Test
    void deberia_concordar_el_numero_cuando_el_plazo_es_de_un_solo_dia() {
        assertThat(VerificationMailTexts.cuerpoDeRecibida(UserLocale.ES, 1))
                .contains("1 día hábil")
                .doesNotContain("1 días hábiles");
        assertThat(VerificationMailTexts.cuerpoDeRecibida(UserLocale.EN, 1))
                .contains("1 business day")
                .doesNotContain("1 business days");
    }

    @Test
    void deberia_escribir_los_cuatro_asuntos_en_los_dos_idiomas() {
        assertThat(VerificationMailTexts.asuntoDeRecibida(UserLocale.ES))
                .isNotEqualTo(VerificationMailTexts.asuntoDeRecibida(UserLocale.EN));
        assertThat(VerificationMailTexts.asuntoDeAprobada(UserLocale.ES))
                .isNotEqualTo(VerificationMailTexts.asuntoDeAprobada(UserLocale.EN));
        assertThat(VerificationMailTexts.asuntoDeRechazada(UserLocale.ES))
                .isNotEqualTo(VerificationMailTexts.asuntoDeRechazada(UserLocale.EN));
        assertThat(VerificationMailTexts.asuntoDeRevocada(UserLocale.ES))
                .isNotEqualTo(VerificationMailTexts.asuntoDeRevocada(UserLocale.EN));
    }

    /**
     * No anuncia lo que se puede hacer ahora ni enlaza a publicar: esa pantalla llega con
     * su propia historia, y prometerla aqui deja un enlace a algo que no existe.
     */
    @Test
    void deberia_anunciar_el_sello_sin_prometer_publicar() {
        String espanol = VerificationMailTexts.cuerpoDeAprobada(UserLocale.ES);

        assertThat(espanol).contains("sello");
        assertThat(espanol).doesNotContain("publicar", "publicaciones", "http");
    }

    // --- Los cinco motivos ----------------------------------------------------

    @Test
    void deberia_tener_texto_para_los_cinco_motivos_en_los_dos_idiomas() {
        for (RejectionReason motivo : EnumSet.allOf(RejectionReason.class)) {
            String espanol = VerificationMailTexts.textoDelMotivo(UserLocale.ES, motivo);
            String ingles = VerificationMailTexts.textoDelMotivo(UserLocale.EN, motivo);

            assertThat(espanol).as("%s en espanol", motivo).isNotBlank();
            assertThat(ingles).as("%s en ingles", motivo).isNotBlank();
            // Ninguno se quedo sin traducir devolviendo el nombre del valor.
            assertThat(espanol).doesNotContain(motivo.name());
            assertThat(ingles).doesNotContain(motivo.name());
        }
    }

    @Test
    void deberia_llevar_el_motivo_en_el_cuerpo_del_rechazo() {
        String cuerpo =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.EXPIRED_DOCUMENT, null, 2);

        assertThat(cuerpo).contains("el documento está vencido");
    }

    // --- RN-014 ---------------------------------------------------------------

    @Test
    void deberia_invitar_a_reintentar_cuando_quedan_intentos() {
        String cuerpo =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, null, 2);

        assertThat(cuerpo).contains("volver a enviarlo").contains("2 intentos");
    }

    /**
     * Con uno solo concuerdan el verbo y el sustantivo.
     *
     * <p>No es un caso raro: RN-014 da tres intentos, asi que quedar uno es el paso previo
     * a quedarse sin ninguno, y lo lee alguien a quien acaban de rechazar por segunda vez.
     * Hasta el 8 de septiembre de 2026 decia "Te quedan 1 intentos" en espanol y "You have
     * 1 attempts left" en ingles.
     */
    @Test
    void deberia_concordar_el_numero_cuando_queda_un_solo_intento() {
        String espanol =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, null, 1);
        String ingles =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.EN, RejectionReason.ILLEGIBLE_PHOTOS, null, 1);

        assertThat(espanol).contains("Te queda 1 intento.").doesNotContain("1 intentos");
        assertThat(ingles).contains("You have 1 attempt left").doesNotContain("1 attempts");
    }

    /**
     * En cero no se invita a reintentar. RN-014 no lo permite, y decir «vuelve a
     * intentarlo» cuando el sistema va a negarlo es peor que no decir nada.
     */
    @Test
    void deberia_cumplir_RN_014_no_invitando_a_reintentar_sin_intentos() {
        String espanol =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, null, 0);
        String ingles =
                VerificationMailTexts.cuerpoDeRechazada(UserLocale.EN, RejectionReason.ILLEGIBLE_PHOTOS, null, 0);

        assertThat(espanol).doesNotContain("volver a enviarlo").contains("Escríbenos");
        assertThat(ingles).doesNotContain("send it again").contains("Write to us");
    }

    // --- RN-013 ---------------------------------------------------------------

    /**
     * Sin esa frase, quien reciba la revocacion no sabe si perdio lo que ya tenia
     * publicado.
     */
    @Test
    void deberia_cumplir_RN_013_diciendo_que_lo_publicado_sigue_visible() {
        assertThat(VerificationMailTexts.cuerpoDeRevocada(
                        UserLocale.ES, RevocationReason.REQUIREMENTS_NO_LONGER_MET, null))
                .contains("sigue visible")
                .contains("no puedes crear publicaciones");
        assertThat(VerificationMailTexts.cuerpoDeRevocada(
                        UserLocale.EN, RevocationReason.REQUIREMENTS_NO_LONGER_MET, null))
                .contains("stays visible");
    }

    // --- La nota del moderador ------------------------------------------------

    @Test
    void deberia_incluir_la_nota_cuando_la_hay() {
        String cuerpo = VerificationMailTexts.cuerpoDeRechazada(
                UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, "El reverso sale oscuro", 2);

        assertThat(cuerpo).contains("Nota de quien revisó").contains("El reverso sale oscuro");
    }

    @Test
    void deberia_omitir_el_parrafo_de_la_nota_cuando_no_hay() {
        assertThat(VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, null, 2))
                .doesNotContain("Nota de quien revisó");
        assertThat(VerificationMailTexts.cuerpoDeRechazada(UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, "   ", 2))
                .doesNotContain("Nota de quien revisó");
    }

    /**
     * La nota la escribe alguien de la casa, asi que esto no protege de un atacante:
     * protege de que un signo de menor rompa el mensaje. Se hace igual, porque «el texto
     * lo escribe alguien de confianza» es una suposicion que envejece mal.
     */
    @Test
    void deberia_escapar_lo_que_escribio_quien_revisa() {
        String cuerpo = VerificationMailTexts.cuerpoDeRechazada(
                UserLocale.ES, RejectionReason.ILLEGIBLE_PHOTOS, "Se ve <b>mal</b> & borroso", 2);

        assertThat(cuerpo).contains("&lt;b&gt;mal&lt;/b&gt; &amp; borroso");
        assertThat(cuerpo).doesNotContain("<b>mal</b>");
    }

    @Test
    void deberia_escapar_tambien_en_la_revocacion() {
        String cuerpo = VerificationMailTexts.cuerpoDeRevocada(
                UserLocale.EN, RevocationReason.REQUIREMENTS_NO_LONGER_MET, "check \"this\"");

        assertThat(cuerpo).contains("&quot;this&quot;");
    }
}
