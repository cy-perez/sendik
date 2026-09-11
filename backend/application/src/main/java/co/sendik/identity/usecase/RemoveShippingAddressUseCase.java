package co.sendik.identity.usecase;

import co.sendik.identity.dto.RemoveShippingAddressCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.AddressBook;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quita una direccion de la libreta. HU-016, criterios 10, 11, 12 y 14.
 *
 * <p><strong>Es idempotente y no falla por repetirse</strong> (criterio 14). Borrar lo que
 * ya no esta responde igual que borrar lo que estaba: un reintento de red o dos pestanas no
 * tienen por que distinguirse, y no hay nada que informar.
 *
 * <p>Por eso tampoco lanza {@code AddressNotFoundException} sobre una direccion ajena: la
 * filtra y no hace nada, que desde fuera es indistinguible de un identificador inventado.
 * El resultado es el mismo 204 y no se revela que exista.
 *
 * <p><strong>El relevo de la predeterminada va en la misma transaccion que el borrado</strong>,
 * y en ese orden: primero se borra la que se va y despues se marca la que la releva. Al
 * reves habria dos filas con {@code is_default} a la vez y el indice unico parcial de V21
 * se dispararia.
 */
public class RemoveShippingAddressUseCase {

    private final ShippingAddressRepository direcciones;
    private final UserRepository usuarios;

    public RemoveShippingAddressUseCase(ShippingAddressRepository direcciones, UserRepository usuarios) {
        this.direcciones = direcciones;
        this.usuarios = usuarios;
    }

    /**
     * @throws AccountNoLongerExistsException si la cuenta del token ya se cerro
     */
    @Transactional
    public void execute(RemoveShippingAddressCommand comando) {
        if (usuarios.buscarPorId(comando.usuario()).isEmpty()) {
            throw new AccountNoLongerExistsException();
        }

        AddressBook libreta = new AddressBook(direcciones.deCuenta(comando.usuario()));

        // Si no esta en su libreta no hay nada que hacer, y eso incluye la de otra persona:
        // el mismo 204 que un identificador inventado (criterios 14 y 15).
        if (libreta.buscar(comando.direccion()).isEmpty()) {
            return;
        }

        // RN-099: quien releva a la predeterminada es la mas reciente de las que quedan, y
        // eso solo lo sabe la libreta entera. Se calcula antes de borrar porque despues ya
        // no esta la que se va y no se podria saber si lo era.
        Optional<ShippingAddress> relevo = libreta.relevoAlQuitar(comando.direccion());

        direcciones.borrar(comando.direccion());

        relevo.ifPresent(nueva -> direcciones.marcarPredeterminada(comando.usuario(), nueva.id()));
    }
}
