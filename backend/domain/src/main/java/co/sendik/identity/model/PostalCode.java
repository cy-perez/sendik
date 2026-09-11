package co.sendik.identity.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Codigo postal colombiano: seis digitos. HU-016.
 *
 * <p><strong>Opcional, y eso es una apuesta anotada.</strong> En Colombia casi nadie
 * se lo sabe y las direcciones se entregan sin el, asi que exigirlo dejaria fuera a
 * la mayoria. Lo que todavia no se sabe es si Skydropx lo necesita para cotizar: es
 * una de las cosas que hay que comprobar cuando conteste, y si resulta obligatorio
 * deja de ser opcional y hay que pedirselo a quien ya guardo direcciones sin el
 * (HU-016, "Cuando revisar").
 *
 * <p>Se guarda cifrado con el resto de los campos libres, aunque por si solo no
 * identifique a nadie: va dentro del mismo documento y separarlo no ganaria nada.
 */
public record PostalCode(String value) {

    private static final Pattern VALIDO = Pattern.compile("\\d{6}");

    public PostalCode {
        Objects.requireNonNull(value, "El codigo postal es obligatorio: para no tenerlo, se deja sin poner");
        value = value.replaceAll("\\s", "");

        if (!VALIDO.matcher(value).matches()) {
            throw new IllegalArgumentException("El codigo postal colombiano son seis digitos, y llego: " + value);
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
