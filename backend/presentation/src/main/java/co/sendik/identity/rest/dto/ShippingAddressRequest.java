package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cuerpo de {@code POST} y {@code PUT} sobre {@code /api/v1/users/me/addresses}. HU-016.
 *
 * <p>El mismo para crear y para editar: los campos son los mismos y lo unico que distingue
 * una operacion de la otra es que la segunda dice cual. {@code PUT} y no {@code PATCH}, por
 * lo mismo que {@link UpdateProfileRequest}: se manda la direccion entera y se guarda entera,
 * y con {@code PATCH} habria que distinguir «no mande este campo» de «lo deje vacio», que es
 * justo donde se pierde el borrado de un dato.
 *
 * <p><strong>No hay campo de departamento, y esa ausencia es la regla.</strong> El codigo del
 * municipio lleva dentro el de su departamento (RN-100), asi que un par incoherente no puede
 * existir porque no hay par: el criterio 5 de la historia se cumple por construccion. Lo que
 * este DTO no tenga es lo que nadie puede contradecir, y hay una prueba que manda
 * {@code departmentCode} de mas para fijar que el servidor no lo mira.
 *
 * <p><strong>Aqui se comprueba la forma y no el contenido.</strong> Que el municipio exista
 * de verdad lo decide el caso de uso contra la division sembrada; que la linea de direccion
 * sea una direccion no lo decide nadie, y es a proposito: una direccion colombiana lleva
 * almohadilla y guion y se escribe de quince maneras, asi que cualquier patron rechazaria
 * direcciones reales.
 *
 * <p><strong>El borde mide exactamente lo que va a medir el dominio</strong>, y de eso trata
 * el constructor compacto de abajo. La primera version no lo hacia y eso costo un fallo de
 * produccion.
 *
 * @param phone solo digitos, con un mas opcional delante. Los separadores que la gente
 *     escribe los quita el cliente antes de mandarlo
 * @param postalCode lo mismo, y la cadena vacia se admite como ausencia
 */
public record ShippingAddressRequest(
        @NotBlank @Size(min = 2, max = 80) String recipientName,
        @NotBlank @Pattern(regexp = "\\+?\\d{7,15}") String phone,
        @NotBlank @Pattern(regexp = "\\d{5}") String municipalityCode,
        @NotBlank @Size(min = 5, max = 120) String line,
        @Nullable @Size(max = 60) String complement,
        @Nullable @Size(max = 200) String instructions,
        @Nullable @Pattern(regexp = "\\d{6}|") String postalCode) {

    /**
     * El mismo colapso que hacen {@code AddressLine} y los demas objetos de valor.
     *
     * <p>Incluye {@code Cf}, {@code Zl} y {@code Zp} ademas de {@code Cntrl}, que en Java es
     * solo ASCII: {@code U+2028}, {@code U+2029} y la anulacion bidireccional {@code U+202E}
     * atravesaban los seis objetos de valor y quedaban guardados, camino de una guia de
     * envio y de un JSON.
     */
    private static final java.util.regex.Pattern INVISIBLES =
            java.util.regex.Pattern.compile("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]");

    private static final java.util.regex.Pattern ESPACIOS = java.util.regex.Pattern.compile("\\s+");

    /**
     * Normaliza los cuatro campos de texto libre antes de que nadie los mida.
     *
     * <p><strong>Es la segunda mitad del fallo del codigo postal, y se quedo abierta en la
     * primera correccion.</strong> El defecto no era del telefono ni del codigo postal: era
     * que {@code @Size} mide la cadena <strong>cruda</strong> y los objetos de valor colapsan
     * espacios y caracteres de control <strong>antes</strong> de medir. Con eso,
     * {@code "Cl  7"} son cinco caracteres, pasa {@code @Size(min = 5)}, llega al dominio
     * convertido en {@code "Cl 7"} —cuatro— y sale como 400 <strong>sin {@code errors}</strong>:
     * sin campo que marcar, que es lo contrario del criterio 7.
     *
     * <p>Va en el constructor compacto porque Jackson lo ejecuta al deserializar y Jakarta
     * Validation mira el objeto <strong>ya construido</strong>. Asi las dos validaciones miden
     * lo mismo, y la de mas adentro deja de poder rechazar lo que la de fuera acepto.
     */
    public ShippingAddressRequest {
        recipientName = normalizado(recipientName);
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

    /**
     * No imprime nada de lo que hay dentro.
     *
     * <p>Un {@code record} imprime todos sus campos por omision, y estos llevan donde vive
     * una persona, su telefono y el nombre de quien recibe. El criterio 19 no puede depender
     * de que nadie escriba nunca un {@code LOG.debug} con el objeto entero —{@code co.sendik}
     * esta en {@code DEBUG} en {@code dev} y en {@code local}—, asi que lo que se imprime es
     * lo que no identifica a nadie.
     *
     * <p><strong>Tampoco el municipio.</strong> No esta en la lista del criterio 19, pero
     * Spring registra el cuerpo deserializado en {@code DEBUG} y en que municipio vive
     * alguien es dato personal: lo destapo la prueba de registros al afirmar sobre el codigo
     * rechazado. Lo que queda impreso es lo que no identifica a nadie, o nada.
     */
    @Override
    public String toString() {
        return "ShippingAddressRequest[sin imprimir]";
    }
}
