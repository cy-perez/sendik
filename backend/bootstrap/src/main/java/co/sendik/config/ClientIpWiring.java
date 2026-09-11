package co.sendik.config;

import co.sendik.shared.config.ClientIpProperties;
import co.sendik.shared.rest.ClientIpHasher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Le da al borde el unico numero que necesita para saber quien llama.
 *
 * <p>Vive aqui por lo mismo que {@link RateLimitWiring}: la configuracion tipada es
 * de {@code infrastructure} y quien la consume es {@code presentation}, y ningun
 * otro modulo ve los dos.
 *
 * <p>Dejo de ser un {@code @Component} el 10 de septiembre de 2026, cuando la
 * direccion de quien llama paso a depender de la topologia (ADR-0036). Antes no
 * dependia de nada configurable, y por eso podia construirse solo.
 */
@Configuration
public class ClientIpWiring {

    @Bean
    ClientIpHasher clientIpHasher(ClientIpProperties propiedades) {
        return new ClientIpHasher(propiedades.trustedHops());
    }
}
