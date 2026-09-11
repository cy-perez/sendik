package co.sendik.identity.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * Una direccion de entrega tal como se pinta. HU-016.
 *
 * <p>Es la direccion guardada <strong>cruzada con la division politico-administrativa</strong>:
 * de la fila salen los codigos, y los dos nombres salen de V20. Son dos cosas y no una, como
 * {@code CartItem} y {@code CartLine}: lo guardado es el codigo —estable, y lo unico con lo
 * que se puede cotizar— y lo que se lee es el nombre.
 *
 * <p><strong>Lleva el departamento aunque la fila no lo guarde.</strong> Se deriva del codigo
 * del municipio (RN-100) y llega hasta aqui porque la pantalla lo necesita: hay mas de un
 * «San Pedro» y mas de una «Santa María» en Colombia, y un municipio sin su departamento al
 * lado no identifica un sitio (criterio 24).
 *
 * @param municipioActivo falso cuando el DANE lo suprimio. La direccion se sigue leyendo
 *     igual (criterio 23); lo que cambia es que al editarla hay que elegir otro
 */
public record ShippingAddressView(
        String id,
        String quienRecibe,
        String telefono,
        String departamentoCodigo,
        String departamentoNombre,
        String municipioCodigo,
        String municipioNombre,
        boolean municipioActivo,
        String linea,
        @Nullable String complemento,
        @Nullable String indicaciones,
        @Nullable String codigoPostal,
        boolean predeterminada,
        Instant guardadaEl) {}
