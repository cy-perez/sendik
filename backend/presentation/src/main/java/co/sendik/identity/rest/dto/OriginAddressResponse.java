package co.sendik.identity.rest.dto;

import co.sendik.identity.dto.OriginAddressView;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * La direccion de origen tal como sale de la API. HU-017.
 *
 * <p>Con el departamento por codigo y nombre aunque el cuerpo de escritura no lo pida, por
 * lo mismo que {@link ShippingAddressResponse}: al leer hace falta porque hay mas de un
 * «San Pedro». Y con el remitente —nombre y telefono del perfil— porque es lo que la
 * pantalla muestra al lado del origen (criterio 4).
 *
 * @param senderPhone nulo si se quito del perfil despues de guardar el origen
 * @param municipalityActive falso cuando el DANE suprimio ese municipio: se lee igual y al
 *     editar hay que elegir otro (criterio 13)
 */
public record OriginAddressResponse(
        String departmentCode,
        String departmentName,
        String municipalityCode,
        String municipalityName,
        boolean municipalityActive,
        String line,
        @Nullable String complement,
        @Nullable String instructions,
        @Nullable String postalCode,
        String senderName,
        @Nullable String senderPhone,
        Instant savedAt) {

    public static OriginAddressResponse de(OriginAddressView vista) {
        return new OriginAddressResponse(
                vista.departamentoCodigo(),
                vista.departamentoNombre(),
                vista.municipioCodigo(),
                vista.municipioNombre(),
                vista.municipioActivo(),
                vista.linea(),
                vista.complemento(),
                vista.indicaciones(),
                vista.codigoPostal(),
                vista.remitenteNombre(),
                vista.remitenteTelefono(),
                vista.guardadaEl());
    }

    /** No imprime nada de lo que hay dentro (criterio 17). */
    @Override
    public String toString() {
        return "OriginAddressResponse[sin imprimir]";
    }
}
