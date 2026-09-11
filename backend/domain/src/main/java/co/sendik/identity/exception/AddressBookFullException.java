package co.sendik.identity.exception;

import co.sendik.identity.model.AddressBook;
import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * RN-101: una cuenta admite hasta
 * {@value co.sendik.identity.model.AddressBook#MAXIMO_DE_DIRECCIONES} direcciones.
 *
 * <p><strong>El mensaje nombra el tope</strong>, por lo mismo que {@code CartFullException}:
 * el criterio 8 pide que se le diga cual es, y un rechazo que no lo dice deja a quien lo
 * recibe borrando direcciones a ciegas.
 *
 * <p>Sale como 422 y no como 403: la peticion es legitima y quien la manda tiene derecho a
 * hacerla, lo que pasa es que no cabe. Un 403 hablaria de permisos que aqui no faltan.
 */
public final class AddressBookFullException extends DomainException {

    private static final long serialVersionUID = 1L;

    public AddressBookFullException() {
        super(
                ErrorCode.USER_ADDRESS_BOOK_FULL,
                "La libreta admite hasta " + AddressBook.MAXIMO_DE_DIRECCIONES + " direcciones (RN-101)");
    }
}
