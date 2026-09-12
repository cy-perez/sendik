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
 * <p>La conversion vive aqui y no en el caso de uso porque crear y editar la comparten
 * entera —los campos son los mismos— y porque es mapeo y no orquestacion: no habla con
 * ningun puerto.
 *
 * <p>La primera version citaba {@code SearchHit.de(Listing)} como precedente y no lo es:
 * aquello envuelve un agregado y rellena un nulo, no convierte texto en objetos de valor. El
 * precedente honesto es que no hay ninguno, y la razon para dejarlo aqui es la de arriba.
 *
 * <p><strong>El primer intento la puso aqui por un motivo equivocado</strong>, y conviene
 * que quede escrito para que nadie lo repita: se leyo que {@code ArchitectureTest} prohibia
 * clases auxiliares en {@code usecase} y se concluyo que alli no cabia nada mas. La regla
 * lleva {@code .areTopLevelClasses()}, asi que nunca prohibio un metodo estatico, y el
 * proyecto ya tenia la respuesta escrita una historia antes:
 * {@code ReadCartUseCase.conVendedores}. Lo que si tuvo que salir de aqui por ese motivo es
 * el cruce con la division politico-administrativa, que hablaba con un puerto.
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
     * rechazado. Lo que queda impreso es lo que no identifica a nadie, o nada. Es la misma decision que {@code ShippingAddress} y
     * {@code EncryptedValue}, extendida tras la revision de seguridad.
     */
    @Override
    public String toString() {
        return "ShippingAddressData[sin imprimir]";
    }
}
