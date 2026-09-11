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
 * <p><strong>El telefono y el codigo postal viajan ya normalizados: solo digitos.</strong> Se
 * corrigio el 11 de septiembre de 2026, despues de la revision de pruebas, y el defecto valia
 * la pena: el borde media el telefono en <strong>caracteres</strong> —de 7 a 30— y el dominio
 * en <strong>digitos</strong> —de 7 a 15—, asi que un numero de dieciseis digitos atravesaba
 * la validacion del borde y reventaba en {@code Phone} como {@code IllegalArgumentException},
 * que sale como 400 <strong>sin {@code errors}</strong>: sin campo que marcar, que es lo
 * contrario del criterio 7. Y el codigo postal era peor todavia: el dominio normaliza
 * «110 111» a «110111» y el borde lo rechazaba, asi que escribirlo como lo escribe la gente
 * daba un 400 con las tres suites en verde. Ahora los dos patrones son exactamente los del
 * dominio y quien normaliza es el cliente, que es de quien es el trabajo de hablar el
 * formato del contrato.
 *
 * <p><strong>Aqui se comprueba la forma y no el contenido.</strong> Que el municipio exista
 * de verdad lo decide el caso de uso contra la division sembrada; que la linea de direccion
 * sea una direccion no lo decide nadie, y es a proposito: una direccion colombiana lleva
 * almohadilla y guion y se escribe de quince maneras, asi que cualquier patron rechazaria
 * direcciones reales.
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
     * No imprime nada de lo que hay dentro.
     *
     * <p>Un {@code record} imprime todos sus campos por omision, y estos llevan donde vive
     * una persona, su telefono y el nombre de quien recibe. El criterio 19 no puede depender
     * de que nadie escriba nunca un {@code LOG.debug} con el objeto entero —{@code co.sendik}
     * esta en {@code DEBUG} en {@code dev} y en {@code local}—, asi que lo que se imprime es
     * lo que no identifica a nadie. Es la misma decision que {@code ShippingAddress} y
     * {@code EncryptedValue}, extendida tras la revision de seguridad.
     */
    @Override
    public String toString() {
        return "ShippingAddressRequest[municipalityCode=" + municipalityCode + "]";
    }
}
