package co.sendik.shared.rest;

import java.time.Clock;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Enchufa {@link RateLimitInterceptor} al despachador.
 *
 * <p>Esta mitad vive en {@code presentation} y no en {@code bootstrap} porque
 * {@code WebMvcConfigurer} es de Spring MVC, que solo esta en el classpath de este
 * modulo. La otra mitad, los numeros, llega en {@link RateLimitSettings} desde
 * {@code bootstrap}, que es quien ve la configuracion.
 */
@Configuration
public class RateLimitRegistration implements WebMvcConfigurer {

    private final RateLimitInterceptor limite;

    public RateLimitRegistration(RateLimitSettings ajustes, ClientIpHasher hasherDeIp, Clock reloj) {
        this.limite = new RateLimitInterceptor(
                new RateLimiter(ajustes.maxDeCredenciales(), ajustes.ventanaDeCredenciales(), ajustes.maxDeOrigenes()),
                new RateLimiter(ajustes.maxDeSesion(), ajustes.ventanaDeSesion(), ajustes.maxDeOrigenes()),
                new RateLimiter(ajustes.maxDeCuenta(), ajustes.ventanaDeCuenta(), ajustes.maxDeOrigenes()),
                new RateLimiter(ajustes.maxDePublicacion(), ajustes.ventanaDePublicacion(), ajustes.maxDeOrigenes()),
                new RateLimiter(
                        ajustes.maxDeCarritoAnonimo(), ajustes.ventanaDeCarritoAnonimo(), ajustes.maxDeOrigenes()),
                hasherDeIp,
                reloj);
    }

    /**
     * Se acota a los prefijos que el interceptor conoce: el interceptor ya decide por
     * ruta, pero limitar el patron aqui evita que corra en cada peticion del catalogo, que
     * es publico y de lectura y no tiene a quien contar.
     *
     * <p>{@code /api/v1/users} entra desde HU-012. Hasta entonces ninguna ruta autenticada
     * tenia tope, asi que cualquier cuenta registrada podia repetir sin freno una lectura
     * que ejecuta un agregado.
     *
     * <p><strong>{@code /api/v1/listings} entra ahora</strong>, por el bucle sin cota que
     * dejo a la vista HU-013. Este prefijo es el unico de los tres que incluye una ruta
     * publica -la ficha, {@code GET /api/v1/listings/{uuid}}, que es {@code permitAll}-,
     * asi que el interceptor si corre sobre ella. No la cuenta: sin sesion no hay sujeto,
     * y el interceptor descarta expresamente el token anonimo. El coste por peticion
     * publica es una lectura del contexto de seguridad.
     *
     * <p>La coleccion se declara aparte de su prefijo, {@code /api/v1/listings} ademas de
     * {@code /api/v1/listings/**}, y no es redundante por gusto: ahi vive el {@code POST}
     * que crea una publicacion, y crear borradores sin cota es el mismo problema que
     * reenviarlos. Depender de si el comparador de rutas hace que un {@code /**} final case
     * tambien la ruta sin barra seria apoyar un limite en un detalle del framework.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registro) {
        registro.addInterceptor(limite)
                .addPathPatterns(
                        "/api/v1/auth/**",
                        "/api/v1/users/**",
                        "/api/v1/listings",
                        "/api/v1/listings/**",
                        "/api/v1/carts");
    }
}
