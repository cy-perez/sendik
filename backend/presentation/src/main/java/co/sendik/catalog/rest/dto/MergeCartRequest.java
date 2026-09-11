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
 * nombre del campo que el cliente escribio.
 *
 * <p><strong>Lo que no hace es acotar el cuerpo que llega.</strong> Aqui decia que si, y era
 * falso: {@code @Valid} corre despues de que Jackson materialice la lista entera, asi que un
 * cuerpo con mil identificadores se deserializa y luego se rechaza. Lo que de verdad acota
 * esta ruta es que exige token y que {@code /api/v1/users/**} esta bajo el limitador por
 * sujeto; un cuerpo JSON sin tope es una carencia general de la API, no de esta anotacion.
 *
 * <p>La lista llega en el orden del navegador, lo mas reciente primero, y ese orden decide
 * que se conserva cuando no cabe todo.
 *
 * @param listingIds los identificadores guardados en el navegador
 */
public record MergeCartRequest(
        @NotNull @NotEmpty @Size(max = Cart.MAXIMO_DE_PRODUCTOS)
        List<@NotNull String> listingIds) {}
