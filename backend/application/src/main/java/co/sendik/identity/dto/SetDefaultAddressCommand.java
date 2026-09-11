package co.sendik.identity.dto;

import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;

/**
 * Elegir cual es la direccion predeterminada. HU-016, criterio 13.
 *
 * <p>Es un recurso singular de la persona —{@code PUT /users/me/default-address}— y no un
 * verbo en la ruta: marcar es reemplazar el valor de ese recurso, que es lo que {@code PUT}
 * significa.
 */
public record SetDefaultAddressCommand(UserId usuario, ShippingAddressId direccion) {}
