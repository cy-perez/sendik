package co.sendik;

import static java.util.stream.Collectors.joining;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * La direccion de origen por HTTP, con la cadena de seguridad de verdad. HU-017.
 *
 * <p>Lo que se prueba aqui y no en otro sitio: que sin token no hay origen (criterio 14);
 * que sin telefono en el perfil el servidor rechaza con 422 (criterio 5); que guardar dos
 * veces reemplaza (criterio 3); que la ciudad del perfil pasa a ser el municipio del origen
 * y vuelve a quedar vacia al borrarlo (criterios 11 y 12); que el municipio inexistente sale
 * 422 (criterio 7); y que ningun registro escribe la linea, el complemento ni las
 * indicaciones (criterio 17).
 */
@SpringBootTest(properties = "sendik.features.checkout=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class OriginAddressSecurityTest {

    private static final String RUTA = "/api/v1/users/me/origin-address";
    private static final String LINEA = "Carrera 15 # 93-47";
    private static final String COMPLEMENTO = "Local 3";
    private static final String INDICACIONES = "Entrar por el parqueadero, preguntar por Nubia";

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final JdbcClient jdbc;

    private MockMvc mvc;

    OriginAddressSecurityTest(WebApplicationContext contexto, AccessTokenIssuer emisor, JdbcClient jdbc) {
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

        /** Criterio 14: las tres rutas exigen token. */
        @Test
        void deberia_responder_401_en_las_tres_rutas() throws Exception {
            mvc.perform(get(RUTA)).andExpect(status().isUnauthorized());
            mvc.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON).content(cuerpo("11001")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(RUTA)).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class ElCaminoCompleto {

        @Test
        void deberia_guardar_reemplazar_y_borrar_con_la_ciudad_del_perfil_siguiendo_al_origen() throws Exception {
            String token = tokenNuevo("3001234567", "bogota");

            // Sin origen: 204 y no 404 (criterio 1), y la ciudad escrita a mano sigue ahi.
            mvc.perform(get(RUTA).header("Authorization", token))
                    .andExpect(status().isNoContent())
                    .andExpect(content().string(""));
            mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                    .andExpect(jsonPath("$.city").value("bogota"))
                    .andExpect(jsonPath("$.cityEditable").value(true));

            // Guardar: con el remitente del perfil y el departamento al lado.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalityName").value("Bogotá, D.C."))
                    .andExpect(jsonPath("$.departmentName").value("Bogotá, D.C."))
                    .andExpect(jsonPath("$.municipalityActive").value(true))
                    .andExpect(jsonPath("$.line").value(LINEA))
                    .andExpect(jsonPath("$.senderName").value("Quien vende"))
                    .andExpect(jsonPath("$.senderPhone").value("3001234567"));

            // Criterio 11: la ciudad del perfil es ahora el municipio con su departamento, y no
            // se edita desde el perfil.
            mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                    .andExpect(jsonPath("$.city").value("Bogotá, D.C., Bogotá, D.C."))
                    .andExpect(jsonPath("$.cityEditable").value(false));

            // Y aunque el perfil mande una ciudad, se descarta.
            mvc.perform(put("/api/v1/users/me")
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"displayName\":\"Quien vende\",\"city\":\"Cali\",\"phone\":\"3001234567\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.city").value("Bogotá, D.C., Bogotá, D.C."))
                    .andExpect(jsonPath("$.cityEditable").value(false));

            // Criterio 3: reemplazar deja uno.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalityName").value("Medellín"))
                    .andExpect(jsonPath("$.departmentName").value("Antioquia"));
            mvc.perform(get(RUTA).header("Authorization", token))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalityCode").value("05001"));
            mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                    .andExpect(jsonPath("$.city").value("Medellín, Antioquia"));

            // Criterio 9 y 12: borrar, dos veces, y la ciudad queda vacia y editable.
            mvc.perform(delete(RUTA).header("Authorization", token)).andExpect(status().isNoContent());
            mvc.perform(delete(RUTA).header("Authorization", token)).andExpect(status().isNoContent());
            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isNoContent());
            mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                    .andExpect(jsonPath("$.city").doesNotExist())
                    .andExpect(jsonPath("$.cityEditable").value(true));
        }

        @Test
        void deberia_admitir_que_falten_los_tres_opcionales() throws Exception {
            String token = tokenNuevo("3001234567", null);

            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"municipalityCode\":\"11001\",\"line\":\"" + LINEA + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.complement").doesNotExist())
                    .andExpect(jsonPath("$.instructions").doesNotExist())
                    .andExpect(jsonPath("$.postalCode").doesNotExist());
        }
    }

    @Nested
    class LoQueElBordeRechaza {

        /** Criterio 5: sin telefono en el perfil, 422 con su codigo y nada guardado. */
        @Test
        void deberia_rechazar_guardar_sin_telefono_en_el_perfil() throws Exception {
            String token = tokenNuevo(null, null);

            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("USER_PHONE_REQUIRED"));

            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isNoContent());
        }

        /** Criterio 7: uno que no existe, 422 con el codigo de RN-100. */
        @Test
        void deberia_rechazar_un_municipio_inexistente() throws Exception {
            mvc.perform(put(RUTA)
                            .header("Authorization", tokenNuevo("3001234567", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("99999")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("USER_UNKNOWN_MUNICIPALITY"));
        }

        /** Criterio 8: una entrada por campo. */
        @Test
        void deberia_marcar_cada_campo_que_falta() throws Exception {
            mvc.perform(put(RUTA)
                            .header("Authorization", tokenNuevo("3001234567", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"municipalityCode\":\"\",\"line\":\"\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[*].field").value(Matchers.hasItems("municipalityCode", "line")));
        }

        /** Criterio 15: una cuenta cerrada con token vivo no escribe. */
        @Test
        void deberia_responder_401_a_una_cuenta_que_ya_no_existe() throws Exception {
            String token = tokenDeUnaCuentaQueNoEsta();

            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(delete(RUTA).header("Authorization", token)).andExpect(status().isUnauthorized());
            // Tambien al leer: no un 204 que la pantalla leeria como «no tienes origen».
            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isUnauthorized());
        }

        /** JSON roto: 400 con ProblemDetail y sin ningun trozo del cuerpo en el registro. */
        @Test
        void deberia_responder_400_con_problem_detail_a_un_cuerpo_ilegible() throws Exception {
            mvc.perform(put(RUTA)
                            .header("Authorization", tokenNuevo("3001234567", null))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"municipalityCode\":\"11001\",\"line\":" + LINEA + "}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.traceId").isNotEmpty());
        }
    }

    @Nested
    class LoQueCambiaPorDebajo {

        /** Criterio 13, la mitad que solo se ve contra la base: el DANE renombra y el perfil lo lee. */
        @Test
        void deberia_leer_el_nombre_nuevo_si_el_dane_renombra_el_municipio() throws Exception {
            String token = tokenNuevo("3001234567", null);
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isOk());

            jdbc.sql("UPDATE municipalities SET name = 'Medellín (renombrado)' WHERE code = '05001'")
                    .update();
            try {
                mvc.perform(get("/api/v1/users/me").header("Authorization", token))
                        .andExpect(jsonPath("$.city").value("Medellín (renombrado), Antioquia"));
                mvc.perform(get(RUTA).header("Authorization", token))
                        .andExpect(jsonPath("$.municipalityName").value("Medellín (renombrado)"));
            } finally {
                jdbc.sql("UPDATE municipalities SET name = 'Medellín' WHERE code = '05001'")
                        .update();
            }
        }

        /** Borrar en una pestana mientras otra edita: la segunda guarda igual, como uno nuevo. */
        @Test
        void deberia_admitir_guardar_despues_de_que_otra_pestana_lo_borrara() throws Exception {
            String token = tokenNuevo("3001234567", null);
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isOk());

            mvc.perform(delete(RUTA).header("Authorization", token)).andExpect(status().isNoContent());

            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.municipalityCode").value("05001"));
        }
    }

    /**
     * Criterio 17: ningun registro, en ningun nivel, con la linea, el complemento ni las
     * indicaciones. Recorre las tres rutas mas la descarga y los dos rechazos, y lee tambien
     * las excepciones con sus causas, como la prueba hermana de HU-016.
     */
    @Test
    void deberia_no_escribir_ningun_dato_del_origen_en_los_registros() throws Exception {
        Logger raiz = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        ListAppender<ILoggingEvent> capturadas = new ListAppender<>();
        capturadas.start();
        Level nivelAnterior = raiz.getLevel();
        raiz.setLevel(Level.DEBUG);
        raiz.addAppender(capturadas);

        try {
            String token = tokenNuevo("3001234567", null);

            // Cada peticion afirma su estado: sin eso, un 401 o un 400 por cualquier motivo
            // dejaria la prueba en verde sin haber recorrido el camino que registra.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("11001")))
                    .andExpect(status().isOk());
            mvc.perform(get(RUTA).header("Authorization", token)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/users/me").header("Authorization", token)).andExpect(status().isOk());
            mvc.perform(get("/api/v1/users/me/export").header("Authorization", token))
                    .andExpect(status().isOk());

            // El rechazo de negocio, que pasa por el manejador de DomainException.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("99999")))
                    .andExpect(status().isUnprocessableContent());

            // El JSON roto, que antes lo resolvia Spring con un WARN que llevaba el token que no
            // pudo leer: un trozo de la linea. Es el unico camino por el que el cuerpo crudo
            // podia llegar a un registro.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"municipalityCode\":\"11001\",\"line\":" + LINEA + "}"))
                    .andExpect(status().isBadRequest());

            // No hay ningun camino que llegue al manejador de IllegalArgumentException: el borde
            // mide exactamente lo mismo que el dominio. Un guardado mas para que Spring registre
            // en DEBUG el cuerpo deserializado.
            mvc.perform(put(RUTA)
                            .header("Authorization", token)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(cuerpo("05001")))
                    .andExpect(status().isOk());

            mvc.perform(delete(RUTA).header("Authorization", token)).andExpect(status().isNoContent());
        } finally {
            raiz.detachAppender(capturadas);
            raiz.setLevel(nivelAnterior);
        }

        assertThat(capturadas.list).isNotEmpty();
        // Y que el canal que registra los cuerpos deserializados estuvo abierto: sin esto, un
        // `org.springframework.web: INFO` en el perfil dejaria la prueba mirando nada.
        assertThat(capturadas.list)
                .anyMatch(evento -> evento.getLevel() == Level.DEBUG
                        && evento.getLoggerName().startsWith("org.springframework.web"));

        String todo = capturadas.list.stream()
                .map(OriginAddressSecurityTest::todoElTexto)
                .collect(joining("\n"));

        assertThat(todo)
                .doesNotContain(LINEA)
                .doesNotContain(COMPLEMENTO)
                .doesNotContain(INDICACIONES)
                .doesNotContain("Nubia")
                .doesNotContain("110221");
    }

    private static String todoElTexto(ILoggingEvent evento) {
        StringBuilder texto = new StringBuilder(evento.getFormattedMessage());
        for (IThrowableProxy causa = evento.getThrowableProxy(); causa != null; causa = causa.getCause()) {
            texto.append('\n').append(causa.getClassName()).append(": ").append(causa.getMessage());
        }
        return texto.toString();
    }

    private static String cuerpo(String municipio) {
        return """
                {"municipalityCode":"%s","line":"%s","complement":"%s",
                 "instructions":"%s","postalCode":"110221"}
                """.formatted(municipio, LINEA, COMPLEMENTO, INDICACIONES);
    }

    /** Una cuenta nueva en la base, con o sin telefono y ciudad escrita, y su token. */
    private String tokenNuevo(@Nullable String telefono, @Nullable String ciudad) {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status, phone, city)
                        VALUES (:id, :correo, 'Quien vende', DATE '1990-01-01', 'ACTIVE', :telefono, :ciudad)
                        """)
                .param("id", id)
                .param("correo", id + "@ejemplo.co")
                .param("telefono", telefono)
                .param("ciudad", ciudad)
                .update();

        return "Bearer "
                + emisor.emitir(cuentaDePrueba(new UserId(id)), TokenFamilyId.nueva(), Instant.now())
                        .value();
    }

    /** Un token valido de una cuenta que no esta en la base: el caso de la cuenta cerrada. */
    private String tokenDeUnaCuentaQueNoEsta() {
        return "Bearer "
                + emisor.emitir(cuentaDePrueba(UserId.nuevo()), TokenFamilyId.nueva(), Instant.now())
                        .value();
    }

    private static User cuentaDePrueba(UserId id) {
        return User.rehidratar(
                id,
                new Email(id + "@ejemplo.co"),
                new DisplayName("Quien vende"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                Instant.now(),
                Set.of(),
                Instant.now());
    }
}
