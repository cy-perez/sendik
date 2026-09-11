package co.sendik.identity.model;

import co.sendik.shared.geo.MunicipalityCode;
import java.time.Instant;

/**
 * Direcciones para las pruebas, por constructor y no por SQL repetido (backend/CLAUDE.md).
 */
final class DireccionesDePrueba {

    static final Instant AHORA = Instant.parse("2026-09-11T10:00:00Z");
    static final MunicipalityCode BOGOTA = new MunicipalityCode("11001");
    static final MunicipalityCode MEDELLIN = new MunicipalityCode("05001");

    private DireccionesDePrueba() {}

    static ShippingAddress nueva(UserId duena) {
        return nueva(duena, AHORA);
    }

    static ShippingAddress nueva(UserId duena, Instant cuando) {
        return ShippingAddress.nueva(
                duena,
                new RecipientName("Ana María Ruiz"),
                new Phone("3001234567"),
                BOGOTA,
                new AddressLine("Calle 45 # 12-34"),
                new AddressComplement("Apto 802"),
                new DeliveryInstructions("La casa de la esquina, el timbre no sirve"),
                new PostalCode("110111"),
                cuando);
    }

    /** Sin complemento, sin indicaciones y sin codigo postal: los tres son opcionales. */
    static ShippingAddress minima(UserId duena) {
        return ShippingAddress.nueva(
                duena,
                new RecipientName("Ana María Ruiz"),
                new Phone("3001234567"),
                MEDELLIN,
                new AddressLine("Carrera 70 # 45-12"),
                null,
                null,
                null,
                AHORA);
    }
}
