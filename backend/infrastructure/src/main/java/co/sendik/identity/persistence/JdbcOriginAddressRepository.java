package co.sendik.identity.persistence;

import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.shared.crypto.EncryptedValue;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.SensitiveDataCipher;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia de la direccion de origen. HU-017.
 *
 * <p>Calca a {@link JdbcShippingAddressRepository} con una diferencia que lo simplifica
 * todo: <strong>la clave primaria es la cuenta</strong>. No hay identificador, no hay
 * marca de predeterminada, no hay orden que respetar, y el {@code ON CONFLICT} va sobre
 * {@code user_id}, que es lo que hace del criterio 3 —guardar otro reemplaza y nunca quedan
 * dos— una garantia de la base y no de la aplicacion.
 *
 * <p>Los cuatro campos libres van cifrados en una sola columna; fuera del cifrado quedan el
 * municipio, que es clave foranea, y las fechas. No cruza con {@code municipalities}: los
 * nombres los pone la capa de aplicacion.
 */
@Repository
public class JdbcOriginAddressRepository implements OriginAddressRepository {

    private final JdbcClient jdbc;
    private final SensitiveDataCipher cifrado;

    public JdbcOriginAddressRepository(JdbcClient jdbc, SensitiveDataCipher cifrado) {
        this.jdbc = jdbc;
        this.cifrado = cifrado;
    }

    @Override
    public Optional<OriginAddress> deCuenta(UserId cuenta) {
        return jdbc.sql("""
                        SELECT user_id, municipality_code, details_cipher, details_key_version,
                               created_at, updated_at
                        FROM origin_addresses
                        WHERE user_id = :cuenta
                        """).param("cuenta", cuenta.value()).query(this::armar).optional();
    }

    /**
     * Inserta si no habia y reescribe si habia. {@code created_at} no se toca al
     * actualizar: es la fecha en que la cuenta dijo por primera vez desde donde despacha.
     */
    @Override
    public void guardar(OriginAddress origen) {
        EncryptedValue detalle = cifrado.cifrar(OriginAddressDetailsJson.aJson(
                origen.linea(), origen.complemento(), origen.indicaciones(), origen.codigoPostal()));

        jdbc.sql("""
                        INSERT INTO origin_addresses (
                            user_id, municipality_code, details_cipher, details_key_version,
                            created_at, updated_at)
                        VALUES (:cuenta, :municipio, :detalle, :version, :creada, :actualizada)
                        ON CONFLICT (user_id) DO UPDATE SET
                            municipality_code   = EXCLUDED.municipality_code,
                            details_cipher      = EXCLUDED.details_cipher,
                            details_key_version = EXCLUDED.details_key_version,
                            updated_at          = EXCLUDED.updated_at
                        """)
                .param("cuenta", origen.duena().value())
                .param("municipio", origen.municipio().value())
                .param("detalle", detalle.cipher())
                .param("version", detalle.keyVersion())
                .param("creada", Timestamp.from(origen.creadaEn()))
                .param("actualizada", Timestamp.from(origen.actualizadaEn()))
                .update();
    }

    /** Idempotente: borrar cero filas es un resultado, no un error (criterio 9). */
    @Override
    public void borrar(UserId cuenta) {
        jdbc.sql("DELETE FROM origin_addresses WHERE user_id = :cuenta")
                .param("cuenta", cuenta.value())
                .update();
    }

    private OriginAddress armar(ResultSet fila, int numero) throws SQLException {
        OriginAddressDetailsJson.Detalle detalle = OriginAddressDetailsJson.deJson(cifrado.descifrar(
                new EncryptedValue(fila.getString("details_cipher"), fila.getInt("details_key_version"))));

        return OriginAddress.reconstruir(
                new UserId(fila.getObject("user_id", UUID.class)),
                new MunicipalityCode(fila.getString("municipality_code")),
                detalle.linea(),
                detalle.complemento(),
                detalle.indicaciones(),
                detalle.codigoPostal(),
                fila.getTimestamp("created_at").toInstant(),
                fila.getTimestamp("updated_at").toInstant());
    }
}
