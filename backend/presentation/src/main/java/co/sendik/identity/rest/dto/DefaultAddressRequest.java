package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Cuerpo de {@code PUT /api/v1/users/me/default-address}. HU-016, criterio 13.
 *
 * <p><strong>Recurso singular y no un verbo en la ruta.</strong> El contrato no admite verbos
 * y {@code /addresses/{id}/default} chocaria ademas con {@code {id}}. La direccion
 * predeterminada es un recurso que la persona tiene uno solo, y marcar es reemplazar su
 * valor, que es exactamente lo que {@code PUT} significa.
 */
public record DefaultAddressRequest(@NotBlank String addressId) {}
