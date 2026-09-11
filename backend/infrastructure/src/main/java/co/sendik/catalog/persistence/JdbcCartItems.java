package co.sendik.catalog.persistence;

import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.CartItem;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.port.out.CartItems;
import co.sendik.shared.money.Money;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia del carrito. HU-015.
 *
 * <p><strong>Adaptador propio y no un metodo mas en {@link JdbcListingRepository}</strong>,
 * por lo mismo que {@link JdbcFavorites}: aquel es el repositorio de un agregado y el carrito
 * no forma parte de el. Que agregar al carrito no pueda tocar la publicacion es RN-089 dicho
 * en el codigo, y con un solo adaptador quedaria a un metodo de distancia.
 *
 * <p><strong>Aqui no se lee ninguna publicacion.</strong> Es la diferencia con
 * {@link JdbcFavorites}, que cruza con {@code listings} para poder filtrar por estado dentro
 * del {@code WHERE}. El carrito no filtra —RN-094 quiere lo no disponible a la vista— asi que
 * no hay nada que decidir en SQL: este adaptador devuelve las filas y las publicaciones las
 * pide el caso de uso en una sola consulta aparte. Dos consultas simples en vez de un
 * {@code JOIN} de treinta columnas que no serviria para filtrar nada.
 */
@Repository
public class JdbcCartItems implements CartItems {

    private final JdbcClient jdbc;

    public JdbcCartItems(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotente por la clave primaria y no por una lectura previa. Criterio 4.
     *
     * <p><strong>{@code ON CONFLICT DO NOTHING} y no {@code DO UPDATE}</strong>, y aqui
     * protege dos columnas y no una. Con {@code DO UPDATE}, un reintento de red movería el
     * producto a la cabeza de una lista que ordena por esa fecha —como en los favoritos— y
     * ademas pisaria {@code added_price}, que es lo unico que sostiene el aviso del criterio
     * 18: volver a pulsar sobre algo que subio de precio borraria justo el aviso de que
     * subio.
     *
     * <p>Un {@code SELECT} antes del {@code INSERT} no serviria: entre los dos cabe la
     * peticion de la otra pestana, y lo unico que decide entre dos escrituras simultaneas es
     * la restriccion de la tabla.
     */
    @Override
    public void guardar(CartItem item) {
        jdbc.sql("""
                        INSERT INTO cart_items (user_id, listing_id, created_at, added_price)
                        VALUES (:quien, :publicacion, :cuando, :precio)
                        ON CONFLICT (user_id, listing_id) DO NOTHING
                        """)
                .param("quien", item.quien().value())
                .param("publicacion", item.publicacion().value())
                .param("cuando", Timestamp.from(item.agregadoEn()))
                .param("precio", item.precioAlAgregar().amount())
                .update();
    }

    /** Idempotente: borrar cero filas es un resultado, no un error. */
    @Override
    public void quitar(BuyerId quien, ListingId publicacion) {
        jdbc.sql("DELETE FROM cart_items WHERE user_id = :quien AND listing_id = :publicacion")
                .param("quien", quien.value())
                .param("publicacion", publicacion.value())
                .update();
    }

    /** Criterio 1: un contador sobre la clave primaria, sin traerse el carrito entero. */
    @Override
    public boolean existe(BuyerId quien, ListingId publicacion) {
        return jdbc.sql("SELECT count(*) FROM cart_items WHERE user_id = :quien AND listing_id = :publicacion")
                        .param("quien", quien.value())
                        .param("publicacion", publicacion.value())
                        .query(Long.class)
                        .single()
                > 0;
    }

    /**
     * Todo lo que lleva, sin filtrar por estado y sin paginar.
     *
     * <p>El orden es el del gesto —lo ultimo agregado primero— y con desempate por
     * identificador. El desempate no es de adorno: {@code created_at} se repite —dos toques
     * seguidos, y siempre con un reloj fijo en pruebas— y sin el, dos lecturas del mismo
     * carrito podrian devolver las filas en distinto orden y la pantalla barajaria los grupos.
     *
     * <p>Va contra el indice de V19, que es esta consulta escrita como indice.
     */
    @Override
    public List<CartItem> todosDe(BuyerId quien) {
        return jdbc.sql("""
                        SELECT listing_id, created_at, added_price
                        FROM cart_items
                        WHERE user_id = :quien
                        ORDER BY created_at DESC, listing_id DESC
                        """)
                .param("quien", quien.value())
                .query((fila, numero) -> CartItem.reconstruir(
                        quien,
                        new ListingId(fila.getObject("listing_id", UUID.class)),
                        fila.getTimestamp("created_at").toInstant(),
                        new Money(fila.getBigDecimal("added_price"))))
                .list();
    }

    /** Para el tope de RN-097. Cuenta en la base en vez de traerse veinte filas. */
    @Override
    public int cuantosLleva(BuyerId quien) {
        return jdbc.sql("SELECT count(*) FROM cart_items WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Integer.class)
                .single();
    }

    @Override
    public void borrarTodosDe(BuyerId quien) {
        jdbc.sql("DELETE FROM cart_items WHERE user_id = :quien")
                .param("quien", quien.value())
                .update();
    }
}
