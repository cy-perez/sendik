package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.City;
import co.sendik.identity.model.Consent;
import co.sendik.identity.model.ConsentDocument;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.RefreshToken;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.UserCart;
import co.sendik.identity.port.out.UserFavorites;
import co.sendik.identity.port.out.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Criterio 22: el derecho a conocer.
 *
 * <p>Lo que se prueba aqui no es que el caso devuelva algo, sino que devuelva
 * <strong>todo</strong>. Un archivo de portabilidad al que le falta un campo pasa
 * igual de bien cualquier prueba de "responde 200" y sigue incumpliendo la Ley
 * 1581. Por eso hay una prueba que enumera los datos personales uno por uno, en
 * lugar de comprobar que el archivo no viene vacio: lo que se rompio de verdad
 * fue una ausencia, y una asercion generica no la habria visto.
 */
@ExtendWith(MockitoExtension.class)
class ExportUserDataUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");
    private static final LocalDate NACIMIENTO = LocalDate.of(1990, 3, 4);

    @Mock
    private UserRepository usuarios;

    @Mock
    private ConsentRepository consentimientos;

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private UserFavorites favoritos;

    @Mock
    private UserCart carrito;

    private ExportUserDataUseCase caso;
    private UserId usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new ExportUserDataUseCase(
                usuarios, consentimientos, refrescos, favoritos, carrito, Clock.fixed(AHORA, ZoneOffset.UTC));
        usuario = UserId.nuevo();
    }

    private User cuentaCon(@Nullable City ciudad, @Nullable Phone telefono) {
        return User.rehidratar(
                usuario,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(NACIMIENTO),
                ciudad,
                telefono,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                AHORA.minus(Duration.ofDays(10)),
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void hayCuentaCon(@Nullable City ciudad, @Nullable Phone telefono) {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(ciudad, telefono)));
        when(consentimientos.listarDe(usuario)).thenReturn(List.of());
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of());
    }

    private RefreshToken sesionEn(String navegador, String ipHash) {
        return RefreshToken.abrirSesion(
                usuario, "hash-de-token", AHORA.minus(Duration.ofDays(1)), RefreshToken.VIGENCIA, navegador, ipHash);
    }

    /**
     * La prueba que faltaba, y por la que ciudad y telefono no salian en el
     * archivo. Los campos se enumeran uno a uno a proposito.
     */
    @Test
    void deberia_incluir_todos_los_datos_personales_de_la_cuenta_criterio_22() {
        hayCuentaCon(new City("Medellin"), new Phone("3001234567"));

        UserDataExport.Cuenta cuenta = caso.execute(usuario).cuenta();

        assertThat(cuenta.id()).isEqualTo(usuario.toString());
        assertThat(cuenta.correo()).isEqualTo("ana@correo.co");
        assertThat(cuenta.nombre()).isEqualTo("Ana Maria");
        assertThat(cuenta.fechaDeNacimiento()).isEqualTo(NACIMIENTO);
        assertThat(cuenta.ciudad()).isEqualTo("Medellin");
        assertThat(cuenta.telefono()).isEqualTo("3001234567");
        assertThat(cuenta.idioma()).isEqualTo("es");
        assertThat(cuenta.estado()).isEqualTo("ACTIVE");
        assertThat(cuenta.correoVerificado()).isTrue();
        assertThat(cuenta.correoVerificadoEl()).isEqualTo(AHORA.minus(Duration.ofDays(10)));
        assertThat(cuenta.roles()).containsExactly("BUYER");
        assertThat(cuenta.creadaEl()).isEqualTo(AHORA.minus(Duration.ofDays(30)));
    }

    /**
     * Nulo, no clave ausente: "no tenemos tu ciudad" es una respuesta al derecho a
     * conocer, y un archivo que omite la clave no la da.
     */
    @Test
    void deberia_emitir_ciudad_y_telefono_vacios_cuando_la_persona_no_los_puso() {
        hayCuentaCon(null, null);

        UserDataExport.Cuenta cuenta = caso.execute(usuario).cuenta();

        assertThat(cuenta.ciudad()).isNull();
        assertThat(cuenta.telefono()).isNull();
    }

    /** La evidencia con su version y su fecha: es lo que prueba a que dijo que si. */
    @Test
    void deberia_incluir_la_evidencia_de_cada_consentimiento() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null, null)));
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of());
        when(consentimientos.listarDe(usuario))
                .thenReturn(List.of(
                        Consent.otorgar(usuario, ConsentDocument.TERMS, "2026-08-01", AHORA, "hash-de-ip"),
                        Consent.otorgar(usuario, ConsentDocument.PRIVACY, "2026-08-02", AHORA, "hash-de-ip")));

        assertThat(caso.execute(usuario).consentimientos())
                .extracting(UserDataExport.Consentimiento::documento, UserDataExport.Consentimiento::version)
                .containsExactly(tuple("TERMS", "2026-08-01"), tuple("PRIVACY", "2026-08-02"));
    }

    /**
     * Los favoritos son dato personal y entran en la descarga (HU-011,
     * docs/operacion/datos-personales.md). Vienen de catalog por un puerto: la tabla no
     * es de este contexto y este caso de uso no la lee.
     */
    @Test
    void deberia_incluir_los_favoritos_HU_011() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null, null)));
        when(consentimientos.listarDe(usuario)).thenReturn(List.of());
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of());
        when(favoritos.de(usuario))
                .thenReturn(List.of(new UserDataExport.Favorito("una-publicacion", AHORA.minus(Duration.ofDays(2)))));

        assertThat(caso.execute(usuario).favoritos())
                .singleElement()
                .extracting(UserDataExport.Favorito::publicacion, UserDataExport.Favorito::marcadoEl)
                .containsExactly("una-publicacion", AHORA.minus(Duration.ofDays(2)));
    }

    /**
     * Y el carrito. HU-015.
     *
     * <p>Esta prueba tampoco existia, por lo mismo: el doble se anadio para compilar. Sin
     * ella, quitar el carrito de {@code UserDataExport} dejaba la suite en verde y la descarga
     * incompleta, que es responder con un resumen a un derecho que es sobre lo que hay.
     */
    @Test
    void deberia_incluir_el_carrito_HU_015() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null, null)));
        when(consentimientos.listarDe(usuario)).thenReturn(List.of());
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of());
        when(carrito.de(usuario))
                .thenReturn(List.of(
                        new UserDataExport.ProductoEnCarrito("una-publicacion", AHORA.minus(Duration.ofDays(1)))));

        assertThat(caso.execute(usuario).carrito())
                .singleElement()
                .extracting(UserDataExport.ProductoEnCarrito::publicacion, UserDataExport.ProductoEnCarrito::agregadoEl)
                .containsExactly("una-publicacion", AHORA.minus(Duration.ofDays(1)));
    }

    @Test
    void deberia_incluir_las_sesiones_abiertas_con_su_navegador_y_sus_fechas() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.of(cuentaCon(null, null)));
        when(consentimientos.listarDe(usuario)).thenReturn(List.of());
        RefreshToken abierta = sesionEn("Firefox", "hash-de-ip");
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(abierta));

        assertThat(caso.execute(usuario).sesiones())
                .singleElement()
                .extracting(UserDataExport.Sesion::navegador, UserDataExport.Sesion::iniciada)
                .containsExactly("Firefox", abierta.createdAt());
    }

    /**
     * Ni el hash de la contrasena, ni el de ningun token, ni la IP de los
     * consentimientos: son secretos del sistema, no datos de la persona, y a quien
     * recibe el archivo un hash no le dice nada
     * (docs/operacion/datos-personales.md).
     */
    @Test
    void nunca_deberia_exponer_hashes_ni_la_ip() {
        when(usuarios.buscarPorId(usuario))
                .thenReturn(Optional.of(cuentaCon(new City("Medellin"), new Phone("3001234567"))));
        when(consentimientos.listarDe(usuario))
                .thenReturn(
                        List.of(Consent.otorgar(usuario, ConsentDocument.TERMS, "2026-08-01", AHORA, "hash-de-ip")));
        when(refrescos.listarSesionesActivasDe(usuario, AHORA)).thenReturn(List.of(sesionEn("Firefox", "hash-de-ip")));

        assertThat(caso.execute(usuario).toString()).doesNotContain("hash");
    }

    /** El archivo dice a que momento corresponde: sin eso no se sabe que se esta leyendo. */
    @Test
    void deberia_sellar_el_archivo_con_el_momento_en_que_se_genero() {
        hayCuentaCon(null, null);

        assertThat(caso.execute(usuario).generado()).isEqualTo(AHORA);
    }

    /**
     * Cerrar la cuenta no invalida el token de acceso ya emitido, que sigue
     * sirviendo hasta quince minutos (ADR-0003). Con la cuenta ya borrada la
     * peticion falla con su propio error y no con una referencia nula.
     */
    @Test
    void deberia_fallar_si_la_cuenta_ya_no_existe() {
        when(usuarios.buscarPorId(usuario)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> caso.execute(usuario)).isInstanceOf(AccountNoLongerExistsException.class);
    }
}
