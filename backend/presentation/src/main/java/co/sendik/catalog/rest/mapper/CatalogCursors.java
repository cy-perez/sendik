package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.ListingId;
import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * El cursor del catalogo, de valores tipados a cadena opaca y vuelta. HU-009, criterio 3;
 * HU-014, criterio 22.
 *
 * <p>La mecanica del transporte —base64 de URL, el JSON de dentro y el rechazo de lo que no
 * se entiende— vive en {@link Cursores} desde HU-011, porque la lista de favoritos necesita
 * exactamente la misma y copiarla habria dejado dos sitios donde dejar de rechazar un cursor
 * corrupto. Lo que se queda aqui es lo unico propio del catalogo: que ese cursor es un orden
 * y la clave de ese orden.
 *
 * <p><strong>El orden viaja dentro y esa es la novedad de HU-014.</strong> Sin el, el
 * criterio 22 no se puede cumplir: un cursor de precio usado en una busqueda por fecha no
 * produce una pagina incompleta sino una arbitraria, y el servidor no tendria con que
 * distinguirlo de uno legitimo.
 *
 * <p><strong>El nombre del orden que viaja es el del dominio</strong> y no el del contrato.
 * No es una inconsistencia: el cursor es opaco, nadie de fuera lo lee, y hacerlo pasar por la
 * traduccion del borde solo agregaria un sitio donde equivocarse. Lo que si es del contrato
 * es el {@code sort} que el cliente escribe, y de eso se ocupa {@link CatalogSorts}.
 *
 * <p><strong>El tipo no se comparte y eso es lo importante.</strong> {@link CatalogCursor} y
 * {@code FavoriteCursor} siguen siendo records distintos, asi que el compilador impide que el
 * cursor de un listado sirva en el otro; si lo permitiera, pasar el de aqui a la lista de
 * favoritos devolveria un tramo arbitrario en vez de un error.
 */
public final class CatalogCursors {

    /** Con que orden nacio. */
    private static final String CAMPO_ORDEN = "o";

    /** La clave del orden: una fecha, un precio o una puntuacion, segun cual sea. */
    private static final String CAMPO_CLAVE = "k";

    private CatalogCursors() {}

    public static @Nullable String texto(@Nullable CatalogCursor cursor) {
        if (cursor == null) {
            return null;
        }

        Map<String, String> campos = new LinkedHashMap<>();
        campos.put(CAMPO_ORDEN, cursor.orden().name());
        campos.put(CAMPO_CLAVE, clave(cursor));
        campos.put(Cursores.CAMPO_ID, cursor.id().value().toString());

        return Cursores.texto(campos);
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de este listado. Sale como 400,
     *     que es lo que el manejador ya hace con esta excepcion. Que el orden que trae sea el
     *     que se esta pidiendo no se comprueba aqui sino en {@code ListCatalogQuery}: aqui no
     *     se sabe que se pidio, y esa comparacion es del criterio 22
     */
    public static @Nullable CatalogCursor cursor(@Nullable String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }

        JsonNode nodo = Cursores.nodo(texto);
        CatalogSort orden = orden(Cursores.exigir(nodo, CAMPO_ORDEN));
        ListingId id = new ListingId(Cursores.uuid(nodo, Cursores.CAMPO_ID));
        String clave = Cursores.exigir(nodo, CAMPO_CLAVE);

        return switch (orden) {
            case NEWEST -> CatalogCursor.porFecha(instante(clave), id);
            case PRICE_ASC, PRICE_DESC -> CatalogCursor.porPrecio(orden, pesos(clave), id);
            case RELEVANCE -> CatalogCursor.porRelevancia(numero(clave), id);
        };
    }

    /** Una sola clave y no tres campos opcionales: el cursor lleva la de su orden y ya. */
    private static String clave(CatalogCursor cursor) {
        return switch (cursor.orden()) {
            case NEWEST -> cursor.exigirPublicadaEn().toString();
            case PRICE_ASC, PRICE_DESC -> String.valueOf(cursor.exigirPrecio().enPesos());
            case RELEVANCE -> String.valueOf(cursor.exigirRelevancia());
        };
    }

    /**
     * Un orden que no existe no es un cursor de este listado.
     *
     * <p>Sale como 400 y no como «orden por omision»: si un cursor con un orden inventado se
     * resolviera al del catalogo, quien lo mandara recibiria un tramo coherente de otra lista.
     */
    private static CatalogSort orden(String nombre) {
        try {
            return CatalogSort.valueOf(nombre);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }

    private static Instant instante(String clave) {
        try {
            return Instant.parse(clave);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }

    private static Money pesos(String clave) {
        try {
            return Money.dePesos(Long.parseLong(clave));
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }

    private static double numero(String clave) {
        try {
            return Double.parseDouble(clave);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }
}
