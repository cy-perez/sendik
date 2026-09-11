package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

/**
 * La libreta por HTTP, con la cadena de seguridad de verdad. HU-016.
 *
 * <p>Lo que se prueba aqui y no en otro sitio:
 *
 * <ul>
 *   <li>Que sin token no hay libreta, y que el identificador sale del {@code sub} y no de la
 *       peticion: no hay donde escribir el de otra persona (criterio 17).
 *   <li>Que una direccion ajena responde <strong>404 y nunca 403</strong>, en las tres rutas
 *       que la aceptan (criterio 15). Un 403 confirmaria que ese identificador es de alguien.
 *   <li>Que el borde rechaza con una entrada por campo y no con un aviso general (criterio 7).
 *   <li>Que los dos codigos nuevos salen con su estado: 422 para el municipio desconocido y
 *       422 para la libreta llena.
 * </ul>
 */
@SpringBootTest(properties = "sendik.features.checkout=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ShippingAddressesSecurityTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final JdbcClient jdbc;

    private MockMvc mvc;

    ShippingAddressesSecurityTest(WebApplicationContext contexto, AccessTokenIssuer emisor, JdbcClient jdbc) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Nested
    class SinSesion {

        /** Criterio 17: las cinco rutas exigen token. */
        @Test
        void deberia_responder_401_en_la_libreta() throws Exception {
            mvc.perform(get("/api/v1/users/me/addresses")).andExpect(status().isUnauthorized());
            mvc.perform(post("/api/v1/users/me/addresses")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isUnauthorized());
        }

        /** Y la division tambien, aunque su contenido no sea secreto. */
        @Test
        void deberia_responder_401_en_la_division() throws Exception {
            mvc.perform(get("/api/v1/locations/departments")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ElCaminoCompleto {

        @Test
        void deberia_crear_listar_editar_marcar_y_borrar() throws Exception {
            String token = tokenNuevo();

            // Crear: 201 con Location y predeterminada sola, que es RN-099.
            String creada = mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", Matchers.containsString("/api/v1/users/me/addresses/")))
                    .andExpect(jsonPath("$.isDefault").value(true))
                    .andExpect(jsonPath("$.municipalityName").value("Bogotá, D.C."))
                    .andExpect(jsonPath("$.departmentName").value("Bogotá, D.C."))
                    .andExpect(jsonPath("$.municipalityActive").value(true))
                    .andReturn()
                    .getResponse()
                    .getContentAsString();

            String id = JSON.readTree(creada).get("id").asString();

            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.addresses.length()").value(1))
                    .andExpect(jsonPath("$.addresses[0].id").value(id));

            // Editar: la misma fila, con el municipio cambiado.
            mvc.perform(put("/api/v1/users/me/addresses/" + id)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(id))
                    .andExpect(jsonPath("$.municipalityName").value("Medellín"))
                    .andExpect(jsonPath("$.departmentName").value("Antioquia"))
                    // Editar no mueve la predeterminada.
                    .andExpect(jsonPath("$.isDefault").value(true));

            // Una segunda, y marcarla.
            String segunda = JSON.readTree(mvc.perform(post("/api/v1/users/me/addresses")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo("11001")))
                            .andExpect(jsonPath("$.isDefault").value(false))
                            .andReturn()
                            .getResponse()
                            .getContentAsString())
                    .get("id")
                    .asString();

            mvc.perform(put("/api/v1/users/me/default-address")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addressId\":\"" + segunda + "\"}"))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                    .andExpect(
                            jsonPath("$.addresses[?(@.isDefault == true)].id").value(segunda));

            // Borrar la predeterminada: la otra la releva (criterio 11).
            mvc.perform(delete("/api/v1/users/me/addresses/" + segunda).header("Authorization", token))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                    .andExpect(jsonPath("$.addresses.length()").value(1))
                    .andExpect(jsonPath("$.addresses[0].id").value(id))
                    .andExpect(jsonPath("$.addresses[0].isDefault").value(true));

            // Y borrar dos veces responde igual (criterio 14).
            mvc.perform(delete("/api/v1/users/me/addresses/" + id).header("Authorization", token))
                    .andExpect(status().isNoContent());
            mvc.perform(delete("/api/v1/users/me/addresses/" + id).header("Authorization", token))
                    .andExpect(status().isNoContent());
        }

        /** Los tres opcionales pueden no venir. */
        @Test
        void deberia_admitir_una_direccion_sin_los_campos_opcionales() throws Exception {
            mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", tokenNuevo())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"recipientName":"Ana María Ruiz","phone":"300 123 4567",
                                     "municipalityCode":"11001","line":"Calle 45 # 12-34"}
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.complement").doesNotExist())
                    .andExpect(jsonPath("$.instructions").doesNotExist())
                    .andExpect(jsonPath("$.postalCode").doesNotExist())
                    // El telefono se normaliza en el dominio: entra con espacios y sale sin ellos.
                    .andExpect(jsonPath("$.phone").value("3001234567"));
        }

        @Test
        void deberia_servir_la_division_politico_administrativa() throws Exception {
            String token = tokenNuevo();

            mvc.perform(get("/api/v1/locations/departments").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.departments.length()").value(33));

            mvc.perform(get("/api/v1/locations/departments/11/municipalities").header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalities.length()").value(1))
                    .andExpect(jsonPath("$.municipalities[0].name").value("Bogotá, D.C."));
        }

        /**
         * Un departamento que no existe da una lista vacia y no un error.
         *
         * <p>El 01 no esta en la Divipola: los codigos empiezan en 05, que es Antioquia. El 99
         * si existe -es Vichada- y esta prueba lo usaba por descuido.
         */
        @Test
        void deberia_devolver_una_lista_vacia_para_un_departamento_que_no_existe() throws Exception {
            mvc.perform(get("/api/v1/locations/departments/01/municipalities").header("Authorization", tokenNuevo()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalities").isEmpty());
        }
    }

    @Nested
    class LaDeOtraPersona {

        /**
         * Criterio 15, en las tres rutas que aceptan un identificador. 404 y nunca 403: un 403
         * confirmaria que esa direccion existe y que es de alguien.
         */
        @Test
        void deberia_responder_404_y_nunca_403() throws Exception {
            String deUna = tokenNuevo();
            String deOtra = tokenNuevo();

            String ajena = JSON.readTree(mvc.perform(post("/api/v1/users/me/addresses")
                                    .header("Authorization", deUna)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(cuerpo("11001")))
                            .andReturn()
                            .getResponse()
                            .getContentAsString())
                    .get("id")
                    .asString();

            mvc.perform(put("/api/v1/users/me/addresses/" + ajena)
                            .header("Authorization", deOtra)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));

            mvc.perform(put("/api/v1/users/me/default-address")
                            .header("Authorization", deOtra)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"addressId\":\"" + ajena + "\"}"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));

            // Borrar la ajena responde 204 —es idempotente— pero no la borra.
            mvc.perform(delete("/api/v1/users/me/addresses/" + ajena).header("Authorization", deOtra))
                    .andExpect(status().isNoContent());

            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", deUna))
                    .andExpect(jsonPath("$.addresses.length()").value(1));
        }

        /** Y no aparece en la libreta de nadie mas. */
        @Test
        void deberia_no_aparecer_en_la_libreta_de_otra_persona() throws Exception {
            String deUna = tokenNuevo();
            String deOtra = tokenNuevo();

            mvc.perform(post("/api/v1/users/me/addresses")
                    .header("Authorization", deUna)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(cuerpo("11001")));

            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", deOtra))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.addresses").isEmpty());
        }
    }

    @Nested
    class LoQueElBordeRechaza {

        /** Criterio 7: una entrada por campo, no un aviso general. */
        @Test
        void deberia_marcar_cada_campo_que_falta() throws Exception {
            mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", tokenNuevo())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"recipientName\":\"\",\"phone\":\"\",\"municipalityCode\":\"\",\"line\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field")
                            .value(Matchers.hasItems("recipientName", "phone", "municipalityCode", "line")));
        }

        /** Un municipio que no son cinco digitos lo para el borde, antes del dominio. */
        @Test
        void deberia_rechazar_un_codigo_de_municipio_mal_formado() throws Exception {
            mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", tokenNuevo())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("bogota")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
        }

        /** RN-100: bien formado pero inexistente. 422 y con su propio codigo. */
        @Test
        void deberia_rechazar_un_municipio_que_no_esta_en_la_division() throws Exception {
            mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", tokenNuevo())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("99999")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("USER_UNKNOWN_MUNICIPALITY"));
        }

        /** RN-101, criterio 8: 422 y no 403. La peticion es legitima, lo que pasa es que no cabe. */
        @Test
        void deberia_rechazar_la_direccion_once() throws Exception {
            String token = tokenNuevo();

            for (int i = 0; i < 10; i++) {
                mvc.perform(post("/api/v1/users/me/addresses")
                                .header("Authorization", token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(cuerpo("11001")))
                        .andExpect(status().isCreated());
            }

            mvc.perform(post("/api/v1/users/me/addresses")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("USER_ADDRESS_BOOK_FULL"));

            // Y no se descarto la mas antigua en silencio.
            mvc.perform(get("/api/v1/users/me/addresses").header("Authorization", token))
                    .andExpect(jsonPath("$.addresses.length()").value(10));
        }
    }

    // --- Ayudantes ---

    private static String cuerpo(String municipio) {
        return """
                {"recipientName":"Ana María Ruiz","phone":"3001234567","municipalityCode":"%s",
                 "line":"Calle 45 # 12-34","complement":"Apto 802",
                 "instructions":"El timbre no sirve","postalCode":"110111"}
                """.formatted(municipio);
    }

    /** Una cuenta nueva en la base y su token, que es lo unico que el controlador mira. */
    private String tokenNuevo() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Quien compra', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();

        User cuenta = User.rehidratar(
                new UserId(id),
                new Email(id + "@ejemplo.co"),
                new DisplayName("Quien compra"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                Instant.now(),
                Set.of(),
                Instant.now());

        return "Bearer "
                + emisor.emitir(cuenta, TokenFamilyId.nueva(), Instant.now()).value();
    }
}
