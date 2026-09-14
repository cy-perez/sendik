package co.sendik.identity.usecase;

import co.sendik.identity.dto.OriginAddressView;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * La direccion de origen de quien pregunta. HU-017, criterio 1.
 *
 * <p><strong>El identificador viene del token, nunca de la peticion.</strong> No hay
 * ruta con identificador porque el origen es un recurso singular de la cuenta: no hay
 * forma de pedir el de otra persona.
 *
 * <p><strong>Una cuenta cerrada con token vivo responde 401 tambien al leer</strong>, y no
 * un 204 que la pantalla leeria como «no tienes origen» mientras {@code GET /users/me}
 * responde 401 en la misma sesion. La cuenta ya se carga aqui para el remitente, asi
 * que la comprobacion no cuesta nada. Lo cazo la revision de seguridad.
 *
 * <p><strong>Aqui vive ademas el cruce con la division y con el perfil</strong>, que
 * comparte con {@code SaveOriginAddressUseCase}. Es el patron de
 * {@code ListShippingAddressesUseCase.conLaDivision}.
 */
public class ReadOriginAddressUseCase {

    private final OriginAddressRepository origenes;
    private final UserRepository usuarios;
    private final GeographicDivision division;

    public ReadOriginAddressUseCase(
            OriginAddressRepository origenes, UserRepository usuarios, GeographicDivision division) {
        this.origenes = origenes;
        this.usuarios = usuarios;
        this.division = division;
    }

    /*
     * En una transaccion, y no por escribir: son tres lecturas -el origen, la cuenta y la
     * division- y conviene que se armen con una sola instantanea. Sin readOnly = true, por lo
     * mismo que ExportUserDataUseCase.
     */
    /** @throws AccountNoLongerExistsException si la cuenta del token ya se cerro */
    @Transactional
    public Optional<OriginAddressView> execute(UserId usuario) {
        User cuenta = usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);
        return origenes.deCuenta(usuario).map(origen -> conLaDivision(origen, cuenta, division));
    }

    /**
     * Cruza el origen con la division politico-administrativa y con el perfil del
     * remitente. Estatico y con el puerto por argumento porque lo comparte {@code Save}.
     *
     * <p>Un municipio que no este en la division es imposible: la clave foranea de V22 lo
     * impide y {@code buscarMunicipio} devuelve tambien los inactivos. Se afirma en vez de
     * suponerse, <strong>y el mensaje no lleva el municipio</strong>: sale en {@code ERROR}
     * con traza, y en que municipio despacha alguien no va a ningun registro.
     */
    static OriginAddressView conLaDivision(OriginAddress origen, User remitente, GeographicDivision division) {
        Municipality municipio = division.buscarMunicipio(origen.municipio())
                .orElseThrow(() -> new IllegalStateException(
                        "El origen de " + origen.duena() + " apunta a un municipio que no existe"));

        Department departamento = division.departamentos().stream()
                .filter(candidato -> candidato.codigo().equals(origen.departamento()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "El origen de " + origen.duena() + " apunta a un departamento que no existe"));

        return new OriginAddressView(
                departamento.codigo().value(),
                departamento.nombre(),
                municipio.codigo().value(),
                municipio.nombre(),
                municipio.activo(),
                origen.linea().value(),
                origen.complemento() == null ? null : origen.complemento().value(),
                origen.indicaciones() == null ? null : origen.indicaciones().value(),
                origen.codigoPostal() == null ? null : origen.codigoPostal().value(),
                remitente.displayName().value(),
                remitente.phone() == null ? null : remitente.phone().value(),
                origen.creadaEn());
    }
}
