package co.sendik.identity.dto;

import org.jspecify.annotations.Nullable;

/**
 * Los campos de una direccion de entrega, tal como llegan del borde. HU-016.
 *
 * <p>Existe para no repetir siete campos en el comando de crear y en el de editar, que son
 * los mismos: lo unico que los distingue es que el segundo dice cual.
 *
 * <p><strong>Texto plano y no objetos de valor del dominio</strong>, como
 * {@code UpdateProfileCommand}. Quien los construye —y por tanto quien los rechaza por
 * formato— es el caso de uso: el borde valida con Jakarta Validation y el dominio vuelve a
 * validar, las dos y no una (backend/CLAUDE.md).
 *
 * <p><strong>No hay campo de departamento</strong>, y esa ausencia es la regla. El codigo
 * del municipio lleva dentro el de su departamento (RN-100), asi que mandar los dos abriria
 * la posibilidad de que se contradijeran.
 *
 * @param municipio codigo DANE de cinco digitos
 * @param complemento nulo cuando no lo hay. La cadena vacia y la ausencia significan lo
 *     mismo y el borde ya las ha unificado
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
        @Nullable String codigoPostal) {}
