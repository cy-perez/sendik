package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.usecase.ListDepartmentsUseCase;
import co.sendik.shared.usecase.ListMunicipalitiesUseCase;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** El borde de la division politico-administrativa. HU-016, criterios 4, 21 y 26. */
class LocationsControllerTest {

    private final ListDepartmentsUseCase departamentos = mock(ListDepartmentsUseCase.class);
    private final ListMunicipalitiesUseCase municipios = mock(ListMunicipalitiesUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(new LocationsController(departamentos, municipios))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    /** Los dos van con la misma forma —codigo y nombre— y envueltos en un objeto. */
    @Test
    void deberia_devolver_los_departamentos_con_su_codigo_y_su_nombre() throws Exception {
        when(departamentos.execute())
                .thenReturn(List.of(
                        new Department(new DepartmentCode("05"), "Antioquia"),
                        new Department(new DepartmentCode("11"), "Bogotá, D.C.")));

        mvc.perform(get("/api/v1/locations/departments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departments.length()").value(2))
                .andExpect(jsonPath("$.departments[0].code").value("05"))
                .andExpect(jsonPath("$.departments[0].name").value("Antioquia"));
    }

    @Test
    void deberia_pedir_los_municipios_del_departamento_de_la_ruta() throws Exception {
        when(municipios.execute(new DepartmentCode("11")))
                .thenReturn(List.of(new Municipality(new MunicipalityCode("11001"), "Bogotá, D.C.", true)));

        mvc.perform(get("/api/v1/locations/departments/11/municipalities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipalities[0].code").value("11001"))
                .andExpect(jsonPath("$.municipalities[0].name").value("Bogotá, D.C."));

        ArgumentCaptor<DepartmentCode> pedido = ArgumentCaptor.forClass(DepartmentCode.class);
        verify(municipios).execute(pedido.capture());
        assertThat(pedido.getValue().value()).isEqualTo("11");
    }

    /**
     * Un codigo que no son dos digitos lo rechaza el objeto de valor, y el manejador lo
     * traduce a 400: es un problema de la peticion y no del negocio.
     */
    @Test
    void deberia_rechazar_un_codigo_de_departamento_mal_formado() throws Exception {
        mvc.perform(get("/api/v1/locations/departments/bogota/municipalities"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }
}
