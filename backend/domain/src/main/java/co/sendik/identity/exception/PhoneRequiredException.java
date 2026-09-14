package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;

/**
 * HU-017, criterio 5: guardar la direccion de origen exige que el perfil tenga
 * telefono, porque el remitente de la guia es el titular de la cuenta (RN-078) y un
 * remitente sin telefono no es remitente.
 *
 * <p>Sale como 422 y no como 400: la peticion esta bien formada y quien la manda tiene
 * derecho a hacerla; lo que falta es un dato que vive en otro sitio, el perfil.
 */
public final class PhoneRequiredException extends DomainException {

    private static final long serialVersionUID = 1L;

    public PhoneRequiredException() {
        super(ErrorCode.USER_PHONE_REQUIRED, "El perfil no tiene telefono y el remitente lo necesita (HU-017)");
    }
}
