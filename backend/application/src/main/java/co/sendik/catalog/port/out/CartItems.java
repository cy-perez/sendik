package co.sendik.catalog.port.out;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.ListingId;
import java.util.List;

/**
 * Donde viven los productos del carrito. HU-015.
 *
 * <p>Puerto propio y no un metodo mas en {@link ListingRepository}, por lo mismo que
 * {@link Favorites}: aquel es el repositorio del agregado de la publicacion, y un carrito no
 * forma parte de ese agregado. Agregar algo al carrito no puede tocar la publicacion —eso es
 * RN-089 dicho en el codigo— y guardar una publicacion no puede tocar el carrito de nadie.
 *
 * <p><strong>Ningun metodo recibe la publicacion entera.</strong> Las reglas ya se
 * comprobaron cuando se construyo el {@link CartItem}; aqui solo se escribe y se lee.
 */
public interface CartItems {

    /**
     * Guarda el item. <strong>Idempotente</strong>: guardarlo dos veces deja uno.
     *
     * <p>Es el criterio 4, y se resuelve donde de verdad se puede resolver. Comprobar antes
     * de escribir no basta: entre la comprobacion y la escritura cabe la peticion de la otra
     * pestana. Lo sostiene la unicidad del par en la tabla, y quien implemente esto tiene que
     * apoyarse en ella y no en un {@code if}.
     *
     * <p><strong>Repetir no actualiza el precio de entrada.</strong> Es lo que hace que el
     * aviso del criterio 18 sobreviva a un reintento: si la segunda escritura pisara el
     * precio, volver a pulsar sobre un producto que subio de precio borraria justo el aviso
     * de que subio.
     */
    void guardar(CartItem item);

    /**
     * Lo quita. <strong>Idempotente tambien</strong>: quitar lo que no esta no es un error.
     *
     * <p>No comprueba el estado de la publicacion, y es a proposito: RN-094 conserva a la
     * vista lo que dejo de estar disponible, asi que tiene que poder quitarse. Un carrito
     * donde lo vendido no se pudiera sacar seria un carrito con basura permanente.
     */
    void quitar(BuyerId quien, ListingId publicacion);

    /** Si esa persona tiene esa publicacion en su carrito. Criterio 1. */
    boolean existe(BuyerId quien, ListingId publicacion);

    /**
     * Todo lo que esta persona lleva, <strong>sin filtrar por estado</strong>.
     *
     * <p>Es la diferencia exacta con {@link Favorites#publicadasDe}, y es deliberada. Alli
     * filtrar era la regla —RN-071 esconde lo que dejo de verse—; aqui filtrar seria el
     * defecto: RN-094 quiere que lo no disponible siga a la vista, apagado y fuera del
     * subtotal. Quien lee decide que se ensena y que suma; el puerto entrega todo.
     *
     * <p>Sin paginar y sin tope de argumento: el tope de RN-097 lo hace innecesario, y una
     * suma paginada no es una suma.
     *
     * <p>Ordena por la fecha en que se agrego, de lo mas reciente a lo mas antiguo. El orden
     * es el del gesto, como en los favoritos, y lo necesita ademas el criterio 10: cuando
     * una fusion no cabe entera, lo que se conserva es lo mas reciente.
     */
    List<CartItem> todosDe(BuyerId quien);

    /**
     * Cuantos lleva. Para el tope de RN-097.
     *
     * <p>Metodo propio y no {@code todosDe().size()}: comprobar el tope antes de agregar no
     * tiene por que traerse veinte filas de la base para contarlas.
     */
    int cuantosLleva(BuyerId quien);

    /**
     * Borra el carrito de esa persona. Para el cierre de cuenta.
     *
     * <p>Borrar de verdad y no anonimizar, igual que los favoritos: un carrito sin dueno no
     * le sirve a nadie y sigue diciendo que a alguien le interesaba eso, y ademas que estuvo
     * a punto de comprarlo.
     */
    void borrarTodosDe(BuyerId quien);
}
