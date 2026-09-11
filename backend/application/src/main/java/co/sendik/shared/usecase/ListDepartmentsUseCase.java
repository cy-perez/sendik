package co.sendik.shared.usecase;

import co.sendik.shared.geo.Department;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.List;

/**
 * Los departamentos de Colombia, para que el formulario los ofrezca. HU-016, criterio 21.
 *
 * <p>Existe aunque solo delegue, por lo mismo que {@code ListFinancialInstitutionsUseCase}:
 * entre el controlador y el puerto va el caso de uso, y {@code ArchitectureTest} lo
 * comprueba.
 *
 * <p>Vive en {@code shared} porque la division politico-administrativa no es de ningun
 * contexto: hoy la pide el formulario de direccion y manana el cotizador de envios
 * (ADR-0040).
 */
public class ListDepartmentsUseCase {

    private final GeographicDivision division;

    public ListDepartmentsUseCase(GeographicDivision division) {
        this.division = division;
    }

    public List<Department> execute() {
        return division.departamentos();
    }
}
