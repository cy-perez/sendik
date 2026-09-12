package co.sendik.identity.rest;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.EditShippingAddressCommand;
import co.sendik.identity.dto.RemoveShippingAddressCommand;
import co.sendik.identity.dto.SetDefaultAddressCommand;
import co.sendik.identity.dto.ShippingAddressData;
import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.model.ShippingAddressId;
import co.sendik.identity.model.UserId;
import co.sendik.identity.rest.dto.AddressBookResponse;
import co.sendik.identity.rest.dto.DefaultAddressRequest;
import co.sendik.identity.rest.dto.ShippingAddressRequest;
import co.sendik.identity.rest.dto.ShippingAddressResponse;
import co.sendik.identity.usecase.AddShippingAddressUseCase;
import co.sendik.identity.usecase.EditShippingAddressUseCase;
import co.sendik.identity.usecase.ListShippingAddressesUseCase;
import co.sendik.identity.usecase.RemoveShippingAddressUseCase;
import co.sendik.identity.usecase.SetDefaultShippingAddressUseCase;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * La libreta de direcciones de entrega. HU-016.
 *
 * <p><strong>Cuelga de {@code /users/me}</strong>, como el carrito y los favoritos: una
 * direccion es de la persona y no de otra cosa, alli la regla de seguridad ya es
 * «autenticado», y la ruta dice de quien es el dato. El identificador de quien pide sale
 * siempre del {@code sub} del token y jamas de la peticion.
 *
 * <p><strong>Un solo controlador para dos recursos.</strong> {@code /addresses} y
 * {@code /default-address} son hermanos y no padre e hijo: la predeterminada es un recurso
 * singular de la persona, no un sub-recurso de una direccion. Por eso el mapeo de la clase es
 * {@code /users/me} y cada metodo pone su segmento.
 *
 * <p>Con {@code FEATURE_CHECKOUT} apagada el bean no existe, asi que las cinco rutas
 * responden lo mismo que una que no existe: 404 con {@code COMMON_NOT_FOUND}, nunca 403
 * (criterio 25). Es el mismo mecanismo de {@code CartController}.
 *
 * <p>Ninguno de los cinco metodos decide nada: RN-099, RN-100 y RN-101 las comprueban el
 * dominio y los casos de uso, y aqui solo se traduce.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me")
@ConditionalOnProperty(prefix = "sendik.features", name = "checkout", havingValue = "true")
public class ShippingAddressesController {

    private final ListShippingAddressesUseCase casoDeListar;
    private final AddShippingAddressUseCase casoDeAgregar;
    private final EditShippingAddressUseCase casoDeEditar;
    private final RemoveShippingAddressUseCase casoDeQuitar;
    private final SetDefaultShippingAddressUseCase casoDeMarcar;

    public ShippingAddressesController(
            ListShippingAddressesUseCase casoDeListar,
            AddShippingAddressUseCase casoDeAgregar,
            EditShippingAddressUseCase casoDeEditar,
            RemoveShippingAddressUseCase casoDeQuitar,
            SetDefaultShippingAddressUseCase casoDeMarcar) {
        this.casoDeListar = casoDeListar;
        this.casoDeAgregar = casoDeAgregar;
        this.casoDeEditar = casoDeEditar;
        this.casoDeQuitar = casoDeQuitar;
        this.casoDeMarcar = casoDeMarcar;
    }

    /** La libreta entera, de la mas reciente a la mas antigua. Criterios 1 y 10. */
    @GetMapping("/addresses")
    public AddressBookResponse libreta(@AuthenticationPrincipal Jwt token) {
        return new AddressBookResponse(casoDeListar.execute(quienDe(token)).stream()
                .map(ShippingAddressResponse::de)
                .toList());
    }

    /**
     * Guarda una direccion nueva. Criterios 2, 3 y 8.
     *
     * <p>201 con {@code Location}, que es lo que el contrato pide para crear. Aqui si se puede
     * —al reves que en el registro— porque no hay nada que ocultar sobre si el recurso existia:
     * no existia, lo acaba de escribir quien lo pide.
     */
    @PostMapping("/addresses")
    public ResponseEntity<ShippingAddressResponse> agregar(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody ShippingAddressRequest peticion) {
        ShippingAddressView guardada =
                casoDeAgregar.execute(new AddShippingAddressCommand(quienDe(token), datosDe(peticion)));

        return ResponseEntity.created(URI.create("/api/v1/users/me/addresses/" + guardada.id()))
                .body(ShippingAddressResponse.de(guardada));
    }

    /** Cambia sus datos. Criterio 9. Una ajena responde 404 y nunca 403 (criterio 15). */
    @PutMapping("/addresses/{addressId}")
    public ShippingAddressResponse editar(
            @AuthenticationPrincipal Jwt token,
            @PathVariable String addressId,
            @Valid @RequestBody ShippingAddressRequest peticion) {
        return ShippingAddressResponse.de(casoDeEditar.execute(
                new EditShippingAddressCommand(quienDe(token), ShippingAddressId.de(addressId), datosDe(peticion))));
    }

    /**
     * La quita. Criterios 10 a 12 y 14.
     *
     * <p><strong>204 tambien si no estaba</strong>, y tambien si era de otra persona. Con un
     * 404 ahi, un reintento de red acabaria en un mensaje de error sobre algo que salio como
     * se pidio, y ademas distinguirlo confirmaria que ese identificador existe.
     *
     * <p>Sin cuerpo. Cual quedo como predeterminada cuando esta lo era (criterio 11) lo sabe
     * la pantalla releyendo la libreta, que es lo que hace de todos modos al invalidar la
     * consulta: inventar un cuerpo en un {@code DELETE} para eso rompe la tabla de metodos del
     * contrato.
     */
    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> quitar(@AuthenticationPrincipal Jwt token, @PathVariable String addressId) {
        casoDeQuitar.execute(new RemoveShippingAddressCommand(quienDe(token), ShippingAddressId.de(addressId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * Elige cual es la predeterminada. Criterio 13.
     *
     * <p>{@code PUT} sobre un recurso singular de la persona: marcar es reemplazar su valor.
     * Marcar la que ya lo era responde igual y no cambia nada.
     */
    @PutMapping("/default-address")
    public ResponseEntity<Void> marcarPredeterminada(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody DefaultAddressRequest peticion) {
        casoDeMarcar.execute(new SetDefaultAddressCommand(quienDe(token), ShippingAddressId.de(peticion.addressId())));

        return ResponseEntity.noContent().build();
    }

    private static ShippingAddressData datosDe(ShippingAddressRequest peticion) {
        return new ShippingAddressData(
                peticion.recipientName(),
                peticion.phone(),
                peticion.municipalityCode(),
                peticion.line(),
                peticion.complement(),
                peticion.instructions(),
                peticion.postalCode());
    }

    /** El {@code sub} del token es el identificador de la cuenta. */
    private static UserId quienDe(Jwt token) {
        return UserId.de(token.getSubject());
    }
}
