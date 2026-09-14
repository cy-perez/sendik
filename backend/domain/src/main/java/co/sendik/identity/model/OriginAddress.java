package co.sendik.identity.model;

import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * Desde donde despacha quien vende. HU-017.
 *
 * <p>Es la pareja de {@link ShippingAddress} al otro extremo del envio: aquella dice a
 * donde llega lo que alguien compra y esta dice de donde sale lo que alguien vende.
 * RN-039 la necesita para cotizar y RN-078 para que el vendedor sea el remitente de la
 * guia. Hoy no la usa nadie: guardarla no cotiza, no comprueba cobertura de recogida
 * y no promete nada.
 *
 * <p><strong>Una por cuenta y sin identificador propio.</strong> RN-039 habla de "la
 * ciudad de origen del vendedor", en singular: una persona despacha desde un sitio.
 * La clave es la cuenta, y guardar otra reemplaza la que habia.
 *
 * <p><strong>Sin nombre ni telefono.</strong> El remitente es el titular de la cuenta
 * (RN-078) y sus datos ya estan en el perfil; aqui no hay tercero. Que el perfil tenga
 * telefono lo exige el caso de uso al guardar, porque un remitente sin telefono no es
 * remitente.
 *
 * <p>Es una clase y no un {@code record} por coherencia con {@link ShippingAddress}: el
 * constructor no queda a la vista y las dos fabricas dicen de donde viene cada objeto.
 *
 * <p><strong>El departamento no es un campo.</strong> Vive dentro del codigo de
 * municipio y se deriva (RN-100).
 */
public final class OriginAddress {

    private final UserId duena;
    private final MunicipalityCode municipio;
    private final AddressLine linea;

    @Nullable
    private final AddressComplement complemento;

    @Nullable
    private final PickupInstructions indicaciones;

    @Nullable
    private final PostalCode codigoPostal;

    private final Instant creadaEn;
    private final Instant actualizadaEn;

    private OriginAddress(
            UserId duena,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant creadaEn,
            Instant actualizadaEn) {
        this.duena = Objects.requireNonNull(duena, "La cuenta duena es obligatoria");
        this.municipio = Objects.requireNonNull(municipio, "El municipio es obligatorio");
        this.linea = Objects.requireNonNull(linea, "La direccion es obligatoria");
        this.complemento = complemento;
        this.indicaciones = indicaciones;
        this.codigoPostal = codigoPostal;
        this.creadaEn = Objects.requireNonNull(creadaEn, "La fecha de creacion es obligatoria");
        this.actualizadaEn = Objects.requireNonNull(actualizadaEn, "La fecha de actualizacion es obligatoria");
    }

    /** Un origen recien escrito, para una cuenta que no tenia. */
    public static OriginAddress nueva(
            UserId duena,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant ahora) {
        return new OriginAddress(duena, municipio, linea, complemento, indicaciones, codigoPostal, ahora, ahora);
    }

    /** Un origen que ya estaba guardado. No vuelve a decidir nada. */
    public static OriginAddress reconstruir(
            UserId duena,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant creadaEn,
            Instant actualizadaEn) {
        return new OriginAddress(
                duena, municipio, linea, complemento, indicaciones, codigoPostal, creadaEn, actualizadaEn);
    }

    /**
     * El mismo origen con otros datos. Criterio 3: reemplaza, no crea una segunda.
     *
     * <p>Conserva la duena y la fecha de creacion: el origen sigue siendo el mismo
     * hecho —"esta cuenta despacha desde aqui"— con otro contenido.
     */
    public OriginAddress con(
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable PickupInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant ahora) {
        return new OriginAddress(duena, municipio, linea, complemento, indicaciones, codigoPostal, creadaEn, ahora);
    }

    public boolean esDe(UserId cuenta) {
        return duena.equals(cuenta);
    }

    public UserId duena() {
        return duena;
    }

    public MunicipalityCode municipio() {
        return municipio;
    }

    /** Derivado del codigo del municipio, nunca guardado aparte (RN-100). */
    public DepartmentCode departamento() {
        return municipio.departamento();
    }

    public AddressLine linea() {
        return linea;
    }

    public @Nullable AddressComplement complemento() {
        return complemento;
    }

    public @Nullable PickupInstructions indicaciones() {
        return indicaciones;
    }

    public @Nullable PostalCode codigoPostal() {
        return codigoPostal;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public Instant actualizadaEn() {
        return actualizadaEn;
    }

    /** Dos origenes son el mismo si son de la misma cuenta: solo hay uno por cuenta. */
    @Override
    public boolean equals(Object otro) {
        return otro instanceof OriginAddress origen && duena.equals(origen.duena);
    }

    @Override
    public int hashCode() {
        return duena.hashCode();
    }

    /**
     * No imprime nada de lo que hay dentro, ni el municipio: en que municipio despacha
     * alguien es dato personal y Spring registra los objetos en {@code DEBUG}. Es lo
     * que la prueba de registros de HU-016 destapo sobre {@code ShippingAddressData}.
     */
    @Override
    public String toString() {
        return "OriginAddress[duena=" + duena + "]";
    }
}
