package co.sendik.catalog.dto;

import co.sendik.catalog.model.ListingId;
import java.util.List;
import java.util.Objects;

/**
 * Como quedo el carrito tras la fusion, y que no entro. HU-015, criterios 9 y 10.
 *
 * <p><strong>Lo que no entro se dice y no se descarta en silencio.</strong> Es el criterio
 * 10 y es la mitad menos evidente de la fusion: si la union supera el tope de RN-097 se
 * conserva hasta el tope, lo mas reciente primero, y quien acaba de entrar tiene derecho a
 * saber que algo se quedo fuera. Una fusion que adelgaza sin avisar es el mismo defecto que
 * RN-094 evita en la pantalla.
 *
 * <p><strong>Y la fusion no falla entera por una linea mala.</strong> Un identificador que
 * ya no existe, uno que dejo de estar publicado y uno que resulta ser de quien entra
 * —RN-092, que mientras el carrito era anonimo no tenia contra quien comprobarse— se
 * descartan y se anotan aqui. Rechazar la peticion completa obligaria a quien entra a
 * limpiar a mano un carrito que armo hace semanas.
 *
 * <p>Los tres motivos van juntos y sin distinguirse, por lo mismo que el criterio 22: la
 * respuesta dice cuantos y cuales no entraron, no por que. Distinguir «se vendio» de «es
 * tuyo» aqui seria decir sobre esos identificadores lo que RN-068 no deja decir en ninguna
 * otra parte.
 *
 * @param carrito como quedo, ya armado y con sus subtotales
 * @param noEntraron lo que se quedo fuera, por el tope o por no ser agregable
 */
public record MergeResult(CartView carrito, List<ListingId> noEntraron) {

    public MergeResult {
        Objects.requireNonNull(carrito, "El carrito es obligatorio");
        Objects.requireNonNull(noEntraron, "La lista de descartes es obligatoria");

        noEntraron = List.copyOf(noEntraron);
    }
}
