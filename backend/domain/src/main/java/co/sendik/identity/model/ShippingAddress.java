package co.sendik.identity.model;

import co.sendik.shared.geo.DepartmentCode;
import co.sendik.shared.geo.MunicipalityCode;
import java.time.Instant;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

/**
 * A donde quiere alguien que le llegue lo que compra. HU-016, RN-098 a RN-104.
 *
 * <p><strong>Guardarla no promete nada.</strong> No se comprueba que exista, no se
 * geocodifica y no se mira si hay cobertura (RN-103): la cobertura se sabra al
 * cotizar, y hoy esta sin comprobar (RN-080). Este objeto es un dato guardado, no una
 * promesa de entrega.
 *
 * <p><strong>No se comparte con nadie</strong> hasta que haya un pago aprobado
 * (RN-098, que es RN-048 dicho desde este lado). Mientras no exista pedido pagado, no
 * sale de la cuenta de su dueno: ni al vendedor, ni al agregador, ni a una
 * transportadora.
 *
 * <p>Es una clase y no un {@code record} por lo mismo que {@code CartItem} y
 * {@code Favorite}: un record expone su constructor canonico, y con el a la vista
 * cualquiera fabrica una direccion con la marca de predeterminada puesta sin pasar por
 * {@link AddressBook}, que es quien sabe que solo puede haber una.
 *
 * <p><strong>El departamento no es un campo.</strong> Vive dentro del codigo de
 * municipio y se deriva (RN-100). Guardarlo aparte seria poder contradecirlo.
 */
public final class ShippingAddress {

    private final ShippingAddressId id;
    private final UserId duena;
    private final RecipientName quienRecibe;
    private final Phone telefono;
    private final MunicipalityCode municipio;
    private final AddressLine linea;

    @Nullable
    private final AddressComplement complemento;

    @Nullable
    private final DeliveryInstructions indicaciones;

    @Nullable
    private final PostalCode codigoPostal;

    private final boolean predeterminada;
    private final Instant creadaEn;
    private final Instant actualizadaEn;

    private ShippingAddress(
            ShippingAddressId id,
            UserId duena,
            RecipientName quienRecibe,
            Phone telefono,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            boolean predeterminada,
            Instant creadaEn,
            Instant actualizadaEn) {
        this.id = Objects.requireNonNull(id, "El identificador es obligatorio");
        this.duena = Objects.requireNonNull(duena, "La cuenta duena es obligatoria");
        this.quienRecibe = Objects.requireNonNull(quienRecibe, "Quien recibe es obligatorio");
        this.telefono = Objects.requireNonNull(telefono, "El telefono es obligatorio");
        this.municipio = Objects.requireNonNull(municipio, "El municipio es obligatorio");
        this.linea = Objects.requireNonNull(linea, "La direccion es obligatoria");
        this.complemento = complemento;
        this.indicaciones = indicaciones;
        this.codigoPostal = codigoPostal;
        this.predeterminada = predeterminada;
        this.creadaEn = Objects.requireNonNull(creadaEn, "La fecha de creacion es obligatoria");
        this.actualizadaEn = Objects.requireNonNull(actualizadaEn, "La fecha de actualizacion es obligatoria");
    }

    /**
     * Una direccion recien escrita.
     *
     * <p>Nace <strong>sin</strong> la marca de predeterminada. Quien decide si lo es
     * —porque es la primera de la libreta, RN-099— es
     * {@link AddressBook#laSiguienteSeriaPredeterminada()}, que es lo unico que puede
     * saberlo: una direccion no conoce a sus hermanas.
     */
    public static ShippingAddress nueva(
            UserId duena,
            RecipientName quienRecibe,
            Phone telefono,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant ahora) {
        return new ShippingAddress(
                ShippingAddressId.nuevo(),
                duena,
                quienRecibe,
                telefono,
                municipio,
                linea,
                complemento,
                indicaciones,
                codigoPostal,
                false,
                ahora,
                ahora);
    }

    /** Una direccion que ya estaba guardada. No vuelve a decidir nada. */
    public static ShippingAddress reconstruir(
            ShippingAddressId id,
            UserId duena,
            RecipientName quienRecibe,
            Phone telefono,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            boolean predeterminada,
            Instant creadaEn,
            Instant actualizadaEn) {
        return new ShippingAddress(
                id,
                duena,
                quienRecibe,
                telefono,
                municipio,
                linea,
                complemento,
                indicaciones,
                codigoPostal,
                predeterminada,
                creadaEn,
                actualizadaEn);
    }

    /**
     * La misma direccion con otros datos. Criterio 9.
     *
     * <p>Conserva identificador, duena, fecha de creacion y <strong>la marca de
     * predeterminada</strong>: editar una direccion no cambia cual es la
     * predeterminada de la libreta. Para eso esta {@code PUT /users/me/default-address},
     * que es otra operacion y otro endpoint.
     */
    public ShippingAddress con(
            RecipientName quienRecibe,
            Phone telefono,
            MunicipalityCode municipio,
            AddressLine linea,
            @Nullable AddressComplement complemento,
            @Nullable DeliveryInstructions indicaciones,
            @Nullable PostalCode codigoPostal,
            Instant ahora) {
        return new ShippingAddress(
                id,
                duena,
                quienRecibe,
                telefono,
                municipio,
                linea,
                complemento,
                indicaciones,
                codigoPostal,
                predeterminada,
                creadaEn,
                ahora);
    }

    /**
     * La misma direccion con la marca de predeterminada puesta o quitada.
     *
     * <p>La usa el caso de uso que agrega, para la primera direccion de una libreta
     * vacia (RN-099): quien sabe que es la primera es {@link AddressBook}, y quien la
     * construye ya marcada es esto.
     *
     * <p>Cambiar cual es la predeterminada <strong>entre varias</strong> no pasa por
     * aqui: son dos escrituras que tienen que ocurrir en orden —quitar la vieja antes de
     * poner la nueva, o el indice unico parcial de V21 se dispara— y eso es del
     * repositorio, en una sola transaccion.
     */
    public ShippingAddress comoPredeterminada(boolean valor) {
        if (predeterminada == valor) {
            return this;
        }
        return new ShippingAddress(
                id,
                duena,
                quienRecibe,
                telefono,
                municipio,
                linea,
                complemento,
                indicaciones,
                codigoPostal,
                valor,
                creadaEn,
                actualizadaEn);
    }

    public boolean esDe(UserId cuenta) {
        return duena.equals(cuenta);
    }

    public ShippingAddressId id() {
        return id;
    }

    public UserId duena() {
        return duena;
    }

    public RecipientName quienRecibe() {
        return quienRecibe;
    }

    public Phone telefono() {
        return telefono;
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

    public @Nullable DeliveryInstructions indicaciones() {
        return indicaciones;
    }

    public @Nullable PostalCode codigoPostal() {
        return codigoPostal;
    }

    public boolean esPredeterminada() {
        return predeterminada;
    }

    public Instant creadaEn() {
        return creadaEn;
    }

    public Instant actualizadaEn() {
        return actualizadaEn;
    }

    /** Dos direcciones son la misma si tienen el mismo identificador. */
    @Override
    public boolean equals(Object otra) {
        return otra instanceof ShippingAddress direccion && id.equals(direccion.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    /**
     * No imprime nada de lo que hay dentro.
     *
     * <p>Es la misma decision de {@code EncryptedValue}: un {@code toString} util aqui
     * invita a interpolar el objeto en un registro, y el criterio 19 prohibe que la
     * linea, el telefono o el nombre de quien recibe aparezcan en ninguno. El
     * identificador y el municipio no son dato personal por si solos.
     */
    @Override
    public String toString() {
        return "ShippingAddress[id=" + id + ", municipio=" + municipio + "]";
    }
}
