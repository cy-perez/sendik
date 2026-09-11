package co.sendik.catalog.dto;

import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartGroup;
import co.sendik.catalog.model.SellerId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * El carrito con el nombre de cada vendedor puesto. HU-015, criterio 14.
 *
 * <p><strong>Son dos cosas y no una a proposito.</strong> {@link Cart} agrupa por
 * {@link SellerId} y no sabe como se llama nadie: el nombre y la insignia son de
 * {@code identity} y llegan por el puerto {@code SellerProfiles}, asi que meterlos dentro
 * del agregado obligaria al dominio del carrito a conocer datos de otro contexto para poder
 * existir. El carrito se puede armar sin un solo nombre; la pantalla no se puede pintar sin
 * ellos. Esa diferencia es esta clase.
 *
 * <p><strong>El mapa puede no traer a algun vendedor.</strong> Un perfil que no responde
 * —cuenta cerrada, dato que falta— no puede tumbar el carrito entero: el grupo se pinta con
 * lo que hay y quien mira sigue viendo sus productos. Por eso es un mapa y no un campo
 * obligatorio dentro del grupo.
 *
 * @param carrito los grupos y sus subtotales, ya calculados
 * @param vendedores por identificador, para poner el encabezado de cada grupo
 */
public record CartView(Cart carrito, Map<SellerId, SellerProfileView> vendedores) {

    public CartView {
        Objects.requireNonNull(carrito, "El carrito es obligatorio");
        Objects.requireNonNull(vendedores, "Los vendedores son obligatorios");

        vendedores = Map.copyOf(vendedores);
    }

    /**
     * Los tres delegados que evitan que cada sitio escriba {@code vista.carrito().algo()}.
     *
     * <p>No son logica y no deciden nada: preguntan al carrito. Existen porque la alternativa
     * era un doble salto en cada llamada, y porque quien tiene una vista delante quiere saber
     * cuantos productos hay, no atravesar una capa para averiguarlo. La suma y el agrupamiento
     * siguen viviendo donde deben.
     */
    public List<CartGroup> grupos() {
        return carrito.grupos();
    }

    public int cuantos() {
        return carrito.cuantos();
    }

    public boolean estaVacio() {
        return carrito.estaVacio();
    }
}
