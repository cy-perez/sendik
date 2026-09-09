package co.sendik.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.dto.ListSellerCatalogQuery;
import co.sendik.catalog.exception.UnknownCategoryException;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El catalogo publico. HU-009.
 *
 * <p>Lo que se prueba aqui es lo que decide el caso de uso y no el SQL: que RN-068 se
 * aplica, que una familia trae lo de sus hijas, que el cursor sale del ultimo entregado y
 * que una categoria retirada no se confunde con una vacia. Que la consulta ordene bien
 * contra PostgreSQL lo prueba {@code CatalogPersistenceTest}, que es donde se puede.
 */
class CatalogoPublicoTest {

    private static final Instant AHORA = Instant.parse("2026-08-27T15:00:00Z");

    private static final CategoryId FAMILIA = new CategoryId(UUID.randomUUID());
    private static final CategoryId CAMISAS = new CategoryId(UUID.randomUUID());
    private static final CategoryId JEANS = new CategoryId(UUID.randomUUID());

    private CatalogoEnMemoria.Publicaciones publicaciones;
    private CatalogoEnMemoria.Arbol arbol;
    private ListCatalogUseCase catalogo;
    private ListSellerCatalogUseCase escaparate;

    @BeforeEach
    void montar() {
        publicaciones = new CatalogoEnMemoria.Publicaciones();
        arbol = new CatalogoEnMemoria.Arbol();

        arbol.agregar(familia(FAMILIA));
        arbol.agregar(hoja(CAMISAS, FAMILIA));
        arbol.agregar(hoja(JEANS, FAMILIA));

        catalogo = new ListCatalogUseCase(new CatalogoEnMemoria.Motor(publicaciones), arbol);
        escaparate = new ListSellerCatalogUseCase(publicaciones);
    }

    /** RN-068: de los siete estados, uno. */
    @Test
    void deberia_mostrar_solo_lo_publicado_RN_068() {
        publicar(CAMISAS, AHORA);
        guardar(borrador(CAMISAS));
        guardar(enRevision(CAMISAS));

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(null, null, 24));

        assertThat(tramo.items()).hasSize(1);
        assertThat(tramo.items().getFirst().status()).isEqualTo(ListingStatus.PUBLISHED);
    }

    /**
     * El dueno tampoco ve lo suyo aqui.
     *
     * <p>No hay parametro «quien pregunta» en la consulta, asi que la prueba lo demuestra
     * por la unica via que hay: sus borradores no salen ni siquiera cuando el listado se
     * filtra por el.
     */
    @Test
    void no_deberia_ensenar_al_dueno_sus_propios_borradores_RN_068() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        guardar(borrador(CAMISAS, vendedor));
        Listing viva = publicar(CAMISAS, AHORA, vendedor);

        CatalogPage suyo = escaparate.execute(new ListSellerCatalogQuery(vendedor, null, 24));

        assertThat(suyo.items()).extracting(Listing::id).containsExactly(viva.id());
    }

    /** Lo mas reciente primero. */
    @Test
    void deberia_ordenar_de_lo_recien_publicado_a_lo_mas_viejo() {
        Listing vieja = publicar(CAMISAS, AHORA.minusSeconds(3600));
        Listing nueva = publicar(CAMISAS, AHORA);

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(null, null, 24));

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(nueva.id(), vieja.id());
    }

    /**
     * Criterio 3. El tramo lleno no significa que haya mas.
     *
     * <p>Es el fallo clasico de la paginacion por cursor: con exactamente tantas
     * publicaciones como el limite, deducir «hay mas» de que el tramo venga lleno entrega
     * un cursor que lleva a un tramo vacio.
     */
    @Test
    void no_deberia_prometer_mas_cuando_el_catalogo_cabe_justo_en_el_tramo() {
        publicar(CAMISAS, AHORA);
        publicar(CAMISAS, AHORA.minusSeconds(1));

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(null, null, 2));

        assertThat(tramo.items()).hasSize(2);
        assertThat(tramo.hayMas()).isFalse();
        assertThat(tramo.siguiente()).isNull();
    }

    /** Criterio 3: el segundo tramo empieza donde termino el primero, sin repetir. */
    @Test
    void deberia_seguir_por_donde_iba_sin_repetir_ni_saltarse_nada() {
        List<Listing> todas = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            todas.add(publicar(CAMISAS, AHORA.minusSeconds(i)));
        }

        CatalogPage primera = catalogo.execute(ListCatalogQuery.todo(null, null, 2));
        assertThat(primera.hayMas()).isTrue();

        CatalogPage segunda = catalogo.execute(ListCatalogQuery.todo(null, primera.siguiente(), 2));
        CatalogPage tercera = catalogo.execute(ListCatalogQuery.todo(null, segunda.siguiente(), 2));

        List<ListingId> recorridas = new ArrayList<>();
        primera.items().forEach(p -> recorridas.add(p.id()));
        segunda.items().forEach(p -> recorridas.add(p.id()));
        tercera.items().forEach(p -> recorridas.add(p.id()));

        assertThat(recorridas).doesNotHaveDuplicates().hasSize(5);
        assertThat(tercera.hayMas()).isFalse();
    }

    /**
     * Dos publicadas en el mismo instante.
     *
     * <p>Con un reloj fijo es la norma, y en produccion es posible. Si el cursor solo
     * llevara la fecha, una de las dos se perderia o se repetiria para siempre.
     */
    @Test
    void deberia_recorrer_dos_publicadas_en_el_mismo_instante_sin_perder_ninguna() {
        Listing una = publicar(CAMISAS, AHORA);
        Listing otra = publicar(CAMISAS, AHORA);

        CatalogPage primera = catalogo.execute(ListCatalogQuery.todo(null, null, 1));
        CatalogPage segunda = catalogo.execute(ListCatalogQuery.todo(null, primera.siguiente(), 1));

        assertThat(primera.items()).hasSize(1);
        assertThat(segunda.items()).hasSize(1);
        assertThat(List.of(
                        primera.items().getFirst().id(),
                        segunda.items().getFirst().id()))
                .containsExactlyInAnyOrder(una.id(), otra.id());
    }

    /** Criterio 8: una categoria filtra. */
    @Test
    void deberia_traer_solo_lo_de_la_categoria_elegida() {
        Listing camisa = publicar(CAMISAS, AHORA);
        publicar(JEANS, AHORA.minusSeconds(1));

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(CAMISAS, null, 24));

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(camisa.id());
    }

    /** Criterio 10: no se publica en una familia, sino en una categoria suya. */
    @Test
    void deberia_traer_lo_de_todas_las_hijas_al_abrir_una_familia() {
        Listing camisa = publicar(CAMISAS, AHORA);
        Listing jean = publicar(JEANS, AHORA.minusSeconds(1));

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(FAMILIA, null, 24));

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(camisa.id(), jean.id());
    }

    /**
     * Criterio 9. Una categoria retirada no devuelve un listado vacio.
     *
     * <p>El vacio se leeria como «existe y no tiene nada», que es otra cosa y ademas
     * mentira. Sale como 404.
     */
    @Test
    void deberia_negar_una_categoria_que_no_esta_en_el_arbol_criterio_9() {
        CategoryId inventada = new CategoryId(UUID.randomUUID());

        assertThatThrownBy(() -> catalogo.execute(ListCatalogQuery.todo(inventada, null, 24)))
                .isInstanceOf(UnknownCategoryException.class);
    }

    /** Criterio 3: el tope no se recorta en silencio. */
    @Test
    void deberia_rechazar_un_limite_por_encima_del_tope() {
        assertThatThrownBy(() -> ListCatalogQuery.todo(null, null, ListCatalogQuery.LIMITE_MAXIMO + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /** Criterio 20: un vendedor sin nada publicado es un vacio, no un error. */
    @Test
    void deberia_devolver_vacio_el_escaparate_de_quien_no_tiene_nada_publicado() {
        CatalogPage tramo = escaparate.execute(new ListSellerCatalogQuery(new SellerId(UUID.randomUUID()), null, 24));

        assertThat(tramo.items()).isEmpty();
        assertThat(tramo.hayMas()).isFalse();
    }

    /**
     * El cursor del catalogo no vale en el escaparate si nacio con otro orden.
     *
     * <p>Desde HU-014 el cursor lleva dentro su orden, y el escaparate solo tiene uno. Uno
     * de precio -copiado de una busqueda y pegado en el perfil de alguien- llegaba a una
     * consulta que ordena por fecha y le pedia una fecha que ese cursor no lleva: reventaba
     * con un 500. El criterio 22 dice 400, y eso es lo que sale de rechazarlo aqui.
     */
    @Test
    void deberia_rechazar_en_el_escaparate_un_cursor_nacido_con_otro_orden_criterio_22() {
        CatalogCursor dePrecio =
                CatalogCursor.porPrecio(CatalogSort.PRICE_ASC, Money.dePesos(90_000), ListingId.nuevo());

        assertThatThrownBy(() -> new ListSellerCatalogQuery(new SellerId(UUID.randomUUID()), dePrecio, 24))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRICE_ASC");
    }

    /** Y el del propio escaparate si vale: es el mismo orden que el del catalogo. */
    @Test
    void deberia_admitir_en_el_escaparate_el_cursor_del_catalogo() {
        CatalogCursor deFecha = CatalogCursor.porFecha(AHORA, ListingId.nuevo());

        assertThat(new ListSellerCatalogQuery(new SellerId(UUID.randomUUID()), deFecha, 24).desde())
                .isEqualTo(deFecha);
    }

    /**
     * El escaparate paginado, que hasta HU-014 no probaba nadie.
     *
     * <p>Las dos llamadas que habia pasaban {@code desde = null}, asi que la rama que sella
     * el cursor del escaparate no se ejercitaba en ningun punto de la pila. Y el formato del
     * cursor acaba de cambiar debajo de ella.
     */
    @Test
    void deberia_paginar_el_escaparate_con_su_propio_cursor() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        for (int i = 0; i < 3; i++) {
            publicar(CAMISAS, AHORA.minusSeconds(i), vendedor);
        }

        CatalogPage primera = escaparate.execute(new ListSellerCatalogQuery(vendedor, null, 2));
        assertThat(primera.hayMas()).isTrue();
        assertThat(primera.siguiente()).isNotNull();
        assertThat(primera.siguiente().orden()).isEqualTo(CatalogSort.NEWEST);

        CatalogPage segunda = escaparate.execute(new ListSellerCatalogQuery(vendedor, primera.siguiente(), 2));

        assertThat(segunda.items()).hasSize(1);
        assertThat(segunda.hayMas()).isFalse();
        assertThat(primera.items())
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(
                        segunda.items().stream().map(Listing::id).toList());
    }

    // --- apoyo ---------------------------------------------------------------

    private Listing publicar(CategoryId categoria, Instant cuando) {
        return publicar(categoria, cuando, new SellerId(UUID.randomUUID()));
    }

    private Listing publicar(CategoryId categoria, Instant cuando, SellerId vendedor) {
        Listing aprobada = conTomas(borrador(categoria, vendedor))
                .enviarARevision(cuando)
                .aprobar(new ModeratorId(UUID.randomUUID()), cuando);

        return guardar(aprobada);
    }

    private Listing guardar(Listing publicacion) {
        return publicaciones.guardar(publicacion);
    }

    private Listing enRevision(CategoryId categoria) {
        return conTomas(borrador(categoria)).enviarARevision(AHORA);
    }

    private Listing borrador(CategoryId categoria) {
        return borrador(categoria, new SellerId(UUID.randomUUID()));
    }

    private Listing borrador(CategoryId categoria, SellerId vendedor) {
        Map<MeasurementKind, BigDecimal> medidas = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> medidas.put(medida, new BigDecimal("50.0")));

        Product producto = new Product(
                ProductId.nuevo(),
                vendedor,
                categoria,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(medidas),
                Color.BEIGE,
                Money.dePesos(185_000),
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);

        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    private static Listing conTomas(Listing publicacion) {
        Listing resultado = publicacion;
        for (int i = 0; i < ProductImage.TOMAS_DE_LA_SECUENCIA; i++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            i,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    /** Una familia no declara talla ni medidas: eso es de sus hojas. */
    private static Category familia(CategoryId id) {
        return new Category(id, "familia-" + id.value(), null, Set.of(), null, true, true);
    }

    private static Category hoja(CategoryId id, CategoryId padre) {
        return new Category(
                id, "hoja-" + id.value(), padre, Set.of(SizeSystem.ALPHA), MeasurementGroup.TOP, true, true);
    }
}
