package co.sendik.identity.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** Los campos libres de una direccion de entrega. HU-016. */
class CamposDeDireccionTest {

    @Nested
    class LaLineaDeDireccion {

        /**
         * Lo que este caso protege es que nadie meta despues una expresion regular que
         * "valide direcciones": estas son todas reales y ninguna encaja en un patron comun.
         */
        @ParameterizedTest
        @ValueSource(
                strings = {
                    "Calle 45 # 12-34",
                    "Cra 7 No. 71-21 Of. 502",
                    "Dg 61C Bis # 24-30",
                    "Tv 93 # 53-48 Mz 5 Et 2",
                    "Km 3 vía Siberia-Cota",
                    "Av. El Dorado #69-76"
                })
        void deberia_admitir_las_formas_reales_de_escribir_una_direccion(String texto) {
            assertThat(new AddressLine(texto).value()).isEqualTo(texto);
        }

        @Test
        void deberia_colapsar_espacios_y_quitar_caracteres_de_control() {
            assertThat(new AddressLine("  Calle 45\n\t#  12-34  ").value()).isEqualTo("Calle 45 # 12-34");
        }

        @ParameterizedTest
        @ValueSource(strings = {"Cll", "   ", ""})
        void deberia_rechazar_lo_demasiado_corto(String texto) {
            assertThatThrownBy(() -> new AddressLine(texto)).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_rechazar_lo_demasiado_largo() {
            assertThatThrownBy(() -> new AddressLine("x".repeat(121))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class ElNombreDeQuienRecibe {

        /** RN-104: puede no ser el titular, y puede no parecer un nombre de persona. */
        @ParameterizedTest
        @ValueSource(strings = {"Ana María Ruiz", "Portería Torre 3", "Almacén Sendik S.A.S.", "Ño Pérez"})
        void deberia_admitir_cualquier_nombre_real(String texto) {
            assertThat(new RecipientName(texto).value()).isEqualTo(texto);
        }

        @Test
        void deberia_rechazar_lo_demasiado_corto_o_largo() {
            assertThatThrownBy(() -> new RecipientName("A")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new RecipientName("x".repeat(81))).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class LosOpcionales {

        /**
         * Vacio no es lo mismo que ausente: para no tener complemento se deja sin poner, y
         * el nulo lo lleva {@code ShippingAddress}. Una cadena vacia dentro de un objeto de
         * valor es un dato que dice que hay algo cuando no lo hay.
         */
        @Test
        void deberia_rechazar_el_vacio_en_vez_de_aceptarlo_como_ausencia() {
            assertThatThrownBy(() -> new AddressComplement("   ")).isInstanceOf(IllegalArgumentException.class);
            assertThatThrownBy(() -> new DeliveryInstructions("")).isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_acotar_el_complemento_y_las_indicaciones() {
            assertThat(new AddressComplement("Apto 802").value()).isEqualTo("Apto 802");
            assertThatThrownBy(() -> new AddressComplement("x".repeat(61)))
                    .isInstanceOf(IllegalArgumentException.class);

            assertThat(new DeliveryInstructions("El timbre no sirve").value()).isEqualTo("El timbre no sirve");
            assertThatThrownBy(() -> new DeliveryInstructions("x".repeat(201)))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        void deberia_aceptar_un_codigo_postal_de_seis_digitos_con_o_sin_espacios() {
            assertThat(new PostalCode("110111").value()).isEqualTo("110111");
            assertThat(new PostalCode("110 111").value()).isEqualTo("110111");
        }

        @ParameterizedTest
        @ValueSource(strings = {"11011", "1101111", "11011a", ""})
        void deberia_rechazar_un_codigo_postal_que_no_lo_sea(String texto) {
            assertThatThrownBy(() -> new PostalCode(texto)).isInstanceOf(IllegalArgumentException.class);
        }
    }
}
