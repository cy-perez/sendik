package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * A quien se le cuenta cada peticion.
 *
 * <p>{@link RateLimiterTest} prueba la cuenta; esto prueba **la clave**, que es la decision
 * que de verdad se tomo: en {@code /api/v1/auth} se cuenta por origen y en
 * {@code /api/v1/users} por sujeto del token. Confundirlas no rompe ninguna cuenta, hace
 * otra cosa peor: contar por IP en las rutas de cuenta deja sin servicio a una oficina
 * entera por lo que haga una sola persona.
 */
class RateLimitInterceptorTest {

    private static final Clock RELOJ = Clock.fixed(Instant.parse("2026-09-04T15:00:00Z"), ZoneOffset.UTC);
    private static final Duration MINUTO = Duration.ofMinutes(1);

    /** Cualquiera sirve: el interceptor decide por prefijo y no mira el identificador. */
    private static final String UUID_DE_PRUEBA = "0199b0f0-0000-7000-8000-000000000001";

    /** Uno por grupo, con dos peticiones de margen para que agotarlo quepa en una prueba. */
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(
            new RateLimiter(2, MINUTO, 1000),
            new RateLimiter(2, MINUTO, 1000),
            new RateLimiter(2, MINUTO, 1000),
            new RateLimiter(2, MINUTO, 1000),
            // El quinto es el carrito anonimo de HU-015, contado por origen.
            new RateLimiter(2, MINUTO, 1000),
            new ClientIpHasher(),
            RELOJ);

    @AfterEach
    void limpiarElContexto() {
        SecurityContextHolder.clearContext();
    }

    private static HttpServletRequest peticion(String ruta, String ip) {
        MockHttpServletRequest peticion = new MockHttpServletRequest("GET", ruta);
        peticion.setRemoteAddr(ip);
        return peticion;
    }

    private static void entrarComo(String sujeto) {
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(sujeto, "n/a", List.of()));
    }

    private boolean dejaPasar(HttpServletRequest peticion) {
        return interceptor.preHandle(peticion, null, null);
    }

    @Test
    void deberia_ignorar_lo_que_no_cae_en_ningun_grupo() {
        HttpServletRequest categorias = peticion("/api/v1/categories", "10.0.0.1");

        for (int i = 0; i < 10; i++) {
            assertThat(dejaPasar(categorias)).isTrue();
        }
    }

    @Test
    void deberia_limitar_las_rutas_de_cuenta_por_sujeto_del_token() {
        entrarComo("ana");

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isTrue();
        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * <strong>La que justifica contar por sujeto.</strong>
     *
     * <p>Ana y Luis salen por la misma IP -una oficina, un operador movil- y Ana agota su
     * cupo. Contando por origen, Luis se quedaria fuera sin haber hecho nada. Contando por
     * sujeto, ni se entera.
     */
    @Test
    void no_deberia_dejar_fuera_a_quien_comparte_salida_con_quien_agoto_su_cupo() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);

        entrarComo("luis");

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1")))
                .as("la misma IP, otra cuenta: no le toca el limite de nadie")
                .isTrue();
    }

    /** Y cambiar de salida no renueva el cupo, que es la otra mitad de la decision. */
    @Test
    void deberia_seguir_contando_a_la_misma_cuenta_aunque_cambie_de_origen() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.2"));

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.3")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /** Cada ruta lleva su propia cuenta, tambien aqui: agotar una no cierra las demas. */
    @Test
    void deberia_contar_cada_ruta_de_cuenta_por_separado() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/users/me/listings/summary", "10.0.0.1"));

        assertThat(dejaPasar(peticion("/api/v1/users/me/listings", "10.0.0.1"))).isTrue();
    }

    /** Sin autenticacion no hay a quien contar: la peticion va a salir 401 de todos modos. */
    @Test
    void deberia_dejar_pasar_una_ruta_de_cuenta_sin_sesion() {
        for (int i = 0; i < 5; i++) {
            assertThat(dejaPasar(peticion("/api/v1/users/me/listings", "10.0.0.1")))
                    .isTrue();
        }
    }

    /**
     * <strong>La que justifica el grupo de publicaciones.</strong>
     *
     * <p>El bucle que dejo a la vista HU-013: enviar a revision, retirar y volver a enviar
     * engorda el rastro de moderacion sin cota. Recorre dos URI distintas -{@code POST} y
     * {@code DELETE} sobre la misma ruta-, asi que se cuenta por grupo y no por ruta. Con
     * una cuenta por ruta, cada mitad del ciclo tendria el cupo entero y el ciclo no se
     * frenaria nunca.
     */
    @Test
    void deberia_acotar_el_ciclo_de_enviar_y_retirar_con_una_sola_cuenta() {
        entrarComo("ana");
        String envio = "/api/v1/listings/" + UUID_DE_PRUEBA + "/submission";

        assertThat(dejaPasar(peticion(envio, "10.0.0.1"))).isTrue();
        assertThat(dejaPasar(peticion(envio, "10.0.0.1"))).isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion(envio, "10.0.0.1")))
                .as("la tercera vuelta del ciclo ya no pasa")
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * Y agotar una ruta de publicacion cierra las demas del grupo, al reves que en cuenta.
     * Es la diferencia deliberada: si no, bastaria repartir el bucle entre rutas.
     */
    @Test
    void deberia_contar_todas_las_rutas_de_publicacion_juntas() {
        entrarComo("ana");
        dejaPasar(peticion("/api/v1/listings/" + UUID_DE_PRUEBA + "/submission", "10.0.0.1"));
        dejaPasar(peticion("/api/v1/listings/" + UUID_DE_PRUEBA + "/price", "10.0.0.1"));

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/listings/" + UUID_DE_PRUEBA + "/pause", "10.0.0.1")))
                .as("otra ruta, la misma cuenta")
                .isInstanceOf(RateLimitExceededException.class);
    }

    /** Crear tambien cuenta: la coleccion no lleva barra final y aun asi entra en el grupo. */
    @Test
    void deberia_limitar_la_creacion_de_publicaciones() {
        entrarComo("ana");

        assertThat(dejaPasar(peticion("/api/v1/listings", "10.0.0.1"))).isTrue();
        assertThat(dejaPasar(peticion("/api/v1/listings", "10.0.0.1"))).isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/listings", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);
    }

    /**
     * <strong>La que evita convertir la defensa en el ataque.</strong>
     *
     * <p>El catalogo y la ficha son publicos y cuelgan del mismo prefijo. La cadena no
     * desactiva el filtro anonimo, asi que llegan con un {@code AnonymousAuthenticationToken}
     * que responde {@code true} a {@code isAuthenticated()} y se llama {@code anonymousUser}.
     * Si contara como sujeto, todo el trafico anonimo del sitio compartiria un unico cupo.
     */
    @Test
    void no_deberia_contar_el_catalogo_publico_que_llega_como_anonimo() {
        SecurityContextHolder.getContext()
                .setAuthentication(new AnonymousAuthenticationToken(
                        "clave", "anonymousUser", createAuthorityList("ROLE_ANONYMOUS")));

        for (int i = 0; i < 10; i++) {
            assertThat(dejaPasar(peticion("/api/v1/listings", "10.0.0.1")))
                    .as("el catalogo publico no se cuenta")
                    .isTrue();
            assertThat(dejaPasar(peticion("/api/v1/listings/" + UUID_DE_PRUEBA, "10.0.0.1")))
                    .as("la ficha publica tampoco")
                    .isTrue();
        }
    }

    /** En `auth` no cambia nada: ahi se sigue contando por origen, porque no hay cuenta. */
    @Test
    void deberia_seguir_limitando_las_rutas_de_sesion_por_origen() {
        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1"))).isTrue();
        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1"))).isTrue();

        assertThatThrownBy(() -> dejaPasar(peticion("/api/v1/auth/session", "10.0.0.1")))
                .isInstanceOf(RateLimitExceededException.class);

        assertThat(dejaPasar(peticion("/api/v1/auth/session", "10.0.0.9")))
                .as("otra salida, otra cuenta")
                .isTrue();
    }
    /**
     * El carrito anonimo se cuenta, y se cuenta por origen.
     *
     * <p>Es la unica ruta publica de la API que dispara mas de una consulta por peticion —un
     * {@code IN} de hasta veinte publicaciones con su join de portadas, mas una consulta de
     * perfil por vendedor distinto—, asi que sin tope queda abierta una amplificacion de una
     * a veintiuna sin credencial ninguna. Y por origen porque aqui no hay sujeto: la ruta es
     * {@code permitAll} y no llega con token, que es justo lo contrario de los dos grupos
     * anteriores.
     */
    @Test
    void deberia_contar_el_carrito_anonimo_por_origen() {
        HttpServletRequest carrito = peticion("/api/v1/carts", "203.0.113.10");

        assertThat(dejaPasar(carrito)).isTrue();
        assertThat(dejaPasar(carrito)).isTrue();

        assertThatThrownBy(() -> dejaPasar(carrito)).isInstanceOf(RateLimitExceededException.class);
    }

    /** Y dos origenes distintos no se estorban: el de al lado no paga lo que hizo el primero. */
    @Test
    void no_deberia_mezclar_dos_origenes_en_el_carrito_anonimo() {
        HttpServletRequest una = peticion("/api/v1/carts", "203.0.113.20");
        HttpServletRequest otra = peticion("/api/v1/carts", "203.0.113.21");

        dejaPasar(una);
        dejaPasar(una);

        assertThat(dejaPasar(otra)).isTrue();
    }
}
