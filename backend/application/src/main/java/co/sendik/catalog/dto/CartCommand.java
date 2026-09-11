package co.sendik.catalog.dto;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.ListingId;
import java.util.Objects;

/**
 * Agregar o quitar un producto del carrito. HU-015, criterios 2, 3 y 4.
 *
 * <p>El mismo comando para las dos operaciones y para la consulta del estado, por lo mismo
 * que {@link FavoriteCommand}: la pregunta es la misma —quien, y sobre cual— y lo que cambia
 * es el verbo, que es el caso de uso.
 *
 * <p><strong>{@code quien} sale del token y jamas de la peticion.</strong> Es la regla de
 * backend/CLAUDE.md, y aqui protege de algo mas caro que en los favoritos: con un
 * identificador que viaje en el cuerpo, cualquiera llenaria el carrito de otra persona con
 * lo que quisiera que comprara.
 */
public record CartCommand(BuyerId quien, ListingId publicacion) {

    public CartCommand {
        Objects.requireNonNull(quien, "Quien agrega es obligatorio");
        Objects.requireNonNull(publicacion, "La publicacion es obligatoria");
    }
}
