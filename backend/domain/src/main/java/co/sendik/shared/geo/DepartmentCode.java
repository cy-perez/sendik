package co.sendik.shared.geo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Codigo de departamento de la division politico-administrativa del DANE. HU-016.
 *
 * <p>Dos digitos, y se guardan como texto y no como numero por una razon concreta:
 * varios empiezan por cero —Antioquia es {@code 05} y Atlantico {@code 08}— y un
 * entero se los come. Un codigo con el cero perdido no casa con ninguna fila.
 *
 * <p>Vive en {@code shared} y no en {@code identity} porque no es de nadie: hoy lo
 * usa la direccion de entrega y manana el cotizador de envios (ADR-0040).
 */
public record DepartmentCode(String value) {

    /**
     * Sin el valor dentro, y no es cosmetica.
     *
     * <p>{@code ApiExceptionHandler} registra el mensaje de esta excepcion, y este
     * codigo llega de un segmento de la ruta: seria entrada elegida por quien llama,
     * reflejada literalmente en el registro. Es la misma decision que ya se tomo en
     * {@code PostalCode}, y se extendio aqui tras la revision de seguridad.
     */
    private static final String MAL_FORMADO = "El codigo de departamento son dos digitos";

    private static final Pattern VALIDO = Pattern.compile("\\d{2}");

    public DepartmentCode {
        Objects.requireNonNull(value, "El codigo de departamento es obligatorio");
        value = value.trim();

        if (!VALIDO.matcher(value).matches()) {
            throw new IllegalArgumentException(MAL_FORMADO);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
