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
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
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

        ListCatalogQuery consulta = consultaPedida();

        assertThat(consulta.texto()).isNull();
        assertThat(consulta.condiciones()).isEmpty();
        assertThat(consulta.colores()).isEmpty();
        assertThat(consulta.talla()).isNull();
        assertThat(consulta.precio().sinLimite()).isTrue();
        assertThat(consulta.orden()).isNull();
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

    /**
     * Criterio 26, la parte que se me escapaba: el 404 tiene que salir **antes** de que
     * nadie mire si lo pedido es valido.
     *
     * <p>Estos dos no pasaban por el cuerpo del metodo cuando eran un {@code @Size} sobre
     * {@code q} y un {@code Long} en los precios: la validacion de restricciones y la
     * conversion de argumentos ocurren antes de que el metodo empiece, asi que respondian
     * 400 con la bandera apagada. Y ese 400 dice que el parametro se entiende, que es tanto
     * como confirmar que la busqueda esta detras, apagada.
     */
    @Test
    void deberia_responder_404_y_no_400_a_un_texto_larguisimo_con_la_bandera_apagada() throws Exception {
        MockMvc sinBusqueda = sinLaBandera();

        sinBusqueda
                .perform(get("/api/v1/listings").param("q", "a".repeat(SearchText.LARGO_MAXIMO + 1)))
                .andExpect(status().isNotFound());
        sinBusqueda
                .perform(get("/api/v1/listings").param("minPrice", "no-es-un-numero"))
                .andExpect(status().isNotFound());
    }

    /** Y con la bandera encendida, los mismos dos son 400. */
    @Test
    void deberia_rechazar_un_precio_que_no_es_un_numero() throws Exception {
        rechaza("minPrice", "no-es-un-numero");
        rechaza("maxPrice", "50.000");
    }

    /**
     * Un cursor fabricado es 400, tambien cuando lo que trae dentro no es una cadena.
     *
     * <p>Con un objeto donde deberia ir el orden, Jackson lanza una excepcion suya —que no es
     * {@link IllegalArgumentException}— y el cursor salia como 500 con la traza entera en el
     * registro. Es una ruta publica y sin cuenta: cualquiera podia llenar el registro de
     * errores.
     */
    @Test
    void deberia_rechazar_con_400_un_cursor_cuyo_contenido_no_es_texto() throws Exception {
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"o\":{},\"k\":\"1\",\"i\":\"" + UUID.randomUUID() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/listings").param("cursor", cursor)).andExpect(status().isBadRequest());
    }

    /**
     * Y tampoco vale una puntuacion que no es un numero finito.
     *
     * <p>{@code Double.parseDouble} acepta {@code NaN}, y en PostgreSQL {@code NaN} es mayor
     * que cualquier cosa: la condicion de continuidad dejaria de acotar y el tramo volveria a
     * empezar desde el principio en cada pagina.
     */
    @Test
    void deberia_rechazar_un_cursor_de_relevancia_con_una_puntuacion_que_no_es_finita() throws Exception {
        String cursor = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"o\":\"RELEVANCE\",\"k\":\"NaN\",\"i\":\"" + UUID.randomUUID() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/listings").param("cursor", cursor).param("q", "camisa"))
                .andExpect(status().isBadRequest());
    }

    /**
     * Lo que el cliente escribio no vuelve en el mensaje del error.
     *
     * <p>No es discrecion: el manejador registra el mensaje, la ruta es publica y sin cuenta,
     * y un valor con saltos de linea dentro escribe lineas enteras en el registro del
     * servidor, que en Cloud Logging se leen como entradas aparte. {@code Enum.valueOf}
     * construia el mensaje con el texto recibido.
     */
    @Test
    void no_deberia_devolver_el_valor_recibido_al_rechazar_un_filtro() throws Exception {
        String respuesta = mvc.perform(get("/api/v1/listings").param("color", "INVENTADO_Y_LARGO"))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(respuesta).doesNotContain("INVENTADO_Y_LARGO");
    }

    /**
     * Criterio 19 y RN-084, afirmado donde de verdad se puede: sobre el JSON.
     *
     * <p>La defensa estructural es que {@link CatalogSort} tiene cuatro valores y ninguno
     * comprable. Esta es la otra mitad: que la respuesta no lleve un campo donde escribir
     * una posicion. La puntuacion existe dentro -el motor la calcula para ordenar- y no
     * puede asomar.
     */
    @Test
    void deberia_cumplir_RN_084_sin_ningun_campo_de_posicion_en_la_respuesta() throws Exception {
        when(listar.execute(any()))
                .thenReturn(CatalogPage.ultima(List.of(CatalogoDelBorde.publicada(new SellerId(UUID.randomUUID())))));

        mvc.perform(get("/api/v1/listings").param("q", "camisa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].id").exists())
                .andExpect(jsonPath("$.items[0].score").doesNotExist())
                .andExpect(jsonPath("$.items[0].relevance").doesNotExist())
                .andExpect(jsonPath("$.items[0].relevancia").doesNotExist())
                .andExpect(jsonPath("$.items[0].rank").doesNotExist())
                .andExpect(jsonPath("$.items[0].position").doesNotExist())
                .andExpect(jsonPath("$.items[0].promoted").doesNotExist())
                .andExpect(jsonPath("$.items[0].sponsored").doesNotExist());
    }

    /**
     * El cursor de la lista de favoritos no vale en el catalogo.
     *
     * <p>Los javadoc de {@code Cursores} y {@code CatalogCursors} dicen tres veces que el
     * compilador lo impide, y es cierto para los tipos de Java. Por el cable viaja una
     * cadena, y ahi quien decide es que campos exige cada mapeador: el de favoritos lleva un
     * instante y el del catalogo un orden. Eso no lo comprobaba nadie.
     */
    @Test
    void deberia_rechazar_el_cursor_de_otra_lista() throws Exception {
        String deFavoritos = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"p\":\"" + CUANDO + "\",\"i\":\"" + UUID.randomUUID() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/listings").param("cursor", deFavoritos)).andExpect(status().isBadRequest());
    }

    /** Y un cursor con un orden que no existe tampoco se resuelve al de omision. */
    @Test
    void deberia_rechazar_un_cursor_con_un_orden_inventado() throws Exception {
        String inventado = Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(("{\"o\":\"POPULARITY\",\"k\":\"1\",\"i\":\"" + UUID.randomUUID() + "\"}")
                        .getBytes(StandardCharsets.UTF_8));

        mvc.perform(get("/api/v1/listings").param("cursor", inventado)).andExpect(status().isBadRequest());
    }

    private MockMvc sinLaBandera() {
        return MockMvcBuilders.standaloneSetup(new CatalogController(listar, almacen, Optional.empty()))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
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
