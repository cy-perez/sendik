package co.sendik.catalog.rest.dto;

import java.util.List;

/**
 * El carrito, agrupado por vendedor. HU-015, criterios 14, 15 y 16.
 *
 * <p><strong>No hay campo {@code total}, y su ausencia es la regla.</strong> RN-076 obliga a
 * ensenar tres cifras separadas y sumadas —precio base, costo de envio y total— y aqui solo
 * existe la primera: el envio necesita a Skydropx y todavia no existe. Un campo {@code total}
 * con la suma de los subtotales mentiria sobre lo que se va a pagar, y lo haria en el
 * contrato, que es donde mas caro sale corregirlo. El total nace cuando exista el costo de
 * envio (RN-096).
 *
 * <p><strong>Ni {@code count} ni ningun agregado del carrito entero.</strong> Lo que la
 * pantalla suma son grupos, porque cada grupo sera un pedido (RN-090). Un contador global
 * invitaria a tratarlos como uno solo.
 *
 * <p>{@code willSplit} lo decide el servidor y no la plantilla contando grupos: es la lectura
 * de RN-090, y el criterio 15 pide avisar antes de que sorprenda.
 *
 * @param groups uno por vendedor, con el mas reciente primero
 * @param willSplit si habra mas de un pedido y mas de un envio
 */
public record CartResponse(List<CartGroupResponse> groups, boolean willSplit) {}
