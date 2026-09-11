package co.sendik.identity.dto;

import co.sendik.identity.model.UserId;

/**
 * Guardar una direccion nueva. HU-016, criterios 2, 3 y 8.
 *
 * @param usuario sale del token, nunca de la peticion
 */
public record AddShippingAddressCommand(UserId usuario, ShippingAddressData datos) {

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
        return "AddShippingAddressCommand[usuario=" + usuario + "]";
    }
}
