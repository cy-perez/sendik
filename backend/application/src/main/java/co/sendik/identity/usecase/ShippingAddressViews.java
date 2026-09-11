package co.sendik.identity.usecase;

import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.model.ShippingAddress;
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

/**
 * Cruza direcciones guardadas con la division politico-administrativa. HU-016.
 *
 * <p>Existe porque los cuatro casos de uso que devuelven una direccion hacen exactamente lo
 * mismo con ella, y hacerlo en cada uno seria escribir cuatro veces la misma union. Es la
 * misma razon por la que el carrito tiene un solo sitio donde se suma.
 *
 * <p><strong>Dos consultas y no una por fila.</strong> Los municipios se piden todos de
 * golpe y los departamentos son treinta y tres, asi que pintar una libreta entera cuesta lo
 * mismo que pintar una sola direccion.
 */
final class ShippingAddressViews {

    private final GeographicDivision division;

    ShippingAddressViews(GeographicDivision division) {
        this.division = division;
    }

    List<ShippingAddressView> de(Collection<ShippingAddress> direcciones) {
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

    ShippingAddressView de(ShippingAddress direccion) {
        return de(List.of(direccion)).getFirst();
    }

    /**
     * <p>Un municipio que no este en el mapa es imposible: la clave foranea de V21 lo
     * impide y {@code buscarMunicipios} devuelve tambien los inactivos. Se afirma en vez de
     * suponerse, para que el dia que eso cambie falle aqui y no pintando una tarjeta vacia.
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
