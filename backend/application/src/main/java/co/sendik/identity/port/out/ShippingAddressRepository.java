package co.sendik.identity.port.out;

import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;
import java.util.List;
import java.util.Optional;

/**
 * Las direcciones de entrega guardadas. HU-016.
 *
 * <p>Un repositorio por agregado (backend/CLAUDE.md), y el agregado es la direccion: la
 * libreta no es una entidad guardada sino «las filas de esta persona», igual que el
 * carrito y los favoritos. Por eso no hay {@code guardar(AddressBook)}.
 *
 * <p><strong>No es un puerto entre contextos</strong>, al reves que {@link UserFavorites} y
 * {@link UserCart}: aquellos existen porque las tablas son del catalogo y {@code identity}
 * no puede tocarlas. La direccion de entrega es de {@code identity} (ADR-0039), asi que el
 * cierre de cuenta y la descarga de datos la alcanzan sin intermediario.
 */
public interface ShippingAddressRepository {

    /**
     * La libreta de alguien, de la mas reciente a la mas antigua.
     *
     * <p><strong>Ese orden es parte del contrato de este metodo</strong> y no una
     * casualidad del indice: de el depende que {@code AddressBook.relevoAlQuitar} devuelva
     * «la mas reciente de las que quedan», que es el criterio 11.
     *
     * <p>Sin paginar: con el tope de RN-101 no hace falta.
     */
    List<ShippingAddress> deCuenta(UserId cuenta);

    /**
     * Una por su identificador, sea de quien sea.
     *
     * <p>Devuelve la de cualquiera a proposito: quien comprueba de quien es, es el caso de
     * uso, y lo hace para responder 404 y no 403 (criterio 15). Un metodo que filtrara por
     * dueno aqui dejaria esa comprobacion invisible y sin prueba propia.
     */
    Optional<ShippingAddress> buscar(ShippingAddressId id);

    /** Inserta si no estaba y reemplaza si estaba. */
    void guardar(ShippingAddress direccion);

    /** No falla si ya no estaba: el criterio 14 pide que borrar dos veces responda igual. */
    void borrar(ShippingAddressId id);

    /**
     * Cambia cual es la predeterminada de esa cuenta, en un solo gesto. Criterio 13.
     *
     * <p><strong>Son dos escrituras y tienen que ir en orden</strong>: quitarle la marca a
     * la que la tenia y ponersela a la nueva. Al reves, el indice unico parcial de V21 se
     * dispara con dos predeterminadas a la vez. Por eso es un metodo del repositorio y no
     * dos llamadas desde el caso de uso: el orden es un detalle de como se escribe.
     */
    void marcarPredeterminada(UserId cuenta, ShippingAddressId direccion);

    /**
     * Las borra todas. Para el cierre de cuenta (RN-102).
     *
     * <p>No falla si no hay ninguna, que es el caso de casi todas las cuentas que se
     * cierran.
     */
    void borrarDe(UserId cuenta);
}
