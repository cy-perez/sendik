package co.sendik.catalog.dto;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.ListingId;
import java.util.List;
import java.util.Objects;

/**
 * Une al carrito de la cuenta lo que traia el navegador. HU-015, criterios 9, 10 y 12.
 *
 * <p><strong>Es union y no reemplazo</strong>, que es RN-095: no se pierde lo que traia del
 * navegador ni se borra lo que ya tenia guardado. De ahi que repetir la peticion no haga
 * dano —unir dos veces lo mismo da lo mismo— y que no haga falta ninguna cabecera de
 * idempotencia.
 *
 * <p><strong>Lo dispara una persona, no el sistema.</strong> El criterio 9 pide union al
 * entrar y el 12 pide que si entra otra persona en ese mismo navegador no herede nada;
 * siendo el carrito anonimo, el servidor no tiene con que distinguir «vuelve el mismo» de
 * «llega otro». La salida es preguntar una vez antes de fusionar, y este comando es lo que
 * se manda cuando alguien contesta que si (ADR-0037).
 *
 * <p>La lista llega tal como el navegador la guardo, asi que es dato del cliente y no se
 * cree: el caso de uso valida cada identificador contra la base, descarta lo que no existe o
 * no esta publicado, descarta lo propio —RN-092, que hasta este momento no habia contra
 * quien comprobar— y respeta el tope de RN-097.
 *
 * @param quien de quien es el carrito de destino. Del token, nunca del cuerpo
 * @param publicaciones lo que traia el navegador, en el orden en que lo agrego: lo mas
 *     reciente primero, que es lo que decide que se conserva cuando no cabe todo
 */
public record MergeCartCommand(BuyerId quien, List<ListingId> publicaciones) {

    public MergeCartCommand {
        Objects.requireNonNull(quien, "Quien fusiona es obligatorio");
        Objects.requireNonNull(publicaciones, "La lista es obligatoria");

        publicaciones = List.copyOf(publicaciones);
    }
}
