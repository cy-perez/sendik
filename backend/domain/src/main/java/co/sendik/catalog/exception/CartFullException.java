package co.sendik.catalog.exception;

import co.sendik.catalog.model.Cart;
import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-097: el carrito admite hasta {@value co.sendik.catalog.model.Cart#MAXIMO_DE_PRODUCTOS}
 * productos.
 *
 * <p><strong>Hay tope, al reves que en los favoritos.</strong> RN-073 decidio a proposito
 * que aquellos no lo tuvieran, y la diferencia no es de gusto: el carrito sin sesion lo arma
 * el navegador y el servidor lo recibe entero en la fusion, asi que sin tope hay un cuerpo
 * de tamano arbitrario que alguien manda sin haber entrado.
 *
 * <p>El tope tambien es lo que permite que el carrito se lea de una sola vez, sin paginar:
 * una suma paginada no es una suma.
 *
 * <p><strong>El mensaje nombra el tope.</strong> Un rechazo que no dice cuantos caben deja a
 * quien lo recibe quitando productos a ciegas, y el criterio 7 pide explicitamente que se le
 * diga cual es.
 */
public final class CartFullException extends DomainException {

    private static final long serialVersionUID = 1L;

    public CartFullException() {
        super(
                ErrorCode.CATALOG_CART_FULL,
                "El carrito admite hasta " + Cart.MAXIMO_DE_PRODUCTOS + " productos (RN-097)");
    }
}
