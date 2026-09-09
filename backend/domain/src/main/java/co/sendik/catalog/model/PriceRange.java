package co.sendik.catalog.model;

import co.sendik.shared.money.Money;
import org.jspecify.annotations.Nullable;

/**
 * Entre cuanto y cuanto. HU-014, criterio 13.
 *
 * <p><strong>Los dos extremos entran.</strong> Quien pide de 50.000 a 100.000 espera ver lo
 * que vale exactamente 100.000; dejarlo fuera es la clase de detalle que nadie reporta y
 * todo el mundo nota.
 *
 * <p><strong>Los dos son opcionales y por separado</strong>, porque «desde 50.000» y «hasta
 * 100.000» son busquedas normales. Sin ninguno de los dos es {@link #SIN_LIMITE}, que no
 * filtra nada.
 *
 * <p><strong>Un minimo mayor que el maximo se rechaza, no se vacia.</strong> Un listado
 * vacio se leeria como «no hay nada de ese precio», que es mentira: lo que pasa es que no
 * existe ningun precio que cumpla las dos condiciones a la vez. Sale como 400.
 *
 * <p>Que el valor sea entero de pesos y no negativo no se comprueba aqui: lo garantiza
 * {@link Money}, que es donde vive RN-029. Un precio con decimales no llega a construirse.
 */
public record PriceRange(@Nullable Money minimo, @Nullable Money maximo) {

    /** Sin ninguno de los dos extremos: todo el catalogo. */
    public static final PriceRange SIN_LIMITE = new PriceRange(null, null);

    public PriceRange {
        if (minimo != null && maximo != null && minimo.esMayorQue(maximo)) {
            throw new IllegalArgumentException("El precio minimo " + minimo + " supera al maximo " + maximo);
        }
    }

    public static PriceRange desde(Money minimo) {
        return new PriceRange(minimo, null);
    }

    public static PriceRange hasta(Money maximo) {
        return new PriceRange(null, maximo);
    }

    /** Si no acota nada. Lo usa quien construye la consulta para no escribir la condicion. */
    public boolean sinLimite() {
        return minimo == null && maximo == null;
    }

    /** Con los dos extremos incluidos, que es el criterio 13. */
    public boolean contiene(Money precio) {
        return (minimo == null || !precio.esMenorQue(minimo)) && (maximo == null || !precio.esMayorQue(maximo));
    }
}
