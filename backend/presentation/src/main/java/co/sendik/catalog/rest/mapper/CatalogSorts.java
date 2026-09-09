package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.model.CatalogSort;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * El parametro {@code sort} del contrato, traducido al orden del dominio. HU-014, RN-088.
 *
 * <p><strong>La forma es la que {@code contrato-api.md} ya fijaba</strong> —{@code campo} y
 * {@code direccion} separados por coma, contra una lista blanca— y no una invencion de esta
 * historia. La unica que no la sigue es {@code relevance}, y no por descuido: la relevancia
 * no tiene direccion. Ordenarla al reves seria pedir «lo que peor casa primero», que no es
 * una pantalla que exista.
 *
 * <p><strong>Lista blanca y no {@code valueOf} sobre la enumeracion.</strong> Los nombres del
 * dominio son detalle interno; publicarlos convertiria {@code PRICE_ASC} en contrato y ataria
 * el nombre de una constante de Java a lo que escriben los clientes. Ademas, un
 * {@code valueOf} directo dejaria entrar cualquier valor que alguien agregara a la
 * enumeracion sin pasar por aqui, que es exactamente lo que RN-084 no quiere que pueda pasar
 * en silencio.
 *
 * <p>Lo que no esta en la lista se rechaza con 400, como manda el contrato para los
 * parametros no reconocidos: un orden mal escrito que se ignorara devolveria el catalogo en
 * otro orden sin decirlo, y eso se lee como que el sitio esta roto.
 */
public final class CatalogSorts {

    private static final Map<String, CatalogSort> ADMITIDOS = admitidos();

    private CatalogSorts() {}

    private static Map<String, CatalogSort> admitidos() {
        Map<String, CatalogSort> lista = new LinkedHashMap<>();
        lista.put("relevance", CatalogSort.RELEVANCE);
        lista.put("publishedAt,desc", CatalogSort.NEWEST);
        lista.put("price,asc", CatalogSort.PRICE_ASC);
        lista.put("price,desc", CatalogSort.PRICE_DESC);

        return Map.copyOf(lista);
    }

    /**
     * @param sort lo que escribio el cliente, o nulo si no pidio orden
     * @return el orden pedido, o nulo para el de omision de RN-088, que resuelve el dominio
     * @throws IllegalArgumentException si no esta en la lista blanca. Sale como 400
     */
    public static @Nullable CatalogSort orden(@Nullable String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }

        CatalogSort orden = ADMITIDOS.get(sort.trim());
        if (orden == null) {
            throw new IllegalArgumentException("El orden pedido no existe");
        }
        return orden;
    }
}
