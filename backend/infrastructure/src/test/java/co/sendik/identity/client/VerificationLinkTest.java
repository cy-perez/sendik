package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.config.MailProperties;
import co.sendik.shared.config.AppProperties;
import java.net.URI;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Los enlaces que van dentro de los correos.
 *
 * <p>Un enlace mal formado no rompe ninguna prueba de unidad de los casos de uso y
 * sin embargo deja la cuenta sin verificar y a la persona sin poder entrar. Las
 * tres rutas son distintas a proposito (verificacion, restablecimiento y cambio de
 * correo) y confundir dos manda a alguien a la pantalla equivocada con un token
 * que esa pantalla no sabe canjear.
 */
class VerificationLinkTest {

    private static final String TOKEN = "un-token-cualquiera";

    private VerificationLink enlacesCon(String baseUrl) {
        AppProperties app = new AppProperties(
                URI.create(baseUrl),
                URI.create(baseUrl + "/api/v1"),
                "contacto@sendik.co",
                List.of(baseUrl),
                ZoneId.of("America/Bogota"));

        MailProperties mail = new MailProperties(
                "hola@sendik.co",
                null,
                URI.create("https://api.resend.com/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");

        return new VerificationLink(app, mail);
    }

    private final VerificationLink enlaces = enlacesCon("https://sendik.co");

    @Test
    void deberia_apuntar_cada_correo_a_su_propia_pantalla() {
        assertThat(enlaces.para(TOKEN)).startsWith("https://sendik.co/verificar-correo?token=");
        assertThat(enlaces.paraRestablecer(TOKEN)).startsWith("https://sendik.co/restablecer-contrasena?token=");
        assertThat(enlaces.paraCambioDeCorreo(TOKEN)).startsWith("https://sendik.co/confirmar-correo-nuevo?token=");
    }

    /**
     * Con la barra final de la direccion base, el enlace saldria con dos barras
     * seguidas. Es la clase de detalle que nadie mira hasta que un despliegue
     * configura la variable con barra y los correos empiezan a dar 404.
     */
    @Test
    void no_deberia_duplicar_la_barra_cuando_la_direccion_base_la_trae() {
        assertThat(enlacesCon("https://sendik.co/").para(TOKEN))
                .isEqualTo("https://sendik.co/verificar-correo?token=" + TOKEN);
    }

    /**
     * El token va codificado aunque hoy se genere en base64 apto para URL: si manana
     * cambia el alfabeto del generador, el enlace sigue siendo valido en lugar de
     * romperse en silencio para la mitad de las personas.
     */
    @Test
    void deberia_codificar_el_token_para_que_viaje_entero() {
        assertThat(enlaces.para("con espacio+y/simbolos=")).endsWith("?token=con+espacio%2By%2Fsimbolos%3D");
    }
}
