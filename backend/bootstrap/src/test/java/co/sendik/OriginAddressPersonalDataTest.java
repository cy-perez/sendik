package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.dto.OriginAddressData;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.dto.UpdateProfileCommand;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import co.sendik.identity.usecase.SaveOriginAddressUseCase;
import co.sendik.identity.usecase.UpdateProfileUseCase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La direccion de origen como dato personal, con las dos mitades unidas. HU-017,
 * criterios 18 y 19.
 *
 * <p>Por lo mismo que {@code ShippingAddressPersonalDataTest}: el cierre y la descarga
 * estan probados por mitades que no se tocan, y en medio queda el cableado. Si estuviera
 * mal, las dos mitades seguirian verdes y el cierre no borraria nada.
 */
@SpringBootTest(properties = "sendik.features.checkout=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class OriginAddressPersonalDataTest {

    private final SaveOriginAddressUseCase guardar;
    private final UpdateProfileUseCase perfil;
    private final CloseAccountUseCase cierre;
    private final ExportUserDataUseCase descarga;
    private final RegisterUserUseCase registro;
    private final UserRepository usuarios;
    private final JdbcClient jdbc;

    OriginAddressPersonalDataTest(
            SaveOriginAddressUseCase guardar,
            UpdateProfileUseCase perfil,
            CloseAccountUseCase cierre,
            ExportUserDataUseCase descarga,
            RegisterUserUseCase registro,
            UserRepository usuarios,
            JdbcClient jdbc) {
        this.guardar = guardar;
        this.perfil = perfil;
        this.cierre = cierre;
        this.descarga = descarga;
        this.registro = registro;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
    }

    /** Criterio 18: la descarga lo trae entero y con nombres, no con codigos. */
    @Test
    void deberia_incluir_el_origen_en_la_descarga_de_datos() {
        UserId quien = nuevaCuentaConTelefono();
        guardar.execute(new SaveOriginAddressCommand(quien, enBogota()));

        UserDataExport archivo = descarga.execute(quien);

        assertThat(archivo.origen()).isNotNull();
        assertThat(archivo.origen().linea()).isEqualTo("Carrera 15 # 93-47");
        assertThat(archivo.origen().complemento()).isEqualTo("Local 3");
        assertThat(archivo.origen().indicaciones()).isEqualTo("Entrar por el parqueadero");
        assertThat(archivo.origen().codigoPostal()).isEqualTo("110221");
        assertThat(archivo.origen().municipio()).isEqualTo("Bogotá, D.C.");
        assertThat(archivo.origen().departamento()).isEqualTo("Bogotá, D.C.");
    }

    @Test
    void deberia_emitir_el_origen_nulo_cuando_no_hay() {
        assertThat(descarga.execute(nuevaCuentaConTelefono()).origen()).isNull();
    }

    /** Criterio 19: el cierre se lo lleva, de verdad y hasta la tabla, y no toca el de nadie mas. */
    @Test
    void deberia_borrar_el_origen_al_cerrar_la_cuenta() {
        UserId quien = nuevaCuentaConTelefono();
        UserId otra = nuevaCuentaConTelefono();
        guardar.execute(new SaveOriginAddressCommand(quien, enBogota()));
        guardar.execute(new SaveOriginAddressCommand(otra, enBogota()));

        cierre.execute(new CloseAccountCommand(quien, correoDe(quien)));

        assertThat(cuantasFilas(quien)).isZero();
        assertThat(cuantasFilas(otra)).isEqualTo(1);
    }

    // ------------------------------------------------------------- datos

    private static OriginAddressData enBogota() {
        return new OriginAddressData("11001", "Carrera 15 # 93-47", "Local 3", "Entrar por el parqueadero", "110221");
    }

    private long cuantasFilas(UserId quien) {
        return jdbc.sql("SELECT count(*) FROM origin_addresses WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Long.class)
                .single();
    }

    private String correoDe(UserId quien) {
        return usuarios.buscarPorId(quien).orElseThrow().email().value();
    }

    /** Una cuenta de verdad, creada por su caso de uso y con telefono, que el origen exige. */
    private UserId nuevaCuentaConTelefono() {
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

        UserId id = usuarios.buscarPorCorreo(new Email(correo)).orElseThrow().id();
        perfil.execute(new UpdateProfileCommand(id, "Quien Vende", null, "3001234567"));
        return id;
    }
}
