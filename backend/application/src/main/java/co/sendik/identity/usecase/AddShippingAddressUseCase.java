package co.sendik.identity.usecase;

import co.sendik.identity.dto.AddShippingAddressCommand;
import co.sendik.identity.dto.ShippingAddressView;
import co.sendik.identity.exception.AccountNoLongerExistsException;
import co.sendik.identity.exception.AddressBookFullException;
import co.sendik.identity.exception.UnknownMunicipalityException;
import co.sendik.identity.model.AddressBook;
import co.sendik.identity.model.ShippingAddress;
import co.sendik.identity.port.out.ShippingAddressRepository;
import co.sendik.identity.port.out.UserRepository;
import co.sendik.shared.port.out.GeographicDivision;
import java.time.Clock;
import org.springframework.transaction.annotation.Transactional;

/**
 * Guarda una direccion de entrega nueva. HU-016, criterios 2, 3, 6 y 8.
 *
 * <p><strong>Comprueba que la cuenta siga existiendo</strong>, como {@code AddToCartUseCase}
 * y {@code AddFavoriteUseCase}: el token de acceso sobrevive quince minutos al cierre
 * (ADR-0003) y este escribe dato personal. Sin esto quedaria una direccion viva justo
 * despues de que alguien ejerciera su derecho de supresion, y nada volveria a borrarla
 * porque el cierre ya paso. Aqui pesa mas que en el carrito: lo que quedaria vivo es donde
 * vive una persona.
 *
 * <p><strong>El orden de las comprobaciones importa.</strong> Primero la cuenta, despues el
 * municipio y al final el tope. Al reves, quien tenga la libreta llena recibiria «esta
 * llena» para un municipio inventado y «municipio desconocido» para uno real, y eso es un
 * oraculo sobre la Divipola —menor que el del carrito, porque la lista es publica, pero
 * gratuito de evitar—.
 */
public class AddShippingAddressUseCase {

    private final ShippingAddressRepository direcciones;
    private final GeographicDivision division;
    private final UserRepository usuarios;
    private final ShippingAddressViews vistas;
    private final Clock reloj;

    public AddShippingAddressUseCase(
            ShippingAddressRepository direcciones, GeographicDivision division, UserRepository usuarios, Clock reloj) {
        this.direcciones = direcciones;
        this.division = division;
        this.usuarios = usuarios;
        this.vistas = new ShippingAddressViews(division);
        this.reloj = reloj;
    }

    /**
     * @throws AccountNoLongerExistsException si la cuenta del token ya se cerro
     * @throws UnknownMunicipalityException si el municipio no existe o el DANE lo suprimio
     *     (RN-100)
     * @throws AddressBookFullException si ya tiene las diez de RN-101
     */
    @Transactional
    public ShippingAddressView execute(AddShippingAddressCommand comando) {
        if (usuarios.buscarPorId(comando.usuario()).isEmpty()) {
            throw new AccountNoLongerExistsException();
        }

        ShippingAddress direccion = ShippingAddressFactory.nueva(comando.usuario(), comando.datos(), reloj.instant());

        ShippingAddressFactory.exigirMunicipioVigente(division, direccion.municipio());

        AddressBook libreta = new AddressBook(direcciones.deCuenta(comando.usuario()));
        if (libreta.estaLlena()) {
            throw new AddressBookFullException();
        }

        // RN-099: la primera de una libreta vacia nace predeterminada sin que nadie lo pida.
        // Quien lo sabe es la libreta; una direccion no conoce a sus hermanas.
        ShippingAddress guardada = direccion.comoPredeterminada(libreta.laSiguienteSeriaPredeterminada());
        direcciones.guardar(guardada);

        return vistas.de(guardada);
    }
}
