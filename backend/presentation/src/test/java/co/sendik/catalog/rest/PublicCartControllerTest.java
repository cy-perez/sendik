package co.sendik.catalog.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.catalog.dto.CartView;
import co.sendik.catalog.dto.SellerProfileView;
import co.sendik.catalog.model.Cart;
import co.sendik.catalog.model.CartLine;
import co.sendik.catalog.model.Listing;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.model.SellerId;
import co.sendik.catalog.usecase.PreviewCartUseCase;
import co.sendik.shared.file.FileKey;
import co.sendik.shared.money.Money;
import co.sendik.shared.port.out.PublicFileStore;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

/**
 * El borde del carrito de quien no ha entrado. HU-015, criterio 13.
 *
 * <p>Lo que se comprueba aqui: que devuelve <strong>la misma forma</strong> que el carrito de
 * la cuenta —que es toda la razon de que este recurso exista—, que el orden de los
 * identificadores se respeta, y que el tope rechaza antes de llamar a nadie.
 */
class PublicCartControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-10T15:00:00Z");

    private final PreviewCartUseCase leer = mock(PreviewCartUseCase.class);
    private final PublicFileStore almacen = mock(PublicFileStore.class);

    private MockMvc mvc;

    @BeforeEach
    void montarElBorde() {
        when(almacen.direccionDe(any(FileKey.class))).thenReturn(URI.create("https://cdn.sendik.co/toma.jpg"));

        mvc = MockMvcBuilders.standaloneSetup(new PublicCartController(leer, almacen))
                .setControllerAdvice(new ApiExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();
    }

    /**
     * La misma forma que {@code /users/me/cart}: grupos, subtotal y {@code willSplit}.
     *
     * <p>Si esta respuesta se pareciera a un tramo de catalogo, el navegador tendria que
     * agrupar y sumar por su cuenta, que es exactamente lo que ADR-0037 descarto.
     */
    @Test
    void deberia_devolver_la_misma_forma_que_el_carrito_de_la_cuenta_criterio_13() throws Exception {
        SellerId vendedor = new SellerId(UUID.randomUUID());
        when(leer.execute(anyList())).thenReturn(carritoDe(vendedor, 185_000));

        mvc.perform(get("/api/v1/carts").param("ids", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.groups.length()").value(1))
                .andExpect(jsonPath("$.groups[0].subtotal.amount").value(185000))
                .andExpect(jsonPath("$.willSplit").value(false));
    }

    /** El orden que trae el navegador llega intacto al caso de uso. */
    @Test
    void deberia_pasar_los_identificadores_en_el_orden_recibido() throws Exception {
        when(leer.execute(anyList())).thenReturn(new CartView(Cart.de(List.of()), Map.of()));
        ListingId primera = ListingId.nuevo();
        ListingId segunda = ListingId.nuevo();

        mvc.perform(get("/api/v1/carts").param("ids", primera.toString()).param("ids", segunda.toString()))
                .andExpect(status().isOk());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ListingId>> ids = ArgumentCaptor.forClass(List.class);
        verify(leer).execute(ids.capture());
        assertThat(ids.getValue()).containsExactly(primera, segunda);
    }

    /**
     * Un identificador que no es un UUID es 400 y no un 500.
     *
     * <p>Esta si se puede probar aqui porque la conversion la hace el propio metodo, no el
     * proxy de validacion.
     */
    @Test
    void deberia_rechazar_un_identificador_que_no_es_un_uuid() throws Exception {
        mvc.perform(get("/api/v1/carts").param("ids", "no-soy-un-uuid")).andExpect(status().isBadRequest());

        verify(leer, never()).execute(anyList());
    }

    /*
     * El tope de veinte y la lista vacia NO se prueban aqui, y no por descuido.
     *
     * Las restricciones de un parametro de consulta las evalua el proxy que crea `@Validated`,
     * y el montaje autonomo de MockMvc no lo crea: aqui esas anotaciones no existen y la
     * peticion pasa de largo. Es exactamente lo que HU-011 descubrio con el `limit` del
     * catalogo, donde una prueba de este mismo tipo se quedo en verde mientras produccion
     * respondia 500.
     *
     * Viven en CartSecurityTest, con el contexto entero, que es donde el proxy existe y donde
     * el 400 es de verdad.
     */

    private CartView carritoDe(SellerId vendedor, long pesos) {
        Listing publicada = CatalogoDelBorde.publicada(vendedor);
        Cart carrito = Cart.de(List.of(new CartLine(publicada, AHORA, Money.dePesos(pesos))));

        return new CartView(carrito, Map.of(vendedor, new SellerProfileView(vendedor, "Ana Maria", null, true)));
    }
}
