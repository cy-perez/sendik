package co.sendik.identity.usecase;

import co.sendik.identity.dto.SetDefaultAddressCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.AddressNotFoundException;
import co.sendik.identity.model.AddressBook;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Elige cual es la direccion predeterminada. HU-016, criterios 13 y 15.
 *
 * <p><strong>Aqui no se garantiza que haya exactamente una: eso lo garantiza la base.</strong>
 * El indice unico parcial de V21 —{@code UNIQUE (user_id) WHERE is_default}— es lo que hace
 * imposible que dos pestanas dejen dos. Una comprobacion en este caso de uso no lo podria
 * cerrar: entre leer cual es la actual y escribir la nueva cabe la otra peticion.
 *
 * <p>Lo que si hace este caso de uso es lo que la base no puede: comprobar que la direccion
 * es de quien la pide, y responder 404 si no (criterio 15).
 *
 * <p>Marcar la que ya lo era no falla ni cambia nada, que es lo que se espera de un
 * {@code PUT} sobre un recurso singular.
 */
public class SetDefaultShippingAddressUseCase {

    private final ShippingAddressRepository direcciones;
    private final UserRepository usuarios;

    public SetDefaultShippingAddressUseCase(ShippingAddressRepository direcciones, UserRepository usuarios) {
        this.direcciones = direcciones;
        this.usuarios = usuarios;
    }

    /**
     * @throws AccountNoLongerExistsException si la cuenta del token ya se cerro
     * @throws AddressNotFoundException si no existe o no es suya
     */
    @Transactional
    public void execute(SetDefaultAddressCommand comando) {
        if (usuarios.buscarPorId(comando.usuario()).isEmpty()) {
            throw new AccountNoLongerExistsException();
        }

        AddressBook libreta = new AddressBook(direcciones.deCuenta(comando.usuario()));

        // Se busca dentro de SU libreta, no en la tabla entera: asi una direccion ajena cae
        // en el mismo 404 que una inventada, sin una comprobacion de dueno aparte.
        if (libreta.buscar(comando.direccion()).isEmpty()) {
            throw new AddressNotFoundException(comando.direccion());
        }

        direcciones.marcarPredeterminada(comando.usuario(), comando.direccion());
    }
}
