package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.shared.money.Money;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CartLineTest {

    private static final BuyerId ALGUIEN = new BuyerId(UUID.randomUUID());
    private static final SellerId VENDEDOR = new SellerId(UUID.randomUUID());

    @Nested
    class Disponibilidad {

        @Test
        void deberia_estar_disponible_mientras_este_publicada() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));

            CartLine linea = CartLine.de(CartItem.de(ALGUIEN, publicada, AHORA), publicada);

            assertThat(linea.estaDisponible()).isTrue();
        }

        /**
         * Criterio 22: los tres motivos dan lo mismo.
         *
         * <p>Vendido, pausado y archivado responden identico, y esa pobreza es deliberada:
         * distinguirlos publicaria el movimiento del catalogo y las decisiones de un vendedor
         * a cualquiera que apunte identificadores en su carrito (RN-068).
         */
        @Test
        void deberia_apagarse_igual_sea_cual_sea_el_motivo() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));
            CartItem item = CartItem.de(ALGUIEN, publicada, AHORA);

            CartLine pausada = CartLine.de(item, publicada.pausar(AHORA));
            CartLine archivada = CartLine.de(item, publicada.archivar(AHORA));

            assertThat(pausada.estaDisponible()).isFalse();
            assertThat(archivada.estaDisponible()).isFalse();
        }

        /**
         * Criterio 24: vuelve a publicarse, vuelve a contar. Sin escribir nada.
         *
         * <p>La disponibilidad no es un dato del carrito sino una lectura del estado de la
         * publicacion, asi que reanudar la deja disponible sin que nadie vuelva a agregarla.
         */
        @Test
        void deberia_volver_a_contar_cuando_su_vendedor_la_reanuda() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));
            CartItem item = CartItem.de(ALGUIEN, publicada, AHORA);
            Listing pausada = publicada.pausar(AHORA);

            assertThat(CartLine.de(item, pausada).estaDisponible()).isFalse();
            assertThat(CartLine.de(item, pausada.reanudar(AHORA)).estaDisponible()).isTrue();
        }
    }

    @Nested
    class ElPrecio {

        /** RN-093: manda el vigente, no el de entrada. */
        @Test
        void deberia_devolver_el_precio_vigente_y_no_el_de_entrada() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));
            Listing masCara = publicada.cambiarPrecio(Money.dePesos(120_000), AHORA);
            CartItem item = CartItem.de(ALGUIEN, publicada, AHORA);

            CartLine linea = CartLine.de(item, masCara);

            assertThat(linea.precio()).isEqualTo(Money.dePesos(120_000));
            assertThat(linea.precioAlAgregar()).isEqualTo(Money.dePesos(100_000));
        }

        @Test
        void no_deberia_avisar_cuando_el_precio_no_ha_cambiado() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));

            CartLine linea = CartLine.de(CartItem.de(ALGUIEN, publicada, AHORA), publicada);

            assertThat(linea.cambioDePrecio()).isFalse();
        }

        /** Criterio 18. Sube o baja da igual: las dos cosas son cambio de precio. */
        @Test
        void deberia_avisar_cuando_sube_y_cuando_baja() {
            Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));
            CartItem item = CartItem.de(ALGUIEN, publicada, AHORA);

            CartLine subio = CartLine.de(item, publicada.cambiarPrecio(Money.dePesos(120_000), AHORA));
            CartLine bajo = CartLine.de(item, publicada.cambiarPrecio(Money.dePesos(80_000), AHORA));

            assertThat(subio.cambioDePrecio()).isTrue();
            assertThat(bajo.cambioDePrecio()).isTrue();
        }
    }

    @Test
    void deberia_decir_de_que_vendedor_es() {
        Listing publicada = CatalogoDePrueba.publicadaDe(VENDEDOR, Money.dePesos(100_000));

        CartLine linea = CartLine.de(CartItem.de(ALGUIEN, publicada, AHORA), publicada);

        assertThat(linea.vendedor()).isEqualTo(VENDEDOR);
    }
}
