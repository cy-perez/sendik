package co.sendik.identity.model;

import java.util.Objects;

/**
 * Lo que hay que saber para entregar: "la casa de la esquina, el timbre no sirve".
 * HU-016.
 *
 * <p>Opcional. Es el campo donde la gente escribe media historia, asi que tiene tope
 * y se limpia igual que los demas.
 *
 * <p><strong>Es dato personal y a veces de un tercero</strong> —"preguntar por mi
 * mama"—, asi que se guarda cifrado y no aparece en ningun registro.
 */
public record DeliveryInstructions(String value) {

    private static final int LARGO_MAXIMO = 200;

    public DeliveryInstructions {
        Objects.requireNonNull(value, "Las indicaciones son obligatorias: para no tenerlas, se dejan sin poner");
        // `\\p{Cntrl}` en Java es solo ASCII, asi que U+2028, U+2029 y la anulacion
        // bidireccional U+202E atravesaban esto y quedaban guardados. Lo cazo la
        // revision de seguridad.
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
