package co.sendik.identity.model;

import static co.sendik.identity.model.DireccionesDePrueba.AHORA;
import static co.sendik.identity.model.DireccionesDePrueba.BOGOTA;
import static co.sendik.identity.model.DireccionesDePrueba.MEDELLIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.shared.geo.DepartmentCode;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class OriginAddressTest {

    private static final UserId ALGUIEN = UserId.nuevo();
    private static final UserId OTRA_PERSONA = UserId.nuevo();

    @Nested
    class AlEscribirlo {

        @Test
        void deberia_guardar_lo_que_se_le_dio() {
            OriginAddress origen = DireccionesDePrueba.origen(ALGUIEN);

            assertThat(origen.duena()).isEqualTo(ALGUIEN);
            assertThat(origen.municipio()).isEqualTo(BOGOTA);
            assertThat(origen.linea().value()).isEqualTo("Carrera 15 # 93-47");
            assertThat(origen.complemento()).isNotNull();
            assertThat(origen.indicaciones()).isNotNull();
            assertThat(origen.codigoPostal()).isNotNull();
            assertThat(origen.creadaEn()).isEqualTo(AHORA);
            assertThat(origen.actualizadaEn()).isEqualTo(AHORA);
        }

        @Test
        void deberia_admitir_que_falten_los_tres_campos_opcionales() {
            OriginAddress minimo = DireccionesDePrueba.origenMinimo(ALGUIEN);

            assertThat(minimo.complemento()).isNull();
            assertThat(minimo.indicaciones()).isNull();
            assertThat(minimo.codigoPostal()).isNull();
        }

        /** RN-100: el departamento se deriva y no se guarda. */
        @Test
        void deberia_derivar_el_departamento_del_municipio() {
            assertThat(DireccionesDePrueba.origen(ALGUIEN).departamento()).isEqualTo(new DepartmentCode("11"));
            assertThat(DireccionesDePrueba.origenMinimo(ALGUIEN).departamento()).isEqualTo(new DepartmentCode("05"));
        }

        @Test
        void deberia_exigir_los_campos_obligatorios() {
            assertThatThrownBy(() -> OriginAddress.nueva(ALGUIEN, BOGOTA, null, null, null, null, AHORA))
                    .isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> OriginAddress.nueva(
                            ALGUIEN, null, new AddressLine("Carrera 15 # 93-47"), null, null, null, AHORA))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class AlReemplazarlo {

        /** Criterio 3: reemplaza el contenido y sigue siendo el mismo origen de la misma cuenta. */
        @Test
        void deberia_cambiar_los_datos_conservando_la_duena_y_la_fecha_de_creacion() {
            Instant despues = AHORA.plus(Duration.ofDays(3));

            OriginAddress cambiado = DireccionesDePrueba.origen(ALGUIEN)
                    .con(MEDELLIN, new AddressLine("Carrera 70 # 45-12"), null, null, null, despues);

            assertThat(cambiado.duena()).isEqualTo(ALGUIEN);
            assertThat(cambiado.municipio()).isEqualTo(MEDELLIN);
            assertThat(cambiado.linea().value()).isEqualTo("Carrera 70 # 45-12");
            assertThat(cambiado.complemento()).isNull();
            assertThat(cambiado.creadaEn()).isEqualTo(AHORA);
            assertThat(cambiado.actualizadaEn()).isEqualTo(despues);
        }
    }

    @Nested
    class DeQuienEs {

        @Test
        void deberia_saber_de_quien_es() {
            OriginAddress origen = DireccionesDePrueba.origen(ALGUIEN);

            assertThat(origen.esDe(ALGUIEN)).isTrue();
            assertThat(origen.esDe(OTRA_PERSONA)).isFalse();
        }

        /** Solo hay uno por cuenta, asi que la cuenta es la identidad. */
        @Test
        void deberia_ser_el_mismo_origen_si_es_de_la_misma_cuenta() {
            OriginAddress uno = DireccionesDePrueba.origen(ALGUIEN);
            OriginAddress otro = DireccionesDePrueba.origenMinimo(ALGUIEN);

            assertThat(uno).isEqualTo(otro).hasSameHashCodeAs(otro);
            assertThat(uno).isNotEqualTo(DireccionesDePrueba.origen(OTRA_PERSONA));
        }

        /** Criterio 17: ni la linea, ni las indicaciones, ni el municipio en un registro. */
        @Test
        void deberia_no_imprimir_nada_de_lo_que_hay_dentro() {
            String texto = DireccionesDePrueba.origen(ALGUIEN).toString();

            assertThat(texto)
                    .doesNotContain("Carrera 15")
                    .doesNotContain("Local 3")
                    .doesNotContain("Nubia")
                    .doesNotContain("110221")
                    .doesNotContain("11001");
        }
    }
}
