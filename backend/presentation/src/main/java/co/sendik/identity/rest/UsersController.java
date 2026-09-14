package co.sendik.identity.rest;

import co.sendik.identity.dto.ActiveSession;
import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.dto.ProfileView;
import co.sendik.identity.dto.RequestEmailChangeCommand;
import co.sendik.identity.dto.RequestEmailVerificationCommand;
import co.sendik.identity.dto.UpdateAvatarCommand;
import co.sendik.identity.dto.UpdateProfileCommand;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.rest.dto.ActiveSessionResponse;
import co.sendik.identity.rest.dto.ChangeEmailRequest;
import co.sendik.identity.rest.dto.CloseAccountRequest;
import co.sendik.identity.rest.dto.ProfileResponse;
import co.sendik.identity.rest.dto.UpdateProfileRequest;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.ListSessionsUseCase;
import co.sendik.identity.usecase.ReadProfileViewUseCase;
import co.sendik.identity.usecase.RemoveAvatarUseCase;
import co.sendik.identity.usecase.RequestEmailChangeUseCase;
import co.sendik.identity.usecase.RequestEmailVerificationUseCase;
import co.sendik.identity.usecase.RevokeSessionUseCase;
import co.sendik.identity.usecase.UpdateAvatarUseCase;
import co.sendik.identity.usecase.UpdateProfileUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.RefreshCookies;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Lo que una persona puede hacer sobre su propia cuenta ya autenticada.
 *
 * <p><strong>El identificador sale del token, nunca de la peticion.</strong> Es la
 * diferencia entre un endpoint que actua sobre quien llama y uno que actua sobre
 * quien le digan (backend/CLAUDE.md). Por eso ninguna de estas rutas lleva el
 * identificador del usuario en la direccion.
 *
 * <p>Falta la foto del criterio 21, que es la unica parte que necesita
 * almacenamiento de archivos y va en su propia rebanada.
 */
@RestController
@RequestMapping("/api/v1/users/me")
public class UsersController {

    private final RequestEmailVerificationUseCase casoDeReenvio;
    private final ListSessionsUseCase casoDeListado;
    private final RevokeSessionUseCase casoDeRevocacion;
    private final ExportUserDataUseCase casoDeExportacion;
    private final CloseAccountUseCase casoDeCierre;
    private final ReadProfileViewUseCase casoDeLectura;
    private final UpdateProfileUseCase casoDePerfil;
    private final RequestEmailChangeUseCase casoDeCambioDeCorreo;
    private final UpdateAvatarUseCase casoDeAvatar;
    private final RemoveAvatarUseCase casoDeQuitarAvatar;

    /**
     * El almacen se inyecta solo para componer la direccion publica de la foto. No
     * se guarda ni se lee nada por aqui: eso pasa por los casos de uso. La direccion
     * la compone el almacen porque depende de configuracion, y esta capa no la ve.
     */
    private final PublicFileStore almacen;

    private final RefreshCookies cookies;

    public UsersController(
            RequestEmailVerificationUseCase casoDeReenvio,
            ListSessionsUseCase casoDeListado,
            RevokeSessionUseCase casoDeRevocacion,
            ExportUserDataUseCase casoDeExportacion,
            CloseAccountUseCase casoDeCierre,
            ReadProfileViewUseCase casoDeLectura,
            UpdateProfileUseCase casoDePerfil,
            RequestEmailChangeUseCase casoDeCambioDeCorreo,
            UpdateAvatarUseCase casoDeAvatar,
            RemoveAvatarUseCase casoDeQuitarAvatar,
            PublicFileStore almacen,
            RefreshCookies cookies) {
        this.casoDeReenvio = casoDeReenvio;
        this.casoDeListado = casoDeListado;
        this.casoDeRevocacion = casoDeRevocacion;
        this.casoDeExportacion = casoDeExportacion;
        this.casoDeCierre = casoDeCierre;
        this.casoDeLectura = casoDeLectura;
        this.casoDePerfil = casoDePerfil;
        this.casoDeCambioDeCorreo = casoDeCambioDeCorreo;
        this.casoDeAvatar = casoDeAvatar;
        this.casoDeQuitarAvatar = casoDeQuitarAvatar;
        this.almacen = almacen;
        this.cookies = cookies;
    }

    /**
     * Criterio 13. Comparte con el reenvio publico el limite de tres por hora, asi
     * que puede responder 429.
     */
    @PostMapping("/email-verification")
    public ResponseEntity<Void> pedirVerificacionDeCorreo(@AuthenticationPrincipal Jwt token) {
        casoDeReenvio.execute(new RequestEmailVerificationCommand(usuarioDe(token)));
        return ResponseEntity.accepted().build();
    }

    /**
     * Criterio 21: el perfil tal como esta ahora.
     *
     * <p>Desde HU-017 lo arma {@code ReadProfileViewUseCase} y no la cuenta directa: la
     * ciudad puede venir del origen (ADR-0042), y resolverlo es del caso de uso.
     */
    @GetMapping
    public ProfileResponse perfil(@AuthenticationPrincipal Jwt token) {
        return comoPerfil(casoDeLectura.execute(usuarioDe(token)), almacen);
    }

    /**
     * Criterio 21: guarda el perfil y devuelve como quedo.
     *
     * <p>Devolver el resultado y no un 204 evita que el cliente tenga que adivinar
     * como quedaron los datos tras la normalizacion: el telefono, por ejemplo,
     * entra con separadores y se guarda solo con digitos.
     */
    @PutMapping
    public ProfileResponse guardarPerfil(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody UpdateProfileRequest peticion) {
        casoDePerfil.execute(
                new UpdateProfileCommand(usuarioDe(token), peticion.displayName(), peticion.city(), peticion.phone()));

        // Se relee en vez de traducir lo que devolvio el caso de uso: la ciudad que se
        // muestra puede no ser la que se guardo, si hay direccion de origen (HU-017).
        return comoPerfil(casoDeLectura.execute(usuarioDe(token)), almacen);
    }

    /**
     * Criterio 21: pide cambiar el correo. <strong>No lo cambia.</strong>
     *
     * <p>202 siempre, este la direccion libre u ocupada. Cualquier diferencia,
     * incluido el codigo de estado, convertiria este formulario en una forma de
     * averiguar quien tiene cuenta, que es justo lo que el registro evita.
     */
    @PostMapping("/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void pedirCambioDeCorreo(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody ChangeEmailRequest peticion) {
        casoDeCambioDeCorreo.execute(new RequestEmailChangeCommand(usuarioDe(token), peticion.newEmail()));
    }

    /** Criterio 17: las sesiones abiertas, con la actual marcada. */
    @GetMapping("/sessions")
    public List<ActiveSessionResponse> sesiones(@AuthenticationPrincipal Jwt token) {
        return casoDeListado.execute(usuarioDe(token), sesionDe(token)).stream()
                .map(UsersController::comoRespuesta)
                .toList();
    }

    /**
     * Criterio 17: cierra una sesion concreta.
     *
     * <p>Responde 204 tambien cuando esa sesion no existe o no es suya. Distinguir
     * los casos permitiria averiguar si un identificador pertenece a alguien
     * probandolo, y el resultado que la persona pidio se cumple igual.
     *
     * <p>Si cierra la suya, se borra ademas la cookie: dejarla puesta apuntando a
     * una sesion revocada solo produciria un 401 en el siguiente refresco.
     */
    @DeleteMapping("/sessions/{id}")
    public ResponseEntity<Void> cerrarSesion(@AuthenticationPrincipal Jwt token, @PathVariable String id) {
        TokenFamilyId sesion = new TokenFamilyId(UUID.fromString(id));
        casoDeRevocacion.execute(usuarioDe(token), sesion);

        if (sesion.equals(sesionDe(token))) {
            return ResponseEntity.noContent()
                    .header(HttpHeaders.SET_COOKIE, cookies.vacia().toString())
                    .build();
        }
        return ResponseEntity.noContent().build();
    }

    /**
     * Criterio 21: la foto de perfil.
     *
     * <p>PUT y no POST: se reemplaza la foto entera, no se agrega una a una coleccion.
     * Subir una segunda vez sustituye la primera, que es lo que significa PUT.
     *
     * <p>El archivo llega como multipart y no en base64 dentro de un JSON. En base64
     * ocupa un tercio mas y obliga a tener la cadena entera en memoria dos veces,
     * una codificada y otra no.
     *
     * <p>Aqui no se valida el contenido, y no es un olvido: validar en el borde
     * dejaria la regla en un sitio donde no se puede probar sin HTTP. El caso de uso
     * comprueba el tamano, el tipo por los bytes de cabecera y las dimensiones
     * (ADR-0018). Lo unico que se hace aqui es leer los bytes.
     */
    @PutMapping("/avatar")
    public ResponseEntity<ProfileResponse> ponerAvatar(
            @AuthenticationPrincipal Jwt token, @RequestPart("archivo") MultipartFile archivo) throws IOException {

        casoDeAvatar.execute(new UpdateAvatarCommand(usuarioDe(token), archivo.getBytes()));

        return ResponseEntity.ok(comoPerfil(casoDeLectura.execute(usuarioDe(token)), almacen));
    }

    /**
     * Criterio 21: quitar la foto.
     *
     * <p>Idempotente: quitarla cuando no hay ninguna responde igual. Devuelve el
     * perfil y no 204 para que el cliente no tenga que volver a pedirlo solo para
     * saber que ya no hay foto.
     */
    @DeleteMapping("/avatar")
    public ResponseEntity<ProfileResponse> quitarAvatar(@AuthenticationPrincipal Jwt token) {
        casoDeQuitarAvatar.execute(usuarioDe(token));

        return ResponseEntity.ok(comoPerfil(casoDeLectura.execute(usuarioDe(token)), almacen));
    }

    /**
     * Criterio 22: el derecho a conocer, en un archivo que la persona se lleva.
     *
     * <p>{@code Content-Disposition} para que el navegador lo descargue en vez de
     * pintarlo: lo que se ejerce aqui es llevarselo, no mirarlo.
     */
    @GetMapping("/export")
    public ResponseEntity<UserDataExport> exportar(@AuthenticationPrincipal Jwt token) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"sendik-mis-datos.json\"")
                // Un archivo con datos personales no se guarda en ninguna cache
                // intermedia (docs/operacion/datos-personales.md).
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(casoDeExportacion.execute(usuarioDe(token)));
    }

    /**
     * Criterio 23: cierra la cuenta y borra lo que identifica a la persona.
     *
     * <p>Se borra la cookie de refresco ademas de revocar en el servidor: la cuenta
     * ya no existe y la cookie duraria 30 dias apuntando a la nada.
     */
    @DeleteMapping
    public ResponseEntity<Void> cerrarCuenta(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody CloseAccountRequest peticion) {
        casoDeCierre.execute(new CloseAccountCommand(usuarioDe(token), peticion.confirmation()));

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.vacia().toString())
                .build();
    }

    /**
     * El almacen entra por parametro para componer la direccion de la foto. Sigue
     * siendo estatico: asi no puede leer nada del controlador por accidente.
     */
    private static ProfileResponse comoPerfil(ProfileView vista, PublicFileStore almacen) {
        return new ProfileResponse(
                vista.cuenta().email().value(),
                vista.cuenta().tieneElCorreoVerificado(),
                vista.cuenta().displayName().value(),
                vista.ciudad(),
                vista.ciudadEditable(),
                vista.cuenta().phone() == null ? null : vista.cuenta().phone().value(),
                vista.cuenta().avatarKey() == null
                        ? null
                        : almacen.direccionDe(vista.cuenta().avatarKey()).toString());
    }

    private static UserId usuarioDe(Jwt token) {
        return UserId.de(token.getSubject());
    }

    /**
     * El {@code sid} del token: a que sesion pertenece.
     *
     * <p>Puede faltar en un token emitido antes de que ese claim existiera. Entonces
     * no se marca ninguna sesion como actual, que es mejor que marcar la
     * equivocada.
     */
    private static @Nullable TokenFamilyId sesionDe(Jwt token) {
        String sid = token.getClaimAsString("sid");
        return sid == null ? null : new TokenFamilyId(UUID.fromString(sid));
    }

    private static ActiveSessionResponse comoRespuesta(ActiveSession sesion) {
        return new ActiveSessionResponse(
                sesion.id(), sesion.userAgent(), sesion.iniciada(), sesion.expira(), sesion.actual());
    }
}
