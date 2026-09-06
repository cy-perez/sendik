package co.sendik.identity.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.sendik.identity.config.MailProperties;
import co.sendik.identity.config.VerificationProperties;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.shared.config.AppProperties;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * El envio real de los correos transaccionales (ADR-0012).
 *
 * <p>{@link ResendMailSenderTest} cubre la validacion de la clave al construirse.
 * Esto cubre lo otro: que el mensaje sale, que sale con lo que debe llevar dentro y
 * que un fallo del proveedor no se propaga.
 *
 * <p>Corre contra un servidor local, que es exactamente para lo que
 * {@code sendik.mail.api-url} es parametrizable —lo dice su propio Javadoc en
 * {@link MailProperties}—. Sin esto, los diez mensajes del sistema no tenian
 * ninguna prueba: el unico modo de descubrir que uno llevaba el enlace equivocado
 * o el asunto en el idioma equivocado era que alguien lo recibiera.
 */
class ResendMailSenderEnvioTest {

    private static final Instant AHORA = Instant.parse("2026-08-20T15:00:00Z");
    private static final String CLAVE = "re_una_clave_de_verdad";

    private HttpServer servidor;
    private final List<String> cuerposRecibidos = new CopyOnWriteArrayList<>();
    private final List<String> autorizaciones = new CopyOnWriteArrayList<>();

    private int codigo = 200;

    /**
     * Cuantas respuestas mas deben fallar con {@link #codigo} antes de volver a 200.
     *
     * <p>Es lo que permite probar que un fallo transitorio se reintenta y acaba saliendo,
     * sin lo cual solo se puede probar "siempre bien" o "siempre mal", y el reintento no se
     * distingue de no tenerlo.
     */
    private final AtomicInteger respuestasFallidas = new AtomicInteger();

    /**
     * Cuantas conexiones mas se cortan a media respuesta.
     *
     * <p>Simula el {@code ResourceAccessException} que se llevo un correo en `dev`: no hay
     * respuesta HTTP que interpretar, se corta el hilo. Se hace cortando la conexion y no
     * apagando el servidor, porque apagandolo no habria adonde reintentar.
     */
    private final AtomicInteger cortesPendientes = new AtomicInteger();

    @BeforeEach
    void levantarServidor() throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/emails", intercambio -> {
            cuerposRecibidos.add(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String autorizacion = intercambio.getRequestHeaders().getFirst("Authorization");
            autorizaciones.add(autorizacion == null ? "" : autorizacion);

            if (cortesPendientes.getAndUpdate(quedan -> quedan > 0 ? quedan - 1 : 0) > 0) {
                // Se anuncia un cuerpo y no se manda: el cliente se queda esperando bytes
                // que no llegan y falla con un error de entrada/salida.
                intercambio.sendResponseHeaders(200, 100);
                intercambio.close();
                return;
            }

            int estado = respuestasFallidas.getAndUpdate(quedan -> quedan > 0 ? quedan - 1 : 0) > 0 ? codigo : 200;

            byte[] respuesta = "{\"id\":\"1\"}".getBytes(StandardCharsets.UTF_8);
            intercambio.sendResponseHeaders(estado, respuesta.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(respuesta);
            }
        });
        servidor.start();
    }

    @AfterEach
    void apagarServidor() {
        servidor.stop(0);
    }

    private AppProperties app() {
        return new AppProperties(
                URI.create("https://sendik.co"),
                URI.create("https://sendik.co/api/v1"),
                "soporte@sendik.co",
                List.of("https://sendik.co"),
                ZoneId.of("America/Bogota"));
    }

    private ResendMailSender transporte() {
        MailProperties correo = new MailProperties(
                "no-responder@sendik.co",
                CLAVE,
                URI.create("http://127.0.0.1:" + servidor.getAddress().getPort() + "/emails"),
                "/verificar-correo",
                "/restablecer-contrasena",
                "/confirmar-correo-nuevo");

        return new ResendMailSender(
                new VerificationLink(app(), correo),
                app(),
                new VerificationProperties(2),
                new ResendMailTransport(correo));
    }

    private User cuenta(UserLocale idioma) {
        return User.rehidratar(
                UserId.nuevo(),
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                // Sin foto de perfil: esta prueba no trata de eso.
                null,
                idioma,
                UserStatus.ACTIVE,
                AHORA.minus(Duration.ofDays(1)),
                EnumSet.of(Role.BUYER),
                AHORA.minus(Duration.ofDays(30)));
    }

    private String unicoCuerpo() {
        assertThat(cuerposRecibidos).hasSize(1);
        return cuerposRecibidos.getFirst();
    }

    @Test
    void deberia_enviar_la_verificacion_con_su_enlace_al_destinatario() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token-de-verificacion");

        assertThat(unicoCuerpo())
                .contains("\"to\":[\"ana@correo.co\"]")
                .contains("\"from\":\"no-responder@sendik.co\"")
                .contains("https://sendik.co/verificar-correo?token=token-de-verificacion");
    }

    /** La clave viaja en la cabecera, nunca en el cuerpo ni en la direccion. */
    @Test
    void deberia_autenticarse_con_la_clave_en_la_cabecera() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");

        assertThat(autorizaciones).containsExactly("Bearer " + CLAVE);
        assertThat(unicoCuerpo()).doesNotContain(CLAVE);
    }

    /**
     * El idioma sale de la cuenta, no del navegador de quien disparo la accion: el
     * correo de bloqueo lo provoca a veces un tercero intentando entrar, y quien lo
     * recibe es su titular.
     */
    @Test
    void deberia_escribir_en_el_idioma_de_la_cuenta() {
        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");
        assertThat(unicoCuerpo()).contains("Confirma tu correo en Sendik");

        cuerposRecibidos.clear();

        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.EN), "token");
        assertThat(unicoCuerpo()).contains("Confirm your email on Sendik");
    }

    /** Cada enlace a su pantalla: un token de restablecimiento no se canjea en la de verificacion. */
    @Test
    void deberia_mandar_cada_enlace_a_su_propia_pantalla() {
        ResendMailSender transporte = transporte();

        transporte.enviarRestablecimientoDeContrasena(cuenta(UserLocale.ES), "token-de-reset");
        assertThat(unicoCuerpo()).contains("/restablecer-contrasena?token=token-de-reset");

        cuerposRecibidos.clear();

        transporte.enviarConfirmacionDeCorreoNuevo(cuenta(UserLocale.ES), new Email("nuevo@correo.co"), "token-nuevo");
        assertThat(unicoCuerpo())
                .contains("/confirmar-correo-nuevo?token=token-nuevo")
                .contains("\"to\":[\"nuevo@correo.co\"]");
    }

    /**
     * La confirmacion del correo nuevo va al correo nuevo y el aviso de que cambio va
     * al anterior. Invertirlos deja a la persona sin poder confirmar y avisa al
     * buzon que ya no usa.
     */
    @Test
    void deberia_avisar_al_correo_anterior_de_que_la_cuenta_cambio() {
        transporte().enviarAvisoDeCorreoCambiado(cuenta(UserLocale.ES), new Email("viejo@correo.co"));

        assertThat(unicoCuerpo()).contains("\"to\":[\"viejo@correo.co\"]");
    }

    /** RN-006: el aviso de bloqueo dice a que hora se desbloquea, en la zona del proyecto. */
    @Test
    void deberia_decir_a_que_hora_se_desbloquea_la_cuenta() {
        transporte().enviarAvisoDeCuentaBloqueada(cuenta(UserLocale.ES), Instant.parse("2026-08-20T20:30:00Z"));

        // 20:30 UTC son 15:30 en America/Bogota.
        assertThat(unicoCuerpo()).contains("15:30");
    }

    /**
     * Los diez mensajes del sistema salen. Uno que no llegue a enviarse no rompe
     * nada visible: simplemente alguien no se entera de algo que le concierne.
     */
    @Test
    void deberia_enviar_los_diez_mensajes_del_sistema() {
        ResendMailSender transporte = transporte();
        User titular = cuenta(UserLocale.ES);

        transporte.enviarVerificacionDeCorreo(titular, "t1");
        transporte.enviarAvisoDeRegistroConCorreoExistente(titular);
        transporte.enviarAvisoDeCuentaBloqueada(titular, AHORA);
        transporte.enviarAvisoDeSesionRevocadaPorSeguridad(titular);
        transporte.enviarRestablecimientoDeContrasena(titular, "t2");
        transporte.enviarAvisoDeContrasenaCambiada(titular);
        transporte.enviarAvisoDeCuentaCerrada(titular);
        transporte.enviarConfirmacionDeCorreoNuevo(titular, new Email("nuevo@correo.co"), "t3");
        transporte.enviarAvisoDeIntentoDeCambioAEsteCorreo(titular);
        transporte.enviarAvisoDeCorreoCambiado(titular, new Email("viejo@correo.co"));

        assertThat(cuerposRecibidos).hasSize(10);
    }

    /**
     * Un error del proveedor no se propaga: se registra. Quien acaba de registrarse
     * ya tiene cuenta, y devolverle un error por un correo que no salio le diria que
     * su registro fallo cuando no fallo.
     */
    @Test
    void no_deberia_propagar_un_error_del_proveedor() {
        codigo = 422;
        respuestasFallidas.set(Integer.MAX_VALUE);

        assertThatCode(() -> transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token"))
                .doesNotThrowAnyException();
    }

    /** Lo mismo si el proveedor no esta: la direccion no responde y nadie se cae. */
    @Test
    void no_deberia_propagar_una_caida_del_proveedor() {
        ResendMailSender transporte = transporte();
        servidor.stop(0);

        assertThatCode(() -> transporte.enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token"))
                .doesNotThrowAnyException();
    }

    // --- Reintentos. Solo lo transitorio -------------------------------------

    /**
     * <strong>Un 4xx no se reintenta.</strong> El proveedor entendio la peticion y la
     * rechaza -remitente sin verificar, clave sin permiso-, asi que insistir manda tres
     * veces lo mismo para recibir tres veces el mismo no, y ocupa tres veces el hilo.
     *
     * <p>Es el caso que de verdad ocurrio: `dev` estuvo devolviendo 403 durante horas
     * porque el dominio no estaba verificado en Resend.
     */
    @Test
    void no_deberia_reintentar_cuando_el_proveedor_rechaza_con_4xx() {
        codigo = 403;
        respuestasFallidas.set(Integer.MAX_VALUE);

        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");

        assertThat(cuerposRecibidos).hasSize(1);
    }

    /**
     * <strong>Un 5xx si.</strong> Es el proveedor caido un momento, no una peticion mal
     * hecha, y el segundo intento sale bien.
     *
     * <p>Se afirma sobre los cuerpos recibidos y no sobre el registro: lo que importa es
     * que el correo <em>sale</em>, no que alguien escriba una linea.
     */
    @Test
    void deberia_reintentar_y_salir_cuando_el_proveedor_devuelve_5xx() {
        codigo = 503;
        respuestasFallidas.set(1);

        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");

        assertThat(cuerposRecibidos).hasSize(2);
    }

    /**
     * Y se rinde tras los tres, sin propagar.
     *
     * <p>El correo se pierde: no hay buzon de reintentos, y decirlo es la mitad de esta
     * prueba. La otra mitad es que no insiste indefinidamente ocupando un hilo del
     * ejecutor de correo.
     */
    @Test
    void deberia_rendirse_tras_tres_intentos_sin_propagar() {
        codigo = 503;
        respuestasFallidas.set(Integer.MAX_VALUE);

        assertThatCode(() -> transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token"))
                .doesNotThrowAnyException();

        assertThat(cuerposRecibidos).hasSize(3);
    }

    /**
     * Un corte de red tambien se reintenta, que es el fallo que provoco todo esto: en `dev`
     * se perdio un correo de verificacion por un {@code ResourceAccessException} de un
     * segundo, y quien lo esperaba no tenia salida porque el reenvio exige el token
     * caducado que viajaba en ese correo.
     *
     * <p>Se simula con una espera de lectura mas larga que la del cliente en el primer
     * intento. No se puede hacer apagando el servidor, porque entonces no habria a donde
     * volver a intentarlo.
     */
    @Test
    void deberia_reintentar_cuando_la_conexion_se_corta() {
        cortesPendientes.set(1);

        transporte().enviarVerificacionDeCorreo(cuenta(UserLocale.ES), "token");

        assertThat(cuerposRecibidos).hasSize(2);
    }
}
