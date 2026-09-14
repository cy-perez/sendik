package co.sendik.identity.usecase;

import co.sendik.identity.dto.OriginAddressView;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.PhoneRequiredException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda o reemplaza la direccion de origen. HU-017, criterios 2, 3, 5, 7 y 11.
 *
 * <p><strong>El orden de las comprobaciones importa</strong>: primero la cuenta —el
 * token sobrevive quince minutos al cierre, ADR-0003—, despues el telefono del perfil,
 * y al final el municipio. El telefono antes que el municipio porque es lo que la
 * pantalla ya sabe antes de que la persona escriba nada: si falta, la respuesta
 * correcta es «ve al perfil», no «elige otro municipio».
 *
 * <p><strong>Guardar el origen descarta la ciudad escrita a mano</strong> (criterio 11,
 * ADR-0042). Desde ese momento la ciudad del perfil es el municipio del origen y se lee
 * uniendo; el texto libre queda en nulo y no vuelve al borrar el origen (criterio 12).
 */
public class SaveOriginAddressUseCase {

    private final OriginAddressRepository origenes;
    private final GeographicDivision division;
    private final UserRepository usuarios;
    private final Clock reloj;

    public SaveOriginAddressUseCase(
            OriginAddressRepository origenes, GeographicDivision division, UserRepository usuarios, Clock reloj) {
        this.origenes = origenes;
        this.division = division;
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /**
     * @throws AccountNoLongerExistsException si la cuenta del token ya se cerro
     * @throws PhoneRequiredException si el perfil no tiene telefono (criterio 5)
     * @throws UnknownMunicipalityException si el municipio no existe o el DANE lo suprimio
     *     (RN-100)
     */
    @Transactional
    public OriginAddressView execute(SaveOriginAddressCommand comando) {
        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);

        if (cuenta.phone() == null) {
            throw new PhoneRequiredException();
        }

        Instant ahora = reloj.instant();
        Optional<OriginAddress> actual = origenes.deCuenta(comando.usuario());
        OriginAddress origen = actual.isPresent()
                ? comando.datos().aplicadaA(actual.get(), ahora)
                : comando.datos().comoNueva(comando.usuario(), ahora);

        exigirMunicipioVigente(origen.municipio());

        origenes.guardar(origen);

        // Criterio 11: con origen, la ciudad del perfil es el municipio del origen. El texto
        // libre se descarta aqui y no se copia nada: la ciudad se lee uniendo (ADR-0042).
        // Con una operacion minima y no reescribiendo la cuenta desde la instantanea: si el
        // cierre se colara entre leer y escribir, `actualizar` resucitaria la fila.
        if (cuenta.city() != null) {
            usuarios.limpiarCiudad(comando.usuario());
        }

        return ReadOriginAddressUseCase.conLaDivision(origen, cuenta, division);
    }

    /**
     * RN-100: el borde comprueba la forma, {@code MunicipalityCode} los cinco digitos y
     * esto que exista de verdad. Un municipio inactivo se rechaza igual que uno
     * inexistente; lo que ya estaba guardado con el se sigue leyendo (criterio 13).
     */
    private void exigirMunicipioVigente(MunicipalityCode codigo) {
        Optional<Municipality> municipio = division.buscarMunicipio(codigo);
        if (municipio.isEmpty() || !municipio.get().activo()) {
            throw new UnknownMunicipalityException();
        }
    }
}
