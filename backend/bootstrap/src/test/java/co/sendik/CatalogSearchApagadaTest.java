package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.shared.money.Money;
import java.util.Objects;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Criterio 26 por el lado apagado, con el contexto entero. HU-014.
 *
 * <p><strong>Es la mitad que faltaba.</strong> {@code CatalogLimitTest} cubre el lado
 * encendido y {@code CatalogControllerTest} construye el controlador a mano con
 * {@code Optional.empty()}, que demuestra la logica y no la bandera: un nombre de propiedad
 * mal escrito dejaria la busqueda encendida en produccion con toda la suite en verde. Aqui
 * el bean marcador lo crea Spring o no lo crea, segun la propiedad.
 *
 * <p>Y afirma el <strong>cuerpo</strong> y no solo el estado. {@code SearchFeature} promete
 * un 404 «byte por byte igual» al que Spring devuelve cuando un controlador no existe; sin
 * mirar el codigo de error, el dia que {@code NoResourceFoundException} se mapee distinto la
 * promesa se rompe en silencio y la busqueda apagada empieza a distinguirse de una ruta que
 * no existe.
 */
@SpringBootTest(properties = {"sendik.features.catalog=true", "sendik.features.search=false"})
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CatalogSearchApagadaTest {

    private final WebApplicationContext contexto;

    private MockMvc mvc;

    CatalogSearchApagadaTest(WebApplicationContext contexto) {
        this.contexto = contexto;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    /** El catalogo de HU-009 sigue respondiendo: lo que desaparece no es la ruta. */
    @Test
    void deberia_seguir_sirviendo_el_catalogo_con_la_busqueda_apagada() throws Exception {
        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    /** Y buscar no existe: 404 con el codigo de una ruta que no esta. */
    @Test
    void deberia_responder_404_con_COMMON_NOT_FOUND_al_buscar() throws Exception {
        mvc.perform(get("/api/v1/listings").param("q", "camisa"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /**
     * Un filtro invalido tampoco se entiende: 404 y no el 400 que lo entenderia.
     *
     * <p>Es lo que obliga a comprobar la bandera antes de convertir nada. Con la conversion
     * primero, este color inventado saldria como 400 y ese 400 diria que el parametro se
     * entiende, que es tanto como confirmar que la busqueda esta detras, apagada.
     */
    @Test
    void deberia_responder_404_y_no_400_a_un_filtro_invalido() throws Exception {
        mvc.perform(get("/api/v1/listings").param("color", "AZUL_INVENTADO"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /**
     * Y el cursor, que es el unico parametro que pide buscar sin decirlo.
     *
     * <p>Un cursor nacido bajo un orden de HU-014 sigue siendo valido despues de apagar la
     * bandera —lo tiene quien haya paginado antes, o quien comparta el enlace—. Sin mirarlo,
     * {@code ListCatalogQuery} lo rechazaba con «el cursor nacio con el orden PRICE_ASC y se
     * esta pidiendo NEWEST»: un 400 que nombra un orden que, con la bandera apagada, no
     * deberia existir.
     */
    @Test
    void deberia_responder_404_y_no_400_a_un_cursor_de_otro_orden() throws Exception {
        String cursorDePrecio = Objects.requireNonNull(CatalogCursors.texto(
                CatalogCursor.porPrecio(CatalogSort.PRICE_ASC, Money.dePesos(90_000), ListingId.nuevo())));

        mvc.perform(get("/api/v1/listings").param("cursor", cursorDePrecio))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    /** El cursor del catalogo de siempre sigue valiendo: lo que se cierra es el otro. */
    @Test
    void deberia_seguir_admitiendo_el_cursor_por_fecha() throws Exception {
        String cursorDeFecha = Objects.requireNonNull(
                CatalogCursors.texto(CatalogCursor.porFecha(java.time.Instant.now(), ListingId.nuevo())));

        mvc.perform(get("/api/v1/listings").param("cursor", cursorDeFecha)).andExpect(status().isOk());
    }
}
