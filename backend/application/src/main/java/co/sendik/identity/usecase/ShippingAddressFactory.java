package co.sendik.identity.usecase;

import co.sendik.identity.dto.ShippingAddressData;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.DeliveryInstructions;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.RecipientName;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.UserId;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;

/**
 * Convierte los siete campos de texto del borde en una direccion de dominio. HU-016.
 *
 * <p>Aqui es donde el dominio vuelve a validar lo que el borde ya valido, que es lo que
 * backend/CLAUDE.md pide: las dos y no una. Cada objeto de valor rechaza lo suyo, y lo que
 * sale de aqui o es una direccion valida o es una excepcion.
 *
 * <p>Crear y editar comparten esto entero porque los campos son los mismos: lo unico que
 * cambia es si nace una direccion o se reescribe la que habia.
 */
final class ShippingAddressFactory {

    private ShippingAddressFactory() {}

    static ShippingAddress nueva(UserId duena, ShippingAddressData datos, Instant ahora) {
        return ShippingAddress.nueva(
                duena,
                new RecipientName(datos.quienRecibe()),
                new Phone(datos.telefono()),
                new MunicipalityCode(datos.municipio()),
                new AddressLine(datos.linea()),
                opcional(datos.complemento(), AddressComplement::new),
                opcional(datos.indicaciones(), DeliveryInstructions::new),
                opcional(datos.codigoPostal(), PostalCode::new),
                ahora);
    }

    static ShippingAddress editada(ShippingAddress actual, ShippingAddressData datos, Instant ahora) {
        return actual.con(
                new RecipientName(datos.quienRecibe()),
                new Phone(datos.telefono()),
                new MunicipalityCode(datos.municipio()),
                new AddressLine(datos.linea()),
                opcional(datos.complemento(), AddressComplement::new),
                opcional(datos.indicaciones(), DeliveryInstructions::new),
                opcional(datos.codigoPostal(), PostalCode::new),
                ahora);
    }

    /**
     * El municipio tiene que estar en la division vigente. RN-100, criterios 6 y 23.
     *
     * <p>Vive aqui y no en cada caso de uso porque es la tercera y ultima validacion del
     * mismo campo —el borde comprueba la forma, {@link MunicipalityCode} los cinco digitos
     * y esto que exista de verdad— y una regla compartida que se escribe dos veces es una
     * regla que acaba diciendo dos cosas.
     *
     * <p>Un municipio <strong>inactivo</strong> se rechaza igual que uno inexistente: el
     * DANE lo suprimio y lo que hay que hacer es lo mismo, elegir otro. Lo que si sigue
     * leyendose es la direccion que ya lo tenia guardado (criterio 23); esto solo gobierna
     * lo que se escribe.
     */
    static void exigirMunicipioVigente(GeographicDivision division, MunicipalityCode codigo) {
        Optional<Municipality> municipio = division.buscarMunicipio(codigo);
        if (municipio.isEmpty() || !municipio.get().activo()) {
            throw new UnknownMunicipalityException(codigo);
        }
    }

    /**
     * Vacio y ausente son lo mismo: la persona no quiere tener ese dato.
     *
     * <p>Se unifica aqui y no en cada objeto de valor a proposito. Un {@code AddressComplement}
     * vacio no existe —su constructor lo rechaza— porque una cadena vacia dentro de un objeto
     * de valor dice que hay algo cuando no lo hay; lo que decide que no haya nada es esto.
     * Sin ello, vaciar el complemento desde un formulario seria imposible: el campo llegaria
     * como cadena vacia y no como ausencia.
     */
    private static <T> @Nullable T opcional(@Nullable String valor, Function<String, T> constructor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        return constructor.apply(valor);
    }
}
