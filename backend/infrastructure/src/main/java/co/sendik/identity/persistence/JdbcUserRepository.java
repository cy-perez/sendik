package co.sendik.identity.persistence;

import co.sendik.identity.model.BirthDate;
import co.sendik.identity.model.City;
import co.sendik.identity.model.DisplayName;
import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.Phone;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import co.sendik.identity.model.UserLocale;
import co.sendik.identity.model.UserStatus;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.file.FileKey;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.jspecify.annotations.Nullable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/**
 * Adaptador de persistencia del agregado de cuenta.
 *
 * <p>SQL explicito con {@link JdbcClient} en lugar del mapeo automatico de Spring
 * Data JDBC. El agregado vive en tres tablas y la columna de correo es
 * {@code citext}: describirlo con anotaciones costaria mas que escribir estas
 * consultas, y escondería justo lo que conviene tener a la vista
 * (backend/CLAUDE.md, ADR-0004).
 */
@Repository
public class JdbcUserRepository implements UserRepository {

    private static final String SELECT_BASE = """
            SELECT u.id, u.email, u.email_verified_at, u.display_name, u.birth_date,
                   u.city, u.phone, u.avatar_key, u.locale, u.status, u.created_at
            FROM users u
            """;

    private final JdbcClient jdbc;

    public JdbcUserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void crear(User usuario, PasswordHash hash) {
        jdbc.sql("""
                        INSERT INTO users (id, email, display_name, birth_date, locale, status, created_at, updated_at)
                        VALUES (:id, :email, :displayName, :birthDate, :locale, :status, :ahora, :ahora)
                        """)
                .param("id", usuario.id().value())
                .param("email", usuario.email().value())
                .param("displayName", usuario.displayName().value())
                .param("birthDate", usuario.birthDate().value())
                .param("locale", usuario.locale().etiqueta())
                .param("status", usuario.status().name())
                .param("ahora", Timestamp.from(usuario.createdAt()))
                .update();

        for (Role rol : usuario.roles()) {
            jdbc.sql("INSERT INTO user_roles (user_id, role, granted_at) VALUES (:usuario, :rol, :ahora)")
                    .param("usuario", usuario.id().value())
                    .param("rol", rol.name())
                    .param("ahora", Timestamp.from(usuario.createdAt()))
                    .update();
        }

        jdbc.sql("""
                        INSERT INTO user_credentials (user_id, password_hash, password_updated_at)
                        VALUES (:usuario, :hash, :ahora)
                        """)
                .param("usuario", usuario.id().value())
                .param("hash", hash.value())
                .param("ahora", Timestamp.from(usuario.createdAt()))
                .update();
    }

    @Override
    public void actualizar(User usuario) {
        jdbc.sql("""
                        UPDATE users
                        SET email_verified_at = :verificado,
                            display_name      = :displayName,
                            city              = :city,
                            phone             = :phone,
                            avatar_key        = :avatarKey,
                            locale            = :locale,
                            status            = :status,
                            updated_at        = now()
                        WHERE id = :id
                        """)
                .param(
                        "verificado",
                        usuario.emailVerifiedAt() == null ? null : Timestamp.from(usuario.emailVerifiedAt()))
                .param("displayName", usuario.displayName().value())
                .param("city", usuario.city() == null ? null : usuario.city().value())
                .param("phone", usuario.phone() == null ? null : usuario.phone().value())
                .param(
                        "avatarKey",
                        usuario.avatarKey() == null ? null : usuario.avatarKey().value())
                .param("locale", usuario.locale().etiqueta())
                .param("status", usuario.status().name())
                .param("id", usuario.id().value())
                .update();
    }

    /** Una columna y solo sobre una cuenta activa: nunca sobre una fila ya anonimizada. */
    @Override
    public void limpiarCiudad(UserId usuario) {
        jdbc.sql("UPDATE users SET city = NULL, updated_at = now() WHERE id = :id AND status = 'ACTIVE'")
                .param("id", usuario.value())
                .update();
    }

    /**
     * El unico UPDATE que toca la columna del correo. Criterio 21.
     *
     * <p>Aparte de {@code actualizar} por lo mismo que el hash de la contrasena
     * esta fuera del suyo: guardar el perfil no puede cambiar el correo, porque
     * cambiarlo exige antes verificar el nuevo.
     */
    @Override
    public void actualizarCorreo(User usuario) {
        jdbc.sql("""
                        UPDATE users
                        SET email             = :email,
                            email_verified_at = :verificado,
                            updated_at        = now()
                        WHERE id = :id
                        """)
                .param("email", usuario.email().value())
                .param(
                        "verificado",
                        usuario.emailVerifiedAt() == null ? null : Timestamp.from(usuario.emailVerifiedAt()))
                .param("id", usuario.id().value())
                .update();
    }

    /**
     * {@code ON CONFLICT DO NOTHING} para que sea idempotente: aprobar dos veces la
     * misma verificacion no puede reventar por la clave primaria de {@code user_roles}.
     */
    @Override
    public void otorgarRol(UserId usuario, Role rol, Instant ahora) {
        jdbc.sql("""
                        INSERT INTO user_roles (user_id, role, granted_at)
                        VALUES (:usuario, :rol, :ahora)
                        ON CONFLICT (user_id, role) DO NOTHING
                        """)
                .param("usuario", usuario.value())
                .param("rol", rol.name())
                .param("ahora", Timestamp.from(ahora))
                .update();
    }

    @Override
    public void revocarRol(UserId usuario, Role rol) {
        jdbc.sql("DELETE FROM user_roles WHERE user_id = :usuario AND role = :rol")
                .param("usuario", usuario.value())
                .param("rol", rol.name())
                .update();
    }

    @Override
    public Optional<User> buscarPorCorreo(Email correo) {
        // La comparacion la resuelve citext: "Ana@Correo.co" encuentra la fila de
        // "ana@correo.co" sin funciones alrededor de la columna, que ademas
        // inutilizarian el indice unico (RN-001).
        return jdbc.sql(SELECT_BASE + " WHERE u.email = :email AND u.status <> 'CLOSED'")
                .param("email", correo.value())
                .query(this::mapear)
                .optional();
    }

    @Override
    public Optional<User> buscarPorId(UserId id) {
        return jdbc.sql(SELECT_BASE + " WHERE u.id = :id AND u.status <> 'CLOSED'")
                .param("id", id.value())
                .query(this::mapear)
                .optional();
    }

    private User mapear(ResultSet fila, int numeroDeFila) throws SQLException {
        UserId id = new UserId(fila.getObject("id", java.util.UUID.class));

        return User.rehidratar(
                id,
                new Email(fila.getString("email")),
                new DisplayName(fila.getString("display_name")),
                new BirthDate(fila.getObject("birth_date", java.time.LocalDate.class)),
                // Nulos mientras la persona no los ponga: son opcionales.
                valorONulo(fila.getString("city"), City::new),
                valorONulo(fila.getString("phone"), Phone::new),
                valorONulo(fila.getString("avatar_key"), FileKey::new),
                UserLocale.de(fila.getString("locale")),
                UserStatus.valueOf(fila.getString("status").toUpperCase(Locale.ROOT)),
                instanteONulo(fila.getTimestamp("email_verified_at")),
                rolesDe(id),
                fila.getTimestamp("created_at").toInstant());
    }

    private static <T> @Nullable T valorONulo(@Nullable String columna, java.util.function.Function<String, T> como) {
        return columna == null || columna.isBlank() ? null : como.apply(columna);
    }

    /**
     * Consulta aparte y no un JOIN con agregacion: sin sesion ni carga perezosa,
     * dos consultas claras se leen mejor que una con GROUP BY, y el volumen aqui
     * es de un puñado de filas (ADR-0004).
     */
    private Set<Role> rolesDe(UserId usuario) {
        List<Role> roles = jdbc.sql("SELECT role FROM user_roles WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .query((fila, numero) -> Role.valueOf(fila.getString("role")))
                .list();

        return roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles);
    }

    private static Instant instanteONulo(Timestamp marca) {
        return marca == null ? null : marca.toInstant();
    }

    /**
     * Criterio 23: vacia la fila en vez de borrarla.
     *
     * <p>El correo se reemplaza por uno del dominio reservado {@code .invalid}, que
     * por norma no puede existir, y lleva dentro el identificador para seguir
     * cumpliendo la unicidad de RN-001. Conservar el original impediria a esa
     * persona volver a registrarse nunca con su propia direccion, que seria
     * convertir el derecho de supresion en una expulsion.
     *
     * <p>Se borran ademas la contrasena y los tokens pendientes. La fecha de
     * nacimiento se conserva: separada del correo y del nombre ya no identifica a
     * nadie. Cuando existan pedidos habra que revisar esa decision, porque un
     * historial de compras si puede volver a identificar.
     *
     * <p>Son cuatro sentencias y no una porque tocan cuatro tablas. La atomicidad la
     * pone la transaccion del caso de uso: a medias, esto dejaria una cuenta sin
     * nombre pero con contrasena, o al reves.
     */
    @Override
    public void cerrarYAnonimizar(UserId usuario, Instant ahora) {
        jdbc.sql("""
                        UPDATE users
                        SET email        = 'cerrada-' || id || '@sendik.invalid',
                            display_name = 'Cuenta cerrada',
                            city         = NULL,
                            phone        = NULL,
                            avatar_url   = NULL,
                            avatar_key   = NULL,
                            status       = 'CLOSED',
                            closed_at    = :ahora,
                            updated_at   = :ahora
                        WHERE id = :usuario
                        """)
                .param("ahora", Timestamp.from(ahora))
                .param("usuario", usuario.value())
                .update();

        // La contrasena no se anonimiza: se borra. Un hash de Argon2id no
        // identifica a nadie, pero tampoco hace falta para nada.
        jdbc.sql("DELETE FROM user_credentials WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .update();

        // Enlaces de verificacion y de restablecimiento pendientes: son
        // credenciales de un solo uso y no pueden sobrevivir a la cuenta.
        jdbc.sql("DELETE FROM verification_tokens WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .update();

        jdbc.sql("DELETE FROM user_roles WHERE user_id = :usuario")
                .param("usuario", usuario.value())
                .update();
    }
}
