package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.CartGroup;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.rest.dto.CartGroupResponse;
import co.sendik.catalog.rest.dto.CartLineResponse;
import co.sendik.catalog.rest.dto.CartResponse;
import co.sendik.catalog.rest.dto.MoneyPayload;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Del carrito al cuerpo de la respuesta. HU-015.
 *
 * <p><strong>Cada linea pasa por {@code ListingResponses.publica}</strong>, que es el metodo
 * que garantiza que no salga nada de moderacion. Aqui importa igual que en el catalogo: quien
 * mira su carrito no es el dueno de lo que hay dentro, y una publicacion lleva motivo de
 * rechazo y nota interna que jamas pueden cruzar este borde.
 *
 * <p><strong>El subtotal se copia del dominio y no se recalcula.</strong> Es lo que hace que
 * la cifra de la pantalla sea la misma que la del servidor y la misma que vera el pedido
 * cuando exista: {@link CartGroup#subtotal()} es el unico sitio donde se suma dinero en todo
 * el proyecto.
 */
public final class Carts {

    private Carts() {}

    public static CartResponse de(CartView vista, PublicFileStore almacen) {
        List<CartGroupResponse> grupos = vista.carrito().grupos().stream()
                .map(grupo -> deGrupo(grupo, vista.vendedores().get(grupo.vendedor()), almacen))
                .toList();

        return new CartResponse(grupos, vista.carrito().seDividira());
    }

    private static CartGroupResponse deGrupo(
            CartGroup grupo, @Nullable SellerProfileView vendedor, PublicFileStore almacen) {

        return new CartGroupResponse(
                grupo.vendedor().toString(),
                vendedor == null ? null : vendedor.nombre(),
                vendedor != null && vendedor.verificado(),
                grupo.lineas().stream().map(linea -> deLinea(linea, almacen)).toList(),
                dinero(grupo.subtotal()),
                grupo.estaEnteroNoDisponible());
    }

    private static CartLineResponse deLinea(CartLine linea, PublicFileStore almacen) {
        return new CartLineResponse(
                ListingResponses.publica(linea.publicacion(), almacen),
                linea.agregadoEn(),
                linea.estaDisponible(),
                linea.cambioDePrecio());
    }

    private static MoneyPayload dinero(Money valor) {
        return new MoneyPayload(valor.amount(), Money.MONEDA);
    }
}
