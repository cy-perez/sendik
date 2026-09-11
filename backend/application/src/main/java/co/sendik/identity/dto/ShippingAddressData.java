package co.sendik.identity.dto;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.DeliveryInstructions;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.RecipientName;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.UserId;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Instant;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Los campos de una direccion de entrega, tal como llegan del borde. HU-016.
 *
 * <p>Existe para no repetir siete campos en el comando de crear y en el de editar, que son
 * los mismos: lo unico que los distingue es que el segundo dice cual.
 *
 * <p><strong>Texto plano y no objetos de valor del dominio</strong>, como
 * {@code UpdateProfileCommand}. Convertirlos es cosa de esta clase, y ahi es donde el
 * dominio vuelve a validar lo que el borde ya valido: las dos y no una (backend/CLAUDE.md).
 * Cada objeto de valor rechaza lo suyo, y de {@link #comoNueva} o sale una direccion valida
 * o sale una excepcion.
 *
 * <p>La conversion vive aqui y no en una clase aparte del paquete {@code usecase} porque
 * alli solo viven casos de uso, y {@code ArchitectureTest} lo comprueba. Un DTO que sabe
 * convertirse en lo que representa es el sitio que queda, y no es mal sitio: crear y editar
 * comparten esto entero porque los campos son los mismos.
 *
 * <p><strong>No hay campo de departamento</strong>, y esa ausencia es la regla. El codigo del
 * municipio lleva dentro el de su departamento (RN-100), asi que mandar los dos abriria la
 * posibilidad de que se contradijeran.
 *
 * @param municipio codigo DANE de cinco digitos
 * @param complemento nulo cuando no lo hay. La cadena vacia y la ausencia significan lo mismo
 * @param indicaciones lo mismo
 * @param codigoPostal lo mismo. Opcional hasta que se sepa si Skydropx lo exige
 */
public record ShippingAddressData(
        String quienRecibe,
        String telefono,
        String municipio,
        String linea,
        @Nullable String complemento,
        @Nullable String indicaciones,
        @Nullable String codigoPostal) {

    /** Una direccion nueva de esta persona, sin marca de predeterminada: eso lo decide la libreta. */
    public ShippingAddress comoNueva(UserId duena, Instant ahora) {
        return ShippingAddress.nueva(
                duena,
                new RecipientName(quienRecibe),
                new Phone(telefono),
                new MunicipalityCode(municipio),
                new AddressLine(linea),
                opcional(complemento, AddressComplement::new),
                opcional(indicaciones, DeliveryInstructions::new),
                opcional(codigoPostal, PostalCode::new),
                ahora);
    }

    /** Estos campos escritos sobre una direccion que ya existia. Criterio 9. */
    public ShippingAddress aplicadaA(ShippingAddress actual, Instant ahora) {
        return actual.con(
                new RecipientName(quienRecibe),
                new Phone(telefono),
                new MunicipalityCode(municipio),
                new AddressLine(linea),
                opcional(complemento, AddressComplement::new),
                opcional(indicaciones, DeliveryInstructions::new),
                opcional(codigoPostal, PostalCode::new),
                ahora);
    }

    /**
     * Vacio y ausente son lo mismo: la persona no quiere tener ese dato.
     *
     * <p>Se unifica aqui y no en cada objeto de valor a proposito. Un {@code AddressComplement}
     * vacio no existe —su constructor lo rechaza— porque una cadena vacia dentro de un objeto
     * de valor dice que hay algo cuando no lo hay; lo que decide que no haya nada es esto. Sin
     * ello, vaciar el complemento desde un formulario seria imposible: el campo llegaria como
     * cadena vacia y no como ausencia.
     */
    private static <T> @Nullable T opcional(@Nullable String valor, Function<String, T> constructor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return constructor.apply(valor);
    }
}
