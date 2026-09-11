package co.sendik.identity.model;

import java.util.Objects;

/**
 * El complemento de la direccion: "Apto 802", "Torre 3", "Local 14". HU-016.
 *
 * <p>Opcional: quien no lo tiene no lo pone. Va aparte de {@link AddressLine} porque
 * es lo que el mensajero necesita despues de llegar al edificio, no para llegar, y
 * separarlos deja que la guia lo imprima donde corresponde.
 *
 * <p>Se guarda cifrado.
 */
public record AddressComplement(String value) {

    private static final int LARGO_MAXIMO = 60;

    public AddressComplement {
        Objects.requireNonNull(value, "El complemento es obligatorio: para no tenerlo, se deja sin poner");
        value = value.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "El complemento no puede estar vacio: para no tenerlo, se deja sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El complemento supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
