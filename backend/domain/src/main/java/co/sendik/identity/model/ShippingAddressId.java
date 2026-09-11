package co.sendik.identity.model;

import co.sendik.shared.id.Uuid7;
import java.util.Objects;
import java.util.UUID;

/**
 * Identificador de una direccion de entrega. HU-016.
 *
 * <p>Una direccion tiene identidad propia, al reves que un favorito o un item del
 * carrito, donde la identidad es el par persona-publicacion. Aqui no hay par: la
 * misma persona puede guardar dos direcciones identicas en todos sus campos —la casa,
 * y la misma casa a nombre de otra persona para un regalo— y son dos.
 */
public record ShippingAddressId(UUID value) {

    public ShippingAddressId {
        Objects.requireNonNull(value, "El identificador es obligatorio");
    }

    public static ShippingAddressId nuevo() {
        return new ShippingAddressId(Uuid7.nuevo());
    }

    public static ShippingAddressId de(String texto) {
        Objects.requireNonNull(texto, "El identificador es obligatorio");
        try {
            return new ShippingAddressId(UUID.fromString(texto));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("El identificador no es un UUID valido", e);
        }
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
