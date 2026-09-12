package co.sendik.identity.usecase;

import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.CloseConfirmationMismatchException;
import co.sendik.identity.model.User;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserCart;
import co.sendik.identity.port.out.UserFavorites;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.port.out.PublicFileStore;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cierra la cuenta y borra de ella todo lo que identifica a una persona.
 * Criterio 23 y derecho de supresion de la Ley 1581.
 *
 * <p><strong>Se anonimiza en el mismo gesto, no a los treinta dias.</strong> El
 * plazo del inventario existe para resolver pedidos en curso, y en Fase 1 no hay
 * pedidos ni facturas: no queda nada que la ley obligue a conservar, asi que
 * esperar solo dejaria datos vivos aguardando un proceso programado que todavia
 * no existe. Cuando existan pedidos, RN-009 obliga a bifurcar aqui: cierre
 * pendiente en {@code CLOSING} mientras haya alguno sin resolver.
 *
 * <p>Va en una transaccion porque las escrituras solo valen juntas. Anonimizar sin
 * revocar dejaria sesiones vivas de una cuenta que ya no tiene dueno, y su token de
 * refresco dura 30 dias.
 *
 * <p>El aviso se manda antes de anonimizar y no despues, por un motivo simple: al
 * terminar ya no se sabe a que direccion escribir.
 */
public class CloseAccountUseCase {

    private final UserRepository usuarios;
    private final RefreshTokenRepository refrescos;
    private final MailSender correo;
    private final PublicFileStore almacen;
    private final UserFavorites favoritos;
    private final UserCart carrito;
    private final ShippingAddressRepository direcciones;
    private final Clock reloj;

    public CloseAccountUseCase(
            UserRepository usuarios,
            RefreshTokenRepository refrescos,
            MailSender correo,
            PublicFileStore almacen,
            UserFavorites favoritos,
            UserCart carrito,
            ShippingAddressRepository direcciones,
            Clock reloj) {
        this.usuarios = usuarios;
        this.refrescos = refrescos;
        this.correo = correo;
        this.almacen = almacen;
        this.favoritos = favoritos;
        this.carrito = carrito;
        this.direcciones = direcciones;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(CloseAccountCommand comando) {
        Instant ahora = reloj.instant();

        User cuenta = usuarios.buscarPorId(comando.usuario()).orElseThrow(AccountNoLongerExistsException::new);

        verificarLaConfirmacion(cuenta, comando.confirmacion());

        // Antes de anonimizar: despues ya no hay direccion a la que escribir.
        correo.enviarAvisoDeCuentaCerrada(cuenta);

        refrescos.revocarTodasDe(cuenta.id(), ahora);

        // Los favoritos se van con la cuenta, y se borran en vez de anonimizarse. La fila
        // de users sobrevive vaciada porque hay integridad referencial que sostener; un
        // favorito sin dueno no le sirve a nadie y seguiria diciendo que a alguien le
        // interesaba eso (HU-011, docs/operacion/datos-personales.md).
        //
        // Dentro de la misma transaccion que lo demas y antes de anonimizar: si esto
        // fallara despues, la cuenta quedaria sin dueno y con los favoritos puestos.
        favoritos.borrarDe(cuenta.id());

        // Y el carrito con ellos, por la misma razon y con una de mas: no solo dice que le
        // interesaba, dice que estuvo a punto de comprarlo (HU-015, RN-095).
        carrito.borrarDe(cuenta.id());

        // Y las direcciones de entrega (RN-102). No es una limpieza de cortesia: es lo que
        // sostiene lo que `datos-personales.md` afirma sobre la ventana de quince minutos
        // del token de acceso, que es aceptable **porque** cuando se abre ya no queda dato
        // personal que ese token pueda alcanzar. Una direccion que sobreviviera al cierre
        // volveria falsa esa frase, que es justo la que el documento pone por escrito ante
        // una autoridad.
        //
        // Ademas, cuando quien recibe no era el titular, lo que quedaria vivo seria el
        // nombre y el telefono de un tercero que nunca abrio una cuenta (RN-104).
        direcciones.borrarDe(cuenta.id());

        usuarios.cerrarYAnonimizar(cuenta.id(), ahora);

        // La foto de perfil se borra del almacen, no solo la referencia de la fila.
        //
        // Anonimizar la fila y dejar el archivo donde estaba no es ejercer el
        // derecho de eliminacion: la imagen del rostro de alguien seguiria estando
        // ahi, y accesible por su direccion para quien la tuviera guardada
        // (Ley 1581, docs/operacion/datos-personales.md). Va despues de anonimizar
        // por el mismo orden que en el resto: si el borrado falla queda un archivo
        // huerfano, que se limpia; si fallara al contrario, quedaria una fila
        // apuntando a nada.
        if (cuenta.avatarKey() != null) {
            almacen.borrar(cuenta.avatarKey());
        }
    }

    /**
     * La persona escribe su propio correo. Se compara normalizado, como en el
     * ingreso: quien lo escribe con mayusculas no esta equivocandose de cuenta.
     */
    private static void verificarLaConfirmacion(User cuenta, String escrito) {
        if (!cuenta.email().value().equalsIgnoreCase(escrito.trim())) {
            throw new CloseConfirmationMismatchException();
        }
    }
}
