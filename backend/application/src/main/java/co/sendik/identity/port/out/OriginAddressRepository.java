package co.sendik.identity.port.out;

import co.sendik.identity.model.OriginAddress;
import co.sendik.identity.model.UserId;
import java.util.Optional;

/**
 * La direccion de origen de una cuenta. HU-017.
 *
 * <p>Una por cuenta, asi que la cuenta es la clave y no hay identificador propio: no
 * hay «buscar por id» porque no hay id, y no hay forma de pedir la de otra persona
 * porque el unico argumento sale del token.
 *
 * <p>Vive en {@code identity} por lo mismo que {@link ShippingAddressRepository}
 * (ADR-0039): es de la persona, y el cierre de cuenta y la descarga de datos la
 * alcanzan sin puerto entre contextos.
 */
public interface OriginAddressRepository {

    /** El origen de esa cuenta, si lo tiene. */
    Optional<OriginAddress> deCuenta(UserId cuenta);

    /**
     * Inserta si no habia y reemplaza si habia. Criterio 3: nunca quedan dos, y lo
     * garantiza la clave primaria sobre la cuenta, no el orden de dos escrituras.
     */
    void guardar(OriginAddress origen);

    /**
     * Lo borra. No falla si no habia: el criterio 9 pide que borrar dos veces responda
     * igual. Lo llama tambien el cierre de cuenta (RN-102).
     */
    void borrar(UserId cuenta);
}
