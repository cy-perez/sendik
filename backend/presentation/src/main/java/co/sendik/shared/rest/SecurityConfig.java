package co.sendik.shared.rest;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Entrada de Spring Security al proyecto (HU-001, ADR-0003).
 *
 * <p>Se declara con la DSL de lambdas y un bean {@link SecurityFilterChain}. La clase
 * adaptadora con la que esto se configuraba en Spring Security 5 no existe desde hace
 * varias versiones; quien la nombra por su nombre es {@code ArchitectureTest}, que es el
 * catalogo de API prohibidas y el unico archivo exento del hook de convenciones. Aqui no
 * se escribe, o este archivo no se podria editar sin que el hook saltara.
 *
 * <p><strong>Lo ultimo es {@code denyAll} y no {@code authenticated}.</strong> Con
 * {@code authenticated}, un endpoint nuevo al que se olvide declararle su
 * autorizacion queda accesible para cualquiera con sesion, que en un marketplace es
 * cualquiera que se registre. Con {@code denyAll} devuelve 403 hasta que alguien lo
 * declare, y eso se nota en la primera prueba.
 *
 * <p><strong>Por que sigue sin CSRF con una cookie de por medio.</strong> La cookie
 * del refresco es {@code SameSite=Strict} y de ruta limitada a {@code /api/v1/auth},
 * asi que un formulario o un script de otro sitio no consigue que el navegador la
 * envie. El resto de la API se autentica con la cabecera {@code Authorization}, que
 * ningun sitio ajeno puede poner. Si algun dia la cookie tuviera que ser
 * {@code SameSite=Lax} para admitir un flujo de terceros, esta decision se cae y hay
 * que meter el token de CSRF.
 */
@Configuration
@EnableWebSecurity
// Activa @PreAuthorize. Sin esta anotacion, Spring **ignora** esas reglas y no avisa de
// nada: el metodo queda anotado, se lee como protegido y no lo esta. Se enciende aqui
// porque las rutas de revision de HU-002 la usan como segunda cerradura, ademas de la
// regla por ruta de mas abajo.
@EnableMethodSecurity
public class SecurityConfig {

    /** La forma de un UUID. Un identificador de publicacion, y nada mas. */
    private static final String UUID = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    /**
     * @param decodificadorDeSesion el de {@code SessionSecurityWiring}, pedido por nombre.
     *     Pedirlo por tipo funcionó mientras hubo uno solo; con la cola de correo
     *     encendida aparece el de Cloud Tasks y la cadena no arranca por ambigüedad
     *     (6 de septiembre de 2026). La cadena interna ya lo pedía calificado.
     */
    @Bean
    SecurityFilterChain cadenaDeFiltros(
            HttpSecurity http, ExposedFeatures expuestas, @Qualifier("jwtDecoder") JwtDecoder decodificadorDeSesion)
            throws Exception {
        // Con FEATURE_PUBLISHING apagada no se declara la regla de rol de las rutas de
        // moderacion. Sin esto respondian 403 con la bandera apagada, y un 403 confirma
        // que la funcionalidad esta ahi: el criterio 3 pide 404. Ver ExposedFeatures.
        boolean catalogoExpuesto = expuestas.publishing();
        boolean verificacionExpuesta = expuestas.sellerVerification();
        boolean catalogoPublicoExpuesto = expuestas.catalog();
        boolean carritoExpuesto = expuestas.checkout();

        http
                // El origen permitido lo aporta un bean CorsConfigurationSource que
                // vive en bootstrap: la lista sale de la configuracion, y la
                // configuracion es de infrastructure, una capa que este modulo no ve.
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sesion -> sesion.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rutas -> {
                    rutas
                            // Registro, verificacion y las tres rutas de sesion: publicas
                            // por definicion. Quien las usa no tiene token de acceso
                            // todavia, y presenta otra credencial (la contrasena, o la
                            // cookie de refresco).
                            .requestMatchers("/api/v1/auth/**")
                            .permitAll()
                            // El catalogo de entidades financieras. Token si, rol no: son
                            // veintiocho nombres de bancos, iguales para todo el mundo, y no
                            // hay nada personal que proteger. Se pide token porque solo lo
                            // necesita quien se esta verificando.
                            .requestMatchers("/api/v1/financial-institutions")
                            .authenticated()
                            // Leer una publicacion es publico, y es deliberado: es la ruta
                            // que va a usar el catalogo. **Quien decide que se ve no es esta
                            // regla sino el caso de uso**, que responde vacio tanto si no
                            // existe como si no es para quien pregunta, y sale como 404 y
                            // nunca 403 (criterio 33). Con "authenticated" aqui, un 401
                            // delataria que la publicacion existe.
                            //
                            // **Casa un identificador, no cualquier segmento.** Con
                            // `/api/v1/listings/*` esta regla se tragaria tambien la
                            // bandeja del moderador el dia que exista —`/pending`,
                            // `/queue`, el nombre que sea es un segmento igual que un
                            // id— y la dejaria publica, con el motivo del rechazo y la
                            // nota de publicaciones ajenas dentro. Y el denyAll del
                            // final no salva nada: permitAll casa primero y gana.
                            //
                            // **Patron de ruta y no expresion regular, desde HU-013**, que es
                            // cuando esto dejo de ser una cuestion de estilo.
                            //
                            // `RegexRequestMatcher` compara contra ruta + "?" + consulta sobre
                            // una cadena **ya decodificada**, asi que hacia falta un sufijo
                            // `(\?.*)?` para que `GET /api/v1/listings/{uuid}?x=1` siguiera
                            // siendo publica. Y ese sufijo no distingue el `?` que separa la
                            // consulta del `?` que llego como `%3F` dentro de un segmento:
                            //
                            //     /api/v1/listings/{uuid}%3Fx/moderation-history
                            //
                            // se decodificaba a `/api/v1/listings/{uuid}?x/moderation-history`,
                            // casaba con este permitAll y **saltaba la regla autenticada** que
                            // protege todo lo que cuelga de /listings. No llego a filtrar nada
                            // -- ListingId.de rechaza un identificador de 37 caracteres -- pero
                            // lo unico que lo impedia era una comprobacion de longitud
                            // incidental, y la peticion entraba al manejador sin token.
                            //
                            // Un patron de ruta trabaja sobre segmentos ya separados e **ignora
                            // la cadena de consulta por diseno**, asi que no necesita sufijo y
                            // el `%3F` se queda dentro del segmento, donde no casa con la
                            // plantilla del identificador. ListingSecurityTest lo fija con esa
                            // URL exacta.
                            .requestMatchers(HttpMethod.GET, "/api/v1/listings/{id:" + UUID + "}")
                            .permitAll();

                    // Revision de verificaciones: ver la cedula de otra persona y decidir
                    // sobre su solicitud. **Rol, no solo token.**
                    //
                    // Vive en su propia ruta y no bajo /users/** precisamente por esto:
                    // alli la regla es "autenticado", y cualquiera con token podria
                    // aprobar su propia verificacion. Los metodos llevan ademas
                    // @PreAuthorize, que es redundante a proposito: mover un endpoint de
                    // sitio no se lleva su autorizacion por delante.
                    //
                    // **Solo se declara si la verificacion esta expuesta**, por lo mismo
                    // que las del catalogo: con FEATURE_SELLER_VERIFICATION apagada, el
                    // controlador no se crea y esta regla contestaba 403 en el filtro,
                    // antes de que nadie buscara un manejador. HU-002 pide 404 con la
                    // bandera apagada, igual que HU-007.
                    if (verificacionExpuesta) {
                        rutas.requestMatchers("/api/v1/verifications/**").hasRole("MODERATOR");
                    } else {
                        // Con la bandera apagada hace falta igualmente una regla, y no vale
                        // omitirla: sin ninguna, la peticion cae en el denyAll del final y
                        // vuelve a salir 403, que es lo que se queria evitar. Con
                        // "authenticated" atraviesa la cadena, no encuentra manejador
                        // —el controlador no existe— y sale el 404 que corresponde.
                        //
                        // Sigue exigiendo token a proposito: si algun dia apareciera un
                        // manejador bajo esta ruta sin actualizar esto, quedaria detras de
                        // una sesion y no abierto.
                        rutas.requestMatchers("/api/v1/verifications/**").authenticated();
                    }

                    // Decision del moderador sobre una publicacion. **Rol, no solo
                    // token**, y por metodo y patron en lugar de por prefijo: la historia
                    // pone estas rutas bajo /listings/{id}, que es donde tambien escribe
                    // el vendedor, asi que no hay prefijo que las separe. Van antes que la
                    // regla generica de /listings/**, que si no se las tragaria como
                    // "autenticado" y cualquiera con sesion aprobaria su propia
                    // publicacion.
                    //
                    // Los metodos llevan ademas @PreAuthorize, redundante a proposito,
                    // igual que en la revision de verificaciones.
                    //
                    // **Solo se declara si el catalogo esta expuesto.** Con la bandera
                    // apagada no hay controlador que proteger, y esta regla respondia 403
                    // en el filtro antes de que nadie buscara un manejador. El criterio 3
                    // pide 404: la funcionalidad no esta, y un 403 diria que si.
                    if (catalogoExpuesto) {
                        rutas.requestMatchers(
                                        HttpMethod.POST,
                                        "/api/v1/listings/*/approval",
                                        "/api/v1/listings/*/rejection",
                                        "/api/v1/listings/*/removal")
                                .hasRole("MODERATOR");
                    }

                    // La bandeja del moderador de publicaciones (HU-008). Espacio propio,
                    // y por prefijo, que es lo que /listings no permitia: alli la lectura
                    // de una publicacion es publica y un segmento literal casa igual que
                    // un identificador, asi que una bandeja colgada de ahi habria salido
                    // abierta, con el motivo del rechazo y la nota de publicaciones
                    // ajenas dentro.
                    //
                    // Rol y no solo token: quien tiene sesion es un vendedor cualquiera, y
                    // esta cola es la lista de todo lo que espera decision.
                    if (catalogoExpuesto) {
                        rutas.requestMatchers("/api/v1/moderation/**").hasRole("MODERATOR");
                    } else {
                        // Criterio 3, y la misma trampa de siempre: omitir la regla no
                        // basta, porque entonces cae en el denyAll del final y vuelve a
                        // salir 403. Con "authenticated" atraviesa la cadena, no encuentra
                        // manejador —el controlador no existe sin la bandera— y sale el
                        // 404 que corresponde a una funcionalidad que no esta.
                        rutas.requestMatchers("/api/v1/moderation/**").authenticated();
                    }

                    if (catalogoExpuesto || catalogoPublicoExpuesto) {
                        // El arbol de categorias. Publico y sin token: son treinta y siete
                        // nombres iguales para todo el mundo, y el catalogo publico pide
                        // esto mismo. Se declara si esta encendida cualquiera de las dos
                        // banderas, igual que su controlador: publicar lo necesita para el
                        // formulario y el catalogo para navegar.
                        rutas.requestMatchers(HttpMethod.GET, "/api/v1/categories")
                                .permitAll();
                    } else {
                        // Con la bandera apagada hace falta una regla igualmente: sin
                        // ninguna, la peticion cae en el denyAll del final y sale 403,
                        // que es lo que el criterio 3 no quiere. Con "authenticated"
                        // atraviesa la cadena, no encuentra manejador y sale el 404.
                        rutas.requestMatchers("/api/v1/categories").authenticated();
                    }

                    // El catalogo publico. HU-009.
                    //
                    // **Va antes que la regla generica de /listings/** y no es opcional
                    // que sea asi**: en esta DSL gana la primera que casa, y
                    // `/api/v1/listings/**` casa tambien con `/api/v1/listings` a secas
                    // -el `**` admite cero segmentos-, asi que declarada primero dejaria
                    // el catalogo pidiendo token.
                    //
                    // **Solo GET, y solo la coleccion.** El patron no lleva `/**`: sin ese
                    // cuidado abriria cualquier cosa que cuelgue de /listings, y de ahi
                    // cuelgan las escrituras del vendedor y las tres decisiones del
                    // moderador. La lectura de una publicacion por identificador ya tiene
                    // su propia regla mas arriba, con su expresion regular.
                    //
                    // **Sin nada para la cadena de consulta**, desde HU-013: un patron de ruta
                    // la ignora por diseno, asi que el catalogo se puede pedir con `limit` y
                    // `cursor` sin que la ruta deje de ser publica. Con la expresion regular
                    // hacia falta un sufijo, y ese sufijo era el agujero que se explica en la
                    // regla de la lectura por identificador.
                    if (catalogoPublicoExpuesto) {
                        rutas.requestMatchers(HttpMethod.GET, "/api/v1/listings")
                                .permitAll()
                                // El perfil del vendedor y su escaparate. Prefijo entero
                                // porque de /sellers no cuelga nada que no sea publico: es
                                // justo la razon de que estas dos rutas no vivan bajo
                                // /users, que exige token unas lineas mas abajo.
                                .requestMatchers(HttpMethod.GET, "/api/v1/sellers/**")
                                .permitAll();
                    } else {
                        // Con la bandera apagada hace falta una regla igualmente, por lo
                        // mismo que en el arbol de categorias: sin ninguna, /sellers cae en
                        // el denyAll del final y sale 403, que confirma que el catalogo
                        // esta ahi. Con "authenticated" atraviesa la cadena, no encuentra
                        // manejador y sale el 404 que pide el criterio 22.
                        rutas.requestMatchers("/api/v1/sellers/**").authenticated();
                    }

                    // El carrito de quien no ha entrado. HU-015, criterio 13.
                    //
                    // **Publica y sin token, que es toda la razon de que esta ruta exista.**
                    // Devuelve publicaciones del catalogo, que ya son publicas por
                    // identificador desde HU-009: quien pide veinte identificadores podria
                    // pedir veinte fichas. Lo que agrega es agruparlas por vendedor y
                    // sumarlas, para que esa suma no haya que escribirla tambien en el
                    // navegador (ADR-0037).
                    //
                    // **Solo GET y solo la coleccion**, sin `/**`: de /carts no cuelga hoy
                    // nada mas, y abrir el prefijo entero seria dejar la puerta abierta a lo
                    // que cuelgue manana. El carrito de quien si entro no esta aqui: vive
                    // bajo /users/me/cart y lo cubre la regla autenticada de mas abajo.
                    if (carritoExpuesto) {
                        rutas.requestMatchers(HttpMethod.GET, "/api/v1/carts").permitAll();
                    } else {
                        // Con la bandera apagada hace falta una regla igualmente, por lo
                        // mismo que en las otras tres: sin ninguna, la peticion cae en el
                        // denyAll del final y sale 403, que confirmaria que el carrito esta
                        // ahi, apagado. Con "authenticated" atraviesa la cadena, no encuentra
                        // manejador y sale el 404 que pide el criterio 27.
                        rutas.requestMatchers("/api/v1/carts/**").authenticated();
                    }

                    // La division politico-administrativa. HU-016, criterios 21 y 25.
                    //
                    // **Autenticada tanto si la bandera esta encendida como si no**, que es
                    // el unico caso del proyecto donde la regla no cambia con la bandera. El
                    // motivo es que las dos ramas quieren lo mismo: encendida, su unico
                    // consumidor es el formulario de direccion, que exige sesion; apagada, la
                    // regla tiene que existir igual para que la peticion atraviese la cadena
                    // y salga 404 en vez del 403 del denyAll final.
                    //
                    // Que la lista de municipios de Colombia no sea secreta no la hace
                    // publica: una ruta viva que nadie pide es superficie sin dueno. El dia
                    // que el cotizador de envios la necesite sin sesion, cambia aqui.
                    rutas.requestMatchers("/api/v1/locations/**").authenticated();

                    rutas
                            // Todo lo demas del catalogo lo hace el vendedor sobre lo suyo.
                            // Que sea suyo lo comprueba el repositorio, que solo devuelve la
                            // publicacion si es de quien pregunta.
                            .requestMatchers("/api/v1/listings/**")
                            .authenticated()
                            // Todo lo que actua sobre la propia cuenta exige token. Aqui
                            // entra tambien la verificacion de vendedor
                            // (/api/v1/users/me/verification), que es de quien la pide.
                            //
                            // **Las rutas del moderador no van a caber aqui.** Aprobar o
                            // rechazar la verificacion de OTRA persona, y ver su cedula,
                            // exige `hasRole("MODERATOR")` y no solo estar autenticado: iran
                            // en su propia regla, antes de esta, cuando existan. Con la
                            // regla generica y una ruta bajo /users/**, cualquier persona
                            // con token podria aprobarse a si misma.
                            .requestMatchers("/api/v1/users/**")
                            .authenticated()
                            // Sondas de estado. Que responda /actuator/flyway o no lo
                            // decide management.endpoints.web.exposure.include, que en
                            // prod solo deja health e info.
                            .requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/flyway")
                            .permitAll()
                            // Documentacion de la API. En prod springdoc esta apagado,
                            // asi que estas rutas ni existen.
                            .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                            .permitAll()
                            // Los archivos del almacen publico: fotos de perfil hoy, tomas
                            // de producto en Fase 2. Publicas por definicion, igual que
                            // cualquier imagen de un catalogo.
                            //
                            // **Solo existen con el almacen local.** En la nube las sirve
                            // Cloud Storage y esta ruta no responde nada (ADR-0018), que es
                            // lo que se quiere: el backend no es un servidor de archivos.
                            // Lo reservado —cedula y selfie— no se sirve por ninguna ruta,
                            // ni aqui ni alli (RN-046).
                            .requestMatchers("/archivos/**")
                            .permitAll()
                            .anyRequest()
                            .denyAll();
                })
                // El decodificador lo aporta infrastructure, que es quien tiene el
                // secreto de firma. Aqui se declara cual, y no se deja que lo resuelva el
                // tipo: en este contexto hay dos y el otro valida tokens de Google.
                .oauth2ResourceServer(recursos -> recursos.jwt(
                        jwt -> jwt.decoder(decodificadorDeSesion).jwtAuthenticationConverter(deJwtAAutoridades())))
                // Ni formulario de acceso ni autenticacion basica: esto es una API.
                .httpBasic(basica -> basica.disable())
                .formLogin(formulario -> formulario.disable());

        return http.build();
    }

    /**
     * Traduce el claim {@code roles} del token en autoridades de Spring Security.
     *
     * <p>Hay que declararlo porque el convertidor por omision lee {@code scope} y
     * {@code scp}, que son de OAuth2 y este sistema no emite: sin esto, todo token
     * llegaria sin ninguna autoridad y cualquier regla por rol quedaria muerta sin
     * dar error.
     *
     * <p>El prefijo {@code ROLE_} es el que espera {@code hasRole}. Los nombres en el
     * token van sin el, porque un token no tiene por que hablar el dialecto de un
     * framework concreto.
     */
    private static JwtAuthenticationConverter deJwtAAutoridades() {
        JwtGrantedAuthoritiesConverter autoridades = new JwtGrantedAuthoritiesConverter();
        autoridades.setAuthoritiesClaimName("roles");
        autoridades.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter convertidor = new JwtAuthenticationConverter();
        convertidor.setJwtGrantedAuthoritiesConverter(autoridades);
        return convertidor;
    }
}
