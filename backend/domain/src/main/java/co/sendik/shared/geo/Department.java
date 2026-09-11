package co.sendik.shared.geo;

import java.util.Objects;

/**
 * Un departamento de la division politico-administrativa del DANE. HU-016.
 *
 * <p>Dato de referencia y no de nadie: se siembra con V20 y solo se lee, para poblar
 * el primer selector del formulario de direccion.
 *
 * <p><strong>El nombre no se traduce.</strong> Es un nombre propio y va igual en
 * espanol y en ingles (criterio 26 de HU-016), asi que aqui hay un solo texto y no
 * uno por idioma, al reves que en {@code Category}.
 */
public record Department(DepartmentCode codigo, String nombre) {

    public Department {
        Objects.requireNonNull(codigo, "El codigo del departamento es obligatorio");
        Objects.requireNonNull(nombre, "El nombre del departamento es obligatorio");

        if (nombre.isBlank()) {
            throw new IllegalArgumentException("El nombre del departamento no puede venir vacio");
        }
    }
}
