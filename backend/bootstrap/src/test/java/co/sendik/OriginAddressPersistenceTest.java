package co.sendik;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.sendik.identity.model.AddressComplement;
import co.sendik.identity.model.AddressLine;
import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.PickupInstructions;
import co.sendik.identity.model.PostalCode;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.OriginAddressRepository;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;

/**
 * La direccion de origen contra PostgreSQL 17 real. HU-017.
 *
 * <p>Lo que se prueba aqui y no se puede probar en otro sitio: que la clave primaria sobre
 * la cuenta convierte dos escrituras en una (criterio 3), que un municipio inactivo se
 * sigue leyendo (criterio 13), que la clave foranea impide borrar un municipio que algun
 * origen apunta, y que lo que queda escrito en la columna esta de verdad cifrado.
 *
 * <p>{@code DireccionDeOrigenTest} pasa contra un doble en memoria; esto comprueba que el
 * esquema lo hace de verdad.
 */
@SpringBootTest
@ActiveProfiles("local")
@Import(PostgresTestContainer.class)
class OriginAddressPersistenceTest {

    private static final Instant AHORA = Instant.parse("2026-09-14T10:00:00Z");
    private static final MunicipalityCode BOGOTA = new MunicipalityCode("11001");
    private static final MunicipalityCode MEDELLIN = new MunicipalityCode("05001");

    private final OriginAddressRepository origenes;
    private final JdbcClient jdbc;

    OriginAddressPersistenceTest(OriginAddressRepository origenes, JdbcClient jdbc) {
        this.origenes = origenes;
        this.jdbc = jdbc;
    }

    @Test
    void deberia_guardar_y_leer_los_cuatro_campos_y_el_municipio() {
        UserId alguien = nuevaCuenta();

        origenes.guardar(completo(alguien, BOGOTA, AHORA));

        Optional<OriginAddress> leido = origenes.deCuenta(alguien);
        assertThat(leido).isPresent();
        assertThat(leido.get().municipio()).isEqualTo(BOGOTA);
        assertThat(leido.get().linea().value()).isEqualTo("Carrera 15 # 93-47");
        assertThat(leido.get().complemento()).isEqualTo(new AddressComplement("Local 3"));
        assertThat(leido.get().indicaciones())
                .isEqualTo(new PickupInstructions("Entrar por el parqueadero, preguntar por Nubia"));
        assertThat(leido.get().codigoPostal()).isEqualTo(new PostalCode("110221"));
        assertThat(leido.get().creadaEn()).isEqualTo(AHORA);
    }

    @Test
    void deberia_leer_los_opcionales_ausentes_como_nulos() {
        UserId alguien = nuevaCuenta();

        origenes.guardar(
                OriginAddress.nueva(alguien, MEDELLIN, new AddressLine("Carrera 70 # 45-12"), null, null, null, AHORA));

        Optional<OriginAddress> leido = origenes.deCuenta(alguien);
        assertThat(leido).isPresent();
        assertThat(leido.get().complemento()).isNull();
        assertThat(leido.get().indicaciones()).isNull();
        assertThat(leido.get().codigoPostal()).isNull();
    }

    @Test
    void deberia_no_haber_nada_para_una_cuenta_sin_origen() {
        assertThat(origenes.deCuenta(nuevaCuenta())).isEmpty();
    }

    /**
     * Criterio 3: la clave primaria es la cuenta, asi que guardar dos veces deja una fila.
     * Y conserva {@code created_at}, que el {@code ON CONFLICT DO UPDATE} deja fuera del SET.
     */
    @Test
    void deberia_reemplazar_en_vez_de_agregar_y_conservar_la_fecha_de_creacion() {
        UserId alguien = nuevaCuenta();
        origenes.guardar(completo(alguien, BOGOTA, AHORA));

        origenes.guardar(OriginAddress.nueva(
                alguien,
                MEDELLIN,
                new AddressLine("Carrera 70 # 45-12"),
                null,
                null,
                null,
                AHORA.plus(Duration.ofDays(1))));

        long filas = jdbc.sql("SELECT count(*) FROM origin_addresses WHERE user_id = :cuenta")
                .param("cuenta", alguien.value())
                .query(Long.class)
                .single();
        assertThat(filas).isEqualTo(1);

        Optional<OriginAddress> leido = origenes.deCuenta(alguien);
        assertThat(leido).isPresent();
        assertThat(leido.get().municipio()).isEqualTo(MEDELLIN);
        assertThat(leido.get().creadaEn()).isEqualTo(AHORA);
        assertThat(leido.get().actualizadaEn()).isEqualTo(AHORA.plus(Duration.ofDays(1)));
    }

    /** Criterio 9: borrar dos veces responde igual, y borrar lo de una cuenta no toca otra. */
    @Test
    void deberia_borrar_sin_fallar_al_repetirse_y_sin_tocar_a_otra_cuenta() {
        UserId alguien = nuevaCuenta();
        UserId otraPersona = nuevaCuenta();
        origenes.guardar(completo(alguien, BOGOTA, AHORA));
        origenes.guardar(completo(otraPersona, MEDELLIN, AHORA));

        origenes.borrar(alguien);

        assertThat(origenes.deCuenta(alguien)).isEmpty();
        assertThat(origenes.deCuenta(otraPersona)).isPresent();

        origenes.borrar(alguien);
        assertThat(origenes.deCuenta(alguien)).isEmpty();
        assertThat(origenes.deCuenta(otraPersona)).isPresent();
    }

    /** Lo que queda en la columna no se puede leer: el cifrado ocurre de verdad. */
    @Test
    void deberia_dejar_la_columna_cifrada_y_no_el_texto() {
        UserId alguien = nuevaCuenta();

        origenes.guardar(completo(alguien, BOGOTA, AHORA));

        String columna = jdbc.sql("SELECT details_cipher FROM origin_addresses WHERE user_id = :cuenta")
                .param("cuenta", alguien.value())
                .query(String.class)
                .single();

        assertThat(columna)
                .doesNotContain("Carrera 15")
                .doesNotContain("Local 3")
                .doesNotContain("Nubia")
                .doesNotContain("110221");
        assertThat(jdbc.sql("SELECT details_key_version FROM origin_addresses WHERE user_id = :cuenta")
                        .param("cuenta", alguien.value())
                        .query(Integer.class)
                        .single())
                .isPositive();
    }

    /** Criterio 13: un municipio que el DANE suprimio se sigue leyendo. */
    @Test
    void deberia_seguir_leyendo_un_origen_cuyo_municipio_se_marco_inactivo() {
        UserId alguien = nuevaCuenta();
        origenes.guardar(completo(alguien, BOGOTA, AHORA));
        marcarInactivo(BOGOTA, true);

        try {
            assertThat(origenes.deCuenta(alguien)).map(OriginAddress::municipio).contains(BOGOTA);
        } finally {
            marcarInactivo(BOGOTA, false);
        }
    }

    /** La clave foranea con {@code ON DELETE RESTRICT}, como en V21. */
    @Test
    void deberia_impedir_borrar_un_municipio_que_algun_origen_apunta() {
        UserId alguien = nuevaCuenta();
        origenes.guardar(completo(alguien, MEDELLIN, AHORA));

        assertThatThrownBy(() -> jdbc.sql("DELETE FROM municipalities WHERE code = :codigo")
                        .param("codigo", MEDELLIN.value())
                        .update())
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    /** Un municipio que no esta en la division no se puede guardar: la clave foranea lo impide. */
    @Test
    void deberia_rechazar_un_municipio_que_no_existe() {
        UserId alguien = nuevaCuenta();

        assertThatThrownBy(() -> origenes.guardar(completo(alguien, new MunicipalityCode("99999"), AHORA)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // --- Ayudantes ---

    private static OriginAddress completo(UserId duena, MunicipalityCode municipio, Instant cuando) {
        return OriginAddress.nueva(
                duena,
                municipio,
                new AddressLine("Carrera 15 # 93-47"),
                new AddressComplement("Local 3"),
                new PickupInstructions("Entrar por el parqueadero, preguntar por Nubia"),
                new PostalCode("110221"),
                cuando);
    }

    private void marcarInactivo(MunicipalityCode codigo, boolean inactivo) {
        jdbc.sql("UPDATE municipalities SET active = :activo WHERE code = :codigo")
                .param("activo", !inactivo)
                .param("codigo", codigo.value())
                .update();
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
