package co.sendik.catalog.usecase;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.dto.CatalogPage;
import co.sendik.catalog.dto.ListCatalogQuery;
import co.sendik.catalog.dto.SearchCriteria;
import co.sendik.catalog.dto.SearchHit;
import co.sendik.catalog.exception.UnknownCategoryException;
import co.sendik.catalog.model.CatalogSort;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.SearchEngine;
import co.sendik.shared.money.Money;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * El catalogo publico y la busqueda. HU-009, criterios 1, 2, 3, 5, 8, 9 y 10; HU-014,
 * criterios 7, 8, 9, 16, 17, 18, 21 y 22.
 *
 * <p><strong>Uno y no dos, porque buscar es listar con mas condiciones.</strong> El
 * criterio 8 de HU-014 dice que una busqueda sin texto y sin filtros ensena el catalogo tal
 * cual; partirlo en dos casos de uso habria hecho de esa frase una coincidencia que alguien
 * tendria que mantener, en vez de una consecuencia de que es el mismo codigo.
 *
 * <p><strong>La regla que este caso de uso hace cumplir es RN-068, y con HU-014 tambien
 * RN-081</strong>: se ve solo lo que esta {@code PUBLISHED}, por los dos caminos. No recibe
 * quien pregunta y no tiene como saberlo, y eso es deliberado: el catalogo ensena lo mismo a
 * todo el mundo, tambien al dueno de la publicacion, que ve lo suyo en su panel. Un
 * parametro «quien mira» aqui seria la puerta por la que un dia se cuela una excepcion.
 *
 * <p><strong>Una familia no es una hoja.</strong> No se publica en una familia sino en una
 * categoria suya, asi que abrir «Tecnologia» tiene que traer lo de sus siete categorias.
 * Resolverlo aqui y no en el motor mantiene al motor sin saber que existe un arbol, que es
 * lo que permite que manana sea otro (ADR-0035).
 *
 * <p>Una categoria retirada del arbol no devuelve un listado vacio: devuelve
 * {@link UnknownCategoryException}. Un vacio se leeria como «esta
 * categoria existe y no tiene nada», que es otra cosa y ademas mentira.
 *
 * <p><strong>Lo que este caso de uso no hace es buscar.</strong> No sabe si detras hay
 * PostgreSQL o Typesense, no construye ninguna consulta y no puntua nada. Lo unico que sabe
 * del orden es cual quedo aplicado, y lo necesita para una sola cosa: armar el cursor con la
 * clave correcta.
 */
public class ListCatalogUseCase {

    private final SearchEngine motor;
    private final Categories categorias;

    public ListCatalogUseCase(SearchEngine motor, Categories categorias) {
        this.motor = motor;
        this.categorias = categorias;
    }

    public CatalogPage execute(ListCatalogQuery consulta) {
        List<CategoryId> donde = resolver(consulta.categoria());
        CatalogSort orden = consulta.ordenEfectivo();

        // Se pide una mas de la que se va a entregar: es como se sabe si hay siguiente sin
        // contar el catalogo entero, y la sobrante nunca sale de aqui.
        List<SearchHit> traidas = motor.buscar(new SearchCriteria(
                consulta.texto(),
                donde,
                consulta.condiciones(),
                consulta.talla(),
                consulta.colores(),
                consulta.precio(),
                orden,
                consulta.desde(),
                consulta.limite() + 1));

        return armar(traidas, orden, consulta.limite());
    }

    /**
     * Las categorias donde de verdad hay publicaciones.
     *
     * <p>Vacio significa «todo el catalogo» y no «ninguna»: es lo que llega cuando nadie
     * eligio categoria. Y con texto escrito tampoco hereda nada, que es RN-083: la categoria
     * llega en nulo desde el borde porque la pantalla la solto al escribir.
     */
    private List<CategoryId> resolver(@Nullable CategoryId elegida) {
        if (elegida == null) {
            return List.of();
        }

        List<CategoryId> publicables = categorias.publicablesBajo(elegida);
        if (publicables.isEmpty()) {
            throw new UnknownCategoryException(elegida);
        }
        return publicables;
    }

    /**
     * Lo mismo para el escaparate del vendedor, que tiene un solo orden y es el del catalogo.
     *
     * <p>Alli no se busca ni se filtra: es el perfil publico de alguien, lo suyo y lo mas
     * reciente primero. Recibe publicaciones y no resultados porque no hay nada que puntuar,
     * y sella {@link CatalogSort#NEWEST} en el cursor para que ese cursor no valga en una
     * busqueda ordenada de otra manera.
     */
    static CatalogPage armar(List<Listing> traidas, int limite) {
        return armar(traidas.stream().map(SearchHit::de).toList(), CatalogSort.NEWEST, limite);
    }

    /** Parte el tramo en lo que se entrega y la senal de que hay mas. */
    static CatalogPage armar(List<SearchHit> traidas, CatalogSort orden, int limite) {
        List<Listing> publicaciones =
                traidas.stream().map(SearchHit::publicacion).toList();

        if (traidas.size() <= limite) {
            return CatalogPage.ultima(publicaciones);
        }

        List<Listing> entregadas = new ArrayList<>(publicaciones.subList(0, limite));

        return new CatalogPage(entregadas, cursorTras(traidas.get(limite - 1), orden), true);
    }

    /**
     * Por donde sigue, con la clave del orden que se aplico.
     *
     * <p>Sale del ultimo resultado <strong>entregado</strong> y no del que sobra: el cursor
     * dice hasta donde llego quien mira, y usar el sobrante se saltaria justamente esa fila.
     */
    private static CatalogCursor cursorTras(SearchHit ultima, CatalogSort orden) {
        Listing publicacion = ultima.publicacion();

        return switch (orden) {
            case NEWEST -> CatalogCursor.porFecha(publicadaEn(publicacion), publicacion.id());
            case PRICE_ASC, PRICE_DESC -> CatalogCursor.porPrecio(orden, precioDe(publicacion), publicacion.id());
            case RELEVANCE -> CatalogCursor.porRelevancia(ultima.exigirRelevancia(), publicacion.id());
        };
    }

    /**
     * Cuando se publico, que es por lo que ordena el catalogo.
     *
     * <p>No puede ser nulo aqui: el motor solo devuelve publicaciones publicadas y el dominio
     * sella la fecha al aprobar. Si lo fuera, seria una fila escrita a mano.
     */
    private static Instant publicadaEn(Listing publicacion) {
        Instant momento = publicacion.publishedAt();
        if (momento == null) {
            throw new IllegalStateException(
                    "Una publicacion del catalogo sin fecha de publicacion: " + publicacion.id());
        }
        return momento;
    }

    /**
     * Cuanto vale, que es por lo que ordenan los dos ordenes de precio.
     *
     * <p>Nulo por la misma razon que la fecha: el producto lo admite porque un borrador se
     * guarda a medias, y nada publicado puede estar en ese estado.
     */
    private static Money precioDe(Listing publicacion) {
        Money precio = publicacion.product().price();
        if (precio == null) {
            throw new IllegalStateException("Una publicacion del catalogo sin precio: " + publicacion.id());
        }
        return precio;
    }
}
