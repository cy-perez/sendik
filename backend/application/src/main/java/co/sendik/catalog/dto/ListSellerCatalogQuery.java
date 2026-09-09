package co.sendik.catalog.dto;

import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.SellerId;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Un tramo de lo que vende alguien, visto desde fuera. HU-009.
 *
 * <p>Mismo tope y mismo cursor que {@link ListCatalogQuery}: es la misma lista filtrada de
 * otra manera, y dos topes distintos para dos listas iguales acaban divergiendo.
 *
 * <p><strong>Un solo orden, y hay que exigirlo.</strong> El escaparate ensena lo suyo y lo
 * mas reciente primero, y nada mas: no se busca ni se filtra en el perfil publico de
 * alguien. Desde HU-014 el cursor lleva dentro el orden con el que nacio, asi que uno de
 * precio -copiado de una busqueda y pegado aqui- llegaria a una consulta que ordena por
 * fecha y le pediria una fecha que ese cursor no lleva. Eso reventaba con un 500; el
 * criterio 22 dice 400, y es lo que sale de esta comprobacion.
 *
 * @param vendedor de quien es el escaparate
 * @param desde por donde seguir, o nulo para el primer tramo
 * @param limite cuantas se piden como maximo
 */
public record ListSellerCatalogQuery(
        SellerId vendedor, @Nullable CatalogCursor desde, int limite) {

    public ListSellerCatalogQuery {
        Objects.requireNonNull(vendedor, "El vendedor es obligatorio");

        if (limite < 1 || limite > ListCatalogQuery.LIMITE_MAXIMO) {
            throw new IllegalArgumentException(
                    "El limite va entre 1 y " + ListCatalogQuery.LIMITE_MAXIMO + ", y llego " + limite);
        }
        if (desde != null && desde.orden() != CatalogSort.NEWEST) {
            throw new IllegalArgumentException("El cursor nacio con el orden " + desde.orden()
                    + " y el escaparate solo ordena por " + CatalogSort.NEWEST);
        }
    }
}
