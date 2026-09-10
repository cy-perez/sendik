package co.sendik.catalog.rest;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.rest.dto.CatalogPageResponse;
import co.sendik.catalog.rest.mapper.CatalogCursors;
import co.sendik.catalog.rest.mapper.CatalogPages;
import co.sendik.catalog.rest.mapper.CatalogQueries;
import co.sendik.catalog.usecase.ListCatalogUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpMethod;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * El catalogo publico y la busqueda. HU-009 y HU-014.
 *
 * <p><strong>Publico y sin token, que es toda la historia.</strong> Es la primera ruta del
 * proyecto que sirve a alguien que no tiene cuenta, y por eso el identificador de quien
 * pregunta no entra: RN-068 y RN-081 dicen que se ve lo mismo con sesion y sin ella, tambien
 * para el dueno de la publicacion, que ve lo suyo en su panel.
 *
 * <p><strong>Buscar es listar este mismo recurso con mas condiciones</strong>, y por eso no
 * nace una ruta {@code /search}: una ruta aparte obligaria a duplicar la paginacion, la
 * bandera y la forma de la respuesta, y dejaria dos sitios donde decidir que se ensena. Sin
 * texto y sin filtros, lo que se devuelve es el catalogo tal cual (criterio 8).
 *
 * <p><strong>Cuelga de {@code /api/v1/listings} porque el contrato ya se lo reservo.</strong>
 * contrato-api.md ilustra la paginacion por cursor con esta ruta exacta y explica por que la
 * cola del moderador tuvo que irse a {@code /moderation}: son dos listas del mismo recurso
 * con paginacion distinta, y juntarlas dejaria la autorizacion colgando de un parametro de
 * consulta.
 *
 * <p>La lectura de una publicacion por identificador **no esta aqui**: la sirve
 * {@code ListingsController}, que ya responde la forma publica a quien no es dueno ni
 * moderador desde HU-007.
 *
 * <p><strong>Dos banderas y no una.</strong> Con {@code FEATURE_CATALOG} apagada el
 * controlador no se crea y la ruta responde 404: no rechaza, no esta. Con
 * {@code FEATURE_SEARCH} apagada la ruta sigue, pero cualquier parametro de busqueda responde
 * el mismo 404 (criterio 26): ver {@link SearchFeature}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/listings")
@ConditionalOnProperty(prefix = "sendik.features", name = "catalog", havingValue = "true")
public class CatalogController {

    private static final String RUTA = "/api/v1/listings";

    private final ListCatalogUseCase casoDeListar;
    private final PublicFileStore almacen;
    private final Optional<SearchFeature> busqueda;

    public CatalogController(
            ListCatalogUseCase casoDeListar, PublicFileStore almacen, Optional<SearchFeature> busqueda) {
        this.casoDeListar = casoDeListar;
        this.almacen = almacen;
        this.busqueda = busqueda;
    }

    /**
     * Un tramo del catalogo, o de lo que casa con lo que se pidio.
     *
     * <p>{@code category} admite tanto una hoja como una familia: el caso de uso resuelve las
     * categorias publicables que cuelgan de ella, porque no se publica en una familia sino en
     * una categoria suya. Una categoria retirada del arbol se rechaza y no sale como listado
     * vacio, que se leeria como «existe y no tiene nada».
     *
     * <p>{@code condition} y {@code color} se repiten para pedir varios valores
     * —{@code ?color=BLUE&color=GREEN}—, y dentro de un mismo filtro basta con que case uno.
     * Entre filtros distintos se exigen todos (criterio 14).
     *
     * <p>{@code sort} admite {@code relevance}, {@code publishedAt,desc}, {@code price,asc} y
     * {@code price,desc}, y nada mas (RN-088). Sin el, el orden es relevancia cuando hay texto
     * y lo mas reciente cuando no lo hay.
     *
     * <p>El tope del limit lo pone {@link ListCatalogQuery} y ademas se declara aqui. Es
     * redundante a proposito, igual que en la bandeja del moderador: el del caso de uso
     * protege a cualquiera que lo use, y el de aqui hace que el 400 salga antes de tocar la
     * base y con el nombre del parametro que el cliente escribio.
     *
     * <p><strong>Los parametros de busqueda entran todos como texto</strong>, y el tope de
     * {@code q} no se declara aqui con {@code @Size}. Es deliberado y cuesta explicarlo:
     * la validacion de argumentos y la conversion de tipos ocurren <em>antes</em> de que
     * este metodo empiece, asi que un {@code @Size} sobre {@code q} o un {@code Long} en
     * los precios responderian 400 con la bandera apagada, y ese 400 diria que el
     * parametro se entiende —justo lo que el criterio 26 no quiere—. Convertidos aqui
     * dentro, los rechaza {@link SearchText} y {@link co.sendik.catalog.model.PriceRange},
     * que salen igualmente como 400 cuando la busqueda existe.
     *
     * <p>Por encima del tope se responde 400 y no se recorta: recortar en silencio devuelve
     * resultados de una busqueda que nadie pidio.
     */
    @GetMapping
    public CatalogPageResponse catalogo(
            @RequestParam(name = "limit", defaultValue = "24") @Min(1) @Max(ListCatalogQuery.LIMITE_MAXIMO) int limit,
            @RequestParam(name = "cursor", required = false) @Nullable String cursor,
            @RequestParam(name = "category", required = false) @Nullable String category,
            @RequestParam(name = "q", required = false) @Nullable String q,
            @RequestParam(name = "condition", required = false) @Nullable List<String> condition,
            @RequestParam(name = "sizeSystem", required = false) @Nullable String sizeSystem,
            @RequestParam(name = "size", required = false) @Nullable String size,
            @RequestParam(name = "color", required = false) @Nullable List<String> color,
            @RequestParam(name = "minPrice", required = false) @Nullable String minPrice,
            @RequestParam(name = "maxPrice", required = false) @Nullable String maxPrice,
            @RequestParam(name = "sort", required = false) @Nullable String sort)
            throws NoResourceFoundException {

        exigirBusquedaEncendida(q, condition, sizeSystem, size, color, minPrice, maxPrice, sort, cursor);

        ListCatalogQuery consulta = CatalogQueries.consulta(
                q, category, condition, sizeSystem, size, color, minPrice, maxPrice, sort, cursor, limit);

        CatalogPage tramo = casoDeListar.execute(consulta);

        return CatalogPages.de(tramo, almacen);
    }

    /**
     * Criterio 26: con la bandera apagada, buscar no existe.
     *
     * <p>Se comprueba <strong>antes</strong> de convertir nada, y eso importa: si se
     * convirtiera primero, un color inventado con la bandera apagada saldria como 400, y ese
     * 400 diria que el parametro se entiende. Lo que no existe responde 404 y nada mas.
     */
    private void exigirBusquedaEncendida(
            @Nullable String q,
            @Nullable List<String> condition,
            @Nullable String sizeSystem,
            @Nullable String size,
            @Nullable List<String> color,
            @Nullable String minPrice,
            @Nullable String maxPrice,
            @Nullable String sort,
            @Nullable String cursor)
            throws NoResourceFoundException {

        if (busqueda.isPresent()) {
            return;
        }

        if (CatalogQueries.pideBuscar(q, condition, sizeSystem, size, color, minPrice, maxPrice, sort)) {
            // La misma excepcion que Spring lanza cuando una ruta no existe, para que la
            // respuesta sea la misma hasta en el codigo de error. Su mensaje habla de
            // recursos estaticos y no sale nunca: se registra en debug y el cliente recibe
            // COMMON_NOT_FOUND, igual que con las otras tres banderas.
            throw new NoResourceFoundException(HttpMethod.GET, RUTA, RUTA);
        }

        // Y el cursor, que es el unico parametro que puede pedir buscar sin decirlo: uno
        // nacido bajo un orden de HU-014 sigue valiendo despues de apagar la bandera, y sin
        // esto lo rechazaria ListCatalogQuery con un 400 que dice «ese orden lo entiendo».
        // Decodificarlo aqui no reabre lo que el metodo evita: un cursor ilegible ya
        // responde 400 con la bandera encendida y con ella apagada desde HU-009, asi que ese
        // 400 no distingue un entorno del otro.
        CatalogCursor desde = CatalogCursors.cursor(cursor);
        if (desde != null && desde.orden() != CatalogSort.NEWEST) {
            throw new NoResourceFoundException(HttpMethod.GET, RUTA, RUTA);
        }
    }
}
