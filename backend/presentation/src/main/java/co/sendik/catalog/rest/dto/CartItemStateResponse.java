package co.sendik.catalog.rest.dto;

/**
 * Si una publicacion esta en el carrito, y si se puede agregar. HU-015, criterios 1 y 5.
 *
 * <p>Misma forma y misma razon que {@link FavoriteStateResponse}: responde 200 con
 * {@code inCart: false} y no 404 cuando no esta, porque lo que esta ruta devuelve es el
 * estado del control que la ficha va a pintar, y ese estado existe siempre. Con 404, la
 * pantalla tendria que tratar como error el caso mas comun de todos.
 *
 * <p>{@code eligible} es falso sobre la publicacion propia (RN-092) y sobre la que ya no esta
 * publicada. La regla se sigue comprobando al agregar: esto solo evita que alguien pulse para
 * enterarse.
 */
public record CartItemStateResponse(boolean inCart, boolean eligible) {}
