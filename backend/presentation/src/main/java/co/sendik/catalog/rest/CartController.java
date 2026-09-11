package co.sendik.catalog.rest;

import co.sendik.catalog.dto.CartCommand;
import co.sendik.catalog.dto.CartItemState;
import co.sendik.catalog.dto.MergeCartCommand;
import co.sendik.catalog.dto.MergeResult;
import co.sendik.catalog.model.BuyerId;
import co.sendik.catalog.model.ListingId;
import co.sendik.catalog.rest.dto.CartItemStateResponse;
import co.sendik.catalog.rest.dto.CartResponse;
import co.sendik.catalog.rest.dto.MergeCartRequest;
import co.sendik.catalog.rest.dto.MergeCartResponse;
import co.sendik.catalog.rest.mapper.Carts;
import co.sendik.catalog.usecase.AddToCartUseCase;
import co.sendik.catalog.usecase.MergeCartUseCase;
import co.sendik.catalog.usecase.ReadCartItemStateUseCase;
import co.sendik.catalog.usecase.ReadCartUseCase;
import co.sendik.catalog.usecase.RemoveFromCartUseCase;
import co.sendik.shared.port.out.PublicFileStore;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.List;
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
 * El carrito de quien pregunta. HU-015.
 *
 * <p><strong>Cuelga de {@code /users/me} y no de {@code /listings/{id}/cart}</strong>, por lo
 * mismo que {@code FavoritesController}: el carrito es de la persona y no de la publicacion,
 * bajo {@code /users/me} la regla de seguridad ya es «autenticado» y no hay que inventar
 * ninguna, y la ruta dice de quien es el dato.
 *
 * <p><strong>Detras de {@code FEATURE_CHECKOUT}.</strong> Con la bandera apagada el
 * controlador no se crea y las cinco rutas responden 404: no rechazan, no estan. Y no hace
 * falta tocar {@code SecurityConfig}: la regla de {@code /api/v1/users/**} es «autenticado»,
 * asi que la peticion atraviesa la cadena, no encuentra manejador y sale el 404 que
 * corresponde. Una regla por rol habria respondido 403 en el filtro y con eso habria
 * confirmado que la funcionalidad esta ahi, apagada.
 *
 * <p><strong>El identificador de quien compra sale del token, nunca de la peticion.</strong>
 * Es la regla de backend/CLAUDE.md, y aqui es lo unico que impide llenar el carrito de otra
 * persona con lo que uno quiera que compre. No hay ninguna ruta que acepte de quien se quiere
 * leer o escribir.
 *
 * <p>Ninguna de las cinco decide nada: RN-092 y RN-097 las comprueban el dominio y los casos
 * de uso, y aqui solo se traduce.
 */
@RestController
@Validated
@RequestMapping("/api/v1/users/me/cart")
@ConditionalOnProperty(prefix = "sendik.features", name = "checkout", havingValue = "true")
public class CartController {

    private final AddToCartUseCase casoDeAgregar;
    private final RemoveFromCartUseCase casoDeQuitar;
    private final ReadCartItemStateUseCase casoDeConsultar;
    private final ReadCartUseCase casoDeLeer;
    private final MergeCartUseCase casoDeFusionar;
    private final PublicFileStore almacen;

    public CartController(
            AddToCartUseCase casoDeAgregar,
            RemoveFromCartUseCase casoDeQuitar,
            ReadCartItemStateUseCase casoDeConsultar,
            ReadCartUseCase casoDeLeer,
            MergeCartUseCase casoDeFusionar,
            PublicFileStore almacen) {
        this.casoDeAgregar = casoDeAgregar;
        this.casoDeQuitar = casoDeQuitar;
        this.casoDeConsultar = casoDeConsultar;
        this.casoDeLeer = casoDeLeer;
        this.casoDeFusionar = casoDeFusionar;
        this.almacen = almacen;
    }

    /**
     * El carrito entero, ya agrupado y con sus subtotales. Criterios 14 a 24.
     *
     * <p><strong>Sin paginacion, y no es un olvido.</strong> El tope de RN-097 la hace
     * innecesaria, y una suma paginada no es una suma: un tramo de la mitad del carrito daria
     * un subtotal que no es el subtotal de nada.
     */
    @GetMapping
    public CartResponse carrito(@AuthenticationPrincipal Jwt token) {
        return Carts.de(casoDeLeer.execute(quienDe(token)), almacen);
    }

    /**
     * Agrega el producto. Criterios 2, 4 y 7.
     *
     * <p><strong>{@code PUT} y no {@code POST}, y 204 en vez de 201</strong>, por lo mismo que
     * marcar un favorito: agregar es idempotente y la misma peticion repetida deja el mismo
     * carrito, que es exactamente lo que {@code PUT} promete. Con {@code POST} y 201, el
     * segundo intento —un reintento de red, dos pestanas— tendria que elegir entre mentir con
     * otro 201 o inventar un conflicto que no existe.
     */
    @PutMapping("/items/{listingId}")
    public ResponseEntity<Void> agregar(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        casoDeAgregar.execute(new CartCommand(quienDe(token), ListingId.de(listingId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * Lo quita. Criterio 3.
     *
     * <p>Idempotente: quitar lo que no esta responde 204 y no 404. Con un 404 ahi, el doble
     * pulsado acabaria en un mensaje de error sobre algo que salio como se pidio.
     */
    @DeleteMapping("/items/{listingId}")
    public ResponseEntity<Void> quitar(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        casoDeQuitar.execute(new CartCommand(quienDe(token), ListingId.de(listingId)));

        return ResponseEntity.noContent().build();
    }

    /**
     * El estado del control para una publicacion concreta. Criterios 1 y 5.
     *
     * <p><strong>Es la ruta que permite que la ficha publica siga siendo publica.</strong>
     * {@code GET /listings/{id}} responde hoy lo mismo para cualquiera y se renderiza en el
     * servidor; anadirle un campo «esto esta en tu carrito» la volveria distinta por persona.
     * El estado se pide aparte y desde el navegador, despues de hidratar.
     *
     * <p>Lectura puntual y no un filtro sobre el carrito: es mas barata y no obliga a la ficha
     * a descargar veinte publicaciones para mirar una.
     */
    @GetMapping("/items/{listingId}")
    public CartItemStateResponse estado(@AuthenticationPrincipal Jwt token, @PathVariable String listingId) {
        CartItemState estado = casoDeConsultar.execute(new CartCommand(quienDe(token), ListingId.de(listingId)));

        return new CartItemStateResponse(estado.enElCarrito(), estado.elegible());
    }

    /**
     * Une lo que traia el navegador. Criterios 9, 10 y 12.
     *
     * <p><strong>{@code POST} sobre el recurso y sin verbo en la ruta.</strong> No hay
     * {@code /merge}: el contrato no admite verbos, y esto es «agregar varios a este recurso».
     * Que la operacion sea una union la hace idempotente de hecho, asi que repetirla no
     * necesita cabecera de idempotencia ni la inventa.
     *
     * <p>200 y no 201: no nace un recurso nuevo, cambia el que ya existia. Devuelve el carrito
     * ya fusionado para que la pantalla no tenga que pedirlo otra vez, y lo que no entro para
     * que el navegador limpie su copia local.
     */
    @PostMapping
    public MergeCartResponse fusionar(
            @AuthenticationPrincipal Jwt token, @Valid @RequestBody MergeCartRequest peticion) {

        // **Un identificador malformado se descarta, no tumba la fusion.** Es el caso borde
        // que la historia describe —«el carrito del navegador manipulado a mano»— y no estaba
        // cubierto: `ListingId.de` lanza sobre lo que no es un UUID, y con un solo valor malo
        // la peticion entera salia 400. Peor: el navegador vacia su carrito al terminar la
        // fusion salga como salga, asi que ese 400 se llevaba por delante los diecinueve
        // identificadores buenos.
        //
        // Van a `notMerged`, que es el campo que existe para lo que no entro, y se mezclan con
        // los demas descartes sin distinguirse: decir «este estaba mal escrito» seria decir
        // sobre esos identificadores algo que RN-068 no deja decir del resto.
        List<String> malformados = new ArrayList<>();
        List<ListingId> validos = new ArrayList<>();
        for (String crudo : peticion.listingIds()) {
            try {
                validos.add(ListingId.de(crudo));
            } catch (IllegalArgumentException e) {
                malformados.add(crudo);
            }
        }

        MergeResult resultado = casoDeFusionar.execute(new MergeCartCommand(quienDe(token), validos));

        List<String> noEntraron = new ArrayList<>(
                resultado.noEntraron().stream().map(ListingId::toString).toList());
        noEntraron.addAll(malformados);

        return new MergeCartResponse(Carts.de(resultado.carrito(), almacen), noEntraron);
    }

    /**
     * El {@code sub} del token es el identificador de la cuenta.
     *
     * <p>Se usa siempre este y nunca un parametro de la peticion: es lo unico que el cliente no
     * puede elegir, y es lo que hace que el carrito de cada persona sea suyo sin ninguna
     * comprobacion adicional. No hay forma de nombrar el carrito de otra persona porque no hay
     * donde escribirla.
     */
    private static BuyerId quienDe(Jwt token) {
        return BuyerId.de(token.getSubject());
    }
}
