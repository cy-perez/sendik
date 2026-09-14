package co.sendik.identity.rest.dto;

import org.jspecify.annotations.Nullable;

/**
 * El perfil tal como lo ve su dueno. Criterio 21.
 *
 * <p>Lleva el correo porque es la propia cuenta. Un perfil publico de vendedor
 * sera otro tipo y no incluira ni correo ni telefono
 * (docs/operacion/datos-personales.md).
 *
 * <p>De la foto sale la <strong>direccion</strong>, no la clave con la que esta
 * guardada. La clave es un detalle del almacen y el cliente no la necesita para
 * nada: lo que hace con la foto es pintarla. La compone el almacen, que es quien
 * conoce su configuracion (ADR-0018).
 *
 * <p><strong>La ciudad tiene dos fuentes desde HU-017</strong> (ADR-0042): lo escrito a
 * mano mientras no hay direccion de origen, y el municipio del origen con su departamento
 * al lado cuando la hay. {@code cityEditable} dice cual es el caso, y con ello la pantalla
 * sabe si pintar un campo o un texto con enlace al origen (criterios 10 a 12).
 */
public record ProfileResponse(
        String email,
        boolean emailVerified,
        String displayName,
        @Nullable String city,
        boolean cityEditable,
        @Nullable String phone,
        @Nullable String avatarUrl) {

    /** No imprime nada: lleva correo, telefono y, desde HU-017, el municipio de origen. */
    @Override
    public String toString() {
        return "ProfileResponse[sin imprimir]";
    }
}
