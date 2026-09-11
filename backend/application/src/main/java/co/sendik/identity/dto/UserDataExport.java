package co.sendik.identity.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Todo lo que Sendik guarda de una persona, para que se lo lleve. Criterio 22.
 *
 * <p><strong>Lo que no esta aqui importa tanto como lo que esta.</strong> No lleva
 * el hash de la contrasena ni el de ningun token: son secretos del sistema, no
 * datos de la persona, y entregarlos no le sirve de nada mientras que exponerlos
 * si tiene coste (docs/operacion/datos-personales.md). Tampoco la IP hasheada de
 * los consentimientos, por lo mismo: un hash no le dice nada a quien lo recibe.
 *
 * <p>Si lleva la evidencia de consentimiento con su version y su fecha, que es
 * justamente lo que le permite comprobar a que dijo que si.
 *
 * <p><strong>Lo que si esta, esta entero.</strong> Todo dato personal que la
 * persona puede editar en su perfil tiene que salir aqui: ciudad y telefono son
 * datos suyos —el telefono clasificado como interno en
 * docs/operacion/datos-personales.md— y omitirlos convierte el derecho a conocer
 * en un resumen. La regla para decidir si un campo entra no es si parece
 * interesante, sino si es de la persona o del sistema.
 *
 * @param generado cuando se produjo este archivo, para que quien lo lea sepa a que
 *     momento corresponde
 */
public record UserDataExport(
        Instant generado,
        Cuenta cuenta,
        List<Consentimiento> consentimientos,
        List<Sesion> sesiones,
        List<Favorito> favoritos,
        List<ProductoEnCarrito> carrito) {

    /**
     * @param ciudad nula si nunca se puso o si se quito. Se emite igual con valor
     *     nulo en lugar de omitirse: "no tenemos tu ciudad" es una respuesta al
     *     derecho a conocer, y una clave ausente no la da
     * @param telefono lo mismo. Es dato interno, no publico
     *     (docs/operacion/datos-personales.md), pero interno significa que solo lo
     *     ve su titular, y este archivo es justamente para su titular
     */
    public record Cuenta(
            String id,
            String correo,
            String nombre,
            LocalDate fechaDeNacimiento,
            @Nullable String ciudad,
            @Nullable String telefono,
            String idioma,
            String estado,
            boolean correoVerificado,
            @Nullable Instant correoVerificadoEl,
            List<String> roles,
            Instant creadaEl) {}

    public record Consentimiento(String documento, String version, Instant aceptadoEl) {}

    /** Las mismas que muestra el criterio 17, y por el mismo motivo sin la IP. */
    public record Sesion(@Nullable String navegador, Instant iniciada, Instant expira) {}

    /**
     * Una publicacion guardada, y cuando se guardo. HU-011.
     *
     * <p><strong>Salen todas, tambien las que la lista ya no ensena.</strong> RN-071 hace
     * que un favorito cuya publicacion se vendio o se archivo deje de verse, y aqui si
     * aparece: lo que Sendik guarda de esta persona es el par y su fecha, y el derecho a
     * conocer es sobre lo que hay, no sobre lo que se muestra.
     *
     * <p>Va el identificador de la publicacion y no su titulo. El titulo es del vendedor
     * —puede cambiar, y desaparece cuando se archiva—, asi que copiarlo aqui seria
     * entregar como dato propio algo que no lo es y que ademas podria estar viejo. Lo que
     * es de esta persona es que guardo eso, y cuando.
     */
    public record Favorito(String publicacion, Instant marcadoEl) {}

    /**
     * Un producto que esta en el carrito, y cuando entro. HU-015.
     *
     * <p><strong>El carrito es dato personal por la misma razon que los favoritos, y por
     * una mas.</strong> Aquel dice que le interesa a una persona identificada; este dice
     * ademas que estuvo a punto de comprarlo, que es una intencion de compra y no solo un
     * gusto. Entra en la descarga y el cierre de cuenta se lo lleva.
     *
     * <p>Va el identificador y no el titulo, por lo mismo que en {@link Favorito}: el titulo
     * es del vendedor y puede cambiar. Lo que es de esta persona es que lo puso en su
     * carrito, y cuando.
     *
     * <p><strong>No sale el precio con el que entro.</strong> Es dato de la publicacion en
     * un instante, no de quien la agrego, y ademas es el unico campo de la fila que no
     * responde a ninguna pregunta que esta persona pueda hacerse sobre sus propios datos.
     *
     * <p>El carrito que vive en el navegador de alguien no entra aqui y no puede entrar:
     * Sendik no lo tiene. Mientras no se fusione con una cuenta no es dato de nadie
     * identificado, y en cuanto se fusiona pasa a ser estas filas.
     */
    public record ProductoEnCarrito(String publicacion, Instant agregadoEl) {}
}
