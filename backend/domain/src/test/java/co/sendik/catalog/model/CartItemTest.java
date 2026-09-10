package co.sendik.catalog.model;

import static co.sendik.catalog.model.CatalogoDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.catalog.exception.ListingNotFoundException;
import co.sendik.catalog.exception.SelfCartForbiddenException;
import co.sendik.shared.money.Money;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CartItemTest {

    private static final BuyerId ALGUIEN = new BuyerId(UUID.randomUUID());

    @Nested
    class Agregar {

        @Test
        void deberia_guardar_quien_agrego_que_cuando_y_con_que_precio() {
            Listing publicacion = CatalogoDePrueba.publicada();

            CartItem item = CartItem.de(ALGUIEN, publicacion, AHORA);

            assertThat(item.quien()).isEqualTo(ALGUIEN);
            assertThat(item.publicacion()).isEqualTo(publicacion.id());
            assertThat(item.agregadoEn()).isEqualTo(AHORA);
            assertThat(item.precioAlAgregar()).isEqualTo(publicacion.product().price());
        }

        @Test
        void deberia_rechazar_la_publicacion_propia_RN_092() {
            Listing publicacion = CatalogoDePrueba.publicada();
            BuyerId elVendedor = new BuyerId(publicacion.sellerId().value());

            assertThatThrownBy(() -> CartItem.de(elVendedor, publicacion, AHORA))
                    .isInstanceOf(SelfCartForbiddenException.class);
        }

        /**
         * RN-089: agregar no reserva. La publicacion sale igual que entro.
         *
         * <p>Es la regla que hace honesto todo lo demas y la unica forma de comprobarla en el
         * dominio: si agregar cambiara algo de la publicacion, dos personas no podrian llevar
         * el mismo producto en su carrito.
         */
        @Test
        void no_deberia_tocar_la_publicacion_RN_089() {
            Listing publicacion = CatalogoDePrueba.publicada();

            CartItem.de(ALGUIEN, publicacion, AHORA);

            assertThat(publicacion.status()).isEqualTo(ListingStatus.PUBLISHED);
            assertThat(publicacion.esVisible()).isTrue();
        }
    }

    @Nested
    class LoQueNoSeVe {

        /**
         * El orden de las dos comprobaciones: primero el estado, despues el dueno.
         *
         * <p>Al reves, agregar el borrador de otra persona respondera 403 y con eso
         * confirmaria que ese identificador es una publicacion real. Aqui el borrador es
         * ademas propio, asi que las dos reglas aplican y solo una puede ganar: tiene que
         * ganar RN-068.
         */
        @Test
        void deberia_responder_no_encontrado_antes_que_prohibido_RN_068() {
            Listing borrador = CatalogoDePrueba.borradorCompleto();
            BuyerId elVendedor = new BuyerId(borrador.sellerId().value());

            assertThatThrownBy(() -> CartItem.de(elVendedor, borrador, AHORA))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_rechazar_lo_pausado() {
            Listing pausada = CatalogoDePrueba.publicada().pausar(AHORA);

            assertThatThrownBy(() -> CartItem.de(ALGUIEN, pausada, AHORA))
                    .isInstanceOf(ListingNotFoundException.class);
        }

        @Test
        void deberia_rechazar_lo_archivado() {
            Listing archivada = CatalogoDePrueba.publicada().archivar(AHORA);

            assertThatThrownBy(() -> CartItem.de(ALGUIEN, archivada, AHORA))
                    .isInstanceOf(ListingNotFoundException.class);
        }
    }

    @Nested
    class LaIdentidadEsElPar {

        /**
         * RN-091: un producto entra una sola vez. Criterio 4.
         *
         * <p>Ni la fecha ni el precio entran en la identidad. Que el precio quede fuera
         * importa mas de lo que parece: si entrara, volver a agregar un producto que cambio
         * de precio crearia un segundo item y el carrito ensenaria el mismo producto dos
         * veces.
         */
        @Test
        void deberia_ignorar_la_fecha_y_el_precio() {
            Listing publicacion = CatalogoDePrueba.publicada();

            CartItem primero = CartItem.de(ALGUIEN, publicacion, AHORA);
            CartItem segundo = CartItem.reconstruir(
                    ALGUIEN, publicacion.id(), AHORA.plus(Duration.ofDays(3)), Money.dePesos(999_999));

            assertThat(primero).isEqualTo(segundo);
            assertThat(primero).hasSameHashCodeAs(segundo);
        }

        @Test
        void deberia_distinguir_dos_personas_sobre_la_misma_publicacion() {
            Listing publicacion = CatalogoDePrueba.publicada();

            CartItem mio = CartItem.de(ALGUIEN, publicacion, AHORA);
            CartItem tuyo = CartItem.de(new BuyerId(UUID.randomUUID()), publicacion, AHORA);

            assertThat(mio).isNotEqualTo(tuyo);
        }

        @Test
        void deberia_distinguir_dos_publicaciones_de_la_misma_persona() {
            CartItem una = CartItem.de(ALGUIEN, CatalogoDePrueba.publicada(), AHORA);
            CartItem otra = CartItem.de(ALGUIEN, CatalogoDePrueba.publicada(), AHORA);

            assertThat(una).isNotEqualTo(otra);
        }
    }

    @Nested
    class LoQueYaEstabaGuardado {

        /**
         * Reconstruir no vuelve a comprobar las reglas, y de eso depende el criterio 21.
         *
         * <p>Si las comprobara, leer el carrito fallaria en cuanto alguien vendiera uno de
         * los productos, que es justo lo contrario de lo que RN-094 manda hacer: sigue a la
         * vista, apagado.
         */
        @Test
        void deberia_reconstruir_un_item_sobre_algo_que_ya_no_esta_publicado() {
            Listing vendida = CatalogoDePrueba.publicada();

            CartItem item = CartItem.reconstruir(ALGUIEN, vendida.id(), AHORA, Money.dePesos(185_000));

            assertThat(item.publicacion()).isEqualTo(vendida.id());
            assertThat(item.precioAlAgregar()).isEqualTo(Money.dePesos(185_000));
        }
    }
}
