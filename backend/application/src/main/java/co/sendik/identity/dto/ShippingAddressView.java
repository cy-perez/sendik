package co.sendik.identity.dto;

import co.sendik.identity.model.ShippingAddress;
import co.sendik.shared.geo.Department;
import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.Municipality;
import co.sendik.shared.geo.MunicipalityCode;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;

/**
 * Una direccion de entrega tal como se pinta. HU-016.
 *
 * <p>Es la direccion guardada <strong>cruzada con la division politico-administrativa</strong>:
 * de la fila salen los codigos, y los dos nombres salen de V20. Son dos cosas y no una, como
 * {@code CartItem} y {@code CartLine}: lo guardado es el codigo —estable, y lo unico con lo
 * que se puede cotizar— y lo que se lee es el nombre.
 *
 * <p><strong>Lleva el departamento aunque la fila no lo guarde.</strong> Se deriva del codigo
 * del municipio (RN-100) y llega hasta aqui porque la pantalla lo necesita: hay mas de un
 * «San Pedro» y mas de una «Santa María» en Colombia, y un municipio sin su departamento al
 * lado no identifica un sitio (criterio 24).
 *
 * @param municipioActivo falso cuando el DANE lo suprimio. La direccion se sigue leyendo
 *     igual (criterio 23); lo que cambia es que al editarla hay que elegir otro
 */
public record ShippingAddressView(
        String id,
        String quienRecibe,
        String telefono,
        String departamentoCodigo,
        String departamentoNombre,
        String municipioCodigo,
        String municipioNombre,
        boolean municipioActivo,
        String linea,
        @Nullable String complemento,
        @Nullable String indicaciones,
        @Nullable String codigoPostal,
        boolean predeterminada,
        Instant guardadaEl) {

    /**
     * Cruza una libreta entera con la division politico-administrativa.
     *
     * <p>Vive aqui y no en una clase aparte del paquete {@code usecase} porque alli solo
     * viven casos de uso, y {@code ArchitectureTest} lo comprueba. Que la vista sepa armarse
     * es ademas lo que evita escribir cuatro veces la misma union: la piden el caso de uso
     * que agrega, el que edita, el que lista y la descarga de datos.
     *
     * <p><strong>Dos consultas y no una por fila.</strong> Los municipios se piden todos de
     * golpe y los departamentos son treinta y tres, asi que pintar una libreta entera cuesta
     * lo mismo que pintar una sola direccion.
     */
    public static List<ShippingAddressView> de(Collection<ShippingAddress> direcciones, GeographicDivision division) {
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

    public static ShippingAddressView de(ShippingAddress direccion, GeographicDivision division) {
        return de(List.of(direccion), division).getFirst();
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
