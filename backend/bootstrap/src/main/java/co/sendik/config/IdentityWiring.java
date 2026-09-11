package co.sendik.config;

import co.sendik.identity.config.SessionProperties;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.identity.port.out.BreachedPasswordChecker;
import co.sendik.identity.port.out.ConfiguredModerators;
import co.sendik.identity.port.out.ConsentRepository;
import co.sendik.identity.port.out.CredentialsRepository;
import co.sendik.identity.port.out.FinancialInstitutions;
import co.sendik.identity.port.out.LegalDocuments;
import co.sendik.identity.port.out.LoginAttemptRecorder;
import co.sendik.identity.port.out.MailSender;
import co.sendik.identity.port.out.PasswordHasher;
import co.sendik.identity.port.out.RefreshTokenRepository;
import co.sendik.identity.port.out.SellerVerificationRepository;
import co.sendik.identity.port.out.TokenGenerator;
import co.sendik.identity.port.out.UserCart;
import co.sendik.identity.port.out.UserFavorites;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.port.out.VerificationAccessLog;
import co.sendik.identity.port.out.VerificationTokenRepository;
import co.sendik.identity.usecase.ApproveVerificationUseCase;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ConfirmEmailChangeUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.ForgotPasswordUseCase;
import co.sendik.identity.usecase.GrantConfiguredModeratorsUseCase;
import co.sendik.identity.usecase.IssueSessionUseCase;
import co.sendik.identity.usecase.ListFinancialInstitutionsUseCase;
import co.sendik.identity.usecase.ListPendingVerificationsUseCase;
import co.sendik.identity.usecase.ListSessionsUseCase;
import co.sendik.identity.usecase.LoginUseCase;
import co.sendik.identity.usecase.LogoutUseCase;
import co.sendik.identity.usecase.ReadProfileUseCase;
import co.sendik.identity.usecase.ReadPublicProfileUseCase;
import co.sendik.identity.usecase.ReadSellerVerificationUseCase;
import co.sendik.identity.usecase.RefreshSessionUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import co.sendik.identity.usecase.RejectVerificationUseCase;
import co.sendik.identity.usecase.RemoveAvatarUseCase;
import co.sendik.identity.usecase.RequestEmailChangeUseCase;
import co.sendik.identity.usecase.RequestEmailVerificationUseCase;
import co.sendik.identity.usecase.ResendVerificationUseCase;
import co.sendik.identity.usecase.ResetPasswordUseCase;
import co.sendik.identity.usecase.RevokeSessionUseCase;
import co.sendik.identity.usecase.RevokeVerificationUseCase;
import co.sendik.identity.usecase.StartSellerVerificationUseCase;
import co.sendik.identity.usecase.SubmitBankAccountUseCase;
import co.sendik.identity.usecase.SubmitIdentityDocumentUseCase;
import co.sendik.identity.usecase.SubmitSelfieUseCase;
import co.sendik.identity.usecase.SubmitVerificationForReviewUseCase;
import co.sendik.identity.usecase.UpdateAvatarUseCase;
import co.sendik.identity.usecase.UpdateProfileUseCase;
import co.sendik.identity.usecase.VerifyEmailUseCase;
import co.sendik.identity.usecase.ViewVerificationImageUseCase;
import co.sendik.shared.config.AppProperties;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.file.ImagePolicy;
import co.sendik.shared.file.StorageProperties;
import co.sendik.shared.port.out.ImageNormalizer;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.port.out.RestrictedFileStore;
import co.sendik.shared.rest.RefreshCookies;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Cableado de los casos de uso de identidad.
 *
 * <p>Los casos de uso no llevan {@code @Service} ni ninguna otra anotacion: el
 * modulo {@code application} solo puede ver {@code spring-tx}, y una prueba de
 * arquitectura falla si aparece cualquier otra cosa de Spring. Por eso se
 * registran aqui, en {@code bootstrap}, que es el modulo del cableado.
 *
 * <p>Tiene una ventaja que no es solo formal: los casos de uso se construyen con
 * {@code new} en sus pruebas, sin contexto de Spring y sin simular un
 * contenedor.
 */
@Configuration
public class IdentityWiring {

    /**
     * Reloj del sistema en la zona de operacion, no en UTC.
     *
     * <p>RN-008 compara fechas, no instantes: con UTC alguien en Colombia
     * cumpliria 18 anos cinco horas antes de que aqui sea su cumpleanos.
     */
    @Bean
    Clock reloj(AppProperties app) {
        return Clock.system(app.timeZone());
    }

    @Bean
    RegisterUserUseCase registerUserUseCase(
            UserRepository usuarios,
            ConsentRepository consentimientos,
            VerificationTokenRepository tokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            LegalDocuments documentosLegales,
            Clock reloj) {
        return new RegisterUserUseCase(
                usuarios,
                consentimientos,
                tokens,
                hasher,
                filtradas,
                generadorDeTokens,
                correo,
                documentosLegales,
                reloj);
    }

    @Bean
    VerifyEmailUseCase verifyEmailUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            IssueSessionUseCase abrirSesion,
            ConfiguredModerators moderadoresConfigurados,
            Clock reloj) {
        return new VerifyEmailUseCase(usuarios, tokens, generadorDeTokens, abrirSesion, moderadoresConfigurados, reloj);
    }

    @Bean
    ResendVerificationUseCase resendVerificationUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ResendVerificationUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    RequestEmailVerificationUseCase requestEmailVerificationUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new RequestEmailVerificationUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    /**
     * La vigencia del refresco llega como {@link java.time.Duration} y no como el
     * objeto de configuracion completo: un caso de uso no tiene por que conocer el
     * formato en que se configura el sistema, solo el plazo que debe aplicar.
     */
    @Bean
    IssueSessionUseCase issueSessionUseCase(
            RefreshTokenRepository refrescos,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            SessionProperties sesion,
            Clock reloj) {
        return new IssueSessionUseCase(refrescos, accesos, generadorDeTokens, sesion.refreshTtl(), reloj);
    }

    @Bean
    LoginUseCase loginUseCase(
            UserRepository usuarios,
            CredentialsRepository credenciales,
            PasswordHasher hasher,
            LoginAttemptRecorder intentos,
            MailSender correo,
            IssueSessionUseCase abrirSesion,
            Clock reloj) {
        return new LoginUseCase(usuarios, credenciales, hasher, intentos, correo, abrirSesion, reloj);
    }

    @Bean
    RefreshSessionUseCase refreshSessionUseCase(
            RefreshTokenRepository refrescos,
            UserRepository usuarios,
            AccessTokenIssuer accesos,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            SessionProperties sesion,
            Clock reloj) {
        return new RefreshSessionUseCase(
                refrescos,
                usuarios,
                accesos,
                generadorDeTokens,
                correo,
                sesion.refreshTtl(),
                sesion.refreshGrace(),
                reloj);
    }

    @Bean
    LogoutUseCase logoutUseCase(RefreshTokenRepository refrescos, TokenGenerator generadorDeTokens, Clock reloj) {
        return new LogoutUseCase(refrescos, generadorDeTokens, reloj);
    }

    @Bean
    ForgotPasswordUseCase forgotPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ForgotPasswordUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    ResetPasswordUseCase resetPasswordUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            CredentialsRepository credenciales,
            RefreshTokenRepository refrescos,
            TokenGenerator generadorDeTokens,
            PasswordHasher hasher,
            BreachedPasswordChecker filtradas,
            MailSender correo,
            Clock reloj) {
        return new ResetPasswordUseCase(
                usuarios, tokens, credenciales, refrescos, generadorDeTokens, hasher, filtradas, correo, reloj);
    }

    @Bean
    ListSessionsUseCase listSessionsUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        return new ListSessionsUseCase(refrescos, reloj);
    }

    @Bean
    RevokeSessionUseCase revokeSessionUseCase(RefreshTokenRepository refrescos, Clock reloj) {
        return new RevokeSessionUseCase(refrescos, reloj);
    }

    @Bean
    ExportUserDataUseCase exportUserDataUseCase(
            UserRepository usuarios,
            ConsentRepository consentimientos,
            RefreshTokenRepository refrescos,
            UserFavorites favoritos,
            UserCart carrito,
            Clock reloj) {
        return new ExportUserDataUseCase(usuarios, consentimientos, refrescos, favoritos, carrito, reloj);
    }

    @Bean
    CloseAccountUseCase closeAccountUseCase(
            UserRepository usuarios,
            RefreshTokenRepository refrescos,
            MailSender correo,
            PublicFileStore almacen,
            UserFavorites favoritos,
            UserCart carrito,
            Clock reloj) {
        return new CloseAccountUseCase(usuarios, refrescos, correo, almacen, favoritos, carrito, reloj);
    }

    /**
     * La politica de la foto de perfil.
     *
     * <p>Se llama asi y no "politica de imagenes" a proposito: las tomas de producto
     * tienen la suya, {@code politicaDeTomas} en {@code CatalogWiring}, con el minimo
     * de RN-019. Son numeros distintos: un unico bean compartido habria acabado
     * aplicando 900x1200 al avatar, que rechaza casi cualquier foto que alguien tenga
     * a mano.
     *
     * <p>Desde que existen los dos, cada inyeccion lleva {@link Qualifier}. Sin el, pedir
     * un {@code ImagePolicy} por tipo es ambiguo y el contexto no arranca; y antes de que
     * existiera el segundo bean, el catalogo recibia este sin que nada avisara.
     */
    @Bean
    ImagePolicy politicaDeAvatar(StorageProperties almacenamiento) {
        return new ImagePolicy(
                almacenamiento.maxImageBytes(),
                new ImageDimensions(almacenamiento.avatarMinWidth(), almacenamiento.avatarMinHeight()));
    }

    @Bean
    UpdateAvatarUseCase updateAvatarUseCase(
            UserRepository usuarios,
            PublicFileStore almacen,
            ImageNormalizer normalizador,
            @Qualifier("politicaDeAvatar") ImagePolicy politica) {
        return new UpdateAvatarUseCase(usuarios, almacen, normalizador, politica);
    }

    @Bean
    RemoveAvatarUseCase removeAvatarUseCase(UserRepository usuarios, PublicFileStore almacen) {
        return new RemoveAvatarUseCase(usuarios, almacen);
    }

    @Bean
    ReadProfileUseCase readProfileUseCase(UserRepository usuarios) {
        return new ReadProfileUseCase(usuarios);
    }

    /**
     * El perfil publico de cualquiera. HU-009.
     *
     * <p>Aparte del anterior a proposito: aquel solo lee el de quien pregunta y devuelve el
     * usuario entero; este lee el de cualquiera y devuelve solo lo que es publico.
     */
    @Bean
    ReadPublicProfileUseCase readPublicProfileUseCase(UserRepository usuarios) {
        return new ReadPublicProfileUseCase(usuarios);
    }

    // --- Verificacion de vendedor. HU-002 rebanada C -------------------------
    //
    // Los tres casos de uso que suben imagenes reciben el almacen RESERVADO y no el
    // publico, y ahi esta la garantia de RN-046: no es que se acuerden de usar el
    // correcto, es que el otro no lo tienen.
    //
    // La politica de imagen es la misma del avatar por ahora. Nadie ha decidido un
    // minimo de pixeles para la foto de una cedula, y ponerle uno inventado
    // rechazaria documentos legibles; que la foto se pueda leer lo decide el
    // moderador, y para eso existe el motivo de rechazo ILLEGIBLE_PHOTOS.

    @Bean
    StartSellerVerificationUseCase startSellerVerificationUseCase(
            UserRepository usuarios, SellerVerificationRepository verificaciones, Clock reloj) {
        return new StartSellerVerificationUseCase(usuarios, verificaciones, reloj);
    }

    @Bean
    SubmitIdentityDocumentUseCase submitIdentityDocumentUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            ImageNormalizer normalizador,
            @Qualifier("politicaDeAvatar") ImagePolicy politica,
            Clock reloj) {
        return new SubmitIdentityDocumentUseCase(verificaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    SubmitSelfieUseCase submitSelfieUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            ImageNormalizer normalizador,
            @Qualifier("politicaDeAvatar") ImagePolicy politica,
            Clock reloj) {
        return new SubmitSelfieUseCase(verificaciones, almacen, normalizador, politica, reloj);
    }

    @Bean
    SubmitBankAccountUseCase submitBankAccountUseCase(
            SellerVerificationRepository verificaciones, FinancialInstitutions entidades, Clock reloj) {
        return new SubmitBankAccountUseCase(verificaciones, entidades, reloj);
    }

    @Bean
    ReadSellerVerificationUseCase readSellerVerificationUseCase(SellerVerificationRepository verificaciones) {
        return new ReadSellerVerificationUseCase(verificaciones);
    }

    @Bean
    SubmitVerificationForReviewUseCase submitVerificationForReviewUseCase(
            SellerVerificationRepository verificaciones, UserRepository usuarios, MailSender correo, Clock reloj) {
        return new SubmitVerificationForReviewUseCase(verificaciones, usuarios, correo, reloj);
    }

    // El camino del moderador. Los tres reciben la bitacora, y los dos que mueven el
    // sello reciben tambien el repositorio de cuentas: aprobar otorga el rol SELLER y
    // revocar lo quita, en la misma transaccion que el cambio de estado.

    @Bean
    ListFinancialInstitutionsUseCase listFinancialInstitutionsUseCase(FinancialInstitutions entidades) {
        return new ListFinancialInstitutionsUseCase(entidades);
    }

    @Bean
    ListPendingVerificationsUseCase listPendingVerificationsUseCase(SellerVerificationRepository verificaciones) {
        return new ListPendingVerificationsUseCase(verificaciones);
    }

    @Bean
    ViewVerificationImageUseCase viewVerificationImageUseCase(
            SellerVerificationRepository verificaciones,
            RestrictedFileStore almacen,
            VerificationAccessLog bitacora,
            Clock reloj) {
        return new ViewVerificationImageUseCase(verificaciones, almacen, bitacora, reloj);
    }

    @Bean
    ApproveVerificationUseCase approveVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new ApproveVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    @Bean
    RejectVerificationUseCase rejectVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new RejectVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    @Bean
    RevokeVerificationUseCase revokeVerificationUseCase(
            SellerVerificationRepository verificaciones,
            UserRepository usuarios,
            VerificationAccessLog bitacora,
            MailSender correo,
            Clock reloj) {
        return new RevokeVerificationUseCase(verificaciones, usuarios, bitacora, correo, reloj);
    }

    /** HU-006: quien arranca siendo moderador. Con la lista vacia no hace nada. */
    @Bean
    GrantConfiguredModeratorsUseCase grantConfiguredModeratorsUseCase(UserRepository usuarios, Clock reloj) {
        return new GrantConfiguredModeratorsUseCase(usuarios, reloj);
    }

    @Bean
    UpdateProfileUseCase updateProfileUseCase(UserRepository usuarios) {
        return new UpdateProfileUseCase(usuarios);
    }

    @Bean
    RequestEmailChangeUseCase requestEmailChangeUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new RequestEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    @Bean
    ConfirmEmailChangeUseCase confirmEmailChangeUseCase(
            UserRepository usuarios,
            VerificationTokenRepository tokens,
            TokenGenerator generadorDeTokens,
            MailSender correo,
            Clock reloj) {
        return new ConfirmEmailChangeUseCase(usuarios, tokens, generadorDeTokens, correo, reloj);
    }

    /**
     * La cookie del token de refresco.
     *
     * <p>Vive aqui por lo mismo que el origen de CORS: necesita a la vez la
     * configuracion tipada, que es de {@code infrastructure}, y el tipo que consume
     * el controlador, que es de {@code presentation}. Ningun otro modulo ve las dos
     * cosas.
     */
    @Bean
    RefreshCookies refreshCookies(SessionProperties sesion) {
        return new RefreshCookies(
                sesion.cookie().name(), sesion.cookie().path(), sesion.cookie().secure(), sesion.refreshTtl());
    }
}
