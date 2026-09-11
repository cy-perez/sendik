package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * De donde sale la direccion de quien llama, y por que no de donde parecia.
 *
 * <p>La IP se convierte en hash en el borde y no viaja en claro a ninguna capa
 * interior (docs/operacion/datos-personales.md). Pero antes de hashearla hay que
 * acertar con cual es: hasta el 10 de septiembre de 2026 se tomaba la primera
 * entrada de {@code X-Forwarded-For}, que la escribe quien llama, y eso dejaba que
 * cada quien eligiera su propio identificador (ADR-0038).
 *
 * <p>Los dos sentidos del error importan. Una correccion que dejara a quien llama
 * elegir su entrada es no tener limite; una que juntara a clientes distintos en una
 * sola clave lo cambia por una negacion de servicio. Aqui se prueban los dos.
 *
 * <p><strong>El hash esperado se calcula aqui y no con la clase que se prueba.</strong>
 * Comparar su salida contra si misma deja pasar cualquier cambio del digest -una sal,
 * un prefijo, el HMAC que ADR-0038 deja abierto-, y {@code ip_hash} es evidencia legal
 * que tiene que seguir siendo comparable entre despliegues.
 */
class ClientIpHasherTest {

    /** La direccion que anade el proxy: la de verdad. */
    private static final String DEL_CLIENTE = "190.85.12.7";

    /** Vector fijo, para que el digest no pueda cambiar sin que falle algo. */
    private static final String HASH_DEL_CLIENTE = "5851ebff3148209e37ea9ea2cd908b3bb5d539d0628105d8056b610da2dc90fb";

    /** Lo que ve la conexion detras de un proxy: el proxy, igual para todo el mundo. */
    private static final String DE_LA_CONEXION = "169.254.1.1";

    /** Una mentira cualquiera de quien llama. */
    private static final String INVENTADA = "203.0.113.1";

    /** Cloud Run anade una entrada al final y nada mas. */
    private final ClientIpHasher trasUnProxy = new ClientIpHasher(1);

    /** Dos saltos: lo que habria con un balanceador delante de Cloud Run. */
    private final ClientIpHasher trasDosProxies = new ClientIpHasher(2);

    /** Sin proxy delante, que es `local`. */
    private final ClientIpHasher sinProxy = new ClientIpHasher(0);

    private ListAppender<ILoggingEvent> registro;
    private Logger logger;
    private Level nivelOriginal;

    @BeforeEach
    void engancharElRegistro() {
        registro = new ListAppender<>();
        registro.start();
        logger = (Logger) LoggerFactory.getLogger(ClientIpHasher.class);
        nivelOriginal = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.addAppender(registro);
    }

    @AfterEach
    void desengancharElRegistro() {
        logger.detachAppender(registro);
        logger.setLevel(nivelOriginal);
    }

    private static MockHttpServletRequest peticionDesde(String ip) {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(ip);
        return peticion;
    }

    /** Una sola linea de cabecera con las entradas en orden, que es lo habitual. */
    private static MockHttpServletRequest conCabecera(String... entradas) {
        MockHttpServletRequest peticion = peticionDesde(DE_LA_CONEXION);
        peticion.addHeader("X-Forwarded-For", String.join(", ", entradas));
        return peticion;
    }

    /** Una linea por elemento: la otra forma legitima de escribir la misma lista. */
    private static MockHttpServletRequest conVariasLineas(String... lineas) {
        MockHttpServletRequest peticion = peticionDesde(DE_LA_CONEXION);
        for (String linea : lineas) {
            peticion.addHeader("X-Forwarded-For", linea);
        }
        return peticion;
    }

    /**
     * El hash que le corresponde a una direccion <strong>ya canonica</strong>, calculado
     * aqui. Si la clase canoniza de otra forma, la comparacion falla, que es la idea.
     */
    private static String hashDe(String direccionCanonica) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(direccionCanonica.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private List<ILoggingEvent> avisos() {
        return registro.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .toList();
    }

    private List<ILoggingEvent> formas() {
        return registro.list.stream()
                .filter(evento -> evento.getLevel() == Level.DEBUG)
                .toList();
    }

    @Test
    void deberia_devolver_un_hash_y_nunca_la_direccion() {
        assertThat(trasUnProxy.hashear(conCabecera(DEL_CLIENTE)))
                .isEqualTo(HASH_DEL_CLIENTE)
                .isEqualTo(hashDe(DEL_CLIENTE))
                .doesNotContain(DEL_CLIENTE)
                .hasSize(64);
    }

    @Test
    void deberia_dar_el_mismo_hash_para_la_misma_direccion() {
        assertThat(trasUnProxy.hashear(conCabecera(DEL_CLIENTE)))
                .isEqualTo(trasUnProxy.hashear(conCabecera(DEL_CLIENTE)))
                .isNotEqualTo(trasUnProxy.hashear(conCabecera("190.85.12.8")));
    }

    /**
     * <strong>La que justifica contar desde el final.</strong>
     *
     * <p>Quien llama escribe el principio de la cabecera; el proxy anade lo suyo al final.
     * Hay que comprobar las dos mitades: que sale la del proxy <em>y</em> que no sale la
     * del cliente. Sin la segunda, una cabecera de una sola entrada hace pasar la prueba
     * con el defecto puesto, porque ahi la primera y la ultima son la misma.
     */
    @Test
    void deberia_tomar_la_ultima_entrada_y_no_la_primera() {
        assertThat(trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE)))
                .isEqualTo(hashDe(DEL_CLIENTE))
                .isNotEqualTo(hashDe(INVENTADA));
    }

    /** Y mentir distinto no abre un contador nuevo, que es toda la defensa. */
    @Test
    void no_deberia_dejar_que_quien_llama_elija_su_direccion() {
        String conUnaMentira = trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE));
        String conOtra = trasUnProxy.hashear(conCabecera("203.0.113.2", "198.51.100.9", DEL_CLIENTE));

        assertThat(conUnaMentira).isEqualTo(conOtra).isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * La otra mitad: dos clientes detras del mismo proxy siguen siendo dos. Un arreglo que
     * los juntara cambiaria una evasion del limite por una negacion de servicio.
     */
    @Test
    void deberia_distinguir_a_dos_clientes_detras_del_mismo_proxy() {
        assertThat(trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE)))
                .isNotEqualTo(trasUnProxy.hashear(conCabecera(INVENTADA, "190.85.12.8")));
    }

    /**
     * <strong>La cabecera repetida, que es la forma fina de volver a elegir la direccion.</strong>
     *
     * <p>{@code getHeader} devuelve solo la primera linea por contrato del servlet. Si un
     * salto escribe lo suyo en una linea aparte, leer unicamente la primera es leer una
     * lista escrita entera por quien llama, y la evasion vuelve con el arreglo puesto. Las
     * dos formas de escribir la misma lista tienen que dar el mismo resultado.
     */
    @Test
    void no_deberia_mirar_solo_la_primera_linea_cuando_la_cabecera_llega_repetida() {
        assertThat(trasUnProxy.hashear(conVariasLineas(INVENTADA, DEL_CLIENTE)))
                .isEqualTo(trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE)))
                .isEqualTo(hashDe(DEL_CLIENTE))
                .isNotEqualTo(hashDe(INVENTADA));
    }

    /**
     * <strong>La que prueba que el numero se usa de verdad.</strong>
     *
     * <p>Con un solo salto, «contar desde el final» y «tomar la ultima» son
     * indistinguibles, asi que sin este caso la promesa de ADR-0038 —que el dia que haya un
     * balanceador delante basta con subir la cifra— no la comprueba nada. Y lo que quien
     * llama anada por delante no puede mover el indice.
     */
    @Test
    void deberia_contar_dos_saltos_cuando_se_declaran_dos() {
        assertThat(trasDosProxies.hashear(conCabecera(INVENTADA, DEL_CLIENTE, "10.0.0.9")))
                .isEqualTo(hashDe(DEL_CLIENTE))
                .isEqualTo(trasDosProxies.hashear(conCabecera("198.51.100.1", INVENTADA, DEL_CLIENTE, "10.0.0.9")))
                .isNotEqualTo(hashDe("10.0.0.9"));
    }

    /**
     * <strong>La entrada vacia ya no apaga la cuenta.</strong>
     *
     * <p>Con una primera entrada en blanco, el hash salia nulo y el interceptor lo leia
     * como «no hay a quien contar»: una sola cabecera fija quitaba el limite entero, y la
     * constancia de consentimiento se guardaba nula.
     */
    @Test
    void no_deberia_quedarse_sin_direccion_por_una_entrada_vacia_del_cliente() {
        assertThat(trasUnProxy.hashear(conCabecera("", DEL_CLIENTE)))
                .isNotNull()
                .isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * El hueco que de verdad importa no es el que quien llama escribe delante -contando
     * desde el final, ese no mueve el indice- sino el que cae en la posicion de confianza:
     * la coma final que deja un proxy mal configurado. Sin descartar los huecos, esa
     * peticion se iria al respaldo, que detras de un proxy es una clave compartida.
     */
    @Test
    void deberia_aguantar_la_coma_final_de_un_proxy_mal_configurado() {
        MockHttpServletRequest peticion = peticionDesde(DE_LA_CONEXION);
        peticion.addHeader("X-Forwarded-For", INVENTADA + ", " + DEL_CLIENTE + ",");

        assertThat(trasUnProxy.hashear(peticion)).isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * El puerto se quita, y no hacerlo es un fallo abierto: un proxy que escriba
     * {@code ip:puerto} le da a cada peticion un identificador distinto, porque el puerto
     * efimero cambia, y el limite desaparece sin que nada lo diga.
     */
    @Test
    void deberia_quitar_el_puerto_de_la_entrada() {
        assertThat(trasUnProxy.hashear(conCabecera(DEL_CLIENTE + ":41237")))
                .isEqualTo(hashDe(DEL_CLIENTE))
                .isEqualTo(trasUnProxy.hashear(conCabecera(DEL_CLIENTE + ":52918")));
    }

    /**
     * Pero solo un puerto. Con cualquier otra cosa pegada la entrada se descarta: dejarla
     * pasar con el sufijo dentro seria una clave nueva por peticion, que es el mismo fallo
     * abierto por otra puerta.
     */
    @ParameterizedTest
    @ValueSource(strings = {"190.85.12.7:", "190.85.12.7:123456", "190.85.12.7:abc", "[2001:db8::1]basura"})
    void no_deberia_aceptar_una_direccion_con_algo_pegado_que_no_sea_un_puerto(String entrada) {
        assertThat(trasUnProxy.hashear(conCabecera(entrada))).isEqualTo(hashDe(DE_LA_CONEXION));
    }

    /**
     * IPv6 entra, con corchetes y sin ellos. Rechazarla era un fallo cerrado: toda esa
     * familia caia al respaldo, que detras de un proxy es una sola clave compartida.
     */
    @Test
    void deberia_aceptar_ipv6_con_y_sin_corchetes() {
        assertThat(trasUnProxy.hashear(conCabecera("2001:db8::1")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("[2001:db8::1]")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("[2001:db8::1]:443")))
                .isEqualTo(hashDe("2001:db8:0:0:0:0:0:1"))
                .isNotEqualTo(hashDe(DE_LA_CONEXION));
    }

    /**
     * <strong>Una direccion, una clave.</strong> Cinco escrituras de lo mismo: en
     * mayuscula, expandida, con ceros a la izquierda y la forma IPv4 mapeada en IPv6.
     * Cada variante que no se canonizara seria el mismo cliente contando dos veces con
     * medio cupo cada una.
     */
    @Test
    void deberia_contar_junta_la_misma_direccion_escrita_de_varias_formas() {
        assertThat(trasUnProxy.hashear(conCabecera("2001:DB8::1")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("2001:0db8:0000:0000:0000:0000:0000:0001")))
                .isEqualTo(hashDe("2001:db8:0:0:0:0:0:1"));

        assertThat(trasUnProxy.hashear(conCabecera("190.085.012.007")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("::ffff:190.85.12.7")))
                .isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * El borde del octeto, por debajo y no solo por encima. Un tope mal puesto en 254
     * mandaria al respaldo a cualquier cliente con un 255, y el respaldo detras de un
     * proxy es la clave compartida de todos.
     */
    @Test
    void deberia_aceptar_un_octeto_de_255() {
        assertThat(trasUnProxy.hashear(conCabecera("190.85.255.7")))
                .isEqualTo(hashDe("190.85.255.7"))
                .isNotEqualTo(hashDe(DE_LA_CONEXION));
    }

    /** Sin proxy delante la cabecera es de quien llama y no se mira. */
    @Test
    void deberia_ignorar_la_cabecera_cuando_no_hay_proxy_delante() {
        MockHttpServletRequest peticion = peticionDesde(DEL_CLIENTE);
        peticion.addHeader("X-Forwarded-For", INVENTADA);

        assertThat(sinProxy.hashear(peticion)).isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * Si la cabecera trae menos entradas de las que anade la infraestructura, algo no
     * cuadra: se usa la conexion y nunca una entrada de la cabecera, que seria justo la
     * que eligio quien llama.
     */
    @Test
    void deberia_caer_a_la_conexion_si_faltan_entradas() {
        assertThat(trasDosProxies.hashear(conCabecera(INVENTADA)))
                .isEqualTo(hashDe(DE_LA_CONEXION))
                .isNotEqualTo(hashDe(INVENTADA));
    }

    /** El caso de produccion que no es ninguno de los anteriores: no llega la cabecera. */
    @Test
    void deberia_caer_a_la_conexion_cuando_no_llega_la_cabecera() {
        assertThat(trasUnProxy.hashear(peticionDesde(DE_LA_CONEXION))).isEqualTo(hashDe(DE_LA_CONEXION));
    }

    /**
     * Y si lo que hay en esa posicion no se parece a una direccion, tampoco se usa.
     *
     * <p>La mitad de los casos llevan dos puntos a proposito: la comprobacion anterior era
     * un filtro de caracteres y aceptaba todo lo escrito con hexadecimal, puntos y dos
     * puntos, asi que una lista de basura sin dos puntos no la habria visto fallar. Un
     * proxy que escriba el nombre del host y no la direccion es el caso realista.
     *
     * <p>Lo que no esta en la lista y podria parecerlo: {@code 0:3}, que Java lee como la
     * direccion {@code 0} con puerto {@code 3} y canoniza a {@code 0.0.0.0}. Es una de las
     * formas abreviadas de IPv4 que admite el parser, no basura, y no parte ninguna clave
     * porque la canonizacion es deterministica.
     */
    @ParameterizedTest
    @ValueSource(
            strings = {
                "unknown",
                "cafe",
                "...",
                "256.1.1.1",
                "-1.1.1.1",
                "unknown:443",
                "host.example.com:443",
                "cafe:",
                "..:.."
            })
    void deberia_caer_a_la_conexion_si_la_entrada_no_parece_una_direccion(String basura) {
        assertThat(trasUnProxy.hashear(conCabecera(basura))).isEqualTo(hashDe(DE_LA_CONEXION));
    }

    @Test
    void deberia_ignorar_una_cabecera_reenviada_vacia() {
        MockHttpServletRequest peticion = peticionDesde(DEL_CLIENTE);
        peticion.addHeader("X-Forwarded-For", "   ");

        assertThat(trasUnProxy.hashear(peticion)).isEqualTo(hashDe(DEL_CLIENTE));
    }

    /**
     * La direccion de la conexion se canoniza igual que la de la cabecera. No es un
     * adorno: con cero saltos -{@code local} y el ensayo de integracion continua- ese es
     * el camino principal, y sin canonizar, dos escrituras de la misma direccion serian
     * dos claves.
     */
    @Test
    void deberia_canonizar_tambien_la_direccion_de_la_conexion() {
        assertThat(sinProxy.hashear(peticionDesde("2001:DB8::1"))).isEqualTo(hashDe("2001:db8:0:0:0:0:0:1"));
    }

    // Sin direccion no hay nada que hashear. No es un error: hay llamadas
    // internas y pruebas que no la traen.
    @Test
    void deberia_devolver_nulo_si_no_hay_direccion() {
        MockHttpServletRequest peticion = new MockHttpServletRequest();
        peticion.setRemoteAddr(null);

        assertThat(sinProxy.hashear(peticion)).isNull();
    }

    /**
     * <strong>La promesa de la clase, y la unica prueba que la sostiene.</strong>
     *
     * <p>El registro existe para poder medir la topologia sin desplegar nada a mano, y dice
     * que lo hace «con numeros y nunca con una direccion», que es la garantia de la Ley
     * 1581 en esta clase. Sin esta prueba, anadir un {@code {}} de mas a esa linea no rompe
     * nada.
     */
    @Test
    void deberia_registrar_la_forma_de_la_cabecera_sin_ninguna_direccion() {
        trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE));

        assertThat(formas()).isNotEmpty();
        assertThat(registro.list)
                .allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                        .doesNotContain(DEL_CLIENTE)
                        .doesNotContain(INVENTADA)
                        .doesNotContain(DE_LA_CONEXION));
    }

    /**
     * Y dice si hay algo delante de las entradas de confianza, que es el booleano con el
     * que {@code despliegue.md} manda ajustar la cifra: sale {@code true} cuando la
     * peticion no trae nada escrito por quien llama. Invertirlo lleva a poner la cifra mal.
     *
     * <p>El fragmento del mensaje tambien se fija aqui, porque el procedimiento del runbook
     * busca por el: reescribirlo deja ese {@code grep} devolviendo cero lineas con el build
     * en verde.
     */
    @Test
    void deberia_decir_si_hay_algo_delante_de_las_entradas_de_confianza() {
        trasUnProxy.hashear(conCabecera(DEL_CLIENTE));

        assertThat(formas())
                .singleElement()
                .satisfies(evento -> assertThat(evento.getFormattedMessage())
                        .contains("X-Forwarded-For con")
                        .contains("nada delante de las nuestras: true"));

        trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE));

        assertThat(formas())
                .last()
                .satisfies(evento ->
                        assertThat(evento.getFormattedMessage()).contains("nada delante de las nuestras: false"));
    }

    /**
     * Y el respaldo avisa a WARN, no a DEBUG: en produccion el registro corre a INFO, y
     * detras de un proxy caer al respaldo significa contar a todo el mundo junto. El
     * fragmento es el que busca el runbook.
     */
    @Test
    void deberia_avisar_a_warn_cuando_acaba_usando_la_conexion() {
        trasUnProxy.hashear(peticionDesde(DE_LA_CONEXION));

        assertThat(avisos())
                .singleElement()
                .satisfies(evento -> assertThat(evento.getFormattedMessage())
                        .contains("Se usa la direccion de la conexion")
                        .doesNotContain(DE_LA_CONEXION));
    }

    /**
     * El aviso es una senal, asi que el camino que cuadra tiene que callar. Si avisara
     * tambien ahi, el WARN que {@code despliegue.md} manda buscar seria ruido permanente y
     * no diria nada.
     */
    @Test
    void no_deberia_avisar_cuando_la_cabecera_cuadra() {
        trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE));
        trasDosProxies.hashear(conCabecera(INVENTADA, DEL_CLIENTE, "10.0.0.9"));

        assertThat(avisos()).isEmpty();
    }

    /**
     * Y ninguno de los cuatro caminos de respaldo suelta una direccion. Es la misma
     * promesa de la Ley 1581 que {@link #deberia_registrar_la_forma_de_la_cabecera_sin_ninguna_direccion}
     * cubre en el camino feliz: el sitio donde alguien anadiria «una ayudita para depurar»
     * es precisamente el aviso de que algo va mal.
     */
    @Test
    void no_deberia_soltar_direcciones_en_ningun_aviso_de_respaldo() {
        sinProxy.hashear(peticionDesde(DEL_CLIENTE));
        trasUnProxy.hashear(peticionDesde(DE_LA_CONEXION));
        trasDosProxies.hashear(conCabecera(INVENTADA));
        trasUnProxy.hashear(conCabecera("unknown"));

        assertThat(avisos()).hasSize(4);
        assertThat(avisos())
                .allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                        .doesNotContain(DEL_CLIENTE)
                        .doesNotContain(INVENTADA)
                        .doesNotContain(DE_LA_CONEXION)
                        .doesNotContain("unknown"));
    }

    /**
     * El aviso no se repite en cada peticion: inundaria el registro justo cuando algo va
     * mal. Una vez por causa, y el cupo no lo puede llenar quien llama variando la
     * cabecera, que es lo que pasaba cuando la clave era el numero de entradas.
     */
    @Test
    void no_deberia_repetir_el_aviso_ni_poder_enmudecerlo_desde_fuera() {
        for (int i = 0; i < 10; i++) {
            trasUnProxy.hashear(conCabecera("no-es-una-direccion-" + i));
        }
        trasUnProxy.hashear(peticionDesde(DE_LA_CONEXION));

        assertThat(avisos())
                .as("uno por causa: la entrada invalida y la cabecera ausente")
                .hasSize(2);
    }

    /**
     * <strong>Cero saltos avisa, y era el unico camino sin ninguna senal.</strong>
     *
     * <p>En {@code local} es lo correcto y aun asi avisa, porque el codigo no puede
     * distinguirlo de una nube con la variable en cero, y eso ultimo es la direccion del
     * proxy para todo el mundo y la misma constancia de consentimiento para todos. Una
     * linea por arranque en la maquina de quien programa es el precio.
     */
    @Test
    void deberia_avisar_cuando_se_declaran_cero_saltos() {
        sinProxy.hashear(peticionDesde(DEL_CLIENTE));
        sinProxy.hashear(peticionDesde(DEL_CLIENTE));

        assertThat(avisos())
                .singleElement()
                .satisfies(evento -> assertThat(evento.getFormattedMessage()).contains("CERO_SALTOS"));
    }
}
