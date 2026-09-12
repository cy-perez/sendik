package co.sendik.shared.usecase;

import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.List;

/**
 * Los municipios de un departamento, para el segundo selector. HU-016, criterios 4 y 21.
 *
 * <p><strong>Solo los activos.</strong> Ofrecer uno que el DANE suprimio seria dejar guardar
 * una direccion que despues no se podra cotizar. Los inactivos siguen leyendose, pero solo
 * para las direcciones que ya los tenian guardados (criterio 23).
 *
 * <p><strong>Dos peticiones y no una.</strong> Los departamentos son treinta y tres y los
 * municipios mas de mil: mandar el arbol entero para pintar el primer selector es mandar
 * cuarenta veces lo que se necesita. Los dos son cacheables y cambian una vez cada varios
 * anos.
 *
 * <p>Un departamento que no existe devuelve una lista vacia y no un error: el codigo es de
 * la ruta, y la pantalla que lo pide acaba de sacarlo de la lista de arriba.
 */
public class ListMunicipalitiesUseCase {

    private final GeographicDivision division;

    public ListMunicipalitiesUseCase(GeographicDivision division) {
        this.division = division;
    }

    public List<Municipality> execute(DepartmentCode departamento) {
        return division.municipiosActivosDe(departamento);
    }
}
