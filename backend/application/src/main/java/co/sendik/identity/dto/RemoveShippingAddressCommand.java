package co.sendik.identity.dto;

import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;

/** Quitar una direccion de la libreta. HU-016, criterios 10, 11, 12 y 14. */
public record RemoveShippingAddressCommand(UserId usuario, ShippingAddressId direccion) {}
