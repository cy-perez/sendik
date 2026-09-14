package co.sendik.identity.port.out;

import co.sendik.identity.model.Email;
import co.sendik.identity.model.PasswordHash;
import co.sendik.identity.model.Role;
import co.sendik.identity.model.User;
import co.sendik.identity.model.UserId;
import java.time.Instant;
import java.util.Optional;

/**
 * Puerto de salida hacia el almacen de cuentas.
 *
 * <p>La firma habla de objetos de dominio, no de filas: quien lo implemente
 * decide si {@code users}, {@code user_roles} y {@code user_credentials} son tres
 * tablas o una. Un repositorio por agregado, no por tabla (backend/CLAUDE.md).
 */
public interface UserRepository {

    /**
     * Crea la cuenta con sus roles y sus credenciales.
     *
     * <p>Recibe el hash aparte porque no vive en el agregado: un {@link User}
     * cargado en memoria no debe llevar consigo con que suplantar a nadie.
     */
    void crear(User usuario, PasswordHash hash);

    /**
     * Guarda el perfil: nombre, ciudad, telefono e idioma. <strong>No el correo.</strong>
     *
     * <p>Excluirlo es deliberado, igual que {@code CredentialsRepository.actualizar}
     * excluye el hash: cambiar el correo exige verificar el nuevo antes, y si
     * cupiera en este metodo cualquier guardado de perfil podria saltarse esa
     * verificacion.
     */
    void actualizar(User usuario);

    /**
     * Deja la ciudad escrita a mano en nulo. HU-017, criterio 11.
     *
     * <p>Una operacion minima y no {@link #actualizar}, a proposito: aquella reescribe la
     * fila entera desde una instantanea de la cuenta, y si el cierre de cuenta se colara
     * entre leer y escribir, restauraria nombre, telefono y estado sobre una fila ya
     * anonimizada. Esto toca una columna y solo sobre una cuenta activa. Lo cazo la
     * revision de seguridad.
     */
    void limpiarCiudad(UserId usuario);

    /**
     * El unico metodo que escribe el correo. Criterio 21.
     *
     * <p>Escribe tambien la fecha de verificacion, porque el correo nuevo llega ya
     * verificado: la persona acaba de demostrar que ese buzon es suyo abriendo el
     * enlace.
     */
    void actualizarCorreo(User usuario);

    /**
     * Otorga un rol. Criterio 8 de HU-002: aprobada la verificacion, la persona pasa a
     * ser vendedora.
     *
     * <p>Metodo propio y no parte de {@code actualizar}, por el mismo motivo por el que
     * ese metodo excluye el correo: los roles deciden lo que alguien puede hacer, y si
     * cupieran en el guardado del perfil, cualquier cambio de nombre podria llevarse un
     * rol por delante. Hasta ahora los roles solo se escribian al crear la cuenta.
     *
     * <p>Es idempotente: otorgar dos veces el mismo rol no falla ni duplica la fila.
     */
    void otorgarRol(UserId usuario, Role rol, Instant ahora);

    /**
     * Quita un rol. RN-013: revocar la verificacion quita el sello de vendedor.
     *
     * <p>Tambien idempotente: quitar lo que no esta no es un error.
     */
    void revocarRol(UserId usuario, Role rol);

    Optional<User> buscarPorCorreo(Email correo);

    Optional<User> buscarPorId(UserId id);

    /**
     * Cierra la cuenta y borra de ella todo lo que identifica a una persona.
     * Criterio 23 y derecho de supresion de la Ley 1581.
     *
     * <p>La fila no se elimina: se vacia. Quedan el identificador, la fecha de
     * creacion y el estado, que ya no apuntan a nadie, y eso permite que lo que
     * manana haya que conservar por obligacion contable siga teniendo a que
     * referirse (docs/operacion/datos-personales.md).
     *
     * <p><strong>El correo se sustituye, no se deja.</strong> Si se conservara, RN-001
     * impediria a esa persona volver a registrarse nunca con su propia direccion.
     * Se reemplaza por una del dominio reservado {@code .invalid}, que por norma no
     * puede existir, y asi la restriccion de unicidad se sigue cumpliendo sin
     * guardar la direccion de nadie.
     *
     * <p>Se lleva por delante tambien la contrasena y los tokens pendientes. La
     * fecha de nacimiento se conserva: separada del correo, del nombre y de todo lo
     * demas ya no identifica a nadie. Cuando existan pedidos habra que revisar esa
     * decision, porque un historial de compras si puede volver a identificar.
     */
    void cerrarYAnonimizar(UserId usuario, Instant ahora);
}
