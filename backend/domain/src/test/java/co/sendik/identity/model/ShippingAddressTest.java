package co.sendik.identity.model;

import static co.sendik.identity.model.DireccionesDePrueba.AHORA;
import static co.sendik.identity.model.DireccionesDePrueba.BOGOTA;
import static co.sendik.identity.model.DireccionesDePrueba.MEDELLIN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.shared.geo.DepartmentCode;
import java.time.Duration;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ShippingAddressTest {

    private static final UserId ALGUIEN = UserId.nuevo();
    private static final UserId OTRA_PERSONA = UserId.nuevo();

    @Nested
    class AlEscribirla {

        @Test
        void deberia_guardar_lo_que_se_le_dio() {
            ShippingAddress direccion = DireccionesDePrueba.nueva(ALGUIEN);

            assertThat(direccion.duena()).isEqualTo(ALGUIEN);
            assertThat(direccion.quienRecibe().value()).isEqualTo("Ana María Ruiz");
            assertThat(direccion.telefono().value()).isEqualTo("3001234567");
            assertThat(direccion.municipio()).isEqualTo(BOGOTA);
            assertThat(direccion.linea().value()).isEqualTo("Calle 45 # 12-34");
            assertThat(direccion.creadaEn()).isEqualTo(AHORA);
            assertThat(direccion.actualizadaEn()).isEqualTo(AHORA);
        }

        /**
         * RN-099: quien decide que la primera es la predeterminada es la libreta, que es la
         * unica que sabe que no hay otras. Una direccion suelta nace sin marca.
         */
        @Test
        void deberia_nacer_sin_la_marca_de_predeterminada() {
            assertThat(DireccionesDePrueba.nueva(ALGUIEN).esPredeterminada()).isFalse();
        }

        @Test
        void deberia_admitir_que_falten_los_tres_campos_opcionales() {
            ShippingAddress minima = DireccionesDePrueba.minima(ALGUIEN);

            assertThat(minima.complemento()).isNull();
            assertThat(minima.indicaciones()).isNull();
            assertThat(minima.codigoPostal()).isNull();
        }

        /** RN-100: el departamento se deriva y no se guarda, asi que no se puede contradecir. */
        @Test
        void deberia_derivar_el_departamento_del_municipio() {
            assertThat(DireccionesDePrueba.nueva(ALGUIEN).departamento()).isEqualTo(new DepartmentCode("11"));
            assertThat(DireccionesDePrueba.minima(ALGUIEN).departamento()).isEqualTo(new DepartmentCode("05"));
        }

        @Test
        void deberia_exigir_los_campos_obligatorios() {
            assertThatThrownBy(() -> ShippingAddress.nueva(
                            ALGUIEN,
                            new RecipientName("Ana María Ruiz"),
                            new Phone("3001234567"),
                            BOGOTA,
                            null,
                            null,
                            null,
                            null,
                            AHORA))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    @Nested
    class AlEditarla {

        /** Criterio 9: cambia esa y no se crea una segunda. */
        @Test
        void deberia_conservar_identificador_duena_y_fecha_de_creacion() {
            ShippingAddress antes = DireccionesDePrueba.nueva(ALGUIEN);

            ShippingAddress despues = antes.con(
                    new RecipientName("Carlos Pérez"),
                    new Phone("3109876543"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA.plus(Duration.ofDays(1)));

            assertThat(despues.id()).isEqualTo(antes.id());
            assertThat(despues.duena()).isEqualTo(antes.duena());
            assertThat(despues.creadaEn()).isEqualTo(antes.creadaEn());
            assertThat(despues).isEqualTo(antes);
        }

        @Test
        void deberia_cambiar_los_datos_y_la_fecha_de_actualizacion() {
            ShippingAddress antes = DireccionesDePrueba.nueva(ALGUIEN);

            ShippingAddress despues = antes.con(
                    new RecipientName("Carlos Pérez"),
                    new Phone("3109876543"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA.plus(Duration.ofDays(1)));

            assertThat(despues.quienRecibe().value()).isEqualTo("Carlos Pérez");
            assertThat(despues.municipio()).isEqualTo(MEDELLIN);
            assertThat(despues.complemento()).isNull();
            assertThat(despues.actualizadaEn()).isEqualTo(AHORA.plus(Duration.ofDays(1)));
        }

        /**
         * Editar no cambia cual es la predeterminada: eso es otro endpoint y otra operacion.
         */
        @Test
        void deberia_conservar_la_marca_de_predeterminada() {
            ShippingAddress predeterminada = DireccionesDePrueba.nueva(ALGUIEN).comoPredeterminada(true);

            ShippingAddress editada = predeterminada.con(
                    new RecipientName("Carlos Pérez"),
                    new Phone("3109876543"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA);

            assertThat(editada.esPredeterminada()).isTrue();
        }
    }

    @Nested
    class DeQuienEs {

        @Test
        void deberia_reconocer_a_su_duena_y_solo_a_ella() {
            ShippingAddress direccion = DireccionesDePrueba.nueva(ALGUIEN);

            assertThat(direccion.esDe(ALGUIEN)).isTrue();
            assertThat(direccion.esDe(OTRA_PERSONA)).isFalse();
        }

        /** Dos direcciones identicas en campos son dos direcciones distintas. */
        @Test
        void deberia_identificarse_por_su_identificador_y_no_por_su_contenido() {
            ShippingAddress una = DireccionesDePrueba.nueva(ALGUIEN);
            ShippingAddress otra = DireccionesDePrueba.nueva(ALGUIEN);

            assertThat(una).isNotEqualTo(otra);
            assertThat(una).isEqualTo(una.comoPredeterminada(true));
        }
    }

    /**
     * Criterio 19: ningun registro contiene la linea, el telefono ni el nombre de quien
     * recibe. La primera defensa es que el objeto no sepa imprimirlos, como
     * {@code EncryptedValue} y {@code SearchText}.
     */
    @Test
    void deberia_no_imprimir_ningun_dato_personal() {
        ShippingAddress direccion = DireccionesDePrueba.nueva(ALGUIEN);

        assertThat(direccion.toString())
                .doesNotContain("Ana María Ruiz")
                .doesNotContain("3001234567")
                .doesNotContain("Calle 45")
                .doesNotContain("Apto 802")
                .doesNotContain("timbre")
                .doesNotContain("110111")
                .contains(direccion.id().toString());
    }
}
