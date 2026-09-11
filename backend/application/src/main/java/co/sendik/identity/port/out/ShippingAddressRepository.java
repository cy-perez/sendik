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
     * Una direccion <strong>de esa cuenta</strong>, por su identificador.
     *
     * <p><strong>El dueno va en la firma, y se corrigio despues de la revision de
     * seguridad.</strong> Antes devolvia la de cualquiera y el caso de uso filtraba despues,
     * con la idea de dejar la comprobacion del criterio 15 a la vista. El problema es cuando
     * ocurria: el adaptador ya habia descifrado la fila ajena y reconstruido sus seis objetos
     * de valor <strong>antes</strong> de que nadie decidiera el 404. Eso descifra la direccion
     * de otra persona en cada sondeo, y —peor— convierte cualquier fallo de esa fila en un
     * oraculo: una version de clave retirada o un valor que ya no pasa la validacion del
     * dominio saldria como 500 o 400 en vez de 404, distinguiendo «existe y es de alguien» de
     * «no existe», que es justo lo que el criterio 15 prohibe.
     *
     * <p>Filtrar aqui no esconde la regla: el 404 lo sigue lanzando el caso de uso, con su
     * prueba propia. Lo que cambia es que la fila ajena no llega a descifrarse.
     */
    Optional<ShippingAddress> buscar(ShippingAddressId id, UserId cuenta);

    /** Inserta si no estaba y reemplaza si estaba. */
    void guardar(ShippingAddress direccion);

    /**
     * La borra, si es de esa cuenta.
     *
     * <p>No falla si ya no estaba: el criterio 14 pide que borrar dos veces responda igual.
     *
     * <p><strong>El dueno va en la firma por lo mismo que en {@link #buscar}</strong>, y se
     * corrigio despues de la segunda revision de seguridad: sin el, el puerto exponia un
     * primitivo de borrado entre cuentas a una llamada de distancia, y lo unico que lo
     * sostenia era una comprobacion en memoria del caso de uso. Es justo lo que la
     * correccion de {@code buscar} habia decidido no hacer.
     */
    void borrar(ShippingAddressId id, UserId cuenta);

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
