package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.CloseAccountCommand;
import co.sendik.identity.dto.RegisterUserCommand;
import co.sendik.identity.dto.ShippingAddressData;
import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.identity.usecase.AddShippingAddressUseCase;
import co.sendik.identity.usecase.CloseAccountUseCase;
import co.sendik.identity.usecase.ExportUserDataUseCase;
import co.sendik.identity.usecase.RegisterUserUseCase;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La direccion de entrega como dato personal, con las dos mitades unidas. HU-016, RN-102.
 *
 * <p>Existe por lo mismo que {@code FavoritesPersonalDataTest}: el cierre y la descarga
 * estan probados por mitades que no se tocan. {@code CloseAccountUseCaseTest} verifica sobre
 * un simulador que se llama a {@code borrarDe}; {@code ShippingAddressPersistenceTest}
 * verifica que {@code borrarDe} borra. En medio queda el cableado, y si estuviera mal las
 * dos mitades seguirian verdes y el cierre no borraria nada.
 *
 * <p><strong>Aqui pesa mas que en los favoritos.</strong> Lo que quedaria vivo despues de
 * que alguien ejerciera su derecho de supresion no seria una lista de gustos: seria donde
 * vive, su telefono, y a veces el nombre y el telefono de un tercero que nunca abrio una
 * cuenta (RN-104). Ademas, `datos-personales.md` sostiene que la ventana de quince minutos
 * del token de acceso es aceptable porque al abrirse ya no queda dato personal que alcanzar;
 * esta prueba es lo que hace cierta esa frase.
 */
@SpringBootTest(properties = "sendik.features.checkout=true")
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ShippingAddressPersonalDataTest {

    private final AddShippingAddressUseCase agregar;
    private final CloseAccountUseCase cierre;
    private final ExportUserDataUseCase descarga;
    private final RegisterUserUseCase registro;
    private final UserRepository usuarios;
    private final JdbcClient jdbc;

    ShippingAddressPersonalDataTest(
            AddShippingAddressUseCase agregar,
            CloseAccountUseCase cierre,
            ExportUserDataUseCase descarga,
            RegisterUserUseCase registro,
            UserRepository usuarios,
            JdbcClient jdbc) {
        this.agregar = agregar;
        this.cierre = cierre;
        this.descarga = descarga;
        this.registro = registro;
        this.usuarios = usuarios;
        this.jdbc = jdbc;
    }

    /**
     * La descarga las trae enteras y con los nombres, no con los codigos.
     *
     * <p>Es la diferencia con el favorito y con el producto del carrito, donde va el
     * identificador: alli el titulo es del vendedor y aqui no hay nada que sea de otro.
     */
    @Test
    void deberia_incluir_las_direcciones_en_la_descarga_de_datos() {
        UserId quien = nuevaCuenta();
        agregar.execute(new AddShippingAddressCommand(quien, enBogota()));

        UserDataExport archivo = descarga.execute(quien);

        assertThat(archivo.direcciones()).hasSize(1);
        UserDataExport.Direccion direccion = archivo.direcciones().getFirst();
        assertThat(direccion.quienRecibe()).isEqualTo("Ana María Ruiz");
        assertThat(direccion.telefono()).isEqualTo("3001234567");
        assertThat(direccion.linea()).isEqualTo("Calle 45 # 12-34");
        assertThat(direccion.complemento()).isEqualTo("Apto 802");
        assertThat(direccion.indicaciones()).isEqualTo("El timbre no sirve");
        assertThat(direccion.codigoPostal()).isEqualTo("110111");
        assertThat(direccion.municipio()).isEqualTo("Bogotá, D.C.");
        assertThat(direccion.departamento()).isEqualTo("Bogotá, D.C.");
        assertThat(direccion.predeterminada()).isTrue();
    }

    /** RN-102: el cierre se las lleva, de verdad y hasta la tabla. */
    @Test
    void deberia_borrar_las_direcciones_al_cerrar_la_cuenta() {
        UserId quien = nuevaCuenta();
        UserId otra = nuevaCuenta();
        agregar.execute(new AddShippingAddressCommand(quien, enBogota()));
        agregar.execute(new AddShippingAddressCommand(otra, enBogota()));

        cierre.execute(new CloseAccountCommand(quien, correoDe(quien)));

        assertThat(cuantasFilas(quien)).isZero();
        // Y no toca las de nadie mas.
        assertThat(cuantasFilas(otra)).isEqualTo(1);
    }

    // ------------------------------------------------------------- datos

    private static ShippingAddressData enBogota() {
        return new ShippingAddressData(
                "Ana María Ruiz",
                "3001234567",
                "11001",
                "Calle 45 # 12-34",
                "Apto 802",
                "El timbre no sirve",
                "110111");
    }

    private long cuantasFilas(UserId quien) {
        return jdbc.sql("SELECT count(*) FROM shipping_addresses WHERE user_id = :quien")
                .param("quien", quien.value())
                .query(Long.class)
                .single();
    }

    private String correoDe(UserId quien) {
        return usuarios.buscarPorId(quien).orElseThrow().email().value();
    }

    /** Una cuenta de verdad, creada por su caso de uso: aqui se cierra. */
    private UserId nuevaCuenta() {
        String correo = "direcciones-" + UUID.randomUUID() + "@ejemplo.co";

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
}
