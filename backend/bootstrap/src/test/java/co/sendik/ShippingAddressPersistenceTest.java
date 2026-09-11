package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.DeliveryInstructions;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.RecipientName;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La libreta de direcciones y la Divipola, contra PostgreSQL 17 real. HU-016.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que V20 sembro exactamente lo
 * que dice la fuente (criterio 22), que el indice unico parcial impide dos predeterminadas
 * aunque la aplicacion lo intente (criterio 13), que la clave foranea con
 * {@code ON DELETE RESTRICT} impide borrar un municipio que alguien apunta (criterio 23), y
 * que lo que queda escrito en la columna esta <strong>de verdad cifrado</strong>.
 *
 * <p>{@code DireccionesDeEntregaTest} pasa contra un doble en memoria que se comporta igual;
 * esto comprueba que el esquema lo hace de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class ShippingAddressPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");
    private static final MunicipalityCode BOGOTA = new MunicipalityCode("11001");
    private static final MunicipalityCode MEDELLIN = new MunicipalityCode("05001");

    private final ShippingAddressRepository direcciones;
    private final GeographicDivision division;
    private final JdbcClient jdbc;

    ShippingAddressPersistenceTest(
            ShippingAddressRepository direcciones, GeographicDivision division, JdbcClient jdbc) {
        this.direcciones = direcciones;
        this.division = division;
        this.jdbc = jdbc;
    }

    @Nested
    class LaDivisionSembrada {

        /**
         * Criterio 22: lo sembrado coincide con la fuente. Los dos numeros estan escritos en
         * el encabezado de V20 junto a la URL y la fecha de descarga, y esta prueba es lo
         * que impide que una resiembra futura se deje filas por el camino sin que nadie lo
         * note.
         */
        @Test
        void deberia_tener_los_33_departamentos_y_los_1122_municipios_de_la_fuente() {
            assertThat(jdbc.sql("SELECT count(*) FROM departments")
                            .query(Long.class)
                            .single())
                    .isEqualTo(33);
            assertThat(jdbc.sql("SELECT count(*) FROM municipalities")
                            .query(Long.class)
                            .single())
                    .isEqualTo(1122);
        }

        /** En la Divipola, Bogota D.C. es departamento y municipio a la vez. */
        @Test
        void deberia_tener_a_bogota_como_departamento_y_como_municipio() {
            assertThat(division.departamentos())
                    .extracting(departamento -> departamento.codigo().value())
                    .contains("11");
            assertThat(division.buscarMunicipio(BOGOTA))
                    .map(Municipality::nombre)
                    .contains("Bogotá, D.C.");
        }

        /**
         * El codigo de municipio lleva dentro el del departamento, y de esa propiedad
         * depende que el cuerpo de la API no pida departamento. Si alguna fila la rompiera,
         * la derivacion devolveria un departamento equivocado sin fallar.
         */
        @Test
        void deberia_cumplirse_en_toda_la_tabla_que_el_codigo_lleva_dentro_su_departamento() {
            long incoherentes = jdbc.sql("SELECT count(*) FROM municipalities WHERE left(code, 2) <> department_code")
                    .query(Long.class)
                    .single();

            assertThat(incoherentes).isZero();
        }

        /** Los nombres van capitalizados y no en mayuscula sostenida, que es como llegan. */
        @Test
        void deberia_guardar_los_nombres_como_se_leen() {
            assertThat(division.buscarMunicipio(MEDELLIN))
                    .map(Municipality::nombre)
                    .contains("Medellín");
        }

        @Test
        void deberia_devolver_los_municipios_de_un_departamento_ordenados_por_nombre() {
            List<Municipality> deAntioquia = division.municipiosActivosDe(new DepartmentCode("05"));

            assertThat(deAntioquia).isNotEmpty();
            assertThat(deAntioquia).extracting(Municipality::nombre).isSortedAccordingTo(String::compareTo);
            assertThat(deAntioquia)
                    .allMatch(municipio -> municipio.codigo().value().startsWith("05"));
        }

        /** Criterio 24: hay homonimos, y por eso el municipio nunca se lee solo. */
        @Test
        void deberia_haber_municipios_con_el_mismo_nombre_en_departamentos_distintos() {
            long homonimos = jdbc.sql("""
                            SELECT count(*) FROM (
                                SELECT name FROM municipalities GROUP BY name HAVING count(*) > 1
                            ) AS repetidos
                            """).query(Long.class).single();

            assertThat(homonimos).isPositive();
        }

        @Test
        void deberia_devolver_varios_municipios_de_una_vez_y_ninguno_si_no_se_pide_nada() {
            Map<MunicipalityCode, Municipality> encontrados = division.buscarMunicipios(List.of(BOGOTA, MEDELLIN));

            assertThat(encontrados).containsOnlyKeys(BOGOTA, MEDELLIN);
            assertThat(division.buscarMunicipios(List.of())).isEmpty();
        }
    }

    @Nested
    class LaLibreta {

        @Test
        void deberia_guardar_y_devolver_una_direccion_entera() {
            UserId alguien = nuevaCuenta();
            ShippingAddress guardada = completa(alguien, BOGOTA, AHORA);

            direcciones.guardar(guardada);

            ShippingAddress leida = direcciones.buscar(guardada.id()).orElseThrow();
            assertThat(leida.quienRecibe().value()).isEqualTo("Ana María Ruiz");
            assertThat(leida.telefono().value()).isEqualTo("3001234567");
            assertThat(leida.linea().value()).isEqualTo("Calle 45 # 12-34");
            assertThat(leida.complemento()).isNotNull();
            assertThat(leida.indicaciones()).isNotNull();
            assertThat(leida.codigoPostal()).isNotNull();
            assertThat(leida.municipio()).isEqualTo(BOGOTA);
            assertThat(leida.departamento()).isEqualTo(new DepartmentCode("11"));
            assertThat(leida.creadaEn()).isEqualTo(AHORA);
        }

        @Test
        void deberia_admitir_una_direccion_sin_los_tres_opcionales() {
            UserId alguien = nuevaCuenta();
            ShippingAddress minima = ShippingAddress.nueva(
                    alguien,
                    new RecipientName("Ana María Ruiz"),
                    new Phone("3001234567"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA);

            direcciones.guardar(minima);

            ShippingAddress leida = direcciones.buscar(minima.id()).orElseThrow();
            assertThat(leida.complemento()).isNull();
            assertThat(leida.indicaciones()).isNull();
            assertThat(leida.codigoPostal()).isNull();
        }

        /**
         * Lo que queda en la columna no se puede leer. Es la prueba de que el cifrado ocurre
         * de verdad y no es una decision que solo vive en la documentacion.
         */
        @Test
        void deberia_dejar_la_columna_cifrada_y_no_el_texto() {
            UserId alguien = nuevaCuenta();
            ShippingAddress guardada = completa(alguien, BOGOTA, AHORA);

            direcciones.guardar(guardada);

            String columna = jdbc.sql("SELECT details_cipher FROM shipping_addresses WHERE id = :id")
                    .param("id", guardada.id().value())
                    .query(String.class)
                    .single();

            assertThat(columna)
                    .doesNotContain("Ana María Ruiz")
                    .doesNotContain("3001234567")
                    .doesNotContain("Calle 45")
                    .doesNotContain("Apto 802")
                    .doesNotContain("timbre");
            assertThat(jdbc.sql("SELECT details_key_version FROM shipping_addresses WHERE id = :id")
                            .param("id", guardada.id().value())
                            .query(Integer.class)
                            .single())
                    .isPositive();
        }

        /**
         * Dos llamadas con el mismo valor dan textos cifrados distintos, asi que dos
         * direcciones identicas no se delatan como identicas sin descifrarlas.
         */
        @Test
        void deberia_cifrar_distinto_dos_direcciones_identicas() {
            UserId alguien = nuevaCuenta();
            ShippingAddress una = completa(alguien, BOGOTA, AHORA);
            ShippingAddress otra = completa(alguien, BOGOTA, AHORA);

            direcciones.guardar(una);
            direcciones.guardar(otra);

            List<String> columnas = jdbc.sql("SELECT details_cipher FROM shipping_addresses WHERE user_id = :cuenta")
                    .param("cuenta", alguien.value())
                    .query(String.class)
                    .list();

            assertThat(columnas).hasSize(2);
            assertThat(columnas.get(0)).isNotEqualTo(columnas.get(1));
        }

        @Test
        void deberia_devolverlas_de_la_mas_reciente_a_la_mas_antigua() {
            UserId alguien = nuevaCuenta();
            ShippingAddress vieja = completa(alguien, BOGOTA, AHORA.minus(Duration.ofDays(2)));
            ShippingAddress nueva = completa(alguien, MEDELLIN, AHORA);
            direcciones.guardar(vieja);
            direcciones.guardar(nueva);

            assertThat(direcciones.deCuenta(alguien))
                    .extracting(ShippingAddress::id)
                    .containsExactly(nueva.id(), vieja.id());
        }

        @Test
        void deberia_reescribir_la_misma_fila_al_guardar_dos_veces() {
            UserId alguien = nuevaCuenta();
            ShippingAddress guardada = completa(alguien, BOGOTA, AHORA);
            direcciones.guardar(guardada);

            direcciones.guardar(guardada.con(
                    new RecipientName("Carlos Pérez"),
                    new Phone("3109876543"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA.plus(Duration.ofDays(1))));

            assertThat(direcciones.deCuenta(alguien)).hasSize(1);
            ShippingAddress leida = direcciones.buscar(guardada.id()).orElseThrow();
            assertThat(leida.quienRecibe().value()).isEqualTo("Carlos Pérez");
            assertThat(leida.municipio()).isEqualTo(MEDELLIN);
            assertThat(leida.complemento()).isNull();
            // La fecha de creacion sobrevive: de ella depende el orden de la libreta.
            assertThat(leida.creadaEn()).isEqualTo(AHORA);
            assertThat(leida.actualizadaEn()).isEqualTo(AHORA.plus(Duration.ofDays(1)));
        }

        /** Criterio 14: borrar cero filas es un resultado, no un error. */
        @Test
        void deberia_poder_borrarse_dos_veces() {
            UserId alguien = nuevaCuenta();
            ShippingAddress guardada = completa(alguien, BOGOTA, AHORA);
            direcciones.guardar(guardada);

            direcciones.borrar(guardada.id());

            assertThatCode(() -> direcciones.borrar(guardada.id())).doesNotThrowAnyException();
            assertThat(direcciones.buscar(guardada.id())).isEmpty();
        }

        /** RN-102: el cierre de cuenta se las lleva todas. */
        @Test
        void deberia_borrar_la_libreta_entera_de_una_cuenta() {
            UserId alguien = nuevaCuenta();
            UserId otra = nuevaCuenta();
            direcciones.guardar(completa(alguien, BOGOTA, AHORA));
            direcciones.guardar(completa(alguien, MEDELLIN, AHORA));
            direcciones.guardar(completa(otra, BOGOTA, AHORA));

            direcciones.borrarDe(alguien);

            assertThat(direcciones.deCuenta(alguien)).isEmpty();
            assertThat(direcciones.deCuenta(otra)).hasSize(1);
        }
    }

    @Nested
    class LaPredeterminada {

        /**
         * Criterio 13, y esto es lo unico que de verdad lo garantiza. La aplicacion no puede:
         * entre leer cual es la actual y escribir la nueva cabe la peticion de otra pestana.
         */
        @Test
        void deberia_impedir_que_una_cuenta_tenga_dos() {
            UserId alguien = nuevaCuenta();
            ShippingAddress primera = completa(alguien, BOGOTA, AHORA).comoPredeterminada(true);
            ShippingAddress segunda = completa(alguien, MEDELLIN, AHORA).comoPredeterminada(true);
            direcciones.guardar(primera);

            assertThatThrownBy(() -> direcciones.guardar(segunda)).isInstanceOf(DataIntegrityViolationException.class);
        }

        /** Dos cuentas distintas pueden tener cada una la suya: el indice es parcial y por persona. */
        @Test
        void deberia_admitir_una_por_cada_cuenta() {
            UserId alguien = nuevaCuenta();
            UserId otra = nuevaCuenta();

            direcciones.guardar(completa(alguien, BOGOTA, AHORA).comoPredeterminada(true));

            assertThatCode(() ->
                            direcciones.guardar(completa(otra, BOGOTA, AHORA).comoPredeterminada(true)))
                    .doesNotThrowAnyException();
        }

        /**
         * El cambio es un solo gesto: le quita la marca a la que la tenia y se la pone a la
         * nueva. Si el adaptador lo hiciera al reves, el indice unico parcial abortaria.
         */
        @Test
        void deberia_cambiar_de_una_a_otra_sin_pasar_por_dos() {
            UserId alguien = nuevaCuenta();
            ShippingAddress primera = completa(alguien, BOGOTA, AHORA).comoPredeterminada(true);
            ShippingAddress segunda = completa(alguien, MEDELLIN, AHORA);
            direcciones.guardar(primera);
            direcciones.guardar(segunda);

            direcciones.marcarPredeterminada(alguien, segunda.id());

            assertThat(direcciones.deCuenta(alguien))
                    .filteredOn(ShippingAddress::esPredeterminada)
                    .extracting(ShippingAddress::id)
                    .containsExactly(segunda.id());
        }

        /** Editar una direccion no puede mover la predeterminada sin que nadie lo pida. */
        @Test
        void deberia_sobrevivir_a_que_se_editen_los_datos() {
            UserId alguien = nuevaCuenta();
            ShippingAddress marcada = completa(alguien, BOGOTA, AHORA).comoPredeterminada(true);
            direcciones.guardar(marcada);

            direcciones.guardar(marcada.con(
                    new RecipientName("Carlos Pérez"),
                    new Phone("3109876543"),
                    MEDELLIN,
                    new AddressLine("Carrera 70 # 45-12"),
                    null,
                    null,
                    null,
                    AHORA.plus(Duration.ofDays(1))));

            assertThat(direcciones.buscar(marcada.id()).orElseThrow().esPredeterminada())
                    .isTrue();
        }
    }

    @Nested
    class ElMunicipio {

        /** RN-100: no se puede guardar una direccion sobre un municipio que no existe. */
        @Test
        void deberia_rechazar_un_codigo_que_no_esta_en_la_division() {
            UserId alguien = nuevaCuenta();
            ShippingAddress inventada = completa(alguien, new MunicipalityCode("99999"), AHORA);

            assertThatThrownBy(() -> direcciones.guardar(inventada))
                    .isInstanceOf(DataIntegrityViolationException.class);
        }

        /**
         * Criterio 23: la clave foranea con {@code ON DELETE RESTRICT} es lo que convierte
         * «se marca inactivo y no se borra» en algo que la base hace cumplir, en vez de una
         * costumbre que alguien puede olvidar en la proxima resiembra.
         */
        @Test
        void deberia_impedir_borrar_un_municipio_que_alguna_direccion_apunta() {
            UserId alguien = nuevaCuenta();
            direcciones.guardar(completa(alguien, BOGOTA, AHORA));

            assertThatThrownBy(() -> jdbc.sql("DELETE FROM municipalities WHERE code = :codigo")
                            .param("codigo", BOGOTA.value())
                            .update())
                    .isInstanceOf(DataIntegrityViolationException.class);
        }
    }

    // --- Ayudantes ---

    private ShippingAddress completa(UserId duena, MunicipalityCode municipio, Instant cuando) {
        return ShippingAddress.nueva(
                duena,
                new RecipientName("Ana María Ruiz"),
                new Phone("3001234567"),
                municipio,
                new AddressLine("Calle 45 # 12-34"),
                new AddressComplement("Apto 802"),
                new DeliveryInstructions("La casa de la esquina, el timbre no sirve"),
                new PostalCode("110111"),
                cuando);
    }

    private UserId nuevaCuenta() {
        UUID id = UUID.randomUUID();
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, status)
                        VALUES (:id, :correo, 'Alguien de prueba', DATE '1990-01-01', 'ACTIVE')
                        """).param("id", id).param("correo", id + "@ejemplo.co").update();
        return new UserId(id);
    }
}
