package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Guardar la direccion de origen, sea la primera o un reemplazo. HU-017.
 *
 * <p>Un solo comando para las dos cosas: el origen es un recurso singular de la cuenta y
 * escribirlo es reemplazar su valor, haya o no algo antes.
 *
 * @param usuario sale del token, nunca de la peticion
 */
public record SaveOriginAddressCommand(UserId usuario, OriginAddressData datos) {}
