package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.shared.config.ClientIpProperties;
import co.sendik.shared.rest.ClientIpHasher;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Que el numero de saltos de confianza llegue de la configuracion al borde.
 *
 * <p>Es el unico eslabon de ADR-0036 que no se puede comprobar con una prueba de
 * unidad, y es el que falla en silencio: si la clave no coincide con el prefijo del
 * record, o si el cableado no pasa el valor, {@code trustedHops} queda en {@code 0} y
 * la aplicacion arranca igual. En la nube eso significa ignorar la cabecera entera y
 * contar a todo el mundo junto, que desde un solo cliente se ve identico a que
 * funcione.
 *
 * <p>Se declara <strong>3</strong> a proposito, y no el {@code 1} de Cloud Run ni el
 * {@code 0} de {@code local}: con un valor que no es el de ningun entorno, una clave
 * mal escrita no puede pasar por casualidad.
 */
@SpringBootTest(properties = "sendik.client-ip.trusted-hops=3")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ClientIpWiringTest {

    private static final String DEL_CLIENTE = "190.85.12.7";

    private final ClientIpProperties propiedades;
    private final ClientIpHasher hasher;
    private final Environment entorno;

    ClientIpWiringTest(ClientIpProperties propiedades, ClientIpHasher hasher, Environment entorno) {
        this.propiedades = propiedades;
        this.hasher = hasher;
        this.entorno = entorno;
    }

    @Test
    void deberia_enlazar_los_saltos_de_confianza_declarados() {
        assertThat(propiedades.trustedHops()).isEqualTo(3);
    }

    /**
     * Y el bean del borde tiene que haber recibido ese numero, no otro: con tres saltos la
     * direccion de quien llama es la tercera desde el final, asi que lo que quien llama
     * escriba delante no la alcanza.
     */
    @Test
    void deberia_construir_el_hasher_con_ese_numero() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr("169.254.1.1");
        peticion.addHeader("X-Forwarded-For", "203.0.113.1, " + DEL_CLIENTE + ", 10.0.0.9, 10.0.0.1");

        MockHttpServletRequest directa = new MockHttpServletRequest();
        directa.setRemoteAddr(DEL_CLIENTE);

        assertThat(hasher.hashear(peticion)).isEqualTo(new ClientIpHasher(0).hashear(directa));
    }

    /**
     * <strong>La estrategia de Tomcat se queda sin poner, y eso hay que sostenerlo.</strong>
     *
     * <p>ADR-0036 la descarta porque su modelo de confianza no es el de Cloud Run:
     * {@code RemoteIpValve} recorre la cabecera desde la derecha saltando rangos privados, y
     * el frontend de Google es publico. Si alguien la enciende, {@code getRemoteAddr()} pasa
     * a derivarse de la cabecera y con cero saltos -{@code local} y el ensayo de integracion
     * continua- se vuelve al fallo original, con el respaldo contaminado tambien en la nube.
     */
    @Test
    void no_deberia_activarse_la_estrategia_de_cabeceras_reenviadas_de_tomcat() {
        assertThat(entorno.getProperty("server.forward-headers-strategy")).isNull();
    }

    /**
     * <strong>El valor por omision de la nube, que ninguna prueba podia alcanzar.</strong>
     *
     * <p>Todo lo que levanta un contexto aqui corre con el perfil {@code local}, que lo pisa
     * con {@code 0}, asi que borrar el bloque de {@code application.yaml} o cambiar su
     * respaldo a cero dejaba la suite entera en verde y Cloud Run contando a todo el mundo
     * junto. Es el valor con mas radio de explosion del cambio, asi que se fija donde se
     * puede: en el texto del archivo que lo declara.
     */
    @Test
    void deberia_declarar_uno_como_respaldo_para_los_entornos_desplegados() throws Exception {
        String configuracion = new ClassPathResource("application.yaml").getContentAsString(StandardCharsets.UTF_8);

        assertThat(configuracion).contains("trusted-hops: ${CLIENT_IP_TRUSTED_HOPS:1}");
    }
}
