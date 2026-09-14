package co.sendik.identity.model;

import java.util.Objects;

/**
 * Lo que hay que saber para recoger: "local 3 del centro comercial, entrar por el
 * parqueadero". HU-017.
 *
 * <p>Es la pareja de {@link DeliveryInstructions} al otro extremo del envio, con el
 * mismo tope y la misma limpieza. Es un tipo aparte y no el mismo porque quien lo lee
 * es la transportadora al recoger y no al entregar, y un nombre que dice "entrega"
 * sobre el origen del vendedor se leeria como un error.
 *
 * <p><strong>Es dato personal y a veces de un tercero</strong> —"preguntar por
 * Nubia"—, asi que se guarda cifrado y no aparece en ningun registro.
 */
public record PickupInstructions(String value) {

    private static final int LARGO_MAXIMO = 200;

    public PickupInstructions {
        Objects.requireNonNull(value, "Las indicaciones son obligatorias: para no tenerlas, se dejan sin poner");
        // `\\p{Cntrl}` en Java es solo ASCII; U+2028, U+2029 y U+202E pasarian sin esto.
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (value.isEmpty()) {
            throw new IllegalArgumentException(
                    "Las indicaciones no pueden estar vacias: para no tenerlas, se dejan sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("Las indicaciones superan los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
