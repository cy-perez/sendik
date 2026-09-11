package co.sendik.identity.persistence;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.DeliveryInstructions;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.RecipientName;
import java.util.function.Function;
import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Traduce los seis campos libres de una direccion al documento que se cifra. HU-016.
 *
 * <p>Vive en infraestructura y no en el dominio porque es un detalle de como se guarda: el
 * dominio no sabe que hay una base de datos ni JSON de por medio, igual que en
 * {@code MeasurementsJson}.
 *
 * <p><strong>Los seis en un solo documento y no en seis columnas.</strong> Se leen y se
 * escriben siempre juntos, porque juntos son una direccion: seis pares
 * {@code _cipher}/{@code _key_version} serian doce columnas que nunca se consultan por
 * separado. Es distinto de V8, donde cada dato sensible se lee por su cuenta —el numero de
 * documento tiene ademas su huella y sus ultimos cuatro—.
 *
 * <p><strong>Las claves salen ordenadas</strong>, como en {@code MeasurementsJson}: el texto
 * es estable para la misma direccion. Aqui gana poco —el cifrado es distinto cada vez por
 * definicion— pero cuesta nada y hace legible un documento descifrado a mano el dia que haya
 * que mirar uno.
 *
 * <p>Los campos opcionales <strong>se omiten</strong> en vez de escribirse nulos. Un
 * documento sin la clave y un documento con la clave en nulo significan lo mismo y conviene
 * que solo haya una forma de decirlo.
 */
final class ShippingAddressDetailsJson {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String QUIEN_RECIBE = "quienRecibe";
    private static final String TELEFONO = "telefono";
    private static final String LINEA = "linea";
    private static final String COMPLEMENTO = "complemento";
    private static final String INDICACIONES = "indicaciones";
    private static final String CODIGO_POSTAL = "codigoPostal";

    private ShippingAddressDetailsJson() {}

    static String aJson(
            RecipientName quienRecibe,
            Phone telefono,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal) {
        ObjectNode raiz = JSON.createObjectNode();

        // En orden alfabetico, que es como los deja un TreeMap en MeasurementsJson.
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
        raiz.put(QUIEN_RECIBE, quienRecibe.value());
        raiz.put(TELEFONO, telefono.value());

        return raiz.toString();
    }

    static Detalle deJson(String texto) {
        JsonNode raiz = JSON.readTree(texto);

        return new Detalle(
                new RecipientName(raiz.get(QUIEN_RECIBE).asString()),
                new Phone(raiz.get(TELEFONO).asString()),
                new AddressLine(raiz.get(LINEA).asString()),
                opcional(raiz, COMPLEMENTO, AddressComplement::new),
                opcional(raiz, INDICACIONES, DeliveryInstructions::new),
                opcional(raiz, CODIGO_POSTAL, PostalCode::new));
    }

    private static <T> @Nullable T opcional(JsonNode raiz, String clave, Function<String, T> constructor) {
        JsonNode valor = raiz.get(clave);
        return valor == null || valor.isNull() ? null : constructor.apply(valor.asString());
    }

    /** Lo que sale del documento, para que el repositorio lo arme en una direccion. */
    record Detalle(
            RecipientName quienRecibe,
            Phone telefono,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal) {

        /**
         * No imprime nada, y aqui hace mas falta que en ningun otro sitio.
         *
         * <p>Esto es <strong>la direccion ya descifrada</strong>, viviendo a dos lineas del
         * criptograma, y los seis objetos de valor devuelven su contenido desde
         * {@code toString}. Un {@code record} sin esto los imprime todos.
         */
        @Override
        public String toString() {
            return "Detalle[descifrado]";
        }
    }
}
