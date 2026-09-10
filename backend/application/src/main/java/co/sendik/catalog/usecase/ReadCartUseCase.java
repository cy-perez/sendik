package co.sendik.catalog.usecase;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.ListingRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * El carrito de quien pregunta, agrupado por vendedor. HU-015, criterios 14 a 24.
 *
 * <p><strong>Trae todo y no filtra por estado.</strong> Es la diferencia exacta con
 * {@code ListFavoritesUseCase}, y es deliberada: alli RN-071 esconde lo que dejo de verse;
 * aqui RN-094 lo conserva a la vista, apagado y fuera del subtotal. Filtrar seria el defecto
 * del criterio 21, no una optimizacion.
 *
 * <p><strong>Sin paginar.</strong> El tope de RN-097 la hace innecesaria y una suma paginada
 * no es una suma: un tramo de la mitad del carrito daria un subtotal que no es el subtotal de
 * nada.
 *
 * <p><strong>Dos consultas y no veintiuna.</strong> Una para las filas del carrito y otra
 * para las publicaciones, que es para lo que existe {@link ListingRepository#buscarVarias}.
 *
 * <p><strong>Una fila cuya publicacion ya no existe se descarta en silencio.</strong> No es
 * lo mismo que una publicacion que dejo de estar disponible —esa si sigue a la vista, y es
 * el criterio 21—: aqui se habla de una fila cuyo identificador no devuelve nada de la base,
 * que solo puede ocurrir si alguien borro la publicacion de verdad. No hay nada que pintar y
 * no hay nada que decir.
 */
public class ReadCartUseCase {

    private final CartItems carrito;
    private final ListingRepository publicaciones;

    public ReadCartUseCase(CartItems carrito, ListingRepository publicaciones) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
    }

    /*
     * Sin readOnly = true, por lo mismo que los demas de lectura: presentation no declara
     * spring-tx y con -Xlint:all -Werror ese aviso rompe la compilacion.
     */
    @Transactional
    public Cart execute(BuyerId quien) {
        List<CartItem> items = carrito.todosDe(quien);

        if (items.isEmpty()) {
            return Cart.de(List.of());
        }

        Map<ListingId, Listing> porId = new HashMap<>();
        publicaciones
                .buscarVarias(items.stream().map(CartItem::publicacion).toList())
                .forEach(publicacion -> porId.put(publicacion.id(), publicacion));

        List<CartLine> lineas = new ArrayList<>();
        for (CartItem item : items) {
            Listing publicacion = porId.get(item.publicacion());
            if (publicacion != null) {
                lineas.add(CartLine.de(item, publicacion));
            }
        }

        return Cart.de(lineas);
    }
}
