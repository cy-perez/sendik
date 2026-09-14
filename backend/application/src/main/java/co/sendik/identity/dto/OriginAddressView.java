package co.sendik.identity.dto;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/**
 * La direccion de origen tal como la ve su duena. HU-017.
 *
 * <p>Lleva el remitente —nombre y telefono del perfil— porque es lo que la pantalla
 * muestra al lado del origen (criterio 4) y lo que la guia necesitara (RN-078). El
 * telefono puede faltar si se quito del perfil despues de guardar el origen; hoy nada lo
 * impide y esta anotado en la historia como caso a cerrar cuando exista la guia.
 *
 * <p>Con el departamento por codigo y nombre, aunque no se guarde: hay mas de un
 * «San Pedro» y un municipio sin su departamento al lado no identifica un sitio.
 */
public record OriginAddressView(
        String departamentoCodigo,
        String departamentoNombre,
        String municipioCodigo,
        String municipioNombre,
        boolean municipioActivo,
        String linea,
        @Nullable String complemento,
        @Nullable String indicaciones,
        @Nullable String codigoPostal,
        String remitenteNombre,
        @Nullable String remitenteTelefono,
        Instant guardadaEl) {

    /** No imprime nada de lo que hay dentro (criterio 17). */
    @Override
    public String toString() {
        return "OriginAddressView[sin imprimir]";
    }
}
