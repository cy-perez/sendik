package co.sendik.identity.usecase;

import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * La direccion de origen, en memoria. HU-017.
 *
 * <p>Reproduce lo unico del esquema que las pruebas necesitan: que la clave es la cuenta,
 * asi que guardar dos veces deja una. La division la presta {@link LibretaEnMemoria}.
 */
final class OrigenEnMemoria implements OriginAddressRepository {

    private final Map<UserId, OriginAddress> filas = new HashMap<>();

    @Override
    public Optional<OriginAddress> deCuenta(UserId cuenta) {
        return Optional.ofNullable(filas.get(cuenta));
    }

    /** Conserva la fecha de creacion si ya habia, como el {@code ON CONFLICT DO UPDATE} real. */
    @Override
    public void guardar(OriginAddress origen) {
        OriginAddress anterior = filas.get(origen.duena());
        filas.put(
                origen.duena(),
                anterior == null
                        ? origen
                        : OriginAddress.reconstruir(
                                origen.duena(),
                                origen.municipio(),
                                origen.linea(),
                                origen.complemento(),
                                origen.indicaciones(),
                                origen.codigoPostal(),
                                anterior.creadaEn(),
                                origen.actualizadaEn()));
    }

    @Override
    public void borrar(UserId cuenta) {
        filas.remove(cuenta);
    }

    int cuantasFilas() {
        return filas.size();
    }
}
