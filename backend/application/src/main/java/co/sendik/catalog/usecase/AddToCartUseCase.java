package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.exception.BuyerAccountClosedException;
import co.sendik.catalog.exception.CartFullException;
import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.BuyerAccounts;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.catalog.port.out.ListingRepository;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agrega un producto al carrito. HU-015, criterios 2, 4, 5, 6 y 7.
 *
 * <p><strong>Carga la publicacion aunque solo vaya a escribir un par de identificadores y un
 * precio.</strong> Es lo que le permite a {@link CartItem#de} comprobar RN-068 y RN-092 con
 * la publicacion delante, y ademas lo unico que da el precio de entrada del criterio 18.
 *
 * <p><strong>Comprueba que la cuenta siga existiendo</strong>, como {@code AddFavoriteUseCase}
 * y por lo mismo: el token sobrevive quince minutos al cierre (ADR-0003) y este es un caso de
 * uso que escribe dato personal. Sin esto quedaria carrito vivo justo despues de ejercer el
 * derecho de supresion, y nada volveria a borrarlo porque el cierre ya paso.
 *
 * <p><strong>El tope se comprueba aqui y no en el dominio.</strong> Un {@link CartItem} no
 * puede saber cuantos hermanos tiene; el carrito entero si, y quien sabe cuantos hay
 * guardados es el puerto. Se cuenta antes de escribir a proposito y se acepta lo que eso
 * implica: dos pestanas que agregan a la vez con diecinueve productos pueden dejar
 * veintiuno. Es un desbordamiento de uno, acotado y sin consecuencia —el tope existe para
 * que el cuerpo de la fusion no sea arbitrario, no para que veintiuno rompa nada— y
 * cerrarlo del todo exigiria bloquear la cuenta entera en cada peticion de agregar.
 */
public class AddToCartUseCase {

    private final CartItems carrito;
    private final ListingRepository publicaciones;
    private final BuyerAccounts cuentas;
    private final Clock reloj;

    public AddToCartUseCase(CartItems carrito, ListingRepository publicaciones, BuyerAccounts cuentas, Clock reloj) {
        this.carrito = carrito;
        this.publicaciones = publicaciones;
        this.cuentas = cuentas;
        this.reloj = reloj;
    }

    /**
     * @throws BuyerAccountClosedException si la cuenta del token ya se cerro
     * @throws ListingNotFoundException si no existe o no esta publicada. Las dos con el mismo
     *     codigo, que es RN-068
     * @throws co.sendik.catalog.exception.SelfCartForbiddenException si es suya (RN-092)
     * @throws CartFullException si ya lleva el tope de RN-097
     */
    @Transactional
    public void execute(CartCommand comando) {
        if (!cuentas.estaActiva(comando.quien())) {
            throw new BuyerAccountClosedException();
        }

        Listing publicacion = publicaciones
                .buscar(comando.publicacion())
                .orElseThrow(() -> new ListingNotFoundException(comando.publicacion()));

        // El tope se mira solo cuando el producto no estaba ya, porque volver a agregar lo
        // que ya esta no suma ninguna fila. Sin esta condicion, un carrito lleno rechazaria
        // el reintento de algo que ya tiene dentro y romperia la idempotencia del criterio 4
        // justo en el borde donde mas se nota.
        if (!carrito.existe(comando.quien(), comando.publicacion())
                && carrito.cuantosLleva(comando.quien()) >= Cart.MAXIMO_DE_PRODUCTOS) {
            throw new CartFullException();
        }

        carrito.guardar(CartItem.de(comando.quien(), publicacion, reloj.instant()));
    }
}
