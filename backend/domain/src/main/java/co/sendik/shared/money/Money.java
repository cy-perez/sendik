package co.sendik.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Dinero en pesos colombianos. RN-029: nunca punto flotante.
 *
 * <p><strong>Sin decimales, y no por simplificar.</strong> El peso colombiano no los
 * usa en precios de venta, asi que un valor con centavos no es un dato mas preciso:
 * es un dato que alguien tecleo mal o que salio de una division que no debio
 * ocurrir. Se rechaza en el constructor en vez de redondearse en silencio, porque
 * redondear aqui esconderia el error justo donde importa.
 *
 * <p><strong>Una sola moneda y ninguna clase que la represente.</strong> Sendik opera
 * en Colombia (docs/producto/vision.md) y todo el dinero del sistema es COP. Un tipo
 * {@code Currency} que nunca toma un segundo valor no protege de nada y obliga a
 * escribirlo en cada firma. El contrato de la API si publica la moneda —
 * {@code {"amount": 185000, "currency": "COP"}}— porque un numero suelto en un JSON
 * es ambiguo para quien lo lee desde fuera; el que la escribe es el borde, con la
 * constante {@link #MONEDA}. El dia que haya una segunda moneda, esto cambia; hoy no
 * la hay.
 *
 * <p>El valor va en pesos enteros, no en la unidad menor: 185000 son ciento ochenta
 * y cinco mil pesos. Como el peso no tiene unidad menor en precios, las dos lecturas
 * coinciden y no hay factor de conversion que equivocar.
 */
public record Money(BigDecimal amount) {

    /** ISO 4217. Lo publica el borde en el objeto de dinero del contrato. */
    public static final String MONEDA = "COP";

    public Money {
        Objects.requireNonNull(amount, "El valor es obligatorio");

        if (amount.scale() > 0 && amount.stripTrailingZeros().scale() > 0) {
            throw new IllegalArgumentException("El peso colombiano no usa decimales en precios: " + amount);
        }
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("El dinero no puede ser negativo: " + amount);
        }

        amount = amount.setScale(0, RoundingMode.UNNECESSARY);
    }

    /** La forma normal de construirlo: desde un entero de pesos. */
    public static Money dePesos(long pesos) {
        return new Money(BigDecimal.valueOf(pesos));
    }

    /**
     * Suma dos cantidades. HU-015: es lo que necesita el subtotal de un grupo del carrito.
     *
     * <p><strong>Hasta hoy {@code Money} sabia comparar y no sabia sumar</strong>, porque
     * ninguna regla habia pedido todavia sumar dos precios: la comision de RN-026 se calcula
     * sobre uno solo y el catalogo nunca totaliza nada. El subtotal de RN-096 es la primera
     * cifra del proyecto que nace de sumar varias.
     *
     * <p>Suma con {@link BigDecimal} y por eso es exacta, que es RN-029 dicho en codigo. Que
     * el peso no tenga decimales no hace irrelevante la regla: la hace facil de cumplir aqui
     * y sigue siendo lo que impide que alguien meta un {@code double} manana.
     *
     * <p>No hay resta y no es un olvido. Restar dinero abre la puerta a un negativo, que el
     * constructor rechaza, y hoy no hay ninguna regla que reste: el reintegro de RN-054
     * devuelve un valor, no la diferencia de dos. Cuando exista, llegara con su regla y con
     * su prueba.
     */
    public Money mas(Money otro) {
        Objects.requireNonNull(otro, "El sumando es obligatorio");

        return new Money(amount.add(otro.amount));
    }

    public boolean esMenorQue(Money otro) {
        return amount.compareTo(otro.amount) < 0;
    }

    public boolean esMayorQue(Money otro) {
        return amount.compareTo(otro.amount) > 0;
    }

    /** Cero es dinero valido como cantidad, y no lo es como precio. Eso lo decide quien lo use. */
    public boolean esCero() {
        return amount.signum() == 0;
    }

    /** El valor en pesos enteros, para la API y para la base. */
    public long enPesos() {
        return amount.longValueExact();
    }

    @Override
    public String toString() {
        return amount + " " + MONEDA;
    }
}
