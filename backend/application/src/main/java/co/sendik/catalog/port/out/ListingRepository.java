package co.sendik.catalog.port.out;

import co.sendik.catalog.dto.CatalogCursor;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.ListingStatus;
import co.sendik.catalog.model.SellerId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Donde viven las publicaciones. Un repositorio por agregado, no por tabla.
 *
 * <p>Guardar una publicacion guarda tambien su producto y sus imagenes: son el mismo
 * agregado aunque sean tres tablas. Quien implemente esto decide como, pero no puede
 * ofrecer guardar una imagen suelta, porque entonces alguien podria dejar el agregado
 * en un estado que el dominio nunca habria permitido.
 */
public interface ListingRepository {

    /** Inserta o actualiza. El bloqueo optimista lo resuelve el adaptador. */
    Listing guardar(Listing publicacion);

    Optional<Listing> buscar(ListingId id);

    /**
     * Varias publicaciones de una vez, en cualquier estado. HU-015.
     *
     * <p><strong>Existe por el carrito, que es la primera pantalla del proyecto que necesita
     * un punado de publicaciones sueltas y no un tramo de un listado.</strong> Con
     * {@link #buscar} una a una, leer un carrito lleno serian veinte viajes a la base para
     * pintar una sola pantalla.
     *
     * <p><strong>No filtra por estado, y quien llama decide.</strong> El carrito de quien
     * tiene sesion las quiere todas, porque RN-094 conserva a la vista lo que dejo de estar
     * disponible; la lectura anonima se queda solo con las visibles, porque a quien no ha
     * entrado no se le puede contar nada de lo que RN-068 protege. Un filtro fijo aqui
     * serviria a uno de los dos y obligaria al otro a un segundo metodo casi igual.
     *
     * <p>Lo que no existe simplemente no viene: la lista devuelta puede ser mas corta que la
     * pedida y no hay error en ello. Quien llama sabe que pidio.
     *
     * <p>El orden de la respuesta no esta definido. Quien necesite uno lo impone: el carrito
     * ordena por la fecha en que se agrego cada producto, que es dato suyo y no de aqui.
     */
    List<Listing> buscarVarias(List<ListingId> ids);

    /**
     * La publicacion, solo si es de ese vendedor.
     *
     * <p>Metodo propio y no un filtro en cada caso de uso: la comprobacion de dueno la
     * necesitan nueve de ellos, y escrita nueve veces tarde o temprano una se escribe
     * distinta. Esa es la que deja editar la publicacion de otro.
     *
     * <p>Devuelve vacio tanto si no existe como si no es suya. Un caso de uso que
     * distinguiera las dos cosas acabaria respondiendo 403 y confirmando que existe
     * (criterio 33).
     */
    Optional<Listing> buscarDelDueno(ListingId id, SellerId vendedor);

    /**
     * Lo del vendedor, lo mas reciente primero.
     *
     * <p>Por pagina y no por cursor: es un listado acotado y de uso administrativo, que
     * es la excepcion que contrato-api.md admite. El catalogo publico, cuando llegue,
     * si va por cursor.
     */
    List<Listing> buscarDelVendedor(SellerId vendedor, int pagina, int tamano);

    /**
     * Lo publicado de un vendedor, para cualquiera. RN-068.
     *
     * <p>Metodo aparte de {@link #buscarDelVendedor} a proposito: aquel es el panel del
     * dueno y trae los siete estados, este es el escaparate y trae uno. Escribirlos como
     * el mismo metodo con un booleano seria dejar la diferencia entre lo publico y lo
     * privado a merced de un parametro.
     */
    List<Listing> publicadasDelVendedor(SellerId vendedor, @Nullable CatalogCursor desde, int limite);

    /**
     * La cola del moderador: lo que espera revision, lo que lleva mas tiempo primero.
     *
     * <p>Ordena por {@code submittedAt} y no por {@code updatedAt}, y esa es toda la
     * razon de que la columna exista: una publicacion en revision puede cambiar de
     * precio, y con {@code updatedAt} tocarlo retrasaria su propio turno.
     *
     * <p>No recibe quien pregunta. Filtrar por moderador seria un error: la cola es una
     * sola y RN-063 —que nadie decida sobre lo suyo— se comprueba al decidir, no al
     * listar. Esconderle su propia publicacion le impediria ver que esta en la fila.
     *
     * <p><strong>Salto y no numero de pagina.</strong> Con {@code (pagina, tamano)} el
     * desplazamiento se deriva del mismo argumento que el limite, asi que quien pida una
     * fila de mas -para saber si hay pagina siguiente sin contar la tabla- mueve tambien
     * el arranque: {@code (1, 21)} salta 21 filas en vez de 20 y la fila 21 no sale en
     * ninguna pagina. Se pierde una por pagina, en silencio.
     *
     * <p>Aqui no llego a pasar porque nadie pide de mas todavia; le paso a la cola de
     * verificaciones en cuanto quiso su {@code hasMore}, y esta firma es la misma leccion
     * aplicada antes de pagarla. Ver {@code SellerVerificationRepository}.
     *
     * @param salto cuantas filas se saltan antes de empezar. Como {@code long}: el
     *     producto de pagina por tamano desborda en {@code int} mucho antes de que la
     *     tabla llegue ahi, y desbordar da un salto negativo, que PostgreSQL responde con
     *     un error y no con una pagina vacia
     * @param cuantas cuantas traer
     */
    List<Listing> pendientesDeRevision(long salto, int cuantas);

    /**
     * Si queda al menos una esperando revision a partir de esa posicion. La respuesta a
     * «¿hay pagina siguiente?».
     *
     * <p>Existe para no tener que traer a nadie para contestarlo. Pedir una fila de mas y
     * usar su presencia como señal funciona, pero {@link #pendientesDeRevision} devuelve
     * la publicacion entera y ademas resuelve su portada: seria una consulta de imagenes
     * mas por cada carga, para una fila que nadie va a ver.
     *
     * <p>Tampoco es contar: no dice cuantas quedan -que exigiria recorrerlas todas- sino
     * si queda alguna, y para eso basta con llegar hasta la primera.
     *
     * @param salto la posicion a partir de la cual se pregunta. Para saber si hay pagina
     *     siguiente es el final de la actual
     */
    boolean hayPendientesDesde(long salto);

    /**
     * Cuantas publicaciones tiene el vendedor en cada estado. HU-012.
     *
     * <p><strong>Cuenta el servidor y no la pantalla.</strong> Contarlas sobre la lista ya
     * cargada funcionaria hoy, porque {@link #buscarDelVendedor} trae todo lo que cabe en
     * una pagina, pero ata la cifra al tamano de esa pagina el dia que la lista crezca por
     * encima. Una cifra que dice «3 publicadas» porque solo miro veinte filas es peor que
     * no tenerla.
     *
     * <p>Devuelve <strong>solo los estados que tienen filas</strong>. Rellenar los siete de
     * RN-061 con cero es decision de la aplicacion y no de SQL: un {@code GROUP BY} no
     * inventa grupos vacios, y hacer que los invente -con una union sobre los siete
     * literales- meteria la enumeracion del dominio dentro de una consulta.
     */
    Map<ListingStatus, Long> contarPorEstadoDelVendedor(SellerId vendedor);
}
