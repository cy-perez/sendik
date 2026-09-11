package co.sendik.shared.error;

/**
 * Catalogo de codigos de error de la API.
 *
 * <p>El backend nunca envia texto para mostrar al usuario final: envia uno de
 * estos codigos y el frontend lo traduce con Transloco. Agregar un codigo aqui
 * obliga a agregarlo a {@code frontend/src/i18n/*.json} en el mismo commit
 * (docs/arquitectura/contrato-api.md).
 *
 * <p>Los prefijos separan contextos: {@code AUTH_}, {@code USER_},
 * {@code SELLER_}, {@code CATALOG_}, {@code ORDER_}, {@code PAYMENT_},
 * {@code SHIPPING_}, {@code FILE_}, {@code COMMON_}.
 *
 * <p>{@code FILE_} no es un contexto de negocio sino de mecanismo: lo usan por igual
 * la foto de perfil, el documento de identidad y las tomas de producto, porque el
 * archivo se valida igual venga de donde venga (ADR-0018).
 *
 * <p>Falta a proposito un codigo para "el correo ya existe". El criterio 2 de
 * HU-001 exige que registrar un correo existente responda exactamente igual que
 * un registro nuevo: devolver un codigo distinto convertiria el formulario en un
 * detector de cuentas.
 */
public enum ErrorCode {

    /** RN-005: la contrasena no llega al minimo de caracteres. */
    AUTH_PASSWORD_TOO_SHORT,

    /** RN-005: la contrasena aparece en una filtracion conocida (ADR-0013). */
    AUTH_PASSWORD_BREACHED,

    /** RN-008: no ha cumplido 18 anos. */
    AUTH_UNDERAGE,

    /** Falta aceptar los terminos o la politica de tratamiento de datos. */
    AUTH_CONSENT_REQUIRED,

    /** El enlace no corresponde a ningun token, o ya se uso. */
    AUTH_VERIFICATION_TOKEN_INVALID,

    /** RN-003: el enlace caduco. */
    AUTH_VERIFICATION_TOKEN_EXPIRED,

    /** Se agotaron los reenvios permitidos dentro de la hora. */
    AUTH_RESEND_LIMIT_REACHED,

    /**
     * El correo o la contrasena no coinciden. Criterio 11 de HU-001: es el mismo
     * codigo para las dos causas, y tambien para un correo que no existe. Un
     * codigo distinto por caso convertiria el formulario de acceso en un
     * detector de cuentas.
     */
    AUTH_INVALID_CREDENTIALS,

    /**
     * RN-006: cinco intentos fallidos bloquean el acceso 15 minutos.
     *
     * <p>Solo se responde cuando la contrasena era correcta. Con contrasena
     * incorrecta se responde {@link #AUTH_INVALID_CREDENTIALS}, porque decirle
     * "esta bloqueada" a quien no sabe la clave le confirma que la cuenta existe.
     */
    AUTH_ACCOUNT_LOCKED,

    /**
     * RN-007: el token de refresco no sirve. Da igual si caduco, si se revoco o
     * si ya se habia usado: hacia afuera es el mismo codigo, y la reaccion del
     * cliente es la misma, volver a pedir las credenciales.
     */
    AUTH_SESSION_INVALID,

    /**
     * RN-007: el refresco llego con un token que otra peticion del mismo cliente acaba de
     * rotar. **No es que la sesion no valga**: la cookie que el navegador tiene ahora es
     * buena, y quien reciba esto debe reintentar la peticion original en vez de cerrar.
     *
     * <p>Existe porque el servidor ya sabe cual de los dos casos es -lo calcula para
     * decidir si revoca la familia- y hasta ADR-0030 tiraba ese dato justo antes de
     * responder. El cliente, con un solo codigo, no tenia con que distinguir una carrera
     * entre dos pestanas de una sesion muerta, y cerraba la sesion en las dos.
     *
     * <p>Dice que paso, no que hacer: reintentar es la reaccion, y la reaccion es del
     * cliente.
     */
    AUTH_SESSION_RACE,

    /**
     * El enlace de restablecimiento no sirve: no existe o ya se uso.
     *
     * <p>Codigo propio y no el de la verificacion de correo, aunque el mecanismo
     * sea el mismo. Lo que cambia es lo que hay que decirle a la persona: aquel
     * enlace dura 24 horas y este 30 minutos (criterio 18), y un mensaje que dijera
     * la duracion equivocada la mandaria a buscar un correo que ya no sirve.
     */
    AUTH_RESET_TOKEN_INVALID,

    /** El enlace de restablecimiento venció. Criterio 18: dura 30 minutos. */
    AUTH_RESET_TOKEN_EXPIRED,

    /**
     * Criterio 21: el correo al que se quiere cambiar ya tiene cuenta.
     *
     * <p>Solo se responde al <strong>confirmar</strong> el cambio, nunca al
     * pedirlo. Al pedirlo se responde igual este libre u ocupado, como en el
     * registro, para que el formulario no sirva de detector de cuentas. Al
     * confirmar ya no hay nada que revelar: quien abre el enlace demostro que ese
     * buzon es suyo.
     */
    AUTH_EMAIL_TAKEN,

    /**
     * Criterio 23: lo escrito para confirmar el cierre de cuenta no coincide.
     *
     * <p>Cerrar no se deshace, y la confirmación es lo único que separa un clic mal
     * dado de perder el acceso.
     */
    AUTH_CLOSE_CONFIRMATION_MISMATCH,

    /**
     * RN-101: la libreta ya tiene las diez direcciones que caben. HU-016, criterio 8.
     *
     * <p>422 y no 403, por lo mismo que {@link #CATALOG_CART_FULL}: la peticion es
     * legitima y quien la manda tiene derecho a hacerla; lo que pasa es que no cabe.
     *
     * <p><strong>Es el primer codigo {@code USER_} del proyecto.</strong> El prefijo
     * estaba declarado en el contrato desde el primer dia y hasta hoy no lo usaba nadie:
     * lo de la cuenta salia como {@code AUTH_} porque todo lo de la cuenta era
     * autenticarse. La direccion de entrega no lo es.
     */
    USER_ADDRESS_BOOK_FULL,

    /**
     * RN-100: ese municipio no esta en la division politico-administrativa vigente.
     *
     * <p>Uno solo para "no existe" y para "el DANE lo suprimio y la fila esta inactiva",
     * porque lo que hay que hacer es lo mismo: elegir otro de la lista. Distinguirlos
     * obligaria al formulario a explicar la Divipola.
     *
     * <p>Codigo propio y no el de validacion generica, por lo mismo que
     * {@link #SELLER_UNKNOWN_INSTITUTION} y {@link #CATALOG_UNKNOWN_CATEGORY}: lo que hay
     * que decirle es que elija de la lista, no que revise el formulario.
     */
    USER_UNKNOWN_MUNICIPALITY,

    /**
     * Lo que se subio no es una imagen de un tipo aceptado.
     *
     * <p>Se decide por los bytes de cabecera, no por la extension ni por el
     * {@code Content-Type}: los dos los pone quien sube (ADR-0018).
     */
    FILE_TYPE_UNSUPPORTED,

    /** El archivo pasa del tamano maximo. */
    FILE_TOO_LARGE,

    /**
     * La imagen es valida pero no llega al minimo de pixeles.
     *
     * <p>El minimo depende de para que sea: RN-019 fija 900x1200 para las tomas de
     * producto, y la foto de perfil tiene el suyo, mas bajo. El codigo es el mismo
     * porque lo que el cliente tiene que hacer es lo mismo: subir una mas grande.
     */
    FILE_DIMENSIONS_TOO_SMALL,

    /**
     * RN-059: la accion no corresponde al estado de la verificacion.
     *
     * <p>Uno solo para todas las transiciones prohibidas, y no uno por par de
     * estados. Lo que el cliente tiene que hacer es lo mismo en todos los casos
     * —recargar y mirar en que punto va— y un codigo por combinacion serian
     * dieciocho codigos que dicen lo mismo.
     */
    SELLER_VERIFICATION_INVALID_STATE,

    /** RN-014: se agotaron los tres intentos y el siguiente exige revision manual. */
    SELLER_VERIFICATION_ATTEMPTS_EXHAUSTED,

    /** RN-012: el titular de la cuenta bancaria no es el del documento. */
    SELLER_ACCOUNT_HOLDER_MISMATCH,

    /**
     * Criterio 1 de HU-002: hay que tener el correo verificado para empezar.
     *
     * <p>Codigo propio y no uno de {@code AUTH_}: no ha fallado ninguna credencial,
     * falta un paso que la persona ya tiene empezado y puede reenviar.
     */
    SELLER_EMAIL_NOT_VERIFIED,

    /** La entidad financiera no esta en el catalogo activo. */
    SELLER_UNKNOWN_INSTITUTION,

    /**
     * RN-010: ese documento ya esta verificado en otra cuenta.
     *
     * <p>Es lo contrario del silencio que guarda el registro con un correo repetido,
     * y a proposito: aqui quien escribe el numero es quien lo tiene en la mano, asi
     * que no se le esta confirmando nada que no sepa. Lo que no se dice nunca es de
     * quien es la otra cuenta.
     */
    SELLER_DOCUMENT_ALREADY_VERIFIED,

    /**
     * RN-060: un moderador no puede decidir sobre su propia solicitud.
     *
     * <p>403 y no 422: la peticion esta bien formada y el estado la admitiria; lo que
     * falla es quien la hace. Es de las pocas veces que este proyecto responde 403 a
     * alguien que si tiene el rol, y por eso el codigo es propio: un 403 generico le
     * diria "no eres moderador" a quien lo es, y a esa persona la dejaria buscando un
     * problema de permisos que no existe.
     */
    SELLER_SELF_REVIEW_FORBIDDEN,

    /**
     * RN-011: para publicar hay que estar verificado.
     *
     * <p>Cubre tambien al vendedor revocado de RN-013, y a proposito: lo que el
     * cliente tiene que hacer es lo mismo —completar o rehacer la verificacion— y un
     * codigo por cada motivo obligaria a la interfaz a distinguir dos caminos que
     * terminan en la misma pantalla.
     */
    CATALOG_SELLER_NOT_VERIFIED,

    /**
     * RN-061: la accion no corresponde al estado de la publicacion.
     *
     * <p>Uno solo para todas las transiciones prohibidas, por lo mismo que
     * {@link #SELLER_VERIFICATION_INVALID_STATE}: lo que hay que hacer es recargar y
     * mirar en que punto va, y un codigo por par de estados serian veintitantos
     * codigos que dicen lo mismo.
     */
    CATALOG_LISTING_INVALID_STATE,

    /**
     * RN-016 y RN-017: faltan tomas, o falta alguna de las cuatro canonicas.
     *
     * <p>Cuantas se exigen depende del producto: ocho en general, cuatro si es
     * tecnologia declarada sellada (RN-065). El codigo es el mismo porque lo que el
     * vendedor tiene que hacer es lo mismo: subir las que falten.
     */
    CATALOG_SHOTS_INCOMPLETE,

    /**
     * Criterio 6: faltan datos para enviar el borrador a revision.
     *
     * <p>Distinto de {@link #COMMON_VALIDATION_FAILED}: la peticion no esta mal
     * formada, y guardar el borrador asi es valido. Lo que no se puede es enviarlo.
     */
    CATALOG_LISTING_INCOMPLETE,

    /** El estado actual no admite editar la publicacion (criterio 19). */
    CATALOG_LISTING_NOT_EDITABLE,

    /** RN-064: esa categoria no admite lo usado. Toda la familia de tecnologia. */
    CATALOG_CONDITION_NOT_ALLOWED,

    /** RN-066: solo la tecnologia declarada sellada admite imagenes de referencia. */
    CATALOG_REFERENCE_IMAGE_NOT_ALLOWED,

    /** La categoria no existe en el arbol, o esta retirada. */
    CATALOG_UNKNOWN_CATEGORY,

    /**
     * RN-063: un moderador no decide sobre su propia publicacion.
     *
     * <p>Codigo propio y 403, por lo mismo que {@link #SELLER_SELF_REVIEW_FORBIDDEN}:
     * un 403 generico le diria "no eres moderador" a quien lo es, y lo dejaria
     * buscando un problema de permisos que no existe.
     */
    CATALOG_SELF_MODERATION_FORBIDDEN,

    /**
     * RN-072: nadie marca como favorita su propia publicacion.
     *
     * <p>Codigo propio y 403, por lo mismo que {@link #CATALOG_SELF_MODERATION_FORBIDDEN}:
     * un 403 generico le diria "no puedes hacer esto" a quien si puede hacerlo con
     * cualquier otra publicacion, y lo dejaria buscando un problema de sesion que no
     * tiene. Lo que hay que decirle es que esa publicacion es suya.
     *
     * <p>Es tambien el codigo que ve quien pulso el control sin sesion sobre lo suyo y
     * volvio del ingreso con la intencion pendiente (HU-011, criterio 10): pasar por el
     * ingreso no salta la regla, y el mensaje tiene que explicar por que no se guardo.
     */
    CATALOG_SELF_FAVORITE_FORBIDDEN,

    /**
     * RN-092: nadie agrega al carrito su propia publicacion.
     *
     * <p>Codigo propio y 403, por lo mismo que {@link #CATALOG_SELF_FAVORITE_FORBIDDEN},
     * y por una razon mas fuerte que aquel: comprarse a si mismo moveria dinero y comision
     * en circulo. Alli la regla evita una senal sin sentido; aqui evita una transaccion.
     */
    CATALOG_SELF_CART_FORBIDDEN,

    /**
     * RN-097: el carrito admite hasta veinte productos.
     *
     * <p>422 y no 403: la peticion es legitima y quien la manda tiene derecho a hacerla,
     * lo que pasa es que el carrito no da para mas. Es lo mismo que {@code 422} dice del
     * resto del catalogo —la peticion se entiende y no se puede cumplir— y lo contrario de
     * lo que diria un 403, que hablaria de permisos que aqui no faltan.
     *
     * <p>El mensaje tiene que nombrar el tope. Un rechazo que no dice cuantos caben deja a
     * quien lo recibe quitando productos a ciegas.
     */
    CATALOG_CART_FULL,

    /** La peticion no cumple el contrato. El detalle por campo va en {@code errors}. */
    COMMON_VALIDATION_FAILED,

    /**
     * Llegaron demasiadas peticiones desde el mismo origen.
     *
     * <p>No es una regla de negocio sino una defensa del borde, pero el codigo
     * vive aqui como los demas: el catalogo de codigos es uno solo y el frontend
     * lo traduce igual (docs/arquitectura/contrato-api.md).
     */
    COMMON_TOO_MANY_REQUESTS,

    /**
     * No existe lo que se pidio.
     *
     * <p>Hoy lo usa el borde para un archivo que no esta. No dice si nunca existio o
     * si se borro: las dos cosas son "no esta", y distinguirlas le contaria a
     * cualquiera que ahi hubo algo.
     */
    COMMON_NOT_FOUND,

    /**
     * Se pidio algo que existe y para lo que no se tiene permiso.
     *
     * <p>Lo usa el borde cuando la seguridad de metodo deniega. Sin este codigo, esa
     * denegacion caia en el manejador de {@code Exception} y salia como 500 con la traza
     * entera: le decia al cliente que el servidor esta roto, y un 500 confirma que la ruta
     * existe igual que un 403.
     *
     * <p>No confundir con las rutas apagadas por bandera, que responden 404: alli la
     * funcionalidad no esta, y decir "no tienes permiso" contaria que si.
     */
    COMMON_FORBIDDEN,

    /** Cualquier fallo no previsto. Nunca lleva detalle hacia afuera. */
    COMMON_UNEXPECTED
}
