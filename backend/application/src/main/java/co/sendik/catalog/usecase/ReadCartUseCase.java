package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.SellerProfiles;
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
    private final SellerProfiles vendedores;

    public ReadCartUseCase(CartItems carrito, ListingRepository publicaciones, SellerProfiles vendedores) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
        this.vendedores = vendedores;
    }

    /*
     * Sin readOnly = true, por lo mismo que los demas de lectura: presentation no declara
     * spring-tx y con -Xlint:all -Werror ese aviso rompe la compilacion.
     */
    @Transactional
    public CartView execute(BuyerId quien) {
        List<CartItem> items = carrito.todosDe(quien);

        if (items.isEmpty()) {
            return new CartView(Cart.de(List.of()), Map.of());
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

        return conVendedores(Cart.de(lineas), vendedores);
    }

    /**
     * Le pone a cada grupo el perfil de su vendedor.
     *
     * <p>Una consulta por vendedor distinto y no por linea: un carrito de veinte productos de
     * un mismo vendedor pregunta una vez. Con el tope de RN-097 el peor caso son veinte
     * vendedores distintos, que es una pantalla que nadie va a tener y que aun asi cabe.
     *
     * <p>Un perfil que no responde se omite del mapa y no rompe nada: el grupo se pinta con lo
     * que hay. Que no se pueda leer el nombre de un vendedor no es razon para negarle a nadie
     * su carrito.
     *
     * <p>Estatico y con el puerto por argumento porque lo comparte {@link PreviewCartUseCase},
     * que hace exactamente lo mismo con un carrito que no esta en la base.
     */
    static CartView conVendedores(Cart armado, SellerProfiles vendedores) {
        Map<SellerId, SellerProfileView> perfiles = new HashMap<>();
        armado.grupos()
                .forEach(grupo -> vendedores
                        .buscar(grupo.vendedor())
                        .ifPresent(perfil -> perfiles.put(grupo.vendedor(), perfil)));

        return new CartView(armado, perfiles);
    }
}
