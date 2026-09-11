package co.sendik.identity.dto;

import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;

/**
 * Cambiar los datos de una direccion. HU-016, criterio 9.
 *
 * <p>No lleva la marca de predeterminada: editar una direccion no cambia cual lo es. Para
 * eso esta {@link SetDefaultAddressCommand}, que es otra operacion y otro endpoint.
 *
 * @param usuario sale del token. Es lo que permite responder 404 sobre la direccion de otra
 *     persona en vez de 403 (criterio 15)
 */
public record EditShippingAddressCommand(UserId usuario, ShippingAddressId direccion, ShippingAddressData datos) {

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
        return "EditShippingAddressCommand[usuario=" + usuario + ", direccion=" + direccion + "]";
    }
}
