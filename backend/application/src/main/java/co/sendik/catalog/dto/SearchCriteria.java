package co.sendik.catalog.dto;

import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.PriceRange;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.Size;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * La pregunta que se le hace al motor de busqueda. HU-014.
 *
 * <p>Es {@link ListCatalogQuery} despues de que el caso de uso haga lo unico que el motor
 * no puede hacer: <strong>resolver el arbol</strong>. Ahi entra una categoria que puede ser
 * una familia, y aqui salen las hojas publicables que cuelgan de ella, porque no se publica
 * en una familia sino en una categoria suya. El motor no sabe que existe un arbol, y esa es
 * la razon de que sean dos tipos y no uno.
 *
 * <p><strong>Lo que no esta aqui es tan importante como lo que esta.</strong> No hay «quien
 * pregunta»: RN-081 dice que en la busqueda se ve solo lo {@code PUBLISHED}, tambien para
 * el dueno con la sesion abierta, y un parametro de identidad seria la puerta por la que un
 * dia se cuela una excepcion. Tampoco hay nada que pueda expresar posicion comprada
 * (RN-084): el orden es {@link CatalogSort} y no hay un quinto valor.
 *
 * <p><strong>Los filtros se exigen todos y dentro de cada uno basta con uno</strong>
 * (criterio 14): quien pide azul y talla M no recibe lo azul de otra talla, pero quien pide
 * azul o verde recibe los dos. Por eso los multiples son conjuntos y la talla no: una talla
 * es sistema mas valor (RN-087), y dos tallas de sistemas distintos no son comparables ni
 * se mezclan.
 *
 * @param texto lo que se escribio, o nulo si no se escribio nada
 * @param categorias las hojas donde buscar, ya resueltas. Vacio es todo el catalogo
 * @param condiciones cuales de las cuatro. Vacio es todas
 * @param talla la talla con su sistema, o nulo. RN-087: nunca el valor suelto
 * @param colores cuales de los quince. Vacio es todos
 * @param precio entre cuanto y cuanto, con los dos extremos incluidos
 * @param orden el orden ya resuelto, nunca el pedido a secas: ver {@link CatalogSort#efectivo}
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas traer. Quien llama pide una de mas para saber si hay siguiente
 */
public record SearchCriteria(
        @Nullable SearchText texto,
        List<CategoryId> categorias,
        Set<Condition> condiciones,
        @Nullable Size talla,
        Set<Color> colores,
        PriceRange precio,
        CatalogSort orden,
        @Nullable CatalogCursor desde,
        int limite) {

    public SearchCriteria {
        categorias = List.copyOf(Objects.requireNonNull(categorias, "Las categorias son obligatorias"));
        condiciones = Set.copyOf(Objects.requireNonNull(condiciones, "Las condiciones son obligatorias"));
        colores = Set.copyOf(Objects.requireNonNull(colores, "Los colores son obligatorios"));
        Objects.requireNonNull(precio, "El rango de precio es obligatorio: sin limite es PriceRange.SIN_LIMITE");
        Objects.requireNonNull(orden, "El orden es obligatorio");

        if (limite < 1) {
            throw new IllegalArgumentException("El limite tiene que ser al menos 1, y llego " + limite);
        }
        if (orden.necesitaTexto() && texto == null) {
            throw new IllegalArgumentException("No se puede ordenar por relevancia sin texto que puntuar");
        }
        if (desde != null && desde.orden() != orden) {
            throw new IllegalArgumentException(
                    "El cursor nacio con el orden " + desde.orden() + " y se esta pidiendo " + orden);
        }
    }
}
