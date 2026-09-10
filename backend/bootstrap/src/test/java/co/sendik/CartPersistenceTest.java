package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
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
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * El adaptador del carrito, contra PostgreSQL 17 real. HU-015.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que la unicidad del par la
 * sostiene la tabla y no un {@code if}, que {@code ON CONFLICT DO NOTHING} conserva
 * <strong>las dos</strong> columnas del reintento —la fecha y el precio de entrada—, que la
 * lectura <strong>no</strong> filtra por estado, y que {@code buscarVarias} devuelve lo que se
 * le pide en cualquier estado. {@code CarritoTest} pasa contra un doble en memoria que se
 * comporta igual; esto comprueba que el SQL lo hace de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CartPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final Money PRECIO = Money.dePesos(185_000);

    private final CartItems carrito;
    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final JdbcClient jdbc;

    CartPersistenceTest(CartItems carrito, ListingRepository publicaciones, Categories categorias, JdbcClient jdbc) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.jdbc = jdbc;
    }

    // ------------------------------------------------------------- escritura

    @Test
    void deberia_guardar_y_leer_un_producto_del_carrito() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));

        assertThat(carrito.existe(quien, publicada.id())).isTrue();
        assertThat(carrito.cuantosLleva(quien)).isEqualTo(1);
    }

    /**
     * Criterio 4, y la razon de que la clave primaria sea el par. Sin la restriccion, esto
     * insertaria dos filas y el carrito ensenaria el mismo producto dos veces, que es lo que
     * RN-091 prohibe.
     */
    @Test
    void deberia_ser_idempotente_por_la_clave_primaria_criterio_4() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));

        assertThatCode(() -> carrito.guardar(
                        CartItem.reconstruir(quien, publicada.id(), AHORA.plus(Duration.ofHours(1)), PRECIO)))
                .doesNotThrowAnyException();

        assertThat(cuantasFilas(quien)).isEqualTo(1);
    }

    /**
     * Y la segunda escritura no toca ninguna de las dos columnas.
     *
     * <p>Aqui {@code DO NOTHING} protege una cosa mas que en los favoritos: si pisara
     * {@code added_price}, volver a pulsar sobre algo que subio de precio borraria justo el
     * aviso de que subio, que es el criterio 18.
     */
    @Test
    void no_deberia_mover_la_fecha_ni_el_precio_al_repetir() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));
        carrito.guardar(
                CartItem.reconstruir(quien, publicada.id(), AHORA.plus(Duration.ofHours(1)), Money.dePesos(200_000)));

        CartItem guardado = carrito.todosDe(quien).getFirst();

        assertThat(guardado.agregadoEn()).isEqualTo(AHORA);
        assertThat(guardado.precioAlAgregar()).isEqualTo(PRECIO);
    }

    @Test
    void deberia_quitar_el_producto_y_no_fallar_al_repetirlo() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));

        carrito.quitar(quien, publicada.id());
        assertThatCode(() -> carrito.quitar(quien, publicada.id())).doesNotThrowAnyException();

        assertThat(carrito.existe(quien, publicada.id())).isFalse();
    }

    /** El mismo producto en el carrito de dos personas son dos filas, no un conflicto. */
    @Test
    void deberia_admitir_que_dos_personas_lleven_el_mismo_producto_RN_089() {
        BuyerId una = nuevoComprador();
        BuyerId otra = nuevoComprador();
        Listing publicada = publicada();

        carrito.guardar(CartItem.reconstruir(una, publicada.id(), AHORA, PRECIO));
        carrito.guardar(CartItem.reconstruir(otra, publicada.id(), AHORA, PRECIO));

        assertThat(carrito.existe(una, publicada.id())).isTrue();
        assertThat(carrito.existe(otra, publicada.id())).isTrue();
    }

    /**
     * La clave foranea existe y muerde. Un carrito no puede apuntar a una publicacion que no
     * existe: si lo hiciera, la lectura tendria que descartar filas fantasma en cada consulta.
     */
    @Test
    void no_deberia_admitir_una_publicacion_que_no_existe() {
        BuyerId quien = nuevoComprador();

        assertThatThrownBy(() -> carrito.guardar(CartItem.reconstruir(quien, ListingId.nuevo(), AHORA, PRECIO)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------- lectura

    /**
     * <strong>La lectura del carrito no filtra por estado.</strong> Es la diferencia exacta
     * con la lista de favoritos, donde el {@code JOIN} filtra {@code PUBLISHED}, y es lo que
     * hace posible el criterio 21: lo que dejo de estar disponible sigue a la vista.
     */
    @Test
    void deberia_traer_tambien_lo_que_dejo_de_estar_publicado_RN_094() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();
        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));

        publicaciones.guardar(publicada.archivar(AHORA));

        assertThat(carrito.todosDe(quien)).hasSize(1);
    }

    /** El orden es el del gesto: lo ultimo agregado, primero. */
    @Test
    void deberia_ordenar_por_lo_agregado_mas_recientemente() {
        BuyerId quien = nuevoComprador();
        Listing primera = publicada();
        Listing segunda = publicada();

        carrito.guardar(CartItem.reconstruir(quien, primera.id(), AHORA, PRECIO));
        carrito.guardar(CartItem.reconstruir(quien, segunda.id(), AHORA.plus(Duration.ofMinutes(5)), PRECIO));

        assertThat(carrito.todosDe(quien))
                .extracting(CartItem::publicacion)
                .containsExactly(segunda.id(), primera.id());
    }

    /**
     * El desempate por identificador, que no es de adorno.
     *
     * <p>Dos productos agregados en el mismo instante —dos toques seguidos, o cualquier
     * prueba con reloj fijo— tienen la misma fecha. Sin {@code listing_id} en el
     * {@code ORDER BY}, PostgreSQL puede devolverlos en cualquier orden y dos lecturas del
     * mismo carrito barajarian los grupos sin que nada hubiera cambiado.
     */
    @Test
    void deberia_desempatar_por_identificador_cuando_la_fecha_se_repite() {
        BuyerId quien = nuevoComprador();
        Listing una = publicada();
        Listing otra = publicada();
        carrito.guardar(CartItem.reconstruir(quien, una.id(), AHORA, PRECIO));
        carrito.guardar(CartItem.reconstruir(quien, otra.id(), AHORA, PRECIO));

        List<ListingId> primera =
                carrito.todosDe(quien).stream().map(CartItem::publicacion).toList();
        List<ListingId> segunda =
                carrito.todosDe(quien).stream().map(CartItem::publicacion).toList();

        assertThat(primera).isEqualTo(segunda);
        assertThat(primera).hasSize(2);
    }

    /** El precio vuelve de la base como se guardo, sin decimales y sin perder exactitud. */
    @Test
    void deberia_conservar_el_precio_de_entrada_RN_029() {
        BuyerId quien = nuevoComprador();
        Listing publicada = publicada();

        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, Money.dePesos(20_000_000)));

        assertThat(carrito.todosDe(quien).getFirst().precioAlAgregar()).isEqualTo(Money.dePesos(20_000_000));
    }

    @Test
    void no_deberia_ver_el_carrito_de_otra_persona() {
        BuyerId quien = nuevoComprador();
        BuyerId otra = nuevoComprador();
        carrito.guardar(CartItem.reconstruir(quien, publicada().id(), AHORA, PRECIO));

        assertThat(carrito.todosDe(otra)).isEmpty();
        assertThat(carrito.cuantosLleva(otra)).isZero();
    }

    @Test
    void deberia_borrar_solo_el_de_esa_persona_al_cerrar_la_cuenta() {
        BuyerId quien = nuevoComprador();
        BuyerId otra = nuevoComprador();
        Listing publicada = publicada();
        carrito.guardar(CartItem.reconstruir(quien, publicada.id(), AHORA, PRECIO));
        carrito.guardar(CartItem.reconstruir(otra, publicada.id(), AHORA, PRECIO));

        carrito.borrarTodosDe(quien);

        assertThat(carrito.todosDe(quien)).isEmpty();
        assertThat(carrito.todosDe(otra)).hasSize(1);
    }

    // ------------------------------------------------------------- buscarVarias

    /**
     * {@code buscarVarias} trae lo pedido en cualquier estado. Es lo que necesita el carrito
     * de quien tiene sesion; la lectura anonima filtra ella lo visible.
     */
    @Test
    void deberia_buscar_varias_publicaciones_en_cualquier_estado() {
        Listing publicada = publicada();
        Listing pausada = publicaciones.guardar(publicada().pausar(AHORA));

        List<Listing> encontradas = publicaciones.buscarVarias(List.of(publicada.id(), pausada.id()));

        assertThat(encontradas).extracting(Listing::id).containsExactlyInAnyOrder(publicada.id(), pausada.id());
    }

    /** Lo que no existe no viene, y no es un error: quien llama sabe que pidio. */
    @Test
    void deberia_omitir_sin_fallar_los_identificadores_que_no_existen() {
        Listing publicada = publicada();

        List<Listing> encontradas = publicaciones.buscarVarias(List.of(publicada.id(), ListingId.nuevo()));

        assertThat(encontradas).extracting(Listing::id).containsExactly(publicada.id());
    }

    /** Una lista vacia no llega a la base: {@code IN ()} no es SQL valido. */
    @Test
    void no_deberia_fallar_con_una_lista_vacia() {
        assertThat(publicaciones.buscarVarias(List.of())).isEmpty();
    }

    /**
     * Vienen con su portada, que es lo que la fila del carrito pinta.
     *
     * <p>Y con la portada sola: cargar las ocho tomas de veinte publicaciones para quedarse
     * con veinte imagenes serian ciento sesenta filas por pantalla.
     */
    @Test
    void deberia_traer_la_portada_de_cada_publicacion() {
        Listing publicada = publicada();

        Listing encontrada = publicaciones.buscarVarias(List.of(publicada.id())).getFirst();

        assertThat(encontrada.images())
                .singleElement()
                .satisfies(imagen -> assertThat(imagen.position()).isZero());
    }

    // ------------------------------------------------------------- datos

    private long cuantasFilas(BuyerId quien) {
        return jdbc.sql("SELECT count(*) FROM cart_items WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Long.class)
                .single();
    }

    /** Una cuenta real: cart_items.user_id apunta a users. */
    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private BuyerId nuevoComprador() {
        return new BuyerId(nuevoUsuario());
    }

    private Listing publicada() {
        Listing enRevision = publicaciones.guardar(borradorConTomas().enviarARevision(AHORA));
        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), AHORA));
    }

    private Listing borradorConTomas() {
        Listing resultado = Listing.crearBorrador(ListingId.nuevo(), producto(), AHORA);

        for (int posicion = 0; posicion < ProductImage.TOMAS_DE_LA_SECUENCIA; posicion++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            posicion,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    private Product producto() {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return Product.crear(
                ProductId.nuevo(),
                new SellerId(nuevoUsuario()),
                camisas,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(valores),
                Color.BEIGE,
                PRECIO,
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);
    }

    private Category categoriaPorSlug(String slug) {
        UUID id = jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();

        return categorias.buscar(new CategoryId(id)).orElseThrow();
    }
}
