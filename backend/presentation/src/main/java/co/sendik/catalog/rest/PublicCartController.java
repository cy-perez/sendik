package co.sendik.catalog.rest;

import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.rest.dto.CartResponse;
import co.sendik.catalog.rest.mapper.Carts;
import co.sendik.catalog.usecase.PreviewCartUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * El carrito de quien todavia no ha entrado. HU-015, criterio 13.
 *
 * <p><strong>Existe para que la suma se escriba una sola vez.</strong> Quien no tiene sesion
 * guarda su carrito en el navegador, y los grupos por vendedor y los subtotales tienen que
 * salir iguales que los de quien si la tiene. La alternativa —que el navegador agrupara y
 * sumara por su cuenta— deja dos implementaciones de la misma cifra, una en Java y otra en
 * TypeScript, que divergen en cuanto una de las dos cambie (ADR-0037).
 *
 * <p><strong>Recurso propio y no un parametro {@code ids} en {@code GET /listings}.</strong>
 * Esa era la primera idea y se descarto por dos razones: aquella ruta es tambien el catalogo
 * de HU-009 y la busqueda de HU-014, asi que anadirle un parametro obliga al patron del bean
 * testigo de {@code SearchFeature} para que la bandera no la apague entera; y sobre todo,
 * devuelve un tramo plano de publicaciones, que es justo lo que no sirve aqui. Este recurso
 * devuelve <strong>la misma forma</strong> que {@code /users/me/cart}, y de eso vive que la
 * suma sea una sola.
 *
 * <p>No nace un {@code /listings/batch} por lo mismo que no nacio un {@code /search}: pedir
 * varias publicaciones es listar el mismo recurso con otra condicion. Lo que justifica una
 * ruta nueva no es pedir por identificador, es que lo que sale es un carrito.
 *
 * <p><strong>Es publico y no revela nada.</strong> Devuelve publicaciones del catalogo, que ya
 * son publicas por identificador desde HU-009: quien pregunta por veinte identificadores
 * podria preguntar veinte veces por la ficha de cada uno. Y solo devuelve lo visible, que es
 * RN-068: lo que no esta se cae de la respuesta y el navegador lo pinta apagado con la copia
 * que guardo.
 *
 * <p><strong>Detras de {@code FEATURE_CHECKOUT}</strong>, y aqui la bandera sale gratis: al
 * ser ruta propia, con la bandera apagada el controlador no se crea y la ruta entera
 * desaparece con un 404, sin tocar {@code SecurityConfig} y sin el bean testigo que hizo falta
 * para la busqueda.
 */
@RestController
@Validated
@RequestMapping("/api/v1/carts")
@ConditionalOnProperty(prefix = "sendik.features", name = "checkout", havingValue = "true")
public class PublicCartController {

    private final PreviewCartUseCase casoDeLeer;
    private final PublicFileStore almacen;

    public PublicCartController(PreviewCartUseCase casoDeLeer, PublicFileStore almacen) {
        this.casoDeLeer = casoDeLeer;
        this.almacen = almacen;
    }

    /**
     * Los productos que trae el navegador, agrupados y sumados.
     *
     * <p><strong>El tope se declara aqui y es el mismo de RN-097.</strong> No es una
     * optimizacion: es la unica defensa contra una peticion arbitraria de quien no ha entrado,
     * que es exactamente la razon de que ese tope exista. Por encima es 400 y no se recorta en
     * silencio, como el {@code limit} del catalogo.
     *
     * <p>Repetible —{@code ?ids=a&ids=b}— y no separado por comas, que es como el contrato ya
     * pasa {@code condition} y {@code color}.
     *
     * <p>El orden de la lista se respeta: es el unico orden que existe sin filas en la base,
     * porque nadie anoto cuando se agrego cada cosa.
     */
    @GetMapping
    public CartResponse carrito(
            @RequestParam(name = "ids") @NotEmpty @Size(max = Cart.MAXIMO_DE_PRODUCTOS) List<String> ids) {

        return Carts.de(casoDeLeer.execute(ids.stream().map(ListingId::de).toList()), almacen);
    }
}
