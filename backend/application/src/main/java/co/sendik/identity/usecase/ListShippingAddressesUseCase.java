package co.sendik.identity.usecase;

import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.model.UserId;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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
 *
 * <p><strong>Aqui vive ademas el cruce con la division politico-administrativa</strong>, que
 * comparten los cuatro casos de uso que devuelven una direccion. Es el patron de
 * {@code ReadCartUseCase.conVendedores}: un estatico de paquete con el puerto por argumento.
 */
public class ListShippingAddressesUseCase {

    private final ShippingAddressRepository direcciones;
    private final GeographicDivision division;

    public ListShippingAddressesUseCase(ShippingAddressRepository direcciones, GeographicDivision division) {
        this.direcciones = direcciones;
        this.division = division;
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
        return conLaDivision(direcciones.deCuenta(usuario), division);
    }

    /**
     * Cruza direcciones guardadas con la division politico-administrativa.
     *
     * <p>Estatico y con el puerto por argumento porque lo comparten {@code Add}, {@code Edit}
     * y {@code ExportUserDataUseCase}, que hacen exactamente lo mismo con una direccion o con
     * una libreta entera. Es el mismo reparto que {@code ReadCartUseCase.conVendedores}.
     *
     * <p><strong>Dos consultas y no una por fila.</strong> Los municipios se piden todos de
     * golpe y los departamentos son treinta y tres, asi que pintar una libreta entera cuesta
     * lo mismo que pintar una sola direccion.
     */
    static List<ShippingAddressView> conLaDivision(
            Collection<ShippingAddress> direcciones, GeographicDivision division) {
        if (direcciones.isEmpty()) {
            return List.of();
        }

        Map<MunicipalityCode, Municipality> municipios = division.buscarMunicipios(
                direcciones.stream().map(ShippingAddress::municipio).distinct().toList());

        Map<DepartmentCode, Department> departamentos =
                division.departamentos().stream().collect(Collectors.toMap(Department::codigo, Function.identity()));

        return direcciones.stream()
                .map(direccion -> armar(direccion, municipios, departamentos))
                .toList();
    }

    static ShippingAddressView conLaDivision(ShippingAddress direccion, GeographicDivision division) {
        return conLaDivision(List.of(direccion), division).getFirst();
    }

    /**
     * Un municipio que no este en el mapa es imposible: la clave foranea de V21 lo impide y
     * {@code buscarMunicipios} devuelve tambien los inactivos. Se afirma en vez de suponerse,
     * para que el dia que eso cambie falle aqui y no pintando una tarjeta vacia.
     */
    private static ShippingAddressView armar(
            ShippingAddress direccion,
            Map<MunicipalityCode, Municipality> municipios,
            Map<DepartmentCode, Department> departamentos) {
        Municipality municipio = municipios.get(direccion.municipio());
        if (municipio == null) {
            throw new IllegalStateException("La direccion " + direccion.id() + " apunta a un municipio que no existe");
        }

        Department departamento = departamentos.get(direccion.departamento());
        if (departamento == null) {
            throw new IllegalStateException(
                    "El municipio " + municipio.codigo() + " apunta a un departamento que no existe");
        }

        return new ShippingAddressView(
                direccion.id().toString(),
                direccion.quienRecibe().value(),
                direccion.telefono().value(),
                departamento.codigo().value(),
                departamento.nombre(),
                municipio.codigo().value(),
                municipio.nombre(),
                municipio.activo(),
                direccion.linea().value(),
                direccion.complemento() == null ? null : direccion.complemento().value(),
                direccion.indicaciones() == null
                        ? null
                        : direccion.indicaciones().value(),
                direccion.codigoPostal() == null
                        ? null
                        : direccion.codigoPostal().value(),
                direccion.esPredeterminada(),
                direccion.creadaEn());
    }
}
