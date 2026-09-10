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
            @Nullable String minPrice,
            @Nullable String maxPrice,
            @Nullable String sort,
            @Nullable String cursor,
            int limit) {

        return new ListCatalogQuery(
                SearchText.de(q),
                category == null || category.isBlank() ? null : CategoryId.de(category),
                valores(condition, Condition.values()),
                talla(sizeSystem, size),
                valores(color, Color.values()),
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
            @Nullable String minPrice,
            @Nullable String maxPrice,
            @Nullable String sort) {

        return puesto(q)
                || !vacia(condition)
                || puesto(sizeSystem)
                || puesto(size)
                || !vacia(color)
                || puesto(minPrice)
                || puesto(maxPrice)
                || puesto(sort);
    }

    /**
     * Un filtro de varios valores, repetido en la direccion: {@code ?color=BLUE&color=GREEN}.
     *
     * <p>Conjunto y no lista porque repetir un valor no significa nada, y ordenado por
     * insercion para que dos peticiones iguales produzcan la misma consulta.
     */
    private static <T extends Enum<T>> Set<T> valores(@Nullable List<String> crudos, T[] admitidos) {
        if (crudos == null || crudos.isEmpty()) {
            return Set.of();
        }

        Set<T> convertidos = new LinkedHashSet<>();
        for (String crudo : crudos) {
            if (!crudo.isBlank()) {
                convertidos.add(deLaLista(crudo, admitidos));
            }
        }
        return convertidos;
    }

    /**
     * Un valor de una lista cerrada, <strong>sin devolver lo que llego</strong>.
     *
     * <p>Es la diferencia con {@code Enum.valueOf}, y no es cosmetica: aquel construye el
     * mensaje con el texto recibido —«No enum constant ... Color.loQueSea»— y ese mensaje
     * acaba en el registro del servidor, que es una ruta publica y sin cuenta. Quien mande
     * un color con saltos de linea dentro escribe lineas enteras en el registro, y en Cloud
     * Logging cada una se lee como una entrada aparte.
     *
     * <p>Es el mismo patron que ya usan {@code CategoryId.de} y {@link CatalogSorts}: se dice
     * que el valor no existe, no cual era.
     */
    private static <T extends Enum<T>> T deLaLista(String crudo, T[] admitidos) {
        String limpio = crudo.trim();

        for (T candidato : admitidos) {
            if (candidato.name().equals(limpio)) {
                return candidato;
            }
        }
        throw new IllegalArgumentException("El filtro no admite ese valor");
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

        SizeSystem escala = deLaLista(sistema, SizeSystem.values());

        try {
            return new Size(escala, valor.trim());
        } catch (IllegalArgumentException e) {
            // El dominio nombra la talla recibida en su mensaje, que le sirve a quien publica.
            // Aqui no puede salir: es entrada publica y el mensaje se registra.
            throw new IllegalArgumentException("Esa talla no existe en ese sistema");
        }
    }

    /**
     * El rango, con los dos extremos incluidos y los dos opcionales.
     *
     * <p>El negativo y el decimal los rechaza {@link Money} —RN-029— y el minimo mayor que el
     * maximo lo rechaza {@link PriceRange}, que es donde esta escrito por que no puede ser un
     * vacio.
     */
    private static PriceRange precio(@Nullable String minimo, @Nullable String maximo) {
        if (!puesto(minimo) && !puesto(maximo)) {
            return PriceRange.SIN_LIMITE;
        }

        try {
            return new PriceRange(pesos(minimo), pesos(maximo));
        } catch (IllegalArgumentException e) {
            // El dominio nombra los dos importes en su mensaje, que le sirve a quien publica.
            // Aqui no puede salir, por lo mismo que en la talla: es entrada publica y el
            // mensaje se registra.
            throw new IllegalArgumentException("El precio minimo supera al maximo");
        }
    }

    /**
     * Un entero de pesos, o nulo si no vino.
     *
     * <p>Se convierte aqui y no en la firma del controlador porque un {@code Long} en el
     * borde se convierte antes de que el metodo empiece, y con la bandera de busqueda
     * apagada eso responderia 400 en vez del 404 del criterio 26.
     *
     * <p>Lo que no es un entero se rechaza. El decimal tambien, y no por rigor: RN-029 dice
     * que el peso colombiano no usa decimales, asi que un precio con coma es un dato que
     * alguien tecleo mal.
     */
    private static @Nullable Money pesos(@Nullable String crudo) {
        if (!puesto(crudo)) {
            return null;
        }
        try {
            return Money.dePesos(Long.parseLong(crudo.trim()));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("El precio tiene que ser un entero de pesos", e);
        }
    }

    private static boolean puesto(@Nullable String valor) {
        return valor != null && !valor.isBlank();
    }

    private static boolean vacia(@Nullable List<String> valores) {
        return valores == null || valores.stream().allMatch(String::isBlank);
    }
}
