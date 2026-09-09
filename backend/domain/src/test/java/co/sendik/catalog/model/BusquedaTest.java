package co.sendik.catalog.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.shared.money.Money;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** Los objetos de valor con los que se pregunta al catalogo. HU-014. */
class BusquedaTest {

    @Nested
    class TextoBuscado {

        @Test
        void deberia_colapsar_espacios_y_quitar_control() {
            SearchText texto = SearchText.de("  camisa   de\tlino\n ");

            assertThat(texto).isNotNull();
            assertThat(texto.value()).isEqualTo("camisa de lino");
        }

        @Test
        void deberia_conservar_las_tildes_tal_como_se_escribieron() {
            // Quitarlas es cosa del motor, que tiene que encontrar lo mismo con y sin
            // ellas (criterio 5). Aqui se guarda lo que la persona escribio.
            SearchText texto = SearchText.de("camisón");

            assertThat(texto).isNotNull();
            assertThat(texto.value()).isEqualTo("camisón");
        }

        @Test
        void deberia_ser_nulo_cuando_no_hay_nada_escrito() {
            assertThat(SearchText.de(null)).isNull();
            assertThat(SearchText.de("")).isNull();
            assertThat(SearchText.de("   ")).isNull();
        }

        @Test
        void deberia_ser_nulo_cuando_solo_hay_puntuacion() {
            // Caso borde de la historia: se trata como busqueda sin texto, que es el
            // catalogo, y no como una busqueda que no encuentra nada.
            assertThat(SearchText.de("???")).isNull();
            assertThat(SearchText.de("  -- ... !! ")).isNull();
        }

        @Test
        void deberia_aceptar_un_texto_que_solo_tiene_digitos() {
            // Un modelo o una talla escrita en la caja es una busqueda legitima.
            assertThat(SearchText.de("501")).isNotNull();
        }

        @Test
        void deberia_rechazar_un_texto_mas_largo_que_el_tope_en_vez_de_recortarlo() {
            String larguisimo = "a".repeat(SearchText.LARGO_MAXIMO + 1);

            assertThatThrownBy(() -> SearchText.de(larguisimo))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining(String.valueOf(SearchText.LARGO_MAXIMO));
        }

        @Test
        void deberia_aceptar_un_texto_justo_en_el_tope() {
            assertThat(SearchText.de("a".repeat(SearchText.LARGO_MAXIMO))).isNotNull();
        }

        @Test
        void deberia_negarse_a_existir_vacio() {
            assertThatThrownBy(() -> new SearchText("   ")).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class RangoDePrecio {

        private static final Money CINCUENTA = Money.dePesos(50_000);
        private static final Money CIEN = Money.dePesos(100_000);

        @Test
        void deberia_incluir_los_dos_extremos() {
            PriceRange rango = new PriceRange(CINCUENTA, CIEN);

            assertThat(rango.contiene(CINCUENTA)).isTrue();
            assertThat(rango.contiene(CIEN)).isTrue();
            assertThat(rango.contiene(Money.dePesos(75_000))).isTrue();
        }

        @Test
        void deberia_dejar_fuera_lo_que_cae_a_los_lados() {
            PriceRange rango = new PriceRange(CINCUENTA, CIEN);

            assertThat(rango.contiene(Money.dePesos(49_999))).isFalse();
            assertThat(rango.contiene(Money.dePesos(100_001))).isFalse();
        }

        @Test
        void deberia_admitir_un_solo_extremo() {
            assertThat(PriceRange.desde(CINCUENTA).contiene(CIEN)).isTrue();
            assertThat(PriceRange.desde(CIEN).contiene(CINCUENTA)).isFalse();
            assertThat(PriceRange.hasta(CIEN).contiene(CINCUENTA)).isTrue();
            assertThat(PriceRange.hasta(CINCUENTA).contiene(CIEN)).isFalse();
        }

        @Test
        void deberia_no_filtrar_nada_sin_extremos() {
            assertThat(PriceRange.SIN_LIMITE.sinLimite()).isTrue();
            assertThat(PriceRange.SIN_LIMITE.contiene(CIEN)).isTrue();
            assertThat(new PriceRange(CINCUENTA, null).sinLimite()).isFalse();
        }

        @Test
        void deberia_rechazar_un_minimo_mayor_que_el_maximo_en_vez_de_devolver_vacio() {
            assertThatThrownBy(() -> new PriceRange(CIEN, CINCUENTA))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("supera al maximo");
        }

        @Test
        void deberia_admitir_un_rango_de_un_solo_precio() {
            assertThat(new PriceRange(CIEN, CIEN).contiene(CIEN)).isTrue();
        }
    }

    @Nested
    class Orden {

        @Test
        void deberia_cumplir_RN_088_ordenando_por_relevancia_cuando_hay_texto_y_nadie_pidio_orden() {
            assertThat(CatalogSort.efectivo(null, true)).isEqualTo(CatalogSort.RELEVANCE);
        }

        @Test
        void deberia_cumplir_RN_088_ordenando_por_lo_mas_reciente_cuando_no_hay_texto() {
            assertThat(CatalogSort.efectivo(null, false)).isEqualTo(CatalogSort.NEWEST);
        }

        @Test
        void deberia_respetar_el_orden_pedido() {
            assertThat(CatalogSort.efectivo(CatalogSort.PRICE_ASC, true)).isEqualTo(CatalogSort.PRICE_ASC);
            assertThat(CatalogSort.efectivo(CatalogSort.PRICE_DESC, false)).isEqualTo(CatalogSort.PRICE_DESC);
            assertThat(CatalogSort.efectivo(CatalogSort.NEWEST, true)).isEqualTo(CatalogSort.NEWEST);
        }

        @Test
        void deberia_degradar_la_relevancia_a_lo_mas_reciente_cuando_no_hay_texto() {
            // Sin texto todas las filas puntuarian igual y el orden lo decidiria el
            // desempate, que es un orden que nadie pidio y que la pantalla no sabe nombrar.
            assertThat(CatalogSort.efectivo(CatalogSort.RELEVANCE, false)).isEqualTo(CatalogSort.NEWEST);
        }

        @Test
        void deberia_cumplir_RN_084_no_ofreciendo_ningun_orden_comprable() {
            // La regla se sostiene en que no hay un quinto valor: adelantar un resultado
            // por haberlo pagado exigiria agregarlo aqui, a la vista.
            assertThat(CatalogSort.values())
                    .containsExactly(
                            CatalogSort.RELEVANCE, CatalogSort.NEWEST, CatalogSort.PRICE_ASC, CatalogSort.PRICE_DESC);
        }

        @Test
        void deberia_saber_cual_necesita_texto() {
            assertThat(CatalogSort.RELEVANCE.necesitaTexto()).isTrue();
            assertThat(CatalogSort.NEWEST.necesitaTexto()).isFalse();
            assertThat(CatalogSort.PRICE_ASC.necesitaTexto()).isFalse();
        }
    }
}
