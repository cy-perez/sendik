package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CategoryView;
import co.sendik.catalog.dto.FavoriteCursor;
import co.sendik.catalog.dto.FavoritedListing;
import co.sendik.catalog.dto.SearchCriteria;
import co.sendik.catalog.dto.SearchHit;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Favorite;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingRejectionReason;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.ModerationAction;
import co.sendik.catalog.model.ModerationEvent;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.Favorites;
import co.sendik.catalog.port.out.ListingNotifier;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.ModerationLog;
import co.sendik.catalog.port.out.SearchEngine;
import co.sendik.catalog.port.out.SellerEligibility;
import co.sendik.catalog.port.out.SellerProfiles;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.NormalizedImage;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import java.text.Normalizer;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;

/**
 * Dobles de los puertos del catalogo, en memoria.
 *
 * <p>Escritos a mano y no con un simulador. Un repositorio simulado obliga a decir en
 * cada prueba que devuelve cada llamada, y entonces la prueba describe la
 * implementacion en vez del comportamiento. Con estos, una prueba guarda algo y luego
 * lo lee, que es lo que de verdad hace el sistema.
 */
final class CatalogoEnMemoria {

    private CatalogoEnMemoria() {}

    static final class Publicaciones implements ListingRepository {

        private final Map<ListingId, Listing> filas = new LinkedHashMap<>();

        @Override
        public Listing guardar(Listing publicacion) {
            filas.put(publicacion.id(), publicacion);
            return publicacion;
        }

        @Override
        public Optional<Listing> buscar(ListingId id) {
            return Optional.ofNullable(filas.get(id));
        }

        /** Todo lo guardado, para el motor de busqueda, que consulta la misma base. */
        List<Listing> todas() {
            return List.copyOf(filas.values());
        }

        /**
         * Varias de una vez, en cualquier estado y sin orden garantizado.
         *
         * <p>Devuelve solo las que existen y no falla por las que no, igual que el
         * {@code WHERE id IN (...)} de verdad: quien llama sabe que pidio. El orden es el de
         * insercion, que no es el de la peticion, y eso es a proposito —el puerto declara que
         * no hay orden definido, asi que una prueba que dependiera de el estaria probando el
         * doble y no la base—.
         */
        @Override
        public List<Listing> buscarVarias(List<ListingId> ids) {
            return ids.stream()
                    .map(filas::get)
                    .filter(java.util.Objects::nonNull)
                    .toList();
        }

        @Override
        public Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor) {
            return buscar(id).filter(publicacion -> publicacion.sellerId().equals(vendedor));
        }

        @Override
        public List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano) {
            List<Listing> suyas = filas.values().stream()
                    .filter(publicacion -> publicacion.sellerId().equals(vendedor))
                    .sorted(Comparator.comparing(Listing::createdAt).reversed())
                    .toList();

            int desde = Math.min(pagina * tamano, suyas.size());
            return suyas.subList(desde, Math.min(desde + tamano, suyas.size()));
        }

        @Override
        public List<Listing> pendientesDeRevision(long salto, int cuantas) {
            List<Listing> esperando = filas.values().stream()
                    .filter(publicacion -> publicacion.status() == ListingStatus.PENDING_REVIEW)
                    .sorted(Comparator.comparing(Listing::submittedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                            // El mismo desempate que el SQL: sin el, dos publicaciones
                            // enviadas en el mismo instante salen en cualquier orden.
                            .thenComparing(publicacion -> publicacion.id().value()))
                    .toList();

            // Corta igual que el SQL: el salto llega dado y el limite no lo mueve.
            int desde = (int) Math.min(salto, esperando.size());
            return esperando.subList(desde, Math.min(desde + cuantas, esperando.size()));
        }

        /**
         * Cuenta igual que el SQL: agrupa por estado y <strong>no inventa los vacios</strong>.
         *
         * <p>Que aqui salieran los siete con cero haria pasar la prueba del caso de uso sin
         * que el caso de uso hiciera nada, y romperia contra PostgreSQL, que es donde un
         * {@code GROUP BY} de verdad solo devuelve los grupos que existen.
         */
        @Override
        public Map<ListingStatus, Long> contarPorEstadoDelVendedor(SellerId vendedor) {
            Map<ListingStatus, Long> porEstado = new EnumMap<>(ListingStatus.class);
            filas.values().stream()
                    .filter(publicacion -> publicacion.sellerId().equals(vendedor))
                    .forEach(publicacion -> porEstado.merge(publicacion.status(), 1L, Long::sum));

            return porEstado;
        }

        @Override
        public boolean hayPendientesDesde(long salto) {
            long esperando = filas.values().stream()
                    .filter(publicacion -> publicacion.status() == ListingStatus.PENDING_REVIEW)
                    .count();

            return esperando > salto;
        }

        @Override
        public List<Listing> publicadasDelVendedor(SellerId vendedor, @Nullable CatalogCursor desde, int limite) {
            return tramo(
                    filas.values().stream()
                            .filter(publicacion -> publicacion.sellerId().equals(vendedor)),
                    desde,
                    limite);
        }

        private static List<Listing> tramo(Stream<Listing> candidatas, @Nullable CatalogCursor desde, int limite) {

            return candidatas
                    .filter(publicacion -> publicacion.status() == ListingStatus.PUBLISHED)
                    .filter(publicacion -> publicacion.publishedAt() != null)
                    .sorted(Comparator.comparing((Listing p) -> Objects.requireNonNull(p.publishedAt()))
                            .thenComparing(p -> p.id().value())
                            .reversed())
                    .filter(publicacion -> desde == null || despuesDe(publicacion, desde))
                    .limit(limite)
                    .toList();
        }

        /** La misma comparacion de pareja que hace `(published_at, id) < (:fecha, :id)`. */
        private static boolean despuesDe(Listing publicacion, CatalogCursor cursor) {
            java.time.Instant cuando = Objects.requireNonNull(publicacion.publishedAt());
            int porFecha = cuando.compareTo(cursor.exigirPublicadaEn());

            return porFecha < 0
                    || (porFecha == 0
                            && publicacion.id().value().compareTo(cursor.id().value()) < 0);
        }

        int cuantas() {
            return filas.size();
        }
    }

    /**
     * El motor de busqueda, en memoria. HU-014.
     *
     * <p><strong>Filtra, ordena y corta igual que el SQL, y eso es lo que le da valor.</strong>
     * Un doble que devolviera lo guardado en cualquier orden dejaria pasar las pruebas del
     * cursor y de los cuatro ordenes, que son justo las que tienen que fallar si el motor de
     * verdad se escribe mal.
     *
     * <p>Consulta las mismas publicaciones que el repositorio y no una copia suya, porque asi
     * es tambien contra PostgreSQL: con ADR-0035 no hay indice aparte que sincronizar, se
     * pregunta a la base que ya es la fuente de verdad.
     *
     * <p><strong>Dos cosas no las reproduce, y conviene saber cuales.</strong> No lematiza
     * -«camisas» no encuentra «camisa»- y no puntua como {@code ts_rank}. Lo primero es
     * comportamiento del diccionario {@code spanish} y lo segundo es una formula del motor;
     * las dos se prueban contra la base de verdad, que es donde se puede. Lo que si reproduce
     * es lo que decide el caso de uso: que solo salga lo publicado, que los filtros se exijan
     * todos, que el orden desempate por identificador y que el cursor compare la pareja
     * entera.
     */
    static final class Motor implements SearchEngine {

        private final Publicaciones publicaciones;

        Motor(Publicaciones publicaciones) {
            this.publicaciones = publicaciones;
        }

        @Override
        public List<SearchHit> buscar(SearchCriteria criterios) {
            List<SearchHit> casan = publicaciones.todas().stream()
                    .filter(Motor::estaPublicada)
                    .filter(publicacion -> casanLosFiltros(publicacion, criterios))
                    .filter(publicacion -> casaElTexto(publicacion, criterios.texto()))
                    .map(publicacion -> new SearchHit(
                            publicacion,
                            criterios.orden().necesitaTexto() ? relevancia(publicacion, criterios.texto()) : null))
                    .sorted(comparador(criterios.orden()))
                    .toList();

            return casan.stream()
                    .filter(resultado -> criterios.desde() == null || despuesDelCursor(resultado, criterios))
                    .limit(criterios.limite())
                    .toList();
        }

        /** RN-081: solo lo publicado, tambien para su dueno. */
        private static boolean estaPublicada(Listing publicacion) {
            return publicacion.status() == ListingStatus.PUBLISHED && publicacion.publishedAt() != null;
        }

        /**
         * Criterio 14: se exigen todos los filtros, y dentro de cada uno basta con un valor.
         *
         * <p>Cada conjunto vacio significa «ese filtro no se puso» y no «ninguno vale». La
         * comprobacion de nulo delante de cada {@code contains} no es defensiva: los campos del
         * producto admiten nulo porque un borrador se guarda a medias, y un conjunto inmutable
         * lanza al preguntarle por nulo.
         */
        private static boolean casanLosFiltros(Listing publicacion, SearchCriteria criterios) {
            Product producto = publicacion.product();

            boolean categoria =
                    criterios.categorias().isEmpty() || criterios.categorias().contains(producto.categoryId());
            boolean condicion = criterios.condiciones().isEmpty()
                    || (producto.condition() != null && criterios.condiciones().contains(producto.condition()));
            boolean color = criterios.colores().isEmpty()
                    || (producto.color() != null && criterios.colores().contains(producto.color()));
            // RN-087: la talla es sistema mas valor, asi que una M por letra nunca casa con una
            // 38 numerica. Lo garantiza que Size compare los dos campos.
            boolean talla = criterios.talla() == null || criterios.talla().equals(producto.size());
            boolean precio = producto.price() != null && criterios.precio().contiene(producto.price());

            return categoria && condicion && color && talla && precio;
        }

        /** RN-082: el texto va contra el titulo y la marca, y no contra la descripcion. */
        private static boolean casaElTexto(Listing publicacion, @Nullable SearchText texto) {
            if (texto == null) {
                return true;
            }

            Set<String> tokens = tokens(textoBuscable(publicacion));
            return palabras(texto).allMatch(tokens::contains);
        }

        /**
         * Cuantas de las palabras buscadas estan en el titulo.
         *
         * <p>No es {@code ts_rank} y no pretende serlo: es una puntuacion determinista que
         * empata a menudo, que es justo lo que hace falta para que las pruebas ejerciten el
         * desempate por identificador. Que la formula de verdad ordene bien se prueba contra
         * PostgreSQL.
         */
        private static double relevancia(Listing publicacion, @Nullable SearchText texto) {
            Product producto = publicacion.product();
            String titulo =
                    normalizar(producto.title() == null ? "" : producto.title().value());

            return palabras(Objects.requireNonNull(texto, "Ordenar por relevancia sin texto que puntuar"))
                    .filter(titulo::contains)
                    .count();
        }

        private static Stream<String> palabras(SearchText texto) {
            return tokens(texto.value()).stream();
        }

        private static String textoBuscable(Listing publicacion) {
            Product producto = publicacion.product();
            String titulo = producto.title() == null ? "" : producto.title().value();
            String marca = producto.brand() == null ? "" : producto.brand().value();

            return titulo + " " + marca;
        }

        /**
         * Las palabras sueltas del texto, no el texto entero.
         *
         * <p><strong>Por token completo y no por subcadena</strong>, y esta es la diferencia
         * que mas importa: con {@code contains}, buscar «amis» encontraba «Camisa» aqui y no
         * encontraba nada contra PostgreSQL, que casa lexemas enteros. ADR-0035 dice que no
         * hay tolerancia a errores de escritura, asi que una coincidencia parcial es
         * exactamente la expectativa que alguien escribiria sin darse cuenta y que solo
         * fallaria en produccion.
         */
        private static Set<String> tokens(String texto) {
            return Arrays.stream(normalizar(texto).split("[^\\p{L}\\p{N}]+"))
                    .filter(palabra -> !palabra.isEmpty())
                    .collect(java.util.stream.Collectors.toSet());
        }

        /** Sin tildes y en minusculas, que es lo que hacen `unaccent` y el diccionario. */
        private static String normalizar(String texto) {
            return Normalizer.normalize(texto, Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "")
                    .toLowerCase(Locale.ROOT);
        }

        /** Los cuatro ordenes de RN-088, todos desempatando por identificador. */
        private static Comparator<SearchHit> comparador(CatalogSort orden) {
            Comparator<SearchHit> porId = Comparator.comparing(
                    resultado -> resultado.publicacion().id().value());

            return switch (orden) {
                case NEWEST ->
                    Comparator.comparing((SearchHit resultado) -> Objects.requireNonNull(
                                    resultado.publicacion().publishedAt()))
                            .thenComparing(porId)
                            .reversed();
                case PRICE_ASC ->
                    Comparator.comparing(
                                    (SearchHit resultado) -> precio(resultado).amount())
                            .thenComparing(porId);
                case PRICE_DESC ->
                    Comparator.comparing(
                                    (SearchHit resultado) -> precio(resultado).amount())
                            .thenComparing(porId)
                            .reversed();
                case RELEVANCE ->
                    Comparator.comparingDouble(SearchHit::exigirRelevancia)
                            .thenComparing(porId)
                            .reversed();
            };
        }

        /**
         * La misma comparacion de pareja que hace PostgreSQL con {@code (clave, id) < (:clave, :id)}.
         *
         * <p>De menor a mayor se avanza hacia arriba y en los otros tres hacia abajo, que es la
         * direccion en la que cada uno recorre el catalogo.
         */
        private static boolean despuesDelCursor(SearchHit resultado, SearchCriteria criterios) {
            CatalogCursor cursor = Objects.requireNonNull(criterios.desde());

            return switch (criterios.orden()) {
                case NEWEST ->
                    haciaAbajo(
                            Objects.requireNonNull(resultado.publicacion().publishedAt())
                                    .compareTo(cursor.exigirPublicadaEn()),
                            resultado,
                            cursor);
                case PRICE_ASC ->
                    haciaArriba(
                            precio(resultado)
                                    .amount()
                                    .compareTo(cursor.exigirPrecio().amount()),
                            resultado,
                            cursor);
                case PRICE_DESC ->
                    haciaAbajo(
                            precio(resultado)
                                    .amount()
                                    .compareTo(cursor.exigirPrecio().amount()),
                            resultado,
                            cursor);
                case RELEVANCE ->
                    haciaAbajo(
                            Double.compare(resultado.exigirRelevancia(), cursor.exigirRelevancia()), resultado, cursor);
            };
        }

        private static boolean haciaAbajo(int porClave, SearchHit resultado, CatalogCursor cursor) {
            return porClave < 0 || (porClave == 0 && comparaId(resultado, cursor) < 0);
        }

        private static boolean haciaArriba(int porClave, SearchHit resultado, CatalogCursor cursor) {
            return porClave > 0 || (porClave == 0 && comparaId(resultado, cursor) > 0);
        }

        private static int comparaId(SearchHit resultado, CatalogCursor cursor) {
            return resultado.publicacion().id().value().compareTo(cursor.id().value());
        }

        private static Money precio(SearchHit resultado) {
            return Objects.requireNonNull(
                    resultado.publicacion().product().price(), "Una publicacion del catalogo sin precio");
        }
    }

    /**
     * Los favoritos, en memoria. HU-011.
     *
     * <p><strong>Ordena y filtra igual que el SQL, y eso es lo que le da valor.</strong> Un
     * doble que devolviera lo guardado en cualquier orden dejaria pasar las pruebas del
     * cursor y del criterio 13, que son justo las que tienen que fallar si el repositorio
     * de verdad se escribe mal.
     *
     * <p>Necesita ver las publicaciones para aplicar RN-071 —solo lo {@code PUBLISHED}— y
     * para eso recibe el mismo {@link Publicaciones} que use la prueba, igual que la
     * consulta real cruza las dos tablas.
     */
    static final class Guardados implements Favorites {

        /** La clave es el par, que es la identidad del favorito y la unicidad de la tabla. */
        private final Map<Favorite, Favorite> filas = new LinkedHashMap<>();

        private final Publicaciones publicaciones;

        Guardados(Publicaciones publicaciones) {
            this.publicaciones = publicaciones;
        }

        /**
         * Idempotente, y de la misma forma que la tabla: el par ya presente se queda con
         * su fecha original en vez de recibir la nueva. Con {@code put} a secas, marcar
         * dos veces moveria el favorito a la cabeza de la lista, que es un comportamiento
         * que el {@code ON CONFLICT DO NOTHING} de verdad no tiene.
         */
        @Override
        public void guardar(Favorite favorito) {
            filas.putIfAbsent(favorito, favorito);
        }

        @Override
        public void quitar(BuyerId quien, ListingId publicacion) {
            filas.remove(Favorite.reconstruir(quien, publicacion, java.time.Instant.EPOCH));
        }

        @Override
        public boolean existe(BuyerId quien, ListingId publicacion) {
            return filas.containsKey(Favorite.reconstruir(quien, publicacion, java.time.Instant.EPOCH));
        }

        @Override
        public List<FavoritedListing> publicadasDe(BuyerId quien, @Nullable FavoriteCursor desde, int limite) {
            return filas.values().stream()
                    .filter(favorito -> favorito.quien().equals(quien))
                    .flatMap(favorito -> publicaciones
                            .buscar(favorito.publicacion())
                            .filter(publicacion -> publicacion.status() == ListingStatus.PUBLISHED)
                            .map(publicacion -> new FavoritedListing(publicacion, favorito.marcadoEn()))
                            .stream())
                    .sorted(Comparator.comparing(FavoritedListing::marcadoEn)
                            .thenComparing(par -> par.publicacion().id().value())
                            .reversed())
                    .filter(par -> desde == null || despuesDelCursor(par, desde))
                    .limit(limite)
                    .toList();
        }

        /**
         * Ordena como el SQL —{@code ORDER BY created_at DESC, listing_id DESC}— y no en el
         * orden de insercion.
         *
         * <p>No lo hacia, y el javadoc de esta clase afirmaba que ordena igual que la base.
         * Hoy no muerde porque ninguna prueba de la descarga afirma orden; el dia que una lo
         * haga, pasaria aqui y podria fallar contra PostgreSQL, que es exactamente lo que
         * estos dobles existen para evitar.
         */
        @Override
        public List<Favorite> todosDe(BuyerId quien) {
            return filas.values().stream()
                    .filter(favorito -> favorito.quien().equals(quien))
                    .sorted(Comparator.comparing(Favorite::marcadoEn)
                            .thenComparing(favorito -> favorito.publicacion().value())
                            .reversed())
                    .toList();
        }

        @Override
        public void borrarTodosDe(BuyerId quien) {
            filas.keySet().removeIf(favorito -> favorito.quien().equals(quien));
        }

        /** La misma comparacion de pareja que hace `(created_at, listing_id) < (:fecha, :id)`. */
        private static boolean despuesDelCursor(FavoritedListing par, FavoriteCursor cursor) {
            int porFecha = par.marcadoEn().compareTo(cursor.marcadoEn());

            return porFecha < 0
                    || (porFecha == 0
                            && par.publicacion()
                                            .id()
                                            .value()
                                            .compareTo(cursor.id().value())
                                    < 0);
        }

        int cuantos() {
            return filas.size();
        }
    }

    /**
     * Las cuentas de quien marca, en memoria.
     *
     * <p>Todas activas salvo las que la prueba cierre. Es lo que permite ejercitar la
     * promesa de {@code datos-personales.md}: el token sobrevive quince minutos al cierre,
     * y marcar un favorito con el tiene que responder que la sesion ya no sirve.
     */
    static final class Cuentas implements BuyerAccounts {

        private final Set<BuyerId> cerradas = new HashSet<>();

        void cerrar(BuyerId quien) {
            cerradas.add(quien);
        }

        @Override
        public boolean estaActiva(BuyerId quien) {
            return !cerradas.contains(quien);
        }
    }

    /**
     * El carrito, en memoria. HU-015.
     *
     * <p>Hermano de {@link Guardados} y distinto en lo que importa: {@link #todosDe} entrega
     * <strong>todo</strong>, tambien lo que dejo de estar publicado. Es la diferencia que
     * RN-094 obliga a que exista, y si este doble filtrara, el criterio 21 se probaria contra
     * una mentira.
     */
    static final class Carrito implements CartItems {

        /** La clave es el par, que es la identidad del item y la unicidad de la tabla. */
        private final Map<CartItem, CartItem> filas = new LinkedHashMap<>();

        /**
         * Idempotente y de la misma forma que la tabla: el par ya presente conserva su fecha
         * y su precio de entrada en vez de recibir los nuevos.
         *
         * <p>Que conserve el precio no es un detalle: si la segunda escritura lo pisara,
         * volver a pulsar sobre algo que subio de precio borraria justo el aviso de que
         * subio, y el {@code ON CONFLICT DO NOTHING} de verdad no hace eso.
         */
        @Override
        public void guardar(CartItem item) {
            filas.putIfAbsent(item, item);
        }

        @Override
        public void quitar(BuyerId quien, ListingId publicacion) {
            filas.remove(CartItem.reconstruir(quien, publicacion, Instant.EPOCH, Money.dePesos(0)));
        }

        @Override
        public boolean existe(BuyerId quien, ListingId publicacion) {
            return filas.containsKey(CartItem.reconstruir(quien, publicacion, Instant.EPOCH, Money.dePesos(0)));
        }

        /** Sin filtrar por estado, y ordenado como el SQL: lo mas reciente primero. */
        @Override
        public List<CartItem> todosDe(BuyerId quien) {
            return filas.values().stream()
                    .filter(item -> item.quien().equals(quien))
                    .sorted(Comparator.comparing(CartItem::agregadoEn)
                            .thenComparing(item -> item.publicacion().value())
                            .reversed())
                    .toList();
        }

        @Override
        public int cuantosLleva(BuyerId quien) {
            return (int) filas.values().stream()
                    .filter(item -> item.quien().equals(quien))
                    .count();
        }

        @Override
        public void borrarTodosDe(BuyerId quien) {
            filas.keySet().removeIf(item -> item.quien().equals(quien));
        }
    }

    /**
     * Los perfiles publicos, en memoria.
     *
     * <p>Devuelve vacio para quien no se haya dado de alta aqui, que es lo que hace el
     * adaptador de verdad con una cuenta inexistente o cerrada.
     */
    static final class Perfiles implements SellerProfiles {

        private final Map<SellerId, SellerProfileView> filas = new LinkedHashMap<>();

        void alta(SellerId vendedor, String nombre, boolean verificado) {
            filas.put(vendedor, new SellerProfileView(vendedor, nombre, null, verificado));
        }

        @Override
        public Optional<SellerProfileView> buscar(SellerId vendedor) {
            return Optional.ofNullable(filas.get(vendedor));
        }
    }

    static final class Arbol implements Categories {

        private final Map<CategoryId, Category> filas = new HashMap<>();

        /** Una hoja activa es ella misma; una familia activa son sus hojas activas. */
        @Override
        public List<CategoryId> publicablesBajo(CategoryId id) {
            Category elegida = filas.get(id);
            if (elegida == null || !elegida.active()) {
                return List.of();
            }

            if (!elegida.esFamilia()) {
                return List.of(elegida.id());
            }

            return filas.values().stream()
                    .filter(Category::active)
                    .filter(categoria -> id.equals(categoria.parentId()))
                    .map(Category::id)
                    .toList();
        }

        /**
         * El arbol como lo pide una pantalla.
         *
         * <p>Los nombres visibles no estan en {@code Category}, asi que aqui se componen
         * del slug: a estas pruebas les importa que el arbol salga armado por familias,
         * no como se llama cada categoria.
         */
        @Override
        public List<CategoryView> arbolActivo() {
            Map<CategoryId, List<CategoryView>> hijas = new LinkedHashMap<>();

            filas.values().stream()
                    .filter(categoria -> !categoria.esFamilia() && categoria.active())
                    .forEach(hija -> hijas.computeIfAbsent(
                                    Objects.requireNonNull(hija.parentId()), cualquiera -> new ArrayList<>())
                            .add(vista(hija, "familia", List.of())));

            return filas.values().stream()
                    .filter(Category::esFamilia)
                    .filter(Category::active)
                    .map(familia -> vista(familia, null, hijas.getOrDefault(familia.id(), List.of())))
                    .toList();
        }

        private static CategoryView vista(Category categoria, @Nullable String familiaSlug, List<CategoryView> hijas) {
            return new CategoryView(
                    categoria.id(),
                    categoria.slug(),
                    categoria.slug(),
                    categoria.slug(),
                    familiaSlug,
                    categoria.sizeSystems(),
                    categoria.measurementGroup() == null
                            ? Set.of()
                            : categoria.measurementGroup().obligatorias(),
                    categoria.allowsUsed(),
                    hijas);
        }

        Category agregar(Category categoria) {
            filas.put(categoria.id(), categoria);
            return categoria;
        }

        /** Una hoja de moda, con las cuatro condiciones y medidas de parte superior. */
        Category camisas() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "camisas-y-blusas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ALPHA),
                    MeasurementGroup.TOP,
                    true,
                    true));
        }

        /** Una hoja de tecnologia: RN-064, solo lo nuevo. */
        Category celulares() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "celulares-y-tabletas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ONE_SIZE),
                    MeasurementGroup.DEVICE,
                    false,
                    true));
        }

        Category retirada() {
            return agregar(new Category(
                    CategoryId.nuevo(),
                    "gafas",
                    CategoryId.nuevo(),
                    Set.of(SizeSystem.ONE_SIZE),
                    MeasurementGroup.ACCESSORY_FLAT,
                    true,
                    false));
        }

        @Override
        public Optional<Category> buscar(CategoryId id) {
            return Optional.ofNullable(filas.get(id));
        }
    }

    /**
     * RN-011 y RN-013.
     *
     * <p><strong>Mira el vendedor que le preguntan.</strong> El doble anterior devolvia
     * un booleano de instancia e ignoraba el argumento, asi que un caso de uso que
     * consultara la elegibilidad de otra persona —el moderador, el dueno anterior—
     * pasaba todas las pruebas. Es justo el fallo que un doble tiene que atrapar.
     */
    static final class Elegibilidad implements SellerEligibility {

        private final Set<SellerId> revocados = new HashSet<>();

        void revocar(SellerId vendedor) {
            revocados.add(vendedor);
        }

        @Override
        public boolean puedePublicar(SellerId vendedor) {
            return !revocados.contains(vendedor);
        }
    }

    static final class Bitacora implements ModerationLog {

        /**
         * Guarda todos los argumentos: un doble que descarta uno no puede probarlo.
         *
         * <p>El actor es {@code Object} porque quien escribe no siempre es un moderador: el
         * envio lo anota el vendedor (HU-013). Tiparlo obligaria a dos listas o a convertir
         * un {@link SellerId} en {@link ModeratorId}, que es justo lo que el puerto separa
         * en dos metodos para no hacer.
         */
        record Entrada(
                ListingId publicacion,
                Object actor,
                ModerationAction accion,
                @Nullable String motivo,
                @Nullable String nota,
                Instant cuando) {}

        private final List<Entrada> entradas = new ArrayList<>();

        /**
         * Si alguien ha leido el rastro.
         *
         * <p>Lo unico que este doble observa ademas de lo que guarda, y hace falta para una
         * prueba concreta: que el caso de uso comprueba de quien es la publicacion
         * <strong>antes</strong> de pedir la bitacora. Sin esto, esa prueba solo podria
         * afirmar que la llamada falla, que es lo mismo que ocurriria si preguntara primero
         * y tirara la respuesta al final.
         */
        private boolean leido;

        @Override
        public void registrar(
                ListingId publicacion,
                ModeratorId actor,
                ModerationAction accion,
                @Nullable String motivo,
                @Nullable String nota,
                Instant cuando) {
            entradas.add(new Entrada(publicacion, actor, accion, motivo, nota, cuando));
        }

        @Override
        public void registrarEnvio(ListingId publicacion, SellerId vendedor, Instant cuando) {
            entradas.add(new Entrada(publicacion, vendedor, ModerationAction.SUBMITTED, null, null, cuando));
        }

        /**
         * Lo mas reciente primero, como el adaptador de verdad.
         *
         * <p><strong>Ordena por fecha y no por orden de insercion.</strong> Invertir la
         * insercion era lo comodo y era mentira: el adaptador ordena por
         * {@code created_at DESC, id DESC}, asi que un caso de uso que pasara un instante
         * anterior al de un evento ya escrito -que es exactamente la clase del defecto de los
         * dos relojes- daba aqui un orden correcto y en PostgreSQL uno distinto. Con la
         * insercion como unico criterio, la prueba de las dos vueltas no probaba el orden:
         * pasaba igual con los cuatro eventos fechados al reves.
         *
         * <p>El desempate por posicion de insercion, descendente, imita al del identificador,
         * que en la tabla es creciente (Uuid7).
         */
        @Override
        public List<ModerationEvent> historial(ListingId publicacion) {
            leido = true;

            List<Entrada> suyas = new ArrayList<>();
            for (Entrada entrada : entradas) {
                if (entrada.publicacion().equals(publicacion)) {
                    suyas.add(entrada);
                }
            }

            // Estable, asi que a igual fecha conserva el orden de insercion; invertir despues
            // lo deja descendente en las dos claves, como el ORDER BY del adaptador.
            suyas.sort(Comparator.comparing(Entrada::cuando));
            Collections.reverse(suyas);

            List<ModerationEvent> rastro = new ArrayList<>();
            for (Entrada entrada : suyas) {
                rastro.add(new ModerationEvent(
                        entrada.accion(),
                        entrada.motivo() == null ? null : ListingRejectionReason.valueOf(entrada.motivo()),
                        entrada.cuando()));
            }
            return List.copyOf(rastro);
        }

        List<Entrada> entradas() {
            return List.copyOf(entradas);
        }

        /**
         * Solo lo que decidio un moderador.
         *
         * <p>Desde HU-013 la bitacora anota tambien el envio a revision, que hace el
         * vendedor. Una prueba sobre lo que decide un moderador que afirme sobre
         * {@link #entradas} cuenta tambien los envios y falla por un motivo que no tiene
         * nada que ver con lo que esta probando.
         */
        List<Entrada> decisiones() {
            List<Entrada> soloDecisiones = new ArrayList<>();
            for (Entrada entrada : entradas) {
                if (entrada.accion() != ModerationAction.SUBMITTED) {
                    soloDecisiones.add(entrada);
                }
            }
            return List.copyOf(soloDecisiones);
        }

        boolean seLeyo() {
            return leido;
        }

        void olvidarQueSeLeyo() {
            leido = false;
        }
    }

    /**
     * Los avisos del criterio 26.
     *
     * <p>Guarda la nota, que es el dato del criterio 22. El doble anterior la tiraba, asi
     * que invertir los argumentos de motivo y nota, o dejar de pasarla al correo, no lo
     * habria notado ninguna prueba.
     */
    static final class Avisos implements ListingNotifier {

        record Aviso(
                String tipo,
                ListingId publicacion,
                @Nullable String nota) {}

        private final List<Aviso> enviados = new ArrayList<>();

        @Override
        public void publicacionAprobada(Listing publicacion) {
            enviados.add(new Aviso("aprobada", publicacion.id(), null));
        }

        @Override
        public void publicacionRechazada(Listing publicacion, @Nullable String nota) {
            enviados.add(new Aviso("rechazada", publicacion.id(), nota));
        }

        @Override
        public void publicacionRetirada(Listing publicacion, ListingRejectionReason motivo, @Nullable String nota) {
            enviados.add(new Aviso("retirada", publicacion.id(), nota));
        }

        List<Aviso> enviados() {
            return List.copyOf(enviados);
        }
    }

    /** Almacen publico en memoria, para comprobar que lo que se borra se borra. */
    static final class Almacen implements PublicFileStore {

        private final Set<FileKey> guardados = new java.util.LinkedHashSet<>();
        private int contador;

        @Override
        public FileKey guardar(String carpeta, NormalizedImage imagen) {
            FileKey clave = new FileKey(carpeta + "/prueba-" + (++contador) + ".jpg");
            guardados.add(clave);
            return clave;
        }

        @Override
        public void borrar(FileKey clave) {
            guardados.remove(clave);
        }

        @Override
        public java.net.URI direccionDe(FileKey clave) {
            return java.net.URI.create("https://ejemplo.co/" + clave.value());
        }

        Set<FileKey> guardados() {
            return Set.copyOf(guardados);
        }
    }
}
