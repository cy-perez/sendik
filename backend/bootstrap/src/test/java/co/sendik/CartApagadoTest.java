package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Criterio 27 por el lado apagado, con el contexto entero. HU-015.
 *
 * <p><strong>Se prueba con el contexto de verdad y no montando el controlador a mano</strong>,
 * por lo mismo que {@code CatalogSearchApagadaTest}: un nombre de propiedad mal escrito
 * dejaria el carrito encendido en produccion con toda la suite en verde. Aqui los dos
 * controladores los crea Spring o no los crea, segun la propiedad.
 *
 * <p><strong>Y afirma el cuerpo y no solo el estado.</strong> Un 404 con un codigo distinto
 * del que devuelve Spring cuando un controlador no existe seria distinguible, y con eso una
 * funcionalidad apagada dejaria de estar escondida.
 *
 * <p><strong>Las seis rutas y no una.</strong> Cinco cuelgan de {@code /users/me/cart} y
 * dependen de que la regla de {@code /api/v1/users/**} sea «autenticado» —una regla por rol
 * habria respondido 403 en el filtro, que confirma—; la sexta es {@code /carts}, que es
 * publica cuando esta encendida y necesita su propia regla apagada para no caer en el
 * {@code denyAll} final y salir 403.
 */
@SpringBootTest(properties = {"sendik.features.catalog=true", "sendik.features.checkout=false"})
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CartApagadoTest {

    private static final String CUALQUIERA = UUID.randomUUID().toString();

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    CartApagadoTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /** El catalogo de HU-009 sigue respondiendo: el carrito no lo apaga. */
    @Test
    void deberia_seguir_sirviendo_el_catalogo_con_el_carrito_apagado() throws Exception {
        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    /**
     * La lectura anonima responde <strong>exactamente lo mismo que una ruta inventada</strong>.
     *
     * <p>Y se afirma asi, comparando, en vez de fijar un numero. Lo que la bandera promete no
     * es «404»: es que no se pueda distinguir una funcionalidad apagada de una que no existe.
     * Cual sea el estado concreto lo decide la cadena de seguridad para todas las rutas
     * desconocidas por igual, y el dia que ese estado cambie esta prueba seguira diciendo la
     * verdad en vez de romperse por un motivo que no es el suyo.
     *
     * <p>Sin la regla apagada de {@code /api/v1/carts/**} en {@code SecurityConfig}, la
     * peticion caeria en el {@code denyAll} final. Con ella atraviesa la cadena y termina
     * donde no hay manejador, que es donde termina cualquier ruta que no existe.
     */
    @Test
    void deberia_responder_lo_mismo_que_una_ruta_inventada_en_la_lectura_anonima() throws Exception {
        int inventada = mvc.perform(get("/api/v1/inventado-que-no-existe"))
                .andReturn()
                .getResponse()
                .getStatus();

        mvc.perform(get("/api/v1/carts").param("ids", CUALQUIERA)).andExpect(status().is(inventada));
    }

    /**
     * Y las cinco de la cuenta tampoco, con token valido.
     *
     * <p><strong>Sin token responderian 401 y eso no probaria nada</strong>: la cadena rechaza
     * antes de buscar manejador, asi que el 401 saldria igual con la bandera encendida. Con
     * token la peticion atraviesa la cadena y llega a donde no hay controlador, que es donde
     * se ve que la ruta no existe.
     */
    @Test
    void deberia_responder_404_en_las_cinco_rutas_de_la_cuenta() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        mvc.perform(get("/api/v1/users/me/cart").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mvc.perform(get("/api/v1/users/me/cart/items/" + CUALQUIERA).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/me/cart/items/" + CUALQUIERA).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/users/me/cart/items/" + CUALQUIERA).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/users/me/cart")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[\"" + CUALQUIERA + "\"]}"))
                .andExpect(status().isNotFound());
    }

    /**
     * Un token cualquiera, emitido por el emisor real.
     *
     * <p>No hace falta que la cuenta exista en la base: la peticion no llega a ningun caso de
     * uso, que es justamente lo que esta prueba comprueba.
     */
    private String tokenCualquiera() {
        return contexto.getBean(co.sendik.identity.port.out.AccessTokenIssuer.class)
                .emitir(
                        co.sendik.identity.model.User.rehidratar(
                                co.sendik.identity.model.UserId.nuevo(),
                                new co.sendik.identity.model.Email("alguien@sendik.co"),
                                new co.sendik.identity.model.DisplayName("Quien compra"),
                                new co.sendik.identity.model.BirthDate(java.time.LocalDate.of(1990, 3, 4)),
                                null,
                                null,
                                null,
                                co.sendik.identity.model.UserLocale.ES,
                                co.sendik.identity.model.UserStatus.ACTIVE,
                                java.time.Instant.now(),
                                java.util.Set.of(),
                                java.time.Instant.now()),
                        co.sendik.identity.model.TokenFamilyId.nueva(),
                        java.time.Instant.now())
                .value();
    }
}
