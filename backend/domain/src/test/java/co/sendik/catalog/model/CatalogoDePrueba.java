package co.sendik.catalog.model;

import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Constructores de datos para las pruebas del catalogo.
 *
 * <p>Existe para que una prueba diga solo lo que le importa. Una prueba de transiciones
 * no deberia tener que elegir un color, y si lo hace, el dia que el color cambie de
 * forma habra que tocar cincuenta pruebas que no hablaban de colores.
 */
final class CatalogoDePrueba {

    static final Instant AHORA = Instant.parse("2026-08-24T15:00:00Z");

    /** RN-019: la resolucion minima de una toma. */
    static final ImageDimensions TAMANO_VALIDO = new ImageDimensions(900, 1200);

    private CatalogoDePrueba() {}

    // ------------------------------------------------------------- categorias

    /** Una hoja de moda: admite lo usado y pide las medidas de parte superior. */
    static Category camisas() {
        return new Category(
                CategoryId.nuevo(),
                "camisas-y-blusas",
                CategoryId.nuevo(),
                Set.of(SizeSystem.ALPHA, SizeSystem.NUMERIC_CO),
                MeasurementGroup.TOP,
                true,
                true);
    }

    /** Una hoja de tecnologia: RN-064, solo admite lo nuevo. */
    static Category celulares() {
        return new Category(
                CategoryId.nuevo(),
                "celulares-y-tabletas",
                CategoryId.nuevo(),
                Set.of(SizeSystem.ONE_SIZE),
                MeasurementGroup.DEVICE,
                false,
                true);
    }

    static Category familia() {
        return new Category(CategoryId.nuevo(), "tops", null, Set.of(), null, true, true);
    }

    // ------------------------------------------------------------- medidas

    static Measurements medidasDe(MeasurementGroup grupo) {
        Map<MeasurementKind, BigDecimal> valores = new java.util.EnumMap<>(MeasurementKind.class);
        grupo.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));
        return new Measurements(valores);
    }

    // ------------------------------------------------------------- productos

    static Product camisa() {
        return camisaCon(Money.dePesos(185_000));
    }

    static Product camisaCon(Money precio) {
        return camisaDe(new SellerId(UUID.randomUUID()), precio);
    }

    /**
     * Una camisa de un vendedor concreto. La necesita el carrito (HU-015): RN-090 agrupa por
     * vendedor, asi que sus pruebas tienen que poder decir cuales comparten dueno y cuales
     * no, y con un identificador aleatorio en cada producto eso no se puede escribir.
     */
    static Product camisaDe(SellerId vendedor, Money precio) {
        Category categoria = camisas();
        return Product.crear(
                ProductId.nuevo(),
                vendedor,
                categoria,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces. Sin manchas ni descosidos."),
                new Brand("Zara"),
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                medidasDe(MeasurementGroup.TOP),
                Color.BEIGE,
                precio,
                envio(),
                null,
                null);
    }

    /** Un dispositivo. {@code sellado} decide si admite imagenes de referencia (RN-065). */
    static Product celular(@Nullable Boolean sellado) {
        Category categoria = celulares();
        return Product.crear(
                ProductId.nuevo(),
                new SellerId(UUID.randomUUID()),
                categoria,
                new Title("Telefono de gama media, 128 GB"),
                new Description("Nuevo, con su empaque."),
                new Brand("Motorola"),
                Condition.NEW,
                Size.unica(),
                medidasDe(MeasurementGroup.DEVICE),
                Color.BLACK,
                Money.dePesos(900_000),
                envio(),
                sellado,
                new WarrantyMonths(12));
    }

    static ShippingDimensions envio() {
        return new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0"));
    }

    // ------------------------------------------------------------- imagenes

    static ProductImage toma(int posicion) {
        return ProductImage.toma(
                ProductImageId.nuevo(),
                new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                posicion,
                TAMANO_VALIDO,
                120_000L,
                ImageContentType.JPEG);
    }

    static ProductImage referencia(int posicion) {
        return ProductImage.referencia(
                ProductImageId.nuevo(),
                new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                posicion,
                TAMANO_VALIDO,
                120_000L,
                ImageContentType.JPEG);
    }

    // ------------------------------------------------------------- publicaciones

    static Listing borrador() {
        return Listing.crearBorrador(ListingId.nuevo(), camisa(), AHORA);
    }

    static Listing borradorDe(Product producto) {
        return Listing.crearBorrador(ListingId.nuevo(), producto, AHORA);
    }

    /** Un borrador con las tomas que su producto exige, listo para enviar a revision. */
    static Listing borradorCompleto() {
        return conTomas(borrador());
    }

    /**
     * Las tomas que su producto exige.
     *
     * <p>El paso no es siempre uno: un producto sellado se queda en las cuatro
     * canonicas (RN-065), y las canonicas son las de 0, 90, 180 y 270 grados, o sea
     * las posiciones 0, 2, 4 y 6 de la misma secuencia. Ocho tomas van seguidas;
     * cuatro van de dos en dos.
     */
    static Listing conTomas(Listing publicacion) {
        int exigidas = publicacion.tomasExigidas();
        int paso = ProductImage.TOMAS_DE_LA_SECUENCIA / exigidas;

        Listing resultado = publicacion;
        for (int i = 0; i < exigidas; i++) {
            resultado = resultado.conImagen(toma(i * paso), AHORA);
        }
        return resultado;
    }

    /** Publicada y visible, que es el punto de partida de media docena de pruebas. */
    static Listing publicada() {
        return borradorCompleto().enviarARevision(AHORA).aprobar(new ModeratorId(UUID.randomUUID()), AHORA);
    }

    /** Publicada, de un vendedor concreto y con un precio concreto. Para el carrito. */
    static Listing publicadaDe(SellerId vendedor, Money precio) {
        return conTomas(borradorDe(camisaDe(vendedor, precio)))
                .enviarARevision(AHORA)
                .aprobar(new ModeratorId(UUID.randomUUID()), AHORA);
    }
}
