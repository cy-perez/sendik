package co.sendik.shared.mail;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

/**
 * <strong>La mitad de un contrato que el compilador no puede comprobar.</strong>
 *
 * <p>Entre {@link MailTaskPayload} y el {@code MailDeliveryRequest} de {@code presentation}
 * hay JSON y una cola: uno escribe y el otro lee, y viven en modulos que no se ven. Si
 * alguien renombra un campo aqui, nada falla al compilar; falla en produccion, y falla
 * ademas de la peor forma, porque Cloud Tasks reintentaria durante horas una tarea que el
 * otro lado nunca va a poder leer.
 *
 * <p>La otra mitad esta en {@code MailDeliveryControllerTest}, que da de comer al endpoint
 * exactamente este mismo JSON. Las dos pruebas repiten los tres nombres a proposito: son
 * el contrato escrito, no un detalle duplicado.
 */
class MailTaskPayloadTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void deberia_serializarse_con_los_tres_nombres_que_espera_el_endpoint() {
        String cuerpo =
                JSON.writeValueAsString(new MailTaskPayload("ana@correo.co", "Confirma tu correo", "<p>Hola</p>"));

        assertThat(cuerpo)
                .contains("\"destinatario\":\"ana@correo.co\"")
                .contains("\"asunto\":\"Confirma tu correo\"")
                .contains("\"cuerpoHtml\":\"<p>Hola</p>\"");
    }

    /**
     * El cuerpo lleva el enlace de verificacion, que es una credencial. Que viaje ahi es la
     * concesion que ADR-0031 anota; lo que no puede es viajar recortado o escapado de forma
     * que llegue distinto al otro lado.
     */
    @Test
    void deberia_conservar_el_cuerpo_html_intacto() {
        String enlace = "<a href=\"https://sendik.co/verificar-correo?token=abc&amp;x=1\">Confirmar</a>";

        MailTaskPayload ida = new MailTaskPayload("ana@correo.co", "Asunto", enlace);
        MailTaskPayload vuelta = JSON.readValue(JSON.writeValueAsString(ida), MailTaskPayload.class);

        assertThat(vuelta.cuerpoHtml()).isEqualTo(enlace);
    }
}
