package co.sendik.shared.geo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class GeoCodesTest {

    @Nested
    class ElCodigoDeMunicipio {

        /**
         * La propiedad de la que depende el contrato entero: el cuerpo de la API pide solo
         * el municipio porque el departamento se deriva. Si esto dejara de ser cierto,
         * habria que volver a pedir los dos y comprobar que casan.
         */
        @Test
        void deberia_llevar_dentro_el_codigo_de_su_departamento() {
            assertThat(new MunicipalityCode("11001").departamento()).isEqualTo(new DepartmentCode("11"));
            assertThat(new MunicipalityCode("05001").departamento()).isEqualTo(new DepartmentCode("05"));
            assertThat(new MunicipalityCode("88564").departamento()).isEqualTo(new DepartmentCode("88"));
        }

        /**
         * Antioquia es 05 y Atlantico 08. Guardarlos como numero se come el cero y el
         * codigo deja de casar con ninguna fila.
         */
        @Test
        void deberia_conservar_el_cero_de_la_izquierda() {
            assertThat(new MunicipalityCode("05001").value()).isEqualTo("05001");
            assertThat(new DepartmentCode("08").value()).isEqualTo("08");
        }

        @ParameterizedTest
        @ValueSource(strings = {"1100", "110011", "1100a", "", "  ", "bogota"})
        void deberia_rechazar_lo_que_no_sean_cinco_digitos(String texto) {
            assertThatThrownBy(() -> new MunicipalityCode(texto)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_rechazar_el_nulo() {
            assertThatThrownBy(() -> new MunicipalityCode(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ElCodigoDeDepartamento {

        @ParameterizedTest
        @ValueSource(strings = {"1", "111", "a1", ""})
        void deberia_rechazar_lo_que_no_sean_dos_digitos(String texto) {
            assertThatThrownBy(() -> new DepartmentCode(texto)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_rechazar_el_nulo() {
            assertThatThrownBy(() -> new DepartmentCode(null)).isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class ElDatoDeReferencia {

        @Test
        void deberia_derivar_el_departamento_del_municipio_sin_guardarlo() {
            Municipality medellin = new Municipality(new MunicipalityCode("05001"), "Medellín", true);

            assertThat(medellin.departamento()).isEqualTo(new DepartmentCode("05"));
        }

        /**
         * Criterio 23: un municipio suprimido se marca inactivo y la fila se queda, porque
         * hay direcciones guardadas que la apuntan.
         */
        @Test
        void deberia_poder_estar_inactivo() {
            Municipality suprimido = new Municipality(new MunicipalityCode("05001"), "Medellín", false);

            assertThat(suprimido.activo()).isFalse();
        }

        @Test
        void deberia_rechazar_un_nombre_vacio() {
            assertThatThrownBy(() -> new Department(new DepartmentCode("05"), "  "))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new Municipality(new MunicipalityCode("05001"), "", true))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_rechazar_nulos() {
            assertThatThrownBy(() -> new Department(null, "Antioquia")).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> new Municipality(new MunicipalityCode("05001"), null, true))
                    .isInstanceOf(NullPointerException.class);
        }
    }
}
