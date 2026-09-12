package co.sendik.shared.rest;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * Limita cuantas peticiones acepta cada origen en las rutas de cuenta.
 *
 * <p>RN-006 protege <em>una</em> cuenta, no el endpoint. Sin este limite, cinco
 * intentos por cuenta no impiden probar una contrasena comun contra todas las
 * cuentas que se quiera, ni usar el registro como emisor de correo gratuito
 * contra la cuota del proveedor, ni mantener bloqueada indefinidamente la cuenta
 * de alguien cuyo correo se conozca.
 *
 * <p>Son dos limites y no uno porque las rutas no se parecen. Escribir
 * credenciales es un acto humano y poco frecuente; renovar la sesion lo hace el
 * navegador solo, y con varias pestanas abiertas se dispara mas veces sin que
 * nadie haga nada raro. Un limite unico tendria que ser el mas flojo de los dos.
 *
 * <p>En {@code /api/v1/auth} se cuenta por IP y no por cuenta: por cuenta ya cuenta
 * RN-006, y en el registro no hay cuenta todavia que contar. La IP llega hasheada,
 * asi que este limite no conserva ningun dato de localizacion
 * (docs/operacion/datos-personales.md).
 *
 * <p><strong>En {@code /api/v1/users} se cuenta por sujeto del token</strong>, y es la
 * misma preocupacion llevada mas lejos. Ahi si hay cuenta a la que atribuir la peticion,
 * asi que contar por IP seria castigar a una oficina o a un operador movil entero por lo
 * que haga uno solo -que es exactamente lo que {@link #clave} ya evita entre rutas- y
 * ademas dejaria de limitar a quien cambie de salida. El sujeto identifica al que de
 * verdad esta pidiendo.
 *
 * <p>Sin ese tercer grupo, toda ruta autenticada quedaba sin ningun tope: cualquier
 * cuenta registrada podia repetir sin freno una lectura que ejecuta un agregado.
 *
 * <p><strong>El cuarto grupo, {@code /api/v1/listings}, tambien cuenta por sujeto,</strong>
 * y llega de lo que dejo a la vista HU-013: el bucle enviar -> retirar -> enviar deja a un
 * vendedor engordar su propio rastro de moderacion sin cota, y ese prefijo no estaba
 * cubierto. Es disponibilidad y coste, no fuga. Se diferencia de los otros tres en la
 * clave, que aqui es solo el sujeto; el motivo esta en {@link #clave}.
 *
 * <p>Es un {@link HandlerInterceptor} y no un filtro de servlet para que la
 * excepcion pase por {@link ApiExceptionHandler} y el 429 salga con el mismo
 * {@code ProblemDetail} que los demas errores. Un filtro corre fuera del
 * despachador y tendria que escribir ese cuerpo a mano.
 */
public class RateLimitInterceptor implements HandlerInterceptor {

    /** Rutas donde se escriben o se piden credenciales. */
    private static final String[] RUTAS_DE_CREDENCIALES = {
        "/api/v1/auth/login",
        "/api/v1/auth/register",
        "/api/v1/auth/resend-verification",
        "/api/v1/auth/verify-email",
        "/api/v1/auth/forgot-password",
        "/api/v1/auth/reset-password",
    };

    private static final String PREFIJO_DE_SESION = "/api/v1/auth/";

    /** Las rutas de cuenta que exigen sesion. */
    private static final String PREFIJO_DE_CUENTA = "/api/v1/users/";

    /**
     * Las rutas de publicacion. La ficha publica cuelga de aqui y no se cuenta; ver
     * {@link #sujetoDelToken}.
     */
    private static final String PREFIJO_DE_PUBLICACION = "/api/v1/listings/";

    /**
     * La coleccion, sin barra final, que es un caso aparte porque **sirve dos cosas
     * distintas segun el metodo**: {@code GET} es el catalogo publico y {@code POST} crea
     * una publicacion. Entra en el grupo por el {@code POST} -crear borradores sin cota es
     * el mismo problema que reenviarlos-, y el {@code GET} no se ve afectado porque llega
     * sin sujeto.
     */
    private static final String COLECCION_DE_PUBLICACIONES = "/api/v1/listings";

    /**
     * La lectura publica del carrito. HU-015.
     *
     * <p><strong>Se cuenta por origen y no por sujeto</strong>, al reves que los dos grupos
     * anteriores, y no hay alternativa: esta ruta es {@code permitAll} y no llega con token.
     * Es la unica de la API sin credencial que dispara mas de una consulta por peticion, asi
     * que sin tope queda abierta una amplificacion de una a veintiuna.
     */
    private static final String CARRITO_ANONIMO = "/api/v1/carts";

    /**
     * La division politico-administrativa. HU-016.
     *
     * <p>Va al grupo de cuenta y no sin cota: es autenticada, asi que hay sujeto a quien
     * contar, y era la unica superficie nueva de HU-016 que se quedaba fuera de los cuatro
     * grupos. Devuelve como mucho 1122 filas y cambia una vez cada varios anos, pero «barata»
     * no es «gratis»: sin grupo, no tiene ninguna.
     */
    private static final String PREFIJO_DE_DIVISION = "/api/v1/locations/";

    private final RateLimiter credenciales;
    private final RateLimiter sesion;
    private final RateLimiter cuenta;
    private final RateLimiter publicaciones;
    private final RateLimiter carritoAnonimo;
    private final ClientIpHasher hasherDeIp;
    private final Clock reloj;

    public RateLimitInterceptor(
            RateLimiter credenciales,
            RateLimiter sesion,
            RateLimiter cuenta,
            RateLimiter publicaciones,
            RateLimiter carritoAnonimo,
            ClientIpHasher hasherDeIp,
            Clock reloj) {
        this.credenciales = credenciales;
        this.sesion = sesion;
        this.cuenta = cuenta;
        this.publicaciones = publicaciones;
        this.carritoAnonimo = carritoAnonimo;
        this.hasherDeIp = hasherDeIp;
        this.reloj = reloj;
    }

    @Override
    public boolean preHandle(HttpServletRequest peticion, HttpServletResponse respuesta, Object manejador) {
        RateLimiter limite = limiteDe(peticion.getRequestURI());
        if (limite == null) {
            return true;
        }

        boolean porSujeto = limite == cuenta || limite == publicaciones;

        // Sin a quien contar no se cuenta. En las rutas de cuenta eso significa sin
        // sujeto en el token, que no deberia ocurrir porque la cadena ya exige sesion;
        // en las de publicacion si ocurre y es lo normal -la ficha publica-; en las de
        // `auth`, sin IP, que pasa en pruebas y en llamadas internas. Dejar pasar es
        // preferible a rechazar a todo el que no traiga direccion.
        String quien = porSujeto ? sujetoDelToken() : hasherDeIp.hashear(peticion);
        if (quien == null) {
            return true;
        }

        Instant ahora = reloj.instant();
        Optional<Duration> espera = limite.registrar(clave(quien, peticion, limite == publicaciones), ahora);
        if (espera.isPresent()) {
            throw new RateLimitExceededException(espera.get());
        }
        return true;
    }

    /**
     * Cada ruta cuenta por separado dentro de su grupo, salvo en publicaciones.
     *
     * <p>Si compartieran cuenta, agotar el limite entrando mal dejaria sin poder
     * registrarse a todo el que salga por la misma IP, que en una oficina o detras
     * de un operador movil es mucha gente que no ha hecho nada.
     *
     * <p><strong>En publicaciones se cuenta al reves: una sola cuenta para todas las
     * rutas del grupo.</strong> Ese motivo no aplica aqui, porque no se cuenta por IP
     * sino por sujeto, y un sujeto es una persona: agotar su propio cupo no deja a nadie
     * mas fuera. Y separar por ruta no acotaria lo que hay que acotar. Lo que se defiende
     * es el bucle enviar -> retirar -> enviar, que engorda el rastro de moderacion sin
     * cota; ese bucle recorre {@code POST} y {@code DELETE} sobre
     * {@code /listings/{id}/submission}, que son dos URI distintas, asi que una cuenta por
     * ruta le daria el cupo entero a cada mitad del ciclo y no frenaria el ciclo.
     */
    private static String clave(String quien, HttpServletRequest peticion, boolean porGrupo) {
        return porGrupo ? quien : quien + " " + peticion.getRequestURI();
    }

    /**
     * Quien pide, segun el token que ya valido la cadena de seguridad.
     *
     * <p>Del contexto de seguridad y nunca de un parametro de la peticion, que es lo que
     * exige backend/CLAUDE.md. Si no hay autenticacion no hay a quien contar: la peticion
     * va a salir 401 de todos modos.
     *
     * <p><strong>El token anonimo no cuenta como sujeto.</strong> La cadena no desactiva
     * el filtro anonimo, asi que una peticion sin sesion no llega con {@code null} sino
     * con un {@code AnonymousAuthenticationToken}, que responde {@code true} a
     * {@code isAuthenticated()} y da {@code "anonymousUser"} como nombre. Mientras el
     * unico grupo por sujeto fue {@code /api/v1/users} daba igual, porque ahi no entra
     * nadie sin sesion. Con las publicaciones si importa: la ficha publica es
     * {@code permitAll} y cuelga del mismo prefijo, de modo que sin esta comprobacion
     * todo el catalogo anonimo compartiria un unico cupo bajo ese nombre y el limite
     * pasaria de proteger el sitio a tumbarlo.
     */
    private static @Nullable String sujetoDelToken() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !autenticacion.isAuthenticated()) {
            return null;
        }
        return autenticacion instanceof AnonymousAuthenticationToken ? null : autenticacion.getName();
    }

    private @Nullable RateLimiter limiteDe(String ruta) {
        for (String credencial : RUTAS_DE_CREDENCIALES) {
            if (ruta.equals(credencial)) {
                return credenciales;
            }
        }
        if (ruta.startsWith(PREFIJO_DE_SESION)) {
            return sesion;
        }
        if (ruta.startsWith(PREFIJO_DE_CUENTA)) {
            return cuenta;
        }
        if (ruta.equals(CARRITO_ANONIMO)) {
            return carritoAnonimo;
        }
        if (ruta.startsWith(PREFIJO_DE_DIVISION)) {
            return cuenta;
        }
        return ruta.startsWith(PREFIJO_DE_PUBLICACION) || ruta.equals(COLECCION_DE_PUBLICACIONES)
                ? publicaciones
                : null;
    }
}
