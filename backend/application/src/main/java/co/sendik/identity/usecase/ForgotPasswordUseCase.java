package co.sendik.identity.usecase;

import co.sendik.identity.dto.ForgotPasswordCommand;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenPurpose;
import co.sendik.identity.model.User;
import co.sendik.identity.model.VerificationToken;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationTokenRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pide el enlace para poner una contrasena nueva. Criterios 18 y 19.
 *
 * <p><strong>Lo que este caso de uso no hace es lo que lo define.</strong> No
 * lanza nunca. No devuelve nada. Un correo registrado y uno que no lo esta
 * terminan exactamente igual, y esa es la regla entera del criterio 19: si el
 * formulario respondiera distinto, cualquiera podria averiguar quien tiene cuenta
 * en Sendik escribiendo correos uno por uno.
 *
 * <p>Por eso el {@code Optional} se resuelve con {@code ifPresent} y no con un
 * {@code orElseThrow}: cuando no hay cuenta, aqui no pasa nada, y el borde
 * responde lo mismo que si hubiera pasado todo.
 *
 * <p>El correo no se entrega en el hilo de la peticion: se encola (ADR-0031), asi que
 * la diferencia de tiempo entre los dos caminos es la de crear una tarea y no la de
 * esperar al proveedor. Sin eso, el criterio 19 se cumpliria en el texto de la
 * respuesta y se incumpliria con un cronometro. Es un residuo conocido y aceptado en
 * la ADR: encolar cuesta decenas de milisegundos y **tiene que ocurrir antes de
 * responder**, porque diferirlo a un hilo propio es exactamente el fallo que ADR-0031
 * viene a corregir -Cloud Run congela el contenedor en cuanto la respuesta sale-.
 *
 * <p>No hay limite de reenvios propio como en el criterio 8: aqui no se puede
 * informar de uno sin revelar que la cuenta existe. Quien protege este endpoint es
 * el limite de peticiones por origen del borde.
 */
public class ForgotPasswordUseCase {

    private final UserRepository usuarios;
    private final VerificationTokenRepository tokens;
    private final TokenGenerator generadorDeTokens;
    private final MailSender correo;
    private final Clock reloj;

    public ForgotPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        this.usuarios = usuarios;
        this.tokens = tokens;
        this.generadorDeTokens = generadorDeTokens;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Transactional
    public void execute(ForgotPasswordCommand comando) {
        Instant ahora = reloj.instant();

        // El correo se normaliza igual que en el ingreso: quien escribio su
        // direccion con mayusculas tiene que recibir su enlace.
        Optional<User> encontrado = usuarios.buscarPorCorreo(new Email(comando.email()));

        encontrado.ifPresent(usuario -> emitirYEnviar(usuario, ahora));
    }

    private void emitirYEnviar(User usuario, Instant ahora) {
        TokenGenerator.GeneratedToken generado = generadorDeTokens.generar();

        tokens.guardar(VerificationToken.emitir(
                usuario.id(),
                TokenPurpose.PASSWORD_RESET,
                generado.hash(),
                ahora,
                VerificationToken.VIGENCIA_RESTABLECIMIENTO));

        correo.enviarRestablecimientoDeContrasena(usuario, generado.valorEnClaro());
    }
}
