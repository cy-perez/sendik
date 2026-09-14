# HU-017 — Dirección de origen del vendedor

**Fase:** 3 | **Estado:** pendiente
**Reglas que aplica:** RN-039, RN-078, RN-100, RN-102, RN-103, RN-046, RN-049

## Objetivo

Quien vende puede decir **desde dónde despacha** lo que vende, con el municipio elegido de la
división político-administrativa del DANE, para que el día que exista la cotización haya con
qué cotizar (RN-039) y el día que exista la guía haya un remitente con dirección (RN-078). Hoy
ese dato no existe en ninguna tabla del proyecto: el perfil tiene `city`, texto libre y
opcional, y con «bogota» no hay tarifa.

## Alcance

Es la pareja de HU-016. La libreta guarda **a dónde** llega lo que alguien compra; esta
historia guarda **desde dónde** sale lo que alguien vende. Se parte de la línea de Skydropx
del alcance con el mismo criterio que partió el carrito y la libreta: producir el dato que
las integraciones consumen no necesita a ninguna de las dos.

Entra:

- **Una sola dirección de origen por cuenta.** RN-039 habla de «la ciudad de origen del
  vendedor», en singular: una persona despacha desde un sitio. Guardar otra reemplaza la que
  había; nunca hay dos.
- La página `/mi-direccion-de-origen`: ver la que hay, guardarla o reemplazarla, y borrarla.
- La dirección completa: departamento y municipio elegidos de la lista cerrada, la línea de
  dirección, un complemento opcional, indicaciones para la recogida opcionales y código
  postal opcional.
- **El remitente es el titular de la cuenta**, con el nombre y el teléfono de su perfil. No se
  escriben aquí: se muestran de solo lectura y se editan donde siempre, en `/mi-cuenta`. Para
  guardar el origen el perfil tiene que tener teléfono.
- **El municipio del origen reemplaza a la ciudad del perfil.** Mientras no haya origen, la
  ciudad escrita a mano se conserva y se sigue editando; al guardar el origen, la ciudad del
  perfil pasa a ser su municipio y el texto libre se descarta. Un solo dato y no dos verdades.
- Los endpoints, detrás de `FEATURE_CHECKOUT`, la misma bandera de la libreta y del carrito, y
  reutilizando las dos rutas de la división del DANE que HU-016 dejó bajo ella.
- El borrado al cerrar la cuenta, en la misma transacción que anonimiza, y la inclusión en la
  descarga de datos personales.
- El atajo desde `/mi-cuenta`, solo con la bandera encendida (ADR-0041).

No entra:

- **Cotizar, comprobar cobertura de recogida ni emitir la guía.** Esperan a Skydropx. Guardar
  un origen en Mitú no promete que una transportadora pase a recoger allí, y ninguna pantalla
  lo insinúa: es RN-103 vista desde el lado del vendedor.
- **Exigir el origen para algo.** No se pide al verificarse, no se pide al publicar y no bloquea
  nada. La exigencia llega con la cotización: sin origen no hay tarifa y RN-040 ya dice que sin
  cotización no hay compra. Esta historia solo produce el dato.
- **Varias direcciones de origen ni una por publicación.** Una sola. Si algún día un vendedor
  despacha desde dos sitios, es otra historia y trae reglas que hoy no existen.
- **Mostrar el municipio de origen en el perfil público o en la ficha.** Es tentador —«de dónde
  sale el producto»— y la clasificación de `datos-personales.md` dice que la ciudad es pública,
  pero **hoy nada la muestra**: `SellerProfileResponse` no la lleva. Publicarla es una
  decisión aparte y queda anotada en «Cuándo revisar».
- **Nombre y teléfono propios de la dirección**, como tiene la de entrega. Aquí no hay
  tercero: el remitente es quien vende (RN-078) y sus datos ya están en la cuenta.
- **Migrar las ciudades escritas a mano** cruzándolas con la lista del DANE. Se conservan
  tal cual hasta que cada quien guarde su origen.
- **Restringirlo a quien tiene el sello.** En el código el rol `SELLER` lo otorga aprobar la
  verificación y lo quita revocarla, así que «vendedor sin sello» no existe como rol. Se
  decidió que el origen se pueda dejar listo antes del sello, y la única forma de cumplirlo es
  la misma de la libreta: cualquier cuenta con sesión puede guardarlo. Una cuenta que nunca
  vende puede tener origen igual que una que nunca compra puede tener libreta.
- **La dirección sin sesión** y **fuera de Colombia**, por lo mismo que en HU-016.

## Criterios de aceptación

### Guardar y reemplazar

1. Dado alguien con la sesión abierta y sin dirección de origen, cuando abre
   `/mi-direccion-de-origen`, entonces ve el estado vacío, que explica para qué servirá el
   origen —cotizar el envío y ser el remitente de la guía— sin afirmar que ya sirva para
   nada, y ofrece agregarlo.
2. Dado el formulario con los campos obligatorios —departamento, municipio y línea de
   dirección—, cuando guarda, entonces el origen queda guardado y **sigue ahí después de
   recargar la página**.
3. Dado una cuenta que ya tiene origen, cuando guarda otro, entonces **reemplaza al anterior y
   no queda una segunda fila**: lo garantiza la base de datos, no el orden de dos escrituras.
4. Dado el formulario, entonces el nombre y el teléfono del remitente aparecen **tomados del
   perfil, de solo lectura**, con un enlace a `/mi-cuenta` para cambiarlos. No hay campo para
   escribirlos aquí.
5. Dado un perfil **sin teléfono**, cuando intenta guardar, entonces el servidor rechaza con
   `USER_PHONE_REQUIRED` y la pantalla lo dice **antes de que pulse guardar**, con el enlace al
   perfil. Un remitente sin teléfono no es remitente.
6. Dado el formulario, cuando elige departamento, entonces el selector de municipio ofrece
   **solo los municipios de ese departamento**; mientras no haya departamento elegido, el de
   municipio está deshabilitado y dice por qué (igual que HU-016, criterio 4).
7. Dado un municipio que no pertenece al departamento enviado, o un código que no existe en la
   división, cuando se manda la petición, entonces el servidor la rechaza con
   `USER_UNKNOWN_MUNICIPALITY`. No basta con que el formulario lo impida (RN-100).
8. Dado que falta un campo obligatorio o se pasa de su longitud, cuando guarda, entonces
   responde `COMMON_VALIDATION_FAILED` con **una entrada por campo**, y la pantalla marca cada
   campo y lleva el foco al primero.

### Borrar

9. Dado un origen guardado, cuando lo borra, entonces desaparece, vuelve el estado vacío, y la
   petición repetida —un reintento, dos pestañas— responde lo mismo sin fallar por repetirse.

### La ciudad del perfil

10. Dado una cuenta con ciudad escrita a mano y **sin origen**, entonces el perfil sigue
    mostrando lo escrito y el campo se sigue editando en `/mi-cuenta`, como hasta hoy.
11. Dado que guarda el origen, entonces la ciudad del perfil pasa a ser **el municipio del
    origen con su departamento al lado**, el texto libre se descarta, y en `/mi-cuenta` el campo
    deja de editarse y remite a `/mi-direccion-de-origen`.
12. Dado que borra el origen, entonces la ciudad del perfil queda **vacía** —el texto anterior
    se descartó al guardar y no vuelve— y el campo se puede volver a escribir a mano.
13. Dado que el municipio guardado se marca inactivo o el DANE lo renombra, entonces el perfil
    lee el nombre actual de la fila y **no se queda en blanco**; al editar el origen se pide
    elegir de nuevo (HU-016, criterio 23).

### De quién es y quién lo ve

14. Dado alguien **sin sesión**, cuando pide cualquiera de las rutas del origen, entonces
    responde 401; y `/mi-direccion-de-origen` se comporta como `/mi-cuenta` y lleva a ingresar.
15. Dado alguien que cerró su cuenta y todavía tiene un token de acceso vivo, cuando escribe el
    origen, entonces el caso de uso recarga la cuenta y responde 401.
16. Dado cualquier respuesta pública del proyecto —el perfil público del vendedor, la ficha, el
    catálogo, el carrito—, entonces **en ninguna aparece la línea de dirección, el complemento,
    las indicaciones ni el código postal del origen**, y tampoco el teléfono del vendedor
    (RN-046, `datos-personales.md`). En esta historia tampoco aparece el municipio.
17. Dado cualquier nivel de registro, incluido `debug` y el mensaje de una excepción, entonces
    **ningún registro contiene la línea, el complemento ni las indicaciones**.
18. Dado la descarga de datos personales de HU-001, cuando la pide alguien con origen, entonces
    el origen viene en el archivo, con su municipio y su departamento por nombre (RN-049).

### El cierre de la cuenta

19. Dado alguien con origen guardado, cuando cierra su cuenta, entonces **se borra en la misma
    transacción que anonimiza la fila**, junto a las direcciones de entrega, los favoritos y el
    carrito (RN-102). Es lo que sostiene la ventana de quince minutos del token, y una fila que
    sobreviviera la volvería falsa.

### Las transversales

20. Dado que `FEATURE_CHECKOUT` está apagada, cuando se piden las rutas del origen, entonces
    responden **404 con `COMMON_NOT_FOUND`**, byte por byte igual al de un controlador que no
    existe, y `/mi-direccion-de-origen` muestra su estado de error. El frontend no conoce
    ninguna bandera.
21. Dado `FEATURE_CHECKOUT` encendida, entonces `/mi-cuenta` ofrece el atajo al origen junto a
    los de la libreta y el carrito; apagada, no lo enlaza (ADR-0041).
22. Ningún texto de esta historia vive en una plantilla: todo por clave de Transloco, en
    español y en inglés. Los nombres de departamentos y municipios no se traducen.
23. El formulario se recorre con el teclado, cada campo tiene etiqueta asociada, cada error se
    anuncia junto a su campo y no solo por color, y el selector de municipio anuncia que cambió
    cuando cambia el departamento.
24. En ninguna pantalla de esta historia se afirma que desde ese municipio se pueda recoger ni
    enviar. La cobertura sigue sin comprobar (RN-080).

## Casos borde

- **Dos pestañas guardando orígenes distintos a la vez.** Gana la última y sigue habiendo uno.
  Lo garantiza la clave primaria sobre la persona, no la aplicación.
- **Borrar el origen en una pestaña mientras otra lo edita.** La segunda guarda igual: es un
  `PUT` que crea o reemplaza, así que no hay 404 que manejar. Es la diferencia con la libreta,
  donde cada dirección tiene identificador.
- **Quitar el teléfono del perfil después de haber guardado el origen.** Hoy no se impide: el
  origen queda sin remitente completo y nada lo reclama todavía. El día que exista la guía,
  ese es el caso que hay que cerrar, y queda escrito aquí para no descubrirlo entonces.
- **Bogotá D.C. es departamento y municipio a la vez.** Es la ciudad de la mayor parte de los
  vendedores. El modelo lo admite sin un `if` con nombre propio, como en HU-016.
- **La línea de dirección colombiana lleva `#` y `-`**, «Cra», «Dg», «Mz». No se sanea hasta
  romperla ni se valida contra un patrón rígido: se limita la longitud y se escapa al pintar.
- **Indicaciones para la recogida con media historia dentro.** «Local 3 del centro comercial,
  entrar por el parqueadero, preguntar por Nubia». Tope de longitud, y es dato personal —a
  veces de un tercero, la Nubia de la frase—, así que no aparece en ningún registro.
- **La sesión expiró al guardar.** Se renueva con la cookie de refresco y se reintenta una vez;
  si tampoco, se dice y **el formulario conserva lo escrito**.
- **Una cuenta con verificación en curso.** Puede guardar el origen; no se le pide en la
  verificación ni se le insiste. Al aprobarla, el origen ya está.
- **Una cuenta a la que le revocan el sello.** El origen se queda: es de la persona, no del
  sello, y lo necesitará si vuelve a vender. Borrarlo lo decide el titular.

## Diseño

- **El acento bronce no aparece en esta pantalla.** No hay insignia de vendedor verificado en un
  formulario de dirección. Los botones van en tinta, incluido el principal.
- **Es una pantalla de un solo elemento**, no una lista: la tarjeta del origen si lo hay, con la
  dirección y el municipio con su departamento, el remitente tomado del perfil debajo, y dos
  acciones —editar y borrar—. Reutiliza la tarjeta y el formulario de HU-016; no estrena
  componente.
- **El remitente se muestra como bloque de solo lectura** con el enlace «cambiar en mi cuenta».
  Si falta el teléfono, ese bloque es donde se dice, con el mismo enlace, y el botón de guardar
  queda deshabilitado con motivo anunciado.
- **El formulario es de una sola columna en móvil** y la línea de dirección se escribe entera.
- **El borrado no estrena diálogo de confirmación**, como en HU-016, y no va junto al botón
  principal.
- **El estado vacío es una pantalla**, y su texto habla en futuro condicional de lo que el
  origen servirá, nunca de lo que ya hace.
- **En `/mi-cuenta` la ciudad, cuando viene del origen, se muestra como texto** con el enlace a
  cambiarla en `/mi-direccion-de-origen`, y no como campo deshabilitado: un campo deshabilitado
  con un dato dentro se lee como algo roto.

## Notas técnicas

- **Vive en `identity`, al lado de la libreta, y no necesita otra ADR para eso.** ADR-0039 ya
  decidió que una dirección es de la persona y que `shipping` nacerá con Skydropx. Lo que sí
  puede merecer una decisión escrita, y lo decide el plan, es que **la ciudad del perfil deje de
  ser texto libre** cuando hay origen: cambia el significado de `City`, cuyo Javadoc defiende
  hoy el texto libre con argumentos que esta historia contradice.
- **Endpoints nuevos**, bajo `FEATURE_CHECKOUT` y colgando de `/users/me`. **Es un recurso
  singular de la persona**, como `default-address`, y por eso no hay identificador en la ruta y
  no existe forma de pedir el de otra cuenta:
  - `GET /api/v1/users/me/origin-address` — `200` con el origen, o **`204` sin cuerpo** cuando no
    hay. No `404`: la bandera apagada ya responde `404` con `COMMON_NOT_FOUND`, y la pantalla
    tiene que distinguir «no tengo origen» de «esto no está disponible». Si el contrato
    prefiere `200` con cuerpo vacío, se cambia en el plan; lo que no se negocia es que no sea
    `404`.
  - `PUT /api/v1/users/me/origin-address` — crea o reemplaza, `200` con el guardado. `PUT` y no
    `POST`, porque reemplazar el valor de un recurso singular es exactamente lo que `PUT`
    significa, y es idempotente.
  - `DELETE /api/v1/users/me/origin-address` — `204`, también si no había.
  - Reutiliza `GET /api/v1/locations/departments` y `.../{code}/municipalities`, que ya están
    bajo la misma bandera.
- **Códigos de error.** `USER_UNKNOWN_MUNICIPALITY` ya existe y se reutiliza. Nuevo:
  `USER_PHONE_REQUIRED` (`422`): la petición está bien formada y quien la manda tiene derecho;
  lo que falta es un dato en otro sitio. Los dos con prefijo `USER_`, que es el del contexto.
- **Una migración, `V22__origin_addresses.sql`**, después de `V21__shipping_addresses.sql`:
  `user_id` como **clave primaria** y foránea a `users` —eso es el criterio 3—, `municipality_code`
  foránea a `municipalities` con `ON DELETE RESTRICT` (criterio 13), `details_cipher` y
  `details_key_version` con la línea, el complemento, las indicaciones y el código postal en
  un solo documento cifrado como en V21, y las dos fechas. Sin `is_default`: no hay entre qué
  elegir. Sin `ON DELETE CASCADE` desde `users`, como en V16, V19 y V21: quien borra es el
  cierre de cuenta, explícitamente.
- **`users.city` no se toca en el esquema.** Al guardar el origen el caso de uso la pone a
  `NULL`; al leer el perfil, si hay origen, la ciudad se compone desde el municipio y su
  departamento por nombre, uniendo, y no se copia: un municipio renombrado por el DANE
  devuelve el nombre nuevo (criterio 13). `ProfileResponse.city` conserva su forma; lo que
  cambia es de dónde sale, y hace falta que la respuesta diga **si la ciudad es editable** para
  que `/mi-cuenta` sepa qué pintar (criterios 10 a 12).
- **Puerto `OriginAddressRepository` en `identity/port/out`**, al lado de
  `ShippingAddressRepository`. `CloseAccountUseCase` lo llama en la misma transacción y
  `ExportUserDataUseCase` lo lee. Es el patrón que ya existe y no se estrena nada.
- **La comprobación del teléfono es del caso de uso**, no del borde: el borde no sabe qué hay
  en el perfil. Se recarga la cuenta —que es lo que ya hace el criterio 15— y se mira ahí.
- **Claves de Transloco nuevas**: título de la página, estado vacío, etiquetas y ayudas de los
  cinco campos, el bloque del remitente y su enlace, el aviso del teléfono que falta, los dos
  botones, el texto de la ciudad derivada en `/mi-cuenta`, el atajo, y el código de error nuevo.
  En `es.json` y en `en.json`.
- **`/mi-direccion-de-origen` va en `app.routes.server.ts`**, que es lo que `rutas.spec.ts` caza.
- **El atajo en `/mi-cuenta`** entra en la lista que ADR-0041 ya condiciona a
  `banderas.checkout`, con clave propia.

## Pruebas requeridas

- **De dominio**: que un origen sin municipio o sin línea no es representable; que la línea,
  el complemento y las indicaciones respetan su tope; y que `City` derivada de un municipio se
  lee con su departamento al lado.
- **De aplicación**: que guardar sin teléfono en el perfil se rechaza; que guardar dos veces
  deja uno; que guardar descarta la ciudad escrita a mano; que borrar deja la ciudad vacía y
  editable; que un municipio que no pertenece al departamento se rechaza; y que el cierre de
  cuenta y la descarga de datos lo incluyen.
- **De seguridad**: que sin sesión es 401; que una cuenta cerrada con token vivo no puede
  escribir; y un guardián al estilo de `FavoritesPersonalDataTest` que compruebe que el cierre
  se lleva el origen.
- **De filtración**: que ninguna respuesta pública contiene la línea, el complemento, las
  indicaciones ni el teléfono del vendedor, y que ningún registro los escribe en ningún nivel.
  El criterio 17 se prueba literal, como el 19 de HU-016.
- **De persistencia**: que la clave primaria sobre la persona hace de dos escrituras
  concurrentes un solo origen, y que un municipio inactivo sigue dejando leer el origen que lo
  apunta.
- **De componente**: que el selector de municipio se puebla al elegir departamento; que sin
  teléfono el botón está deshabilitado y el aviso enlaza al perfil; que un fallo de guardado
  conserva lo escrito; que el foco va al primer campo con error; y que en `/mi-cuenta` la
  ciudad derivada se pinta como texto con enlace y no como campo.
- **De la bandera**: que con `FEATURE_CHECKOUT` apagada las tres rutas responden 404 con
  `COMMON_NOT_FOUND`, byte por byte igual al de un controlador que no existe.
- **Extremo a extremo en `e2e-completo/`**: entrar con un perfil sin teléfono, ver el aviso,
  poner el teléfono en `/mi-cuenta`, guardar el origen, comprobar que la ciudad del perfil
  cambió al municipio, reemplazarlo por otro municipio, borrarlo, comprobar que la ciudad quedó
  vacía, cerrar la cuenta y comprobar que no queda fila.

## Lo que habría que agregar, y no se agrega aquí

**Reglas de negocio** (`reglas-negocio.md`, después de RN-104):

- Una cuenta tiene **como mucho una dirección de origen**; guardar otra reemplaza la anterior.
- **El remitente es el titular de la cuenta** con el nombre y el teléfono de su perfil, y por
  eso el origen exige teléfono. Desarrolla RN-078 hacia dentro.
- **La ciudad pública del perfil es el municipio del origen cuando existe**; mientras no, es lo
  que la persona escribió. Cambia lo que `City` defiende hoy.
- **El origen no se exige todavía**: la exigencia llega con la cotización (RN-039, RN-040).
  Puede ir como nota en RN-039 y no como regla nueva.
- **Guardar el origen no comprueba cobertura de recogida**, y ninguna pantalla lo insinúa.
  Extiende RN-103 al otro extremo del envío.
- **El cierre de cuenta borra el origen**, en la misma transacción. Extiende RN-102.
- **Cualquier cuenta puede guardar origen**, tenga o no el sello, porque el rol `SELLER` es el
  sello y el origen se quiere listo antes.

**Glosario**:

- **Dirección de origen** — `OriginAddress`: desde dónde despacha quien vende. Una por cuenta.
  Siempre con apellido, por lo mismo que la de entrega.
- **Remitente** — la persona que figura como tal en la guía: el titular de la cuenta, con su
  nombre y su teléfono (RN-078). Hoy aparece en las reglas sin entrada.
- **Ciudad del perfil** — aclarar que, con origen, es el municipio del origen.

**Modelo de datos** (`modelo-datos.md`): la tabla `origin_addresses` y el nuevo significado de
`users.city`. Y anotar que `orders` copiará también el origen, por lo mismo que copiará la
dirección de entrega.

**Contrato de API** (`contrato-api.md`): las tres rutas del origen y `USER_PHONE_REQUIRED`.

**Datos personales** (`datos-personales.md`): clasificar la dirección de origen —Interno, cifrada
en reposo como la de entrega—, su retención y su borrado al cierre. Y **resolver la
discrepancia** que esta historia destapó: la tabla dice que la ciudad es pública y «aparece
junto a las publicaciones», y hoy no aparece en ninguna respuesta pública.

**Posible ADR**, a criterio del plan: que la ciudad del perfil deje de ser texto libre cuando
hay origen, porque revierte un argumento escrito en `City`.

## Cuándo revisar

- **Cuando Skydropx conteste** y se sepa qué campos exige del remitente. Si pide algo que el
  perfil no tiene —documento, correo distinto—, el bloque de remitente crece aquí.
- **Si un vendedor real despacha desde más de un sitio.** Es la señal de que «una sola» se
  quedó corta, y entonces se parece a la libreta con predeterminada.
- **Cuando se decida mostrar el municipio en el perfil público o en la ficha.** El dato ya
  estará; lo que falta es la decisión, y con ella cerrar la discrepancia de `datos-personales.md`.
- **Cuando exista la guía**, hay que cerrar el caso del teléfono quitado después de guardar el
  origen: o se impide quitarlo mientras haya origen, o se reclama al emitir.
