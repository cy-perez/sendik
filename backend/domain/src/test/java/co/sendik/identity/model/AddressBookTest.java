package co.sendik.identity.model;

import static co.sendik.identity.model.DireccionesDePrueba.AHORA;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class AddressBookTest {

    private static final UserId ALGUIEN = UserId.nuevo();

    /** Como las devuelve el indice de V21: de la mas reciente a la mas antigua. */
    private static AddressBook libretaDe(int cuantas) {
        List<ShippingAddress> direcciones = new ArrayList<>(IntStream.range(0, cuantas)
                .mapToObj(i -> DireccionesDePrueba.nueva(ALGUIEN, AHORA.minus(Duration.ofDays(i))))
                .toList());
        return new AddressBook(direcciones);
    }

    @Nested
    class ElTope {

        /** RN-101. */
        @Test
        void deberia_estar_llena_con_diez() {
            assertThat(libretaDe(9).estaLlena()).isFalse();
            assertThat(libretaDe(10).estaLlena()).isTrue();
        }

        @Test
        void deberia_contar_las_que_tiene() {
            assertThat(libretaDe(3).cuantasTiene()).isEqualTo(3);
            assertThat(AddressBook.vacia().cuantasTiene()).isZero();
            assertThat(AddressBook.vacia().estaVacia()).isTrue();
        }

        /** El numero vive con nombre y no suelto en el codigo. */
        @Test
        void deberia_tener_el_tope_con_nombre() {
            assertThat(AddressBook.MAXIMO_DE_DIRECCIONES).isEqualTo(10);
        }
    }

    @Nested
    class LaPredeterminada {

        /** RN-099: la primera lo es sin que nadie lo pida. */
        @Test
        void deberia_serlo_la_primera_de_una_libreta_vacia() {
            assertThat(AddressBook.vacia().laSiguienteSeriaPredeterminada()).isTrue();
        }

        @Test
        void deberia_no_serlo_la_segunda() {
            assertThat(libretaDe(1).laSiguienteSeriaPredeterminada()).isFalse();
        }

        @Test
        void deberia_encontrarse_cuando_existe() {
            ShippingAddress marcada = DireccionesDePrueba.nueva(ALGUIEN).comoPredeterminada(true);
            AddressBook libreta = new AddressBook(List.of(DireccionesDePrueba.nueva(ALGUIEN), marcada));

            assertThat(libreta.predeterminada()).contains(marcada);
        }

        @Test
        void deberia_no_existir_en_una_libreta_vacia() {
            assertThat(AddressBook.vacia().predeterminada()).isEmpty();
        }
    }

    @Nested
    class ElRelevoAlQuitar {

        /**
         * Criterio 11: pasa a la mas reciente de las que quedan, que es la primera de la
         * lista descontando la que sale.
         */
        @Test
        void deberia_pasar_a_la_mas_reciente_de_las_que_quedan() {
            ShippingAddress laMasReciente = DireccionesDePrueba.nueva(ALGUIEN, AHORA);
            ShippingAddress intermedia = DireccionesDePrueba.nueva(ALGUIEN, AHORA.minus(Duration.ofDays(1)));
            ShippingAddress laMasVieja = DireccionesDePrueba.nueva(ALGUIEN, AHORA.minus(Duration.ofDays(2)))
                    .comoPredeterminada(true);

            AddressBook libreta = new AddressBook(List.of(laMasReciente, intermedia, laMasVieja));

            assertThat(libreta.relevoAlQuitar(laMasVieja.id())).contains(laMasReciente);
        }

        /** Quitar una que no era la predeterminada no mueve nada. */
        @Test
        void deberia_no_haber_relevo_si_la_que_sale_no_era_la_predeterminada() {
            ShippingAddress cualquiera = DireccionesDePrueba.nueva(ALGUIEN, AHORA);
            ShippingAddress marcada = DireccionesDePrueba.nueva(ALGUIEN, AHORA.minus(Duration.ofDays(1)))
                    .comoPredeterminada(true);

            AddressBook libreta = new AddressBook(List.of(cualquiera, marcada));

            assertThat(libreta.relevoAlQuitar(cualquiera.id())).isEmpty();
        }

        /** Criterio 12: era la ultima, la libreta se queda vacia y sin predeterminada. */
        @Test
        void deberia_no_haber_relevo_si_era_la_unica() {
            ShippingAddress unica = DireccionesDePrueba.nueva(ALGUIEN).comoPredeterminada(true);
            AddressBook libreta = new AddressBook(List.of(unica));

            assertThat(libreta.relevoAlQuitar(unica.id())).isEmpty();
        }

        @Test
        void deberia_no_haber_relevo_para_una_direccion_que_no_esta() {
            assertThat(libretaDe(2).relevoAlQuitar(ShippingAddressId.nuevo())).isEmpty();
        }
    }

    @Test
    void deberia_encontrar_una_direccion_por_su_identificador() {
        ShippingAddress buscada = DireccionesDePrueba.nueva(ALGUIEN);
        AddressBook libreta = new AddressBook(List.of(DireccionesDePrueba.nueva(ALGUIEN), buscada));

        assertThat(libreta.buscar(buscada.id())).contains(buscada);
        assertThat(libreta.buscar(ShippingAddressId.nuevo())).isEmpty();
    }

    /** La lista que entra no puede cambiar por debajo. */
    @Test
    void deberia_copiar_la_lista_que_recibe() {
        List<ShippingAddress> mutable = new ArrayList<>(List.of(DireccionesDePrueba.nueva(ALGUIEN)));
        AddressBook libreta = new AddressBook(mutable);

        mutable.clear();

        assertThat(libreta.cuantasTiene()).isEqualTo(1);
    }
}
