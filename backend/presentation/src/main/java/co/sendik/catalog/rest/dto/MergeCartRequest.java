package co.sendik.catalog.rest.dto;

import co.sendik.catalog.model.Cart;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Lo que traia el navegador, para unirlo al carrito de la cuenta. HU-015, criterios 9 y 10.
 *
 * <p><strong>El tope se declara aqui ademas de comprobarse en el caso de uso</strong>, y es
 * redundante a proposito, como el {@code limit} del catalogo: el del caso de uso protege a
 * cualquiera que lo use, y el de aqui hace que el 400 salga antes de tocar la base y con el
 * nombre del campo que el cliente escribio. Es ademas la unica defensa contra un cuerpo
 * arbitrario mandado por quien no ha entrado —que es la razon de que RN-097 exista—, porque
 * rechazarlo despues de deserializar mil identificadores ya es haberlos leido.
 *
 * <p>La lista llega en el orden del navegador, lo mas reciente primero, y ese orden decide
 * que se conserva cuando no cabe todo.
 *
 * @param listingIds los identificadores guardados en el navegador
 */
public record MergeCartRequest(
        @NotNull @NotEmpty @Size(max = Cart.MAXIMO_DE_PRODUCTOS)
        List<@NotNull String> listingIds) {}
