package co.sendik.identity.usecase;

import co.sendik.identity.dto.EditShippingAddressCommand;
import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.AddressNotFoundException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Clock;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cambia los datos de una direccion. HU-016, criterios 9 y 15.
 *
 * <p><strong>Cambia esa y no crea una segunda.</strong> El identificador, la cuenta duena, la
 * fecha de creacion y la marca de predeterminada sobreviven: lo unico que se reescribe son
 * los siete campos y la fecha de actualizacion.
 *
 * <p><strong>Una direccion ajena responde 404 y no 403</strong>, y por eso el repositorio
 * devuelve la de cualquiera y la comprobacion esta aqui a la vista: un 403 confirmaria que
 * ese identificador existe y que es de alguien. Como una direccion no la ve nadie mas que su
 * duena (RN-098), ni siquiera hay que admitir que existe.
 */
public class EditShippingAddressUseCase {

    private final ShippingAddressRepository direcciones;
    private final GeographicDivision division;
    private final UserRepository usuarios;
    private final Clock reloj;

    public EditShippingAddressUseCase(
            ShippingAddressRepository direcciones, GeographicDivision division, UserRepository usuarios, Clock reloj) {
        this.direcciones = direcciones;
        this.division = division;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /**
     * @throws AccountNoLongerExistsException si la cuenta del token ya se cerro
     * @throws AddressNotFoundException si no existe o no es suya (criterio 15)
     * @throws UnknownMunicipalityException si el municipio no esta en la division vigente
     */
    @Transactional
    public ShippingAddressView execute(EditShippingAddressCommand comando) {
        if (usuarios.buscarPorId(comando.usuario()).isEmpty()) {
            throw new AccountNoLongerExistsException();
        }

        ShippingAddress actual = direcciones
                .buscar(comando.direccion())
                .filter(direccion -> direccion.esDe(comando.usuario()))
                .orElseThrow(() -> new AddressNotFoundException(comando.direccion()));

        ShippingAddress editada = comando.datos().aplicadaA(actual, reloj.instant());

        exigirMunicipioVigente(editada.municipio());

        direcciones.guardar(editada);

        return ShippingAddressView.de(editada, division);
    }

    /**
     * RN-100. Un municipio suprimido no se puede elegir, aunque la direccion que se esta
     * editando ya lo tuviera: leer una direccion vieja sigue funcionando (criterio 23), pero
     * volver a guardarla obliga a elegir uno vigente.
     */
    private void exigirMunicipioVigente(MunicipalityCode codigo) {
        Optional<Municipality> municipio = division.buscarMunicipio(codigo);
        if (municipio.isEmpty() || !municipio.get().activo()) {
            throw new UnknownMunicipalityException(codigo);
        }
    }
}
