package co.sendik.identity.persistence;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.usecase.EraseCartUseCase;
import co.sendik.catalog.usecase.ExportCartUseCase;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserCart;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * El carrito de una persona, preguntado al catalogo. HU-015.
 *
 * <p>Gemelo exacto de {@link CatalogUserFavorites} y por las mismas razones: pregunta por dos
 * casos de uso publicos de {@code catalog} y no por su tabla, traduce entre {@link UserId} y
 * {@link BuyerId} —que envuelven el mismo UUID y son tipos distintos a proposito— y convierte
 * al vocabulario del archivo de descarga, que {@code catalog} no tiene por que conocer.
 *
 * <p><strong>Son dos adaptadores y no uno.</strong> Juntarlos ahorraria una clase y ataria
 * dos tablas con dos reglas distintas al mismo bean: el dia que borrar un carrito tenga que
 * hacer algo mas que borrar filas —liberar una reserva, avisar a alguien— eso no puede
 * arrastrar a los favoritos.
 */
@Component
public class CatalogUserCart implements UserCart {

    private final ExportCartUseCase paraDescargar;
    private final EraseCartUseCase paraBorrar;

    public CatalogUserCart(ExportCartUseCase paraDescargar, EraseCartUseCase paraBorrar) {
        this.paraDescargar = paraDescargar;
        this.paraBorrar = paraBorrar;
    }

    @Override
    public List<UserDataExport.ProductoEnCarrito> de(UserId usuario) {
        return paraDescargar.execute(new BuyerId(usuario.value())).stream()
                .map(CatalogUserCart::aFilaDeDescarga)
                .toList();
    }

    @Override
    public void borrarDe(UserId usuario) {
        paraBorrar.execute(new BuyerId(usuario.value()));
    }

    /**
     * El identificador de la publicacion y la fecha, y nada mas.
     *
     * <p>No se copia el titulo, por lo mismo que en los favoritos: es del vendedor, cambia y
     * desaparece cuando se archiva.
     *
     * <p><strong>Y no se copia el precio de entrada.</strong> Es dato de la publicacion en un
     * instante, no de quien la agrego, y no responde a ninguna pregunta que esta persona pueda
     * hacerse sobre sus propios datos. Lo que es suyo es que lo puso en el carrito, y cuando.
     */
    private static UserDataExport.ProductoEnCarrito aFilaDeDescarga(CartItem item) {
        return new UserDataExport.ProductoEnCarrito(item.publicacion().toString(), item.agregadoEn());
    }
}
