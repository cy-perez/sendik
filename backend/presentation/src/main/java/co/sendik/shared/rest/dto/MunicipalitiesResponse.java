package co.sendik.shared.rest.dto;

import java.util.List;

/** Los municipios activos de un departamento. */
public record MunicipalitiesResponse(List<PlaceResponse> municipalities) {}
