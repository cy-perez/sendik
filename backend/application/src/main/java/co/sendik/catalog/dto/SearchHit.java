package co.sendik.catalog.dto;

import co.sendik.catalog.model.Listing;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Una publicacion que caso, con lo que puntuo. HU-014.
 *
 * <p>Existe por una razon muy concreta: <strong>el cursor de relevancia no se puede armar
 * sin la puntuacion</strong>, y la puntuacion no esta en la publicacion. La calcula el
 * motor sobre el texto buscado, cambia con cada busqueda y no pertenece al agregado, asi
 * que no puede vivir en {@link Listing}. Devolver solo publicaciones obligaria al caso de
 * uso a inventarse por donde sigue el tramo, y no hay nada que inventar: el ultimo
 * resultado sabe cuanto puntuo.
 *
 * <p>La puntuacion es <strong>nula en los otros tres ordenes</strong>, y no es un descuido:
 * calcularla cuando nadie ordena por ella seria trabajo que nadie mira, y devolverla en
 * cero seria un numero que se puede confundir con «no casa».
 *
 * <p>No sale a la API. La respuesta del catalogo no lleva puntuacion, y eso es el criterio
 * 19 escrito en el tipo: si no hay campo, no hay donde expresar que algo se adelanto.
 */
public record SearchHit(Listing publicacion, @Nullable Double relevancia) {

    public SearchHit {
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
    }

    /** Sin puntuacion, que es lo normal fuera del orden por relevancia. */
    public static SearchHit de(Listing publicacion) {
        return new SearchHit(publicacion, null);
    }

    /**
     * @throws IllegalStateException si el motor no la trajo. Solo la pide quien esta
     *     ordenando por relevancia, asi que faltar significa que el motor no cumplio su
     *     parte y no que el cliente pidiera algo raro
     */
    public double exigirRelevancia() {
        if (relevancia == null) {
            throw new IllegalStateException(
                    "El motor no trajo la relevancia de " + publicacion.id() + " y el orden la necesita");
        }
        return relevancia;
    }
}
