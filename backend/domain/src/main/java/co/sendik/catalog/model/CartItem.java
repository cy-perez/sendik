package co.sendik.catalog.model;

import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfCartForbiddenException;
import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Un producto que alguien reunio y todavia no ha comprado. HU-015.
 *
 * <p><strong>Su identidad es el par</strong> —quien y cual—, como en {@link Favorite}: el
 * producto es unico y su existencia es siempre 1, asi que no hay cantidad que sumar ni una
 * segunda linea posible del mismo producto (RN-091). De eso vive la idempotencia del
 * criterio 4, y la tabla lo dice igual con clave primaria compuesta.
 *
 * <p><strong>Guarda el precio con el que entro, y no sirve para cobrar.</strong> Existe solo
 * para el aviso del criterio 18: la cifra que manda es siempre la vigente de la publicacion
 * (RN-093), y el precio se congela al crear el pedido y no antes (RN-030). Guardarlo es lo
 * unico que permite responder «esto cambio de precio desde que lo agregaste» sin inventarse
 * un historial de precios que el proyecto no tiene.
 *
 * <p><strong>Agregar no reserva nada</strong> (RN-089). Este objeto no toca la publicacion
 * ni su estado: la publicacion se marca vendida cuando el pago queda aprobado (RN-035) y no
 * antes, asi que dos personas pueden tener el mismo producto en su carrito y solo una lo
 * comprara.
 *
 * <p>Es una clase y no un {@code record} por lo mismo que {@link Favorite}: un record expone
 * su constructor canonico, y con el a la vista cualquiera fabrica un item sobre su propia
 * publicacion o sobre un borrador sin pasar por ninguna regla.
 */
public final class CartItem {

    private final BuyerId quien;
    private final ListingId publicacion;
    private final Instant agregadoEn;
    private final Money precioAlAgregar;

    private CartItem(BuyerId quien, ListingId publicacion, Instant agregadoEn, Money precioAlAgregar) {
        this.quien = Objects.requireNonNull(quien, "Quien agrega es obligatorio");
        this.publicacion = Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
        this.agregadoEn = Objects.requireNonNull(agregadoEn, "La fecha en que se agrego es obligatoria");
        this.precioAlAgregar = Objects.requireNonNull(precioAlAgregar, "El precio con el que entro es obligatorio");
    }

    /**
     * Agrega la publicacion al carrito, si se puede.
     *
     * <p><strong>El orden de las dos comprobaciones importa y no es intercambiable</strong>,
     * exactamente como en {@link Favorite#de}. Primero el estado y despues el dueno: al
     * reves, agregar el borrador de otra persona respondera 403 y con eso confirmaria que
     * ese identificador es una publicacion real que existe y no es suya. Con el estado
     * delante, todo lo que no esta publicado responde igual que lo que no existe, que es
     * RN-068.
     *
     * <p>Sobre lo propio y publicado si sale el 403, y ahi es lo correcto: no se revela nada
     * que quien pregunta no sepa ya, y necesita saber por que no se agrego (criterio 5).
     *
     * <p><strong>El tope de RN-097 no se comprueba aqui.</strong> Un item no puede saber
     * cuantos hermanos tiene: eso es del carrito entero y lo comprueba el caso de uso contra
     * {@link Cart#MAXIMO_DE_PRODUCTOS}. Meterlo aqui obligaria a pasarle la cuenta actual a
     * la factoria, que es hacer que el objeto mas pequeno dependa del mas grande.
     *
     * @throws ListingNotFoundException si la publicacion no esta visible (RN-068)
     * @throws SelfCartForbiddenException si la publicacion es de quien la agrega (RN-092)
     */
    public static CartItem de(BuyerId quien, Listing publicacion, Instant ahora) {
        Objects.requireNonNull(quien, "Quien agrega es obligatorio");
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");

        if (!publicacion.esVisible()) {
            throw new ListingNotFoundException(publicacion.id());
        }
        if (publicacion.esDe(quien)) {
            throw new SelfCartForbiddenException();
        }

        // Una publicacion visible siempre tiene precio: para llegar a PUBLISHED paso por
        // `exigirCompletoPara`, que lo exige. El tipo lo declara anulable porque un borrador
        // no lo tiene, y un borrador no llega hasta aqui: lo para `esVisible` dos lineas
        // arriba. Se afirma en vez de suponerse, para que el dia que ese orden cambie falle
        // aqui y no en un subtotal.
        Money precio = Objects.requireNonNull(
                publicacion.product().price(), "Una publicacion visible tiene precio: " + publicacion.id());

        return new CartItem(quien, publicacion.id(), ahora, precio);
    }

    /**
     * Un item que ya estaba guardado.
     *
     * <p>No vuelve a comprobar las reglas, y es a proposito: lo que se guardo cumplia RN-092
     * el dia que se guardo, y una publicacion cambia de estado sin que el carrito tenga nada
     * que ver. Aplicar {@link #de} aqui haria que leer el carrito fallara en cuanto alguien
     * vendiera uno de los productos, que es justo lo contrario de lo que RN-094 manda hacer:
     * sigue a la vista, apagado.
     */
    public static CartItem reconstruir(
            BuyerId quien, ListingId publicacion, Instant agregadoEn, Money precioAlAgregar) {
        return new CartItem(quien, publicacion, agregadoEn, precioAlAgregar);
    }

    public BuyerId quien() {
        return quien;
    }

    public ListingId publicacion() {
        return publicacion;
    }

    public Instant agregadoEn() {
        return agregadoEn;
    }

    /** Con cuanto costaba entro. Para avisar de que cambio, nunca para cobrar (RN-093). */
    public Money precioAlAgregar() {
        return precioAlAgregar;
    }

    /**
     * Dos items son el mismo si son de la misma persona sobre la misma publicacion.
     *
     * <p><strong>Ni la fecha ni el precio entran.</strong> Es la identidad de la fila, y
     * volver a agregar lo que ya estaba tiene que dar el mismo item y no uno nuevo con otra
     * hora ni con otro precio: es el criterio 4 —dos pestanas, un reintento— dicho en el
     * dominio, y lo mismo que la clave primaria compuesta dice en la tabla.
     *
     * <p>Que el precio quede fuera importa mas de lo que parece: si entrara, agregar de nuevo
     * un producto que cambio de precio crearia un segundo item y el carrito ensenaria el
     * mismo producto dos veces, que es exactamente lo que RN-091 prohibe.
     */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof CartItem item
                && quien.equals(item.quien)
                && publicacion.equals(item.publicacion);
    }

    @Override
    public int hashCode() {
        return Objects.hash(quien, publicacion);
    }

    @Override
    public String toString() {
        return "CartItem[quien=" + quien + ", publicacion=" + publicacion + "]";
    }
}
