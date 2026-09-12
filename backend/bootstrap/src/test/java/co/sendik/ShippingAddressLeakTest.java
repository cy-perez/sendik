package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.ShippingAddressData;
import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.TokenFamilyId;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.AccessTokenIssuer;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.usecase.AddShippingAddressUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/**
 * Criterio 18: la direccion de entrega no sale por ninguna respuesta publica. HU-016.
 *
 * <p><strong>Es cierto por construccion</strong> —tabla nueva, sin ningun {@code JOIN} desde
 * el catalogo— y por eso la primera version de esta historia no lo probo. La razon de
 * probarlo igual la escribio la historia: es la unica defensa contra que manana alguien anada
 * la direccion predeterminada a {@code GET /users/me} o a la respuesta del carrito «porque el
 * proceso de compra la va a necesitar».
 *
 * <p>Se recorre <strong>con sesion</strong> y con una direccion guardada de verdad: sin ella,
 * la prueba no demostraria nada porque no habria dato que filtrar. Y se afirma sobre el
 * cuerpo entero de cada respuesta, no sobre campos concretos: lo que se prohibe es que el
 * texto aparezca, venga en el campo que venga.
 */
@SpringBootTest(
        properties = {"sendik.features.checkout=true", "sendik.features.catalog=true", "sendik.features.publishing=true"
        })
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ShippingAddressLeakTest {

    private static final String QUIEN_RECIBE = "Ana María Ruiz";
    private static final String TELEFONO = "3001234567";
    private static final String LINEA = "Calle 45 # 12-34";
    private static final String COMPLEMENTO = "Apto 802";
    private static final String INDICACIONES = "El timbre no sirve";
    private static final String CODIGO_POSTAL = "110111";

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final RegisterUserUseCase registro;
    private final UserRepository usuarios;
    private final AddShippingAddressUseCase agregar;

    private MockMvc mvc;

    ShippingAddressLeakTest(
            WebApplicationContext contexto,
            AccessTokenIssuer emisor,
            RegisterUserUseCase registro,
            UserRepository usuarios,
            AddShippingAddressUseCase agregar) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.registro = registro;
        this.usuarios = usuarios;
        this.agregar = agregar;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void deberia_no_salir_en_ninguna_respuesta_que_no_sea_la_libreta() throws Exception {
        UserId quien = nuevaCuenta();
        agregar.execute(new AddShippingAddressCommand(
                quien,
                new ShippingAddressData(
                        QUIEN_RECIBE, TELEFONO, "11001", LINEA, COMPLEMENTO, INDICACIONES, CODIGO_POSTAL)));

        String token = "Bearer " + tokenDe(quien);

        List<String> respuestas = List.of(
                // El perfil propio y el publico: RN-047 dice que del vendedor se ve nombre,
                // ciudad, sello y reputacion, y nada mas.
                cuerpoDe(get("/api/v1/users/me").header("Authorization", token)),
                cuerpoDe(get("/api/v1/sellers/" + quien).header("Authorization", token)),
                // El catalogo y el carrito, que son las dos respuestas grandes de la Fase 3.
                cuerpoDe(get("/api/v1/listings")),
                cuerpoDe(get("/api/v1/users/me/cart").header("Authorization", token)),
                // Las sesiones, que es la otra lista de la cuenta.
                cuerpoDe(get("/api/v1/users/me/sessions").header("Authorization", token)));

        assertThat(respuestas)
                .allSatisfy(cuerpo -> assertThat(cuerpo)
                        .doesNotContain(QUIEN_RECIBE)
                        .doesNotContain(TELEFONO)
                        .doesNotContain(LINEA)
                        .doesNotContain(COMPLEMENTO)
                        .doesNotContain(INDICACIONES)
                        .doesNotContain(CODIGO_POSTAL));

        // Y la comprobacion al reves, que es la que hace honesta a la anterior: en la libreta
        // si esta. Sin esto, un cambio que dejara de guardar la direccion dejaria las seis
        // afirmaciones de arriba en verde sin proteger nada.
        assertThat(cuerpoDe(get("/api/v1/users/me/addresses").header("Authorization", token)))
                .contains(QUIEN_RECIBE)
                .contains(LINEA);
    }

    private String cuerpoDe(org.springframework.test.web.servlet.RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion)
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UserId nuevaCuenta() {
        String correo = "filtracion-" + UUID.randomUUID() + "@ejemplo.co";

        registro.execute(new RegisterUserCommand(
                correo,
                "una-contrasena-larga-de-verdad",
                "Quien Compra",
                LocalDate.of(1990, 3, 4),
                "es",
                true,
                true,
                "hash-de-ip"));

        return usuarios.buscarPorCorreo(new Email(correo)).orElseThrow().id();
    }

    private String tokenDe(UserId id) {
        User cuenta = User.rehidratar(
                id,
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

        return emisor.emitir(cuenta, TokenFamilyId.nueva(), Instant.now()).value();
    }
}
