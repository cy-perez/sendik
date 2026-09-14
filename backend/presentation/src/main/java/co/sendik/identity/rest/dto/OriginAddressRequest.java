package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cuerpo de {@code PUT /api/v1/users/me/origin-address}. HU-017.
 *
 * <p>Es {@link ShippingAddressRequest} sin quien recibe ni telefono: el remitente es el
 * titular de la cuenta y sus datos estan en el perfil (RN-078). Lo demas es igual, y por
 * las mismas razones: sin campo de departamento (RN-100), forma y no contenido, y el
 * borde midiendo exactamente lo que va a medir el dominio, que es lo que hace el
 * constructor compacto.
 *
 * @param postalCode solo digitos; la cadena vacia se admite como ausencia
 */
public record OriginAddressRequest(
        @NotBlank @Pattern(regexp = "\\d{5}") String municipalityCode,
        @NotBlank @Size(min = 5, max = 120) String line,
        @Nullable @Size(max = 60) String complement,
        @Nullable @Size(max = 200) String instructions,
        @Nullable @Pattern(regexp = "\\d{6}|") String postalCode) {

    private static final java.util.regex.Pattern INVISIBLES =
            java.util.regex.Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private static final java.util.regex.Pattern ESPACIOS = java.util.regex.Pattern.compile("\\s+");

    /** El mismo colapso que hacen los objetos de valor, antes de que {@code @Size} mida. */
    public OriginAddressRequest {
        line = normalizado(line);
        complement = normalizado(complement);
        instructions = normalizado(instructions);
    }

    private static @Nullable String normalizado(@Nullable String valor) {
        if (valor == null) {
            return null;
        }
        String sinInvisibles = INVISIBLES.matcher(valor).replaceAll(" ");
        return ESPACIOS.matcher(sinInvisibles).replaceAll(" ").trim();
    }

    /** No imprime nada de lo que hay dentro, tampoco el municipio (criterio 17). */
    @Override
    public String toString() {
        return "OriginAddressRequest[sin imprimir]";
    }
}
