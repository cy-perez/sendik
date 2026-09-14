package co.sendik.identity.persistence;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.PickupInstructions;
import co.sendik.identity.model.PostalCode;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduce los cuatro campos libres de la direccion de origen al documento que se cifra.
 * HU-017.
 *
 * <p>Es {@link ShippingAddressDetailsJson} sin quien recibe ni telefono, que aqui son del
 * perfil (RN-078). Las mismas decisiones: un solo documento porque los campos se leen y se
 * escriben siempre juntos, claves en orden alfabetico, y los opcionales se omiten en vez de
 * escribirse nulos.
 */
final class OriginAddressDetailsJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String LINEA = "linea";
    private static final String COMPLEMENTO = "complemento";
    private static final String INDICACIONES = "indicaciones";
    private static final String CODIGO_POSTAL = "codigoPostal";

    private OriginAddressDetailsJson() {}

    static String aJson(
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal) {
        ObjectNode raiz = JSON.createObjectNode();

        if (codigoPostal != null) {
            raiz.put(CODIGO_POSTAL, codigoPostal.value());
        }
        if (complemento != null) {
            raiz.put(COMPLEMENTO, complemento.value());
        }
        if (indicaciones != null) {
            raiz.put(INDICACIONES, indicaciones.value());
        }
        raiz.put(LINEA, linea.value());

        return raiz.toString();
    }

    static Detalle deJson(String texto) {
        JsonNode raiz = JSON.readTree(texto);

        return new Detalle(
                new AddressLine(raiz.get(LINEA).asString()),
                opcional(raiz, COMPLEMENTO, AddressComplement::new),
                opcional(raiz, INDICACIONES, PickupInstructions::new),
                opcional(raiz, CODIGO_POSTAL, PostalCode::new));
    }

    private static <T> @Nullable T opcional(JsonNode raiz, String clave, Function<String, T> constructor) {
        JsonNode valor = raiz.get(clave);
        return valor == null || valor.isNull() ? null : constructor.apply(valor.asString());
    }

    /** Lo que sale del documento, para que el repositorio lo arme en un origen. */
    record Detalle(
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal) {

        /** Es la direccion ya descifrada: no se imprime (criterio 17). */
        @Override
        public String toString() {
            return "Detalle[descifrado]";
        }
    }
}
