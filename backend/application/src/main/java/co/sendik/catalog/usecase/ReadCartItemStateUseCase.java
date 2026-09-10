package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.dto.CartItemState;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.ListingRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Si una publicacion esta en el carrito, y si se puede agregar. HU-015, criterios 1 y 5.
 *
 * <p><strong>Existe para que la ficha publica siga siendo publica.</strong>
 * {@code GET /listings/{id}} responde hoy lo mismo para cualquiera y se renderiza en el
 * servidor; anadirle un campo «esto esta en tu carrito» la volveria distinta por persona y
 * arruinaria esa propiedad. El estado se pide aparte y desde el navegador, despues de
 * hidratar. Es la misma decision que HU-011 tomo para el control de favorito.
 *
 * <p><strong>Quien no tiene sesion no llega hasta aqui</strong>, y no porque se le rechace:
 * su carrito vive en el navegador, asi que sabe sin preguntar si el producto esta dentro. La
 * unica parte que no puede resolver sola es {@code elegible} —no sabe si la publicacion es
 * suya, porque no hay quien la compare— y no le hace falta: sin sesion no hay publicacion
 * propia posible. Esa comprobacion llega en la fusion.
 *
 * <p>Nunca falla por no encontrar la publicacion. Una ficha que se acaba de vender responde
 * {@code (false, false)} y con eso la pantalla retira el control.
 */
public class ReadCartItemStateUseCase {

    private final CartItems carrito;
    private final ListingRepository publicaciones;

    public ReadCartItemStateUseCase(CartItems carrito, ListingRepository publicaciones) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
    }

    /*
     * Sin readOnly = true, por lo mismo que ReadFavoriteStateUseCase: presentation no
     * declara spring-tx, y al leer el atributo para inyectar esta clase avisa de que no
     * puede resolverlo. Con -Xlint:all -Werror ese aviso rompe la compilacion.
     */
    @Transactional
    public CartItemState execute(CartCommand consulta) {
        boolean dentro = carrito.existe(consulta.quien(), consulta.publicacion());

        boolean sePuede = publicaciones
                .buscar(consulta.publicacion())
                .filter(Listing::esVisible)
                .filter(publicacion -> !publicacion.esDe(consulta.quien()))
                .isPresent();

        return new CartItemState(dentro, sePuede);
    }
}
