package co.sendik;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.Category;
import co.sendik.catalog.model.CategoryId;
import co.sendik.catalog.model.Color;
import co.sendik.catalog.model.Condition;
import co.sendik.catalog.model.Description;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.MeasurementGroup;
import co.sendik.catalog.model.MeasurementKind;
import co.sendik.catalog.model.Measurements;
import co.sendik.catalog.model.ModeratorId;
import co.sendik.catalog.model.Product;
import co.sendik.catalog.model.ProductId;
import co.sendik.catalog.model.ProductImage;
import co.sendik.catalog.model.ProductImageId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.model.ShippingDimensions;
import co.sendik.catalog.model.Size;
import co.sendik.catalog.model.SizeSystem;
import co.sendik.catalog.model.Title;
import co.sendik.catalog.port.out.Categories;
import co.sendik.catalog.port.out.ListingRepository;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.file.ImageContentType;
import co.sendik.shared.file.ImageDimensions;
import co.sendik.shared.money.Money;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Quien puede hacer que con el carrito. HU-015.
 *
 * <p><strong>Es la prueba que las de {@code presentation} no pueden hacer.</strong> Alli el
 * montaje es autonomo y no tiene cadena de filtros, asi que la autorizacion no existe y todo
 * responde 200. Y las de {@code application} tampoco: el 403 de RN-092, el 404 de RN-068 y el
 * 422 de RN-097 son excepciones de dominio hasta que alguien las traduce, y quien las traduce
 * es el manejador global, que solo existe con el contexto entero.
 *
 * <p>Lo que se verifica aqui:
 *
 * <ul>
 *   <li>Las cinco rutas de {@code /users/me/cart} exigen token, y el carrito de otra persona
 *       no se puede nombrar: el identificador sale del {@code sub}.
 *   <li>{@code /carts} <strong>no</strong> lo exige, que es la razon de que exista.
 *   <li>Agregar lo propio responde 403 con su codigo (RN-092).
 *   <li>Agregar lo que no esta publicado responde 404, igual que lo que no existe (RN-068).
 *   <li>El tope responde 422 con su codigo y nombra el tope (RN-097, criterio 7).
 *   <li>La respuesta <strong>no lleva un total</strong> por ninguna parte (criterio 16).
 * </ul>
 */
// Las tres banderas, que es la combinacion con la que el carrito funciona de verdad: cuelga
// del catalogo publico, y la lectura de una publicacion por identificador la sirve el
// controlador de HU-007.
@SpringBootTest(
        properties = {"sendik.features.catalog=true", "sendik.features.publishing=true", "sendik.features.checkout=true"
        })
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class CartSecurityTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final String CUALQUIERA = UUID.randomUUID().toString();

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final ListingRepository publicaciones;
    private final Categories categorias;
    private final JdbcClient jdbc;

    private MockMvc mvc;

    CartSecurityTest(
            WebApplicationContext contexto,
            AccessTokenIssuer emisor,
            ListingRepository publicaciones,
            Categories categorias,
            JdbcClient jdbc) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.publicaciones = publicaciones;
        this.categorias = categorias;
        this.jdbc = jdbc;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // --- El carrito es privado: sin token no hay nada ------------------------

    @Test
    void deberia_negar_las_cinco_rutas_sin_token() throws Exception {
        mvc.perform(get("/api/v1/users/me/cart")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me/cart/items/" + CUALQUIERA)).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/users/me/cart/items/" + CUALQUIERA)).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/v1/users/me/cart/items/" + CUALQUIERA)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/users/me/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[\"" + CUALQUIERA + "\"]}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deberia_dejar_ver_su_carrito_a_cualquiera_con_sesion() throws Exception {
        mvc.perform(get("/api/v1/users/me/cart").header("Authorization", "Bearer " + tokenDe(nuevoUsuario())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isArray());
    }

    /**
     * El carrito de una persona no aparece en el de otra, y no hay forma de pedirlo.
     *
     * <p>No es solo que la ruta no acepte un identificador ajeno: es que el que se usa sale del
     * {@code sub} del token, asi que no existe donde escribir el de otra persona.
     */
    @Test
    void no_deberia_ver_el_carrito_de_otra_persona() throws Exception {
        UUID una = nuevoUsuario();
        UUID otra = nuevoUsuario();
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));
        mvc.perform(put("/api/v1/users/me/cart/items/" + publicada.id())
                        .header("Authorization", "Bearer " + tokenDe(una)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/me/cart").header("Authorization", "Bearer " + tokenDe(otra)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    // --- La lectura anonima si es publica ------------------------------------

    /**
     * {@code /carts} sin token. Es toda la razon de que este recurso exista: sin el, el
     * navegador de quien no ha entrado tendria que agrupar y sumar por su cuenta (ADR-0037).
     */
    @Test
    void deberia_servir_el_carrito_anonimo_sin_token_criterio_13() throws Exception {
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));

        mvc.perform(get("/api/v1/carts").param("ids", publicada.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].subtotal.amount").value(185000));
    }

    /** RN-068: a quien no ha entrado no se le dice que un identificador existe y no se ve. */
    @Test
    void no_deberia_devolver_lo_que_no_esta_publicado_al_anonimo() throws Exception {
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));
        publicaciones.guardar(publicada.pausar(AHORA));

        mvc.perform(get("/api/v1/carts").param("ids", publicada.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty());
    }

    /**
     * El tope tambien en la lectura anonima, y por encima 400.
     *
     * <p>Es la unica defensa contra una peticion arbitraria de quien no ha entrado, que es
     * exactamente la razon de que RN-097 exista.
     */
    @Test
    void deberia_rechazar_mas_de_veinte_identificadores_anonimos_RN_097() throws Exception {
        String demasiados = IntStream.range(0, Cart.MAXIMO_DE_PRODUCTOS + 1)
                .mapToObj(i -> UUID.randomUUID().toString())
                .collect(Collectors.joining(","));

        mvc.perform(get("/api/v1/carts").param("ids", demasiados)).andExpect(status().isBadRequest());
    }

    /**
     * Y sin identificadores tampoco: 400 y no una respuesta vacia.
     *
     * <p>Vive aqui y no en {@code PublicCartControllerTest} porque el montaje autonomo de
     * MockMvc no crea el proxy de {@code @Validated}, asi que alli esta comprobacion pasaria
     * sin comprobar nada. Es la leccion que HU-011 dejo escrita con el {@code limit} del
     * catalogo.
     */
    @Test
    void deberia_rechazar_una_lectura_anonima_sin_identificadores() throws Exception {
        mvc.perform(get("/api/v1/carts")).andExpect(status().isBadRequest());
    }

    // --- RN-092: nadie agrega lo suyo ---------------------------------------

    @Test
    void deberia_rechazar_agregar_la_publicacion_propia_RN_092() throws Exception {
        UUID vendedor = nuevoUsuario();
        Listing suya = publicadaDe(new SellerId(vendedor));

        mvc.perform(put("/api/v1/users/me/cart/items/" + suya.id())
                        .header("Authorization", "Bearer " + tokenDe(vendedor)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CATALOG_SELF_CART_FORBIDDEN"));
    }

    /** Y sobre la propia el control no se ofrece: {@code eligible} es falso (criterio 5). */
    @Test
    void no_deberia_ofrecer_el_control_sobre_la_publicacion_propia_criterio_5() throws Exception {
        UUID vendedor = nuevoUsuario();
        Listing suya = publicadaDe(new SellerId(vendedor));

        mvc.perform(get("/api/v1/users/me/cart/items/" + suya.id())
                        .header("Authorization", "Bearer " + tokenDe(vendedor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCart").value(false))
                .andExpect(jsonPath("$.eligible").value(false));
    }

    // --- RN-068 -------------------------------------------------------------

    /**
     * Lo pausado y lo inexistente responden igual, y con el mismo codigo.
     *
     * <p>Si lo pausado respondiera algo distinto, cualquiera podria distinguir con dos
     * peticiones un identificador real de uno inventado, que es justo lo que RN-068 evita.
     *
     * <p>Y el codigo es {@code COMMON_NOT_FOUND} y no uno propio del catalogo, que es lo que ya
     * decidio HU-011: un codigo que dijera «publicacion no encontrada» seria distinguible de
     * una ruta que no existe, y eso ya es decir algo sobre el identificador.
     */
    @Test
    void deberia_responder_lo_mismo_para_lo_pausado_y_lo_inexistente_RN_068() throws Exception {
        UUID quien = nuevoUsuario();
        Listing pausada = publicadaDe(new SellerId(nuevoUsuario()));
        publicaciones.guardar(pausada.pausar(AHORA));

        mvc.perform(put("/api/v1/users/me/cart/items/" + pausada.id())
                        .header("Authorization", "Bearer " + tokenDe(quien)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));

        mvc.perform(put("/api/v1/users/me/cart/items/" + ListingId.nuevo())
                        .header("Authorization", "Bearer " + tokenDe(quien)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }

    // --- RN-097: el tope ----------------------------------------------------

    /**
     * Criterio 7: se rechaza con codigo propio.
     *
     * <p><strong>El numero del tope no viaja en la respuesta</strong>, y no es un olvido: el
     * cuerpo de error de este proyecto lleva {@code code} y {@code traceId} y nada mas, porque
     * el mensaje que ve la persona lo escribe el frontend traduciendo el codigo. Quien dice
     * «caben veinte» es la clave de Transloco, y el numero lo tiene el frontend porque lo
     * necesita de todas formas: el carrito sin sesion vive en el navegador y ahi no hay
     * servidor que lo haga cumplir.
     */
    @Test
    void deberia_rechazar_el_producto_veintiuno_RN_097() throws Exception {
        UUID quien = nuevoUsuario();
        String token = tokenDe(quien);

        for (int i = 0; i < Cart.MAXIMO_DE_PRODUCTOS; i++) {
            Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));
            mvc.perform(put("/api/v1/users/me/cart/items/" + publicada.id()).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNoContent());
        }

        Listing unaMas = publicadaDe(new SellerId(nuevoUsuario()));

        mvc.perform(put("/api/v1/users/me/cart/items/" + unaMas.id()).header("Authorization", "Bearer " + token))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CATALOG_CART_FULL"));
    }

    // --- El contrato no tiene total -----------------------------------------

    /**
     * Criterio 16, comprobado en el contrato y no solo en la plantilla.
     *
     * <p>RN-076 obliga a tres cifras —precio base, envio y total— y aqui solo existe la
     * primera. Un campo {@code total} con la suma de los subtotales mentiria sobre lo que se va
     * a pagar, y en el contrato es donde mas caro sale corregirlo despues.
     */
    @Test
    void no_deberia_publicar_ningun_total_criterio_16() throws Exception {
        UUID quien = nuevoUsuario();
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));
        mvc.perform(put("/api/v1/users/me/cart/items/" + publicada.id())
                        .header("Authorization", "Bearer " + tokenDe(quien)))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/users/me/cart").header("Authorization", "Bearer " + tokenDe(quien)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").doesNotExist())
                .andExpect(jsonPath("$.groups[0].total").doesNotExist())
                .andExpect(jsonPath("$.groups[0].subtotal").exists());
    }

    // --- La fusion ----------------------------------------------------------

    /** Criterio 9: union, y devuelve el carrito ya fusionado para no pedirlo otra vez. */
    @Test
    void deberia_fusionar_lo_que_traia_el_navegador_criterio_9() throws Exception {
        UUID quien = nuevoUsuario();
        Listing publicada = publicadaDe(new SellerId(nuevoUsuario()));

        mvc.perform(post("/api/v1/users/me/cart")
                        .header("Authorization", "Bearer " + tokenDe(quien))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[\"" + publicada.id() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.groups.length()").value(1))
                .andExpect(jsonPath("$.notMerged").isEmpty());
    }

    /** Y lo que no entro se dice: aqui, lo propio (RN-092). */
    @Test
    void deberia_anotar_lo_que_no_entro_en_la_fusion() throws Exception {
        UUID vendedor = nuevoUsuario();
        Listing suya = publicadaDe(new SellerId(vendedor));

        mvc.perform(post("/api/v1/users/me/cart")
                        .header("Authorization", "Bearer " + tokenDe(vendedor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[\"" + suya.id() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.groups").isEmpty())
                .andExpect(jsonPath("$.notMerged.length()").value(1));
    }

    /** El tope del cuerpo, antes de deserializar mil identificadores. */
    @Test
    void deberia_rechazar_una_fusion_de_mas_de_veinte() throws Exception {
        String demasiados = IntStream.range(0, Cart.MAXIMO_DE_PRODUCTOS + 1)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));

        mvc.perform(post("/api/v1/users/me/cart")
                        .header("Authorization", "Bearer " + tokenDe(nuevoUsuario()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[" + demasiados + "]}"))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------- datos

    private String tokenDe(UUID id) {
        User cuenta = User.rehidratar(
                new UserId(id),
                new Email("alguien@sendik.co"),
                new DisplayName("Quien compra"),
                new BirthDate(LocalDate.of(1990, 3, 4)),
                null,
                null,
                null,
                UserLocale.ES,
                UserStatus.ACTIVE,
                Instant.now(),
                Set.of(),
                Instant.now());

        // El sub del token tiene que ser la cuenta concreta: de el sale el BuyerId, y las
        // pruebas de RN-092 dependen de que sea uno y no otro.
        return emisor.emitir(cuenta, TokenFamilyId.nueva(), Instant.now()).value();
    }

    private UUID nuevoUsuario() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return id;
    }

    private Listing publicadaDe(SellerId vendedor) {
        Listing borrador = conTomas(Listing.crearBorrador(ListingId.nuevo(), producto(vendedor), AHORA));
        Listing enRevision = publicaciones.guardar(borrador.enviarARevision(AHORA));

        return publicaciones.guardar(enRevision.aprobar(new ModeratorId(nuevoUsuario()), AHORA));
    }

    private static Listing conTomas(Listing publicacion) {
        Listing resultado = publicacion;
        for (int posicion = 0; posicion < ProductImage.TOMAS_DE_LA_SECUENCIA; posicion++) {
            resultado = resultado.conImagen(
                    ProductImage.toma(
                            ProductImageId.nuevo(),
                            new FileKey("productos/" + UUID.randomUUID() + ".jpg"),
                            posicion,
                            new ImageDimensions(900, 1200),
                            120_000L,
                            ImageContentType.JPEG),
                    AHORA);
        }
        return resultado;
    }

    private Product producto(SellerId vendedor) {
        Category camisas = categoriaPorSlug("camisas-y-blusas");

        Map<MeasurementKind, BigDecimal> valores = new EnumMap<>(MeasurementKind.class);
        MeasurementGroup.TOP.obligatorias().forEach(medida -> valores.put(medida, new BigDecimal("50.0")));

        return Product.crear(
                ProductId.nuevo(),
                vendedor,
                camisas,
                new Title("Camisa de lino color hueso"),
                new Description("Usada dos veces."),
                null,
                Condition.LIKE_NEW,
                new Size(SizeSystem.ALPHA, "M"),
                new Measurements(valores),
                Color.BEIGE,
                Money.dePesos(185_000),
                new ShippingDimensions(600, new BigDecimal("30.0"), new BigDecimal("20.0"), new BigDecimal("10.0")),
                null,
                null);
    }

    private Category categoriaPorSlug(String slug) {
        UUID id = jdbc.sql("SELECT id FROM categories WHERE slug = :s")
                .param("s", slug)
                .query(UUID.class)
                .single();

        return categorias.buscar(new CategoryId(id)).orElseThrow();
    }
}
