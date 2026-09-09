package co.sendik.catalog.model;

import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Lo que alguien escribio en la caja de busqueda. HU-014, RN-082.
 *
 * <p><strong>Que se busca con esto no se decide aqui.</strong> RN-082 dice que el texto va
 * contra el titulo y la marca y no contra la descripcion, pero eso es una condicion de la
 * consulta y vive en el motor. Este objeto es solo el texto, ya limpio y ya acotado.
 *
 * <p><strong>No hay texto vacio.</strong> Un {@code SearchText} existe cuando hay algo que
 * buscar; cuando no lo hay, lo que corresponde es la ausencia del objeto —un nulo— y no un
 * objeto vacio que cada consulta tendria que acordarse de comprobar. Por eso la forma
 * normal de construirlo es {@link #de(String)}, que devuelve nulo donde el criterio 8 dice
 * que se ve el catalogo tal cual.
 *
 * <p>Lo que cuenta como «nada que buscar» incluye <strong>el texto de pura
 * puntuacion</strong>: quien escribe «???» no esta buscando nada, y devolverle el vacio de
 * RN-086 seria decirle que Sendik no tiene «???». Se le devuelve el catalogo.
 *
 * <p>El tope es el mismo de {@link Title}, que es lo mas largo que se puede estar
 * buscando, y <strong>por encima se rechaza en vez de recortar</strong>: recortar en
 * silencio devuelve resultados de una busqueda que nadie pidio.
 */
public record SearchText(String value) {

    /** Lo mismo que cabe en un titulo: mas que eso no es una busqueda. */
    public static final int LARGO_MAXIMO = 120;

    public SearchText {
        Objects.requireNonNull(value, "El texto es obligatorio: para no buscar nada, se deja sin poner");
        value = normalizar(value);

        if (!tieneAlgoQueBuscar(value)) {
            throw new IllegalArgumentException("El texto no busca nada: para eso se deja sin poner");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El texto supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    /**
     * El texto tal como llego del borde, o nulo si no hay nada que buscar.
     *
     * @throws IllegalArgumentException si pasa del tope. Sale como 400: es lo unico de aqui
     *     que el cliente tiene que corregir, y decirselo es mejor que recortar por su cuenta
     */
    public static @Nullable SearchText de(@Nullable String crudo) {
        if (crudo == null) {
            return null;
        }

        String limpio = normalizar(crudo);
        if (limpio.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("El texto supera los " + LARGO_MAXIMO + " caracteres");
        }

        return tieneAlgoQueBuscar(limpio) ? new SearchText(limpio) : null;
    }

    /** Misma limpieza que {@link Title}: sin control y sin espacios de sobra. */
    private static String normalizar(String texto) {
        return texto.replaceAll("\\p{Cntrl}", " ").replaceAll("\\s+", " ").trim();
    }

    /**
     * Si queda alguna letra o algun digito.
     *
     * <p>Por punto de codigo y no por caracter: «camisón» y los emoji que alguien pegue en
     * la caja se cuentan enteros y no por mitades.
     */
    private static boolean tieneAlgoQueBuscar(String texto) {
        return texto.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    @Override
    public String toString() {
        return value;
    }
}
