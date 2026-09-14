package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
 * Criterio 20 por el lado apagado, con el contexto entero. HU-017.
 *
 * <p>Con {@code FEATURE_CHECKOUT} apagada el controlador no existe, y las tres rutas
 * responden <strong>byte por byte</strong> lo mismo que una ruta inventada del mismo
 * prefijo: 404 con {@code COMMON_NOT_FOUND}. Se compara el cuerpo entero salvo el
 * {@code traceId}, que es distinto en cada respuesta por definicion.
 */
@SpringBootTest(properties = "sendik.features.checkout=false")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class OriginAddressApagadoTest {

    private static final String RUTA = "/api/v1/users/me/origin-address";
    private static final String CUERPO = "{\"municipalityCode\":\"11001\",\"line\":\"Carrera 15 # 93-47\"}";

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    OriginAddressApagadoTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /** Con token valido, para que la peticion atraviese la cadena y llegue a donde no hay controlador. */
    @Test
    void deberia_responder_404_en_las_tres_rutas() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        mvc.perform(get(RUTA).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mvc.perform(put(RUTA)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
        mvc.perform(delete(RUTA).header("Authorization", token))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /** Y no se distingue de una ruta inventada de su mismo prefijo, ni por el cuerpo. */
    @Test
    void deberia_responder_lo_mismo_que_una_ruta_inventada_de_su_mismo_prefijo() throws Exception {
        String token = "Bearer " + tokenCualquiera();

        var inventada = mvc.perform(
                        get("/api/v1/users/me/inventado-que-no-existe").header("Authorization", token))
                .andReturn()
                .getResponse();

        mvc.perform(get(RUTA).header("Authorization", token))
                .andExpect(status().is(inventada.getStatus()))
                .andExpect(content().contentType(inventada.getContentType()))
                .andExpect(jsonPath("$.type").value(sinTraza(inventada.getContentAsString(), "type")))
                .andExpect(jsonPath("$.title").value(sinTraza(inventada.getContentAsString(), "title")))
                .andExpect(jsonPath("$.code").value(sinTraza(inventada.getContentAsString(), "code")))
                .andExpect(jsonPath("$.status").value(inventada.getStatus()))
                .andExpect(jsonPath("$.detail").doesNotExist());
        // Lo unico que difiere es el traceId, distinto en cada respuesta por definicion, y el
        // `instance`, que es la ruta.
        assertThat(inventada.getContentAsString()).doesNotContain("\"detail\"");
    }

    private static String sinTraza(String cuerpo, String campo) {
        return com.jayway.jsonpath.JsonPath.read(cuerpo, "$." + campo);
    }

    private String tokenCualquiera() {
        return contexto.getBean(AccessTokenIssuer.class)
                .emitir(
                        User.rehidratar(
                                UserId.nuevo(),
                                new Email("alguien@sendik.co"),
                                new DisplayName("Quien vende"),
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
