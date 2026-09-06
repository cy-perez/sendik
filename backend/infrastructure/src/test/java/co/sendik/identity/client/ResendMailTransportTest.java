package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.config.MailProperties;
import co.sendik.shared.config.AppProperties;
import java.net.URI;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La clave de Resend se exige en el adaptador que la usa, no en {@link
 * MailProperties}.
 *
 * <p>Estuvo como {@code @NotBlank} en el record y eso convertia una clave de un
 * proveedor concreto en un requisito de todo el mundo: con {@code
 * sendik.mail.provider=console}, que es lo que usa el perfil local, no se lee
 * nunca. Bastaba con que el {@code .env} de la raiz trajera la linea en blanco
 * para que la aplicacion no arrancara, porque una variable definida vacia esta
 * presente y el valor por omision del YAML no llega a aplicarse.
 *
 * <p><strong>Estuvo en bootstrap y ya no.</strong> Vivio ahi porque
 * {@code infrastructure} no tenia ninguna prueba: sin datos de cobertura, su
 * {@code jacocoTestCoverageVerification} se saltaba entero, y esta prueba, al ser
 * la primera, encendia el gate y lo hacia fallar por un porcentaje que no tenia
 * nada que ver con lo que aqui se comprueba. Aquel comentario dejo dicho que se
 * mudaria cuando el modulo tuviera pruebas de verdad, y es lo que ha pasado. El
 * minimo se evalua ahora sobre los cinco modulos juntos
 * ({@code verificarCoberturaAgregada} en backend/build.gradle.kts), que es lo que
 * permite medir tambien los adaptadores JDBC sin duplicar el esquema de Flyway.
 */
class ResendMailTransportTest {

    private static final AppProperties APP = new AppProperties(
            URI.create("http://localhost:4200"),
            URI.create("http://localhost:8080"),
            "soporte@localhost",
            List.of("http://localhost:4200"),
            ZoneId.of("America/Bogota"));

    private static MailProperties conClave(String clave) {
        return new MailProperties(
                "no-responder@localhost",
                clave,
                URI.create("https://api.resend.com/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");
    }

    private static ResendMailTransport construir(MailProperties correo) {
        return new ResendMailTransport(correo);
    }

    @Test
    @DisplayName("deberia_construirse_cuando_la_clave_esta_presente")
    void deberia_construirse_cuando_la_clave_esta_presente() {
        assertThatCode(() -> construir(conClave("re_una_clave_de_verdad"))).doesNotThrowAnyException();
    }

    /**
     * El caso que rompia el arranque en local: {@code MAIL_PROVIDER_API_KEY=} en
     * el {@code .env}, sin nada detras.
     */
    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_llega_vacia")
    void deberia_fallar_al_arrancar_cuando_la_clave_llega_vacia() {
        assertThatThrownBy(() -> construir(conClave("")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("MAIL_PROVIDER_API_KEY");
    }

    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_es_solo_espacios")
    void deberia_fallar_al_arrancar_cuando_la_clave_es_solo_espacios() {
        assertThatThrownBy(() -> construir(conClave("   "))).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("deberia_fallar_al_arrancar_cuando_la_clave_no_esta")
    void deberia_fallar_al_arrancar_cuando_la_clave_no_esta() {
        assertThatThrownBy(() -> construir(conClave(null))).isInstanceOf(IllegalStateException.class);
    }

    /**
     * El mensaje tiene que decir la salida, no solo el problema: en local la
     * respuesta correcta no es conseguir una clave de Resend, es no usar Resend.
     */
    @Test
    @DisplayName("deberia_explicar_la_alternativa_de_consola_en_el_mensaje")
    void deberia_explicar_la_alternativa_de_consola_en_el_mensaje() {
        assertThatThrownBy(() -> construir(conClave(null)))
                .satisfies(fallo -> assertThat(fallo.getMessage()).contains("MAIL_PROVIDER=console"));
    }
}
