package co.sendik.catalog.model;

import org.jspecify.annotations.Nullable;

/**
 * En que orden salen los resultados. RN-088, criterios 16, 17 y 18.
 *
 * <p><strong>Cuatro y no mas.</strong> Ordenar por popularidad o por favoritos queda fuera
 * por RN-070: los favoritos son privados y no existe cifra publica derivada de ellos, asi
 * que un orden por favoritos seria esa cifra por la puerta de atras.
 *
 * <p><strong>Aqui no hay orden patrocinado y no lo puede haber</strong> (RN-084). No es una
 * promesa escrita en una regla: es que la enumeracion no tiene un quinto valor, asi que
 * adelantar un resultado por haberlo pagado exigiria agregarlo aqui, a la vista de todos,
 * que es exactamente lo que RN-084 pide.
 *
 * <p><strong>Todos desempatan por identificador</strong>, y eso no es un detalle de la
 * consulta: sin desempate, dos publicaciones con el mismo precio —o con la misma
 * relevancia, que con {@code ts_rank} es lo normal— quedan en orden indefinido, y un cursor
 * sobre un orden indefinido repite o se salta filas.
 */
public enum CatalogSort {

    /** Lo que mejor casa con el texto. Sin texto no significa nada: ver {@link #efectivo}. */
    RELEVANCE,

    /** Lo mas reciente primero, que es el orden del catalogo de HU-009. */
    NEWEST,

    PRICE_ASC,

    PRICE_DESC;

    /**
     * El orden que de verdad se aplica.
     *
     * <p>Resuelve las dos cosas que RN-088 dice y una tercera que no dice pero se deduce:
     *
     * <ul>
     *   <li>Sin orden pedido y con texto, relevancia (criterio 16).
     *   <li>Sin orden pedido y sin texto, lo mas reciente (criterio 17).
     *   <li><strong>Relevancia pedida sin texto, lo mas reciente.</strong> Sin texto no hay
     *       nada que puntuar: todas las filas empatarian en cero y el orden quedaria en
     *       manos del desempate, que es un orden que nadie pidio y que ademas no se puede
     *       explicar en la pantalla. Se degrada al del catalogo en vez de responder 400
     *       porque no es un error del cliente: pedir «lo mas relevante» y borrar el texto
     *       es un gesto normal en la pantalla.
     * </ul>
     *
     * <p>Es esto, y no lo pedido, lo que queda sellado en el cursor: el criterio 22 compara
     * el orden con el que el cursor nacio contra el que se esta aplicando, y comparar
     * contra el pedido dejaria que dos peticiones equivalentes se rechazaran entre si.
     */
    public static CatalogSort efectivo(@Nullable CatalogSort pedido, boolean hayTexto) {
        if (pedido == null) {
            return hayTexto ? RELEVANCE : NEWEST;
        }
        return pedido == RELEVANCE && !hayTexto ? NEWEST : pedido;
    }

    /** Si necesita el texto para poder ordenar. Solo la relevancia. */
    public boolean necesitaTexto() {
        return this == RELEVANCE;
    }
}
