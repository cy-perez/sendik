package co.sendik.identity.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.OriginAddressView;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.exception.PhoneRequiredException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.usecase.DeleteOriginAddressUseCase;
import co.sendik.identity.usecase.ReadOriginAddressUseCase;
import co.sendik.identity.usecase.SaveOriginAddressUseCase;
import co.sendik.shared.rest.ApiExceptionHandler;
import java.time.Instant;
import java.util.Optional;
import org.hamcrest.Matchers;
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
 * El borde de la direccion de origen. HU-017.
 *
 * <p>Lo del borde y nada mas: que la cuenta sale del token, que sin origen es 204 y no
 * 404, que la forma de la respuesta es la del contrato y que los codigos salen con su
 * estado. Que las reglas se cumplan lo prueban {@code DireccionDeOrigenTest} y
 * {@code OriginAddressSecurityTest}.
 */
class OriginAddressControllerTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T10:00:00Z");
    private static final UserId USUARIO = UserId.nuevo();
    private static final TokenFamilyId LA_DE_AHORA = TokenFamilyId.nueva();

    private static final String CUERPO = """
            {"municipalityCode":"11001","line":"Carrera 15 # 93-47","complement":"Local 3",
             "instructions":"Entrar por el parqueadero","postalCode":"110221"}
            """;

    private final ReadOriginAddressUseCase lectura = mock(ReadOriginAddressUseCase.class);
    private final SaveOriginAddressUseCase guardado = mock(SaveOriginAddressUseCase.class);
    private final DeleteOriginAddressUseCase borrado = mock(DeleteOriginAddressUseCase.class);

    private MockMvc mvc;

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

    private static OriginAddressView unaVista() {
        return new OriginAddressView(
                "11",
                "Bogotá, D.C.",
                "11001",
                "Bogotá, D.C.",
                true,
                "Carrera 15 # 93-47",
                "Local 3",
                "Entrar por el parqueadero",
                "110221",
                "Ana María",
                "3001234567",
                AHORA);
    }

    @BeforeEach
    void montarElBorde() {
        mvc = MockMvcBuilders.standaloneSetup(new OriginAddressController(lectura, guardado, borrado))
                .setControllerAdvice(new ApiExceptionHandler())
                .setCustomArgumentResolvers(new TokenDePrueba())
                .build();
    }

    @Test
    void deberia_devolver_el_origen_con_el_departamento_y_el_remitente() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.of(unaVista()));

        mvc.perform(get("/api/v1/users/me/origin-address"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departmentName").value("Bogotá, D.C."))
                .andExpect(jsonPath("$.municipalityCode").value("11001"))
                .andExpect(jsonPath("$.municipalityActive").value(true))
                .andExpect(jsonPath("$.line").value("Carrera 15 # 93-47"))
                .andExpect(jsonPath("$.senderName").value("Ana María"))
                .andExpect(jsonPath("$.senderPhone").value("3001234567"));
    }

    /** Criterio 1: sin origen es 204 y no 404, porque el 404 ya lo usa la bandera apagada. */
    @Test
    void deberia_responder_204_sin_cuerpo_cuando_no_hay_origen() throws Exception {
        when(lectura.execute(USUARIO)).thenReturn(Optional.empty());

        mvc.perform(get("/api/v1/users/me/origin-address"))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));
    }

    /** La cuenta sale del {@code sub} del token: no hay donde escribir una ajena. */
    @Test
    void deberia_guardar_tomando_la_cuenta_del_token() throws Exception {
        when(guardado.execute(any())).thenReturn(unaVista());

        mvc.perform(put("/api/v1/users/me/origin-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.municipalityName").value("Bogotá, D.C."));

        ArgumentCaptor<SaveOriginAddressCommand> comando = ArgumentCaptor.forClass(SaveOriginAddressCommand.class);
        verify(guardado).execute(comando.capture());

        assertThat(comando.getValue().usuario()).isEqualTo(USUARIO);
        assertThat(comando.getValue().datos().municipio()).isEqualTo("11001");
        assertThat(comando.getValue().datos().linea()).isEqualTo("Carrera 15 # 93-47");
        assertThat(comando.getValue().datos().indicaciones()).isEqualTo("Entrar por el parqueadero");
    }

    @Test
    void deberia_responder_204_al_borrar() throws Exception {
        mvc.perform(delete("/api/v1/users/me/origin-address")).andExpect(status().isNoContent());

        verify(borrado).execute(USUARIO);
    }

    /** Criterio 8: una entrada por campo, no un aviso general. */
    @Test
    void deberia_marcar_cada_campo_que_falta() throws Exception {
        mvc.perform(put("/api/v1/users/me/origin-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"municipalityCode\":\"\",\"line\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[*].field", Matchers.hasItems("municipalityCode", "line")));
    }

    /** El borde mide lo mismo que el dominio: «Cl  7» son cuatro caracteres, no cinco. */
    @Test
    void deberia_normalizar_antes_de_medir() throws Exception {
        mvc.perform(put("/api/v1/users/me/origin-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"municipalityCode\":\"11001\",\"line\":\"Cl  7\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("line"));
    }

    /** Criterio 5: 422 con su propio codigo. */
    @Test
    void deberia_traducir_el_telefono_que_falta_a_422() throws Exception {
        when(guardado.execute(any())).thenThrow(new PhoneRequiredException());

        mvc.perform(put("/api/v1/users/me/origin-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_PHONE_REQUIRED"));
    }

    /** RN-100, criterio 7. */
    @Test
    void deberia_traducir_el_municipio_desconocido_a_422() throws Exception {
        when(guardado.execute(any())).thenThrow(new UnknownMunicipalityException());

        mvc.perform(put("/api/v1/users/me/origin-address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("USER_UNKNOWN_MUNICIPALITY"));
    }
}
