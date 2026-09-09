package co.sendik.catalog.dto;

import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.PriceRange;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.Size;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo del catalogo publico. HU-009, criterios 1, 3 y 8; HU-014 entera.
 *
 * <p><strong>Buscar no es otra pregunta: es esta con mas condiciones.</strong> El criterio 8
 * lo dice al reves y con las mismas consecuencias: una consulta sin texto y sin filtros
 * devuelve el catalogo tal cual, lo publicado y lo mas reciente primero. Por eso HU-014 no
 * estrena un tipo paralelo sino que llena este: dos tipos habrian sido dos sitios donde
 * escribir el tope, el cursor y RN-068.
 *
 * <p>El tope vive aqui y no en el controlador, por lo mismo que el de la cola del moderador:
 * escrito en el borde protegeria esa ruta y ninguna otra que use el mismo caso de uso. Un
 * {@code limite} por encima del tope se rechaza y no se recorta en silencio, porque quien
 * pide 500 y recibe 24 sin que nadie se lo diga cree que ya tiene todo.
 *
 * <p><strong>El cursor se comprueba contra el orden aqui</strong> y no en el motor, que es
 * el criterio 22. Se compara contra {@link #ordenEfectivo()} y no contra lo que el cliente
 * escribio: pedir relevancia sin texto es pedir lo mas reciente, y rechazar el cursor de lo
 * mas reciente en esa peticion seria rechazar un cursor que la peticion anterior acababa de
 * entregar.
 *
 * @param texto lo que se escribio en la caja, o nulo. Va contra el titulo y la marca (RN-082)
 * @param categoria donde buscar, o nulo para todo el catalogo. Puede ser una familia: el
 *     caso de uso resuelve las categorias publicables que cuelgan de ella, porque no se
 *     publica en una familia sino en una categoria suya
 * @param condiciones cuales de las cuatro de RN-064. Vacio es todas
 * @param talla sistema y valor juntos, o nulo. RN-087: una M por letra y una 38 numerica no
 *     son comparables y no se mezclan
 * @param colores cuales de los quince. Vacio es todos
 * @param precio entre cuanto y cuanto, con los dos extremos incluidos
 * @param orden el que se pidio, o nulo para el de omision de RN-088
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas se piden como maximo
 */
public record ListCatalogQuery(
        @Nullable SearchText texto,
        @Nullable CategoryId categoria,
        Set<Condition> condiciones,
        @Nullable Size talla,
        Set<Color> colores,
        PriceRange precio,
        @Nullable CatalogSort orden,
        @Nullable CatalogCursor desde,
        int limite) {

    /** El mismo tope que el resto de los listados del contrato. */
    public static final int LIMITE_MAXIMO = 50;

    public static final int LIMITE_POR_OMISION = 24;

    public ListCatalogQuery {
        condiciones = Set.copyOf(Objects.requireNonNull(condiciones, "Las condiciones son obligatorias"));
        colores = Set.copyOf(Objects.requireNonNull(colores, "Los colores son obligatorios"));
        Objects.requireNonNull(precio, "El rango de precio es obligatorio: sin limite es PriceRange.SIN_LIMITE");

        if (limite < 1 || limite > LIMITE_MAXIMO) {
            throw new IllegalArgumentException("El limite va entre 1 y " + LIMITE_MAXIMO + ", y llego " + limite);
        }

        CatalogSort efectivo = CatalogSort.efectivo(orden, texto != null);
        if (desde != null && desde.orden() != efectivo) {
            throw new IllegalArgumentException(
                    "El cursor nacio con el orden " + desde.orden() + " y se esta pidiendo " + efectivo);
        }
    }

    /** El catalogo de siempre: sin texto, sin filtros y con el orden de HU-009. */
    public static ListCatalogQuery todo(@Nullable CategoryId categoria, @Nullable CatalogCursor desde, int limite) {
        return new ListCatalogQuery(
                null, categoria, Set.of(), null, Set.of(), PriceRange.SIN_LIMITE, null, desde, limite);
    }

    /**
     * El orden que de verdad se aplica, que es el que acaba sellado en el cursor.
     *
     * <p>Lo resuelve {@link CatalogSort#efectivo}: relevancia con texto, lo mas reciente sin
     * el, y relevancia pedida sin texto degradada a lo mas reciente.
     */
    public CatalogSort ordenEfectivo() {
        return CatalogSort.efectivo(orden, texto != null);
    }
}
