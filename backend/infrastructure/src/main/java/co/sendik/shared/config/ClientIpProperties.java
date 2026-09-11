package co.sendik.shared.config;

import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Cuantas entradas anade la infraestructura al final de {@code X-Forwarded-For}.
 *
 * <p>Es el unico dato que decide de donde se saca la direccion de quien llama, y
 * es de topologia y no de negocio: depende de cuantos saltos hay entre el cliente
 * y la aplicacion, no de lo que haga Sendik. Por eso es configuracion y no una
 * constante (ADR-0038).
 *
 * <p>La cabecera la puede escribir cualquiera. Lo que no puede es borrar lo que el
 * ultimo salto anade <em>despues</em> de lo suyo, asi que la unica entrada que no
 * se puede elegir es la que cuenta desde el final. Tomar la primera, que es lo que
 * se hacia hasta el 10 de septiembre de 2026, dejaba que quien llama eligiera su
 * propio identificador: con eso se esquivaba el limite de peticiones y la
 * constancia de consentimiento dejaba de probar nada.
 *
 * @param trustedHops entradas que anade la infraestructura al final de la cabecera.
 *     {@code 1} en Cloud Run, que anade la direccion del cliente y nada mas.
 *     {@code 0} significa que no hay proxy delante y que la cabecera se ignora
 *     entera, que es el caso de {@code local}: ahi la conexion es directa y
 *     cualquier valor de la cabecera lo habria puesto quien llama
 */
@Validated
@ConfigurationProperties(prefix = "sendik.client-ip")
public record ClientIpProperties(@Min(0) int trustedHops) {}
