package co.sendik.catalog.usecase;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.port.out.CartItems;
import org.springframework.transaction.annotation.Transactional;

/**
 * Borra el carrito de una persona. HU-015, y el derecho de supresion de la Ley 1581.
 *
 * <p>Lo llama el cierre de cuenta, desde {@code identity}, y por un puerto: un contexto no
 * escribe en las tablas de otro. Es el gemelo de {@link EraseFavoritesUseCase} y no se fundio
 * con el a proposito: son dos tablas, dos reglas y el dia que una de las dos tenga algo mas
 * que borrar la otra no tiene por que enterarse.
 *
 * <p><strong>Borra de verdad, no anonimiza.</strong> Un carrito sin dueno no le sirve a nadie
 * y seguiria diciendo que alguien estuvo a punto de comprar eso.
 *
 * <p>No falla si esta vacio, que es el caso de casi todas las cuentas que se cierran.
 */
public class EraseCartUseCase {

    private final CartItems carrito;

    public EraseCartUseCase(CartItems carrito) {
        this.carrito = carrito;
    }

    @Transactional
    public void execute(BuyerId quien) {
        carrito.borrarTodosDe(quien);
    }
}
