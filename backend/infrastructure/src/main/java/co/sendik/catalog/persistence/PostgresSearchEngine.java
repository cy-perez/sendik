package co.sendik.catalog.persistence;

import static co.sendik.catalog.persistence.JdbcListingRepository.COLUMNAS_BASE;
import static co.sendik.catalog.persistence.JdbcListingRepository.DESDE_BASE;
import static co.sendik.catalog.persistence.JdbcListingRepository.filaAPublicacion;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.SearchCriteria;
import co.sendik.catalog.dto.SearchHit;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.port.out.SearchEngine;
import java.sql.Timestamp;
import java.util.List;
import java.util.stream.IntStream;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.simple.JdbcClient.StatementSpec;
import org.springframework.stereotype.Component;

/**
 * La busqueda, contra PostgreSQL. HU-014, ADR-0035.
 *
 * <p><strong>Aqui vive entera la decision de ADR-0035</strong>, y esa es toda la gracia del
 * puerto: cambiar a Typesense es escribir otra clase como esta y cambiar un bean. Ni el
 * dominio, ni el caso de uso, ni el endpoint, ni la pantalla se enteran.
 *
 * <p><strong>RN-081 se escribe aqui una sola vez.</strong> No asi RN-068, su gemela del
 * catalogo: el escaparate de un vendedor sigue filtrando por {@code PUBLISHED} en
 * {@code JdbcListingRepository}, porque es otra consulta. Son dos sitios donde esta escrito
 * el mismo estado, y conviene saberlo antes de tocar uno de los dos.
 *
 * <p><strong>No hay indice que sincronizar</strong>, y eso no es una simplificacion sino la
 * razon principal por la que ADR-0035 eligio esto: se consulta la misma base que ya es la
 * fuente de verdad, asi que no existe la ventana en la que el indice y la base discrepan, ni
 * hay que reaccionar a publicar, editar, aprobar, pausar, vender y archivar.
 *
 * <p><strong>La proyeccion se comparte con {@code JdbcListingRepository}</strong> y no se
 * copia: son las mismas treinta columnas y el mismo mapeo de fila. Copiarlas habria dejado
 * dos proyecciones que alguien tendria que acordarse de cambiar a la vez, que es justo lo
 * que HU-011 ya evito una vez.
 *
 * <p><strong>Lo que se acepta perder esta escrito en la ADR y conviene tenerlo delante:</strong>
 * no hay tolerancia a errores de escritura -quien escriba «camisa oxfrod» no encuentra nada-,
 * no hay facetas con conteo, y la relevancia es la de {@code ts_rank} sin calibrar.
 */
@Component
public class PostgresSearchEngine implements SearchEngine {

    /**
     * El texto sobre el que se busca. RN-082: el titulo y la marca, no la descripcion.
     *
     * <p><strong>Tiene que ser identica a la expresion del indice de V18.</strong> Si dejan
     * de coincidir, el planificador no usa el indice y nadie se entera: la busqueda devuelve
     * exactamente lo mismo, solo que recorriendo la tabla. Por eso es una constante y no
     * texto suelto dentro de una consulta, y por eso hay una prueba que comprueba el plan.
     */
    public static final String TEXTO_BUSCABLE =
            "to_tsvector('spanish', sendik_unaccent(coalesce(p.title, '') || ' ' || coalesce(p.brand, '')))";

    /**
     * Lo que se pregunta contra ese texto.
     *
     * <p>{@code plainto_tsquery} exige <strong>todas</strong> las palabras: quien escribe
     * «camisa lino» pide las dos, no cualquiera de ellas. Es lo que hace que acotar
     * escribiendo mas funcione como la gente espera.
     *
     * <p>Y no interpreta operadores: un texto con comillas, parentesis o {@code &} entra
     * como palabras y no como sintaxis. Con {@code to_tsquery} habria que sanear, y sanear
     * sintaxis ajena es la clase de codigo que se rompe con la primera comilla.
     */
    private static final String CONSULTA_DE_TEXTO = "plainto_tsquery('spanish', sendik_unaccent(:texto))";

    /**
     * La condicion entera, publica para que una prueba pueda pedirle el plan a PostgreSQL.
     *
     * <p>Es la unica razon de que no sea privada, y merece la pena: <strong>una consulta que
     * pasa por el indice equivocado devuelve exactamente lo mismo</strong> y no se nota en
     * ninguna otra prueba. La de integracion pide {@code EXPLAIN} de esta misma cadena y
     * comprueba que el indice de V18 aparece; escrita a mano en la prueba, comprobaria que
     * una copia coincide consigo misma.
     */
    public static final String CONDICION_DE_TEXTO = TEXTO_BUSCABLE + " @@ " + CONSULTA_DE_TEXTO;

    private static final String RELEVANCIA = "ts_rank(" + TEXTO_BUSCABLE + ", " + CONSULTA_DE_TEXTO + ")";

    private final JdbcClient jdbc;

    public PostgresSearchEngine(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<SearchHit> buscar(SearchCriteria criterios) {
        boolean puntua = criterios.orden().necesitaTexto();

        StatementSpec consulta = jdbc.sql(sentencia(criterios, puntua));
        consulta = conParametros(consulta, criterios);

        List<SearchHit> crudos = consulta.query((fila, numero) ->
                        new SearchHit(filaAPublicacion(fila, numero), puntua ? fila.getDouble("relevancia") : null))
                .list();

        return conPortadaDelTramo(crudos);
    }

    /**
     * La sentencia, armada con las condiciones que de verdad se pidieron.
     *
     * <p>Se construye y no se escribe entera con condiciones neutras del tipo
     * {@code (:color IS NULL OR ...)}: esas condiciones esconden del planificador cuales
     * filtros hay, y con ellas el plan es el mismo se busque lo que se busque.
     *
     * <p>Nada de lo que se concatena viene del cliente. Los filtros entran por parametro con
     * nombre y lo unico que decide la forma de la cadena son enumeraciones del dominio y si
     * un valor esta o no esta.
     */
    private static String sentencia(SearchCriteria criterios, boolean puntua) {
        StringBuilder sql = new StringBuilder();

        // La columna de puntuacion solo cuando alguien la va a leer: calcularla para
        // ordenar por fecha seria trabajo que nadie mira.
        //
        // Se compone de las dos mitades de la proyeccion y no recortando la sentencia
        // entera: recortarle el "SELECT " de delante funciona hasta que alguien escriba
        // "SELECT DISTINCT" o le ponga un salto de linea, y entonces el SQL sale roto en
        // ejecucion sin que la compilacion vea nada.
        sql.append("SELECT ");
        if (puntua) {
            sql.append(RELEVANCIA).append(" AS relevancia, ");
        }
        sql.append(COLUMNAS_BASE).append(DESDE_BASE);

        // RN-081. Se escribe aqui una sola vez y es lo primero de la clausula, para que
        // leerla sea inmediato: de los siete estados, uno.
        sql.append(" WHERE l.status = 'PUBLISHED'");

        if (!criterios.categorias().isEmpty()) {
            sql.append(" AND p.category_id IN (:categorias)");
        }
        if (criterios.texto() != null) {
            sql.append(" AND ").append(CONDICION_DE_TEXTO);
        }
        if (!criterios.condiciones().isEmpty()) {
            sql.append(" AND p.condition IN (:condiciones)");
        }
        if (!criterios.colores().isEmpty()) {
            sql.append(" AND p.color IN (:colores)");
        }
        if (criterios.talla() != null) {
            // RN-087: los dos campos juntos. Filtrar solo por el valor mezclaria una M por
            // letra con una M que no existe en otro sistema, y sobre todo una 38 numerica
            // con una 38 de calzado.
            sql.append(" AND p.size_system = :sistemaDeTalla AND p.size_value = :talla");
        }
        if (criterios.precio().minimo() != null) {
            sql.append(" AND p.price >= :precioMinimo");
        }
        if (criterios.precio().maximo() != null) {
            sql.append(" AND p.price <= :precioMaximo");
        }

        sql.append(condicionDelCursor(criterios));
        sql.append(" ORDER BY ").append(orden(criterios.orden()));
        sql.append(" LIMIT :limite");

        return sql.toString();
    }

    /**
     * Por donde sigue el tramo, como comparacion de pareja.
     *
     * <p>{@code (clave, id) < (:clave, :id)} es una comparacion de filas de PostgreSQL y no
     * dos condiciones sueltas unidas por AND: con dos publicaciones que empatan en la clave
     * -y en precio y en relevancia el empate es la norma- filtrar solo por la clave se salta
     * una del par, y filtrar por {@code <=} la repite para siempre.
     *
     * <p>De menor a mayor se avanza hacia arriba y en los otros tres hacia abajo, que es la
     * direccion en la que cada orden recorre el catalogo.
     */
    private static String condicionDelCursor(SearchCriteria criterios) {
        if (criterios.desde() == null) {
            return "";
        }

        return switch (criterios.orden()) {
            case NEWEST -> " AND (l.published_at, l.id) < (:publicadaEn, :ultimaId)";
            case PRICE_ASC -> " AND (p.price, l.id) > (:precioDelCursor, :ultimaId)";
            case PRICE_DESC -> " AND (p.price, l.id) < (:precioDelCursor, :ultimaId)";
            // El casteo no es cosmetico: ts_rank devuelve `real` y el parametro llega como
            // `float8`. Sin el, la comparacion mezcla precisiones y el empate exacto -que es
            // lo que el desempate por identificador tiene que resolver- deja de serlo.
            case RELEVANCE -> " AND (" + RELEVANCIA + "::float8, l.id) < (:relevanciaDelCursor, :ultimaId)";
        };
    }

    /** Los cuatro de RN-088, todos desempatando por identificador. */
    private static String orden(CatalogSort orden) {
        return switch (orden) {
            case NEWEST -> "l.published_at DESC, l.id DESC";
            case PRICE_ASC -> "p.price ASC, l.id ASC";
            case PRICE_DESC -> "p.price DESC, l.id DESC";
            case RELEVANCE -> RELEVANCIA + " DESC, l.id DESC";
        };
    }

    private static StatementSpec conParametros(StatementSpec consulta, SearchCriteria criterios) {
        StatementSpec conValores = consulta.param("limite", criterios.limite());

        if (!criterios.categorias().isEmpty()) {
            conValores = conValores.param(
                    "categorias",
                    criterios.categorias().stream().map(CategoryId::value).toList());
        }
        if (criterios.texto() != null) {
            conValores = conValores.param("texto", criterios.texto().value());
        }
        if (!criterios.condiciones().isEmpty()) {
            conValores = conValores.param(
                    "condiciones",
                    criterios.condiciones().stream().map(Condition::name).toList());
        }
        if (!criterios.colores().isEmpty()) {
            conValores = conValores.param(
                    "colores", criterios.colores().stream().map(Color::name).toList());
        }

        Size talla = criterios.talla();
        if (talla != null) {
            conValores =
                    conValores.param("sistemaDeTalla", talla.system().name()).param("talla", talla.value());
        }
        if (criterios.precio().minimo() != null) {
            conValores =
                    conValores.param("precioMinimo", criterios.precio().minimo().enPesos());
        }
        if (criterios.precio().maximo() != null) {
            conValores =
                    conValores.param("precioMaximo", criterios.precio().maximo().enPesos());
        }

        return conParametrosDelCursor(conValores, criterios);
    }

    private static StatementSpec conParametrosDelCursor(StatementSpec consulta, SearchCriteria criterios) {
        CatalogCursor desde = criterios.desde();
        if (desde == null) {
            return consulta;
        }

        StatementSpec conCursor = consulta.param("ultimaId", desde.id().value());

        return switch (criterios.orden()) {
            case NEWEST -> conCursor.param("publicadaEn", Timestamp.from(desde.exigirPublicadaEn()));
            case PRICE_ASC, PRICE_DESC ->
                conCursor.param("precioDelCursor", desde.exigirPrecio().enPesos());
            case RELEVANCE -> conCursor.param("relevanciaDelCursor", desde.exigirRelevancia());
        };
    }

    /**
     * Las portadas de todo el tramo, en una sola consulta, conservando la puntuacion.
     *
     * <p>El ayudante que ya existe trabaja sobre publicaciones, asi que la puntuacion se
     * separa y se vuelve a unir por posicion. Se puede porque ese ayudante mapea en orden y
     * devuelve tantas como recibe.
     */
    private List<SearchHit> conPortadaDelTramo(List<SearchHit> crudos) {
        List<Listing> conPortada = JdbcListingRepository.conPortadas(
                jdbc, crudos.stream().map(SearchHit::publicacion).toList());

        return IntStream.range(0, crudos.size())
                .mapToObj(posicion -> new SearchHit(
                        conPortada.get(posicion), crudos.get(posicion).relevancia()))
                .toList();
    }
}
