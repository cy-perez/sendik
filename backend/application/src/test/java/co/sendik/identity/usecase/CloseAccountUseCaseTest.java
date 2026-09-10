package co.sendik.identity.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.exception.CloseConfirmationMismatchException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.UserCart;
import co.sendik.identity.port.out.UserFavorites;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Criterio 23 y derecho de supresion de la Ley 1581. */
@ExtendWith(MockitoExtension.class)
class CloseAccountUseCaseTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    @Mock
    private UserRepository usuarios;

    @Mock
    private RefreshTokenRepository refrescos;

    @Mock
    private MailSender correo;

    @Mock
    private PublicFileStore almacen;

    @Mock
    private UserFavorites favoritos;

    @Mock
    private UserCart carrito;

    private CloseAccountUseCase caso;
    private User usuario;

    @BeforeEach
    void prepararCaso() {
        caso = new CloseAccountUseCase(
                usuarios, refrescos, correo, almacen, favoritos, carrito, Clock.fixed(AHORA, ZoneOffset.UTC));

        usuario = User.registrar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                UserLocale.ES,
                LocalDate.of(2026, 8, 17),
                AHORA.minus(Duration.ofDays(30)));
    }

    private void conCuenta() {
        when(usuarios.buscarPorId(usuario.id())).thenReturn(Optional.of(usuario));
    }

    @Test
    void deberia_cerrar_anonimizar_y_cortar_las_sesiones() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        verify(refrescos).revocarTodasDe(usuario.id(), AHORA);
        verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }

    /**
     * El aviso sale antes de anonimizar. Despues ya no hay direccion a la que
     * escribir, y ese correo es lo que permite a la persona reaccionar si no fue
     * ella quien lo pidio.
     */
    @Test
    void deberia_avisar_antes_de_borrar_el_correo() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        InOrder orden = inOrder(correo, usuarios);
        orden.verify(correo).enviarAvisoDeCuentaCerrada(usuario);
        orden.verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }

    /**
     * Cerrar no se deshace: la confirmacion escrita es lo unico que separa un clic
     * mal dado de perder el acceso.
     */
    @Test
    void no_deberia_cerrar_si_la_confirmacion_no_coincide_criterio_23() {
        conCuenta();

        assertThatThrownBy(() -> caso.execute(new CloseAccountCommand(usuario.id(), "otra@correo.co")))
                .isInstanceOf(CloseConfirmationMismatchException.class);

        verify(usuarios, never()).cerrarYAnonimizar(usuario.id(), AHORA);
        verifyNoInteractions(refrescos, correo);
    }

    @Test
    void no_deberia_cerrar_con_la_confirmacion_vacia() {
        conCuenta();

        assertThatThrownBy(() -> caso.execute(new CloseAccountCommand(usuario.id(), "   ")))
                .isInstanceOf(CloseConfirmationMismatchException.class);
    }

    // Quien escribe su correo con mayusculas no se esta equivocando de cuenta: se
    // compara normalizado, igual que en el ingreso.
    /**
     * Los favoritos se van con la cuenta. Son dato personal —dicen que le interesaba a
     * una persona identificada— y el derecho de supresion de la Ley 1581 los alcanza
     * (HU-011, docs/operacion/datos-personales.md).
     */
    @Test
    void deberia_borrar_los_favoritos_HU_011() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        verify(favoritos).borrarDe(usuario.id());
    }

    /**
     * Y se borran antes de anonimizar, dentro de la misma transaccion. Si esto fallara
     * despues, la cuenta quedaria sin dueno y con los favoritos puestos.
     */
    @Test
    void deberia_borrar_los_favoritos_antes_de_anonimizar() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "ana@correo.co"));

        InOrder orden = inOrder(favoritos, usuarios);
        orden.verify(favoritos).borrarDe(usuario.id());
        orden.verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }

    /** Una confirmacion que no coincide no toca nada, tampoco los favoritos. */
    @Test
    void no_deberia_tocar_los_favoritos_si_la_confirmacion_no_coincide() {
        conCuenta();

        assertThatThrownBy(() -> caso.execute(new CloseAccountCommand(usuario.id(), "otra@correo.co")))
                .isInstanceOf(CloseConfirmationMismatchException.class);

        verifyNoInteractions(favoritos);
    }

    @Test
    void deberia_aceptar_la_confirmacion_con_otras_mayusculas_y_espacios() {
        conCuenta();

        caso.execute(new CloseAccountCommand(usuario.id(), "  ANA@Correo.CO  "));

        verify(usuarios).cerrarYAnonimizar(usuario.id(), AHORA);
    }
}
