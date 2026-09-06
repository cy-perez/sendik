package co.sendik.shared.rest;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * La cadena que protege {@code /internal}, que no es para personas (ADR-0031).
 *
 * <p>Existe aparte de {@link SecurityConfig} porque ahi se validan tokens que emite Sendik
 * con su propio secreto, y aqui hay que validar uno que emite **Google** con sus claves
 * publicas. Son dos decodificadores incompatibles, y un mismo
 * {@code oauth2ResourceServer} no admite los dos. La separacion no es estetica: es la
 * unica forma.
 *
 * <p>Va primero, con {@link Order}, porque la cadena general no declara
 * {@code securityMatcher} y por tanto casa con todo. Sin este orden, una peticion a
 * {@code /internal} entraria por ella y moriria en su {@code denyAll} final.
 *
 * <p><strong>Sin decodificador, nadie entra.</strong> Con la cola apagada el bean no
 * existe y la cadena deniega todo, que es lo correcto: si nadie encola, nadie tiene por
 * que llamar aqui. El fallo cerrado importa mas de lo habitual, porque lo que cuelga de
 * esta ruta manda correo con la papeleria de Sendik.
 *
 * <p>Responde 401 y nunca redirige a un formulario: quien llama es un servicio.
 */
@Configuration
public class InternalSecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain cadenaInterna(
            HttpSecurity http, @Qualifier("decodificadorDeCloudTasks") ObjectProvider<JwtDecoder> decodificador)
            throws Exception {

        http.securityMatcher("/internal/**")
                // Quien llama es Cloud Tasks con un token, no un navegador con una cookie.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(
                        errores -> errores.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));

        JwtDecoder deGoogle = decodificador.getIfAvailable();
        if (deGoogle == null) {
            http.authorizeHttpRequests(rutas -> rutas.anyRequest().denyAll());
            return http.build();
        }

        http.authorizeHttpRequests(rutas -> rutas.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(deGoogle)));

        return http.build();
    }
}
