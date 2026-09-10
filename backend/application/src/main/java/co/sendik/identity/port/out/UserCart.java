package co.sendik.identity.port.out;

import co.sendik.identity.dto.UserDataExport;
import co.sendik.identity.model.UserId;
import java.util.List;

/**
 * El carrito de una persona, visto desde {@code identity}. HU-015.
 *
 * <p><strong>Existe porque el carrito es dato personal y esta capa responde por el.</strong>
 * Dice que le interesa a una persona identificada, y ademas que estuvo a punto de comprarlo:
 * es una intencion de compra y no solo un gusto. Entra en la descarga de datos y el cierre de
 * cuenta se lo lleva (docs/operacion/datos-personales.md).
 *
 * <p>Lo que {@code identity} no puede hacer es leer ni escribir la tabla, que es del
 * catalogo: se lo pide por este puerto, y el adaptador pregunta por un caso de uso publico
 * del otro contexto. Es el mismo patron que {@link UserFavorites}, y son dos puertos y no uno
 * a proposito: son dos tablas con dos reglas, y el dia que una cambie la otra no tiene por
 * que enterarse.
 *
 * <p><strong>Habla de {@link UserId} y no de identificadores del catalogo.</strong> Un puerto
 * se declara con el vocabulario de quien lo define; traducir al {@code BuyerId} del otro
 * contexto es trabajo del adaptador.
 */
public interface UserCart {

    /**
     * Lo que esa persona lleva en el carrito, para su descarga de datos.
     *
     * <p>Sale entero, incluido lo que la pantalla ensena apagado: RN-094 ya lo conserva a la
     * vista, asi que aqui no hay diferencia que hacer.
     */
    List<UserDataExport.ProductoEnCarrito> de(UserId usuario);

    /**
     * Lo borra. Para el cierre de cuenta.
     *
     * <p>No falla si esta vacio, que es el caso de casi todas las cuentas que se cierran.
     */
    void borrarDe(UserId usuario);
}
