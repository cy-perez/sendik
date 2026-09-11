package co.sendik.shared.rest;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * De donde sale la direccion de quien llama, y por que no de donde parecia.
 *
 * <p>La IP se convierte en hash en el borde y no viaja en claro a ninguna capa
 * interior (docs/operacion/datos-personales.md). Pero antes de hashearla hay que
 * acertar con cual es: hasta el 10 de septiembre de 2026 se tomaba la primera
 * entrada de {@code X-Forwarded-For}, que la escribe quien llama, y eso dejaba que
 * cada quien eligiera su propio identificador (ADR-0036).
 *
 * <p>Cada caso de evasion de aqui falla con el codigo anterior puesto. Y los dos
 * sentidos importan: una correccion que juntara a clientes distintos cambiaria la
 * evasion por una negacion de servicio, asi que tambien se prueba que siguen
 * contando por separado.
 */
class ClientIpHasherTest {

    /** La direccion que anade el proxy: la de verdad. */
    private static final String DEL_CLIENTE = "190.85.12.7";

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

    /** El hash que le corresponde a una direccion, sin cabecera de por medio. */
    private String hashDe(String ip) {
        return sinProxy.hashear(peticionDesde(ip));
    }

    private long avisos() {
        return registro.list.stream()
                .filter(evento -> evento.getLevel() == Level.WARN)
                .count();
    }

    @Test
    void deberia_devolver_un_hash_y_nunca_la_direccion() {
        assertThat(hashDe(DEL_CLIENTE)).isNotNull().doesNotContain(DEL_CLIENTE).hasSize(64);
    }

    @Test
    void deberia_dar_el_mismo_hash_para_la_misma_direccion() {
        assertThat(hashDe(DEL_CLIENTE)).isEqualTo(hashDe(DEL_CLIENTE)).isNotEqualTo(hashDe("190.85.12.8"));
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
        String hash = trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE));

        assertThat(hash).isEqualTo(hashDe(DEL_CLIENTE)).isNotEqualTo(hashDe(INVENTADA));
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
        String enVariasLineas = trasUnProxy.hashear(conVariasLineas(INVENTADA, DEL_CLIENTE));

        assertThat(enVariasLineas)
                .isEqualTo(trasUnProxy.hashear(conCabecera(INVENTADA, DEL_CLIENTE)))
                .isEqualTo(hashDe(DEL_CLIENTE))
                .isNotEqualTo(hashDe(INVENTADA));
    }

    /**
     * <strong>La que prueba que el numero se usa de verdad.</strong>
     *
     * <p>Con un solo salto, «contar desde el final» y «tomar la ultima» son
     * indistinguibles, asi que sin este caso la promesa de ADR-0036 —que el dia que haya un
     * balanceador delante basta con subir la cifra— no la comprueba nada. Y lo que quien
     * llama anada por delante no puede mover el indice.
     */
    @Test
    void deberia_contar_dos_saltos_cuando_se_declaran_dos() {
        String hash = trasDosProxies.hashear(conCabecera(INVENTADA, DEL_CLIENTE, "10.0.0.9"));

        assertThat(hash)
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
     * IPv6 entra, con corchetes y sin ellos. Rechazarla era un fallo cerrado: toda esa
     * familia caia al respaldo, que detras de un proxy es una sola clave compartida.
     */
    @Test
    void deberia_aceptar_ipv6_con_y_sin_corchetes() {
        String sinCorchetes = trasUnProxy.hashear(conCabecera("2001:db8::1"));

        assertThat(sinCorchetes)
                .isEqualTo(trasUnProxy.hashear(conCabecera("[2001:db8::1]")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("[2001:db8::1]:443")))
                .isNotEqualTo(hashDe(DE_LA_CONEXION));
    }

    /** Y la misma IPv6 escrita en mayuscula es la misma, no otro cliente con medio cupo. */
    @Test
    void deberia_contar_junta_la_misma_ipv6_escrita_distinto() {
        assertThat(trasUnProxy.hashear(conCabecera("2001:DB8::1")))
                .isEqualTo(trasUnProxy.hashear(conCabecera("2001:db8::1")));
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
     * Y si lo que hay en esa posicion no se parece a una direccion, tampoco se usa. Se
     * prueban varias formas porque la comprobacion anterior era un filtro de caracteres:
     * dejaba pasar {@code cafe} y {@code ...} por estar escritos con hexadecimal y puntos.
     */
    @Test
    void deberia_caer_a_la_conexion_si_la_entrada_no_parece_una_direccion() {
        for (String basura : new String[] {"unknown", "cafe", "...", "1.2.3", "256.1.1.1", "-1.1.1.1"}) {
            assertThat(trasUnProxy.hashear(conCabecera(basura)))
                    .as("%s no es una direccion", basura)
                    .isEqualTo(hashDe(DE_LA_CONEXION));
        }
    }

    @Test
    void deberia_ignorar_una_cabecera_reenviada_vacia() {
        MockHttpServletRequest peticion = peticionDesde(DEL_CLIENTE);
        peticion.addHeader("X-Forwarded-For", "   ");

        assertThat(trasUnProxy.hashear(peticion)).isEqualTo(hashDe(DEL_CLIENTE));
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

        assertThat(registro.list).isNotEmpty();
        assertThat(registro.list)
                .allSatisfy(evento -> assertThat(evento.getFormattedMessage())
                        .doesNotContain(DEL_CLIENTE)
                        .doesNotContain(INVENTADA)
                        .doesNotContain(DE_LA_CONEXION));
    }

    /**
     * Y el respaldo avisa a WARN, no a DEBUG: en produccion el registro corre a INFO, y
     * detras de un proxy caer al respaldo significa contar a todo el mundo junto.
     */
    @Test
    void deberia_avisar_a_warn_cuando_acaba_usando_la_conexion() {
        trasUnProxy.hashear(peticionDesde(DE_LA_CONEXION));

        assertThat(registro.list).singleElement().satisfies(evento -> {
            assertThat(evento.getLevel()).isEqualTo(Level.WARN);
            assertThat(evento.getFormattedMessage()).doesNotContain(DE_LA_CONEXION);
        });
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
                .isEqualTo(2);
    }
}
