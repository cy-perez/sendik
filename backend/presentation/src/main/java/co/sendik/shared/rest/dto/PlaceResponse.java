package co.sendik.shared.rest.dto;

/**
 * Un sitio de la division politico-administrativa: su codigo y su nombre. HU-016.
 *
 * <p><strong>El mismo tipo para un departamento y para un municipio</strong>, y no dos que
 * dirian lo mismo. Para quien pinta un selector los dos son exactamente eso: un valor que se
 * manda y un texto que se lee. Lo que los distingue —que el codigo de un municipio lleva
 * dentro el de su departamento— no se ve desde aqui y no hace falta que se vea.
 *
 * <p>El nombre no se traduce: es un nombre propio y va igual en espanol y en ingles
 * (criterio 26).
 */
public record PlaceResponse(String code, String name) {}
