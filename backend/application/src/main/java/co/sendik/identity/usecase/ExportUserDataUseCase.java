package co.sendik.identity.usecase;

import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserCart;
import co.sendik.identity.port.out.UserFavorites;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Clock;
import java.time.Instant;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reune todo lo que Sendik guarda de una persona. Criterio 22.
 *
 * <p>Es el derecho a conocer de la Ley 1581, y por eso se sirve entero y de una
 * vez en lugar de repartido por pantallas: lo que la ley reconoce no es poder
 * mirar el perfil, es poder llevarse lo que hay.
 *
 * <p>El identificador viene del token, nunca de la peticion. Un endpoint que
 * aceptara el identificador de quien exportar seria una forma de descargarse los
 * datos de cualquiera.
 */
public class ExportUserDataUseCase {

    private final UserRepository usuarios;
    private final ConsentRepository consentimientos;
    private final RefreshTokenRepository refrescos;
    private final UserFavorites favoritos;
    private final UserCart carrito;
    private final ShippingAddressRepository direcciones;
    private final ShippingAddressViews vistas;
    private final Clock reloj;

    public ExportUserDataUseCase(
            UserRepository usuarios,
            ConsentRepository consentimientos,
            RefreshTokenRepository refrescos,
            UserFavorites favoritos,
            UserCart carrito,
            ShippingAddressRepository direcciones,
            GeographicDivision division,
            Clock reloj) {
        this.usuarios = usuarios;
        this.consentimientos = consentimientos;
        this.refrescos = refrescos;
        this.favoritos = favoritos;
        this.carrito = carrito;
        this.direcciones = direcciones;
        this.vistas = new ShippingAddressViews(division);
        this.reloj = reloj;
    }

    /*
     * En una transaccion, y no por escribir: son seis lecturas -la cuenta, los
     * consentimientos, las sesiones, los favoritos desde HU-011, el carrito desde HU-015 y
     * la libreta de direcciones desde HU-016- y dos de ellas ademas atraviesan a otro
     * contexto y abririan la suya. Sin esto, el archivo de datos personales se arma con
     * instantaneas distintas, y para un artefacto de la Ley 1581 conviene que sea una.
     *
     * Sin readOnly = true, por lo mismo que ReadListingUseCase: presentation no declara
     * spring-tx, y al leer ese atributo para inyectar esta clase avisa de que no puede
     * resolverlo. Con -Xlint:all -Werror el aviso rompe la compilacion.
     */
    @Transactional
    public UserDataExport execute(UserId usuario) {
        Instant ahora = reloj.instant();

        // Puede no existir: cerrar la cuenta no invalida el token de acceso que ya
        // estaba emitido, y ese sigue sirviendo hasta quince minutos (ADR-0003).
        User cuenta = usuarios.buscarPorId(usuario).orElseThrow(AccountNoLongerExistsException::new);

        return new UserDataExport(
                ahora,
                new UserDataExport.Cuenta(
                        cuenta.id().toString(),
                        cuenta.email().value(),
                        cuenta.displayName().value(),
                        cuenta.birthDate().value(),
                        cuenta.city() == null ? null : cuenta.city().value(),
                        cuenta.phone() == null ? null : cuenta.phone().value(),
                        cuenta.locale().name().toLowerCase(java.util.Locale.ROOT),
                        cuenta.status().name(),
                        cuenta.tieneElCorreoVerificado(),
                        cuenta.emailVerifiedAt(),
                        cuenta.roles().stream().map(Role::name).sorted().toList(),
                        cuenta.createdAt()),
                consentimientos.listarDe(usuario).stream()
                        .map(consentimiento -> new UserDataExport.Consentimiento(
                                consentimiento.document().name(),
                                consentimiento.version(),
                                consentimiento.acceptedAt()))
                        .toList(),
                refrescos.listarSesionesActivasDe(usuario, ahora).stream()
                        .map(token ->
                                new UserDataExport.Sesion(token.userAgent(), token.createdAt(), token.expiresAt()))
                        .toList(),
                // Los favoritos son dato personal: dicen que le interesa a una persona
                // identificada (docs/operacion/datos-personales.md, HU-011). Vienen de
                // catalog por un puerto, porque la tabla no es de este contexto.
                favoritos.de(usuario),
                // El carrito tambien, y por una razon mas fuerte: no dice solo que le
                // interesa, dice que estuvo a punto de comprarlo (HU-015).
                carrito.de(usuario),
                // Y la libreta de direcciones, que es de este mismo contexto y no necesita
                // puerto (HU-016, ADR-0039). Sale entera y con los nombres del municipio y
                // del departamento, no con sus codigos: un «11001» no responde ninguna
                // pregunta que alguien pueda hacerse sobre sus propios datos.
                vistas.de(direcciones.deCuenta(usuario)).stream()
                        .map(direccion -> new UserDataExport.Direccion(
                                direccion.quienRecibe(),
                                direccion.telefono(),
                                direccion.departamentoNombre(),
                                direccion.municipioNombre(),
                                direccion.linea(),
                                direccion.complemento(),
                                direccion.indicaciones(),
                                direccion.codigoPostal(),
                                direccion.predeterminada(),
                                direccion.guardadaEl()))
                        .toList());
    }
}
