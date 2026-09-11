package co.sendik.identity.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * La libreta de direcciones de una persona. HU-016, RN-099 y RN-101.
 *
 * <p>Es un objeto de lectura que sabe las dos reglas que una direccion suelta no puede
 * saber, porque las dos son sobre el conjunto: <strong>cuantas caben</strong> y
 * <strong>cual es la predeterminada</strong>. El mismo reparto que en el carrito, donde
 * {@code CartItem} no puede contar a sus hermanos y {@code Cart} si.
 *
 * <p><strong>Las direcciones llegan ordenadas de la mas reciente a la mas antigua</strong>,
 * que es el orden que el indice de V21 produce y del que depende {@link #relevoAlQuitar}.
 *
 * <p>No escribe nada. Quien guarda es el caso de uso, y quien garantiza que no haya dos
 * predeterminadas a la vez es el indice unico parcial de la base: entre leer cual es la
 * actual y escribir la nueva cabe la peticion de otra pestana, y ahi no hay objeto de
 * dominio que salve.
 */
public record AddressBook(List<ShippingAddress> direcciones) {

    /**
     * Cuantas direcciones caben en una cuenta. RN-101.
     *
     * <p><strong>Diez y no veinte</strong>, que es el tope del carrito, y la diferencia no
     * es de gusto: alli RN-097 puso veinte porque el cuerpo de la fusion lo manda quien no
     * ha entrado, y sin tope es de tamano arbitrario. Aqui toda escritura es autenticada y
     * el tope existe para otra cosa —que una cuenta no se vuelva almacenamiento gratis de
     * texto libre cifrado—. Diez cubre con holgura las direcciones reales de una persona
     * —casa, trabajo, la de los padres, la de un regalo— y deja la libreta en una pantalla.
     */
    public static final int MAXIMO_DE_DIRECCIONES = 10;

    public AddressBook {
        Objects.requireNonNull(direcciones, "La lista de direcciones es obligatoria");
        direcciones = List.copyOf(direcciones);
    }

    public static AddressBook vacia() {
        return new AddressBook(List.of());
    }

    public boolean estaVacia() {
        return direcciones.isEmpty();
    }

    public int cuantasTiene() {
        return direcciones.size();
    }

    /** Si agregar una mas ya no cabe (RN-101). */
    public boolean estaLlena() {
        return direcciones.size() >= MAXIMO_DE_DIRECCIONES;
    }

    /**
     * Si la que entra tiene que nacer predeterminada. RN-099.
     *
     * <p>La primera lo es sin que nadie lo pida: una libreta con direcciones y sin
     * predeterminada obligaria a elegir en el peor momento, que es al pagar.
     */
    public boolean laSiguienteSeriaPredeterminada() {
        return direcciones.isEmpty();
    }

    public Optional<ShippingAddress> predeterminada() {
        return direcciones.stream().filter(ShippingAddress::esPredeterminada).findFirst();
    }

    public Optional<ShippingAddress> buscar(ShippingAddressId id) {
        return direcciones.stream()
                .filter(direccion -> direccion.id().equals(id))
                .findFirst();
    }

    /**
     * Quien pasa a ser la predeterminada si se quita esta. Criterios 11 y 12.
     *
     * <p>Devuelve vacio en los dos casos en que no hay relevo que hacer: cuando la que se
     * quita <strong>no era</strong> la predeterminada —la libreta sigue teniendo la suya— y
     * cuando era la unica que quedaba, que es el criterio 12: la libreta se queda vacia y
     * sin predeterminada, y eso es correcto.
     *
     * <p>Cuando si lo hay, es <strong>la mas reciente de las que quedan</strong>. Es la
     * primera de la lista descontando la que sale, porque llegan ordenadas asi.
     */
    public Optional<ShippingAddress> relevoAlQuitar(ShippingAddressId id) {
        Optional<ShippingAddress> saliente = buscar(id);
        if (saliente.isEmpty() || !saliente.get().esPredeterminada()) {
            return Optional.empty();
        }
        return direcciones.stream()
                .filter(direccion -> !direccion.id().equals(id))
                .findFirst();
    }
}
