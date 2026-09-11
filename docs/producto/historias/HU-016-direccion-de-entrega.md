# HU-016 — La dirección de entrega

**Fase:** 3 | **Estado:** hecha el 11 de septiembre de 2026
**Reglas que aplica:** RN-039, RN-046, RN-048, RN-049, RN-080, y siete reglas nuevas que
esta historia obliga a escribir (RN-098 a RN-104, al final).

> **Es la mitad de «el proceso de compra» que no espera a nadie**, y el corte es el mismo
> que partió el carrito en HU-015. La línea del alcance dice «crear el pedido, la dirección
> de entrega y el paso a pagar», y las tres parecen una sola cosa. No lo son: el pedido
> necesita el pago, el pago necesita a Wompi y el paso a pagar necesita el costo del envío,
> que necesita a Skydropx. **Guardar a dónde quiere alguien que le llegue lo que compre no
> necesita a ninguno de los dos.**
>
> Y no es una interpretación forzada del alcance para adelantar trabajo: lo dice ya
> `docs/operacion/datos-personales.md`, en «La direccion del comprador y el envio», escrito
> el 8 de septiembre al decidirse ADR-0034. **«Cotizar no necesita la direccion completa.
> Basta la ciudad o el codigo postal de destino. La direccion exacta se manda solo al emitir
> la guia, que es cuando el pago ya esta aprobado.»** Guardar la dirección y usarla son dos
> momentos distintos, y solo el segundo depende de un tercero.
>
> Hay además una razón de orden: de todo lo que queda de la Fase 3, esta es la única pieza
> que **produce el dato que las demás necesitan**. RN-039 cotiza con «la dirección de destino
> del comprador» y hoy ese dato no existe en ninguna tabla del proyecto. El día que
> Skydropx conteste, o hay dirección guardada o hay que construir esto antes de poder
> cotizar una sola vez.

## Objetivo

Quien compra guarda a dónde quiere que le llegue lo que compra —y a nombre de quién, y con
qué teléfono— una sola vez y no en cada compra, y puede tener más de una porque no todo va
siempre al mismo sitio.

## Alcance

Entra:

- La página `/mis-direcciones`: ver la lista, agregar, editar, borrar y elegir cuál es la
  predeterminada.
- La **dirección de entrega** completa: nombre de quien recibe, teléfono, departamento y
  municipio elegidos de una lista cerrada, la línea de dirección, un complemento opcional,
  indicaciones de entrega opcionales y código postal opcional.
- La **división político-administrativa de Colombia** —departamentos y municipios del DANE—
  como dato de referencia sembrado, y las dos rutas de lectura que alimentan el formulario.
- Los endpoints de la libreta, detrás de `FEATURE_CHECKOUT`, la misma bandera del carrito.
- El **borrado al cerrar la cuenta**, en la misma transacción que anonimiza, junto a los
  favoritos y el carrito.

No entra:

- **Cotizar el envío.** Es RN-039 y necesita al agregador, el peso y las dimensiones. Esta
  historia produce el dato de destino y no lo usa para nada.
- **Comprobar la cobertura.** RN-080 está sin comprobar y lo dice: nadie ha contrastado la
  cobertura nacional que el texto legal anuncia contra las transportadoras que Skydropx
  habilite. Guardar una dirección en Mitú no promete que se pueda entregar allí, y esta
  historia no lo insinúa en ninguna pantalla.
- **Elegir la dirección al comprar, crear el pedido y el paso a pagar.** Es la otra mitad de
  la línea, la que sí espera a las dos integraciones.
- **La dirección de origen del vendedor.** RN-039 también la necesita —«la ciudad de origen
  del vendedor»— y hoy el perfil solo tiene `city`, texto libre, opcional y **público**. Pero
  el origen completo existe para emitir la guía y hacer del vendedor el remitente (RN-078),
  y eso es Skydropx entero. Se anota como lo que falta para cotizar, y no se hace aquí.
- **Enseñarle la dirección a alguien más.** RN-048 dice que la dirección completa se revela
  al vendedor solo cuando el pago está aprobado, y aquí no hay pago ni pedido: la dirección
  no sale de la cuenta de su dueño. **Tampoco sale de Sendik**: en esta historia no hay
  encargado, ni transferencia, ni agregador.
- **Validar que la dirección exista.** Sin geocodificación, sin mapa, sin autocompletar, sin
  llamar a nadie. Lo único cerrado es el municipio.
- **La dirección sin sesión.** El carrito sí funciona sin entrar (RN-095) y esto no, y la
  diferencia no es de gusto: un carrito anónimo es una lista de identificadores públicos, y
  una dirección es el nombre, el teléfono y la puerta de una persona. Guardar eso en el
  navegador de quien no tiene cuenta es crear un dato personal sin titular al que responderle,
  y no hay nada que hacer con él hasta que exista un pedido, que exige cuenta.
- **Direcciones fuera de Colombia.** Lo anunciado es el territorio nacional.

## Criterios de aceptación

### Crear

1. Dado alguien con la sesión abierta y sin ninguna dirección, cuando abre `/mis-direcciones`,
   entonces ve el estado vacío, que explica para qué sirve la libreta y ofrece agregar la
   primera.
2. Dado el formulario con todos los campos obligatorios —nombre de quien recibe, teléfono,
   departamento, municipio y línea de dirección—, cuando guarda, entonces la dirección queda
   en la lista y **sigue ahí después de recargar la página**.
3. Dado que es **la primera** dirección de la cuenta, cuando se guarda, entonces queda
   **predeterminada sin que nadie lo pida** (RN-099). Una cuenta con direcciones nunca está
   sin predeterminada.
4. Dado el formulario, cuando elige departamento, entonces el selector de municipio ofrece
   **solo los municipios de ese departamento**; y mientras no haya departamento elegido, el
   de municipio está deshabilitado y dice por qué.
5. Dado un municipio que **no pertenece** al departamento enviado, cuando se manda la petición
   —dos pestañas, un cliente hecho a mano—, entonces el servidor la rechaza. No basta con que
   el formulario lo impida (RN-100).
6. Dado un código de municipio que no existe en la división político-administrativa, cuando se
   manda, entonces se rechaza igual.
7. Dado que falta un campo obligatorio o se pasa de su longitud, cuando guarda, entonces
   responde `COMMON_VALIDATION_FAILED` con **una entrada por campo**, y la pantalla marca cada
   campo y lleva el foco al primero, en vez de pintar un aviso general.
8. Dado que la cuenta ya tiene el tope de direcciones de RN-101, cuando intenta agregar una
   más, entonces se rechaza y se le dice cuál es el tope. No se descarta la más antigua en
   silencio.

### Editar, borrar y la predeterminada

9. Dado una dirección suya, cuando la edita y guarda, entonces cambia esa y **no se crea una
   segunda**.
10. Dado una dirección que no es la predeterminada, cuando la borra, entonces desaparece y el
    resto de la libreta queda intacta, incluida cuál es la predeterminada.
11. Dado que borra **la predeterminada** y quedan otras, entonces la predeterminada pasa a ser
    **la más reciente de las que quedan**, se le dice cuál quedó, y en ningún momento la cuenta
    tiene direcciones sin predeterminada (RN-099).
12. Dado que borra **la última** que le quedaba, entonces vuelve al estado vacío y no queda
    ninguna predeterminada.
13. Dado dos direcciones, cuando marca la segunda como predeterminada, entonces la primera deja
    de serlo en el mismo acto: **hay exactamente una, y lo garantiza la base de datos**, no el
    orden en que se ejecuten dos escrituras.
14. Dado que borra una dirección, cuando la petición se repite —un reintento, dos pestañas—,
    entonces responde lo mismo y no falla por repetirse.

### De quién es

15. Dado el identificador de una dirección **de otra persona**, cuando la pide, la edita, la
    borra o la marca como predeterminada, entonces responde **404 y nunca 403**, con el mismo
    `COMMON_NOT_FOUND` de una que no existe. Un 403 confirmaría que esa dirección existe y de
    quién es.
16. Dado alguien que cerró su cuenta y todavía tiene un token de acceso vivo —los quince
    minutos de ADR-0003—, cuando escribe en la libreta, entonces el caso de uso recarga la
    cuenta y responde 401. Es lo que la revisión de seguridad echó en falta en HU-011.
17. Dado alguien **sin sesión**, cuando pide cualquier ruta de la libreta, entonces responde
    401; y la pantalla `/mis-direcciones` se comporta como `/mi-cuenta` y lleva a ingresar.
18. Dado cualquier respuesta pública del proyecto —el perfil público del vendedor, la ficha de
    producto, el catálogo, el carrito—, entonces **en ninguna aparece una dirección de entrega
    ni el teléfono de quien recibe** (RN-048, RN-098).
19. Dado cualquier nivel de registro, incluido `debug` y el mensaje de una excepción, entonces
    **ningún registro contiene la línea de dirección, el teléfono ni el nombre de quien
    recibe** (`datos-personales.md`, reglas técnicas).

### El cierre de la cuenta

20. Dado alguien con direcciones guardadas, cuando cierra su cuenta, entonces **se borran en la
    misma transacción que anonimiza la fila**, junto a los favoritos y el carrito (RN-102).

    > No es una limpieza de cortesía. `datos-personales.md` sostiene que la ventana de quince
    > minutos del token es aceptable **porque el cierre anonimiza en la misma transacción, así
    > que cuando la ventana se abre ya no queda dato personal que ese token pueda alcanzar**.
    > Una dirección que sobreviviera al cierre volvería falsa esa frase, que es justo la que el
    > documento pone por escrito ante una autoridad.

### La división político-administrativa

21. Dado el formulario, cuando se piden los departamentos y los municipios de uno, entonces
    llegan del servidor: **el navegador no trae la lista escrita dentro**. Es dato de referencia
    del backend y el día que el DANE cree un municipio se cambia en un sitio.
22. Dado el dato sembrado, entonces **el número de departamentos y de municipios coincide con
    el de la fuente**, y la fuente, su versión y su fecha quedan escritas en la migración. No se
    afirma un número redondo en la documentación: se afirma que lo sembrado es lo que la fuente
    dice, y una prueba lo cuenta.
23. Dado que un municipio deja de existir o se renombra, entonces **no se borra la fila**: se
    marca inactiva. Una dirección guardada que apunte a ella sigue leyéndose, y solo al
    editarla se pide elegir de nuevo. Borrar el municipio rompería direcciones de gente real.
24. Dado que hay municipios homónimos en departamentos distintos —hay más de un «San Pedro» y
    más de una «Santa Rosa»—, entonces en toda pantalla el municipio se lee **con su
    departamento al lado**, nunca solo.

### Las transversales

25. Dado que `FEATURE_CHECKOUT` está apagada, cuando se piden los endpoints de la libreta y los
    de la división político-administrativa, entonces responden **404 con `COMMON_NOT_FOUND`**,
    igual que una ruta que no existe. No es 403 en ningún caso, y la ruta `/mis-direcciones`
    muestra su estado de error, que es la forma establecida desde HU-009: el frontend no conoce
    ninguna bandera.
26. Ningún texto de esta historia vive en una plantilla: todo por clave de Transloco, en español
    y en inglés. **Los nombres de departamentos y municipios no se traducen**: son nombres
    propios y van igual en los dos idiomas.
27. El formulario se recorre con el teclado, cada campo tiene etiqueta asociada, cada error se
    anuncia junto a su campo y no solo por color, y el selector de municipio anuncia que cambió
    cuando cambia el departamento.

## Casos borde

- **Dos pestañas marcando predeterminadas distintas a la vez.** Gana la última y sigue habiendo
  exactamente una. Lo garantiza el índice, no la aplicación.
- **Borrar la predeterminada en una pestaña mientras otra la está editando.** La segunda recibe
  404 y la pantalla lo dice sin dejar el formulario colgado.
- **Bogotá D.C. es departamento y municipio a la vez** en la división del DANE. No es un caso
  raro: es la ciudad de la mayor parte del mercado. El modelo tiene que admitirlo sin un `if`
  con nombre propio.
- **San Andrés, Providencia y Santa Catalina.** Existen en la lista y se guardan como cualquier
  otro. Que haya cobertura de transporte es RN-080 y no es de esta historia.
- **La línea de dirección colombiana lleva `#` y `-`** —«Calle 45 # 12-34 apto 802»— y a veces
  «Cra», «Dg», «Tv», «Mz», «Etapa». No se sanea hasta romperla ni se valida contra un patrón
  rígido: lo que se limita es la longitud y se escapa al pintar.
- **El teléfono no se valida contra un formato estricto.** Un celular colombiano son diez
  dígitos y un fijo lleva indicativo; entre los dos formatos, y con los espacios y guiones que
  la gente escribe, un patrón rígido rechaza números reales. Se limpian separadores, se exige
  que sean dígitos y se acota la longitud.
- **Indicaciones de entrega con media historia dentro.** Es el campo donde alguien escribe «la
  casa de la esquina, el timbre no sirve, preguntar por mi mamá». Tiene tope de longitud y es
  dato personal como el resto —a veces de un tercero—, así que no aparece en ningún registro.
- **La sesión expiró al guardar.** Se renueva con la cookie de refresco y se reintenta una vez;
  si tampoco, se dice y **el formulario conserva lo escrito**. Perder una dirección recién
  tecleada por una sesión vencida es el peor momento para pedirla otra vez.
- **El municipio guardado ya no está activo.** La dirección se lee y se muestra igual, con su
  nombre guardado; al editarla se pide elegir de nuevo (criterio 23).
- **Una cuenta que nunca compra.** Puede tener libreta igual. No se le pide dirección en ningún
  otro sitio ni se le insiste.

## Diseño

- **El acento bronce no aparece en esta pantalla.** No hay insignia de vendedor verificado en
  una libreta de direcciones, y el acento va una vez por pantalla y siempre en lo mismo. Los
  botones van en tinta, incluido el principal.
- **La predeterminada se distingue con una etiqueta de texto**, no con un color ni con un icono
  solo. Un lector de pantalla la anuncia como parte del nombre de la tarjeta.
- **Cada dirección es una tarjeta** con el nombre de quien recibe arriba, la dirección y el
  municipio con su departamento debajo, y las tres acciones. Reutiliza la caja y el borde que
  ya usan las tarjetas del proyecto; no estrena componente.
- **El formulario es de una sola columna en móvil** y no parte la línea de dirección en dos
  campos: se escribe entera, como se dice.
- **El borrado no estrena un diálogo de confirmación.** El proyecto no tiene uno, y lo que se
  pierde al borrar por error se vuelve a escribir. Lo que sí se hace es no poner el borrado
  junto al botón principal.
- **El estado vacío es una pantalla**, no una línea: es lo primero que ve todo el mundo la
  primera vez.
- **En ninguna parte se promete que se pueda entregar allí.** Ni «cobertura disponible», ni un
  verde de confirmación: RN-080 está sin comprobar y la pantalla no puede afirmar lo que nadie
  ha contrastado.

## Notas técnicas

- **Dónde vive es una decisión del plan, y hay que tomarla antes de escribir código.** La
  inclinación es `identity`: una dirección es de la persona, como el teléfono o el nombre, y el
  envío la usará igual que usará el nombre. `shipping` nacerá cuando exista Skydropx y no tiene
  sentido estrenar un contexto vacío para guardar un texto. La consecuencia es de contrato: si
  vive en `identity`, sus códigos de error llevan el prefijo `USER_`, que ya existe; el prefijo
  `SHIPPING_` queda para el envío de verdad. **Merece ADR**, por lo mismo que el carrito
  necesitó ADR-0036.
- **La división político-administrativa no es de nadie**, y ahí está el segundo lado de la misma
  decisión: la usan las direcciones hoy y el cotizador mañana. Va como dato de referencia
  compartido, no colgada de `identity`.
- **Endpoints nuevos**, todos bajo `FEATURE_CHECKOUT` y colgando de `/users/me` por lo mismo que
  los favoritos y el carrito —es de la persona, y allí la regla de seguridad ya es
  «autenticado»—:
  - `GET /api/v1/users/me/addresses` — la libreta entera. **Sin paginación**: con el tope de
    RN-101 no hace falta.
  - `POST /api/v1/users/me/addresses` — `201` con la creada.
  - `PUT /api/v1/users/me/addresses/{id}` — `200` con la actualizada.
  - `DELETE /api/v1/users/me/addresses/{id}` — `204`, también si no estaba.
  - `PUT /api/v1/users/me/default-address` — cuál es la predeterminada, con el identificador en
    el cuerpo. **Es un recurso singular de la persona y no un verbo en la ruta**: el contrato no
    admite verbos, y `/addresses/{id}/default` chocaría además con `{id}`. Marcar es reemplazar
    el valor de ese recurso, que es exactamente lo que `PUT` significa.
  - `GET /api/v1/locations/departments` y
    `GET /api/v1/locations/departments/{code}/municipalities` — dos peticiones y no una: los
    departamentos son decenas y los municipios más de mil, y mandar el árbol entero para pintar
    el primer selector es mandar cuarenta veces lo que se necesita. Son cacheables y cambian una
    vez cada varios años.
- **Las dos rutas de referencia también van detrás de la bandera.** No porque revelen nada —una
  lista de municipios de Colombia no dice qué está construyendo Sendik— sino porque hoy su único
  consumidor es este formulario: con la bandera apagada no hay quien las pida, y una ruta viva
  que nadie usa es superficie sin dueño. El día que el cotizador las necesite, dejan la bandera.
- **Dos migraciones y no una.** La última aplicada es `V19__cart_items.sql`:
  - `V20__divipola.sql` — `departments` y `municipalities`, con el código del DANE como clave,
    el nombre, y una marca de activo. Es dato sembrado, y separarla deja que resembrar el día
    que el DANE cambie algo sea una migración propia y legible.
  - `V21__shipping_addresses.sql` — la tabla de direcciones, con el identificador de la persona,
    el municipio como clave foránea **con `ON DELETE RESTRICT`** (criterio 23), los campos de la
    dirección, la marca de predeterminada y las fechas. Índice por persona, y **un índice único
    parcial `(user_id) WHERE is_default`**, que es lo que hace del criterio 13 una garantía de
    la base y no una promesa de la aplicación.
- **La clave foránea al municipio es lo que hace innecesario validar el par**
  departamento-municipio a mano: el municipio ya sabe de qué departamento es, y el criterio 5 se
  cumple comparando contra lo que la fila dice, no contra lo que el cliente mandó.
- **El puerto `UserAddresses` en `identity/port/out`**, al lado de `UserFavorites` y `UserCart`,
  y `CloseAccountUseCase` lo llama en la misma transacción. Es el patrón que ya existe y no se
  estrena nada.
- **La dirección guarda además el nombre del municipio y del departamento** tal como estaban al
  guardarla. No es duplicación por comodidad: es lo que permite leer una dirección cuyo
  municipio se marcó inactivo sin que la pantalla se quede en blanco. La verdad para editar es
  el código; la copia es para leer.
- **Claves de Transloco nuevas**: el título de la página, el estado vacío, las etiquetas y ayudas
  de los siete campos, los tres botones, la etiqueta de predeterminada, el aviso del criterio 11,
  el del tope del criterio 8, el del selector deshabilitado del criterio 4 y los códigos de error
  nuevos. En `es.json` y en `en.json`.
- **`/mis-direcciones` va en `app.routes.server.ts`.** Es lo que se olvidó en `/mis-favoritos` y
  lo cazó `rutas.spec.ts`.
- **No hay enlace de navegación todavía**, igual que `/carrito` y `/mis-favoritos`:
  `FEATURE_CHECKOUT` está apagada y HU-004 y HU-005 prohíben enlazar a algo que no funciona. El
  enlace entra cuando la bandera se encienda, y su sitio natural es `/mi-cuenta`.

## Pruebas requeridas

- **De dominio**: que una dirección sin municipio, sin quien reciba o sin teléfono no es
  representable; que la línea de dirección y las indicaciones respetan su tope; que el teléfono
  se normaliza quitando separadores; y que una libreta tiene exactamente una predeterminada
  mientras tenga al menos una.
- **De aplicación**: que la primera dirección queda predeterminada sola; que borrar la
  predeterminada reasigna a la más reciente; que borrar la última no deja predeterminada; que el
  tope se comprueba en el servidor; y que un municipio que no pertenece al departamento se
  rechaza.
- **De seguridad**: que nadie lee, edita, borra ni marca como predeterminada la dirección de otra
  persona, y que la respuesta es 404 y no 403, **con prueba propia y no como efecto secundario**;
  que una cuenta cerrada con token vivo no puede escribir; y un guardián al estilo de
  `FavoritesPersonalDataTest` que compruebe que el cierre de cuenta se lleva las direcciones.
- **De filtración**: que ninguna respuesta pública del proyecto contiene una dirección o un
  teléfono de entrega, y que ningún registro los escribe en ningún nivel. El criterio 19 se puede
  probar así de literal, y conviene.
- **De la referencia**: que lo sembrado coincide en número con la fuente declarada en la
  migración; que Bogotá D.C. existe como departamento y como municipio; que hay homónimos y que
  se distinguen por departamento; y que un municipio inactivo sigue dejando leer las direcciones
  que lo apuntan.
- **De componente**: que el selector de municipio se puebla al elegir departamento y se vacía al
  cambiarlo; que un fallo de guardado conserva lo escrito; que el foco va al primer campo con
  error; y que la etiqueta de predeterminada no depende solo del color.
- **De la bandera**: que con `FEATURE_CHECKOUT` apagada los cinco endpoints de la libreta y los
  dos de referencia responden 404 con `COMMON_NOT_FOUND`, byte por byte igual al de un
  controlador que no existe.
- **Extremo a extremo en `e2e-completo/`**: entrar, agregar dos direcciones, comprobar que la
  primera quedó predeterminada sola, marcar la segunda, borrar la segunda y comprobar que la
  predeterminada volvió a la primera, cerrar la cuenta y comprobar que no queda ninguna fila.

## Lo que habría que agregar, y no se agrega aquí

**Reglas de negocio.** La última escrita es RN-097. Ninguna regla del proyecto habla hoy de la
dirección del comprador salvo RN-048, que dice **cuándo se revela** y no qué es ni quién la
guarda. Esta historia obliga a escribir siete:

- **RN-098 — La dirección de entrega es de quien compra y no se comparte con nadie hasta que hay
  pago aprobado.** Es RN-048 dicha desde el otro lado: mientras no exista un pedido pagado, la
  dirección no sale de la cuenta de su dueño, ni hacia el vendedor, ni hacia el agregador, ni
  hacia una transportadora.
- **RN-099 — Una cuenta puede tener varias direcciones y tiene exactamente una predeterminada
  mientras tenga al menos una.** La primera lo es sin que se lo pidan; borrar la predeterminada
  la pasa a la más reciente de las que quedan. Una libreta con direcciones y sin predeterminada
  obligaría a elegir en el peor momento, que es al pagar.
- **RN-100 — El municipio no se escribe: se elige de la división político-administrativa oficial
  del DANE.** El municipio es la entrada de la cotización (RN-039) y la que decide si hay
  cobertura (RN-080). Escrito a mano, «Bogotá», «bogota» y «Bogotá D.C.» son tres destinos
  distintos y ninguno cotiza. El departamento se elige por el mismo motivo, y el par tiene que
  ser coherente en el servidor y no solo en el formulario.
- **RN-101 — Una cuenta admite hasta 10 direcciones.** El motivo no es el mismo que el del
  carrito: allí RN-097 puso veinte porque el cuerpo de la fusión lo manda quien no ha entrado y
  sin tope es arbitrario. Aquí todas las escrituras son autenticadas, y el tope existe para que
  una cuenta no se vuelva almacenamiento gratis de texto libre. Diez cubre con holgura las
  direcciones reales de una persona —casa, trabajo, la de los padres, la de un regalo— y deja la
  libreta en una pantalla. Va como constante con nombre, nunca suelto en el código.
- **RN-102 — Cerrar la cuenta borra las direcciones en el acto**, en la misma transacción que
  anonimiza, como los favoritos y el carrito. No es una limpieza opcional: es lo que sostiene lo
  que `datos-personales.md` afirma sobre la ventana de quince minutos del token.
- **RN-103 — Guardar una dirección no comprueba que exista ni que haya cobertura.** Sendik no
  geocodifica ni valida contra nadie, y no insinúa en ninguna pantalla que se pueda entregar
  allí. La cobertura se sabrá al cotizar, y hoy está sin comprobar (RN-080). Es la regla que
  impide que esta pantalla prometa por accidente lo que RN-080 dice que nadie ha verificado.
- **RN-104 — Quien recibe puede no ser quien compra.** El nombre y el teléfono son de la
  dirección y no de la cuenta: la transportadora llama a quien está en el destino. Son dato
  personal **de un tercero** cuando no coinciden con el titular, y eso tiene consecuencias en la
  política de tratamiento.

**Glosario.** Faltan cinco entradas, y hay una colisión que conviene resolver al escribirlas:

> **En este proyecto «dirección» ya significa URL.** El glosario lo usa así en `SearchQuery`
> —«viaja en la dirección»— y en `RestrictedFileStore` —«nunca se sirve por una dirección
> pública»—, y el modelo de datos también. Así que el término que entra es **«dirección de
> entrega», siempre con apellido y nunca a secas**, y el glosario debería decir por qué.

- **Dirección de entrega** / `ShippingAddress` — a dónde, a nombre de quién y con qué teléfono
  llega lo que alguien compra. Va en la sección Transacción, junto a `Shipment` y `Shipper`.
- **Libreta de direcciones** / `AddressBook` — las direcciones de entrega guardadas por una
  persona, con una predeterminada (RN-099).
- **Dirección predeterminada** / `DefaultAddress` — la que se propondrá al comprar. Exactamente
  una mientras haya alguna.
- **Departamento** / `Department` y **Municipio** / `Municipality` — las dos unidades de la
  división político-administrativa del DANE, que es la lista cerrada de RN-100. Conviene anotar
  que en esa división Bogotá D.C. es las dos cosas.

**Modelo de datos.** Las tres tablas y sus dos migraciones, `V20` y `V21`, con el índice único
parcial de la predeterminada y la clave foránea con `ON DELETE RESTRICT`. Llegan con la
implementación, no antes.

**Contrato de API.** `docs/arquitectura/contrato-api.md` gana las cinco rutas de la libreta y las
dos de referencia, y la decisión de que `PUT /users/me/default-address` es un recurso singular en
vez de un verbo.

**Datos personales.** `docs/operacion/datos-personales.md` necesita tres cosas:

- La **dirección de entrega en la tabla de clasificación**, nivel Interno, junto al teléfono y el
  carrito; y en la de conservación, «mientras exista la cuenta; el cierre la borra en el acto».
- El **dato de un tercero de RN-104**. Cuando quien recibe no es el titular, Sendik trata el
  nombre y el teléfono de alguien que nunca abrió una cuenta ni autorizó nada. Es la primera vez
  que pasa en el proyecto y el documento no lo contempla. La salida habitual es que quien compra
  declare que está autorizado a darlos, con una frase en el formulario; **decidir cuál es la
  salida no es de esta historia y hay que hacerlo antes de implementarla.**
- Que en esta historia **no hay encargado**: la dirección no sale de Sendik. Eso cambia el día
  que se cotice, y ese día aplica lo que ya dice «La direccion del comprador y el envio».

**Alcance.** La línea «El proceso de compra: crear el pedido, la dirección de entrega y el paso a
pagar» hay que partirla y anotarla, exactamente como se hizo con «carrito y proceso de compra» el
10 de septiembre: la dirección deja de esperar a nadie y el resto sigue esperando.

**`CLAUDE.md`.** Dice que «nada de pagos ni de envíos se implementa todavía». Guardar una
dirección no es implementar envíos, pero está lo bastante cerca como para que alguien lo lea al
revés dentro de un mes. Conviene acotarlo **antes** de escribir la primera línea, que es lo que
HU-015 aprendió a hacer con la suya.

## Cuándo revisar

- **Cuando Skydropx conteste**, hay que comprobar dos cosas contra lo que aquí se guarda: si el
  agregador identifica el destino por código DANE, por código postal o por nombre de ciudad, y
  si el código postal es obligatorio. Si lo es, deja de ser opcional y hay que pedírselo a quien
  ya guardó direcciones sin él. **Es el riesgo concreto que esta historia acepta por empezar
  antes de tener la respuesta**, y es acotado: son campos de una tabla, no una arquitectura.
- **Cuando exista el pedido**, hay que decidir que el pedido **copia** la dirección en vez de
  apuntarla, por lo mismo que RN-030 congela el precio: editar o borrar una dirección no puede
  cambiar a dónde se mandó algo que ya se envió, ni dejar un pedido sin destino. Ese día RN-102
  también se revisa, porque el borrado inmediato al cerrar la cuenta choca con los diez años de
  conservación contable de las órdenes.
- **Cuando exista la cotización**, RN-103 se afloja: la pantalla podrá decir si hay cobertura,
  porque habrá con qué saberlo. Hoy no.
- **Cuando el vendedor necesite dirección de origen** (RN-039, RN-078), hay que decidir si
  reutiliza esta misma tabla con un papel distinto o si es otra cosa. La respuesta no es obvia:
  comparten forma y no comparten reglas —la del vendedor es una, obligatoria para publicar, y se
  usa para recoger.
- **Si el DANE crea, suprime o renombra un municipio**, se resiembra con una migración propia y
  se marca inactivo lo que desaparezca. Nunca se borra una fila que una dirección apunte.

---

## Cómo quedó

**Hecha el 11 de septiembre de 2026.** Los veintisiete criterios están implementados y
probados —uno de ellos por construcción, ver abajo—. Las siete reglas que la historia
obligaba a escribir, RN-098 a RN-104, están en `reglas-negocio.md`; el glosario estrena
**Dirección de entrega** / `ShippingAddress`, **Libreta de direcciones** / `AddressBook`,
**Dirección predeterminada** / `DefaultAddress`, **Departamento** / `Department` y
**Municipio** / `Municipality`; y las dos decisiones quedaron en **ADR-0039** y
**ADR-0040**.

### Lo que la historia daba por hecho al revés

- **Sí hay descarga de datos.** La historia decía que no la había y que por eso solo
  había que pensar en el borrado. `ExportUserDataUseCase` existe desde el criterio 22 de
  HU-001 y ya llevaba favoritos y carrito. La dirección entra, y **entera**: en el
  favorito va el identificador y no el título porque el título es del vendedor, pero aquí
  no hay nada que sea de otro —el nombre de quien recibe, el teléfono, la línea y las
  indicaciones los escribió esta persona—. Van los nombres del municipio y del
  departamento y no sus códigos: un «11001» no responde ninguna pregunta que alguien
  pueda hacerse sobre sus propios datos.

- **El criterio 5 desapareció por construcción.** Pedía rechazar un municipio que no
  perteneciera al departamento enviado. Los códigos del DANE son jerárquicos —los dos
  primeros dígitos del código de municipio son los de su departamento, sin una sola
  excepción en las 1122 filas—, así que el cuerpo de la API pide **solo el municipio** y
  un par incoherente no puede existir porque no hay par. Lo que lo demuestra es que el
  DTO no tiene campo de departamento, y hay una prueba de integración que comprueba la
  propiedad sobre la tabla entera para que se vea si algún día deja de ser cierta.

- **El nombre del municipio no hace falta copiarlo en la dirección.** La historia lo
  proponía para poder leer una dirección cuyo municipio se hubiera marcado inactivo. No
  hace falta: la fila de `municipalities` no desaparece nunca —`ON DELETE RESTRICT` lo
  impide— así que el nombre siempre se puede leer uniendo. Y si el DANE renombra uno,
  uniendo sale el nombre nuevo, que es lo correcto.

### Las decisiones que se tomaron al implementarla

- **Se cifran los seis campos libres, en una sola columna.** La historia no lo decidía;
  se decidió al planear y está en ADR-0039. El motivo que lo cerró no fue que la dirección
  sea más delicada que el teléfono, sino que `modelo-datos.md` ya decía que la copia del
  pedido —`orders.shipping_address`— iba cifrada, y cifrar la copia dejando la fuente en
  claro no protege nada.

- **El dato del DANE es real y su origen está escrito.** Los dos conjuntos que el DANE
  publica en el portal de datos abiertos del Estado, con su URL, la versión del conjunto
  y la fecha de descarga en el encabezado de V20. Entran los 1122, incluidas 18 áreas no
  municipalizadas y 1 isla: para una dirección de entrega los tres son el sitio al que
  hay que llevar algo.

- **RN-104 se resolvió con una frase y no con una casilla.** Quien guarda la dirección
  declara que está autorizado a dar el nombre y el teléfono de quien recibe. Se descartó
  la casilla con evidencia fechada —más defendible ante la SIC— porque una casilla por
  cada dirección es fricción en el peor momento y Sendik no puede verificar esa
  autorización de ninguna de las dos formas. Queda escrito en `datos-personales.md` para
  poder reabrirlo.

- **El tope de RN-101 vive en un solo sitio**, y ahí esta historia mejora lo que HU-015
  dejó: aquel tuvo que repetir su veinte en el backend y en el frontend, y anotó que era
  la deuda más concreta que dejaba. Aquí toda escritura es autenticada, así que el
  frontend no conoce el diez. Y el mensaje lo nombra igual, que es lo que el criterio 8
  pide: cuando el servidor rechaza por lleno, cuántas direcciones hay en la libreta es
  exactamente el máximo.

### Lo que apareció al escribirla, y no era de esta historia

- **`ArchitectureTest` cazó dos clases auxiliares.** `ShippingAddressFactory` y
  `ShippingAddressViews` vivían en el paquete `usecase` sin llamarse `UseCase`, y la
  regla dice que allí solo viven casos de uso. No hay un solo precedente de auxiliar en
  ese paquete en todo el proyecto, así que la regla tenía razón: convertir los siete
  campos en dominio es ahora cosa de `ShippingAddressData`, y cruzar una dirección con la
  división es cosa de `ShippingAddressView`.

- **La prueba de la bandera apagada comparaba contra la ruta equivocada.** Copiaba de
  `CartApagadoTest` la comparación contra `/api/v1/inventado`, y esa ruta no casa con
  ninguna regla de `SecurityConfig` y cae en el `denyAll` final: responde 403. No es «una
  ruta que no existe», es una ruta fuera de la superficie declarada. Lo que hay que
  comparar es con una inexistente **del mismo prefijo**.

- **Los departamentos los pedía la pantalla y debía pedirlos el formulario.** Lo destapó
  la prueba del formulario, que se quedaba sin opciones: quien solo miraba su libreta
  traía treinta y tres filas que no iba a usar, y el formulario dependía de que alguien
  las hubiera pedido antes.

- **El primer intento escribió su propio catálogo de mensajes de error.** `ApiError` ya
  sabe convertirse en su clave —`errors.byCode.<CÓDIGO>`— y los dos códigos nuevos viven
  allí con todos los demás.

- **`isUnprocessableEntity()` está obsoleto en Spring 7**, por el mismo renombrado de la
  RFC 9110 que ya había sufrido `UNPROCESSABLE_ENTITY`. Es `isUnprocessableContent()`.

- **La cobertura de `presentation` cayó al 78%** y `check` falló. Su regla de JaCoCo lee
  solo su propio archivo de ejecución —al revés que la de `infrastructure`, que lee
  también el de `bootstrap`— así que lo que cubren las pruebas de integración no le
  cuenta. Entraron dos pruebas de controlador.

### Lo que queda fuera, y por qué

- **Cotizar el envío y comprobar la cobertura.** Esperan a Skydropx, y RN-080 sigue sin
  comprobar. Ninguna pantalla promete entrega (RN-103).
- **Crear el pedido y el paso a pagar.** Es lo que queda de la línea del alcance.
- **La dirección de origen del vendedor.** RN-039 la necesita para cotizar y RN-078 para
  emitir la guía; hoy el perfil solo tiene `city`, texto libre y opcional.
- **El enlace de navegación a `/mis-direcciones`.** No hay enlace desde ninguna parte,
  igual que pasó con `/carrito` y `/mis-favoritos`: `FEATURE_CHECKOUT` está apagada y
  HU-004 y HU-005 prohíben enlazar a algo que no funciona. Su sitio natural es
  `/mi-cuenta`, y entra cuando la bandera se encienda.
- **Elegir la dirección al comprar.** No hay dónde: no existe el proceso de compra.

### Lo que encontraron los cuatro revisores, y que estaba mal de verdad

Se lanzaron los cuatro: `arquitecto`, `revisor-pruebas`, `revisor-seguridad` y
`revisor-accesibilidad`. Entre los cuatro, **un fallo de producción, un bloqueante de
arquitectura y tres bugs de interfaz**.

- **El código postal escrito «110 111» daba un 400 y nadie podía evitarlo.** Es como se
  escribe; el formulario lo aceptaba, lo mandaba tal cual, el dominio del servidor lo
  normaliza pero el borde valida antes y lo rechazaba. Las tres suites en verde. El teléfono
  tenía la misma grieta en su otra forma: el borde lo medía en caracteres y el dominio en
  dígitos, así que un número de dieciséis dígitos atravesaba el borde y salía como 400 **sin
  `errors`**, que es lo contrario del criterio 7. Lo encontró la revisión de pruebas, y lo
  encontró **porque faltaba la prueba de `e2e-completo` que esta misma historia pedía**.

- **`ShippingAddressView` hablaba con un puerto**, y eso lo puse ahí por haber leído mal la
  regla que me falló. Cuando `ArchitectureTest` rechazó mis dos clases auxiliares del paquete
  `usecase` concluí que allí no cabía nada más; la regla lleva `.areTopLevelClasses()`, así
  que nunca prohibió un método estático, y el proyecto tenía la respuesta escrita una historia
  antes: `ReadCartUseCase.conVendedores`. De paso, tal como quedó, un controlador podía
  inyectar la división y saltarse el caso de uso y su transacción sin que ninguna regla lo
  viera.

- **`quitar` y `marcarPredeterminada` no tenían `try/catch`.** Un fallo dejaba la promesa
  rechazada sin capturar, la tarjeta en pantalla y ni una palabra. La pantalla tenía sus tres
  estados para la lectura y ninguno para las escrituras.

- **El foco caía a `<body>`** al borrar, al marcar y al abrir o cerrar el formulario, porque
  en los cuatro casos se destruye lo que se acaba de pulsar. `cart-page` ya lo había resuelto.

- **El arreglo del foco al primer error no estaba en el orden del DOM**, aunque el comentario
  que escribí encima dijera que sí.

- **La comprobación de dueño ocurría después de descifrar**, lo que convertía cualquier fallo
  de una fila ajena en un oráculo de existencia justo donde el criterio 15 exige lo contrario.

- **El motivo que escribí para lo que queda sin cifrar era falso.** Puse que el municipio
  queda fuera «porque es lo único por lo que la tabla se consulta» y ninguna consulta filtra
  por municipio: queda fuera porque es clave foránea. Estaba en V21 y, peor, en
  `datos-personales.md`, que es el documento que se enseña ante una autoridad.

- **El formulario no llevaba aviso de privacidad**, siendo el único del producto que recoge
  el nombre y el teléfono de alguien que nunca aceptó nada. `datos-personales.md` anticipaba
  por escrito ese caso de omisión. `PrivacyNotice` estrena la variante `entrega`.

- Tres tokens mal elegidos con la respuesta ya escrita en `marca.css`, hasta treinta botones
  con el mismo nombre accesible, el criterio 27 a medias —el selector no se deshabilitaba y
  el cambio no se anunciaba—, y `/mis-direcciones` fuera de la auditoría de axe.

**Lo que no encontraron, y conviene decirlo:** ningún hallazgo crítico ni alto de seguridad.
No hay IDOR, el 404 se sostiene en las tres rutas, y RN-102 es cierto y está probado contra
PostgreSQL y no solo documentado.

### Dos cosas que quedan anotadas y no se tocaron

- **La clave del límite de tasa usa la URI cruda**, así que en una ruta con `{id}` cada
  identificador es una clave distinta y el tope no acota nada. Es un defecto anterior —afecta
  igual a `DELETE /users/me/sessions/{id}`— y arreglarlo es cambiar cómo se cuenta en los
  cuatro grupos, con sus pruebas. Lo que sí entró aquí es `/api/v1/locations/**` en el grupo
  de cuenta, que era la única superficie nueva sin cota.
- **El criptograma no está atado a su fila** (ADR-0039). Quien consiga escritura en la base
  puede copiar el cifrado de otra fila a la suya y leerlo por la API. Es propiedad heredada de
  ADR-0020; cerrarlo exige cambiar la firma de `SensitiveDataCipher`, que usan también la
  cédula y la cuenta bancaria.

## Cuándo revisar, con lo que se sabe ahora

Además de lo que ya decía la historia:

- **La libreta no tiene guardián de tamaño en la base.** RN-101 se comprueba en el caso de
  uso contando antes de escribir, así que dos pestañas que agreguen a la vez con nueve
  direcciones pueden dejar once. Es el mismo desbordamiento de uno que HU-015 aceptó con
  el carrito, acotado y sin consecuencia, y se cierra igual de caro: bloqueando la cuenta
  entera en cada petición.
- **`GET /api/v1/locations/*` no tiene límite de tasa**, como casi toda la API fuera de
  `/auth`. Es autenticada y devuelve 1155 filas como mucho, así que el riesgo es el de
  cualquier otra ruta de `/users/**`, que sí cuenta por sujeto. Si algún día la división
  se hace pública para el cotizador, esa cuenta desaparece y conviene mirarlo.
