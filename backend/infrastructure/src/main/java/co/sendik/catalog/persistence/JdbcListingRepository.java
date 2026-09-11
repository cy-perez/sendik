package co.sendik.catalog.persistence;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.exception.ListingConcurrentlyModifiedException;
import co.sendik.catalog.model.AttentionReason;
import co.sendik.catalog.model.Brand;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.ImageKind;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
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
import co.sendik.catalog.model.WarrantyMonths;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia del catalogo. HU-007.
 *
 * <p>Un repositorio por agregado y no por tabla: guardar una publicacion escribe en
 * {@code listings}, {@code products} y {@code product_images} dentro de la misma
 * transaccion. No hay forma de guardar una imagen suelta desde fuera, y eso es
 * deliberado: si la hubiera, alguien podria dejar el agregado en un estado que el
 * dominio nunca habria permitido, como una publicacion visible con siete tomas.
 *
 * <p><strong>Las imagenes se reescriben enteras en cada guardado.</strong> Son ocho
 * filas como maximo y comparar cual cambio costaria mas codigo del que ahorra. Lo que
 * si importa es que el borrado y la insercion van juntos, para que nunca exista un
 * instante con la mitad de la secuencia.
 *
 * <p>El bloqueo optimista es del criterio 34: el {@code UPDATE} exige la version que
 * se leyo, y si no la encuentra es que alguien escribio en medio.
 */
@Repository
public class JdbcListingRepository implements ListingRepository {

    /**
     * La proyeccion de una publicacion con su producto.
     *
     * <p>Visible en el paquete y no privada desde HU-011: la lista de favoritos entrega
     * las mismas publicaciones con otro filtro y otro orden, y copiar aqui treinta
     * columnas seria dejar dos proyecciones que alguien tendria que acordarse de cambiar
     * a la vez. La alternativa era duplicar tambien {@link #filaAPublicacion}, que son
     * sesenta lineas de mapeo.
     */
    static final String COLUMNAS_BASE = """
            l.id, l.status, l.submitted_at, l.published_at, l.sold_at,
            l.moderated_by, l.moderated_at, l.rejection_reason, l.rejection_note,
            l.attention_reasons, l.version,
            l.created_at, l.updated_at,
            p.id AS product_id, p.seller_id, p.category_id, p.title, p.description, p.brand,
            p.condition, p.size_system, p.size_value, p.measurements, p.color, p.price,
            p.weight_grams, p.length_cm, p.width_cm, p.height_cm,
            p.is_sealed, p.manufacturer_warranty_months
            """;

    /**
     * De donde salen esas columnas.
     *
     * <p>Partido en dos desde HU-011, y no por gusto: la lista de favoritos necesita
     * <strong>anteponer</strong> una columna suya a la proyeccion para leer la fecha del
     * gesto en la misma pasada, y con la sentencia entera en una sola cadena eso solo se
     * podia hacer trozeando SQL con expresiones regulares. Las dos mitades por separado se
     * componen sin tocar ninguna.
     */
    static final String DESDE_BASE = """
            FROM listings l
            JOIN products p ON p.id = l.product_id
            """;

    static final String SELECT_BASE = "SELECT " + COLUMNAS_BASE + DESDE_BASE;

    private final JdbcClient jdbc;

    public JdbcListingRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Listing guardar(Listing publicacion) {
        guardarProducto(publicacion.product());
        long versionNueva = guardarPublicacion(publicacion);
        reescribirImagenes(publicacion);

        return publicacion.conVersion(versionNueva);
    }

    @Override
    public Optional<Listing> buscar(ListingId id) {
        return jdbc.sql(SELECT_BASE + " WHERE l.id = :id")
                .param("id", id.value())
                .query(JdbcListingRepository::filaAPublicacion)
                .optional()
                .map(this::conImagenes);
    }

    /**
     * Varias de una vez, en cualquier estado y con su portada. HU-015.
     *
     * <p><strong>Con portada y no con el agregado entero</strong>, al reves que
     * {@link #buscar}. Quien la llama pinta filas —el carrito—, y una fila ensena la toma
     * frontal de RN-016 y nada mas: cargar las ocho tomas de veinte publicaciones para
     * quedarse con veinte imagenes serian ciento sesenta filas de {@code product_images} por
     * pantalla. Es la misma decision que ya toman el catalogo y la cola del moderador.
     *
     * <p>Una lista vacia no llega a la base: {@code IN ()} no es SQL valido.
     *
     * <p>No filtra por estado —el carrito necesita lo no disponible (RN-094) y la lectura
     * anonima lo descarta ella— y no garantiza orden: quien necesite uno lo impone.
     */
    @Override
    public List<Listing> buscarVarias(List<ListingId> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }

        return conPortadas(
                jdbc,
                jdbc.sql(SELECT_BASE + " WHERE l.id IN (:ids)")
                        .param("ids", ids.stream().map(ListingId::value).toList())
                        .query(JdbcListingRepository::filaAPublicacion)
                        .list());
    }

    @Override
    public Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor) {
        return jdbc.sql(SELECT_BASE + " WHERE l.id = :id AND p.seller_id = :vendedor")
                .param("id", id.value())
                .param("vendedor", vendedor.value())
                .query(JdbcListingRepository::filaAPublicacion)
                .optional()
                .map(this::conImagenes);
    }

    @Override
    public List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano) {
        return jdbc
                .sql(SELECT_BASE
                        + " WHERE p.seller_id = :vendedor ORDER BY l.created_at DESC LIMIT :limite OFFSET :salto")
                .param("vendedor", vendedor.value())
                .param("limite", tamano)
                .param("salto", (long) pagina * tamano)
                .query(JdbcListingRepository::filaAPublicacion)
                .list()
                .stream()
                .map(this::conImagenes)
                .toList();
    }

    /**
     * El escaparate de un vendedor: lo suyo que esta publicado. RN-068.
     *
     * <p><strong>El cursor aqui es siempre el del catalogo</strong> —lo mas reciente
     * primero— y por eso este metodo no recibe orden: el escaparate es el perfil publico de
     * alguien y no una busqueda. Los cuatro ordenes de HU-014 viven en el motor, que es
     * donde hay algo que ordenar.
     */
    @Override
    public List<Listing> publicadasDelVendedor(SellerId vendedor, @Nullable CatalogCursor desde, int limite) {
        var consulta = jdbc.sql(SELECT_BASE + """
                         WHERE l.status = 'PUBLISHED' AND p.seller_id = :vendedor
                        """ + condicionDelCursor(desde) + """
                         ORDER BY l.published_at DESC, l.id DESC
                         LIMIT :limite
                        """)
                .param("vendedor", vendedor.value())
                .param("limite", limite);

        consulta = conParametrosDelCursor(consulta, desde);

        return conPortadas(
                jdbc, consulta.query(JdbcListingRepository::filaAPublicacion).list());
    }

    private static String condicionDelCursor(@Nullable CatalogCursor desde) {
        return desde == null ? "" : " AND (l.published_at, l.id) < (:publicadaEn, :ultimaId)";
    }

    private static StatementSpec conParametrosDelCursor(StatementSpec consulta, @Nullable CatalogCursor desde) {
        if (desde == null) {
            return consulta;
        }
        return consulta.param("publicadaEn", Timestamp.from(desde.exigirPublicadaEn()))
                .param("ultimaId", desde.id().value());
    }

    /**
     * La cola del moderador. Va contra el indice parcial de V12, que es esta consulta
     * escrita como indice: filtra por estado y ordena por espera.
     *
     * <p>{@code NULLS LAST} no deberia hacer falta —el dominio sella al entrar— pero
     * cubre las filas que quedaron en revision antes de que existiera la columna sin
     * mandarlas a la cabeza de la cola.
     *
     * <p><strong>Desempata por {@code id}, y eso es lo que hace correcto el
     * desplazamiento.</strong> {@code submitted_at} sola no es un orden total: dos
     * publicaciones enviadas en el mismo instante pueden salir en cualquier orden, y si
     * ese orden cambia entre una pagina y la siguiente hay filas que aparecen dos veces y
     * filas que no aparecen nunca. No es hipotetico: la retrollenada de V12 le puso a
     * todas las que ya esperaban turno el valor de {@code updated_at}, y ahi los empates
     * son faciles. Es la misma correccion que ya lleva la cola de verificaciones.
     *
     * <p>El indice parcial de V12 es solo {@code (submitted_at)}, igual que el de la otra
     * cola en V8, asi que el desempate lo resuelve PostgreSQL ordenando los empates. Con
     * el volumen de una cola de moderacion eso no se nota, y ampliar el indice pide una
     * migracion nueva que hoy no compra nada.
     */
    @Override
    public List<Listing> pendientesDeRevision(long salto, int cuantas) {
        List<Listing> cola = jdbc.sql(SELECT_BASE + """
                         WHERE l.status = 'PENDING_REVIEW'
                         ORDER BY l.submitted_at ASC NULLS LAST, l.id ASC
                         LIMIT :limite OFFSET :salto
                        """)
                .param("limite", cuantas)
                // El salto llega calculado y no se deriva de `cuantas`: derivarlo es lo que
                // hace que pedir una fila de mas mueva tambien el arranque.
                .param("salto", salto)
                .query(JdbcListingRepository::filaAPublicacion)
                .list();

        return conPortadas(jdbc, cola);
    }

    /**
     * Sin {@code ORDER BY} a proposito: la pregunta es cuantas hay, no cuales, y para
     * «¿queda alguna despues de la posicion N?» el orden es irrelevante.
     *
     * <p>Se apoya en el mismo indice parcial de V12. No lee ninguna columna de la fila ni
     * resuelve portadas: solo comprueba que existe.
     */
    @Override
    public boolean hayPendientesDesde(long salto) {
        return jdbc.sql("""
                        SELECT EXISTS (
                            SELECT 1 FROM listings
                            WHERE status = 'PENDING_REVIEW'
                            OFFSET :salto LIMIT 1)
                        """).param("salto", salto).query(Boolean.class).single();
    }

    /**
     * Cuantas hay en cada estado, en una sola pasada. HU-012.
     *
     * <p>El vendedor vive en {@code products} y no en {@code listings}, asi que hace falta
     * la misma union que {@link #buscarDelVendedor}. No se reutiliza {@link #DESDE_BASE}
     * porque esto no proyecta la publicacion: cuenta filas, y traer las treinta columnas
     * para descartarlas seria pagar el mapeo entero por un numero.
     *
     * <p><strong>Devuelve solo los estados que tienen filas.</strong> Los siete de RN-061
     * los completa {@code SummarizeSellerListingsUseCase}, que es donde esa decision se
     * puede leer y probar; aqui inventarlos exigiria una union con siete literales y
     * meteria la enumeracion del dominio dentro del SQL.
     */
    @Override
    public Map<ListingStatus, Long> contarPorEstadoDelVendedor(SellerId vendedor) {
        return jdbc
                .sql("""
                        SELECT l.status, COUNT(*) AS cuantas
                        FROM listings l
                        JOIN products p ON p.id = l.product_id
                        WHERE p.seller_id = :vendedor
                        GROUP BY l.status
                        """)
                .param("vendedor", vendedor.value())
                .query((fila, numero) ->
                        Map.entry(ListingStatus.valueOf(fila.getString("status")), fila.getLong("cuantas")))
                .list()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (una, otra) -> una,
                        () -> new EnumMap<>(ListingStatus.class)));
    }

    /**
     * Las tomas frontales de toda la pagina, en una sola consulta.
     *
     * <p>Lo natural aqui era {@code .map(this::conImagenes)}, y es lo que hacia: una
     * consulta de imagenes por fila, veintiuna para pintar veinte miniaturas, cargando ocho
     * tomas de cada publicacion para quedarse con una. La bandeja es la primera pantalla del
     * moderador y la que mas veces se abre.
     *
     * <p>Se traen **solo las de posicion 0**, que es la frontal de RN-016 y lo unico que la
     * fila muestra. El detalle si carga el agregado entero, por {@code buscar}.
     */
    static List<Listing> conPortadas(JdbcClient jdbc, List<Listing> cola) {
        if (cola.isEmpty()) {
            return cola;
        }

        List<UUID> productos = cola.stream().map(p -> p.product().id().value()).toList();

        Map<UUID, ProductImage> frontales = jdbc
                .sql("""
                        SELECT product_id, id, kind, object_key, position, angle_degrees,
                               width, height, bytes, content_type
                        FROM product_images
                        WHERE product_id IN (:productos) AND kind = 'SELLER_SHOT' AND position = 0
                        """)
                .param("productos", productos)
                .query((fila, numero) -> Map.entry(fila.getObject("product_id", UUID.class), filaAImagen(fila, numero)))
                .list()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return cola.stream()
                .map(publicacion -> {
                    ProductImage frontal =
                            frontales.get(publicacion.product().id().value());
                    return frontal == null ? publicacion : conSoloEstasImagenes(publicacion, List.of(frontal));
                })
                .toList();
    }

    // ------------------------------------------------------------------ escritura

    /**
     * Escribe el producto, completo o a medias.
     *
     * <p><strong>Casi todo puede faltar, y por eso ningun campo se desreferencia
     * directo.</strong> El criterio 5 dice que un borrador incompleto se guarda sin
     * exigir que este completo, asi que el titulo, la descripcion, la condicion, la
     * talla, el color, el precio y la caja de envio llegan aqui en nulo cada vez que
     * alguien pulsa «Empezar» y todavia no ha escrito nada. Escrito como
     * {@code producto.title().value()}, eso era una excepcion de puntero nulo y un 500
     * en la unica peticion con la que empieza toda publicacion.
     *
     * <p>Lo obligatorio lo exige {@code Listing.enviarARevision} (criterio 6), no esta
     * tabla: una columna no puede distinguir un borrador a medias de una publicacion
     * que se quiere publicar sin terminar. V13 quito esos {@code NOT NULL}.
     */
    private void guardarProducto(Product producto) {
        jdbc.sql("""
                        INSERT INTO products (
                            id, seller_id, category_id, title, description, brand, condition,
                            size_system, size_value, measurements, color, price,
                            weight_grams, length_cm, width_cm, height_cm,
                            is_sealed, manufacturer_warranty_months, updated_at)
                        VALUES (
                            :id, :vendedor, :categoria, :titulo, :descripcion, :marca, :condicion,
                            :sistemaTalla, :talla, :medidas::jsonb, :color, :precio,
                            :gramos, :largo, :ancho, :alto,
                            :sellado, :garantia, now())
                        ON CONFLICT (id) DO UPDATE SET
                            category_id                  = EXCLUDED.category_id,
                            title                        = EXCLUDED.title,
                            description                  = EXCLUDED.description,
                            brand                        = EXCLUDED.brand,
                            condition                    = EXCLUDED.condition,
                            size_system                  = EXCLUDED.size_system,
                            size_value                   = EXCLUDED.size_value,
                            measurements                 = EXCLUDED.measurements,
                            color                        = EXCLUDED.color,
                            price                        = EXCLUDED.price,
                            weight_grams                 = EXCLUDED.weight_grams,
                            length_cm                    = EXCLUDED.length_cm,
                            width_cm                     = EXCLUDED.width_cm,
                            height_cm                    = EXCLUDED.height_cm,
                            is_sealed                    = EXCLUDED.is_sealed,
                            manufacturer_warranty_months = EXCLUDED.manufacturer_warranty_months,
                            updated_at                   = now()
                        """)
                .param("id", producto.id().value())
                .param("vendedor", producto.sellerId().value())
                .param("categoria", producto.categoryId().value())
                .param("titulo", siEsta(producto.title(), Title::value))
                .param("descripcion", siEsta(producto.description(), Description::value))
                .param("marca", siEsta(producto.brand(), Brand::value))
                .param("condicion", siEsta(producto.condition(), Condition::name))
                .param(
                        "sistemaTalla",
                        siEsta(producto.size(), talla -> talla.system().name()))
                .param("talla", siEsta(producto.size(), Size::value))
                .param("medidas", MeasurementsJson.aJson(producto.measurements()))
                .param("color", siEsta(producto.color(), Color::name))
                .param("precio", siEsta(producto.price(), Money::enPesos))
                .param("gramos", siEsta(producto.shipping(), ShippingDimensions::weightGrams))
                .param("largo", siEsta(producto.shipping(), ShippingDimensions::lengthCm))
                .param("ancho", siEsta(producto.shipping(), ShippingDimensions::widthCm))
                .param("alto", siEsta(producto.shipping(), ShippingDimensions::heightCm))
                .param("sellado", producto.isSealed())
                .param("garantia", siEsta(producto.warranty(), WarrantyMonths::value))
                .update();
    }

    /**
     * Inserta o actualiza exigiendo la version leida. Criterio 34.
     *
     * <p>Cuando el {@code UPDATE} no toca ninguna fila, la version cambio entre la
     * lectura y la escritura: alguien mas guardo en medio y esta decision se pierde. No
     * se reintenta desde aqui, porque quien reintente tiene que volver a decidir con lo
     * que hay ahora, y eso no lo sabe un repositorio.
     */
    private long guardarPublicacion(Listing publicacion) {
        long versionLeida = publicacion.version();
        Product producto = publicacion.product();

        if (esNueva(publicacion)) {
            jdbc.sql("""
                            INSERT INTO listings (
                                id, product_id, status, submitted_at, published_at, sold_at,
                                moderated_by, moderated_at, rejection_reason, rejection_note,
                                attention_reasons, version, created_at, updated_at)
                            VALUES (
                                :id, :producto, :estado, :enviado, :publicado, NULL,
                                :moderador, :moderado, :motivo, :nota,
                                :marcas, 0, :creado, :actualizado)
                            """)
                    .param("id", publicacion.id().value())
                    .param("producto", producto.id().value())
                    .param("estado", publicacion.status().name())
                    .param("enviado", marca(publicacion.submittedAt()))
                    .param("publicado", marca(publicacion.publishedAt()))
                    .param(
                            "moderador",
                            publicacion.moderatedBy() == null
                                    ? null
                                    : publicacion.moderatedBy().value())
                    .param("moderado", marca(publicacion.moderatedAt()))
                    .param(
                            "motivo",
                            publicacion.rejectionReason() == null
                                    ? null
                                    : publicacion.rejectionReason().name())
                    .param("nota", publicacion.rejectionNote())
                    .param("marcas", marcasDe(publicacion))
                    .param("creado", Timestamp.from(publicacion.createdAt()))
                    .param("actualizado", Timestamp.from(publicacion.updatedAt()))
                    .update();
            return 0L;
        }

        int filas = jdbc.sql("""
                        UPDATE listings SET
                            status             = :estado,
                            submitted_at       = :enviado,
                            published_at       = :publicado,
                            moderated_by       = :moderador,
                            moderated_at       = :moderado,
                            rejection_reason   = :motivo,
                            rejection_note     = :nota,
                            attention_reasons  = :marcas,
                            version            = version + 1,
                            updated_at         = :actualizado
                        WHERE id = :id AND version = :version
                        """)
                .param("id", publicacion.id().value())
                .param("version", versionLeida)
                .param("estado", publicacion.status().name())
                .param("enviado", marca(publicacion.submittedAt()))
                .param("publicado", marca(publicacion.publishedAt()))
                .param(
                        "moderador",
                        publicacion.moderatedBy() == null
                                ? null
                                : publicacion.moderatedBy().value())
                .param("moderado", marca(publicacion.moderatedAt()))
                .param(
                        "motivo",
                        publicacion.rejectionReason() == null
                                ? null
                                : publicacion.rejectionReason().name())
                .param("nota", publicacion.rejectionNote())
                .param("marcas", marcasDe(publicacion))
                .param("actualizado", Timestamp.from(publicacion.updatedAt()))
                .update();

        if (filas == 0) {
            throw new ListingConcurrentlyModifiedException(publicacion.id());
        }
        return versionLeida + 1;
    }

    /**
     * Nueva es la que todavia no tiene fila, no la que tiene version cero.
     *
     * <p>Se pregunta a la base y no al agregado: una publicacion recien creada y una
     * guardada una sola vez tienen las dos version cero, y confundirlas convertiria un
     * INSERT en un UPDATE que no encuentra nada, o al reves.
     */
    private boolean esNueva(Listing publicacion) {
        return jdbc.sql("SELECT count(*) FROM listings WHERE id = :id")
                        .param("id", publicacion.id().value())
                        .query(Long.class)
                        .single()
                == 0L;
    }

    private void reescribirImagenes(Listing publicacion) {
        jdbc.sql("DELETE FROM product_images WHERE product_id = :producto")
                .param("producto", publicacion.product().id().value())
                .update();

        for (ProductImage imagen : publicacion.images()) {
            jdbc.sql("""
                            INSERT INTO product_images (
                                id, product_id, kind, object_key, position, angle_degrees,
                                is_canonical, width, height, bytes, content_type)
                            VALUES (
                                :id, :producto, :clase, :clave, :posicion, :angulo,
                                :canonica, :ancho, :alto, :bytes, :tipo)
                            """)
                    .param("id", imagen.id().value())
                    .param("producto", publicacion.product().id().value())
                    .param("clase", imagen.kind().name())
                    .param("clave", imagen.objectKey().value())
                    .param("posicion", imagen.position())
                    .param("angulo", imagen.angleDegrees())
                    .param("canonica", imagen.esCanonica())
                    .param("ancho", imagen.dimensions().width())
                    .param("alto", imagen.dimensions().height())
                    .param("bytes", imagen.bytes())
                    .param("tipo", imagen.contentType().mediaType())
                    .update();
        }
    }

    // ------------------------------------------------------------------ lectura

    private Listing conImagenes(Listing publicacion) {
        List<ProductImage> imagenes = jdbc.sql("""
                        SELECT id, kind, object_key, position, angle_degrees, width, height, bytes, content_type
                        FROM product_images
                        WHERE product_id = :producto
                        ORDER BY kind, position
                        """)
                .param("producto", publicacion.product().id().value())
                .query(JdbcListingRepository::filaAImagen)
                .list();

        return conSoloEstasImagenes(publicacion, imagenes);
    }

    /** Reconstruye la publicacion con las imagenes que se le den y nada mas. */
    private static Listing conSoloEstasImagenes(Listing publicacion, List<ProductImage> imagenes) {
        return Listing.reconstruir()
                .id(publicacion.id())
                .producto(publicacion.product())
                .estado(publicacion.status())
                .imagenes(imagenes)
                .enviada(publicacion.submittedAt())
                .publicada(publicacion.publishedAt())
                .decididaPor(publicacion.moderatedBy(), publicacion.moderatedAt())
                .rechazadaPor(publicacion.rejectionReason(), publicacion.rejectionNote())
                .marcas(publicacion.attentionReasons())
                .version(publicacion.version())
                .creada(publicacion.createdAt())
                .tocada(publicacion.updatedAt())
                .armar();
    }

    static Listing filaAPublicacion(ResultSet fila, int numero) throws SQLException {
        Product producto = filaAProducto(fila);

        String motivoRechazo = fila.getString("rejection_reason");
        UUID moderador = fila.getObject("moderated_by", UUID.class);

        return Listing.reconstruir()
                .id(new ListingId(fila.getObject("id", UUID.class)))
                .producto(producto)
                .estado(ListingStatus.valueOf(fila.getString("status")))
                .imagenes(List.of())
                .enviada(instante(fila.getTimestamp("submitted_at")))
                .publicada(instante(fila.getTimestamp("published_at")))
                .decididaPor(
                        moderador == null ? null : new ModeratorId(moderador),
                        instante(fila.getTimestamp("moderated_at")))
                .rechazadaPor(
                        motivoRechazo == null ? null : ListingRejectionReason.valueOf(motivoRechazo),
                        fila.getString("rejection_note"))
                .marcas(marcas(fila.getArray("attention_reasons")))
                .version(fila.getLong("version"))
                .creada(instante(fila.getTimestamp("created_at")))
                .tocada(instante(fila.getTimestamp("updated_at")))
                .armar();
    }

    /**
     * Reconstruye el producto, que puede estar a medias.
     *
     * <p>Simetrico a {@link #guardarProducto}: lo que se puede escribir en nulo se
     * tiene que poder leer en nulo. {@code Condition.valueOf(null)} y
     * {@code fila.getLong("price")} sobre una columna nula eran, respectivamente, una
     * excepcion de puntero nulo y un precio de cero pesos que el propio {@code Product}
     * rechaza: las dos formas de no poder releer nunca un borrador recien creado.
     */
    private static Product filaAProducto(ResultSet fila) throws SQLException {
        String condicion = fila.getString("condition");
        String sistemaDeTalla = fila.getString("size_system");
        String color = fila.getString("color");
        Object precio = fila.getObject("price");
        Object garantia = fila.getObject("manufacturer_warranty_months");
        Object sellado = fila.getObject("is_sealed");

        return new Product(
                new ProductId(fila.getObject("product_id", UUID.class)),
                new SellerId(fila.getObject("seller_id", UUID.class)),
                new CategoryId(fila.getObject("category_id", UUID.class)),
                siEsta(fila.getString("title"), Title::new),
                siEsta(fila.getString("description"), Description::new),
                siEsta(fila.getString("brand"), Brand::new),
                siEsta(condicion, Condition::valueOf),
                sistemaDeTalla == null
                        ? null
                        : new Size(SizeSystem.valueOf(sistemaDeTalla), fila.getString("size_value")),
                MeasurementsJson.deJson(fila.getString("measurements")),
                siEsta(color, Color::valueOf),
                siEsta(precio, valor -> Money.dePesos(((Number) valor).longValue())),
                envio(fila),
                sellado == null ? null : (Boolean) sellado,
                siEsta(garantia, meses -> new WarrantyMonths(((Number) meses).intValue())));
    }

    /**
     * La caja del envio, o nada.
     *
     * <p>Se pregunta por el peso y no por cada medida: media caja no es una caja, y
     * {@code ShippingDimensions} no admite nulos dentro. Las cuatro columnas se escriben
     * juntas o no se escribe ninguna.
     */
    private static @Nullable ShippingDimensions envio(ResultSet fila) throws SQLException {
        Object gramos = fila.getObject("weight_grams");
        if (gramos == null) {
            return null;
        }
        return new ShippingDimensions(
                ((Number) gramos).intValue(),
                fila.getBigDecimal("length_cm"),
                fila.getBigDecimal("width_cm"),
                fila.getBigDecimal("height_cm"));
    }

    /**
     * El valor derivado de algo que puede no estar todavia.
     *
     * <p>Existe porque el borrador del criterio 5 tiene casi todos los campos en nulo y
     * el ternario repetido dieciseis veces esconde justo al que se olvida.
     */
    private static <T, R> @Nullable R siEsta(@Nullable T valor, Function<T, R> como) {
        return valor == null ? null : como.apply(valor);
    }

    private static ProductImage filaAImagen(ResultSet fila, int numero) throws SQLException {
        Object angulo = fila.getObject("angle_degrees");

        return new ProductImage(
                new ProductImageId(fila.getObject("id", UUID.class)),
                ImageKind.valueOf(fila.getString("kind")),
                new FileKey(fila.getString("object_key")),
                fila.getInt("position"),
                angulo == null ? null : ((Number) angulo).intValue(),
                new ImageDimensions(fila.getInt("width"), fila.getInt("height")),
                fila.getLong("bytes"),
                ImageContentType.porMediaType(fila.getString("content_type")));
    }

    /** Los nombres del enum, como arreglo de texto para la columna. */
    private static String[] marcasDe(Listing publicacion) {
        return publicacion.attentionReasons().stream().map(Enum::name).toArray(String[]::new);
    }

    private static Set<AttentionReason> marcas(@Nullable Array columna) throws SQLException {
        if (columna == null) {
            return Set.of();
        }
        return Arrays.stream((String[]) columna.getArray())
                .map(AttentionReason::valueOf)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(AttentionReason.class)));
    }

    private static @Nullable Timestamp marca(@Nullable Instant momento) {
        return momento == null ? null : Timestamp.from(momento);
    }

    private static @Nullable Instant instante(@Nullable Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }
}
