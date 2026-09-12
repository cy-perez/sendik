package co.sendik.shared.persistence;

import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de lectura de la division politico-administrativa. HU-016, RN-100.
 *
 * <p>Solo lee. Las dos tablas las siembra V20 y las vuelve a sembrar otra migracion el dia
 * que el DANE cambie algo: no hay ningun camino por el que la aplicacion escriba aqui, y por
 * eso este adaptador no tiene un solo {@code INSERT}.
 *
 * <p><strong>Sin cache, a proposito.</strong> Son 33 filas y 1122 filas que caben enteras en
 * memoria de PostgreSQL, se piden dos veces por formulario y cambian una vez cada varios
 * anos. Una cache aqui seria un mecanismo nuevo —con su invalidacion— para ahorrar una
 * consulta que ya es trivial. El momento de volver a mirarlo es si el cotizador de envios
 * empieza a pedirlas por cada linea de un pedido.
 *
 * <p><strong>Se ordena por nombre en la base y no en Java.</strong> Ordenar «Ñ» y los
 * acentos como los espera quien lee es cosa de una intercalacion, y la de la base ya esta
 * configurada; hacerlo en Java obligaria a elegir un {@code Collator} y a acertar con el
 * mismo criterio en dos sitios.
 */
@Repository
public class JdbcGeographicDivision implements GeographicDivision {

    private final JdbcClient jdbc;

    public JdbcGeographicDivision(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<Department> departamentos() {
        return jdbc.sql("SELECT code, name FROM departments ORDER BY name")
                .query((fila, numero) ->
                        new Department(new DepartmentCode(fila.getString("code")), fila.getString("name")))
                .list();
    }

    /**
     * Solo los activos: ofrecer un municipio que el DANE suprimio seria dejar guardar una
     * direccion que despues no se puede cotizar (criterio 23).
     */
    @Override
    public List<Municipality> municipiosActivosDe(DepartmentCode departamento) {
        return jdbc.sql("""
                        SELECT code, name, active
                        FROM municipalities
                        WHERE department_code = :departamento AND active
                        ORDER BY name
                        """)
                .param("departamento", departamento.value())
                .query(JdbcGeographicDivision::armar)
                .list();
    }

    /**
     * Uno, activo o no.
     *
     * <p>Devuelve tambien los inactivos y quien decide que un inactivo no se puede elegir es
     * el caso de uso. Asi el mismo metodo sirve para validar lo que se escribe y para leer
     * una direccion vieja.
     */
    @Override
    public Optional<Municipality> buscarMunicipio(MunicipalityCode codigo) {
        return jdbc.sql("SELECT code, name, active FROM municipalities WHERE code = :codigo")
                .param("codigo", codigo.value())
                .query(JdbcGeographicDivision::armar)
                .optional();
    }

    /**
     * Varios de golpe, para pintar una libreta entera con una sola consulta.
     *
     * <p>Se corta en seco con la coleccion vacia: un {@code IN ()} no es SQL valido, y una
     * libreta vacia es el caso mas comun de todos.
     */
    @Override
    public Map<MunicipalityCode, Municipality> buscarMunicipios(Collection<MunicipalityCode> codigos) {
        if (codigos.isEmpty()) {
            return Map.of();
        }

        List<String> valores =
                codigos.stream().map(MunicipalityCode::value).distinct().toList();

        return jdbc
                .sql("SELECT code, name, active FROM municipalities WHERE code IN (:codigos)")
                .param("codigos", valores)
                .query(JdbcGeographicDivision::armar)
                .list()
                .stream()
                .collect(Collectors.toMap(Municipality::codigo, Function.identity()));
    }

    private static Municipality armar(ResultSet fila, int numero) throws SQLException {
        return new Municipality(
                new MunicipalityCode(fila.getString("code")), fila.getString("name"), fila.getBoolean("active"));
    }
}
