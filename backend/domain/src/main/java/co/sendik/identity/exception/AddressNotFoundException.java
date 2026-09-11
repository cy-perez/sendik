package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * No existe esa direccion de entrega, o no es de quien pregunta. Criterio 15 de HU-016.
 *
 * <p><strong>Las dos cosas con el mismo codigo, y a proposito</strong>, igual que en
 * {@code ListingNotFoundException}: un 403 sobre la direccion de otra persona confirmaria
 * que ese identificador existe y que es de alguien. Con 404 no se distingue de uno
 * inventado.
 *
 * <p>Aqui el motivo es mas fuerte que en una publicacion, que es publica de todos modos:
 * una direccion no la ve nadie mas que su duena (RN-098), asi que ni siquiera hay que
 * admitir que existe.
 */
public final class AddressNotFoundException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AddressNotFoundException() {
        super(ErrorCode.COMMON_NOT_FOUND, "No existe esa direccion, o no es de quien pregunta");
    }
}
