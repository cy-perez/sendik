package co.sendik.identity.usecase;

import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * La libreta y la division politico-administrativa, en memoria. HU-016.
 *
 * <p>Es el equivalente de {@code CatalogoEnMemoria} para esta historia, y existe por lo
 * mismo: los casos de uso se prueban sin Spring y sin base de datos (backend/CLAUDE.md).
 *
 * <p><strong>Reproduce dos comportamientos del esquema que las pruebas necesitan</strong>, y
 * son justamente los que si se rompieran no se notarian en un mock que devuelve lo que se le
 * diga: el orden de {@code deCuenta} —de la mas reciente a la mas antigua, que es de lo que
 * depende el relevo del criterio 11— y que {@code marcarPredeterminada} quite la marca a la
 * anterior, que en la base lo garantiza el indice unico parcial de V21.
 */
final class LibretaEnMemoria implements ShippingAddressRepository, GeographicDivision {

    static final MunicipalityCode BOGOTA = new MunicipalityCode("11001");
    static final MunicipalityCode MEDELLIN = new MunicipalityCode("05001");
    static final MunicipalityCode SUPRIMIDO = new MunicipalityCode("05999");
    static final MunicipalityCode INEXISTENTE = new MunicipalityCode("99999");

    private final Map<ShippingAddressId, ShippingAddress> filas = new LinkedHashMap<>();

    private final Map<MunicipalityCode, Municipality> municipios = Map.of(
            BOGOTA, new Municipality(BOGOTA, "Bogotá, D.C.", true),
            MEDELLIN, new Municipality(MEDELLIN, "Medellín", true),
            SUPRIMIDO, new Municipality(SUPRIMIDO, "Un municipio que el DANE suprimio", false));

    private final List<Department> departamentos = List.of(
            new Department(new DepartmentCode("05"), "Antioquia"),
            new Department(new DepartmentCode("11"), "Bogotá, D.C."));

    // --- ShippingAddressRepository ---

    @Override
    public List<ShippingAddress> deCuenta(UserId cuenta) {
        return filas.values().stream()
                .filter(direccion -> direccion.esDe(cuenta))
                .sorted(Comparator.comparing(ShippingAddress::creadaEn)
                        .thenComparing(direccion -> direccion.id().value())
                        .reversed())
                .toList();
    }

    @Override
    public Optional<ShippingAddress> buscar(ShippingAddressId id, UserId cuenta) {
        return Optional.ofNullable(filas.get(id)).filter(direccion -> direccion.esDe(cuenta));
    }

    /**
     * Inserta si no estaba y reescribe los campos si estaba.
     *
     * <p><strong>Conserva la marca de predeterminada y la fecha de creacion</strong>, que es
     * lo que hace el {@code ON CONFLICT DO UPDATE} del adaptador real: alli las dos columnas
     * quedan fuera del {@code SET} a proposito. Un doble que las pisara seria mas permisivo
     * que lo real, y un futuro {@code guardar(x.comoPredeterminada(true))} pasaria aqui en
     * verde sin hacer nada en produccion. Se corrigio tras la revision de pruebas.
     */
    @Override
    public void guardar(ShippingAddress direccion) {
        ShippingAddress anterior = filas.get(direccion.id());
        filas.put(
                direccion.id(),
                anterior == null
                        ? direccion
                        : ShippingAddress.reconstruir(
                                direccion.id(),
                                direccion.duena(),
                                direccion.quienRecibe(),
                                direccion.telefono(),
                                direccion.municipio(),
                                direccion.linea(),
                                direccion.complemento(),
                                direccion.indicaciones(),
                                direccion.codigoPostal(),
                                // Las dos columnas que el `ON CONFLICT DO UPDATE` real deja
                                // fuera del SET: `is_default` y `created_at`. La version
                                // anterior conservaba solo la primera, aunque su javadoc
                                // dijera que las dos.
                                anterior.esPredeterminada(),
                                anterior.creadaEn(),
                                direccion.actualizadaEn()));
    }

    @Override
    public void borrar(ShippingAddressId id, UserId cuenta) {
        buscar(id, cuenta).ifPresent(direccion -> filas.remove(direccion.id()));
    }

    @Override
    public void marcarPredeterminada(UserId cuenta, ShippingAddressId direccion) {
        deCuenta(cuenta).forEach(actual -> filas.put(actual.id(), actual.comoPredeterminada(false)));
        // Con el dueno, como el SQL real: sin esto, marcar la ajena "funcionaria" aqui.
        buscar(direccion, cuenta).ifPresent(elegida -> filas.put(direccion, elegida.comoPredeterminada(true)));
    }

    @Override
    public void borrarDe(UserId cuenta) {
        deCuenta(cuenta).forEach(direccion -> filas.remove(direccion.id()));
    }

    // --- GeographicDivision ---

    @Override
    public List<Department> departamentos() {
        return departamentos;
    }

    @Override
    public List<Municipality> municipiosActivosDe(DepartmentCode departamento) {
        return municipios.values().stream()
                .filter(municipio ->
                        municipio.activo() && municipio.departamento().equals(departamento))
                .sorted(Comparator.comparing(Municipality::nombre))
                .toList();
    }

    @Override
    public Optional<Municipality> buscarMunicipio(MunicipalityCode codigo) {
        return Optional.ofNullable(municipios.get(codigo));
    }

    @Override
    public Map<MunicipalityCode, Municipality> buscarMunicipios(Collection<MunicipalityCode> codigos) {
        return codigos.stream()
                .filter(municipios::containsKey)
                .distinct()
                .collect(Collectors.toMap(codigo -> codigo, municipios::get));
    }
}
