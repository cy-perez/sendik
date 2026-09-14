package co.sendik.identity.dto;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.PickupInstructions;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.UserId;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Instant;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Los campos de la direccion de origen, tal como llegan del borde. HU-017.
 *
 * <p>Es {@link ShippingAddressData} sin quien recibe ni telefono: el remitente es el
 * titular de la cuenta y sus datos ya estan en el perfil (RN-078). Texto plano y no
 * objetos de valor, por lo mismo que alli: convertirlos es donde el dominio vuelve a
 * validar lo que el borde ya valido, y de {@link #comoNueva} o sale un origen valido o
 * sale una excepcion.
 *
 * <p><strong>No hay campo de departamento</strong> (RN-100): el codigo del municipio lo
 * lleva dentro.
 *
 * @param municipio codigo DANE de cinco digitos
 * @param complemento nulo cuando no lo hay. La cadena vacia y la ausencia significan lo mismo
 * @param indicaciones lo mismo
 * @param codigoPostal lo mismo
 */
public record OriginAddressData(
        String municipio,
        String linea,
        @Nullable String complemento,
        @Nullable String indicaciones,
        @Nullable String codigoPostal) {

    /** Un origen nuevo para una cuenta que no tenia. */
    public OriginAddress comoNueva(UserId duena, Instant ahora) {
        return OriginAddress.nueva(
                duena,
                new MunicipalityCode(municipio),
                new AddressLine(linea),
                opcional(complemento, AddressComplement::new),
                opcional(indicaciones, PickupInstructions::new),
                opcional(codigoPostal, PostalCode::new),
                ahora);
    }

    /** Estos campos escritos sobre el origen que ya habia. Criterio 3. */
    public OriginAddress aplicadaA(OriginAddress actual, Instant ahora) {
        return actual.con(
                new MunicipalityCode(municipio),
                new AddressLine(linea),
                opcional(complemento, AddressComplement::new),
                opcional(indicaciones, PickupInstructions::new),
                opcional(codigoPostal, PostalCode::new),
                ahora);
    }

    /** Vacio y ausente son lo mismo: la persona no quiere tener ese dato. */
    private static <T> @Nullable T opcional(@Nullable String valor, Function<String, T> constructor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return constructor.apply(valor);
    }

    /** No imprime nada de lo que hay dentro, tampoco el municipio (criterio 17). */
    @Override
    public String toString() {
        return "OriginAddressData[sin imprimir]";
    }
}
