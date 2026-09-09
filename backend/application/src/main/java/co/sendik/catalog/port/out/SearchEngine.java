package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.SearchCriteria;
import co.sendik.catalog.dto.SearchHit;
import java.util.List;

/**
 * Con que se busca en el catalogo. El puerto que ADR-0008 definio y que ADR-0035 llena.
 *
 * <p><strong>El caso de uso no sabe que hay detras.</strong> Hoy es PostgreSQL, porque el
 * catalogo es pequeno y no hay nada que indexar —{@code prod} no se ha desplegado nunca—;
 * manana puede ser Typesense sin que cambien ni el dominio, ni el caso de uso, ni el
 * endpoint, ni la pantalla. Toda la decision de ADR-0035 cabe en la clase que implemente
 * esto, y esa es la razon de que el puerto exista antes que la segunda implementacion.
 *
 * <p><strong>Un metodo y no cinco.</strong> No hay {@code indexar}, ni {@code borrar}, ni
 * {@code reconstruir}: con PostgreSQL no hay indice que sincronizar porque se consulta la
 * misma base que ya es la fuente de verdad. El dia que llegue Typesense, esos metodos son
 * suyos y se agregan entonces; declararlos hoy seria pedirle a la implementacion actual que
 * escriba cinco cuerpos vacios y le mentiria a quien lea el puerto sobre lo que el sistema
 * hace.
 *
 * <p><strong>Devuelve publicaciones enteras y no identificadores.</strong> Con un motor
 * externo la respuesta seria una lista de identificadores que habria que ir a buscar a la
 * base; aqui la base es el motor, asi que pedir dos veces lo mismo seria una consulta de
 * regalo. Si la implementacion cambia y esa segunda consulta hace falta, la hace ella y el
 * caso de uso no se entera: es exactamente lo que el puerto protege.
 *
 * <p>RN-081 —solo lo {@code PUBLISHED}, tambien para el dueno— es cosa de quien implemente
 * esto, y es la unica regla que el puerto no puede hacer cumplir por su forma. Se prueba
 * contra la base de verdad, que es donde se puede.
 */
public interface SearchEngine {

    /**
     * Las publicaciones que casan, en el orden pedido.
     *
     * <p>Devuelve como maximo {@code criterios.limite()}, y quien llama pide una de mas para
     * saber si hay tramo siguiente sin contar el catalogo entero.
     */
    List<SearchHit> buscar(SearchCriteria criterios);
}
