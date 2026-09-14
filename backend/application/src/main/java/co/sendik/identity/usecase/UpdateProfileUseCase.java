package co.sendik.identity.usecase;

import co.sendik.identity.dto.UpdateProfileCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.City;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import org.springframework.transaction.annotation.Transactional;

/**
 * Edita el perfil. Criterio 21.
 *
 * <p>Sin correo: cambiarlo es otra operacion, porque exige verificar el nuevo
 * antes de reemplazar el anterior y eso no cabe en un formulario que se guarda de
 * una vez.
 *
 * <p>Los tres campos se escriben juntos aunque solo cambie uno. Es un formulario,
 * no tres: guardar solo lo que cambio obligaria al borde a distinguir "no lo
 * mando" de "lo dejo vacio", y esa distincion es justo donde se pierde el borrado
 * de un dato.
 *
 * <p><strong>Con direccion de origen, la ciudad no se edita desde aqui</strong> (HU-017,
 * criterio 11, ADR-0042): es el municipio del origen y se lee uniendo. Lo que llegue en
 * el comando se descarta y el campo queda en nulo, que es lo que garantiza que no haya dos
 * verdades. El borde ya no ofrece el campo en ese caso; esto es lo que lo sostiene si
 * alguien lo manda igual.
 */
public class UpdateProfileUseCase {

    private final UserRepository usuarios;
    private final OriginAddressRepository origenes;

    public UpdateProfileUseCase(UserRepository usuarios, OriginAddressRepository origenes) {
        this.usuarios = usuarios;
        this.origenes = origenes;
    }

    @Transactional
    public User execute(UpdateProfileCommand comando) {
        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);

        boolean tieneOrigen = origenes.deCuenta(comando.usuario()).isPresent();

        User actualizada = cuenta.conPerfil(
                new DisplayName(comando.displayName()),
                tieneOrigen ? null : siHay(comando.city(), City::new),
                siHay(comando.phone(), Phone::new));

        usuarios.actualizar(actualizada);
        return actualizada;
    }

    /** Vacio y ausente son lo mismo: la persona no quiere tener ese dato. */
    private static <T> @Nullable T siHay(@Nullable String valor, Function<String, T> como) {
        return valor == null || valor.isBlank() ? null : como.apply(valor);
    }
}
