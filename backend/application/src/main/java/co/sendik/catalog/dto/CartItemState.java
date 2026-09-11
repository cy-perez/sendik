package co.sendik.catalog.dto;

/**
 * Si una publicacion esta en el carrito, y si se puede agregar. HU-015, criterios 1 y 5.
 *
 * <p>Misma forma y misma razon que {@link FavoriteState}, incluido el segundo booleano: el
 * criterio 5 dice que sobre la publicacion propia el control no se ofrece, y la pantalla no
 * tiene con que saberlo. La sesion que el navegador guarda lleva correo, nombre, roles y si
 * el correo esta verificado, pero no el identificador de la cuenta, asi que no puede comparar
 * contra el vendedor de la ficha.
 *
 * <p>Agregar el identificador a la sesion seria cambiar el contrato de HU-001 para que una
 * pantalla haga una comprobacion que RN-092 pone en el servidor de todas formas.
 *
 * @param enElCarrito si esta en el carrito de quien pregunta
 * @param elegible si se puede agregar: esta publicada y no es suya
 */
public record CartItemState(boolean enElCarrito, boolean elegible) {}
