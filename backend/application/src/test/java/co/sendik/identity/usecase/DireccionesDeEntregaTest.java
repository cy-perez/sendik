package co.sendik.identity.usecase;

import static co.sendik.identity.usecase.LibretaEnMemoria.BOGOTA;
import static co.sendik.identity.usecase.LibretaEnMemoria.INEXISTENTE;
import static co.sendik.identity.usecase.LibretaEnMemoria.MEDELLIN;
import static co.sendik.identity.usecase.LibretaEnMemoria.SUPRIMIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.EditShippingAddressCommand;
import co.sendik.identity.dto.RemoveShippingAddressCommand;
import co.sendik.identity.dto.SetDefaultAddressCommand;
import co.sendik.identity.dto.ShippingAddressData;
import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.AddressBookFullException;
import co.sendik.identity.exception.AddressNotFoundException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.AddressBook;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.RecipientName;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * La libreta de direcciones de entrega. HU-016.
 *
 * <p>Lo que se prueba aqui es lo que deciden los casos de uso: que la primera direccion
 * nace predeterminada (RN-099), que el relevo al borrar va a la mas reciente de las que
 * quedan (criterio 11), que el tope rechaza (RN-101), que un municipio suprimido no se
 * puede elegir pero si se puede seguir leyendo (RN-100 y criterio 23), y que una direccion
 * ajena responde igual que una inventada (criterio 15).
 *
 * <p>Que la consulta ordene bien contra PostgreSQL y que el indice unico parcial impida dos
 * predeterminadas lo prueba {@code ShippingAddressPersistenceTest}: aqui la libreta es de
 * memoria y reproduce el contrato, no el motor.
 *
 * <p>El reloj lo mueve la prueba a mano, como en {@code CarritoTest}: el relevo depende de
 * cual se guardo antes, y con un reloj real dos direcciones guardadas seguidas tendrian
 * fechas distintas por accidente en vez de a proposito.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DireccionesDeEntregaTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");

    @Mock
    private UserRepository usuarios;

    private Instant reloj = AHORA;
    private LibretaEnMemoria libreta;
    private UserId alguien;
    private UserId otraPersona;

    private AddShippingAddressUseCase agregar;
    private EditShippingAddressUseCase editar;
    private RemoveShippingAddressUseCase quitar;
    private SetDefaultShippingAddressUseCase marcar;
    private ListShippingAddressesUseCase listar;

    @BeforeEach
    void prepararCasos() {
        reloj = AHORA;
        libreta = new LibretaEnMemoria();
        alguien = UserId.nuevo();
        otraPersona = UserId.nuevo();

        conCuentaViva(alguien);
        conCuentaViva(otraPersona);

        Clock movible = new RelojMovible();
        agregar = new AddShippingAddressUseCase(libreta, libreta, usuarios, movible);
        editar = new EditShippingAddressUseCase(libreta, libreta, usuarios, movible);
        quitar = new RemoveShippingAddressUseCase(libreta, usuarios);
        marcar = new SetDefaultShippingAddressUseCase(libreta, usuarios);
        listar = new ListShippingAddressesUseCase(libreta, libreta);
    }

    // --- Crear ---

    @Nested
    class AlGuardarUnaDireccionNueva {

        @Test
        void deberia_devolverla_con_el_nombre_del_municipio_y_del_departamento() {
            ShippingAddressView vista = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            assertThat(vista.quienRecibe()).isEqualTo("Ana María Ruiz");
            assertThat(vista.telefono()).isEqualTo("3001234567");
            assertThat(vista.municipioCodigo()).isEqualTo("11001");
            assertThat(vista.municipioNombre()).isEqualTo("Bogotá, D.C.");
            assertThat(vista.departamentoCodigo()).isEqualTo("11");
            assertThat(vista.departamentoNombre()).isEqualTo("Bogotá, D.C.");
            assertThat(vista.linea()).isEqualTo("Calle 45 # 12-34");
            assertThat(vista.guardadaEl()).isEqualTo(AHORA);
        }

        /** RN-099: sin que nadie lo pida. */
        @Test
        void deberia_dejar_la_primera_como_predeterminada() {
            ShippingAddressView primera = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            assertThat(primera.predeterminada()).isTrue();
        }

        @Test
        void deberia_dejar_la_segunda_sin_marcar() {
            agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            ShippingAddressView segunda = agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));

            assertThat(segunda.predeterminada()).isFalse();
        }

        /**
         * Vacio y ausente significan lo mismo. Sin esto, vaciar el complemento desde un
         * formulario seria imposible: llegaria como cadena vacia y se guardaria como algo.
         */
        @Test
        void deberia_tratar_los_opcionales_vacios_como_ausentes() {
            ShippingAddressView vista = agregar.execute(new AddShippingAddressCommand(
                    alguien,
                    new ShippingAddressData(
                            "Ana María Ruiz", "3001234567", "11001", "Calle 45 # 12-34", "  ", "", "")));

            assertThat(vista.complemento()).isNull();
            assertThat(vista.indicaciones()).isNull();
            assertThat(vista.codigoPostal()).isNull();
        }

        /** RN-100. */
        @Test
        void deberia_rechazar_un_municipio_que_no_existe() {
            assertThatThrownBy(() -> agregar.execute(new AddShippingAddressCommand(alguien, completa(INEXISTENTE))))
                    .isInstanceOf(UnknownMunicipalityException.class);
        }

        /** RN-100 y criterio 23: suprimido por el DANE no se puede elegir. */
        @Test
        void deberia_rechazar_un_municipio_suprimido() {
            assertThatThrownBy(() -> agregar.execute(new AddShippingAddressCommand(alguien, completa(SUPRIMIDO))))
                    .isInstanceOf(UnknownMunicipalityException.class);
        }

        /** RN-101, criterio 8: se rechaza y no se descarta la mas antigua en silencio. */
        @Test
        void deberia_rechazar_la_que_pasa_del_tope() {
            llenarLaLibretaDe(alguien);

            assertThatThrownBy(() -> agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA))))
                    .isInstanceOf(AddressBookFullException.class)
                    .hasMessageContaining(String.valueOf(AddressBook.MAXIMO_DE_DIRECCIONES));

            assertThat(listar.execute(alguien)).hasSize(AddressBook.MAXIMO_DE_DIRECCIONES);
        }

        /**
         * El token de acceso sobrevive quince minutos al cierre (ADR-0003), y lo que
         * quedaria vivo aqui es donde vive una persona.
         */
        @Test
        void deberia_rechazar_a_una_cuenta_que_ya_se_cerro() {
            conCuentaCerrada(alguien);

            assertThatThrownBy(() -> agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA))))
                    .isInstanceOf(AccountNoLongerExistsException.class);
        }

        /**
         * El municipio se comprueba antes que el tope: al reves, una libreta llena
         * respondera «llena» para un municipio inventado y «desconocido» para uno real.
         */
        @Test
        void deberia_comprobar_el_municipio_antes_que_el_tope() {
            llenarLaLibretaDe(alguien);

            assertThatThrownBy(() -> agregar.execute(new AddShippingAddressCommand(alguien, completa(INEXISTENTE))))
                    .isInstanceOf(UnknownMunicipalityException.class);
        }
    }

    // --- Editar ---

    @Nested
    class AlEditarla {

        /** Criterio 9. */
        @Test
        void deberia_cambiar_esa_y_no_crear_una_segunda() {
            ShippingAddressView guardada = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            ShippingAddressView editada = editar.execute(new EditShippingAddressCommand(
                    alguien,
                    ShippingAddressId.de(guardada.id()),
                    new ShippingAddressData(
                            "Carlos Pérez", "3109876543", "05001", "Carrera 70 # 45-12", null, null, null)));

            assertThat(editada.id()).isEqualTo(guardada.id());
            assertThat(editada.quienRecibe()).isEqualTo("Carlos Pérez");
            assertThat(editada.municipioNombre()).isEqualTo("Medellín");
            assertThat(listar.execute(alguien)).hasSize(1);
        }

        @Test
        void deberia_conservar_la_marca_de_predeterminada() {
            ShippingAddressView primera = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            ShippingAddressView editada = editar.execute(
                    new EditShippingAddressCommand(alguien, ShippingAddressId.de(primera.id()), completa(MEDELLIN)));

            assertThat(editada.predeterminada()).isTrue();
        }

        /** Criterio 15: 404 y no 403, y por eso la misma excepcion que una inventada. */
        @Test
        void deberia_no_encontrar_la_direccion_de_otra_persona() {
            ShippingAddressView ajena = agregar.execute(new AddShippingAddressCommand(otraPersona, completa(BOGOTA)));

            assertThatThrownBy(() -> editar.execute(new EditShippingAddressCommand(
                            alguien, ShippingAddressId.de(ajena.id()), completa(MEDELLIN))))
                    .isInstanceOf(AddressNotFoundException.class);
        }

        /**
         * El adaptador real no toca `is_default` al reescribir los campos, y el doble tampoco:
         * si lo pisara, esta prueba pasaria en verde y editar moveria la predeterminada en
         * produccion sin que nadie lo pidiera.
         */
        @Test
        void deberia_no_mover_la_predeterminada_al_editar_otra() {
            ShippingAddressView predeterminada =
                    agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            avanzarUnDia();
            ShippingAddressView otra = agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));

            editar.execute(new EditShippingAddressCommand(alguien, ShippingAddressId.de(otra.id()), completa(BOGOTA)));

            assertThat(listar.execute(alguien))
                    .filteredOn(ShippingAddressView::predeterminada)
                    .extracting(ShippingAddressView::id)
                    .containsExactly(predeterminada.id());
        }

        @Test
        void deberia_no_encontrar_una_direccion_inventada() {
            assertThatThrownBy(() -> editar.execute(
                            new EditShippingAddressCommand(alguien, ShippingAddressId.nuevo(), completa(BOGOTA))))
                    .isInstanceOf(AddressNotFoundException.class);
        }

        @Test
        void deberia_rechazar_a_una_cuenta_que_ya_se_cerro() {
            ShippingAddressView guardada = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            conCuentaCerrada(alguien);

            assertThatThrownBy(() -> editar.execute(new EditShippingAddressCommand(
                            alguien, ShippingAddressId.de(guardada.id()), completa(MEDELLIN))))
                    .isInstanceOf(AccountNoLongerExistsException.class);
        }

        /** Criterio 23: se puede leer con el municipio suprimido, pero no volver a guardarlo. */
        @Test
        void deberia_rechazar_que_se_guarde_sobre_un_municipio_suprimido() {
            ShippingAddressView guardada = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            assertThatThrownBy(() -> editar.execute(new EditShippingAddressCommand(
                            alguien, ShippingAddressId.de(guardada.id()), completa(SUPRIMIDO))))
                    .isInstanceOf(UnknownMunicipalityException.class);
        }
    }

    // --- Quitar ---

    @Nested
    class AlQuitarla {

        @Test
        void deberia_quitarla_y_dejar_el_resto_intacto() {
            ShippingAddressView una = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            avanzarUnDia();
            ShippingAddressView otra = agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));

            quitar.execute(new RemoveShippingAddressCommand(alguien, ShippingAddressId.de(otra.id())));

            assertThat(listar.execute(alguien))
                    .extracting(ShippingAddressView::id)
                    .containsExactly(una.id());
            assertThat(listar.execute(alguien).getFirst().predeterminada()).isTrue();
        }

        /** Criterio 11: pasa a la mas reciente de las que quedan. */
        @Test
        void deberia_pasar_la_predeterminada_a_la_mas_reciente_de_las_que_quedan() {
            ShippingAddressView laPredeterminada =
                    agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            avanzarUnDia();
            agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));
            avanzarUnDia();
            ShippingAddressView laMasReciente =
                    agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            quitar.execute(new RemoveShippingAddressCommand(alguien, ShippingAddressId.de(laPredeterminada.id())));

            assertThat(listar.execute(alguien))
                    .filteredOn(ShippingAddressView::predeterminada)
                    .extracting(ShippingAddressView::id)
                    .containsExactly(laMasReciente.id());
        }

        /** Criterio 12: era la ultima, y la libreta se queda vacia y sin predeterminada. */
        @Test
        void deberia_dejar_la_libreta_vacia_al_quitar_la_ultima() {
            ShippingAddressView unica = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            quitar.execute(new RemoveShippingAddressCommand(alguien, ShippingAddressId.de(unica.id())));

            assertThat(listar.execute(alguien)).isEmpty();
        }

        /** Criterio 14: un reintento o dos pestanas no tienen por que distinguirse. */
        @Test
        void deberia_poder_repetirse_sin_fallar() {
            ShippingAddressView unica = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            ShippingAddressId id = ShippingAddressId.de(unica.id());

            quitar.execute(new RemoveShippingAddressCommand(alguien, id));

            assertThatCode(() -> quitar.execute(new RemoveShippingAddressCommand(alguien, id)))
                    .doesNotThrowAnyException();
        }

        /** La de otra persona no se borra, y desde fuera es igual que una inventada. */
        @Test
        void deberia_no_tocar_la_direccion_de_otra_persona() {
            ShippingAddressView ajena = agregar.execute(new AddShippingAddressCommand(otraPersona, completa(BOGOTA)));

            quitar.execute(new RemoveShippingAddressCommand(alguien, ShippingAddressId.de(ajena.id())));

            assertThat(listar.execute(otraPersona)).hasSize(1);
        }

        @Test
        void deberia_rechazar_a_una_cuenta_que_ya_se_cerro() {
            ShippingAddressView unica = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            conCuentaCerrada(alguien);

            assertThatThrownBy(() ->
                            quitar.execute(new RemoveShippingAddressCommand(alguien, ShippingAddressId.de(unica.id()))))
                    .isInstanceOf(AccountNoLongerExistsException.class);
        }
    }

    // --- La predeterminada ---

    @Nested
    class AlElegirLaPredeterminada {

        /** Criterio 13: la anterior deja de serlo en el mismo acto. */
        @Test
        void deberia_dejar_exactamente_una() {
            agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            avanzarUnDia();
            ShippingAddressView segunda = agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));

            marcar.execute(new SetDefaultAddressCommand(alguien, ShippingAddressId.de(segunda.id())));

            assertThat(listar.execute(alguien))
                    .filteredOn(ShippingAddressView::predeterminada)
                    .extracting(ShippingAddressView::id)
                    .containsExactly(segunda.id());
        }

        @Test
        void deberia_rechazar_a_una_cuenta_que_ya_se_cerro() {
            ShippingAddressView unica = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            conCuentaCerrada(alguien);

            assertThatThrownBy(() ->
                            marcar.execute(new SetDefaultAddressCommand(alguien, ShippingAddressId.de(unica.id()))))
                    .isInstanceOf(AccountNoLongerExistsException.class);
        }

        @Test
        void deberia_no_cambiar_nada_al_marcar_la_que_ya_lo_era() {
            ShippingAddressView unica = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));

            marcar.execute(new SetDefaultAddressCommand(alguien, ShippingAddressId.de(unica.id())));

            assertThat(listar.execute(alguien).getFirst().predeterminada()).isTrue();
        }

        @Test
        void deberia_no_encontrar_la_direccion_de_otra_persona() {
            ShippingAddressView ajena = agregar.execute(new AddShippingAddressCommand(otraPersona, completa(BOGOTA)));

            assertThatThrownBy(() ->
                            marcar.execute(new SetDefaultAddressCommand(alguien, ShippingAddressId.de(ajena.id()))))
                    .isInstanceOf(AddressNotFoundException.class);
        }
    }

    // --- Listar ---

    @Nested
    class AlLeerLaLibreta {

        @Test
        void deberia_devolverla_de_la_mas_reciente_a_la_mas_antigua() {
            ShippingAddressView primera = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            avanzarUnDia();
            ShippingAddressView segunda = agregar.execute(new AddShippingAddressCommand(alguien, completa(MEDELLIN)));

            assertThat(listar.execute(alguien))
                    .extracting(ShippingAddressView::id)
                    .containsExactly(segunda.id(), primera.id());
        }

        @Test
        void deberia_estar_vacia_cuando_no_hay_ninguna() {
            assertThat(listar.execute(alguien)).isEmpty();
        }

        @Test
        void deberia_no_devolver_las_de_otra_persona() {
            agregar.execute(new AddShippingAddressCommand(otraPersona, completa(BOGOTA)));

            assertThat(listar.execute(alguien)).isEmpty();
        }

        /**
         * Criterio 23: el DANE suprime un municipio y la direccion guardada se sigue
         * leyendo, con su nombre. Lo que cambia es que al editarla hay que elegir otro, y
         * eso lo dice {@code municipioActivo}.
         */
        @Test
        void deberia_seguir_leyendo_una_direccion_sobre_un_municipio_suprimido() {
            ShippingAddressView guardada = agregar.execute(new AddShippingAddressCommand(alguien, completa(BOGOTA)));
            libreta.guardar(libreta.buscar(ShippingAddressId.de(guardada.id()), alguien)
                    .orElseThrow()
                    .con(
                            new RecipientName("Ana María Ruiz"),
                            new Phone("3001234567"),
                            SUPRIMIDO,
                            new AddressLine("Calle 45 # 12-34"),
                            null,
                            null,
                            null,
                            AHORA));

            List<ShippingAddressView> vistas = listar.execute(alguien);

            assertThat(vistas).hasSize(1);
            assertThat(vistas.getFirst().municipioNombre()).isEqualTo("Un municipio que el DANE suprimio");
            assertThat(vistas.getFirst().municipioActivo()).isFalse();
        }
    }

    // --- Ayudantes ---

    private static ShippingAddressData completa(MunicipalityCode municipio) {
        return new ShippingAddressData(
                "Ana María Ruiz",
                "3001234567",
                municipio.value(),
                "Calle 45 # 12-34",
                "Apto 802",
                "El timbre no sirve",
                "110111");
    }

    private void llenarLaLibretaDe(UserId cuenta) {
        for (int i = 0; i < AddressBook.MAXIMO_DE_DIRECCIONES; i++) {
            agregar.execute(new AddShippingAddressCommand(cuenta, completa(BOGOTA)));
            avanzarUnDia();
        }
    }

    private void avanzarUnDia() {
        reloj = reloj.plus(Duration.ofDays(1));
    }

    private void conCuentaViva(UserId id) {
        when(usuarios.buscarPorId(id))
                .thenReturn(Optional.of(User.registrar(
                        id,
                        new Email("ana@correo.co"),
                        new DisplayName("Ana Maria"),
                        new BirthDate(LocalDate.of(1990, 3, 4)),
                        UserLocale.ES,
                        LocalDate.of(2026, 9, 11),
                        AHORA.minus(Duration.ofDays(30)))));
    }

    private void conCuentaCerrada(UserId id) {
        when(usuarios.buscarPorId(id)).thenReturn(Optional.empty());
    }

    /** Un reloj que la prueba mueve a mano, para fijar cual se guardo antes. */
    private final class RelojMovible extends Clock {

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return reloj;
        }
    }
}
