package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.AccessTokenIssuer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Criterio 25 por el lado apagado, con el contexto entero. HU-016.
 *
 * <p><strong>Se prueba con el contexto de verdad y no montando el controlador a mano</strong>,
 * por lo mismo que {@code CartApagadoTest}: un nombre de propiedad mal escrito dejaria la
 * libreta encendida en produccion con toda la suite en verde. Aqui los dos controladores los
 * crea Spring o no los crea, segun la propiedad.
 *
 * <p><strong>Y afirma el cuerpo y no solo el estado.</strong> Un 404 con un codigo distinto
 * del que devuelve Spring cuando un controlador no existe seria distinguible, y con eso una
 * funcionalidad apagada dejaria de estar escondida.
 *
 * <p><strong>Las siete rutas</strong>: las cinco de la libreta y las dos de la division
 * politico-administrativa. Las cinco primeras cuelgan de {@code /users/me} y dependen de que
 * esa regla sea «autenticado»; las dos ultimas tienen su propia regla en
 * {@code SecurityConfig} justamente para no caer en el {@code denyAll} final y salir 403.
 */
@SpringBootTest(properties = "sendik.features.checkout=false")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ShippingAddressesApagadoTest {

    private static final String CUALQUIERA = UUID.randomUUID().toString();
    private static final String CUERPO = """
            {"recipientName":"Ana María Ruiz","phone":"3001234567","municipalityCode":"11001",
             "line":"Calle 45 # 12-34"}
            """;

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    ShippingAddressesApagadoTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /**
     * Las cinco de la libreta, con token valido.
     *
     * <p><strong>Sin token responderian 401 y eso no probaria nada</strong>: la cadena rechaza
     * antes de buscar manejador, asi que el 401 saldria igual con la bandera encendida. Con
     * token la peticion atraviesa la cadena y llega a donde no hay controlador.
     */
    @Test
    void deberia_responder_404_en_las_cinco_rutas_de_la_libreta() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mvc.perform(post("/api/v1/users/me/addresses")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/me/addresses/" + CUALQUIERA)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/users/me/addresses/" + CUALQUIERA).header("Authorization", token))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/users/me/default-address")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + CUALQUIERA + "\"}"))
                .andExpect(status().isNotFound());
    }

    /** Y las dos de la division politico-administrativa. */
    @Test
    void deberia_responder_404_en_las_dos_rutas_de_la_division() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        mvc.perform(get("/api/v1/locations/departments").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mvc.perform(get("/api/v1/locations/departments/11/municipalities").header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /**
     * Y no se distinguen de una ruta inventada <strong>de su mismo prefijo</strong>.
     *
     * <p>Se afirma comparando en vez de fijar un numero: lo que la bandera promete no es
     * «404», es que no se pueda distinguir una funcionalidad apagada de una que no existe.
     *
     * <p><strong>La comparacion es contra {@code /users/me/inventado} y no contra
     * {@code /api/v1/inventado}, y la diferencia importa.</strong> Esta segunda no casa con
     * ninguna regla de {@code SecurityConfig} y cae en el {@code denyAll} final, asi que
     * responde 403: no es «una ruta que no existe», es una ruta fuera de la superficie
     * declarada. Lo que hay que comparar es con una que este dentro de la misma regla de
     * autorizacion y no tenga manejador, que es exactamente la situacion en la que la bandera
     * apagada deja a la libreta.
     */
    @Test
    void deberia_responder_lo_mismo_que_una_ruta_inventada_de_su_mismo_prefijo() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        int inventadaDeLaCuenta = mvc.perform(
                        get("/api/v1/users/me/inventado-que-no-existe").header("Authorization", token))
                .andReturn()
                .getResponse()
                .getStatus();

        mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                .andExpect(status().is(inventadaDeLaCuenta));
        mvc.perform(get("/api/v1/locations/departments").header("Authorization", token))
                .andExpect(status().is(inventadaDeLaCuenta));
    }

    /**
     * Un token cualquiera, emitido por el emisor real.
     *
     * <p>No hace falta que la cuenta exista en la base: la peticion no llega a ningun caso de
     * uso, que es justamente lo que esta prueba comprueba.
     */
    private String tokenCualquiera() {
        return contexto.getBean(AccessTokenIssuer.class)
                .emitir(
                        User.rehidratar(
                                UserId.nuevo(),
                                new Email("alguien@sendik.co"),
                                new DisplayName("Quien compra"),
                                new BirthDate(LocalDate.of(1990, 3, 4)),
                                null,
                                null,
                                null,
                                UserLocale.ES,
                                UserStatus.ACTIVE,
                                Instant.now(),
                                Set.of(),
                                Instant.now()),
                        TokenFamilyId.nueva(),
                        Instant.now())
                .value();
    }
}
