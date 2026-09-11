package co.sendik.catalog.model;

import co.sendik.shared.money.Money;
import java.util.List;
import java.util.Objects;

/**
 * Los productos de un mismo vendedor dentro del carrito, con su subtotal. HU-015, RN-090.
 *
 * <p><strong>Tiene nombre propio porque es la unidad de la pantalla y sera la unidad del
 * pedido.</strong> Un pedido es la compra de uno o varios productos a un mismo vendedor
 * (glosario), asi que un carrito con dos vendedores seran dos pedidos y dos envios. Eso se
 * le ensena a quien compra desde el carrito y no se le descubre al pagar, que es el
 * criterio 15.
 *
 * <p>El dia que exista el pedido, esto es lo que se le entrega.
 */
public record CartGroup(SellerId vendedor, List<CartLine> lineas) {

    public CartGroup {
        Objects.requireNonNull(vendedor, "El vendedor es obligatorio");
        Objects.requireNonNull(lineas, "Las lineas son obligatorias");

        if (lineas.isEmpty()) {
            throw new IllegalArgumentException("Un grupo sin lineas no existe: lo que se vacia se va entero");
        }

        lineas = List.copyOf(lineas);
    }

    /**
     * Lo que suman los productos <strong>disponibles</strong> de este vendedor. RN-096.
     *
     * <p><strong>Se llama subtotal y nunca total, y no es una preferencia de estilo.</strong>
     * RN-076 obliga a ensenar tres cifras separadas y sumadas —precio base, costo de envio y
     * total— y aqui solo existe la primera: el envio necesita a Skydropx y todavia no
     * existe. Un carrito que rotulara «total» una suma sin envio mentiria sobre lo que se va
     * a pagar. El total con sus tres cifras nace cuando exista el costo de envio.
     *
     * <p><strong>Lo no disponible no suma</strong> (RN-094, criterio 21). Sigue a la vista y
     * apagado, pero cobrar por ello seria prometer algo que ya no se puede entregar. Un
     * grupo entero no disponible da cero y no desaparece (criterio 23): el subtotal en cero
     * dice mas que la ausencia del grupo.
     *
     * <p>Suma con {@link Money#mas}, que es {@link java.math.BigDecimal} y por tanto exacta
     * (RN-029).
     */
    public Money subtotal() {
        return lineas.stream()
                .filter(CartLine::estaDisponible)
                .map(CartLine::precio)
                .reduce(Money.dePesos(0), Money::mas);
    }

    /** Si no queda nada que comprar en este grupo. Criterio 23. */
    public boolean estaEnteroNoDisponible() {
        return lineas.stream().noneMatch(CartLine::estaDisponible);
    }
}
