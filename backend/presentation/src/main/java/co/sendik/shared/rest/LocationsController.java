package co.sendik.shared.rest;

import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.rest.dto.DepartmentsResponse;
import co.sendik.shared.rest.dto.MunicipalitiesResponse;
import co.sendik.shared.rest.dto.PlaceResponse;
import co.sendik.shared.usecase.ListDepartmentsUseCase;
import co.sendik.shared.usecase.ListMunicipalitiesUseCase;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La division politico-administrativa de Colombia, para los dos selectores del formulario de
 * direccion. HU-016, criterios 4 y 21.
 *
 * <p><strong>Dos rutas y no una.</strong> Los departamentos son treinta y tres y los
 * municipios mas de mil: mandar el arbol entero para pintar el primer selector es mandar
 * cuarenta veces lo que se necesita. Las dos son cacheables y lo que devuelven cambia una vez
 * cada varios anos.
 *
 * <p><strong>Detras de {@code FEATURE_CHECKOUT}, aunque no revelen nada.</strong> Una lista de
 * municipios de Colombia no dice que esta construyendo Sendik; lo que pasa es que hoy su unico
 * consumidor es el formulario de direccion de entrega, y una ruta viva que nadie pide es
 * superficie sin dueno. El dia que el cotizador de envios las necesite, dejan la bandera.
 *
 * <p>El nombre no se traduce y por eso no hay una version por idioma: son nombres propios
 * (criterio 26).
 */
@RestController
@RequestMapping("/api/v1/locations")
@ConditionalOnProperty(prefix = "sendik.features", name = "checkout", havingValue = "true")
public class LocationsController {

    private final ListDepartmentsUseCase casoDeDepartamentos;
    private final ListMunicipalitiesUseCase casoDeMunicipios;

    public LocationsController(ListDepartmentsUseCase casoDeDepartamentos, ListMunicipalitiesUseCase casoDeMunicipios) {
        this.casoDeDepartamentos = casoDeDepartamentos;
        this.casoDeMunicipios = casoDeMunicipios;
    }

    /** Los treinta y tres, ordenados por nombre. Bogota D.C. esta aqui y tambien abajo. */
    @GetMapping("/departments")
    public DepartmentsResponse departamentos() {
        return new DepartmentsResponse(casoDeDepartamentos.execute().stream()
                .map(LocationsController::de)
                .toList());
    }

    /**
     * Los municipios <strong>activos</strong> de un departamento, ordenados por nombre.
     *
     * <p>Un codigo que no existe devuelve una lista vacia y no un 404: el codigo viene de la
     * lista de arriba, que la pantalla acaba de pedir, y un error aqui obligaria a tratar como
     * excepcional algo que no lo es.
     */
    @GetMapping("/departments/{departmentCode}/municipalities")
    public MunicipalitiesResponse municipios(@PathVariable String departmentCode) {
        return new MunicipalitiesResponse(casoDeMunicipios.execute(new DepartmentCode(departmentCode)).stream()
                .map(LocationsController::de)
                .toList());
    }

    private static PlaceResponse de(Department departamento) {
        return new PlaceResponse(departamento.codigo().value(), departamento.nombre());
    }

    private static PlaceResponse de(Municipality municipio) {
        return new PlaceResponse(municipio.codigo().value(), municipio.nombre());
    }
}
