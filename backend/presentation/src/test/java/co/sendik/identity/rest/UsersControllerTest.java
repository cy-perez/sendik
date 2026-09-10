package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.ActiveSession;
import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.dto.RequestEmailChangeCommand;
import co.sendik.identity.dto.UpdateAvatarCommand;
import co.sendik.identity.dto.UpdateProfileCommand;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.exception.CloseConfirmationMismatchException;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.City;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.ListSessionsUseCase;
import co.sendik.identity.usecase.ReadProfileUseCase;
import co.sendik.identity.usecase.RemoveAvatarUseCase;
import co.sendik.identity.usecase.RequestEmailChangeUseCase;
import co.sendik.identity.usecase.RequestEmailVerificationUseCase;
import co.sendik.identity.usecase.RevokeSessionUseCase;
import co.sendik.identity.usecase.UpdateAvatarUseCase;
import co.sendik.identity.usecase.UpdateProfileUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageTooLargeException;
import co.sendik.shared.file.UnsupportedImageTypeException;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import co.sendik.shared.rest.RefreshCookies;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Lo que una persona puede hacer sobre su propia cuenta. Criterios 17, 22 y 23.
 *
 * <p>Comprueba sobre todo lo que no debe salir: la IP en la lista de sesiones, y
 * cualquier hash en el archivo de datos.
 */
class UsersControllerTest {

    private static final Instant AHORA = Instant.parse("2026-08-17T15:00:00Z");

    /** Lo que esta persona guardo, para comprobar que sale en la descarga. */
    private static final String PUBLICACION_GUARDADA = "01a04385-47b7-79c7-b3f2-62c03a8d4a88";

    private static final UserId USUARIO = UserId.nuevo();
    private static final TokenFamilyId LA_DE_AHORA = TokenFamilyId.nueva();

    private final RequestEmailVerificationUseCase reenvio = mock(RequestEmailVerificationUseCase.class);
    private final ListSessionsUseCase listado = mock(ListSessionsUseCase.class);
    private final RevokeSessionUseCase revocacion = mock(RevokeSessionUseCase.class);
    private final ExportUserDataUseCase exportacion = mock(ExportUserDataUseCase.class);
    private final CloseAccountUseCase cierre = mock(CloseAccountUseCase.class);
    private final ReadProfileUseCase lectura = mock(ReadProfileUseCase.class);
    private final UpdateProfileUseCase perfil = mock(UpdateProfileUseCase.class);
    private final RequestEmailChangeUseCase cambioDeCorreo = mock(RequestEmailChangeUseCase.class);
    private final UpdateAvatarUseCase avatar = mock(UpdateAvatarUseCase.class);
    private final RemoveAvatarUseCase quitarAvatar = mock(RemoveAvatarUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    /**
     * Suple lo que en produccion pone Spring Security. El montaje autonomo no trae
     * la cadena de filtros, asi que el token del principal se inyecta aqui con el
     * mismo {@code sub} y el mismo {@code sid} que tendria uno real.
     */
    private static final class TokenDePrueba implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parametro) {
            return Jwt.class.equals(parametro.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parametro,
                ModelAndViewContainer contenedor,
                NativeWebRequest peticion,
                WebDataBinderFactory fabrica) {
            return Jwt.withTokenValue("da-igual")
                    .header("alg", "HS256")
                    .subject(USUARIO.toString())
                    .claim("sid", LA_DE_AHORA.toString())
                    .build();
        }
    }

    private static User cuentaCon(@Nullable FileKey avatar) {
        return User.rehidratar(
                USUARIO,
                new Email("ana@correo.co"),
                new DisplayName("Ana Maria"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                new City("Medellin"),
                new Phone("3001234567"),
                avatar,
                UserLocale.ES,
                co.sendik.identity.model.UserStatus.ACTIVE,
                AHORA,
                java.util.EnumSet.of(co.sendik.identity.model.Role.BUYER),
                AHORA);
    }

    @BeforeEach
    void montarElBorde() {
        UsersController controlador = new UsersController(
                reenvio,
                listado,
                revocacion,
                exportacion,
                cierre,
                lectura,
                perfil,
                cambioDeCorreo,
                avatar,
                quitarAvatar,
                almacen,
                new RefreshCookies("sendik_refresh", "/api/v1/auth", true, Duration.ofDays(30)));

        mvc = MockMvcBuilders.standaloneSetup(controlador)
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    // Criterio 17: la lista marca cual es la sesion desde la que se mira.
    @Test
    void deberia_listar_las_sesiones_marcando_la_actual_criterio_17() throws Exception {
        when(listado.execute(any(), any()))
                .thenReturn(List.of(
                        new ActiveSession(LA_DE_AHORA.toString(), "Chrome", AHORA, AHORA.plusSeconds(60), true),
                        new ActiveSession(
                                UUID.randomUUID().toString(), "Firefox", AHORA, AHORA.plusSeconds(60), false)));

        mvc.perform(get("/api/v1/users/me/sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userAgent").value("Chrome"))
                .andExpect(jsonPath("$[0].current").value(true))
                .andExpect(jsonPath("$[1].current").value(false));

        verify(listado).execute(USUARIO, LA_DE_AHORA);
    }

    /**
     * La IP se guarda para reconocer patrones de ataque, no para ensenarsela a
     * nadie (docs/operacion/datos-personales.md).
     */
    @Test
    void nunca_deberia_devolver_la_ip_en_la_lista_de_sesiones() throws Exception {
        when(listado.execute(any(), any()))
                .thenReturn(List.of(
                        new ActiveSession(LA_DE_AHORA.toString(), "Chrome", AHORA, AHORA.plusSeconds(60), true)));

        MvcResult resultado = mvc.perform(get("/api/v1/users/me/sessions")).andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("ip");
    }

    /**
     * Cerrar la propia sesion borra tambien la cookie: dejarla puesta apuntando a
     * una sesion revocada solo daria un 401 en el siguiente refresco.
     */
    @Test
    void deberia_borrar_la_cookie_al_cerrar_la_sesion_actual_criterio_17() throws Exception {
        MvcResult resultado = mvc.perform(delete("/api/v1/users/me/sessions/" + LA_DE_AHORA))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");
        verify(revocacion).execute(USUARIO, LA_DE_AHORA);
    }

    @Test
    void no_deberia_tocar_la_cookie_al_cerrar_otra_sesion() throws Exception {
        String otra = UUID.randomUUID().toString();

        mvc.perform(delete("/api/v1/users/me/sessions/" + otra))
                .andExpect(status().isNoContent())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    /**
     * Criterio 22: el archivo se descarga y no se cachea. Lleva datos personales,
     * asi que ninguna cache intermedia debe quedarse con una copia.
     */
    @Test
    void deberia_entregar_los_datos_como_descarga_sin_cache_criterio_22() throws Exception {
        when(exportacion.execute(USUARIO))
                .thenReturn(new UserDataExport(
                        AHORA,
                        new UserDataExport.Cuenta(
                                USUARIO.toString(),
                                "ana@correo.co",
                                "Ana Maria",
                                LocalDate.of(1990, 3, 4),
                                "Medellin",
                                "+57 300 000 0000",
                                "es",
                                "ACTIVE",
                                true,
                                AHORA,
                                List.of("BUYER"),
                                AHORA),
                        List.of(new UserDataExport.Consentimiento("PRIVACY", "2026-08-01", AHORA)),
                        List.of(),
                        // HU-011: los favoritos entran en la descarga.
                        List.of(new UserDataExport.Favorito(PUBLICACION_GUARDADA, AHORA)),
                        // HU-015: y el carrito, por una razon mas fuerte: no dice solo que le
                        // interesa, dice que estuvo a punto de comprarlo.
                        List.of(new UserDataExport.ProductoEnCarrito(PUBLICACION_GUARDADA, AHORA))));

        MvcResult resultado = mvc.perform(get("/api/v1/users/me/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"sendik-mis-datos.json\""))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.cuenta.correo").value("ana@correo.co"))
                // Ciudad y telefono salen en el archivo: son datos de la persona,
                // no del sistema, y el derecho a conocer los alcanza.
                .andExpect(jsonPath("$.cuenta.ciudad").value("Medellin"))
                .andExpect(jsonPath("$.cuenta.telefono").value("+57 300 000 0000"))
                // La evidencia con su version: es lo que prueba a que dijo que si.
                .andExpect(jsonPath("$.consentimientos[0].version").value("2026-08-01"))
                // Y los favoritos, que son dato personal (HU-011, RN-070). Se afirma el
                // nombre del campo y su contenido: el simulador los devolvia y nadie
                // comprobaba nada, asi que si desaparecieran del archivo la prueba seguiria
                // verde y el derecho a conocer se serviria incompleto.
                .andExpect(jsonPath("$.favoritos[0].publicacion").value(PUBLICACION_GUARDADA))
                .andReturn();

        // Ni el hash de la contrasena ni el de ningun token: son secretos del
        // sistema, no datos de la persona.
        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("hash");
    }

    @Test
    void deberia_cerrar_la_cuenta_y_borrar_la_cookie_criterio_23() throws Exception {
        MvcResult resultado = mvc.perform(delete("/api/v1/users/me")
                        .contentType("application/json")
                        .content("""
                                {"confirmation":"ana@correo.co"}
                                """))
                .andExpect(status().isNoContent())
                .andReturn();

        assertThat(resultado.getResponse().getHeader("Set-Cookie")).contains("Max-Age=0");

        ArgumentCaptor<CloseAccountCommand> comando = ArgumentCaptor.forClass(CloseAccountCommand.class);
        verify(cierre).execute(comando.capture());
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().confirmacion()).isEqualTo("ana@correo.co");
    }

    // Cerrar no se deshace: la confirmacion es lo unico que separa un clic mal
    // dado de perder el acceso.
    @Test
    void deberia_rechazar_el_cierre_sin_confirmacion_criterio_23() throws Exception {
        mvc.perform(delete("/api/v1/users/me").contentType("application/json").content("""
                                {"confirmation":"  "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(cierre, never()).execute(any());
    }

    @Test
    void deberia_traducir_una_confirmacion_que_no_coincide_criterio_23() throws Exception {
        doThrow(new CloseConfirmationMismatchException()).when(cierre).execute(any());

        mvc.perform(delete("/api/v1/users/me").contentType("application/json").content("""
                                {"confirmation":"otra@correo.co"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("AUTH_CLOSE_CONFIRMATION_MISMATCH"));
    }

    @Test
    void deberia_aceptar_el_reenvio_de_verificacion_con_202_criterio_13() throws Exception {
        mvc.perform(post("/api/v1/users/me/email-verification")).andExpect(status().isAccepted());

        verify(reenvio).execute(any());
    }

    /** Una cuenta con el perfil ya puesto, para las pruebas del criterio 21. */
    private static User conPerfil(@Nullable String ciudad, @Nullable String telefono) {
        return User.registrar(
                        USUARIO,
                        new Email("ana@correo.co"),
                        new DisplayName("Ana Maria"),
                        new BirthDate(LocalDate.of(1990, 3, 4)),
                        UserLocale.ES,
                        LocalDate.of(2026, 8, 18),
                        AHORA)
                .conPerfil(
                        new DisplayName("Ana Maria"),
                        ciudad == null ? null : new City(ciudad),
                        telefono == null ? null : new Phone(telefono));
    }

    @Test
    void deberia_devolver_el_perfil_criterio_21() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(conPerfil("Medellin", "3001234567"));

        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Ana Maria"))
                .andExpect(jsonPath("$.city").value("Medellin"))
                .andExpect(jsonPath("$.phone").value("3001234567"))
                .andExpect(jsonPath("$.email").value("ana@correo.co"))
                .andExpect(jsonPath("$.emailVerified").value(false));
    }

    /**
     * Devuelve como quedo, no un 204: el telefono entra con separadores y sale
     * normalizado, y el cliente no tiene por que reproducir esa regla.
     */
    @Test
    void deberia_guardar_el_perfil_y_devolver_lo_normalizado_criterio_21() throws Exception {
        when(perfil.execute(any())).thenReturn(conPerfil("Medellin", "+573001234567"));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":"Medellin","phone":"+57 300 123 4567"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+573001234567"));

        ArgumentCaptor<UpdateProfileCommand> comando = ArgumentCaptor.forClass(UpdateProfileCommand.class);
        verify(perfil).execute(comando.capture());
        // El usuario sale del token, nunca del cuerpo.
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().city()).isEqualTo("Medellin");
    }

    @Test
    void deberia_dejar_quitar_la_ciudad_y_el_telefono_criterio_21() throws Exception {
        when(perfil.execute(any())).thenReturn(conPerfil(null, null));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":null,"phone":null}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.city").doesNotExist())
                .andExpect(jsonPath("$.phone").doesNotExist());
    }

    @Test
    void deberia_rechazar_un_perfil_sin_nombre_criterio_21() throws Exception {
        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"  ","city":null,"phone":null}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(perfil, never()).execute(any());
    }

    /**
     * Lo que el dominio rechaza tiene que salir como 400 y no como 500: un
     * telefono con letras es un error de quien escribe, no del servidor.
     */
    @Test
    void deberia_traducir_a_400_lo_que_el_dominio_rechaza_criterio_21() throws Exception {
        when(perfil.execute(any())).thenThrow(new IllegalArgumentException("El telefono no es valido"));

        mvc.perform(put("/api/v1/users/me").contentType("application/json").content("""
                        {"displayName":"Ana Maria","city":null,"phone":"no-es-numero"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));
    }

    /**
     * Criterio 21: pedir el cambio responde 202 y no cambia nada todavia. El
     * correo nuevo se verifica antes de reemplazar al anterior.
     */
    @Test
    void deberia_aceptar_la_peticion_de_cambio_de_correo_con_202_criterio_21() throws Exception {
        mvc.perform(post("/api/v1/users/me/email")
                        .contentType("application/json")
                        .content("""
                        {"newEmail":"nueva@correo.co"}
                        """))
                .andExpect(status().isAccepted());

        ArgumentCaptor<RequestEmailChangeCommand> comando = ArgumentCaptor.forClass(RequestEmailChangeCommand.class);
        verify(cambioDeCorreo).execute(comando.capture());
        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().newEmail()).isEqualTo("nueva@correo.co");
    }

    @Test
    void deberia_rechazar_un_correo_con_formato_invalido_criterio_21() throws Exception {
        mvc.perform(post("/api/v1/users/me/email")
                        .contentType("application/json")
                        .content("""
                        {"newEmail":"no-es-un-correo"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"));

        verify(cambioDeCorreo, never()).execute(any());
    }
    /**
     * Criterio 21: la foto llega como multipart y el borde no la valida.
     *
     * <p>Lo que se comprueba aqui es el contrato HTTP: que el archivo llega entero al
     * caso de uso y que la respuesta trae la direccion de la foto, no su clave. La
     * clave es un detalle del almacen y el cliente lo unico que hace con la foto es
     * pintarla.
     */
    @Test
    void deberia_recibir_la_foto_de_perfil_y_devolver_su_direccion_criterio_21() throws Exception {
        byte[] contenido = {(byte) 0x89, 0x50, 0x4E, 0x47};
        FileKey clave = new FileKey("avatares/la-foto.png");

        when(avatar.execute(any())).thenReturn(cuentaCon(clave));
        when(almacen.direccionDe(clave)).thenReturn(URI.create("https://archivos.sendik.co/avatares/la-foto.png"));

        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("archivo", "lo-que-sea.png", "image/png", contenido))
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").value("https://archivos.sendik.co/avatares/la-foto.png"));

        ArgumentCaptor<UpdateAvatarCommand> captor = ArgumentCaptor.forClass(UpdateAvatarCommand.class);
        verify(avatar).execute(captor.capture());

        assertThat(captor.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(captor.getValue().contenido()).isEqualTo(contenido);
    }

    /** La clave con la que se guarda no sale nunca: el cliente recibe una direccion. */
    @Test
    void nunca_deberia_devolver_la_clave_del_archivo() throws Exception {
        FileKey clave = new FileKey("avatares/la-foto.png");
        when(lectura.execute(USUARIO)).thenReturn(cuentaCon(clave));
        when(almacen.direccionDe(clave)).thenReturn(URI.create("https://archivos.sendik.co/avatares/la-foto.png"));

        MvcResult resultado =
                mvc.perform(get("/api/v1/users/me")).andExpect(status().isOk()).andReturn();

        assertThat(resultado.getResponse().getContentAsString()).doesNotContain("avatarKey");
    }

    @Test
    void deberia_quitar_la_foto_de_perfil_criterio_21() throws Exception {
        when(quitarAvatar.execute(USUARIO)).thenReturn(cuentaCon(null));

        mvc.perform(delete("/api/v1/users/me/avatar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avatarUrl").doesNotExist());

        verify(quitarAvatar).execute(USUARIO);
    }

    /**
     * El tipo se decide por el contenido, y cuando no es una imagen la respuesta es
     * 415: le dice al cliente que el problema es el formato y no lo que hay dentro.
     */
    @Test
    void deberia_responder_415_cuando_lo_subido_no_es_una_imagen() throws Exception {
        doThrow(new UnsupportedImageTypeException()).when(avatar).execute(any());

        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("archivo", "falsa.jpg", "image/jpeg", "<script>".getBytes()))
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_TYPE_UNSUPPORTED"));
    }

    /** 413 y no 400: con un 400 el cliente no distingue "recorta" de "revisa el formulario". */
    @Test
    void deberia_responder_413_cuando_la_imagen_pasa_del_maximo() throws Exception {
        doThrow(new ImageTooLargeException(9_000_000, 8_388_608)).when(avatar).execute(any());

        mvc.perform(multipart("/api/v1/users/me/avatar")
                        .file(new MockMultipartFile("archivo", "grande.png", "image/png", new byte[] {1}))
                        .with(peticion -> {
                            peticion.setMethod("PUT");
                            return peticion;
                        }))
                .andExpect(status().isContentTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }
}
