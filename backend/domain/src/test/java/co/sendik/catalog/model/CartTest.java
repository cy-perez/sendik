package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.shared.money.Money;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CartTest {

    private static final SellerId UNA = new SellerId(UUID.randomUUID());
    private static final SellerId OTRA = new SellerId(UUID.randomUUID());

    private static CartLine linea(SellerId vendedor, long pesos, Duration desde) {
        Listing publicacion = CatalogoDePrueba.publicadaDe(vendedor, Money.dePesos(pesos));

        return new CartLine(publicacion, AHORA.plus(desde), Money.dePesos(pesos));
    }

    private static CartLine noDisponible(SellerId vendedor, long pesos, Duration desde) {
        Listing pausada =
                CatalogoDePrueba.publicadaDe(vendedor, Money.dePesos(pesos)).pausar(AHORA);

        return new CartLine(pausada, AHORA.plus(desde), Money.dePesos(pesos));
    }

    @Nested
    class AgruparPorVendedor {

        /** RN-090: un carrito admite varios vendedores y se agrupa por vendedor. */
        @Test
        void deberia_hacer_un_grupo_por_vendedor() {
            Cart carrito = Cart.de(List.of(
                    linea(UNA, 100_000, Duration.ZERO),
                    linea(OTRA, 50_000, Duration.ofMinutes(1)),
                    linea(UNA, 30_000, Duration.ofMinutes(2))));

            assertThat(carrito.grupos()).hasSize(2);
            assertThat(carrito.grupos()).extracting(CartGroup::vendedor).containsExactlyInAnyOrder(UNA, OTRA);
        }

        @Test
        void deberia_dejar_un_solo_grupo_cuando_todo_es_del_mismo_vendedor() {
            Cart carrito =
                    Cart.de(List.of(linea(UNA, 100_000, Duration.ZERO), linea(UNA, 50_000, Duration.ofMinutes(1))));

            assertThat(carrito.grupos()).hasSize(1);
            assertThat(carrito.grupos().getFirst().lineas()).hasSize(2);
            assertThat(carrito.seDividira()).isFalse();
        }

        /** Criterio 15: con mas de un vendedor hay que avisar de que seran dos pedidos. */
        @Test
        void deberia_avisar_de_la_division_con_dos_vendedores() {
            Cart carrito =
                    Cart.de(List.of(linea(UNA, 100_000, Duration.ZERO), linea(OTRA, 50_000, Duration.ofMinutes(1))));

            assertThat(carrito.seDividira()).isTrue();
        }

        /**
         * El orden es el del gesto: lo ultimo agregado, primero.
         *
         * <p>Sin un orden definido, dos lecturas seguidas del mismo carrito podrian barajar
         * los grupos y quien mira creeria que algo cambio.
         */
        @Test
        void deberia_ordenar_por_lo_agregado_mas_recientemente() {
            CartLine vieja = linea(UNA, 100_000, Duration.ZERO);
            CartLine media = linea(OTRA, 50_000, Duration.ofMinutes(5));
            CartLine nueva = linea(UNA, 30_000, Duration.ofMinutes(9));

            Cart carrito = Cart.de(List.of(vieja, media, nueva));

            assertThat(carrito.grupos().getFirst().vendedor()).isEqualTo(UNA);
            assertThat(carrito.grupos().getFirst().lineas()).containsExactly(nueva, vieja);
            assertThat(carrito.grupos().getLast().vendedor()).isEqualTo(OTRA);
        }

        @Test
        void deberia_armar_un_carrito_vacio_sin_grupos() {
            Cart carrito = Cart.de(List.of());

            assertThat(carrito.estaVacio()).isTrue();
            assertThat(carrito.grupos()).isEmpty();
            assertThat(carrito.cuantos()).isZero();
            assertThat(carrito.seDividira()).isFalse();
        }
    }

    @Nested
    class Subtotales {

        /** RN-096: subtotal de precio base por vendedor. Nunca un total. */
        @Test
        void deberia_sumar_el_precio_vigente_de_cada_grupo() {
            Cart carrito = Cart.de(List.of(
                    linea(UNA, 100_000, Duration.ZERO),
                    linea(UNA, 30_000, Duration.ofMinutes(1)),
                    linea(OTRA, 50_000, Duration.ofMinutes(2))));

            CartGroup deUna = carrito.grupos().stream()
                    .filter(grupo -> grupo.vendedor().equals(UNA))
                    .findFirst()
                    .orElseThrow();
            CartGroup deOtra = carrito.grupos().stream()
                    .filter(grupo -> grupo.vendedor().equals(OTRA))
                    .findFirst()
                    .orElseThrow();

            assertThat(deUna.subtotal()).isEqualTo(Money.dePesos(130_000));
            assertThat(deOtra.subtotal()).isEqualTo(Money.dePesos(50_000));
        }

        /** RN-094, criterio 21: lo no disponible sigue a la vista y no suma. */
        @Test
        void no_deberia_sumar_lo_que_dejo_de_estar_disponible() {
            Cart carrito = Cart.de(
                    List.of(linea(UNA, 100_000, Duration.ZERO), noDisponible(UNA, 900_000, Duration.ofMinutes(1))));

            CartGroup grupo = carrito.grupos().getFirst();

            assertThat(grupo.lineas()).hasSize(2);
            assertThat(grupo.subtotal()).isEqualTo(Money.dePesos(100_000));
        }

        /** Criterio 23: un grupo entero apagado da cero y no desaparece. */
        @Test
        void deberia_dejar_en_cero_el_grupo_entero_no_disponible_sin_ocultarlo() {
            Cart carrito = Cart.de(List.of(
                    noDisponible(UNA, 100_000, Duration.ZERO), noDisponible(UNA, 50_000, Duration.ofMinutes(1))));

            CartGroup grupo = carrito.grupos().getFirst();

            assertThat(carrito.grupos()).hasSize(1);
            assertThat(grupo.estaEnteroNoDisponible()).isTrue();
            assertThat(grupo.subtotal()).isEqualTo(Money.dePesos(0));
        }

        /** RN-029: la suma es exacta aunque el carrito vaya lleno de lo mas caro. */
        @Test
        void deberia_sumar_con_exactitud_un_grupo_lleno() {
            List<CartLine> veinte = java.util.stream.IntStream.range(0, Cart.MAXIMO_DE_PRODUCTOS)
                    .mapToObj(i -> linea(UNA, 20_000_000, Duration.ofMinutes(i)))
                    .toList();

            Cart carrito = Cart.de(veinte);

            assertThat(carrito.cuantos()).isEqualTo(20);
            assertThat(carrito.grupos().getFirst().subtotal().enPesos()).isEqualTo(400_000_000L);
        }
    }

    @Nested
    class ElTope {

        /**
         * RN-097. El numero vive aqui y no en la capa de aplicacion: es regla de negocio.
         *
         * <p><strong>Se afirma el efecto y no el literal.</strong> Antes decia
         * {@code assertThat(MAXIMO_DE_PRODUCTOS).isEqualTo(20)}, que es afirmar que una
         * constante vale su propio valor: cambiarla a diez dejaba esta prueba y todas las
         * demas en verde, porque todas usan el simbolo. Lo que si se puede fijar aqui es que
         * el tope acota de verdad un carrito armado.
         *
         * <p>Lo que esta prueba **no** puede cubrir, y la historia lo declara como su deuda
         * mas concreta: que el tope del frontend siga valiendo lo mismo. Son dos dominios y no
         * hay guardian que los compare.
         */
        @Test
        void deberia_acotar_el_carrito_al_tope() {
            List<CartLine> justas = java.util.stream.IntStream.range(0, Cart.MAXIMO_DE_PRODUCTOS)
                    .mapToObj(i -> linea(UNA, 100_000, Duration.ofMinutes(i)))
                    .toList();

            assertThat(Cart.de(justas).cuantos()).isEqualTo(Cart.MAXIMO_DE_PRODUCTOS);
            assertThat(Cart.MAXIMO_DE_PRODUCTOS).isPositive();
        }

        @Test
        void deberia_contar_tambien_lo_no_disponible() {
            Cart carrito = Cart.de(
                    List.of(linea(UNA, 100_000, Duration.ZERO), noDisponible(OTRA, 50_000, Duration.ofMinutes(1))));

            assertThat(carrito.cuantos()).isEqualTo(2);
        }
    }

    @Nested
    class GrupoVacio {

        /** Lo que se vacia se va entero: no queda un encabezado de vendedor sin nada. */
        @Test
        void deberia_rechazar_un_grupo_sin_lineas() {
            assertThatThrownBy(() -> new CartGroup(UNA, List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("sin lineas");
        }
    }
}
