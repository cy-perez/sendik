package co.sendik.identity.model;

import java.util.Objects;

/**
 * La linea de la direccion: "Calle 45 # 12-34". HU-016.
 *
 * <p><strong>No se valida contra ningun patron</strong>, y es deliberado. Una
 * direccion colombiana lleva almohadilla y guion, y se escribe con "Cra", "Dg",
 * "Tv", "Mz", "Etapa", "Km" y media docena de convenciones mas que cambian de ciudad
 * a ciudad y de barrio a barrio. Cualquier expresion regular que intente reconocerla
 * rechaza direcciones reales, y este texto no se procesa: se imprime en una guia para
 * que un mensajero lo lea.
 *
 * <p>Lo que si se hace es quitar los caracteres de control y colapsar espacios, por lo
 * mismo que en {@link co.sendik.catalog.model.Title}: un salto de linea rompe la
 * tarjeta y sirve para colar texto que no se ve.
 *
 * <p>Se guarda cifrada.
 */
public record AddressLine(String value) {

    private static final int LARGO_MINIMO = 5;
    private static final int LARGO_MAXIMO = 120;

    public AddressLine {
        Objects.requireNonNull(value, "La direccion es obligatoria");
        // `\\p{Cntrl}` en Java es solo ASCII, asi que U+2028, U+2029 y la anulacion
        // bidireccional U+202E atravesaban esto y quedaban guardados. Lo cazo la
        // revision de seguridad.
        value = value.replaceAll("[\\p{Cntrl}\\p{Cf}\\p{Zl}\\p{Zp}]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (value.length() < LARGO_MINIMO) {
            throw new IllegalArgumentException("La direccion necesita al menos " + LARGO_MINIMO + " caracteres");
        }
        if (value.length() > LARGO_MAXIMO) {
            throw new IllegalArgumentException("La direccion supera los " + LARGO_MAXIMO + " caracteres");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
