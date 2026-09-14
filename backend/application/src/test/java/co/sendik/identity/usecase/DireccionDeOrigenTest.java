package co.sendik.identity.usecase;

import static co.sendik.identity.usecase.LibretaEnMemoria.BOGOTA;
import static co.sendik.identity.usecase.LibretaEnMemoria.INEXISTENTE;
import static co.sendik.identity.usecase.LibretaEnMemoria.MEDELLIN;
import static co.sendik.identity.usecase.LibretaEnMemoria.SUPRIMIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.OriginAddressData;
import co.sendik.identity.dto.OriginAddressView;
import co.sendik.identity.dto.ProfileView;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.PhoneRequiredException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.City;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * La direccion de origen del vendedor. HU-017.
 *
 * <p>Lo que se prueba aqui es lo que deciden los casos de uso: que guardar exige telefono
 * en el perfil (criterio 5), que guardar dos veces deja uno (criterio 3), que un municipio
 * suprimido no se puede elegir pero si se sigue leyendo (RN-100 y criterio 13), que
 * guardar descarta la ciudad escrita a mano y borrar la deja vacia (criterios 11 y 12), y
 * que el perfil dice de donde sale la ciudad y si se puede editar (criterio 10).
 *
 * <p>Que la clave primaria sobre la cuenta convierta dos escrituras concurrentes en una
 * lo prueba {@code OriginAddressPersistenceTest} contra PostgreSQL.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DireccionDeOrigenTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T10:00:00Z");
    private static final Instant DESPUES = AHORA.plus(Duration.ofHours(2));

    @Mock
    private UserRepository usuarios;

    private OrigenEnMemoria origenes;
    private LibretaEnMemoria division;
    private UserId alguien;

    private ReadOriginAddressUseCase leer;
    private SaveOriginAddressUseCase guardar;
    private DeleteOriginAddressUseCase borrar;
    private ReadProfileViewUseCase perfil;

    @BeforeEach
    void prepararCasos() {
        origenes = new OrigenEnMemoria();
        division = new LibretaEnMemoria();
        alguien = UserId.nuevo();

        leer = new ReadOriginAddressUseCase(origenes, usuarios, division);
        guardar = new SaveOriginAddressUseCase(origenes, division, usuarios, Clock.fixed(AHORA, ZoneOffset.UTC));
        borrar = new DeleteOriginAddressUseCase(origenes, usuarios);
        perfil = new ReadProfileViewUseCase(usuarios, origenes, division);
    }

    private User cuentaCon(@Nullable City ciudad, @Nullable Phone telefono) {
        return User.rehidratar(
                alguien,
                new Email("ana@correo.co"),
                new DisplayName("Ana María"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                ciudad,
                telefono,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA.minus(Duration.ofDays(10)),
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void conCuenta(@Nullable City ciudad, @Nullable Phone telefono) {
        when(usuarios.buscarPorId(alguien)).thenReturn(Optional.of(cuentaCon(ciudad, telefono)));
    }

    private static OriginAddressData datos(MunicipalityCode municipio) {
        return new OriginAddressData(municipio.value(), "Carrera 15 # 93-47", "Local 3", null, "110221");
    }

    private OriginAddressView guardarEn(MunicipalityCode municipio) {
        return guardar.execute(new SaveOriginAddressCommand(alguien, datos(municipio)));
    }

    // --- Guardar ---

    @Nested
    class AlGuardar {

        @Test
        void deberia_guardar_el_origen_con_el_remitente_del_perfil() {
            conCuenta(null, new Phone("3001234567"));

            OriginAddressView vista = guardarEn(BOGOTA);

            assertThat(vista.municipioCodigo()).isEqualTo("11001");
            assertThat(vista.municipioNombre()).isEqualTo("Bogotá, D.C.");
            assertThat(vista.departamentoCodigo()).isEqualTo("11");
            assertThat(vista.departamentoNombre()).isEqualTo("Bogotá, D.C.");
            assertThat(vista.municipioActivo()).isTrue();
            assertThat(vista.linea()).isEqualTo("Carrera 15 # 93-47");
            assertThat(vista.complemento()).isEqualTo("Local 3");
            assertThat(vista.indicaciones()).isNull();
            assertThat(vista.codigoPostal()).isEqualTo("110221");
            assertThat(vista.remitenteNombre()).isEqualTo("Ana María");
            assertThat(vista.remitenteTelefono()).isEqualTo("3001234567");
            assertThat(vista.guardadaEl()).isEqualTo(AHORA);
            assertThat(leer.execute(alguien)).isPresent();
        }

        /** Criterio 5: un remitente sin telefono no es remitente. */
        @Test
        void deberia_rechazar_guardar_si_el_perfil_no_tiene_telefono() {
            conCuenta(null, null);

            assertThatThrownBy(() -> guardarEn(BOGOTA)).isInstanceOf(PhoneRequiredException.class);
            assertThat(origenes.cuantasFilas()).isZero();
        }

        /** Criterio 3: guardar otro reemplaza y nunca quedan dos. */
        @Test
        void deberia_reemplazar_el_que_habia_en_vez_de_agregar_otro() {
            conCuenta(null, new Phone("3001234567"));
            guardarEn(BOGOTA);

            OriginAddressView vista = guardarEn(MEDELLIN);

            assertThat(vista.municipioCodigo()).isEqualTo("05001");
            assertThat(origenes.cuantasFilas()).isEqualTo(1);
            assertThat(leer.execute(alguien))
                    .map(OriginAddressView::municipioNombre)
                    .contains("Medellín");
        }

        /** RN-100, criterio 7: uno que no existe y uno que el DANE suprimio se rechazan igual. */
        @Test
        void deberia_rechazar_un_municipio_inexistente_o_suprimido() {
            conCuenta(null, new Phone("3001234567"));

            assertThatThrownBy(() -> guardarEn(INEXISTENTE)).isInstanceOf(UnknownMunicipalityException.class);
            assertThatThrownBy(() -> guardarEn(SUPRIMIDO)).isInstanceOf(UnknownMunicipalityException.class);
            assertThat(origenes.cuantasFilas()).isZero();
        }

        /**
         * El orden de las comprobaciones: el telefono antes que el municipio. Sin telefono, la
         * respuesta correcta es «ve al perfil», no «elige otro municipio».
         */
        @Test
        void deberia_reclamar_el_telefono_antes_que_el_municipio() {
            conCuenta(null, null);

            assertThatThrownBy(() -> guardarEn(INEXISTENTE)).isInstanceOf(PhoneRequiredException.class);
        }

        /** Criterio 15: una cuenta cerrada con token vivo no escribe. */
        @Test
        void deberia_rechazar_si_la_cuenta_ya_no_existe() {
            when(usuarios.buscarPorId(alguien)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> guardarEn(BOGOTA)).isInstanceOf(AccountNoLongerExistsException.class);
        }

        /**
         * Criterio 11: guardar el origen descarta la ciudad escrita a mano (ADR-0042), con la
         * operacion minima y no reescribiendo la cuenta: `actualizar` desde una instantanea
         * resucitaria una fila que el cierre acabara de anonimizar.
         */
        @Test
        void deberia_descartar_la_ciudad_escrita_a_mano_sin_reescribir_la_cuenta() {
            conCuenta(new City("bogota"), new Phone("3001234567"));

            guardarEn(BOGOTA);

            verify(usuarios).limpiarCiudad(alguien);
            verify(usuarios, never()).actualizar(any());
        }

        @Test
        void no_deberia_tocar_el_perfil_si_no_habia_ciudad_escrita() {
            conCuenta(null, new Phone("3001234567"));

            guardarEn(BOGOTA);

            verify(usuarios, never()).limpiarCiudad(any());
            verify(usuarios, never()).actualizar(any());
        }

        @Test
        void deberia_admitir_que_falten_los_tres_opcionales_y_tratar_el_vacio_como_ausencia() {
            conCuenta(null, new Phone("3001234567"));

            OriginAddressView vista = guardar.execute(new SaveOriginAddressCommand(
                    alguien, new OriginAddressData(BOGOTA.value(), "Carrera 15 # 93-47", "", "  ", null)));

            assertThat(vista.complemento()).isNull();
            assertThat(vista.indicaciones()).isNull();
            assertThat(vista.codigoPostal()).isNull();
        }
    }

    // --- Leer ---

    @Nested
    class AlLeer {

        @Test
        void deberia_no_haber_nada_para_una_cuenta_sin_origen() {
            conCuenta(null, new Phone("3001234567"));

            assertThat(leer.execute(alguien)).isEmpty();
        }

        /** Criterio 15 tambien al leer: 401 y no un 204 que la pantalla leeria como «no tienes». */
        @Test
        void deberia_rechazar_la_lectura_si_la_cuenta_ya_no_existe() {
            when(usuarios.buscarPorId(alguien)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> leer.execute(alguien)).isInstanceOf(AccountNoLongerExistsException.class);
        }

        /** Criterio 13: un municipio que el DANE suprimio se sigue leyendo. */
        @Test
        void deberia_seguir_leyendo_un_origen_cuyo_municipio_se_suprimio() {
            conCuenta(null, new Phone("3001234567"));
            origenes.guardar(OriginAddress.nueva(
                    alguien, SUPRIMIDO, new AddressLine("Carrera 15 # 93-47"), null, null, null, AHORA));

            Optional<OriginAddressView> vista = leer.execute(alguien);

            assertThat(vista).isPresent();
            assertThat(vista.get().municipioActivo()).isFalse();
            assertThat(vista.get().municipioNombre()).isEqualTo("Un municipio que el DANE suprimio");
        }

        /** El telefono puede haberse quitado del perfil despues; el origen se lee igual. */
        @Test
        void deberia_leer_el_origen_aunque_el_perfil_ya_no_tenga_telefono() {
            conCuenta(null, new Phone("3001234567"));
            guardarEn(BOGOTA);
            conCuenta(null, null);

            assertThat(leer.execute(alguien))
                    .map(OriginAddressView::remitenteTelefono)
                    .isEmpty();
            assertThat(leer.execute(alguien)).isPresent();
        }
    }

    // --- Borrar ---

    @Nested
    class AlBorrar {

        @Test
        void deberia_borrar_y_no_fallar_al_repetirse() {
            conCuenta(null, new Phone("3001234567"));
            guardarEn(BOGOTA);

            borrar.execute(alguien);
            assertThat(leer.execute(alguien)).isEmpty();

            // Criterio 9: repetirse responde lo mismo, y «lo mismo» es que sigue sin haber.
            borrar.execute(alguien);
            assertThat(leer.execute(alguien)).isEmpty();
        }

        @Test
        void deberia_rechazar_si_la_cuenta_ya_no_existe() {
            when(usuarios.buscarPorId(alguien)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> borrar.execute(alguien)).isInstanceOf(AccountNoLongerExistsException.class);
        }

        /** Criterio 12: borrar no devuelve la ciudad de antes, porque se descarto al guardar. */
        @Test
        void no_deberia_tocar_el_perfil_al_borrar() {
            conCuenta(null, new Phone("3001234567"));
            guardarEn(BOGOTA);

            borrar.execute(alguien);

            verify(usuarios, never()).actualizar(any());
            verify(usuarios, never()).limpiarCiudad(any());
        }
    }

    // --- El perfil ---

    @Nested
    class ElPerfil {

        /** Criterio 10: sin origen, la ciudad es lo escrito y se edita. */
        @Test
        void deberia_mostrar_la_ciudad_escrita_a_mano_y_dejarla_editar_sin_origen() {
            conCuenta(new City("Cali"), null);

            ProfileView vista = perfil.execute(alguien);

            assertThat(vista.ciudad()).isEqualTo("Cali");
            assertThat(vista.ciudadEditable()).isTrue();
        }

        @Test
        void deberia_no_tener_ciudad_y_dejarla_editar_si_no_hay_nada() {
            conCuenta(null, null);

            ProfileView vista = perfil.execute(alguien);

            assertThat(vista.ciudad()).isNull();
            assertThat(vista.ciudadEditable()).isTrue();
        }

        /** Criterio 11: con origen, la ciudad es el municipio con su departamento y no se edita. */
        @Test
        void deberia_derivar_la_ciudad_del_origen_con_su_departamento_al_lado() {
            conCuenta(null, new Phone("3001234567"));
            guardarEn(MEDELLIN);

            ProfileView vista = perfil.execute(alguien);

            assertThat(vista.ciudad()).isEqualTo("Medellín, Antioquia");
            assertThat(vista.ciudadEditable()).isFalse();
        }

        /** Criterio 13 en el perfil: un municipio que el DANE suprimio se sigue leyendo. */
        @Test
        void deberia_seguir_derivando_la_ciudad_de_un_municipio_suprimido() {
            conCuenta(null, new Phone("3001234567"));
            origenes.guardar(OriginAddress.nueva(
                    alguien, SUPRIMIDO, new AddressLine("Carrera 15 # 93-47"), null, null, null, AHORA));

            ProfileView vista = perfil.execute(alguien);

            assertThat(vista.ciudad()).isEqualTo("Un municipio que el DANE suprimio, Antioquia");
            assertThat(vista.ciudadEditable()).isFalse();
        }

        @Test
        void deberia_rechazar_si_la_cuenta_ya_no_existe() {
            when(usuarios.buscarPorId(alguien)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> perfil.execute(alguien)).isInstanceOf(AccountNoLongerExistsException.class);
        }
    }

    /** Un reloj que avanza cuando la prueba lo pide, para ver que reemplazar conserva la creacion. */
    @Test
    void deberia_conservar_la_fecha_de_creacion_al_reemplazar() {
        conCuenta(null, new Phone("3001234567"));
        guardarEn(BOGOTA);
        SaveOriginAddressUseCase masTarde =
                new SaveOriginAddressUseCase(origenes, division, usuarios, Clock.fixed(DESPUES, ZoneId.of("UTC")));

        masTarde.execute(new SaveOriginAddressCommand(alguien, datos(MEDELLIN)));

        assertThat(leer.execute(alguien)).map(OriginAddressView::guardadaEl).contains(AHORA);
    }
}
