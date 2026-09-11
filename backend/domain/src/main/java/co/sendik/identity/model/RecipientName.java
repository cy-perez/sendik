package co.sendik.identity.model;

import java.util.Objects;

/**
 * El nombre de quien recibe el paquete. HU-016, RN-104.
 *
 * <p><strong>Puede no ser el titular de la cuenta</strong>, y por eso es un tipo
 * propio y no {@link DisplayName}: la transportadora llama a quien esta en el
 * destino, que a veces es la madre de quien compro o el portero de una oficina.
 * Cuando no coinciden, esto es dato personal de un tercero que nunca abrio una
 * cuenta (docs/operacion/datos-personales.md).
 *
 * <p>Se guarda cifrado, como el resto de los campos libres de la direccion.
 *
 * <p>No se valida que "parezca" un nombre. Un apellido compuesto, un nombre de
 * empresa, "Portería torre 3" y un nombre indigena son todos validos, y cualquier
 * patron que intente distinguirlos deja fuera a alguien real sin ganar nada: este
 * texto no se procesa, se imprime en una guia para que una persona lo lea.
 */
public record RecipientName(String value) {

    private static final int LARGO_MINIMO = 2;
    private static final int LARGO_MAXIMO = 80;

    public RecipientName {
        Objects.requireNonNull(value, "El nombre de quien recibe es obligatorio");
        // `\\p{Cntrl}` en Java es solo ASCII, asi que U+2028, U+2029 y la anulacion
        // bidireccional U+202E atravesaban esto y quedaban guardados. Lo cazo la
        // revision de seguridad.
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (value.length() < LARGO_MINIMO) {
            throw new IllegalArgumentException(
                    "El nombre de quien recibe necesita al menos " + LARGO_MINIMO + " caracteres");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El nombre de quien recibe supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
