package co.sendik.shared.geo;

import java.util.Objects;

/**
 * Un municipio de la division politico-administrativa del DANE. HU-016, RN-100.
 *
 * <p>En la misma tabla y con el mismo tipo viven los municipios, las dieciocho areas
 * no municipalizadas y la isla que la Divipola lista. Para una direccion de entrega
 * los tres son lo mismo: el sitio al que hay que llevar algo.
 *
 * <p><strong>Se marca inactivo en vez de borrarse</strong> (criterio 23). Cuando el
 * DANE suprime uno, la fila se queda: hay direcciones guardadas que la apuntan, y
 * borrarla dejaria una direccion de alguien real sin poder leerse. Lo que cambia es
 * que deja de ofrecerse al elegir.
 *
 * <p>El departamento no se guarda aparte: {@link MunicipalityCode#departamento()} lo
 * deriva del propio codigo.
 */
public record Municipality(MunicipalityCode codigo, String nombre, boolean activo) {

    public Municipality {
        Objects.requireNonNull(codigo, "El codigo del municipio es obligatorio");
        Objects.requireNonNull(nombre, "El nombre del municipio es obligatorio");

        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del municipio no puede venir vacio");
        }
    }

    public DepartmentCode departamento() {
        return codigo.departamento();
    }
}
