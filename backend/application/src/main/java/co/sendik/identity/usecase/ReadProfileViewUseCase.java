package co.sendik.identity.usecase;

import co.sendik.identity.dto.ProfileView;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.port.out.GeographicDivision;
import org.springframework.transaction.annotation.Transactional;

/**
 * El perfil de quien pregunta, con la ciudad ya resuelta. HU-017, criterios 10 a 13.
 *
 * <p>Es distinto de {@link ReadProfileUseCase}, y los dos tienen que existir: aquel
 * devuelve la cuenta y es la puerta publica por la que {@code catalog} pregunta nombre y
 * correo para sus avisos; este arma lo que ve la duena en su pantalla. Hasta HU-017 eran
 * lo mismo y el borde traducia la cuenta directo. Ahora la ciudad tiene dos fuentes
 * (ADR-0042) y una de ellas es el origen, que es otro agregado y otra tabla:
 *
 * <ul>
 *   <li>Sin origen, la ciudad es lo que la persona escribio a mano y se puede editar.
 *   <li>Con origen, la ciudad es el municipio del origen <strong>con su departamento al
 *       lado</strong> —hay mas de un San Pedro— y no se edita desde el perfil.
 * </ul>
 *
 * <p>Se lee uniendo y no se copia: si el DANE renombra el municipio, sale el nombre nuevo
 * (criterio 13). Y un municipio inactivo se sigue leyendo igual: {@code buscarMunicipio}
 * devuelve tambien los inactivos.
 */
public class ReadProfileViewUseCase {

    private final UserRepository usuarios;
    private final OriginAddressRepository origenes;
    private final GeographicDivision division;

    public ReadProfileViewUseCase(
            UserRepository usuarios, OriginAddressRepository origenes, GeographicDivision division) {
        this.usuarios = usuarios;
        this.origenes = origenes;
        this.division = division;
    }

    /** @throws AccountNoLongerExistsException si la cuenta del token ya se cerro */
    @Transactional
    public ProfileView execute(UserId usuario) {
        User cuenta = usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);

        return origenes.deCuenta(usuario)
                .map(origen -> {
                    Municipality municipio = division.buscarMunicipio(origen.municipio())
                            .orElseThrow(() -> new IllegalStateException(
                                    "El origen de " + usuario + " apunta a un municipio que no existe"));
                    String departamento = division.departamentos().stream()
                            .filter(candidato -> candidato.codigo().equals(origen.departamento()))
                            .findFirst()
                            .orElseThrow(() -> new IllegalStateException(
                                    "El municipio " + municipio.codigo() + " apunta a un departamento que no existe"))
                            .nombre();
                    return new ProfileView(cuenta, municipio.nombre() + ", " + departamento, false);
                })
                .orElseGet(() -> new ProfileView(
                        cuenta, cuenta.city() == null ? null : cuenta.city().value(), true));
    }
}
