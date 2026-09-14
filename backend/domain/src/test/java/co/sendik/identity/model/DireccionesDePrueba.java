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

    /** El origen de HU-017: desde donde despacha quien vende, con los tres opcionales. */
    static OriginAddress origen(UserId duena) {
        return OriginAddress.nueva(
                duena,
                BOGOTA,
                new AddressLine("Carrera 15 # 93-47"),
                new AddressComplement("Local 3"),
                new PickupInstructions("Entrar por el parqueadero, preguntar por Nubia"),
                new PostalCode("110221"),
                AHORA);
    }

    /** Sin complemento, sin indicaciones y sin codigo postal. */
    static OriginAddress origenMinimo(UserId duena) {
        return OriginAddress.nueva(duena, MEDELLIN, new AddressLine("Carrera 70 # 45-12"), null, null, null, AHORA);
    }
}
