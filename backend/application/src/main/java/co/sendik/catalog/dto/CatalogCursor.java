package co.sendik.catalog.dto;

import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.ListingId;
import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Por donde sigue el catalogo. HU-009, y con el orden desde HU-014.
 *
 * <p><strong>Por cursor y no por numero de pagina</strong>, y el motivo lo escribe
 * contrato-api.md: sobre contenido que se inserta constantemente, la paginacion por
 * desplazamiento repite y salta elementos. Si mientras alguien mira la primera pantalla se
 * aprueban tres publicaciones, la pagina 2 por desplazamiento le devuelve tres que ya vio;
 * y si se archivan tres, se salta tres que nunca vera.
 *
 * <p><strong>Lleva el orden con el que nacio, y esa es la novedad de HU-014.</strong> Con
 * un solo orden posible bastaba con la fecha; con cuatro, un cursor no significa nada fuera
 * del orden que lo produjo. Mandar el de «mas barato primero» pidiendo «mas reciente
 * primero» no da una pagina incompleta: da una pagina arbitraria, porque la condicion de
 * continuidad se compara contra una columna que ya no ordena nada. Se responde 400
 * (criterio 22), y para poder responderlo hay que saber de donde vino.
 *
 * <p><strong>Y lleva la clave de ese orden, no las cuatro.</strong> El constructor exige
 * exactamente la que corresponde: un cursor de precio con una fecha dentro seria un cursor
 * que dos consultas distintas podrian interpretar de dos maneras.
 *
 * <p>El identificador esta siempre, y no es adorno en ninguno de los cuatro ordenes: la
 * fecha de publicacion se repite, el precio se repite mucho mas, y la relevancia de
 * {@code ts_rank} empata tanto que el empate es la norma y no la excepcion. Sin desempate
 * el orden entre iguales es indefinido, y un cursor sobre un orden indefinido se salta
 * filas o las repite, que es justo lo que la paginacion por cursor existe para evitar.
 *
 * <p><strong>No sabe nada de base64 ni de JSON.</strong> Esto es la pregunta; convertirla en
 * una cadena opaca que viaje por HTTP es cosa del borde, y por eso el codificador vive en
 * {@code presentation}. Aqui es un orden y unos valores tipados.
 *
 * @param orden con que orden se produjo, que es contra lo que se comprueba el criterio 22
 * @param id cual era el ultimo elemento entregado, para desempatar
 * @param publicadaEn cuando se publico, solo en {@link CatalogSort#NEWEST}
 * @param precio cuanto valia, solo en los dos ordenes de precio
 * @param relevancia cuanto puntuo, solo en {@link CatalogSort#RELEVANCE}
 */
public record CatalogCursor(
        CatalogSort orden,
        ListingId id,
        @Nullable Instant publicadaEn,
        @Nullable Money precio,
        @Nullable Double relevancia) {

    public CatalogCursor {
        Objects.requireNonNull(orden, "El orden es obligatorio: un cursor sin orden no se puede continuar");
        Objects.requireNonNull(id, "El identificador es obligatorio");

        exigirSolo(orden, publicadaEn, precio, relevancia);
    }

    /** El cursor del catalogo de siempre: lo mas reciente primero. */
    public static CatalogCursor porFecha(Instant publicadaEn, ListingId id) {
        return new CatalogCursor(CatalogSort.NEWEST, id, publicadaEn, null, null);
    }

    /**
     * El cursor de los dos ordenes de precio.
     *
     * <p>Recibe cual de los dos porque la condicion de continuidad los distingue —de menor
     * a mayor sigue hacia arriba y de mayor a menor hacia abajo— y porque el criterio 22
     * tiene que poder rechazar el cursor de uno usado en el otro. Los dos recorren el mismo
     * catalogo en direcciones contrarias: continuar con el equivocado devolveria justo lo
     * que ya se vio.
     */
    public static CatalogCursor porPrecio(CatalogSort orden, Money precio, ListingId id) {
        if (orden != CatalogSort.PRICE_ASC && orden != CatalogSort.PRICE_DESC) {
            throw new IllegalArgumentException("El orden " + orden + " no es de precio");
        }
        return new CatalogCursor(orden, id, null, Objects.requireNonNull(precio, "El precio es obligatorio"), null);
    }

    public static CatalogCursor porRelevancia(double relevancia, ListingId id) {
        return new CatalogCursor(CatalogSort.RELEVANCE, id, null, null, relevancia);
    }

    /** La fecha, cuando el orden la exige. Quien la pide ya sabe que el orden es ese. */
    public Instant exigirPublicadaEn() {
        return exigir(publicadaEn, "la fecha de publicacion");
    }

    public Money exigirPrecio() {
        return exigir(precio, "el precio");
    }

    public double exigirRelevancia() {
        return exigir(relevancia, "la relevancia");
    }

    /**
     * Exactamente la clave del orden y ninguna otra.
     *
     * <p>Se comprueba en el constructor y no al usarla porque un cursor incoherente que
     * nadie mira hasta la consulta se detecta con una pagina rara y no con un error.
     */
    private static void exigirSolo(
            CatalogSort orden, @Nullable Instant publicadaEn, @Nullable Money precio, @Nullable Double relevancia) {

        boolean correcta =
                switch (orden) {
                    case NEWEST -> publicadaEn != null && precio == null && relevancia == null;
                    case PRICE_ASC, PRICE_DESC -> precio != null && publicadaEn == null && relevancia == null;
                    case RELEVANCE -> relevancia != null && publicadaEn == null && precio == null;
                };

        if (!correcta) {
            throw new IllegalArgumentException("El cursor de " + orden + " no lleva la clave que ese orden necesita");
        }
    }

    private static <T> T exigir(@Nullable T valor, String cual) {
        if (valor == null) {
            throw new IllegalStateException("El cursor no lleva " + cual);
        }
        return valor;
    }
}
