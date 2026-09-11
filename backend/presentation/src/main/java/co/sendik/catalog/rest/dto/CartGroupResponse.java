package co.sendik.catalog.rest.dto;

import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Los productos de un mismo vendedor, con su subtotal. HU-015, criterio 14.
 *
 * <p><strong>Se llama {@code subtotal} y nunca {@code total}</strong>, y el nombre es la
 * regla: RN-096 y RN-076. Lo que suma es precio base de lo disponible, y el envio se calcula
 * al comprar.
 *
 * <p>{@code sellerName} y {@code sellerVerified} pueden faltar si el perfil no responde. El
 * grupo se pinta igual: que no se pueda leer el nombre de un vendedor no es razon para
 * negarle a nadie su carrito.
 *
 * <p>{@code allUnavailable} existe para el criterio 23: un grupo entero apagado se ve con
 * subtotal en cero y no se oculta, y la pantalla necesita saberlo sin recorrer las lineas.
 *
 * @param subtotal el objeto de dinero del contrato, como todo el dinero del proyecto: nunca un
 *     numero suelto, para que no haya duda de si son pesos o centavos
 */
public record CartGroupResponse(
        String sellerId,
        @Nullable String sellerName,
        boolean sellerVerified,
        List<CartLineResponse> lines,
        MoneyPayload subtotal,
        boolean allUnavailable) {}
