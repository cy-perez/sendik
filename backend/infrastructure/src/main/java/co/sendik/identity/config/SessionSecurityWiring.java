package co.sendik.identity.config;

import co.sendik.identity.security.JwtAccessTokenIssuer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * El decodificador con el que el borde HTTP valida el token de acceso en cada
 * peticion.
 *
 * <p>Vive aqui y no en {@code presentation} por lo mismo que el origen de CORS vive
 * en {@code bootstrap}: necesita el secreto de firma, que es configuracion, y
 * {@code presentation} no ve la capa de configuracion. La cadena de seguridad lo pide
 * por nombre, {@code jwtDecoder}, y no por tipo: desde ADR-0031 hay un segundo
 * {@link JwtDecoder} en el contexto -el que valida los tokens de Google con los que
 * Cloud Tasks firma sus peticiones- y pedirlo por tipo dejo de arrancar.
 *
 * <p><strong>El algoritmo se fija explicitamente.</strong> Un decodificador que
 * acepte cualquier algoritmo de la cabecera es la puerta de la confusion de
 * algoritmos: quien controle el token elige como se verifica. Aqui solo HS256, que
 * es lo unico que este sistema emite.
 *
 * <p>Y se valida el emisor, no solo la firma y la caducidad: el mismo secreto en dos
 * entornos haria que un token de pruebas sirviera en produccion.
 */
@Configuration
public class SessionSecurityWiring {

    @Bean
    JwtDecoder jwtDecoder(SessionProperties sesion) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(
                        JwtAccessTokenIssuer.claveDeFirma(sesion.secret()))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        decodificador.setJwtValidator(
                JwtValidators.createDefaultWithIssuer(sesion.issuer().toString()));

        return decodificador;
    }
}
