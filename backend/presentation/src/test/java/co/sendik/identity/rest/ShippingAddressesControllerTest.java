package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.EditShippingAddressCommand;
import co.sendik.identity.dto.RemoveShippingAddressCommand;
import co.sendik.identity.dto.SetDefaultAddressCommand;
import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.exception.AddressBookFullException;
import co.sendik.identity.exception.AddressNotFoundException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.usecase.AddShippingAddressUseCase;
import co.sendik.identity.usecase.EditShippingAddressUseCase;
import co.sendik.identity.usecase.ListShippingAddressesUseCase;
import co.sendik.identity.usecase.RemoveShippingAddressUseCase;
import co.sendik.identity.usecase.SetDefaultShippingAddressUseCase;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * El borde de la libreta de direcciones. HU-016.
 *
 * <p>Lo que se prueba aqui es lo del borde y nada mas: que el identificador de quien pide
 * sale del token y no del cuerpo, que la forma de la respuesta es la del contrato, y que los
 * codigos de error salen con el estado que les toca. Que las reglas se cumplan lo prueban
 * {@code DireccionesDeEntregaTest} y {@code ShippingAddressesSecurityTest}.
 */
class ShippingAddressesControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();
    private static final TokenFamilyId LA_DE_AHORA = TokenFamilyId.nueva();
    private static final ShippingAddressId DIRECCION = ShippingAddressId.nuevo();

    private static final String CUERPO = """
            {"recipientName":"Ana María Ruiz","phone":"3001234567","municipalityCode":"11001",
             "line":"Calle 45 # 12-34","complement":"Apto 802",
             "instructions":"El timbre no sirve","postalCode":"110111"}
            """;

    private final ListShippingAddressesUseCase listado = mock(ListShippingAddressesUseCase.class);
    private final AddShippingAddressUseCase agregado = mock(AddShippingAddressUseCase.class);
    private final EditShippingAddressUseCase edicion = mock(EditShippingAddressUseCase.class);
    private final RemoveShippingAddressUseCase borrado = mock(RemoveShippingAddressUseCase.class);
    private final SetDefaultShippingAddressUseCase marcado = mock(SetDefaultShippingAddressUseCase.class);

    private MockMvc mvc;

    /** Suple lo que en produccion pone Spring Security, como en {@code UsersControllerTest}. */
    private static final class TokenDePrueba implements HandlerMethodArgumentResolver {
        @Override
        public boolean supportsParameter(MethodParameter parametro) {
            return Jwt.class.equals(parametro.getParameterType());
        }

        @Override
        public Object resolveArgument(
                MethodParameter parametro,
                ModelAndViewContainer contenedor,
                NativeWebRequest peticion,
                WebDataBinderFactory fabrica) {
            return Jwt.withTokenValue("da-igual")
                    .header("alg", "HS256")
                    .subject(USUARIO.toString())
                    .claim("sid", LA_DE_AHORA.toString())
                    .build();
        }
    }

    private static ShippingAddressView unaVista() {
        return new ShippingAddressView(
                DIRECCION.toString(),
                "Ana María Ruiz",
                "3001234567",
                "11",
                "Bogotá, D.C.",
                "11001",
                "Bogotá, D.C.",
                true,
                "Calle 45 # 12-34",
                "Apto 802",
                "El timbre no sirve",
                "110111",
                true,
                AHORA);
    }

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(
                        new ShippingAddressesController(listado, agregado, edicion, borrado, marcado))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    /** La libreta va envuelta en un objeto, no como arreglo en la raiz. */
    @Test
    void deberia_devolver_la_libreta_envuelta() throws Exception {
        when(listado.execute(USUARIO)).thenReturn(List.of(unaVista()));

        mvc.perform(get("/api/v1/users/me/addresses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.addresses.length()").value(1))
                .andExpect(jsonPath("$.addresses[0].departmentName").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.addresses[0].municipalityActive").value(true))
                .andExpect(jsonPath("$.addresses[0].isDefault").value(true));
    }

    /** 201 con {@code Location}, que es lo que el contrato pide para crear. */
    @Test
    void deberia_responder_201_con_location_al_crear() throws Exception {
        when(agregado.execute(any())).thenReturn(unaVista());

        mvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/me/addresses/" + DIRECCION))
                .andExpect(jsonPath("$.id").value(DIRECCION.toString()));
    }

    /**
     * El identificador de quien pide sale del {@code sub} del token.
     *
     * <p>Es la propiedad que hace imposible pedir la libreta de otra persona: no hay donde
     * escribir un identificador ajeno, ni en la ruta ni en el cuerpo.
     */
    @Test
    void deberia_tomar_la_cuenta_del_token_y_no_del_cuerpo() throws Exception {
        when(agregado.execute(any())).thenReturn(unaVista());

        mvc.perform(post("/api/v1/users/me/addresses")
                .contentType(MediaType.APPLICATION_JSON)
                .content(CUERPO));

        ArgumentCaptor<AddShippingAddressCommand> comando = ArgumentCaptor.forClass(AddShippingAddressCommand.class);
        verify(agregado).execute(comando.capture());

        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().datos().municipio()).isEqualTo("11001");
    }

    @Test
    void deberia_pasar_el_identificador_de_la_ruta_al_editar() throws Exception {
        when(edicion.execute(any())).thenReturn(unaVista());

        mvc.perform(put("/api/v1/users/me/addresses/" + DIRECCION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk());

        ArgumentCaptor<EditShippingAddressCommand> comando = ArgumentCaptor.forClass(EditShippingAddressCommand.class);
        verify(edicion).execute(comando.capture());

        assertThat(comando.getValue().direccion()).isEqualTo(DIRECCION);
    }

    /** 204 y sin cuerpo: quien quiera saber cual quedo predeterminada relee la libreta. */
    @Test
    void deberia_responder_204_al_borrar() throws Exception {
        mvc.perform(delete("/api/v1/users/me/addresses/" + DIRECCION)).andExpect(status().isNoContent());

        ArgumentCaptor<RemoveShippingAddressCommand> comando =
                ArgumentCaptor.forClass(RemoveShippingAddressCommand.class);
        verify(borrado).execute(comando.capture());

        assertThat(comando.getValue().direccion()).isEqualTo(DIRECCION);
    }

    @Test
    void deberia_responder_204_al_marcar_la_predeterminada() throws Exception {
        mvc.perform(put("/api/v1/users/me/default-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":\"" + DIRECCION + "\"}"))
                .andExpect(status().isNoContent());

        ArgumentCaptor<SetDefaultAddressCommand> comando = ArgumentCaptor.forClass(SetDefaultAddressCommand.class);
        verify(marcado).execute(comando.capture());

        assertThat(comando.getValue().direccion()).isEqualTo(DIRECCION);
    }

    /** Criterio 7: una entrada por campo, no un aviso general. */
    @Test
    void deberia_marcar_cada_campo_que_falta() throws Exception {
        mvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipientName\":\"\",\"phone\":\"\",\"municipalityCode\":\"\",\"line\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors").isArray());
    }

    /** RN-100: 422 y con su propio codigo, no el de validacion generica. */
    @Test
    void deberia_traducir_el_municipio_desconocido_a_422() throws Exception {
        when(agregado.execute(any())).thenThrow(new UnknownMunicipalityException(new MunicipalityCode("99999")));

        mvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_UNKNOWN_MUNICIPALITY"));
    }

    /** RN-101: 422 y no 403. La peticion es legitima, lo que pasa es que no cabe. */
    @Test
    void deberia_traducir_la_libreta_llena_a_422() throws Exception {
        when(agregado.execute(any())).thenThrow(new AddressBookFullException());

        mvc.perform(post("/api/v1/users/me/addresses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_ADDRESS_BOOK_FULL"));
    }

    /** Criterio 15: 404 y nunca 403 sobre una direccion ajena. */
    @Test
    void deberia_traducir_la_direccion_ajena_a_404() throws Exception {
        when(edicion.execute(any())).thenThrow(new AddressNotFoundException(DIRECCION));

        mvc.perform(put("/api/v1/users/me/addresses/" + DIRECCION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("COMMON_NOT_FOUND"));
    }
}
