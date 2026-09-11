package co.sendik.shared.rest;

import jakarta.servlet.http.HttpServletRequest;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.List;
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
 * El razonamiento entero esta en ADR-0038 y se resume en una frase: el principio de
 * la cabecera lo escribe quien llama y el final lo escribe la infraestructura.
 */
public class ClientIpHasher {

    private static final Logger log = LoggerFactory.getLogger(ClientIpHasher.class);

    private static final String CABECERA_REENVIADA = "X-Forwarded-For";

    /** Por que se acabo usando la direccion de la conexion en vez de la cabecera. */
    private enum Respaldo {
        CERO_SALTOS,
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
     * acotadas -cuatro causas, cuatro formas- no hace falta tope, y sin tope no hay carrera
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
     *
     * <p><strong>Declarar mas saltos de los que hay es el error grave, y no el contrario.</strong>
     * Con {@code A} entradas escritas por quien llama y {@code H} saltos reales, el indice es
     * {@code (A + H) - saltosDeConfianza}: si se declaran mas de los que hay, basta que quien
     * llama mande la diferencia en entradas para que el indice caiga <em>dentro de su propio
     * prefijo</em>, y ahi vuelve a elegir su identificador sin que salte ningun aviso, porque
     * el indice es valido. Declarar menos lleva al otro fallo, que es tomar una entrada de la
     * infraestructura -constante- y contar a todo el mundo junto.
     */
    private @Nullable String direccionDe(HttpServletRequest peticion) {
        String deLaConexion = peticion.getRemoteAddr();
        if (saltosDeConfianza == 0) {
            avisarDelRespaldo(Respaldo.CERO_SALTOS, 0);
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
     * <p>Los huecos se descartan al aplanar. Contando desde el final, un hueco que quien
     * llama escriba <em>delante</em> no mueve el indice -por eso no es una defensa-; el que
     * importa es el que queda en la posicion de confianza o despues, como la coma final que
     * deja un proxy mal configurado. Sin descartarlo, esa peticion se iria al respaldo.
     *
     * <p>Y la enumeracion puede venir nula: el contrato del servlet lo admite para
     * contenedores que no dan acceso a las cabeceras por esta via. Con Tomcat no pasa, pero
     * {@code Collections.list(null)} seria un 500 en cada peticion de cuenta.
     */
    private static List<String> entradasReenviadas(HttpServletRequest peticion) {
        Enumeration<String> lineas = peticion.getHeaders(CABECERA_REENVIADA);
        if (lineas == null) {
            return List.of();
        }

        List<String> entradas = new ArrayList<>();
        for (String linea : Collections.list(lineas)) {
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
     * La direccion en forma canonica, o {@code null} si lo que hay no es una direccion.
     *
     * <p>Canoniza y no solo valida, porque dos escrituras de la misma direccion son dos
     * claves distintas y eso parte en dos el cupo de un mismo cliente. De eso se encarga
     * {@link #comoLiteral}; aqui solo se quita lo que no es parte de la direccion: el
     * puerto, que algunos proxies escriben y que cambia en cada conexion -sin quitarlo, cada
     * peticion abriria su propio contador y el limite desapareceria-, y los corchetes de
     * IPv6, sin los cuales toda esa familia caia al respaldo.
     *
     * <p>Lo que no se acepta es lo que abre o cierra el limite por accidente: texto que no es
     * una direccion, y direcciones con algo pegado que varie de una peticion a otra.
     */
    private static @Nullable String normalizada(String entrada) {
        String valor = entrada.strip();

        if (valor.startsWith("[")) {
            int cierre = valor.indexOf(']');
            // Tras el corchete solo cabe el puerto. Cualquier otra cosa pegada no es esto.
            return cierre >= 2 && esSufijoDePuerto(valor.substring(cierre + 1))
                    ? comoLiteral(valor.substring(1, cierre))
                    : null;
        }

        String literal = comoLiteral(valor);
        if (literal != null) {
            return literal;
        }

        // Sin corchetes, un puerto solo cabe detras de IPv4: en IPv6 los dos puntos son de
        // la direccion, y por eso la forma con puerto los exige.
        int separador = valor.indexOf(':');
        return separador > 0 && esPuerto(valor.substring(separador + 1))
                ? comoLiteral(valor.substring(0, separador))
                : null;
    }

    /**
     * La forma canonica de una direccion escrita como literal, o {@code null} si no lo es.
     *
     * <p>{@link InetAddress#ofLiteral} es, por contrato, <strong>literal y nunca resuelve
     * nombres</strong>, que es la razon de usarla y no {@code getByName}: aqui entra texto de
     * quien llama, y una consulta de DNS por peticion seria una via de ataque. Llega con Java
     * 22 y este proyecto va por 25.
     *
     * <p>Lo que da a cambio es la canonizacion que hacia falta y que una comprobacion de
     * caracteres no puede dar: los ceros a la izquierda, la IPv6 expandida y la escrita en
     * mayuscula acaban en la misma clave, y {@code ::ffff:190.85.12.7} acaba en la misma que
     * la forma con puntos. Sin eso, un mismo cliente contaba dos veces con medio cupo cada
     * una.
     *
     * <p>A cambio acepta tambien las formas abreviadas de IPv4 que admite Java -{@code 1.2.3}
     * es {@code 1.2.0.3} y {@code 0} es {@code 0.0.0.0}-, que ningun proxy escribe. No hace
     * dano: la canonizacion es deterministica, asi que ninguna de esas escrituras abre una
     * clave nueva ni parte la de nadie.
     */
    private static @Nullable String comoLiteral(String valor) {
        try {
            return InetAddress.ofLiteral(valor).getHostAddress();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Vacio, o {@code :puerto}: lo unico que puede seguir al corchete de cierre. */
    private static boolean esSufijoDePuerto(String resto) {
        return resto.isEmpty() || (resto.startsWith(":") && esPuerto(resto.substring(1)));
    }

    /** Digitos ASCII a proposito: {@code Character.isDigit} admite los de otros alfabetos. */
    private static boolean esPuerto(String valor) {
        if (valor.isEmpty() || valor.length() > 5) {
            return false;
        }
        for (int i = 0; i < valor.length(); i++) {
            char caracter = valor.charAt(i);
            if (caracter < '0' || caracter > '9') {
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
     * <p><strong>{@code CERO_SALTOS} tambien avisa, y en {@code local} es correcto y aun asi
     * avisa.</strong> Una linea por arranque en la maquina de quien programa es el precio de
     * que en la nube exista alguna senal: ahi el codigo no puede distinguir «no hay proxy
     * delante» de «la variable llego en cero», y lo segundo es la direccion del proxy para
     * todo el mundo y la misma constancia de consentimiento para todos. Era el unico camino
     * de respaldo sin ninguna senal.
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
                        + " revisar sendik.client-ip.trusted-hops (ADR-0038)",
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
