package co.sendik.identity.dto;

import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;

/**
 * Cambiar los datos de una direccion. HU-016, criterio 9.
 *
 * <p>No lleva la marca de predeterminada: editar una direccion no cambia cual lo es. Para
 * eso esta {@link SetDefaultAddressCommand}, que es otra operacion y otro endpoint.
 *
 * @param usuario sale del token. Es lo que permite responder 404 sobre la direccion de otra
 *     persona en vez de 403 (criterio 15)
 */
public record EditShippingAddressCommand(UserId usuario, ShippingAddressId direccion, ShippingAddressData datos) {}
