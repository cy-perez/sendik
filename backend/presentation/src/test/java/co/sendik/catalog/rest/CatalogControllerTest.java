package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * El borde del catalogo publico. HU-009, criterios 3 y 4.
 *
 * <p>Lo que se comprueba aqui es lo que solo se puede comprobar con HTTP delante: la forma
 * del cuerpo que fija contrato-api.md, que el cursor viaje opaco y vuelva entero, y que un
 * limite fuera de rango se rechace en vez de recortarse.
 */
class CatalogControllerTest {

    private static final Instant CUANDO = Instant.parse("2026-08-27T15:00:00Z");

    private final ListCatalogUseCase listar = mock(ListCatalogUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/toma.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new CatalogController(listar, almacen, Optional.of(new SearchFeature())))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    /** La forma del contrato para los listados por cursor. */
    @Test
    void deberia_responder_items_con_cursor_y_si_hay_mas() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.nextCursor").doesNotExist());
    }

    /** Criterio 4: sin nada publicado la respuesta es vacia, no un error. */
    @Test
    void deberia_devolver_un_tramo_vacio_cuando_no_hay_nada_publicado_criterio_4() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    /** El cursor sale codificado y vuelve a entrar entero. */
    @Test
    void deberia_devolver_un_cursor_que_al_mandarlo_de_vuelta_dice_lo_mismo() throws Exception {
        ListingId ultima = new ListingId(UUID.randomUUID());
        CatalogCursor siguiente = CatalogCursor.porFecha(CUANDO, ultima);

        when(listar.execute(any())).thenReturn(new CatalogPage(List.of(), siguiente, true));

        String texto = CatalogCursors.texto(siguiente);

        mvc.perform(get("/api/v1/listings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.nextCursor").value(texto));

        mvc.perform(get("/api/v1/listings").param("cursor", texto)).andExpect(status().isOk());

        ArgumentCaptor<ListCatalogQuery> consulta = ArgumentCaptor.forClass(ListCatalogQuery.class);
        verify(listar, org.mockito.Mockito.atLeastOnce()).execute(consulta.capture());

        assertQueLlevaElCursor(consulta.getAllValues().getLast(), siguiente);
    }

    /**
     * Criterio 3: un limite por encima del tope se rechaza con 400.
     *
     * <p>No se recorta en silencio: quien pide 500 y recibe 50 sin que nadie se lo diga
     * cree que ya tiene el catalogo entero.
     *
     * <p><strong>Lo protegen dos guardas y esta prueba ejercita la de dentro.</strong> En
     * la aplicacion en marcha salta primero el {@code @Max} del parametro; en un
     * {@code standaloneSetup} no hay procesador de validacion de metodos, asi que la
     * peticion llega al controlador y la rechaza {@code ListCatalogQuery}. Que las dos
     * respondan lo mismo es justo lo que hace que la de dentro no sea redundante: protege
     * a cualquiera que use el caso de uso, tambien desde otro borde.
     */
    @Test
    void deberia_rechazar_un_limite_por_encima_del_tope_criterio_3() throws Exception {
        mvc.perform(get("/api/v1/listings").param("limit", "500")).andExpect(status().isBadRequest());
    }

    /** Criterio 4 del cursor: uno inventado es un 400 y no el primer tramo. */
    @Test
    void deberia_rechazar_un_cursor_que_no_descifra() throws Exception {
        mvc.perform(get("/api/v1/listings").param("cursor", "esto-no-es-un-cursor"))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------- la busqueda
    //
    // HU-014. Lo que se prueba aqui es lo que decide el borde: que cada parametro llega
    // convertido a la consulta, que lo que no se entiende es 400 y no se ignora, y que
    // con la bandera apagada buscar no existe.

    /** Criterio 15: el texto y los filtros llegan a la consulta tal como se pidieron. */
    @Test
    void deberia_llevar_el_texto_y_los_filtros_a_la_consulta() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings")
                        .param("q", "camisa de lino")
                        .param("condition", "NEW", "LIKE_NEW")
                        .param("color", "BLUE")
                        .param("sizeSystem", "ALPHA")
                        .param("size", "M")
                        .param("minPrice", "50000")
                        .param("maxPrice", "100000")
                        .param("sort", "price,asc"))
                .andExpect(status().isOk());

        ListCatalogQuery consulta = consultaPedida();

        assertThat(consulta.texto()).isNotNull();
        assertThat(consulta.texto().value()).isEqualTo("camisa de lino");
        assertThat(consulta.condiciones()).containsExactlyInAnyOrder(Condition.NEW, Condition.LIKE_NEW);
        assertThat(consulta.colores()).containsExactly(Color.BLUE);
        assertThat(consulta.talla()).isEqualTo(new Size(SizeSystem.ALPHA, "M"));
        assertThat(consulta.precio().minimo()).isEqualTo(Money.dePesos(50_000));
        assertThat(consulta.precio().maximo()).isEqualTo(Money.dePesos(100_000));
        assertThat(consulta.orden()).isEqualTo(CatalogSort.PRICE_ASC);
    }

    /** Criterio 8: sin nada puesto, la consulta es la del catalogo de siempre. */
    @Test
    void deberia_pedir_el_catalogo_tal_cual_cuando_no_se_busca_nada_criterio_8() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings")).andExpect(status().isOk());

        assertThat(consultaPedida().sinFiltros()).isTrue();
    }

    /** Los cuatro ordenes de RN-088, con los nombres del contrato. */
    @Test
    void deberia_admitir_los_cuatro_ordenes_de_RN_088() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        assertThat(ordenPedido("relevance", "camisa")).isEqualTo(CatalogSort.RELEVANCE);
        assertThat(ordenPedido("publishedAt,desc", null)).isEqualTo(CatalogSort.NEWEST);
        assertThat(ordenPedido("price,asc", null)).isEqualTo(CatalogSort.PRICE_ASC);
        assertThat(ordenPedido("price,desc", null)).isEqualTo(CatalogSort.PRICE_DESC);
    }

    /**
     * Criterio 23. Nada se ignora en silencio.
     *
     * <p>Es lo que {@code contrato-api.md} exige para los filtros: «un filtro mal escrito
     * que no filtra es peor que un error». Quien escribe {@code color=AZUL} en vez de
     * {@code BLUE} tiene que enterarse, no recibir el catalogo entero.
     */
    @Test
    void deberia_rechazar_con_400_lo_que_no_entiende_criterio_23() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        rechaza("color", "AZUL");
        rechaza("condition", "SEMINUEVA");
        rechaza("sizeSystem", "INVENTADO");
        rechaza("sort", "price,arriba");
        rechaza("sort", "favorites,desc");
        // Una talla que no existe dentro de su sistema tampoco pasa: la M no es numerica.
        mvc.perform(get("/api/v1/listings").param("sizeSystem", "NUMERIC_CO").param("size", "M"))
                .andExpect(status().isBadRequest());
    }

    /** RN-087: la talla necesita su sistema, y el sistema una talla. */
    @Test
    void deberia_rechazar_la_talla_sin_su_sistema_RN_087() throws Exception {
        rechaza("size", "M");
        rechaza("sizeSystem", "ALPHA");
    }

    /** Caso borde: el minimo por encima del maximo es 400, no un vacio. */
    @Test
    void deberia_rechazar_un_rango_de_precio_del_reves() throws Exception {
        mvc.perform(get("/api/v1/listings").param("minPrice", "100000").param("maxPrice", "50000"))
                .andExpect(status().isBadRequest());
    }

    /** Caso borde: el peso no tiene decimales ni negativos (RN-029). */
    @Test
    void deberia_rechazar_un_precio_negativo_o_con_decimales() throws Exception {
        rechaza("minPrice", "-1");
        rechaza("minPrice", "50000.5");
    }

    /** Caso borde: por encima del tope se responde 400 y no se recorta. */
    @Test
    void deberia_rechazar_un_texto_mas_largo_que_el_tope() throws Exception {
        rechaza("q", "a".repeat(SearchText.LARGO_MAXIMO + 1));
    }

    /** Caso borde: un texto de pura puntuacion es el catalogo, no un vacio. */
    @Test
    void deberia_tratar_un_texto_sin_letras_como_si_no_hubiera_texto() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        mvc.perform(get("/api/v1/listings").param("q", "  ??? ")).andExpect(status().isOk());

        assertThat(consultaPedida().texto()).isNull();
    }

    /**
     * Criterio 22: el cursor solo vale dentro del orden con el que nacio.
     *
     * <p>El cursor viaja opaco, asi que el 400 no lo puede dar el cliente por su cuenta: lo
     * da el servidor al comparar el orden que trae dentro con el que se esta pidiendo.
     */
    @Test
    void deberia_rechazar_un_cursor_nacido_con_otro_orden_criterio_22() throws Exception {
        String delCatalogo = CatalogCursors.texto(CatalogCursor.porFecha(CUANDO, new ListingId(UUID.randomUUID())));

        mvc.perform(get("/api/v1/listings").param("cursor", delCatalogo).param("sort", "price,asc"))
                .andExpect(status().isBadRequest());
    }

    /** El cursor de precio va y vuelve entero, con su orden dentro. */
    @Test
    void deberia_devolver_un_cursor_de_precio_que_al_volver_dice_lo_mismo() throws Exception {
        CatalogCursor siguiente =
                CatalogCursor.porPrecio(CatalogSort.PRICE_ASC, Money.dePesos(90_000), new ListingId(UUID.randomUUID()));

        when(listar.execute(any())).thenReturn(new CatalogPage(List.of(), siguiente, true));

        String texto = CatalogCursors.texto(siguiente);

        mvc.perform(get("/api/v1/listings").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextCursor").value(texto));

        mvc.perform(get("/api/v1/listings").param("sort", "price,asc").param("cursor", texto))
                .andExpect(status().isOk());

        assertThat(consultaPedida().desde()).isEqualTo(siguiente);
    }

    /**
     * Criterio 26. Con la bandera apagada, buscar no existe: 404 y no 403.
     *
     * <p>El catalogo desnudo sigue respondiendo, que es lo que distingue esta bandera de
     * las otras tres: aqui no desaparece la ruta, desaparece la capacidad de buscar en ella.
     */
    @Test
    void no_deberia_existir_la_busqueda_con_la_bandera_apagada_criterio_26() throws Exception {
        when(listar.execute(any())).thenReturn(CatalogPage.ultima(List.of()));

        MockMvc sinBusqueda = MockMvcBuilders.standaloneSetup(new CatalogController(listar, almacen, Optional.empty()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        sinBusqueda.perform(get("/api/v1/listings")).andExpect(status().isOk());
        sinBusqueda.perform(get("/api/v1/listings").param("q", "camisa")).andExpect(status().isNotFound());
        sinBusqueda.perform(get("/api/v1/listings").param("color", "BLUE")).andExpect(status().isNotFound());
        sinBusqueda.perform(get("/api/v1/listings").param("sort", "price,asc")).andExpect(status().isNotFound());
        sinBusqueda.perform(get("/api/v1/listings").param("minPrice", "50000")).andExpect(status().isNotFound());
    }

    /**
     * Y responde 404 antes de mirar si lo que se pidio es valido.
     *
     * <p>Un 400 ahi diria que el parametro se entiende, que es tanto como confirmar que la
     * busqueda esta detras, apagada.
     */
    @Test
    void deberia_responder_404_y_no_400_a_un_filtro_invalido_con_la_bandera_apagada() throws Exception {
        MockMvc sinBusqueda = MockMvcBuilders.standaloneSetup(new CatalogController(listar, almacen, Optional.empty()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        sinBusqueda.perform(get("/api/v1/listings").param("color", "AZUL")).andExpect(status().isNotFound());
    }

    // --- apoyo de la busqueda ------------------------------------------------

    private void rechaza(String parametro, String valor) throws Exception {
        mvc.perform(get("/api/v1/listings").param(parametro, valor)).andExpect(status().isBadRequest());
    }

    private @Nullable CatalogSort ordenPedido(String sort, @Nullable String q) throws Exception {
        var peticion = get("/api/v1/listings").param("sort", sort);
        if (q != null) {
            peticion = peticion.param("q", q);
        }
        mvc.perform(peticion).andExpect(status().isOk());

        return consultaPedida().orden();
    }

    /** La ultima consulta que recibio el caso de uso. */
    private ListCatalogQuery consultaPedida() {
        ArgumentCaptor<ListCatalogQuery> consulta = ArgumentCaptor.forClass(ListCatalogQuery.class);
        verify(listar, org.mockito.Mockito.atLeastOnce()).execute(consulta.capture());

        return consulta.getAllValues().getLast();
    }

    private static void assertQueLlevaElCursor(ListCatalogQuery consulta, CatalogCursor esperado) {
        org.assertj.core.api.Assertions.assertThat(consulta.desde()).isEqualTo(esperado);
    }
}
