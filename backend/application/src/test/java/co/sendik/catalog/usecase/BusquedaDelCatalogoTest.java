package co.sendik.catalog.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.PriceRange;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SearchText;
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
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * La busqueda y los filtros. HU-014.
 *
 * <p>Lo que se prueba aqui es lo que decide el caso de uso y no el SQL: que RN-081 se aplica
 * por este segundo camino, que los filtros se exigen todos, que el orden por omision es el
 * de RN-088 y que el cursor lleva el orden con el que nacio. <strong>Que el texto encuentre
 * lo mismo con tildes y sin ellas, que lematice y que {@code ts_rank} ordene bien se prueba
 * contra PostgreSQL</strong>, que es donde se puede: el doble filtra igual pero no lematiza
 * ni puntua como el motor.
 */
class BusquedaDelCatalogoTest {

    private static final Instant AHORA = Instant.parse("2026-09-09T15:00:00Z");

    private static final CategoryId FAMILIA = new CategoryId(UUID.randomUUID());
    private static final CategoryId CAMISAS = new CategoryId(UUID.randomUUID());
    private static final CategoryId JEANS = new CategoryId(UUID.randomUUID());

    private CatalogoEnMemoria.Publicaciones publicaciones;
    private ListCatalogUseCase catalogo;

    @BeforeEach
    void montar() {
        publicaciones = new CatalogoEnMemoria.Publicaciones();
        CatalogoEnMemoria.Arbol arbol = new CatalogoEnMemoria.Arbol();

        arbol.agregar(familia(FAMILIA));
        arbol.agregar(hoja(CAMISAS, FAMILIA));
        arbol.agregar(hoja(JEANS, FAMILIA));

        catalogo = new ListCatalogUseCase(new CatalogoEnMemoria.Motor(publicaciones), arbol);
    }

    // --- el texto ------------------------------------------------------------

    @Test
    void deberia_cumplir_RN_082_encontrando_por_el_titulo() {
        Listing camisa = una().titulo("Camisa de lino color hueso").publicar();
        una().titulo("Jean recto azul oscuro").publicar();

        assertThat(buscar("camisa")).extracting(Listing::id).containsExactly(camisa.id());
    }

    @Test
    void deberia_cumplir_RN_082_encontrando_por_la_marca() {
        Listing conMarca = una().titulo("Jean recto azul oscuro").marca("Levis").publicar();
        una().titulo("Camisa de lino color hueso").publicar();

        assertThat(buscar("levis")).extracting(Listing::id).containsExactly(conMarca.id());
    }

    /**
     * La consecuencia aceptada de RN-082, escrita sobre una ausencia a proposito: tiene que
     * ser visible el dia que alguien cambie la regla.
     */
    @Test
    void deberia_cumplir_RN_082_no_encontrando_por_la_descripcion() {
        una().titulo("Camisa de lino color hueso")
                .descripcion("Perfecta para un matrimonio en tierra caliente.")
                .publicar();

        assertThat(buscar("matrimonio")).isEmpty();
    }

    @Test
    void deberia_cumplir_RN_081_dejando_fuera_lo_que_no_esta_publicado() {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        publicaciones.guardar(
                una().vendedor(vendedor).titulo("Camisa de lino color hueso").borrador());

        assertThat(buscar("camisa")).isEmpty();
    }

    /** Una publicacion sin marca no queda excluida de nada: la marca es opcional. */
    @Test
    void deberia_encontrar_una_publicacion_sin_marca() {
        Listing sinMarca = una().titulo("Camisa de lino color hueso").publicar();

        assertThat(buscar("lino")).extracting(Listing::id).containsExactly(sinMarca.id());
    }

    /** Criterio 8: buscar nada no es una pantalla distinta. */
    @Test
    void deberia_devolver_el_catalogo_entero_cuando_no_hay_texto_ni_filtros_criterio_8() {
        Listing vieja = una().publicadaEn(AHORA.minusSeconds(60)).publicar();
        Listing nueva = una().publicadaEn(AHORA).publicar();

        CatalogPage tramo = catalogo.execute(ListCatalogQuery.todo(null, null, 24));

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(nueva.id(), vieja.id());
    }

    /** Criterio 20: el vacio es un vacio y no un tramo con cursor a ninguna parte. */
    @Test
    void deberia_devolver_vacio_cuando_nada_casa_criterio_20() {
        una().titulo("Camisa de lino color hueso").publicar();

        CatalogPage tramo = catalogo.execute(consulta().texto("bicicleta").arma());

        assertThat(tramo.items()).isEmpty();
        assertThat(tramo.hayMas()).isFalse();
        assertThat(tramo.siguiente()).isNull();
    }

    // --- los filtros ---------------------------------------------------------

    @Test
    void deberia_filtrar_por_condicion_criterio_10() {
        Listing nueva = una().condicion(Condition.NEW).publicar();
        una().condicion(Condition.WITH_FLAWS).publicar();

        CatalogPage tramo =
                catalogo.execute(consulta().condiciones(Condition.NEW).arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(nueva.id());
    }

    /** Criterio 14: dentro de un mismo filtro con varios valores basta con uno. */
    @Test
    void deberia_admitir_varios_valores_en_un_mismo_filtro_criterio_14() {
        Listing azul = una().color(Color.BLUE).publicar();
        Listing verde = una().color(Color.GREEN).publicar();
        una().color(Color.RED).publicar();

        CatalogPage tramo =
                catalogo.execute(consulta().colores(Color.BLUE, Color.GREEN).arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactlyInAnyOrder(azul.id(), verde.id());
    }

    /** Criterio 14: y entre filtros distintos se exigen todos. */
    @Test
    void deberia_exigir_todos_los_filtros_a_la_vez_criterio_14() {
        Listing azulEmeM = una().color(Color.BLUE).talla("M").publicar();
        una().color(Color.BLUE).talla("L").publicar();
        una().color(Color.RED).talla("M").publicar();

        CatalogPage tramo =
                catalogo.execute(consulta().colores(Color.BLUE).talla("M").arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(azulEmeM.id());
    }

    /** RN-087: una M por letra y una 38 numerica no son comparables y no se mezclan. */
    @Test
    void deberia_cumplir_RN_087_filtrando_la_talla_dentro_de_su_sistema() {
        Listing porLetra = una().talla(SizeSystem.ALPHA, "M").publicar();
        una().talla(SizeSystem.NUMERIC_CO, "10").publicar();

        CatalogPage porLetraSolo =
                catalogo.execute(consulta().talla(SizeSystem.ALPHA, "M").arma());
        CatalogPage porNumero =
                catalogo.execute(consulta().talla(SizeSystem.NUMERIC_CO, "12").arma());

        assertThat(porLetraSolo.items()).extracting(Listing::id).containsExactly(porLetra.id());
        assertThat(porNumero.items()).isEmpty();
    }

    /** Criterio 13: los dos extremos entran. */
    @Test
    void deberia_incluir_los_dos_extremos_del_rango_de_precio_criterio_13() {
        Listing enElMinimo = una().precio(50_000).publicar();
        Listing enElMaximo = una().precio(100_000).publicar();
        una().precio(100_001).publicar();
        una().precio(49_999).publicar();

        CatalogPage tramo = catalogo.execute(
                consulta().precio(50_000, 100_000).orden(CatalogSort.PRICE_ASC).arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(enElMinimo.id(), enElMaximo.id());
    }

    @Test
    void deberia_filtrar_por_categoria_junto_con_el_texto() {
        una().categoria(JEANS).titulo("Camisa de lino color hueso").publicar();
        Listing enCamisas =
                una().categoria(CAMISAS).titulo("Camisa de lino color hueso").publicar();

        CatalogPage tramo =
                catalogo.execute(consulta().texto("camisa").categoria(CAMISAS).arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(enCamisas.id());
    }

    // --- el orden ------------------------------------------------------------

    /** Criterio 17: sin texto y sin orden pedido, el orden del catalogo. */
    @Test
    void deberia_cumplir_RN_088_ordenando_por_lo_mas_reciente_cuando_no_hay_texto() {
        Listing vieja = una().publicadaEn(AHORA.minusSeconds(60)).publicar();
        Listing nueva = una().publicadaEn(AHORA).publicar();

        CatalogPage tramo = catalogo.execute(consulta().arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(nueva.id(), vieja.id());
    }

    /** Criterio 16: con texto y sin orden pedido, relevancia. */
    @Test
    void deberia_cumplir_RN_088_ordenando_por_relevancia_cuando_hay_texto() {
        // Las dos casan -«camisa» y «lino» estan en las dos, entre titulo y marca- pero la
        // que las lleva las dos en el titulo puntua mas. Lo que se prueba aqui es que el
        // orden aplicado es la relevancia y no la fecha: la que gana es la mas vieja.
        Listing menos = una().titulo("Camisa blanca sencilla")
                .marca("Lino")
                .publicadaEn(AHORA)
                .publicar();
        Listing mas = una().titulo("Camisa de lino color hueso")
                .publicadaEn(AHORA.minusSeconds(60))
                .publicar();

        CatalogPage tramo = catalogo.execute(consulta().texto("camisa lino").arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(mas.id(), menos.id());
    }

    @Test
    void deberia_ordenar_por_precio_en_las_dos_direcciones_criterio_18() {
        Listing barata = una().precio(50_000).publicar();
        Listing cara = una().precio(200_000).publicar();

        CatalogPage ascendente =
                catalogo.execute(consulta().orden(CatalogSort.PRICE_ASC).arma());
        CatalogPage descendente =
                catalogo.execute(consulta().orden(CatalogSort.PRICE_DESC).arma());

        assertThat(ascendente.items()).extracting(Listing::id).containsExactly(barata.id(), cara.id());
        assertThat(descendente.items()).extracting(Listing::id).containsExactly(cara.id(), barata.id());
    }

    /**
     * Pedir relevancia sin texto no es un error del cliente: es lo que queda al borrar la
     * caja con el orden ya elegido. Se resuelve como el catalogo.
     */
    @Test
    void deberia_degradar_la_relevancia_a_lo_mas_reciente_cuando_se_borra_el_texto() {
        Listing vieja = una().publicadaEn(AHORA.minusSeconds(60)).publicar();
        Listing nueva = una().publicadaEn(AHORA).publicar();

        CatalogPage tramo =
                catalogo.execute(consulta().orden(CatalogSort.RELEVANCE).arma());

        assertThat(tramo.items()).extracting(Listing::id).containsExactly(nueva.id(), vieja.id());
    }

    // --- el cursor -----------------------------------------------------------

    /** Criterio 21: paginar por precio no repite ni se salta ninguna, tambien con empates. */
    @Test
    void deberia_paginar_por_precio_sin_repetir_ni_saltarse_nada_criterio_21() {
        una().precio(50_000).publicar();
        una().precio(50_000).publicar();
        una().precio(80_000).publicar();
        una().precio(120_000).publicar();
        una().precio(120_000).publicar();

        List<ListingId> recorridas = new ArrayList<>();
        CatalogCursor desde = null;

        for (int tramo = 0; tramo < 3; tramo++) {
            CatalogPage pagina = catalogo.execute(consulta()
                    .orden(CatalogSort.PRICE_ASC)
                    .desde(desde)
                    .limite(2)
                    .arma());
            pagina.items().forEach(publicacion -> recorridas.add(publicacion.id()));
            desde = pagina.siguiente();
        }

        assertThat(recorridas).doesNotHaveDuplicates().hasSize(5);
        assertThat(desde).isNull();
    }

    /** El cursor de una busqueda por relevancia continua por relevancia. */
    @Test
    void deberia_paginar_por_relevancia_sin_repetir_ni_saltarse_nada() {
        for (int i = 0; i < 4; i++) {
            una().titulo("Camisa de lino numero " + i).publicar();
        }

        CatalogPage primera =
                catalogo.execute(consulta().texto("camisa").limite(2).arma());
        CatalogPage segunda = catalogo.execute(
                consulta().texto("camisa").desde(primera.siguiente()).limite(2).arma());

        assertThat(primera.siguiente()).isNotNull();
        assertThat(primera.siguiente().orden()).isEqualTo(CatalogSort.RELEVANCE);
        assertThat(primera.items())
                .extracting(Listing::id)
                .doesNotContainAnyElementsOf(
                        segunda.items().stream().map(Listing::id).toList());
        assertThat(segunda.hayMas()).isFalse();
    }

    /**
     * Criterio 22. El cursor solo tiene sentido dentro del orden con el que nacio: mandarlo
     * bajo otro no da una pagina incompleta, da una arbitraria.
     */
    @Test
    void deberia_rechazar_un_cursor_nacido_con_otro_orden_criterio_22() {
        una().precio(50_000).publicar();
        una().precio(80_000).publicar();

        CatalogPage porPrecio = catalogo.execute(
                consulta().orden(CatalogSort.PRICE_ASC).limite(1).arma());
        CatalogCursor deOtroOrden = porPrecio.siguiente();

        assertThatThrownBy(() ->
                        consulta().orden(CatalogSort.NEWEST).desde(deOtroOrden).arma())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("PRICE_ASC");
    }

    /** Y el de un orden de precio tampoco vale en el contrario: recorren en sentidos opuestos. */
    @Test
    void deberia_rechazar_el_cursor_de_un_orden_de_precio_en_el_contrario_criterio_22() {
        una().precio(50_000).publicar();
        una().precio(80_000).publicar();

        CatalogCursor ascendente = catalogo.execute(
                        consulta().orden(CatalogSort.PRICE_ASC).limite(1).arma())
                .siguiente();

        assertThatThrownBy(() -> consulta()
                        .orden(CatalogSort.PRICE_DESC)
                        .desde(ascendente)
                        .arma())
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * El cursor de una busqueda con texto y el de la misma busqueda sin el son el mismo
     * orden cuando el pedido es relevancia, porque sin texto la relevancia es lo mas
     * reciente. Rechazarlo obligaria a la pantalla a tirar un cursor que acaba de recibir.
     */
    @Test
    void deberia_aceptar_el_cursor_del_catalogo_al_pedir_relevancia_sin_texto() {
        una().publicadaEn(AHORA).publicar();
        una().publicadaEn(AHORA.minusSeconds(60)).publicar();

        CatalogCursor delCatalogo =
                catalogo.execute(consulta().limite(1).arma()).siguiente();

        CatalogPage siguiente = catalogo.execute(
                consulta().orden(CatalogSort.RELEVANCE).desde(delCatalogo).arma());

        assertThat(siguiente.items()).hasSize(1);
    }

    // --- apoyo ---------------------------------------------------------------

    private List<Listing> buscar(String texto) {
        return catalogo.execute(consulta().texto(texto).arma()).items();
    }

    private Consulta consulta() {
        return new Consulta();
    }

    /** Una consulta con lo minimo puesto, para que cada prueba diga solo lo que le importa. */
    private static final class Consulta {

        private @Nullable String texto;
        private @Nullable CategoryId categoria;
        private Set<Condition> condiciones = Set.of();
        private @Nullable Size talla;
        private Set<Color> colores = Set.of();
        private PriceRange precio = PriceRange.SIN_LIMITE;
        private @Nullable CatalogSort orden;
        private @Nullable CatalogCursor desde;
        private int limite = 24;

        Consulta texto(String texto) {
            this.texto = texto;
            return this;
        }

        Consulta categoria(CategoryId categoria) {
            this.categoria = categoria;
            return this;
        }

        Consulta condiciones(Condition... condiciones) {
            this.condiciones = Set.of(condiciones);
            return this;
        }

        Consulta colores(Color... colores) {
            this.colores = Set.of(colores);
            return this;
        }

        Consulta talla(String valor) {
            return talla(SizeSystem.ALPHA, valor);
        }

        Consulta talla(SizeSystem sistema, String valor) {
            this.talla = new Size(sistema, valor);
            return this;
        }

        Consulta precio(long minimo, long maximo) {
            this.precio = new PriceRange(Money.dePesos(minimo), Money.dePesos(maximo));
            return this;
        }

        Consulta orden(CatalogSort orden) {
            this.orden = orden;
            return this;
        }

        Consulta desde(@Nullable CatalogCursor desde) {
            this.desde = desde;
            return this;
        }

        Consulta limite(int limite) {
            this.limite = limite;
            return this;
        }

        ListCatalogQuery arma() {
            return new ListCatalogQuery(
                    SearchText.de(texto), categoria, condiciones, talla, colores, precio, orden, desde, limite);
        }
    }

    private Publicacion una() {
        return new Publicacion();
    }

    /** Una publicacion viva, con lo que cada prueba necesite distinto. */
    private final class Publicacion {

        private CategoryId categoria = CAMISAS;
        private SellerId vendedor = new SellerId(UUID.randomUUID());
        private String titulo = "Camisa de lino color hueso";
        private String descripcion = "Usada dos veces.";
        private @Nullable String marca;
        private Condition condicion = Condition.LIKE_NEW;
        private Size talla = new Size(SizeSystem.ALPHA, "M");
        private Color color = Color.BEIGE;
        private long precio = 185_000;
        private Instant publicadaEn = AHORA;

        Publicacion categoria(CategoryId categoria) {
            this.categoria = categoria;
            return this;
        }

        Publicacion vendedor(SellerId vendedor) {
            this.vendedor = vendedor;
            return this;
        }

        Publicacion titulo(String titulo) {
            this.titulo = titulo;
            return this;
        }

        Publicacion descripcion(String descripcion) {
            this.descripcion = descripcion;
            return this;
        }

        Publicacion marca(String marca) {
            this.marca = marca;
            return this;
        }

        Publicacion condicion(Condition condicion) {
            this.condicion = condicion;
            return this;
        }

        Publicacion talla(String valor) {
            return talla(SizeSystem.ALPHA, valor);
        }

        Publicacion talla(SizeSystem sistema, String valor) {
            this.talla = new Size(sistema, valor);
            return this;
        }

        Publicacion color(Color color) {
            this.color = color;
            return this;
        }

        Publicacion precio(long precio) {
            this.precio = precio;
            return this;
        }

        Publicacion publicadaEn(Instant cuando) {
            this.publicadaEn = cuando;
            return this;
        }

        Listing borrador() {
            Map<MeasurementKind, BigDecimal> medidas = new EnumMap<>(MeasurementKind.class);
            MeasurementGroup.TOP.obligatorias().forEach(medida -> medidas.put(medida, new BigDecimal("50.0")));

            Product producto = new Product(
                    ProductId.nuevo(),
                    vendedor,
                    categoria,
                    new Title(titulo),
                    new Description(descripcion),
                    marca == null ? null : new Brand(marca),
                    condicion,
                    talla,
                    new Measurements(medidas),
                    color,
                    Money.dePesos(precio),
                    new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                    null,
                    null);

            return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
        }

        Listing publicar() {
            Listing aprobada = conTomas(borrador())
                    .enviarARevision(publicadaEn)
                    .aprobar(new ModeratorId(UUID.randomUUID()), publicadaEn);

            return publicaciones.guardar(aprobada);
        }
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

    private static Category familia(CategoryId id) {
        return new Category(id, "familia-" + id.value(), null, Set.of(), null, true, true);
    }

    private static Category hoja(CategoryId id, CategoryId padre) {
        return new Category(
                id,
                "hoja-" + id.value(),
                padre,
                Set.of(SizeSystem.ALPHA, SizeSystem.NUMERIC_CO),
                MeasurementGroup.TOP,
                true,
                true);
    }
}
