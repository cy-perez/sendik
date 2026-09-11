package co.sendik.identity.rest.dto;

import java.util.List;

/**
 * La libreta entera. HU-016, criterio 1.
 *
 * <p>Envuelta en un objeto y no devuelta como arreglo suelto, como el resto del contrato
 * —{@code items} en los listados, {@code events} en el rastro de moderacion, {@code groups}
 * en el carrito—. Un arreglo en la raiz no admite ningun campo mas sin romper a quien lo
 * consuma, y aqui el candidato esta a la vista: cuando el pedido exista habra que decir cual
 * es la predeterminada sin recorrer la lista.
 *
 * <p><strong>Sin paginacion</strong>: con el tope de RN-101 no hace falta.
 */
public record AddressBookResponse(List<ShippingAddressResponse> addresses) {}
