package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.MergeCartCommand;
import co.sendik.catalog.dto.MergeResult;
import co.sendik.catalog.exception.BuyerAccountClosedException;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;

/**
 * Une al carrito de la cuenta lo que traia el navegador. HU-015, criterios 9, 10 y 12.
 *
 * <p><strong>Union y no reemplazo</strong> (RN-095): no se pierde lo que traia del navegador
 * ni se borra lo que ya tenia guardado. Como la escritura es idempotente por el par, unir dos
 * veces lo mismo da lo mismo y la peticion se puede repetir sin dano.
 *
 * <p><strong>Es aqui donde RN-092 se aplica por primera vez a esas filas.</strong> Mientras
 * el carrito fue anonimo no habia contra quien comparar, asi que lo propio pudo entrar en el.
 * El ingreso es el momento en que aparece alguien con nombre, y lo suyo se descarta.
 *
 * <p><strong>No falla entera por una linea mala.</strong> Lo que ya no existe, lo que dejo de
 * estar publicado y lo propio se descartan y se anotan en {@link MergeResult#noEntraron()}.
 * Rechazar la peticion completa obligaria a quien acaba de entrar a limpiar a mano un carrito
 * que armo hace semanas. Lo unico que si la detiene es que la cuenta este cerrada: eso no es
 * una linea mala, es que no hay a donde fusionar.
 *
 * <p><strong>El tope se respeta conservando lo mas reciente</strong> (criterio 10). La lista
 * llega en el orden del navegador —lo ultimo agregado primero— y se toma por delante hasta
 * llenar lo que quede libre; el resto se anota. Lo que ya estaba en la cuenta no se toca ni
 * se desplaza: quien entra no puede perder lo que ya habia guardado por culpa de lo que traia
 * el navegador.
 */
public class MergeCartUseCase {

    private final CartItems carrito;
    private final ListingRepository publicaciones;
    private final BuyerAccounts cuentas;
    private final ReadCartUseCase lectura;
    private final Clock reloj;

    public MergeCartUseCase(
            CartItems carrito,
            ListingRepository publicaciones,
            BuyerAccounts cuentas,
            ReadCartUseCase lectura,
            Clock reloj) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
        this.cuentas = cuentas;
        this.lectura = lectura;
        this.reloj = reloj;
    }

    /**
     * @throws BuyerAccountClosedException si la cuenta del token ya se cerro
     */
    @Transactional
    public MergeResult execute(MergeCartCommand comando) {
        if (!cuentas.estaActiva(comando.quien())) {
            throw new BuyerAccountClosedException();
        }

        List<ListingId> noEntraron = new ArrayList<>();

        if (comando.publicaciones().isEmpty()) {
            return new MergeResult(lectura.execute(comando.quien()), noEntraron);
        }

        Map<ListingId, Listing> porId = new HashMap<>();
        publicaciones
                .buscarVarias(comando.publicaciones())
                .forEach(publicacion -> porId.put(publicacion.id(), publicacion));

        Instant ahora = reloj.instant();

        for (ListingId id : comando.publicaciones()) {
            Listing publicacion = porId.get(id);

            // No existe, no esta publicada (RN-068) o es de quien entra (RN-092). Los tres
            // se descartan sin distinguirse: la respuesta dice cuales no entraron, no por
            // que. Distinguirlos aqui seria decir sobre esos identificadores lo que RN-068
            // no deja decir en ninguna otra parte.
            if (publicacion == null || !publicacion.esVisible() || publicacion.esDe(comando.quien())) {
                noEntraron.add(id);
                continue;
            }

            // Lo que ya estaba en la cuenta no consume hueco ni se vuelve a escribir con otra
            // fecha: el par ya existe y la escritura es idempotente.
            if (carrito.existe(comando.quien(), id)) {
                continue;
            }

            // **Se cuenta en cada vuelta y no una sola vez al principio.** Contarlo fuera del
            // bucle deja un desbordamiento mucho peor que el de agregar de uno en uno: dos
            // fusiones simultaneas sobre un carrito vacio leerian las dos «caben veinte» y
            // escribirian veinte cada una, o sea cuarenta filas. Y es justo el cuerpo que
            // RN-097 existe para acotar, porque el servidor lo recibe entero.
            //
            // Contando dentro, lo que cabe es a lo sumo un desbordamiento por vuelta perdida,
            // igual que en AddToCartUseCase y por la misma razon: cerrarlo del todo exigiria
            // bloquear la cuenta entera durante la fusion.
            if (carrito.cuantosLleva(comando.quien()) >= Cart.MAXIMO_DE_PRODUCTOS) {
                noEntraron.add(id);
                continue;
            }

            carrito.guardar(CartItem.de(comando.quien(), publicacion, ahora));
        }

        return new MergeResult(lectura.execute(comando.quien()), noEntraron);
    }
}
