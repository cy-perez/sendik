package co.sendik.catalog.model;

import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.Objects;

/**
 * Un producto dentro del carrito, tal como se lee. HU-015.
 *
 * <p>No se guarda: al leer se cruza cada {@link CartItem} con su publicacion y sale esto.
 * Lo que la tabla conserva es el par, la fecha y el precio de entrada; todo lo demas
 * —titulo, imagen, vendedor, precio vigente y si sigue disponible— es de la publicacion y
 * se mira en el momento de mirar.
 *
 * <p><strong>De aqui sale que el criterio 24 no escriba nada.</strong> Una publicacion que
 * estaba pausada y su vendedor reanuda vuelve a contar sola, porque la disponibilidad no es
 * un dato del carrito sino una lectura del estado de la publicacion. Nadie tuvo que volver a
 * agregarla y nadie tuvo que actualizar una columna.
 *
 * @param publicacion la publicacion, entera, tal como esta ahora
 * @param agregadoEn cuando entro al carrito. Ordena la lista y decide que se conserva cuando
 *     la fusion no cabe entera (criterio 10)
 * @param precioAlAgregar con cuanto entro, solo para {@link #cambioDePrecio()}
 */
public record CartLine(Listing publicacion, Instant agregadoEn, Money precioAlAgregar) {

    public CartLine {
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
        Objects.requireNonNull(agregadoEn, "La fecha en que se agrego es obligatoria");
        Objects.requireNonNull(precioAlAgregar, "El precio con el que entro es obligatorio");
    }

    /** Arma la linea a partir de lo guardado y de la publicacion tal como esta ahora. */
    public static CartLine de(CartItem item, Listing publicacion) {
        Objects.requireNonNull(item, "El item es obligatorio");

        return new CartLine(publicacion, item.agregadoEn(), item.precioAlAgregar());
    }

    /**
     * Si todavia se puede comprar. RN-094.
     *
     * <p>Lo que no lo esta sigue a la vista y apagado, fuera del subtotal: un favorito que
     * desaparece es un misterio menor, un carrito que adelgaza en silencio justo antes de
     * pagar es otra cosa (criterio 21).
     *
     * <p><strong>Por que no esta disponible no se pregunta ni se responde.</strong> Vendido,
     * pausado o bajado por un moderador dan lo mismo aqui y dan lo mismo en la pantalla
     * (criterio 22): distinguirlos publicaria el movimiento del catalogo y las decisiones de
     * un vendedor a cualquiera que apunte identificadores en su carrito, que es lo que
     * RN-068 existe para no decir. Este metodo devuelve un booleano y no un motivo, y esa
     * pobreza es deliberada.
     */
    public boolean estaDisponible() {
        return publicacion.esVisible();
    }

    /**
     * Lo que cuesta ahora. RN-093: es el precio vigente y no el de entrada.
     *
     * <p>Congelarlo es cosa del pedido (RN-030), que todavia no existe. Mientras tanto lo
     * honesto es ensenar lo que cuesta hoy: un carrito que sumara precios viejos prometeria
     * un valor que nadie va a cobrar.
     *
     * <p>Cero cuando la publicacion dejo de tener precio, que en la practica no ocurre: solo
     * un borrador carece de el y un borrador no llega al carrito. Se responde cero en vez de
     * fallar porque una linea apagada no tiene por que romper la lectura del carrito entero,
     * y de todas formas no entra en ningun subtotal.
     */
    public Money precio() {
        Money vigente = publicacion.product().price();

        return vigente == null ? Money.dePesos(0) : vigente;
    }

    /**
     * Si el precio cambio desde que entro al carrito. Criterio 18.
     *
     * <p>Se avisa y no se hace nada mas: lo que manda sigue siendo el precio de ahora. Sube
     * o baja da igual —las dos cosas son «cambio de precio»— porque el aviso existe para que
     * nadie pague creyendo otra cifra, no para celebrar una rebaja: el glosario prohibe
     * nombrar rebajas en este producto.
     */
    public boolean cambioDePrecio() {
        return !precio().equals(precioAlAgregar);
    }

    public SellerId vendedor() {
        return publicacion.sellerId();
    }
}
