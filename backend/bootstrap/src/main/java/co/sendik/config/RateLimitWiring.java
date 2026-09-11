package co.sendik.config;

import co.sendik.shared.config.RateLimitProperties;
import co.sendik.shared.rest.RateLimitSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Traduce la configuracion del limite de peticiones al tipo que consume el borde.
 *
 * <p>Vive aqui por lo mismo que el origen de CORS y la cookie del refresco: hace
 * falta ver a la vez la configuracion tipada, que es de {@code infrastructure}, y
 * el tipo que usa {@code presentation}. Ningun otro modulo ve los dos.
 *
 * <p>Enchufarlo al despachador es la otra mitad y no puede estar aqui: eso
 * necesita {@code WebMvcConfigurer}, que solo esta en el classpath de
 * {@code presentation}.
 */
@Configuration
public class RateLimitWiring {

    @Bean
    RateLimitSettings rateLimitSettings(RateLimitProperties limites) {
        return new RateLimitSettings(
                limites.credentials().maxRequests(),
                limites.credentials().window(),
                limites.session().maxRequests(),
                limites.session().window(),
                limites.account().maxRequests(),
                limites.account().window(),
                limites.listings().maxRequests(),
                limites.listings().window(),
                limites.anonymousCart().maxRequests(),
                limites.anonymousCart().window(),
                limites.maxTrackedKeys());
    }
}
