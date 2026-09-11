package co.sendik.shared.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Convierte la direccion IP de quien llama en un hash.
 *
 * <p>La evidencia de consentimiento necesita constar de que la aceptacion vino de
 * algun sitio, no de <em>que</em> sitio. Guardar la IP en claro conservaria un dato
 * de localizacion sin necesidad; el hash sirve como prueba sin guardar la direccion
 * (docs/operacion/datos-personales.md). <strong>No es anonimato:</strong> es SHA-256
 * sin clave sobre un espacio de 2^32, asi que quien tenga un volcado puede
 * recorrerlo. Pasar a HMAC con clave esta abierto y no lo cierra esta clase.
 *
 * <p>Ocurre en el borde a proposito: a partir de aqui la IP en claro no viaja a
 * ninguna capa interior, asi que no puede acabar en un registro por descuido.
 *
 * <p><strong>La direccion se cuenta desde el final de {@code X-Forwarded-For} y
 * nunca desde el principio</strong>, y de eso depende que el hash valga para algo.
 * El razonamiento entero esta en ADR-0036 y se resume en una frase: el principio de
 * la cabecera lo escribe quien llama y el final lo escribe la infraestructura.
 */
public class ClientIpHasher {

    private static final Logger log = LoggerFactory.getLogger(ClientIpHasher.class);

    private static final String CABECERA_REENVIADA = "X-Forwarded-For";

    /** Por que se acabo usando la direccion de la conexion en vez de la cabecera. */
    private enum Respaldo {
        SIN_CABECERA,
        FALTAN_ENTRADAS,
        ENTRADA_INVALIDA
    }

    private final int saltosDeConfianza;

    /**
     * Las causas de respaldo ya avisadas y las formas ya registradas.
     *
     * <p>Ninguna de las dos claves la elige quien llama, y es deliberado: deduplicar por
     * el numero de entradas de la cabecera dejaba que ocho peticiones con ocho formas
     * distintas llenaran el cupo y el diagnostico se quedara mudo para siempre. Con claves
     * acotadas -tres causas, cuatro formas- no hace falta tope, y sin tope no hay carrera
     * entre comprobarlo y anadir.
     */
    private final Set<Respaldo> respaldosAvisados = ConcurrentHashMap.newKeySet();

    private final Set<Integer> formasRegistradas = ConcurrentHashMap.newKeySet();

    public ClientIpHasher(int saltosDeConfianza) {
        this.saltosDeConfianza = saltosDeConfianza;
    }

    public @Nullable String hashear(HttpServletRequest peticion) {
        String ip = direccionDe(peticion);
        if (ip == null || ip.isBlank()) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(ip.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no esta disponible en esta JVM", e);
        }
    }

    /**
     * La direccion de quien llama, contando entradas desde el final de la cabecera.
     *
     * <p>Con {@code saltosDeConfianza} entradas anadidas por la infraestructura, la del
     * cliente es la que esta {@code saltosDeConfianza} posiciones desde el final. Lo que
     * haya delante lo puede haber escrito quien llama y por eso no se mira: la cabecera
     * que manda un atacante queda empujada hacia el principio en cuanto el proxy anade la
     * suya, y ahi ya no la alcanza este calculo.
     *
     * <p>Cuando el resultado no cuadra se cae a {@link HttpServletRequest#getRemoteAddr()},
     * que es de la conexion y no de la peticion, y <strong>nunca</strong> a otra entrada de
     * la cabecera. Las dos salidas son malas y no lo son igual: tomar una entrada elegida
     * por quien llama es no tener limite, y el respaldo detras de un proxy es una sola
     * clave para todo el mundo, porque alli la conexion viene del proxy. Por eso el
     * respaldo avisa, y avisa a WARN: a DEBUG no se veria en produccion.
     */
    private @Nullable String direccionDe(HttpServletRequest peticion) {
        String deLaConexion = peticion.getRemoteAddr();
        if (saltosDeConfianza == 0) {
            return respaldo(deLaConexion);
        }

        List<String> entradas = entradasReenviadas(peticion);
        int indice = entradas.size() - saltosDeConfianza;
        if (indice < 0 || indice >= entradas.size()) {
            avisarDelRespaldo(entradas.isEmpty() ? Respaldo.SIN_CABECERA : Respaldo.FALTAN_ENTRADAS, entradas.size());
            return respaldo(deLaConexion);
        }

        String candidata = normalizada(entradas.get(indice));
        if (candidata == null) {
            avisarDelRespaldo(Respaldo.ENTRADA_INVALIDA, entradas.size());
            return respaldo(deLaConexion);
        }

        registrarLaForma(entradas.size(), indice, candidata.equals(respaldo(deLaConexion)));
        return candidata;
    }

    /**
     * Todas las entradas de la cabecera, en orden, vengan en una linea o en varias.
     *
     * <p><strong>Se leen todas las ocurrencias y no solo la primera</strong>, que es lo que
     * devuelve {@code getHeader} por contrato del servlet. Contar desde el final solo
     * protege si lo que anade la infraestructura esta en la misma lista: si un salto
     * escribiera lo suyo en una linea aparte, mirar unicamente la primera seria mirar una
     * lista escrita entera por quien llama, y la evasion volveria con el arreglo puesto.
     * Aplanarlas en orden es la semantica de lista de RFC 9110 y no depende de como las
     * emita cada salto.
     *
     * <p>Los huecos se descartan al aplanar. Una entrada vacia no es de nadie, y contarla
     * correria el indice una posicion a gusto de quien llama.
     */
    private static List<String> entradasReenviadas(HttpServletRequest peticion) {
        List<String> entradas = new ArrayList<>();
        for (String linea : Collections.list(peticion.getHeaders(CABECERA_REENVIADA))) {
            for (String entrada : linea.split(",")) {
                if (!entrada.isBlank()) {
                    entradas.add(entrada.strip());
                }
            }
        }
        return entradas;
    }

    /** La direccion de la conexion, normalizada si se puede y tal cual si no. */
    private static @Nullable String respaldo(@Nullable String deLaConexion) {
        if (deLaConexion == null) {
            return null;
        }
        String normalizada = normalizada(deLaConexion);
        return normalizada != null ? normalizada : deLaConexion;
    }

    /**
     * La direccion en forma comparable, o {@code null} si lo que hay no es una direccion.
     *
     * <p>Normaliza y no solo valida, porque dos formas de la misma direccion son dos claves
     * distintas y eso parte en dos el cupo de un mismo cliente. Se quita el puerto, que
     * algunos proxies escriben y que cambia en cada conexion -sin esto, cada peticion
     * abriria su propio contador y el limite desapareceria-; se quitan los corchetes de
     * IPv6, sin los cuales toda esa familia caia al respaldo; y se pasa a minuscula, que es
     * lo que hace que {@code 2001:DB8::1} y {@code 2001:db8::1} cuenten juntas.
     *
     * <p>Comprueba la forma y no la existencia. Lo que no se acepta es lo que abre o cierra
     * el limite por accidente: texto que no es una direccion, y direcciones con algo pegado
     * que varie de una peticion a otra.
     */
    private static @Nullable String normalizada(String entrada) {
        String valor = entrada.strip();

        if (valor.startsWith("[")) {
            int cierre = valor.indexOf(']');
            if (cierre < 2) {
                return null;
            }
            // Lo que venga despues del corchete es el puerto, y se descarta con el.
            valor = valor.substring(1, cierre);
        } else {
            int separador = valor.indexOf(':');
            if (separador > 0 && esIpv4(valor.substring(0, separador)) && esPuerto(valor.substring(separador + 1))) {
                valor = valor.substring(0, separador);
            }
        }

        return esIpv4(valor) || esIpv6(valor) ? valor.toLowerCase(Locale.ROOT) : null;
    }

    private static boolean esIpv4(String valor) {
        String[] octetos = valor.split("\\.", -1);
        if (octetos.length != 4) {
            return false;
        }
        for (String octeto : octetos) {
            if (octeto.isEmpty() || octeto.length() > 3 || !esDecimal(octeto) || Integer.parseInt(octeto) > 255) {
                return false;
            }
        }
        return true;
    }

    /**
     * IPv6 por su forma: lleva dos puntos y nada que no sea hexadecimal, dos puntos o punto
     * -el punto admite la forma con IPv4 al final, como {@code ::ffff:190.85.12.7}-.
     */
    private static boolean esIpv6(String valor) {
        if (valor.length() < 2 || valor.length() > 45 || valor.indexOf(':') < 0) {
            return false;
        }
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);
            if (caracter != ':' && caracter != '.' && Character.digit(caracter, 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean esPuerto(String valor) {
        return !valor.isEmpty() && valor.length() <= 5 && esDecimal(valor);
    }

    private static boolean esDecimal(String valor) {
        for (int i = 0; i < valor.length(); i++) {
            if (!Character.isDigit(valor.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Avisa de que la direccion salio de la conexion y no de la cabecera.
     *
     * <p>Es WARN y no DEBUG porque detras de un proxy esto no es un detalle: la conexion
     * viene del proxy, asi que todas las peticiones que caigan aqui comparten una sola
     * clave, el limite pasa de defender el sitio a tumbarlo y la constancia de
     * consentimiento queda igual para todo el mundo. Y produccion corre a INFO, donde un
     * DEBUG no se ve.
     *
     * <p>Una vez por causa y por instancia. Repetirlo en cada peticion seria inundar el
     * registro justo cuando algo va mal; una vez basta para verlo, y las instancias de
     * Cloud Run se reciclan, asi que si la causa sigue ahi vuelve a aparecer.
     *
     * <p>Numeros y nunca direcciones, igual que {@link #registrarLaForma}.
     */
    private void avisarDelRespaldo(Respaldo causa, int entradas) {
        if (!respaldosAvisados.add(causa)) {
            return;
        }
        log.warn(
                "Se usa la direccion de la conexion y no {}: {}, con {} entradas y {} saltos de"
                        + " confianza declarados. Detras de un proxy esto cuenta a todo el mundo junto:"
                        + " revisar sendik.client-ip.trusted-hops (ADR-0036)",
                CABECERA_REENVIADA,
                causa,
                entradas,
                saltosDeConfianza);
    }

    /**
     * Deja constancia de la forma de la cabecera, que es el dato con el que se ajusta
     * {@code sendik.client-ip.trusted-hops} cuando cambia la topologia.
     *
     * <p>Se llama <strong>despues</strong> de decidir y con lo que de verdad se usa. Antes
     * se llamaba al leer la cabecera, asi que anunciaba «se toma la entrada N» aunque esa
     * entrada acabara descartada: quien siguiera el procedimiento de {@code despliegue.md}
     * leeria una forma sana mientras todas las peticiones usaban la conexion.
     *
     * <p>Registra numeros y nunca direcciones: la promesa de esta clase es que la IP en
     * claro no sale del borde, y un registro es exactamente donde no debe acabar. Lo que si
     * se registra es si la entrada elegida coincide con la de la conexion, que es un
     * booleano y dice si de verdad hay un proxy delante.
     */
    private void registrarLaForma(int entradas, int indice, boolean coincideConLaConexion) {
        if (!log.isDebugEnabled()) {
            return;
        }

        // Dos booleanos y no el numero de entradas: ese lo elige quien llama.
        boolean nadaDelante = entradas == saltosDeConfianza;
        int forma = (coincideConLaConexion ? 1 : 0) + (nadaDelante ? 2 : 0);
        if (!formasRegistradas.add(forma)) {
            return;
        }

        log.debug(
                "{} con {} entradas; se toma la numero {}; nada delante de las nuestras: {};"
                        + " coincide con la direccion de la conexion: {}",
                CABECERA_REENVIADA,
                entradas,
                indice + 1,
                nadaDelante,
                coincideConLaConexion);
    }
}
