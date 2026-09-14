package co.sendik.identity.model;

import java.util.Objects;

/**
 * Ciudad de la persona, escrita a mano. Dato personal de nivel publico
 * (docs/operacion/datos-personales.md).
 *
 * <p><strong>Es texto libre solo mientras la cuenta no tiene direccion de origen.</strong>
 * Este comentario defendia el texto libre porque "para dar una idea de donde sale el
 * producto basta"; HU-017 le dio a esa idea un uso concreto, cotizar el envio, y con
 * "bogota" no hay tarifa. Desde entonces, cuando hay {@link OriginAddress} la ciudad
 * del perfil es el municipio del origen, elegido de la division del DANE, y este campo
 * queda en nulo: no se copia, se lee uniendo (ADR-0042). Lo escrito a mano se conserva
 * solo hasta que la persona guarde su origen.
 */
public record City(String value) {

    private static final int LARGO_MAXIMO = 80;

    public City {
        Objects.requireNonNull(value, "La ciudad es obligatoria");
        value = value.trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException("La ciudad no puede estar vacia: para no tenerla, se deja sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La ciudad supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
