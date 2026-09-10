package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.catalog.port.out.SellerProfiles;
import co.sendik.shared.money.Money;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * El carrito de quien todavia no ha entrado. HU-015, criterio 13.
 *
 * <p><strong>Existe para que la suma se escriba una sola vez.</strong> Quien no tiene sesion
 * guarda su carrito en el navegador, pero los grupos por vendedor y los subtotales tienen que
 * salir iguales que los de quien si la tiene. La alternativa era que el navegador agrupara y
 * sumara por su cuenta, y eso deja dos implementaciones de la misma cifra —una en Java y otra
 * en TypeScript— que divergen en cuanto una de las dos cambie (ADR-0037).
 *
 * <p><strong>Es publico y no revela nada.</strong> Devuelve publicaciones del catalogo, que
 * ya son publicas por identificador desde HU-009: quien pregunta por veinte identificadores
 * podria preguntar veinte veces por la ficha de cada uno.
 *
 * <p><strong>Solo devuelve lo visible.</strong> A quien no ha entrado no se le puede decir
 * que un identificador existe pero no esta publicado, que es RN-068: lo que no esta se cae de
 * la respuesta y el navegador lo pinta apagado con la copia que guardo, que es exactamente el
 * criterio 21 visto desde el otro lado. La diferencia con {@link ReadCartUseCase} —que si
 * trae lo no disponible— no es una incoherencia: alli quien pregunta tiene una fila suya en
 * la base que prueba que ya conocia esa publicacion, y aqui solo hay una lista que cualquiera
 * pudo escribir.
 *
 * <p><strong>No hay fecha de cuando se agrego, porque no se guardo en ninguna parte.</strong>
 * El orden lo trae el navegador en la lista y aqui se respeta: al primer identificador se le
 * da el instante mas reciente y a los demas uno cada vez mas antiguo, de modo que
 * {@link Cart#de} conserve el orden en que llegaron. Es artificio de lectura y no dato: nada
 * de esto se guarda.
 *
 * <p>El precio de entrada se toma como el vigente, asi que el aviso de cambio de precio del
 * criterio 18 no existe sin sesion. No hay con que: nadie anoto cuanto costaba cuando se
 * agrego.
 */
public class PreviewCartUseCase {

    private final ListingRepository publicaciones;
    private final SellerProfiles vendedores;
    private final Clock reloj;

    public PreviewCartUseCase(ListingRepository publicaciones, SellerProfiles vendedores, Clock reloj) {
        this.publicaciones = publicaciones;
        this.vendedores = vendedores;
        this.reloj = reloj;
    }

    /*
     * Sin readOnly = true, por lo mismo que los demas de lectura.
     */
    @Transactional
    public CartView execute(List<ListingId> ids) {
        if (ids.isEmpty()) {
            return new CartView(Cart.de(List.of()), Map.of());
        }

        Map<ListingId, Listing> porId = new HashMap<>();
        publicaciones.buscarVarias(ids).stream()
                .filter(Listing::esVisible)
                .forEach(publicacion -> porId.put(publicacion.id(), publicacion));

        Instant ahora = reloj.instant();
        List<CartLine> lineas = new ArrayList<>();
        for (int i = 0; i < ids.size(); i++) {
            Listing publicacion = porId.get(ids.get(i));
            if (publicacion == null) {
                continue;
            }

            Money precio = publicacion.product().price();
            lineas.add(new CartLine(publicacion, ahora.minusMillis(i), precio == null ? Money.dePesos(0) : precio));
        }

        return ReadCartUseCase.conVendedores(Cart.de(lineas), vendedores);
    }
}
