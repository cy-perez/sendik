package co.sendik.identity.rest;

import co.sendik.identity.dto.OriginAddressData;
import co.sendik.identity.dto.SaveOriginAddressCommand;
import co.sendik.identity.model.UserId;
import co.sendik.identity.rest.dto.OriginAddressRequest;
import co.sendik.identity.rest.dto.OriginAddressResponse;
import co.sendik.identity.usecase.DeleteOriginAddressUseCase;
import co.sendik.identity.usecase.ReadOriginAddressUseCase;
import co.sendik.identity.usecase.SaveOriginAddressUseCase;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La direccion de origen del vendedor. HU-017.
 *
 * <p><strong>Es un recurso singular de la persona</strong>, como {@code /default-address}:
 * cuelga de {@code /users/me}, no lleva identificador en la ruta y no hay forma de pedir
 * el de otra cuenta. El identificador de quien pide sale siempre del {@code sub} del token.
 *
 * <p><strong>El {@code GET} responde 204 cuando no hay origen, y no 404.</strong> Con la
 * bandera apagada este bean no existe y las tres rutas ya responden 404 con
 * {@code COMMON_NOT_FOUND}; si «no tengo origen» tambien fuera 404, la pantalla no podria
 * distinguir su estado vacio de «esto no esta disponible» (criterios 1 y 20).
 *
 * <p>{@code PUT} crea o reemplaza: escribir un recurso singular es reemplazar su valor,
 * haya o no algo antes, y es idempotente. Por eso no hay {@code POST} ni 201.
 *
 * <p>Con {@code FEATURE_CHECKOUT} apagada el bean no existe (criterio 20), el mismo
 * mecanismo de {@code ShippingAddressesController}.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me/origin-address")
@ConditionalOnProperty(prefix = "sendik.features", name = "checkout", havingValue = "true")
public class OriginAddressController {

    private final ReadOriginAddressUseCase casoDeLectura;
    private final SaveOriginAddressUseCase casoDeGuardar;
    private final DeleteOriginAddressUseCase casoDeBorrar;

    public OriginAddressController(
            ReadOriginAddressUseCase casoDeLectura,
            SaveOriginAddressUseCase casoDeGuardar,
            DeleteOriginAddressUseCase casoDeBorrar) {
        this.casoDeLectura = casoDeLectura;
        this.casoDeGuardar = casoDeGuardar;
        this.casoDeBorrar = casoDeBorrar;
    }

    /** El origen de quien pregunta, o 204 si no tiene. Criterio 1. */
    @GetMapping
    public ResponseEntity<OriginAddressResponse> origen(@AuthenticationPrincipal Jwt token) {
        return casoDeLectura
                .execute(quienDe(token))
                .map(vista -> ResponseEntity.ok(OriginAddressResponse.de(vista)))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Guarda o reemplaza. Criterios 2, 3, 5, 7 y 8. */
    @PutMapping
    public OriginAddressResponse guardar(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody OriginAddressRequest peticion) {
        return OriginAddressResponse.de(casoDeGuardar.execute(new SaveOriginAddressCommand(
                quienDe(token),
                new OriginAddressData(
                        peticion.municipalityCode(),
                        peticion.line(),
                        peticion.complement(),
                        peticion.instructions(),
                        peticion.postalCode()))));
    }

    /** Lo borra. 204 tambien si no habia (criterio 9). */
    @DeleteMapping
    public ResponseEntity<Void> borrar(@AuthenticationPrincipal Jwt token) {
        casoDeBorrar.execute(quienDe(token));

        return ResponseEntity.noContent().build();
    }

    private static UserId quienDe(Jwt token) {
        return UserId.de(token.getSubject());
    }
}
