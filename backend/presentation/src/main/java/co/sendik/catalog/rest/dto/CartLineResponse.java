package co.sendik.catalog.rest.dto;

import java.time.Instant;

/**
 * Un producto dentro del carrito. HU-015, criterios 17, 18, 21 y 22.
 *
 * <p><strong>{@code available} es un booleano y no un motivo, y esa pobreza es la
 * regla.</strong> Vendido, pausado y bajado por un moderador responden identico (criterio
 * 22): distinguirlos publicaria el movimiento del catalogo y las decisiones de un vendedor a
 * cualquiera que apunte identificadores en su carrito, que es lo que RN-068 existe para no
 * decir. Aqui no hay campo donde escribir el motivo, asi que no se puede filtrar por
 * descuido.
 *
 * <p><strong>El precio va dentro de {@code listing} y no aparte.</strong> Es el vigente de la
 * publicacion (RN-093), no una cifra del carrito, y duplicarlo aqui daria dos sitios donde
 * mirar cuanto cuesta algo.
 *
 * <p>{@code priceChanged} es lo unico que el carrito sabe y la publicacion no: que cuando
 * entro costaba otra cosa. Cuanto costaba no se publica —no le sirve a la pantalla, que solo
 * tiene que avisar— y asi el precio viejo no viaja a ninguna parte.
 *
 * @param listing la publicacion en su forma publica, la misma del catalogo
 * @param addedAt cuando entro al carrito
 * @param available si todavia se puede comprar
 * @param priceChanged si el precio cambio desde que entro
 */
public record CartLineResponse(
        PublicListingResponse listing, Instant addedAt, boolean available, boolean priceChanged) {}
