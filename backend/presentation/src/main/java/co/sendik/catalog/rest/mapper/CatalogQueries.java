package co.sendik.catalog.rest.mapper;

import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.PriceRange;
import co.sendik.catalog.model.SearchText;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.shared.money.Money;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.jspecify.annotations.Nullable;

/**
 * Los parametros de {@code GET /api/v1/listings}, convertidos en la consulta. HU-014.
 *
 * <p>Vive aqui y no en el controlador porque es traduccion y no delegacion: un controlador
 * que delega y traduce no deberia tener dentro cuarenta lineas de conversion.
 *
 * <p><strong>Todo lo que no se entiende es 400 y nada se ignora en silencio</strong>, que es
 * lo que {@code contrato-api.md} exige para los filtros: «un filtro mal escrito que no filtra
 * es peor que un error». Un color inventado, una talla que no existe en su sistema o un orden
 * fuera de la lista blanca se rechazan, no se descartan.
 *
 * <p>Las conversiones lanzan {@link IllegalArgumentException} y el manejador la traduce a 400
 * con {@code COMMON_VALIDATION_FAILED}. No se distingue cual parametro fallo, igual que hoy
 * con el cursor: decirlo ayudaria sobre todo a quien esta probando valores.
 */
public final class CatalogQueries {

    private CatalogQueries() {}

    /**
     * @throws IllegalArgumentException si algun valor no es de su lista cerrada, si la talla
     *     llega a medias o si el rango de precio esta del reves
     */
    public static ListCatalogQuery consulta(
            @Nullable String q,
            @Nullable String category,
            @Nullable List<String> condition,
            @Nullable String sizeSystem,
            @Nullable String size,
            @Nullable List<String> color,
            @Nullable Long minPrice,
            @Nullable Long maxPrice,
            @Nullable String sort,
            @Nullable String cursor,
            int limit) {

        return new ListCatalogQuery(
                SearchText.de(q),
                category == null || category.isBlank() ? null : CategoryId.de(category),
                valores(condition, Condition::valueOf),
                talla(sizeSystem, size),
                valores(color, Color::valueOf),
                precio(minPrice, maxPrice),
                CatalogSorts.orden(sort),
                CatalogCursors.cursor(cursor),
                limit);
    }

    /** Si la peticion pide buscar o filtrar algo, que es lo que la bandera de HU-014 tapa. */
    public static boolean pideBuscar(
            @Nullable String q,
            @Nullable List<String> condition,
            @Nullable String sizeSystem,
            @Nullable String size,
            @Nullable List<String> color,
            @Nullable Long minPrice,
            @Nullable Long maxPrice,
            @Nullable String sort) {

        return puesto(q)
                || !vacia(condition)
                || puesto(sizeSystem)
                || puesto(size)
                || !vacia(color)
                || minPrice != null
                || maxPrice != null
                || puesto(sort);
    }

    /**
     * Un filtro de varios valores, repetido en la direccion: {@code ?color=BLUE&color=GREEN}.
     *
     * <p>Conjunto y no lista porque repetir un valor no significa nada, y ordenado por
     * insercion para que dos peticiones iguales produzcan la misma consulta.
     */
    private static <T> Set<T> valores(@Nullable List<String> crudos, java.util.function.Function<String, T> aValor) {
        if (crudos == null || crudos.isEmpty()) {
            return Set.of();
        }

        Set<T> convertidos = new LinkedHashSet<>();
        for (String crudo : crudos) {
            if (!crudo.isBlank()) {
                convertidos.add(aValor.apply(crudo.trim()));
            }
        }
        return convertidos;
    }

    /**
     * La talla, que son dos parametros y un solo filtro. RN-087.
     *
     * <p>Los dos o ninguno: una talla sin sistema no se puede comparar con nada —«M» no
     * significa lo mismo en cada escala y «38» menos todavia— y un sistema sin talla no
     * acota. Que el valor exista dentro del sistema lo comprueba {@link Size}.
     */
    private static @Nullable Size talla(@Nullable String sistema, @Nullable String valor) {
        boolean haySistema = puesto(sistema);
        boolean hayValor = puesto(valor);

        if (!haySistema && !hayValor) {
            return null;
        }
        if (!haySistema || !hayValor) {
            throw new IllegalArgumentException("La talla se filtra con su sistema: hacen falta los dos");
        }

        return new Size(SizeSystem.valueOf(sistema.trim()), valor.trim());
    }

    /**
     * El rango, con los dos extremos incluidos y los dos opcionales.
     *
     * <p>El negativo y el decimal los rechaza {@link Money} —RN-029— y el minimo mayor que el
     * maximo lo rechaza {@link PriceRange}, que es donde esta escrito por que no puede ser un
     * vacio.
     */
    private static PriceRange precio(@Nullable Long minimo, @Nullable Long maximo) {
        if (minimo == null && maximo == null) {
            return PriceRange.SIN_LIMITE;
        }

        return new PriceRange(
                minimo == null ? null : Money.dePesos(minimo), maximo == null ? null : Money.dePesos(maximo));
    }

    private static boolean puesto(@Nullable String valor) {
        return valor != null && !valor.isBlank();
    }

    private static boolean vacia(@Nullable List<String> valores) {
        return valores == null || valores.stream().allMatch(String::isBlank);
    }
}
