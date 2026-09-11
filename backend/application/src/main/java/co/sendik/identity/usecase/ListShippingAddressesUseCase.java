package co.sendik.identity.usecase;

import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

/**
 * La libreta de direcciones de quien pregunta. HU-016, criterios 1, 10 y 24.
 *
 * <p>De la mas reciente a la mas antigua, sin paginar: con el tope de RN-101 no hace falta.
 *
 * <p><strong>El identificador viene del token, nunca de la peticion.</strong> Un endpoint
 * que aceptara de quien leer la libreta seria una forma de saber donde vive cualquiera.
 *
 * <p>No hay caso de uso de «leer una direccion»: la libreta entera cabe en una respuesta y
 * la pantalla nunca necesita una sola. Editar carga la suya por el repositorio, que es otra
 * cosa.
 */
public class ListShippingAddressesUseCase {

    private final ShippingAddressRepository direcciones;
    private final ShippingAddressViews vistas;

    public ListShippingAddressesUseCase(ShippingAddressRepository direcciones, GeographicDivision division) {
        this.direcciones = direcciones;
        this.vistas = new ShippingAddressViews(division);
    }

    /*
     * En una transaccion, y no por escribir: son dos lecturas -las direcciones y la division
     * politico-administrativa- y conviene que la libreta se arme con una sola instantanea.
     *
     * Sin readOnly = true, por lo mismo que ExportUserDataUseCase: presentation no declara
     * spring-tx, y al leer ese atributo para inyectar esta clase avisa de que no puede
     * resolverlo. Con -Xlint:all -Werror el aviso rompe la compilacion.
     */
    @Transactional
    public List<ShippingAddressView> execute(UserId usuario) {
        return vistas.de(direcciones.deCuenta(usuario));
    }
}
