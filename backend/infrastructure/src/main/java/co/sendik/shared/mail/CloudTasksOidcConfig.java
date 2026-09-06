package co.sendik.shared.mail;

import co.sendik.shared.config.MailQueueProperties;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * El decodificador del token con el que Cloud Tasks firma su llamada (ADR-0031).
 *
 * <p>Es **otro** verificador de tokens, distinto del que valida las sesiones de las
 * personas: aquel comprueba un JWT que emite Sendik con su propio secreto, y este
 * comprueba uno que emite Google con sus claves publicas. No se pueden mezclar, y por eso
 * el endpoint interno tiene su propia cadena de seguridad.
 *
 * <p>Se comprueban cuatro cosas, y las cuatro hacen falta:
 *
 * <ol>
 *   <li>La firma, contra las claves publicas de Google.
 *   <li>El emisor, que tiene que ser Google y no cualquiera.
 *   <li>La audiencia, que es la direccion de este endpoint. Sin esto, un token que Google
 *       emitio para otro servicio del mismo proyecto valdria aqui.
 *   <li><strong>El correo de la cuenta de servicio</strong>, que tiene que ser exactamente
 *       la configurada. Sin esto, cualquiera con una cuenta de Google podria pedir un
 *       token para esta audiencia. Es la comprobacion que convierte "un token valido de
 *       Google" en "la tarea que encolo este servicio".
 * </ol>
 *
 * <p>El bean solo existe con la cola encendida. Apagada, la cadena del endpoint interno se
 * queda sin decodificador y rechaza todo, que es lo correcto: sin cola no hay nadie
 * legitimo llamando ahi.
 */
@Configuration
@ConditionalOnProperty(prefix = "sendik.mail.queue", name = "enabled", havingValue = "true")
public class CloudTasksOidcConfig {

    /** Los tokens de cuenta de servicio de Google se firman con estas claves. */
    private static final String CLAVES = "https://www.googleapis.com/oauth2/v3/certs";

    private static final List<String> EMISORES = List.of("https://accounts.google.com", "accounts.google.com");

    @Bean("decodificadorDeCloudTasks")
    JwtDecoder decodificadorDeCloudTasks(MailQueueProperties propiedades) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withJwkSetUri(CLAVES).build();

        decodificador.setJwtValidator(new DelegatingValidator(List.of(
                JwtValidators.createDefault(),
                emisorEsGoogle(),
                audienciaEs(propiedades.audienciaEfectiva()),
                cuentaDeServicioEs(propiedades.serviceAccount()))));

        return decodificador;
    }

    private static OAuth2TokenValidator<Jwt> emisorEsGoogle() {
        return token -> EMISORES.contains(String.valueOf(token.getClaimAsString(JwtClaimNames.ISS)))
                ? OAuth2TokenValidatorResult.success()
                : fallo("El emisor del token no es Google");
    }

    private static OAuth2TokenValidator<Jwt> audienciaEs(String audiencia) {
        return token -> token.getAudience() != null && token.getAudience().contains(audiencia)
                ? OAuth2TokenValidatorResult.success()
                : fallo("El token no fue emitido para este endpoint");
    }

    private static OAuth2TokenValidator<Jwt> cuentaDeServicioEs(String cuenta) {
        return token -> cuenta.equals(token.getClaimAsString("email"))
                        && Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified"))
                ? OAuth2TokenValidatorResult.success()
                : fallo("El token no lo firmo la cuenta de servicio de la cola");
    }

    private static OAuth2TokenValidatorResult fallo(String descripcion) {
        return OAuth2TokenValidatorResult.failure(
                new org.springframework.security.oauth2.core.OAuth2Error("invalid_token", descripcion, null));
    }

    /** Todos tienen que pasar. Se para en el primero que falle. */
    private record DelegatingValidator(List<OAuth2TokenValidator<Jwt>> validadores)
            implements OAuth2TokenValidator<Jwt> {

        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            for (OAuth2TokenValidator<Jwt> validador : validadores) {
                OAuth2TokenValidatorResult resultado = validador.validate(token);
                if (resultado.hasErrors()) {
                    return resultado;
                }
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
