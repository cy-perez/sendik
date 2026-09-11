package co.sendik.shared.geo;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Codigo de municipio de la division politico-administrativa del DANE. HU-016, RN-100.
 *
 * <p>Cinco digitos, y como {@link DepartmentCode} van en texto porque varios empiezan
 * por cero.
 *
 * <p><strong>Lleva su departamento dentro.</strong> Los dos primeros digitos son el
 * codigo del departamento, sin una sola excepcion en las 1122 filas que V20 siembra.
 * De esa propiedad cuelga una decision del contrato: el cuerpo de la API pide solo el
 * municipio, y el departamento se deriva. Un par incoherente —municipio de un
 * departamento y departamento de otro— no puede existir porque no hay par, y el
 * criterio 5 de HU-016 se cumple por construccion en vez de por comprobacion.
 *
 * <p>Que el codigo exista de verdad es otra cosa y no la sabe este tipo: eso lo
 * decide la clave foranea y, antes, el caso de uso contra la division sembrada.
 */
public record MunicipalityCode(String value) {

    /**
     * Sin el valor dentro, y no es cosmetica.
     *
     * <p>{@code ApiExceptionHandler} registra el mensaje de esta excepcion, y este
     * codigo llega de un segmento de la ruta: seria entrada elegida por quien llama,
     * reflejada literalmente en el registro. Es la misma decision que ya se tomo en
     * {@code PostalCode}, y se extendio aqui tras la revision de seguridad.
     */
    private static final String MAL_FORMADO = "El codigo de municipio son cinco digitos";

    private static final Pattern VALIDO = Pattern.compile("\\d{5}");

    public MunicipalityCode {
        Objects.requireNonNull(value, "El codigo de municipio es obligatorio");
        value = value.trim();

        if (!VALIDO.matcher(value).matches()) {
            throw new IllegalArgumentException(MAL_FORMADO);
        }
    }

    /** El departamento al que pertenece, que son sus dos primeros digitos. */
    public DepartmentCode departamento() {
        return new DepartmentCode(value.substring(0, 2));
    }

    @Override
    public String toString() {
        return value;
    }
}
