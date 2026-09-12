package co.sendik.shared.rest.dto;

import java.util.List;

/** Los departamentos. Envuelto en un objeto, como el resto del contrato. */
public record DepartmentsResponse(List<PlaceResponse> departments) {}
