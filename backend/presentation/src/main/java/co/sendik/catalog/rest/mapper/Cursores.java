package co.sendik.catalog.rest.mapper;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Un puñado de valores convertido en cadena opaca, y vuelta.
 *
 * <p>Es la mecanica que comparten los dos listados por cursor del catalogo: el publico
 * ordena por lo que se le pida —cuatro ordenes desde HU-014— y la lista de favoritos por
 * cuando se guardo, pero los dos viajan igual. Se extrajo aqui al llegar HU-011, cuando la
 * alternativa era copiar el base64, el JSON y el manejo del cursor corrupto en un segundo
 * archivo, donde tarde o temprano uno de los dos deja de rechazar lo que no entiende.
 *
 * <p><strong>Lo que no se comparte es el tipo del cursor</strong>, y eso es deliberado:
 * {@code CatalogCursor} y {@code FavoriteCursor} siguen siendo records distintos, asi que el
 * compilador impide que el cursor de un listado se use en el otro. Lo comun es como se
 * transporta; lo que significa, no.
 *
 * <p><strong>Vive en el borde y no en {@code application}.</strong> El cursor es la pregunta
 * —desde donde seguir—; que viaje en base64 dentro de una URL es una decision de transporte,
 * y ponerla en el caso de uso ataria la aplicacion a HTTP.
 *
 * <p><strong>Opaco a proposito.</strong> El cliente no lo lee ni lo construye: lo recibe y lo
 * devuelve. Asi, cuando el orden de un listado cambia —y HU-014 lo cambio: el cursor pasó de
 * llevar una fecha a llevar el orden y la clave de ese orden— el contenido cambia sin romper
 * a nadie ni versionar la ruta. Es tambien la razon de que se codifique en lugar de mandar
 * dos parametros sueltos: dos parametros legibles invitan a que alguien los fabrique a mano
 * y quedan en el contrato para siempre.
 *
 * <p><strong>Los campos no estan fijados aqui</strong>, porque cada listado lleva los suyos:
 * el catalogo ordenado por precio lleva un precio donde el ordenado por fecha lleva una
 * fecha. Quien sabe que significa cada campo es el mapeador de su listado, y es tambien quien
 * tiene que rechazar el cursor al que le falte alguno.
 *
 * <p>En base64 <strong>de URL</strong> y sin relleno: viaja en una cadena de consulta, y el
 * {@code +} del alfabeto normal se interpreta como espacio.
 */
final class Cursores {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();

    /** El instante del ultimo elemento entregado. */
    private static final String CAMPO_INSTANTE = "p";

    /** Cual era, para desempatar. Lo llevan los dos listados. */
    static final String CAMPO_ID = "i";

    private Cursores() {}

    /** El par tal como sale del transporte, todavia sin tipo de dominio. */
    record Par(Instant instante, UUID id) {}

    /** El cursor de instante mas identificador, que es el de la lista de favoritos. */
    static String texto(Instant instante, UUID id) {
        return texto(Map.of(CAMPO_INSTANTE, instante.toString(), CAMPO_ID, id.toString()));
    }

    /**
     * @throws IllegalArgumentException si no es un cursor de este proyecto. Sale como 400,
     *     que es lo que el manejador ya hace con esta excepcion. Un cursor corrupto que se
     *     ignorara en silencio devolveria la primera pagina, y quien esta recorriendo una
     *     lista volveria al principio sin enterarse
     */
    static Par par(String texto) {
        JsonNode nodo = nodo(texto);

        return leer(() -> new Par(Instant.parse(exigir(nodo, CAMPO_INSTANTE)), uuid(nodo, CAMPO_ID)));
    }

    /** Cualquier conjunto de campos, para el cursor del catalogo, que lleva unos u otros. */
    static String texto(Map<String, String> campos) {
        ObjectNode nodo = JSON.createObjectNode();
        campos.forEach(nodo::put);

        return ENCODER.encodeToString(nodo.toString().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * El contenido del cursor, sin mirar que campos trae.
     *
     * <p>Que campos hacen falta lo sabe el mapeador de cada listado, que es quien los puso.
     *
     * @throws IllegalArgumentException si no se puede leer
     */
    static JsonNode nodo(String texto) {
        return leer(() -> JSON.readTree(new String(DECODER.decode(texto), StandardCharsets.UTF_8)));
    }

    /**
     * @throws IllegalArgumentException si el campo no esta. Un cursor al que le falta la
     *     clave de su orden no es un cursor a medias: es uno de otro listado o uno inventado
     */
    static String exigir(JsonNode nodo, String campo) {
        JsonNode valor = nodo.get(campo);
        if (valor == null || valor.isNull()) {
            throw new IllegalArgumentException("El cursor no es valido");
        }
        return valor.asString();
    }

    static UUID uuid(JsonNode nodo, String campo) {
        return UUID.fromString(exigir(nodo, campo));
    }

    /**
     * Convierte en 400 cualquier forma de «esto no se entiende».
     *
     * <p>Existe porque las maneras de fallar son variadas —base64 invertido, JSON roto,
     * fecha imposible, identificador que no es un UUID— y varias de ellas no son
     * {@link IllegalArgumentException} por su cuenta. Sin esto, un cursor con una fecha
     * inventada salia como 500.
     *
     * <p>Se traga el motivo a proposito: decirle a quien manda un cursor inventado que le
     * falta el campo «p» es ensenarle a fabricar uno valido.
     */
    static <T> T leer(java.util.function.Supplier<T> lectura) {
        try {
            return lectura.get();
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("El cursor no es valido", e);
        }
    }
}
