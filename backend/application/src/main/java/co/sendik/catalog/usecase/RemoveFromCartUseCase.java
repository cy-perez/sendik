package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.port.out.CartItems;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quita un producto del carrito. HU-015, criterio 3.
 *
 * <p><strong>Idempotente y sin comprobar nada.</strong> Quitar lo que no esta no es un error,
 * y no se mira el estado de la publicacion a proposito: RN-094 conserva a la vista lo que
 * dejo de estar disponible, asi que tiene que poder sacarse. Un carrito donde lo vendido no
 * se pudiera quitar seria un carrito con basura permanente.
 *
 * <p><strong>No comprueba si la cuenta sigue abierta</strong>, al reves que agregar. Es la
 * misma distincion que en los favoritos: este caso de uso no crea dato ni revela nada sobre
 * una cuenta cerrada, solo borra. Anadirle la comprobacion seria una consulta mas a cambio
 * de nada.
 */
public class RemoveFromCartUseCase {

    private final CartItems carrito;

    public RemoveFromCartUseCase(CartItems carrito) {
        this.carrito = carrito;
    }

    @Transactional
    public void execute(CartCommand comando) {
        carrito.quitar(comando.quien(), comando.publicacion());
    }
}
