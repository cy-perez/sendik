package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.sendik.identity.dto.OriginAddressData;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.dto.UpdateProfileCommand;
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
import co.sendik.identity.usecase.RegisterUserUseCase;
import co.sendik.identity.usecase.SaveOriginAddressUseCase;
import co.sendik.identity.usecase.UpdateProfileUseCase;
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
 * Criterio 16: la direccion de origen no sale por ninguna respuesta publica. HU-017.
 *
 * <p>Es cierto por construccion y se prueba igual, por lo mismo que
 * {@code ShippingAddressLeakTest}: es la unica defensa contra que manana alguien anada la
 * linea del origen al perfil publico del vendedor «porque la guia la va a necesitar». Y
 * aqui hay una tentacion mas concreta: el perfil publico ya lleva la ciudad en la
 * clasificacion de datos personales, y esta historia decidio no publicar el municipio
 * todavia. La afirmacion cubre tambien el telefono del vendedor.
 */
@SpringBootTest(
        properties = {"sendik.features.checkout=true", "sendik.features.catalog=true", "sendik.features.publishing=true"
        })
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class OriginAddressLeakTest {

    private static final String TELEFONO = "3009876543";
    private static final String LINEA = "Carrera 15 # 93-47";
    private static final String COMPLEMENTO = "Local 3";
    private static final String INDICACIONES = "Entrar por el parqueadero";
    private static final String CODIGO_POSTAL = "110221";

    private final WebApplicationContext contexto;
    private final AccessTokenIssuer emisor;
    private final RegisterUserUseCase registro;
    private final UserRepository usuarios;
    private final UpdateProfileUseCase perfil;
    private final SaveOriginAddressUseCase guardar;

    private MockMvc mvc;

    OriginAddressLeakTest(
            WebApplicationContext contexto,
            AccessTokenIssuer emisor,
            RegisterUserUseCase registro,
            UserRepository usuarios,
            UpdateProfileUseCase perfil,
            SaveOriginAddressUseCase guardar) {
        this.contexto = contexto;
        this.emisor = emisor;
        this.registro = registro;
        this.usuarios = usuarios;
        this.perfil = perfil;
        this.guardar = guardar;
    }

    @BeforeEach
    void montarConLaCadenaDeSeguridad() {
        mvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    @Test
    void deberia_no_salir_en_ninguna_respuesta_publica() throws Exception {
        UserId quien = nuevaCuenta();
        perfil.execute(new UpdateProfileCommand(quien, "Quien Vende", null, TELEFONO));
        guardar.execute(new SaveOriginAddressCommand(
                quien, new OriginAddressData("11001", LINEA, COMPLEMENTO, INDICACIONES, CODIGO_POSTAL)));

        String token = "Bearer " + tokenDe(quien);

        List<String> publicas = List.of(
                // El perfil publico del vendedor: nombre, sello y reputacion, y nada mas.
                cuerpoDe(get("/api/v1/sellers/" + quien)),
                cuerpoDe(get("/api/v1/listings")),
                cuerpoDe(get("/api/v1/users/me/cart").header("Authorization", token)),
                cuerpoDe(get("/api/v1/users/me/sessions").header("Authorization", token)));

        assertThat(publicas)
                .allSatisfy(cuerpo -> assertThat(cuerpo)
                        .doesNotContain(TELEFONO)
                        .doesNotContain(LINEA)
                        .doesNotContain(COMPLEMENTO)
                        .doesNotContain(INDICACIONES)
                        .doesNotContain(CODIGO_POSTAL));

        // El perfil propio lleva la ciudad derivada y el telefono, que son del titular, pero
        // nunca la linea ni las indicaciones del origen.
        assertThat(cuerpoDe(get("/api/v1/users/me").header("Authorization", token)))
                .contains("Bogotá, D.C.")
                .doesNotContain(LINEA)
                .doesNotContain(COMPLEMENTO)
                .doesNotContain(INDICACIONES);

        // Y la comprobacion al reves, que es la que hace honesta a la anterior.
        assertThat(cuerpoDe(get("/api/v1/users/me/origin-address").header("Authorization", token)))
                .contains(LINEA)
                .contains(INDICACIONES);
    }

    private String cuerpoDe(org.springframework.test.web.servlet.RequestBuilder peticion) throws Exception {
        return mvc.perform(peticion)
                .andExpect(status().is2xxSuccessful())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private UserId nuevaCuenta() {
        String correo = "origen-" + UUID.randomUUID() + "@ejemplo.co";

        registro.execute(new RegisterUserCommand(
                correo,
                "una-contrasena-larga-de-verdad",
                "Quien Vende",
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
                new DisplayName("Quien vende"),
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
