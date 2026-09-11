package co.sendik.identity.exception;

import co.sendik.shared.error.DomainException;
import co.sendik.shared.error.ErrorCode;
import co.sendik.shared.geo.MunicipalityCode;

/**
 * El municipio no esta en la division politico-administrativa, o ya no se ofrece.
 * RN-100, criterios 6 y 23 de HU-016.
 *
 * <p><strong>Un solo codigo para los dos casos</strong> —no existe, o el DANE lo
 * suprimio y la fila esta inactiva— porque lo que hay que hacer es lo mismo: elegir otro
 * de la lista. Distinguirlos obligaria al formulario a explicar la Divipola.
 *
 * <p>Codigo propio y no el de validacion generica, por lo mismo que
 * {@code SELLER_UNKNOWN_INSTITUTION} y {@code CATALOG_UNKNOWN_CATEGORY}: lo que hay que
 * decirle es que elija de la lista, no que revise el formulario.
 *
 * <p>No cubre el caso de "este municipio no pertenece a ese departamento", que no puede
 * ocurrir: el cuerpo de la peticion no lleva departamento y el codigo del municipio lo
 * lleva dentro (RN-100).
 */
public final class UnknownMunicipalityException extends DomainException {

    private static final long serialVersionUID = 1L;

    public UnknownMunicipalityException(MunicipalityCode municipio) {
        super(ErrorCode.USER_UNKNOWN_MUNICIPALITY, "El municipio " + municipio + " no esta en la division vigente");
    }
}
