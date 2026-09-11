package co.sendik.identity.rest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Cuerpo de {@code POST} y {@code PUT} sobre {@code /api/v1/users/me/addresses}. HU-016.
 *
 * <p>El mismo para crear y para editar: los campos son los mismos y lo unico que distingue
 * una operacion de la otra es que la segunda dice cual. {@code PUT} y no {@code PATCH}, por
 * lo mismo que {@link UpdateProfileRequest}: se manda la direccion entera y se guarda entera,
 * y con {@code PATCH} habria que distinguir «no mande este campo» de «lo deje vacio», que es
 * justo donde se pierde el borrado de un dato.
 *
 * <p><strong>No hay campo de departamento, y esa ausencia es la regla.</strong> El codigo del
 * municipio lleva dentro el de su departamento (RN-100), asi que un par incoherente no puede
 * existir porque no hay par: el criterio 5 de la historia se cumple por construccion. Lo que
 * este DTO no tenga es lo que nadie puede contradecir.
 *
 * <p><strong>Aqui se comprueba la forma y no el contenido.</strong> Que el municipio exista
 * de verdad lo decide el caso de uso contra la division sembrada; que la linea de direccion
 * sea una direccion no lo decide nadie, y es a proposito: una direccion colombiana lleva
 * almohadilla y guion y se escribe de quince maneras, asi que cualquier patron rechazaria
 * direcciones reales.
 *
 * @param predeterminada no existe. Cambiar cual es la predeterminada es otra operacion y otro
 *     endpoint, {@code PUT /users/me/default-address}: si estuviera aqui, editar una
 *     direccion podria mover la predeterminada sin que nadie lo pidiera
 */
public record ShippingAddressRequest(
        @NotBlank @Size(min = 2, max = 80) String recipientName,
        @NotBlank @Size(min = 7, max = 30) String phone,
        @NotBlank @Pattern(regexp = "\\d{5}") String municipalityCode,
        @NotBlank @Size(min = 5, max = 120) String line,
        @Nullable @Size(max = 60) String complement,
        @Nullable @Size(max = 200) String instructions,
        @Nullable @Pattern(regexp = "\\d{6}|") String postalCode) {}
