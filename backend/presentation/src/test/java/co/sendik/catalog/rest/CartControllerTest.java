package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.dto.CartItemState;
import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.dto.MergeCartCommand;
import co.sendik.catalog.dto.MergeResult;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.AddToCartUseCase;
import co.sendik.catalog.usecase.MergeCartUseCase;
import co.sendik.catalog.usecase.ReadCartItemStateUseCase;
import co.sendik.catalog.usecase.ReadCartUseCase;
import co.sendik.catalog.usecase.RemoveFromCartUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * El borde del carrito. HU-015.
 *
 * <p>Lo que se comprueba aqui es lo que solo se ve con HTTP delante: la forma del cuerpo, que
 * agregar y quitar respondan 204 y no 200 con cuerpo, que el identificador de quien compra
 * salga del token y no de la ruta, y que la validacion del tope rechace antes de llamar a
 * nadie.
 *
 * <p><strong>La autorizacion no se prueba aqui y no se puede.</strong> El montaje autonomo no
 * tiene cadena de filtros, asi que todo pasa; quien comprueba que sin token no hay carrito es
 * {@code CartSecurityTest}, con el contexto entero.
 */
class CartControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");
    private static final BuyerId QUIEN = new BuyerId(UUID.randomUUID());

    private final AddToCartUseCase agregar = mock(AddToCartUseCase.class);
    private final RemoveFromCartUseCase quitar = mock(RemoveFromCartUseCase.class);
    private final ReadCartItemStateUseCase estado = mock(ReadCartItemStateUseCase.class);
    private final ReadCartUseCase leer = mock(ReadCartUseCase.class);
    private final MergeCartUseCase fusionar = mock(MergeCartUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/toma.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new CartController(agregar, quitar, estado, leer, fusionar, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .setCustomArgumentResolvers(new TokenDePrueba(QUIEN))
                .build();
    }

    // --- La forma del carrito ------------------------------------------------

    @Test
    void deberia_devolver_los_grupos_con_su_vendedor_y_su_subtotal_criterio_14() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        when(leer.execute(QUIEN)).thenReturn(carritoDe(vendedor, "Ana Maria", 185_000));

        mvc.perform(get("/api/v1/users/me/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].sellerId").value(vendedor.toString()))
                .andExpect(jsonPath("$.groups[0].sellerName").value("Ana Maria"))
                .andExpect(jsonPath("$.groups[0].subtotal.amount").value(185000))
                .andExpect(jsonPath("$.groups[0].subtotal.currency").value("COP"))
                .andExpect(jsonPath("$.groups[0].lines.length()").value(1))
                .andExpect(jsonPath("$.groups[0].lines[0].available").value(true))
                .andExpect(jsonPath("$.groups[0].lines[0].priceChanged").value(false));
    }

    /**
     * Criterio 16, comprobado sobre el JSON entero y no campo a campo.
     *
     * <p>Se busca la <strong>clave</strong> {@code "total"} en cualquier nivel de la respuesta,
     * que es lo que RN-096 prohibe. No basta con comprobar que no existe {@code $.total}: el
     * dia que alguien agregue un total dentro de un grupo, esa comprobacion seguiria en verde.
     *
     * <p>Y se busca la clave y no la palabra, porque {@code subtotal} la contiene: buscar la
     * palabra suelta hace fallar justamente al campo que si tiene que estar.
     */
    @Test
    void no_deberia_llevar_ninguna_clave_total_criterio_16() throws Exception {
        when(leer.execute(QUIEN)).thenReturn(carritoDe(new SellerId(UUID.randomUUID()), "Ana Maria", 185_000));

        String cuerpo = mvc.perform(get("/api/v1/users/me/cart"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        assertThat(cuerpo).doesNotContain("\"total\"");
        assertThat(cuerpo).contains("\"subtotal\"");
    }

    @Test
    void deberia_devolver_un_carrito_vacio_como_lista_vacia_criterio_19() throws Exception {
        when(leer.execute(QUIEN)).thenReturn(new CartView(Cart.de(List.of()), Map.of()));

        mvc.perform(get("/api/v1/users/me/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups").isEmpty())
                .andExpect(jsonPath("$.willSplit").value(false));
    }

    /** Un vendedor sin perfil no tumba el carrito: el grupo se pinta con lo que hay. */
    @Test
    void deberia_pintar_el_grupo_aunque_falte_el_perfil_del_vendedor() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        Listing publicada = CatalogoDelBorde.publicada(vendedor);
        Cart carrito = Cart.de(List.of(new CartLine(publicada, AHORA, Money.dePesos(185_000))));
        when(leer.execute(QUIEN)).thenReturn(new CartView(carrito, Map.of()));

        mvc.perform(get("/api/v1/users/me/cart"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups[0].sellerName").doesNotExist())
                .andExpect(jsonPath("$.groups[0].sellerVerified").value(false))
                .andExpect(jsonPath("$.groups[0].lines.length()").value(1));
    }

    // --- Agregar y quitar ----------------------------------------------------

    /** {@code PUT} y 204 sin cuerpo: agregar es idempotente y no crea un recurso nuevo. */
    @Test
    void deberia_responder_204_sin_cuerpo_al_agregar() throws Exception {
        ListingId publicacion = ListingId.nuevo();

        mvc.perform(put("/api/v1/users/me/cart/items/" + publicacion))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        ArgumentCaptor<CartCommand> orden = ArgumentCaptor.forClass(CartCommand.class);
        verify(agregar).execute(orden.capture());
        assertThat(orden.getValue().publicacion()).isEqualTo(publicacion);
    }

    /**
     * Y quien compra sale del token, no de la ruta.
     *
     * <p>Es lo unico que impide llenar el carrito de otra persona, y no hay ruta donde
     * escribir un identificador ajeno: se comprueba que el comando lleve el del {@code sub}.
     */
    @Test
    void deberia_tomar_quien_compra_del_token_y_no_de_la_peticion() throws Exception {
        mvc.perform(put("/api/v1/users/me/cart/items/" + ListingId.nuevo())).andExpect(status().isNoContent());

        ArgumentCaptor<CartCommand> orden = ArgumentCaptor.forClass(CartCommand.class);
        verify(agregar).execute(orden.capture());
        assertThat(orden.getValue().quien()).isEqualTo(QUIEN);
    }

    @Test
    void deberia_responder_204_al_quitar_aunque_no_estuviera() throws Exception {
        mvc.perform(delete("/api/v1/users/me/cart/items/" + ListingId.nuevo())).andExpect(status().isNoContent());

        verify(quitar).execute(any(CartCommand.class));
    }

    // --- El estado del control ----------------------------------------------

    @Test
    void deberia_devolver_el_estado_del_control_criterio_1() throws Exception {
        when(estado.execute(any(CartCommand.class))).thenReturn(new CartItemState(true, true));

        mvc.perform(get("/api/v1/users/me/cart/items/" + ListingId.nuevo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCart").value(true))
                .andExpect(jsonPath("$.eligible").value(true));
    }

    /** 200 con {@code inCart: false} y no 404: el estado del control existe siempre. */
    @Test
    void deberia_responder_200_cuando_no_esta_en_el_carrito() throws Exception {
        when(estado.execute(any(CartCommand.class))).thenReturn(new CartItemState(false, true));

        mvc.perform(get("/api/v1/users/me/cart/items/" + ListingId.nuevo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.inCart").value(false));
    }

    // --- La fusion -----------------------------------------------------------

    @Test
    void deberia_devolver_el_carrito_fusionado_y_lo_que_no_entro_criterio_10() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        ListingId noEntro = ListingId.nuevo();
        when(fusionar.execute(any(MergeCartCommand.class)))
                .thenReturn(new MergeResult(carritoDe(vendedor, "Ana Maria", 185_000), List.of(noEntro)));

        mvc.perform(post("/api/v1/users/me/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[\"" + ListingId.nuevo() + "\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cart.groups.length()").value(1))
                .andExpect(jsonPath("$.notMerged[0]").value(noEntro.toString()));
    }

    /**
     * El tope del cuerpo se rechaza <strong>antes</strong> de llamar al caso de uso.
     *
     * <p>Es la razon de que la restriccion se declare tambien en el DTO: rechazar despues de
     * deserializar mil identificadores ya es haberlos leido, y esa lista la manda quien todavia
     * no ha entrado.
     */
    @Test
    void deberia_rechazar_mas_de_veinte_sin_llamar_al_caso_de_uso_RN_097() throws Exception {
        String demasiados = IntStream.range(0, Cart.MAXIMO_DE_PRODUCTOS + 1)
                .mapToObj(i -> "\"" + UUID.randomUUID() + "\"")
                .collect(Collectors.joining(","));

        mvc.perform(post("/api/v1/users/me/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[" + demasiados + "]}"))
                .andExpect(status().isBadRequest());

        verify(fusionar, never()).execute(any(MergeCartCommand.class));
    }

    @Test
    void deberia_rechazar_una_lista_vacia() throws Exception {
        mvc.perform(post("/api/v1/users/me/cart")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"listingIds\":[]}"))
                .andExpect(status().isBadRequest());

        verify(fusionar, never()).execute(any(MergeCartCommand.class));
    }

    // ------------------------------------------------------------- datos

    private CartView carritoDe(SellerId vendedor, String nombre, long pesos) {
        Listing publicada = CatalogoDelBorde.publicada(vendedor);
        Cart carrito = Cart.de(List.of(new CartLine(publicada, AHORA, Money.dePesos(pesos))));

        return new CartView(carrito, Map.of(vendedor, new SellerProfileView(vendedor, nombre, null, true)));
    }

    /**
     * Resuelve el {@code @AuthenticationPrincipal Jwt} sin cadena de seguridad.
     *
     * <p>El montaje autonomo no la tiene, asi que sin esto el token llegaria nulo y el
     * controlador fallaria por algo que no es lo que se esta probando.
     */
    private record TokenDePrueba(BuyerId quien)
            implements org.springframework.web.method.support.HandlerMethodArgumentResolver {

        @Override
        public boolean supportsParameter(org.springframework.core.MethodParameter parametro) {
            return Jwt.class.isAssignableFrom(parametro.getParameterType());
        }

        @Override
        public Object resolveArgument(
                org.springframework.core.MethodParameter parametro,
                org.springframework.web.method.support.ModelAndViewContainer contenedor,
                org.springframework.web.context.request.NativeWebRequest peticion,
                org.springframework.web.bind.support.WebDataBinderFactory fabrica) {

            return Jwt.withTokenValue("de-prueba")
                    .header("alg", "HS256")
                    .subject(quien.toString())
                    .build();
        }
    }
}
