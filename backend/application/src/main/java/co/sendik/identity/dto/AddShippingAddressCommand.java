package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Guardar una direccion nueva. HU-016, criterios 2, 3 y 8.
 *
 * @param usuario sale del token, nunca de la peticion
 */
public record AddShippingAddressCommand(UserId usuario, ShippingAddressData datos) {}
