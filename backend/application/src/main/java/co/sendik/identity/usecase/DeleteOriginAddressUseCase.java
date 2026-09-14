package co.sendik.identity.usecase;

import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra la direccion de origen. HU-017, criterios 9 y 12.
 *
 * <p>No falla si no habia: borrar dos veces responde igual. Y comprueba que la cuenta
 * siga viva por lo mismo que {@code RemoveShippingAddressUseCase}: una cuenta cerrada
 * con token vivo no escribe.
 *
 * <p><strong>La ciudad del perfil queda vacia</strong> (criterio 12): el texto que habia
 * antes se descarto al guardar el origen y no se guardo en ninguna parte, asi que no hay
 * a que volver. Se puede escribir otra vez a mano.
 */
public class DeleteOriginAddressUseCase {

    private final OriginAddressRepository origenes;
    private final UserRepository usuarios;

    public DeleteOriginAddressUseCase(OriginAddressRepository origenes, UserRepository usuarios) {
        this.origenes = origenes;
        this.usuarios = usuarios;
    }

    /** @throws AccountNoLongerExistsException si la cuenta del token ya se cerro */
    @Transactional
    public void execute(UserId usuario) {
        if (usuarios.buscarPorId(usuario).isEmpty()) {
            throw new AccountNoLongerExistsException();
        }
        origenes.borrar(usuario);
    }
}
