package co.sendik.identity.persistence;

import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.shared.crypto.EncryptedValue;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.SensitiveDataCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de la libreta de direcciones. HU-016.
 *
 * <p><strong>Los seis campos libres van cifrados en una sola columna.</strong> Fuera del
 * cifrado quedan el municipio, la marca de predeterminada y las fechas, que es lo unico por
 * lo que esta tabla se consulta. El detalle de por que uno y no seis esta en
 * {@link ShippingAddressDetailsJson} y en el encabezado de V21.
 *
 * <p><strong>No cruza con {@code municipalities}.</strong> Este adaptador devuelve el codigo
 * y quien pone los nombres es la capa de aplicacion, con una consulta aparte a la division.
 * Es la misma decision que {@code JdbcCartItems}, que devuelve las filas del carrito y deja
 * las publicaciones para otra consulta: un {@code JOIN} aqui traeria dos columnas de texto
 * repetidas en cada fila y no serviria para filtrar nada.
 */
@Repository
public class JdbcShippingAddressRepository implements ShippingAddressRepository {

    private final JdbcClient jdbc;
    private final SensitiveDataCipher cifrado;

    public JdbcShippingAddressRepository(JdbcClient jdbc, SensitiveDataCipher cifrado) {
        this.jdbc = jdbc;
        this.cifrado = cifrado;
    }

    /**
     * De la mas reciente a la mas antigua, con desempate por identificador.
     *
     * <p>Ese orden es parte del contrato del puerto y no una casualidad: de el depende que
     * {@code AddressBook.relevoAlQuitar} devuelva la mas reciente de las que quedan
     * (criterio 11). El desempate no es de adorno —{@code created_at} se repite, sobre todo
     * con el reloj fijo de las pruebas— y sin el dos lecturas de la misma libreta podrian
     * barajar las tarjetas.
     *
     * <p>Sin paginar: con el tope de RN-101 no hace falta.
     */
    @Override
    public List<ShippingAddress> deCuenta(UserId cuenta) {
        return jdbc.sql("""
                        SELECT id, user_id, municipality_code, details_cipher, details_key_version,
                               is_default, created_at, updated_at
                        FROM shipping_addresses
                        WHERE user_id = :cuenta
                        ORDER BY created_at DESC, id DESC
                        """).param("cuenta", cuenta.value()).query(this::armar).list();
    }

    /**
     * Una por identificador, sea de quien sea.
     *
     * <p>No filtra por dueno a proposito: quien comprueba de quien es, es el caso de uso, y
     * lo hace para responder 404 y no 403 (criterio 15). Filtrar aqui dejaria esa
     * comprobacion invisible y sin prueba propia.
     */
    @Override
    public Optional<ShippingAddress> buscar(ShippingAddressId id) {
        return jdbc.sql("""
                        SELECT id, user_id, municipality_code, details_cipher, details_key_version,
                               is_default, created_at, updated_at
                        FROM shipping_addresses
                        WHERE id = :id
                        """).param("id", id.value()).query(this::armar).optional();
    }

    /**
     * Inserta si no estaba y reescribe si estaba.
     *
     * <p><strong>{@code DO UPDATE} y no {@code DO NOTHING}</strong>, al reves que el carrito
     * y los favoritos, y la diferencia es lo que significa repetir la escritura en cada
     * caso: alli volver a agregar lo mismo no cambia nada —la identidad es el par y no hay
     * mas que guardar—, y aqui editar una direccion es exactamente reescribir sus campos.
     *
     * <p>{@code created_at} no se toca al actualizar: es la fecha en que se guardo por
     * primera vez y de ella depende el orden de la libreta.
     *
     * <p>{@code is_default} tampoco: cambiarlo es otra operacion —{@link #marcarPredeterminada}—
     * y dejarlo aqui haria que editar una direccion pudiera mover la predeterminada sin que
     * nadie lo pidiera. La excepcion es el {@code INSERT}, donde la primera de una libreta
     * vacia nace ya marcada (RN-099).
     */
    @Override
    public void guardar(ShippingAddress direccion) {
        EncryptedValue detalle = cifrado.cifrar(ShippingAddressDetailsJson.aJson(
                direccion.quienRecibe(),
                direccion.telefono(),
                direccion.linea(),
                direccion.complemento(),
                direccion.indicaciones(),
                direccion.codigoPostal()));

        jdbc.sql("""
                        INSERT INTO shipping_addresses (
                            id, user_id, municipality_code, details_cipher, details_key_version,
                            is_default, created_at, updated_at)
                        VALUES (:id, :cuenta, :municipio, :detalle, :version, :predeterminada, :creada, :actualizada)
                        ON CONFLICT (id) DO UPDATE SET
                            municipality_code   = EXCLUDED.municipality_code,
                            details_cipher      = EXCLUDED.details_cipher,
                            details_key_version = EXCLUDED.details_key_version,
                            updated_at          = EXCLUDED.updated_at
                        """)
                .param("id", direccion.id().value())
                .param("cuenta", direccion.duena().value())
                .param("municipio", direccion.municipio().value())
                .param("detalle", detalle.cipher())
                .param("version", detalle.keyVersion())
                .param("predeterminada", direccion.esPredeterminada())
                .param("creada", Timestamp.from(direccion.creadaEn()))
                .param("actualizada", Timestamp.from(direccion.actualizadaEn()))
                .update();
    }

    /** Idempotente: borrar cero filas es un resultado, no un error (criterio 14). */
    @Override
    public void borrar(ShippingAddressId id) {
        jdbc.sql("DELETE FROM shipping_addresses WHERE id = :id")
                .param("id", id.value())
                .update();
    }

    /**
     * Cambia cual es la predeterminada, en dos escrituras y en este orden. Criterio 13.
     *
     * <p><strong>Primero se le quita la marca a la que la tenia y despues se le pone a la
     * nueva.</strong> Al reves, entre las dos escrituras habria dos filas de la misma cuenta
     * con {@code is_default} en cierto y el indice unico parcial de V21 abortaria la
     * transaccion. El orden es un detalle de como se escribe, y por eso vive aqui y no en el
     * caso de uso.
     *
     * <p>La primera sentencia lleva {@code AND is_default} para no reescribir las nueve
     * filas que no lo eran.
     */
    @Override
    public void marcarPredeterminada(UserId cuenta, ShippingAddressId direccion) {
        jdbc.sql("UPDATE shipping_addresses SET is_default = false WHERE user_id = :cuenta AND is_default")
                .param("cuenta", cuenta.value())
                .update();

        jdbc.sql("UPDATE shipping_addresses SET is_default = true WHERE id = :id AND user_id = :cuenta")
                .param("id", direccion.value())
                .param("cuenta", cuenta.value())
                .update();
    }

    /** Para el cierre de cuenta (RN-102). No falla si no hay ninguna. */
    @Override
    public void borrarDe(UserId cuenta) {
        jdbc.sql("DELETE FROM shipping_addresses WHERE user_id = :cuenta")
                .param("cuenta", cuenta.value())
                .update();
    }

    private ShippingAddress armar(ResultSet fila, int numero) throws SQLException {
        ShippingAddressDetailsJson.Detalle detalle = ShippingAddressDetailsJson.deJson(cifrado.descifrar(
                new EncryptedValue(fila.getString("details_cipher"), fila.getInt("details_key_version"))));

        return ShippingAddress.reconstruir(
                new ShippingAddressId(fila.getObject("id", UUID.class)),
                new UserId(fila.getObject("user_id", UUID.class)),
                detalle.quienRecibe(),
                detalle.telefono(),
                new MunicipalityCode(fila.getString("municipality_code")),
                detalle.linea(),
                detalle.complemento(),
                detalle.indicaciones(),
                detalle.codigoPostal(),
                fila.getBoolean("is_default"),
                fila.getTimestamp("created_at").toInstant(),
                fila.getTimestamp("updated_at").toInstant());
    }
}
