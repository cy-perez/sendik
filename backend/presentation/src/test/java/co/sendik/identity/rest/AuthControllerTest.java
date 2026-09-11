package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.AuthenticatedUser;
import co.sendik.identity.dto.ForgotPasswordCommand;
import co.sendik.identity.dto.LoginCommand;
import co.sendik.identity.dto.LogoutCommand;
import co.sendik.identity.dto.RefreshSessionCommand;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.ResendVerificationCommand;
import co.sendik.identity.dto.ResetPasswordCommand;
import co.sendik.identity.dto.SessionResult;
import co.sendik.identity.dto.VerifyEmailCommand;
import co.sendik.identity.dto.VerifyEmailResult;
import co.sendik.identity.exception.AccountLockedException;
import co.sendik.identity.exception.InvalidCredentialsException;
import co.sendik.identity.exception.PasswordTooShortException;
import co.sendik.identity.exception.RefreshTokenInvalidException;
import co.sendik.identity.exception.RefreshTokenRaceException;
import co.sendik.identity.exception.ResendLimitReachedException;
import co.sendik.identity.exception.ResetTokenExpiredException;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.UserId;
import co.sendik.identity.rest.mapper.SessionResponses;
import co.sendik.identity.usecase.ConfirmEmailChangeUseCase;
import co.sendik.identity.usecase.ForgotPasswordUseCase;
import co.sendik.identity.usecase.LoginUseCase;
import co.sendik.identity.usecase.LogoutUseCase;
import co.sendik.identity.usecase.RefreshSessionUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import co.sendik.identity.usecase.ResendVerificationUseCase;
import co.sendik.identity.usecase.ResetPasswordUseCase;
import co.sendik.identity.usecase.VerifyEmailUseCase;
import co.sendik.shared.rest.ApiExceptionHandler;
import co.sendik.shared.rest.ClientIpHasher;
import co.sendik.shared.rest.RateLimitInterceptor;
import co.sendik.shared.rest.RateLimiter;
import co.sendik.shared.rest.RefreshCookies;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * El borde HTTP de la sesion. HU-001 rebanada B.
 *
 * <p>Comprueba lo que solo se ve en la respuesta y que ninguna prueba de caso de
 * uso puede ver: los atributos de la cookie, el codigo de estado, que el cuerpo no
 * lleve nunca el token de refresco y que los errores salgan como
 * {@code ProblemDetail} sin filtrar nada.
 *
 * <p>Se monta con {@code standaloneSetup} y no con {@code @WebMvcTest}: en Spring
 * Boot 4 esa anotacion se mudo a un artefacto de pruebas aparte que este proyecto
 * no declara, y no se agrega una dependencia de paso. A cambio, aqui no hay
 * cadena de seguridad: lo que decide {@code SecurityConfig} se comprueba en
 * {@code bootstrap}, que si levanta el contexto entero.
 */
class AuthControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");
    /** MockMvc llama sin proxy delante: la direccion sale de la conexion. */
    private static final int SIN_PROXY = 0;

    /** Lo que corre en la nube: un salto de confianza delante (ADR-0036). */
    private static final int TRAS_CLOUD_RUN = 1;

    /**
     * SHA-256 de `8.8.8.8`, de `127.0.0.1` y de `203.0.113.1`, escritos y no calculados con
     * la clase que se prueba: comparar su salida contra si misma no fija nada.
     */
    private static final String HASH_DE_8888 = "838c4c2573848f58e74332341a7ca6bc5cd86a8aec7d644137d53b4d597f10f5";

    private static final String HASH_DE_LA_CONEXION =
            "12ca17b49af2289436f303e0166030a21e525d266e209267433801a8fd4071a0";

    private static final String HASH_INVENTADO = "a1ceb3dc7b127ea22d04f67b50908245930cfa9a3f91e1d38c0b266c44669ee7";

    private static final Clock RELOJ = Clock.fixed(AHORA, ZoneOffset.UTC);

    private final RegisterUserUseCase registro = mock(RegisterUserUseCase.class);
    private final VerifyEmailUseCase verificacion = mock(VerifyEmailUseCase.class);
    private final ResendVerificationUseCase reenvio = mock(ResendVerificationUseCase.class);
    private final LoginUseCase ingreso = mock(LoginUseCase.class);
    private final RefreshSessionUseCase refresco = mock(RefreshSessionUseCase.class);
    private final LogoutUseCase cierre = mock(LogoutUseCase.class);
    private final ForgotPasswordUseCase olvido = mock(ForgotPasswordUseCase.class);
    private final ResetPasswordUseCase restablecimiento = mock(ResetPasswordUseCase.class);
    private final ConfirmEmailChangeUseCase confirmacionDeCorreo = mock(ConfirmEmailChangeUseCase.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        AuthController controlador = new AuthController(
                registro,
                verificacion,
                reenvio,
                ingreso,
                refresco,
                cierre,
                olvido,
                restablecimiento,
                confirmacionDeCorreo,
                new SessionResponses(RELOJ),
                // Los mismos atributos que arma bootstrap desde la configuracion.
                new RefreshCookies("sendik_refresh", "/api/v1/auth", true, Duration.ofDays(30)),
                new ClientIpHasher(SIN_PROXY));

        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(
                        // Holgado a proposito: aqui se prueba el borde, no el limite.
                        // El limite tiene sus propias pruebas en RateLimiterTest.
                        new RateLimiter(1000, Duration.ofMinutes(1), 1000),
                        new RateLimiter(1000, Duration.ofMinutes(1), 1000),
                        new RateLimiter(1000, Duration.ofMinutes(1), 1000),
                        new RateLimiter(1000, Duration.ofMinutes(1), 1000),
                        new ClientIpHasher(SIN_PROXY),
                        RELOJ))
                .build();
    }

    private static SessionResult unaSesion() {
        return new SessionResult(
                "un-token-de-acceso",
                AHORA.plus(Duration.ofMinutes(15)),
                "un-token-de-refresco",
                AHORA.plus(Duration.ofDays(30)),
                new AuthenticatedUser(UserId.nuevo(), "ana@correo.co", "Ana Maria", true, Set.of(Role.BUYER)));
    }

    private MvcResult entrar() throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andReturn();
    }

    // Criterio 10: token de acceso en el cuerpo, token de refresco en la cookie.
    @Test
    void deberia_devolver_el_acceso_en_el_cuerpo_al_entrar_criterio_10() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenReturn(unaSesion());

        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("un-token-de-acceso"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.email").value("ana@correo.co"))
                .andExpect(jsonPath("$.user.emailVerified").value(true))
                .andExpect(jsonPath("$.user.roles[0]").value("BUYER"));
    }

    /**
     * ADR-0003: el token de refresco viaja solo en la cookie. Devolverlo tambien en
     * el cuerpo anularia que sea {@code HttpOnly}, porque cualquier script de la
     * pagina podria quedarse con una credencial de 30 dias.
     */
    @Test
    void nunca_deberia_devolver_el_token_de_refresco_en_el_cuerpo() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenReturn(unaSesion());

        assertThat(entrar().getResponse().getContentAsString()).doesNotContain("un-token-de-refresco");
    }

    /** Criterio 10: los cuatro atributos de la cookie son la proteccion, no un adorno. */
    @Test
    void deberia_mandar_la_cookie_con_todos_sus_atributos_criterio_10() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenReturn(unaSesion());

        assertThat(entrar().getResponse().getHeader("Set-Cookie"))
                .contains("sendik_refresh=un-token-de-refresco")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Strict")
                .contains("Path=/api/v1/auth");
    }

    /**
     * Criterio 11: el mismo codigo para un correo que no existe y para una
     * contrasena equivocada. Y ni el texto de la excepcion ni su traza salen.
     */
    @Test
    void deberia_responder_401_generico_ante_credenciales_incorrectas_criterio_11() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenThrow(new InvalidCredentialsException());

        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.traceId").exists())
                .andExpect(jsonPath("$.detail").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    // Criterio 12: la cuenta bloqueada dice cuando volver a intentar.
    @Test
    void deberia_responder_429_con_retry_after_si_la_cuenta_esta_bloqueada_criterio_12() throws Exception {
        when(ingreso.execute(any(LoginCommand.class)))
                .thenThrow(new AccountLockedException(Instant.now().plus(Duration.ofMinutes(15))));

        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_ACCOUNT_LOCKED"))
                .andExpect(header().exists("Retry-After"));
    }

    // La validacion del borde corta antes de gastar un intento de RN-006.
    @Test
    void deberia_rechazar_un_ingreso_sin_correo_valido_sin_llamar_al_caso_de_uso() throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                                {"email":"no-es-un-correo","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("email"));

        verify(ingreso, never()).execute(any());
    }

    /**
     * Criterio 14: el refresco no lleva cuerpo. La credencial es la cookie, y
     * pedirla tambien en el cuerpo permitiria refrescar con un token copiado a
     * mano, que es justo lo que la cookie {@code HttpOnly} evita.
     */
    @Test
    void deberia_tomar_el_refresco_de_la_cookie_y_no_del_cuerpo_criterio_14() throws Exception {
        when(refresco.execute(any(RefreshSessionCommand.class))).thenReturn(unaSesion());

        mvc.perform(post("/api/v1/auth/refresh")
                        .cookie(new Cookie("sendik_refresh", "el-de-la-cookie"))
                        .header("User-Agent", "Chrome"))
                .andExpect(status().isOk());

        ArgumentCaptor<RefreshSessionCommand> comando = ArgumentCaptor.forClass(RefreshSessionCommand.class);
        verify(refresco).execute(comando.capture());
        assertThat(comando.getValue().refreshToken()).isEqualTo("el-de-la-cookie");
        assertThat(comando.getValue().userAgent()).isEqualTo("Chrome");
        // La IP nunca llega en claro a la capa de aplicacion: solo su hash
        // (docs/operacion/datos-personales.md).
        assertThat(comando.getValue().ipHash()).isNotNull().doesNotContain("127.0.0.1");
    }

    // Caso borde de HU-001: el navegador bloqueo la cookie. No falla en silencio.
    @Test
    void deberia_responder_401_al_refrescar_sin_cookie() throws Exception {
        when(refresco.execute(any(RefreshSessionCommand.class))).thenThrow(new RefreshTokenInvalidException());

        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_INVALID"));
    }

    /**
     * <strong>El codigo de la carrera, en el borde, que es donde el cliente lo lee.</strong>
     *
     * <p>El cliente decide con la cadena exacta: ante {@code AUTH_SESSION_RACE} reintenta y
     * ante {@code AUTH_SESSION_INVALID} cierra la sesion. Sin esta prueba, renombrar el
     * codigo o cambiar su estado deja todo en verde de este lado y el cliente vuelve a
     * cerrar sesiones en cada carrera, sin que nada relacione una cosa con la otra.
     *
     * <p>El estado es 401 igual que el otro, a proposito: en los dos casos la peticion no
     * fue autorizada. Lo que cambia es el codigo (ADR-0030).
     */
    @Test
    void deberia_responder_401_con_codigo_propio_ante_la_carrera_del_refresco_ADR_0030() throws Exception {
        when(refresco.execute(any(RefreshSessionCommand.class))).thenThrow(new RefreshTokenRaceException());

        mvc.perform(post("/api/v1/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_SESSION_RACE"));
    }

    /**
     * Criterio 16: responde 204 y borra la cookie siempre, tambien sin cookie o con
     * una que ya no sirve. Quien quiere salir termina fuera.
     */
    @Test
    void deberia_borrar_la_cookie_al_salir_criterio_16() throws Exception {
        MvcResult resultado = mvc.perform(
                        post("/api/v1/auth/logout").cookie(new Cookie("sendik_refresh", "el-de-la-cookie")))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie"))
                .contains("sendik_refresh=")
                .contains("Max-Age=0");
        verify(cierre).execute(new LogoutCommand("el-de-la-cookie"));
    }

    @Test
    void deberia_salir_sin_error_aunque_no_haya_cookie_criterio_16() throws Exception {
        mvc.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());
    }

    /**
     * Criterio 2: registrar un correo nuevo y registrar uno que ya tiene cuenta se
     * responden igual. Por eso 202 sin cuerpo y sin {@code Location}: una cabecera
     * apuntando al recurso creado diria que el correo no existia.
     */
    @Test
    void deberia_responder_202_sin_cuerpo_al_registrar_criterio_2() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga",
                                 "displayName":"Ana Maria","birthDate":"1990-03-04","locale":"es",
                                 "acceptsTerms":true,"acceptsPrivacy":true}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(header().doesNotExist("Location"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).isEmpty();
    }

    /**
     * El agente se recorta: es texto que manda el cliente y no tiene por que ser
     * razonable. Sin recortarlo, una cabecera larguisima acabaria en la columna
     * user_agent de cada sesion.
     */
    @Test
    void deberia_recortar_un_user_agent_desmedido() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenReturn(unaSesion());

        mvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .header("User-Agent", "x".repeat(500))
                .content("""
                        {"email":"ana@correo.co","password":"una-contrasena-larga"}
                        """));

        ArgumentCaptor<LoginCommand> comando = ArgumentCaptor.forClass(LoginCommand.class);
        verify(ingreso).execute(comando.capture());
        assertThat(comando.getValue().userAgent()).hasSize(255);
    }

    /**
     * Criterio 9: verificar el correo deja la sesion abierta. La cookie sale igual
     * que en el ingreso, porque la persona queda dentro sin escribir su contrasena.
     */
    @Test
    void deberia_abrir_sesion_al_verificar_el_correo_criterio_9() throws Exception {
        when(verificacion.execute(any(VerifyEmailCommand.class))).thenReturn(new VerifyEmailResult(unaSesion(), false));

        MvcResult resultado = mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("""
                                {"token":"el-del-correo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.session.accessToken").value("un-token-de-acceso"))
                .andExpect(jsonPath("$.alreadyVerified").value(false))
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("sendik_refresh=un-token-de-refresco");
    }

    // Volver a abrir el enlace no es un error: la cuenta ya estaba activa y la
    // persona entra igual.
    @Test
    void deberia_distinguir_la_cuenta_que_ya_estaba_verificada() throws Exception {
        when(verificacion.execute(any(VerifyEmailCommand.class))).thenReturn(new VerifyEmailResult(unaSesion(), true));

        mvc.perform(post("/api/v1/auth/verify-email")
                        .contentType("application/json")
                        .content("""
                                {"token":"el-del-correo"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyVerified").value(true));
    }

    // Criterio 8: agotados los reenvios se dice cuando volver a intentar.
    @Test
    void deberia_responder_429_con_retry_after_al_agotar_los_reenvios_criterio_8() throws Exception {
        doThrow(new ResendLimitReachedException()).when(reenvio).execute(any(ResendVerificationCommand.class));

        mvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType("application/json")
                        .content("""
                                {"expiredToken":"el-vencido"}
                                """))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("AUTH_RESEND_LIMIT_REACHED"))
                .andExpect(header().string("Retry-After", "3600"));
    }

    @Test
    void deberia_aceptar_el_reenvio_con_202() throws Exception {
        mvc.perform(post("/api/v1/auth/resend-verification")
                        .contentType("application/json")
                        .content("""
                                {"expiredToken":"el-vencido"}
                                """))
                .andExpect(status().isAccepted());

        verify(reenvio).execute(new ResendVerificationCommand("el-vencido"));
    }

    /**
     * Lo que el dominio rechaza por formato despues de que el borde lo dejo pasar.
     * Sale como 400 y no como 500: la peticion esta mal, no el servidor.
     */
    @Test
    void deberia_traducir_un_rechazo_del_dominio_a_400() throws Exception {
        when(ingreso.execute(any(LoginCommand.class))).thenThrow(new IllegalArgumentException("correo invalido"));

        mvc.perform(post("/api/v1/auth/login").contentType("application/json").content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    /** Un fallo inesperado no filtra ni el texto de la excepcion ni su traza. */
    @Test
    void no_deberia_filtrar_nada_de_un_fallo_inesperado() throws Exception {
        when(ingreso.execute(any(LoginCommand.class)))
                .thenThrow(new IllegalStateException("la conexion con la base se cayo en el nodo 3"));

        MvcResult resultado = mvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("COMMON_UNEXPECTED"))
                .andExpect(jsonPath("$.traceId").exists())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("nodo 3");
    }

    /**
     * Criterio 19: el mismo 202 exista o no el correo. El caso de uso ya no
     * distingue; aqui se fija que el borde tampoco, porque un codigo de estado
     * distinto delataria lo mismo que un mensaje distinto.
     */
    @Test
    void deberia_responder_202_al_pedir_restablecimiento_criterio_19() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"ana@correo.co"}
                                """))
                .andExpect(status().isAccepted())
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).isEmpty();
        verify(olvido).execute(new ForgotPasswordCommand("ana@correo.co"));
    }

    @Test
    void deberia_rechazar_un_correo_con_formato_invalido_al_pedir_restablecimiento() throws Exception {
        mvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"email":"no-es-un-correo"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(olvido, never()).execute(any());
    }

    /**
     * Criterio 20: responde 204 y ninguna sesion. Emitir una aqui contradiria el
     * propio criterio, que acaba de cerrar todas, y se la daria sin un paso mas a
     * quien hubiera llegado por tener acceso al buzon.
     */
    @Test
    void no_deberia_abrir_sesion_al_restablecer_criterio_20() throws Exception {
        MvcResult resultado = mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"token":"el-del-correo","newPassword":"una-contrasena-larga"}
                                """))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"))
                .andReturn();

        assertThat(resultado.getResponse().getContentAsString()).isEmpty();
        verify(restablecimiento).execute(new ResetPasswordCommand("el-del-correo", "una-contrasena-larga"));
    }

    /** Criterio 18: el enlace dura 30 minutos y tiene su propio codigo de error. */
    @Test
    void deberia_traducir_el_enlace_de_restablecimiento_vencido_criterio_18() throws Exception {
        doThrow(new ResetTokenExpiredException()).when(restablecimiento).execute(any());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"token":"el-vencido","newPassword":"una-contrasena-larga"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUTH_RESET_TOKEN_EXPIRED"));
    }

    /**
     * El borde no aplica el largo minimo: lo hace el dominio, para poder decir cual
     * de las dos reglas de RN-005 fallo. Con un @Size(min) aqui, este caso daria un
     * error de validacion generico.
     */
    @Test
    void deberia_dejar_que_el_dominio_juzgue_el_largo_de_la_contrasena_RN_005() throws Exception {
        doThrow(new PasswordTooShortException()).when(restablecimiento).execute(any());

        mvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content("""
                                {"token":"el-del-correo","newPassword":"corta"}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("AUTH_PASSWORD_TOO_SHORT"));
    }

    // El limite del borde responde con el mismo ProblemDetail que los demas errores.
    @Test
    void deberia_responder_429_al_pasarse_del_limite_de_peticiones() throws Exception {
        MockMvc conLimiteDeUno = MockMvcBuilders.standaloneSetup(new AuthController(
                        registro,
                        verificacion,
                        reenvio,
                        ingreso,
                        refresco,
                        cierre,
                        olvido,
                        restablecimiento,
                        confirmacionDeCorreo,
                        new SessionResponses(RELOJ),
                        new RefreshCookies("sendik_refresh", "/api/v1/auth", true, Duration.ofDays(30)),
                        new ClientIpHasher(SIN_PROXY)))
                .setControllerAdvice(new ApiExceptionHandler())
                .addInterceptors(new RateLimitInterceptor(
                        new RateLimiter(1, Duration.ofMinutes(1), 100),
                        new RateLimiter(1, Duration.ofMinutes(1), 100),
                        new RateLimiter(1, Duration.ofMinutes(1), 100),
                        new RateLimiter(1, Duration.ofMinutes(1), 100),
                        new ClientIpHasher(SIN_PROXY),
                        RELOJ))
                .build();

        conLimiteDeUno.perform(post("/api/v1/auth/logout")).andExpect(status().isNoContent());

        conLimiteDeUno
                .perform(post("/api/v1/auth/logout"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("COMMON_TOO_MANY_REQUESTS"))
                .andExpect(header().exists("Retry-After"));
    }

    /**
     * <strong>HU-001, criterio 5: la constancia de consentimiento, en el borde donde se
     * rompia.</strong>
     *
     * <p>La cabecera trae las tres cosas que fallaban: una direccion inventada delante, un
     * hueco -la forma exacta del fallo de produccion de ADR-0036, que dejaba el hash nulo y
     * la constancia sin direccion- y al final la que anade el proxy. El criterio pide la IP
     * entre la evidencia de cada consentimiento, asi que esto es la mitad legal del arreglo
     * y no una variante mas del limite de peticiones.
     *
     * <p><strong>Se afirma cual es el hash, no solo que no sea nulo.</strong> Con «no nulo»
     * la prueba pasaba aunque la constancia guardara la direccion de la conexion, que detras
     * de un proxy es la misma para todo el mundo: una evidencia que no prueba de donde vino
     * ninguna aceptacion. Con los tres valores escritos queda fijado ademas que gana la
     * ultima entrada y no la primera.
     *
     * <p>Va con un salto de confianza declarado, que es como corre en la nube y no como
     * corren las demas pruebas de esta clase.
     */
    @Test
    void deberia_guardar_la_constancia_con_la_direccion_del_cliente_criterio_5() throws Exception {
        MockMvc trasUnProxy = conSaltosDeConfianza(TRAS_CLOUD_RUN);

        trasUnProxy
                .perform(post("/api/v1/auth/register")
                        .contentType("application/json")
                        .header("X-Forwarded-For", "203.0.113.1, , 8.8.8.8")
                        .content("""
                                {"email":"ana@correo.co","password":"una-contrasena-larga",
                                 "displayName":"Ana Maria","birthDate":"1990-03-04","locale":"es",
                                 "acceptsTerms":true,"acceptsPrivacy":true}
                                """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<RegisterUserCommand> comando = ArgumentCaptor.forClass(RegisterUserCommand.class);
        verify(registro).execute(comando.capture());
        assertThat(comando.getValue().ipHash())
                .as("la del proxy, que es la del cliente")
                .isEqualTo(HASH_DE_8888)
                .as("ni la de la conexion, que MockMvc pone en 127.0.0.1")
                .isNotEqualTo(HASH_DE_LA_CONEXION)
                .as("ni la que se invento quien llama")
                .isNotEqualTo(HASH_INVENTADO);
    }

    /** El mismo borde, con la topologia que se le declare. */
    private MockMvc conSaltosDeConfianza(int saltos) {
        return MockMvcBuilders.standaloneSetup(new AuthController(
                        registro,
                        verificacion,
                        reenvio,
                        ingreso,
                        refresco,
                        cierre,
                        olvido,
                        restablecimiento,
                        confirmacionDeCorreo,
                        new SessionResponses(RELOJ),
                        new RefreshCookies("sendik_refresh", "/api/v1/auth", true, Duration.ofDays(30)),
                        new ClientIpHasher(saltos)))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }
}
