package co.sendik.identity.rest.dto;

import co.sendik.identity.dto.ShippingAddressView;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Una direccion de entrega tal como sale de la API. HU-016.
 *
 * <p><strong>Devuelve el departamento aunque el cuerpo de escritura no lo pida.</strong> No
 * es asimetria por descuido: al escribir sobra —se deriva del municipio, RN-100— y al leer
 * hace falta, porque hay mas de un «San Pedro» y mas de una «Santa Maria» en Colombia y un
 * municipio sin su departamento al lado no identifica un sitio (criterio 24).
 *
 * @param municipalityActive falso cuando el DANE suprimio ese municipio. La direccion se
 *     sigue leyendo igual (criterio 23); lo que cambia es que al editarla hay que elegir otro,
 *     y este campo es lo que permite a la pantalla decirlo antes de que el servidor lo rechace
 */
public record ShippingAddressResponse(
        String id,
        String recipientName,
        String phone,
        String departmentCode,
        String departmentName,
        String municipalityCode,
        String municipalityName,
        boolean municipalityActive,
        String line,
        @Nullable String complement,
        @Nullable String instructions,
        @Nullable String postalCode,
        boolean isDefault,
        Instant savedAt) {

    public static ShippingAddressResponse de(ShippingAddressView vista) {
        return new ShippingAddressResponse(
                vista.id(),
                vista.quienRecibe(),
                vista.telefono(),
                vista.departamentoCodigo(),
                vista.departamentoNombre(),
                vista.municipioCodigo(),
                vista.municipioNombre(),
                vista.municipioActivo(),
                vista.linea(),
                vista.complemento(),
                vista.indicaciones(),
                vista.codigoPostal(),
                vista.predeterminada(),
                vista.guardadaEl());
    }
}
