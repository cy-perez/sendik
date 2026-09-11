package co.sendik.catalog.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-092: nadie agrega al carrito su propia publicacion.
 *
 * <p>Es {@link SelfFavoriteForbiddenException} aplicada a comprar en vez de a guardar, y la
 * razon es mas fuerte: alli la regla evita una senal sin sentido —quien publico algo no
 * necesita guardarlo para volver a el—; aqui evita que alguien se compre a si mismo y mueva
 * dinero y comision en circulo.
 *
 * <p><strong>Se comprueba en el servidor y no escondiendo el control.</strong> La peticion
 * se puede mandar sin pasar por la interfaz, y una regla que solo vive en una pantalla no es
 * una regla.
 *
 * <p><strong>Y se comprueba tambien al fusionar</strong> el carrito del navegador (HU-015,
 * criterio 9). Mientras el carrito es anonimo no hay contra quien comparar, asi que lo
 * propio puede entrar en el; el momento en que aparece alguien con nombre es el ingreso, y
 * es alli donde esta regla se aplica por primera vez a esas filas. Alli no viaja como
 * excepcion sino como descarte anotado: una fusion no puede fallar entera porque uno de
 * veinte productos fuera de quien entra.
 */
public final class SelfCartForbiddenException extends DomainException {

    private static final long serialVersionUID = 1L;

    public SelfCartForbiddenException() {
        super(ErrorCode.CATALOG_SELF_CART_FORBIDDEN, "Nadie agrega al carrito su propia publicacion (RN-092)");
    }
}
