package co.sendik.catalog.usecase;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.port.out.CartItems;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * Todo lo que una persona lleva en el carrito, para su descarga de datos. HU-015.
 *
 * <p>Existe para {@code identity}, que atiende el derecho a conocer de la Ley 1581 y no puede
 * leer esta tabla: un contexto no consulta las tablas de otro, le pregunta por un caso de uso
 * publico (docs/arquitectura/vision-tecnica.md).
 *
 * <p><strong>Sale todo, tambien lo que la pantalla ensena apagado.</strong> Aqui no hay
 * diferencia que hacer, porque el carrito ya lo trae todo: RN-094 conserva a la vista lo no
 * disponible, asi que la lista de la pantalla y esta son la misma lista vista por dos
 * derechos distintos.
 *
 * <p>No pagina. La descarga se sirve entera y en un solo archivo, como el resto.
 */
public class ExportCartUseCase {

    private final CartItems carrito;

    public ExportCartUseCase(CartItems carrito) {
        this.carrito = carrito;
    }

    @Transactional
    public List<CartItem> execute(BuyerId quien) {
        return carrito.todosDe(quien);
    }
}
