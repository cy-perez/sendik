# HU-015 — El carrito

**Fase:** 3 | **Estado:** hecha el 10 de septiembre de 2026
**Reglas que aplica:** RN-029, RN-030, RN-035, RN-061, RN-068, RN-076, y nueve reglas
nuevas que esta historia obliga a escribir (RN-089 a RN-097, al final).

> **Es la mitad de «carrito y proceso de compra» que no espera a nadie.** La Fase 3 arrancó
> por la búsqueda porque era lo único sin bloqueo; esto es lo segundo. El pago necesita a
> Wompi —que ni siquiera está confirmado como proveedor: `entrega-textos-legales-2026-09-05.md`
> todavía lo tiene como `[[NOMBRE DE LA PASARELA DE PAGO]]`— y el envío necesita las cuatro
> respuestas de Skydropx. Reunir productos y ver cuánto suman no necesita ninguna de las dos.
>
> ~~La línea de `CLAUDE.md` que dice «nada de carrito, pagos ni envíos se implementa
> todavía» se escribió cuando las tres cosas parecían una sola. Hay que acotarla a pagos y
> envíos **antes** de implementar esta historia, no después.~~ **Acotada el 10 de
> septiembre de 2026**, antes de escribir la primera línea de código.

## Objetivo

Quien compra puede reunir varios productos antes de decidir, ver cuánto suma lo que lleva y
volver a encontrarlo al día siguiente, sin que la plataforma le prometa que sigue estando
disponible.

## Alcance

Entra:

- El control **Agregar al carrito** en la ficha de producto `/producto/:id`, y quitarlo desde
  allí mismo.
- La página `/carrito`, **agrupada por vendedor**, con el subtotal de cada grupo.
- **Carrito sin sesión**, en el navegador, que al entrar se fusiona con el de la cuenta.
- **Lo que dejó de estar disponible se queda a la vista**, apagado y fuera del subtotal.
- Una tabla nueva y los endpoints que la mueven, detrás de `FEATURE_CHECKOUT`, que ya está
  declarada y apagada en `application.yaml`.

No entra:

- **El proceso de compra entero**: crear el pedido, la dirección de entrega, la cotización
  del envío, el pago y la división. Es lo que bloquean Wompi y Skydropx, y es la razón de
  que esta historia exista partida. El carrito termina donde empieza el pedido.
- **El total.** RN-076 obliga a enseñar **tres cifras separadas y sumadas** —precio base,
  costo de envío y total—, y aquí solo existe la primera. Un carrito que rotule «Total» una
  suma sin envío está mintiendo sobre lo que se va a pagar, y encima incumple RN-076 el día
  que el envío aparezca. Se dice **subtotal**, y se dice explícitamente que el envío se
  calcula al comprar.
- **Cantidades.** El producto es único y su existencia es siempre 1 (glosario, Catálogo):
  no hay «2 de esta camisa», y si el vendedor tiene dos iguales son dos publicaciones. No
  hay selector de cantidad ni suma de unidades en ninguna parte.
- **Reservar.** Agregar al carrito no aparta nada: la publicación se marca vendida cuando el
  pago queda aprobado (RN-035) y no antes. Quien llegue primero al pago se lo lleva.
- **El control en las tarjetas del catálogo, la búsqueda y el perfil del vendedor.** Fuera
  por lo mismo que en HU-011: esas listas son anónimas, de lectura y renderizadas en el
  servidor, y un control que depende del carrito en cada tarjeta obliga a resolver el estado
  de veinticuatro publicaciones antes de pintar la primera. La ficha es una sola.
- **Guardar para después, mover a favoritos y al revés.** Son dos listas separadas y así se
  quedan. Cruzarlas es una historia propia y ninguna regla la pide.
- **Avisar por correo de que algo del carrito se vendió o cambió de precio.** No hay
  notificaciones al comprador, y montarlas por esto es construir media funcionalidad de
  Fase 4 para adornar una de Fase 3. Es lo mismo que HU-011 dejó fuera y por el mismo motivo.
- **Vaciar el carrito de una vez.** Se quita producto a producto. Con el tope de RN-097 no
  hay listas donde eso duela, y un botón que borra todo pide una confirmación que pide un
  diálogo que no existe.

## Criterios de aceptación

### Agregar y quitar, desde la ficha

1. Dado alguien con la sesión abierta, cuando abre la ficha de una publicación `PUBLISHED`
   que no es suya, entonces ve el control del carrito **con su estado real**: «en el
   carrito» si ya está, «agregar al carrito» si no.
2. Dado que no está en el carrito, cuando pulsa, entonces el producto queda en el carrito, el
   control cambia de estado, y **sigue en el carrito después de recargar la página**.
3. Dado que ya está, cuando pulsa otra vez, entonces se quita, y sigue quitado después de
   recargar.
4. Dado que ya está, cuando la petición de agregar se repite —un reintento, dos pestañas—,
   entonces el resultado es el mismo y el producto no está dos veces: la operación es
   idempotente y no falla por repetirse (RN-091).
5. Dado que la publicación es suya, cuando abre su ficha, entonces el control **no se
   ofrece**; y si la petición se manda de todos modos, el servidor la rechaza (RN-092). No
   basta con esconder el control.
6. Dado que la publicación no está `PUBLISHED` en el momento de agregarla, cuando se manda la
   petición, entonces el servidor la rechaza con el mismo 404 que una que no existe (RN-068).
7. Dado que el carrito ya tiene el tope de productos de RN-097, cuando intenta agregar uno
   más, entonces se rechaza y se le dice cuál es el tope. No se descarta el más antiguo en
   silencio.

### Sin sesión, y la fusión al entrar

8. Dado alguien **sin sesión**, cuando abre la ficha de una publicación `PUBLISHED` y pulsa
   el control, entonces el producto queda en su carrito **sin que se le pida entrar**, y
   sigue ahí después de recargar y al día siguiente en ese mismo navegador.
9. Dado que tiene productos en el carrito sin sesión, cuando entra a su cuenta, entonces el
   carrito resultante es **la unión de los dos**: no se pierde lo que traía del navegador ni
   se borra lo que ya tenía guardado (RN-095).
10. Dado que la unión superaría el tope de RN-097, cuando entra, entonces se conserva hasta
    el tope, **lo más reciente primero**, y se le dice qué no cupo. No falla la entrada por
    esto.
11. Dado que la fusión terminó, cuando recarga o vuelve a entrar, entonces **no se vuelve a
    fusionar nada**: el carrito del navegador se consumió una sola vez y se borró.
12. Dado que una persona armó un carrito sin sesión y **entra otra** en ese mismo navegador,
    entonces ese carrito **no pasa a la segunda** (RN-095). Es el fallo que la revisión de
    seguridad encontró en HU-011 con los favoritos, y aquí se escribe como criterio en vez de
    esperar a que aparezca.
13. Dado alguien sin sesión, cuando abre `/carrito`, entonces ve su carrito del navegador,
    con los mismos grupos y subtotales, y no se le obliga a entrar para verlo.

### La página del carrito

14. Dado alguien con productos de **dos vendedores distintos**, cuando abre `/carrito`,
    entonces los ve en **dos grupos, uno por vendedor**, cada uno con el nombre del vendedor
    y su propio subtotal (RN-090).
15. Dado que hay más de un grupo, entonces la pantalla dice, antes de que sorprenda, que
    **será un pedido y un envío por vendedor**.
16. Dado cualquier carrito, entonces **en ninguna parte aparece la palabra total** ni una
    suma de todos los grupos: se muestran subtotales de precio base y se dice que el costo
    de envío se calcula al comprar (RN-096, RN-076).
17. Dado un producto en el carrito, entonces la cifra que se ve es **el precio vigente de la
    publicación**, no el que tenía cuando se agregó (RN-093).
18. Dado que el precio cambió desde que lo agregó, cuando abre el carrito, entonces se le
    dice que cambió, y la cifra que manda sigue siendo la vigente (RN-093).
19. Dado que el carrito está vacío, entonces ve el estado vacío, que explica para qué sirve y
    lleva al catálogo.
20. Dado que quita el último producto de un grupo, entonces el grupo desaparece entero, con
    su subtotal, sin dejar un encabezado de vendedor vacío.

### Lo que dejó de estar disponible

21. Dado un producto del carrito que **ya no está `PUBLISHED`** —lo compró otro, el vendedor
    lo pausó o lo archivó, un moderador lo bajó—, cuando abre el carrito, entonces **sigue
    apareciendo**, visiblemente apagado, **fuera del subtotal de su grupo** y con la opción
    de quitarlo (RN-094). Es la excepción deliberada a RN-071.
22. Dado ese producto, entonces el texto que lo acompaña es **uno solo y el mismo sea cual
    sea el motivo**: no se distingue si se vendió, si se pausó o si lo bajó un moderador
    (RN-094, RN-068). Que se vendió es información del vendedor y de quien lo compró, no de
    quien lo tenía apuntado.
23. Dado un grupo en el que **todos** los productos dejaron de estar disponibles, entonces el
    grupo se ve con subtotal en cero y sin nada que comprar, y no se oculta.
24. Dado que el producto vuelve a estar `PUBLISHED` —estaba `PAUSED` y su vendedor lo
    reanuda—, cuando abre el carrito, entonces **vuelve a contar**: no se había borrado, solo
    no sumaba.

### Las dos

25. El control del carrito se recorre con el teclado, su estado lo anuncia un lector de
    pantalla, y el cambio no se comunica solo por color.
26. Ningún texto de esta historia vive en una plantilla: todo por clave de Transloco, en
    español y en inglés.
27. Dado que `FEATURE_CHECKOUT` está apagada, cuando se piden los endpoints, entonces
    responden lo mismo que una ruta que no existe: **404 con `COMMON_NOT_FOUND`** para las
    cinco de la cuenta, y para la pública lo que la cadena responda a cualquier ruta
    desconocida. No es 403 en ningún caso.

    > **Este criterio decía además «y el control no se pinta en la ficha», y se corrigió el 10
    > de septiembre de 2026 tras la revisión.** Pedía algo que el proyecto no tiene: el
    > frontend no conoce ninguna bandera —tampoco `FEATURE_CATALOG`—, y la forma establecida
    > desde HU-009 y HU-011 es la contraria: la ruta y el control existen, la API responde 404
    > y la pantalla muestra su estado de error. Construir propagación de banderas al navegador
    > es un mecanismo nuevo y no era de esta historia. Lo que sí se ajustó es el criterio, para
    > que no dé por hecho algo que no existe.
    >
    > Y la parte pública no puede responder 404 a quien no ha entrado: en esta aplicación una
    > ruta desconocida pedida sin token no da 404, así que lo que se afirma —y lo que la prueba
    > comprueba— es la propiedad de verdad, que es no distinguirse de una ruta que no existe.

## Casos borde

- **La publicación se vende mientras el carrito está en pantalla.** No se reintenta nada solo:
  la fila queda como estaba hasta que se recarga, y al recargar cae en el criterio 21.
- **Agregar justo cuando otro está pagando.** Se agrega igual. El carrito no reserva (RN-089)
  y no hay conflicto que resolver: el segundo se entera al comprar, no antes.
- **Doble pulsado.** Pulsar dos veces seguidas no deja el estado invertido ni manda dos
  peticiones que se pisen. El criterio 4 cubre el servidor; esto es la pantalla, y HU-011
  ya enseñó que `[disabled]` en el mismo tick del clic mata el foco: no se usa.
- **La sesión expiró al pulsar.** Se renueva con la cookie de refresco y se reintenta una vez;
  si tampoco, se dice y el control vuelve a su estado anterior en vez de quedarse mintiendo.
- **El navegador sin almacenamiento.** Modo privado, almacenamiento bloqueado o lleno: el
  carrito sin sesión no se puede guardar. Se degrada a decirlo, no a fallar en silencio; con
  sesión no le afecta.
- **El carrito del navegador con un identificador que ya no existe.** Se guardó hace un mes y
  la publicación se archivó: el servidor no la devuelve, y la fila se pinta con lo que el
  navegador guardó de ella y el texto del criterio 22.
- **El carrito del navegador manipulado a mano.** Es dato del cliente y no se cree: los
  identificadores se validan contra la base al fusionar y al pintar, y el precio y el título
  que muestre el servidor mandan sobre lo que diga el navegador. Un identificador inventado
  se descarta sin error.
- **La cuenta se cierra.** El carrito se va con ella, como los favoritos: es dato personal
  asociado a la cuenta.
- **La cuenta cerrada que todavía tiene token.** El token sobrevive quince minutos al cierre
  (ADR-0003). El caso de uso que **escribe** tiene que recargar la cuenta y responder 401,
  que es exactamente lo que la revisión de seguridad encontró que faltaba en HU-011.

## Diseño

- **El carrito no va en bronce.** El acento aparece una vez por pantalla y es la insignia de
  vendedor verificado, que en la ficha y en el encabezado de cada grupo ya existe. El botón
  principal va en tinta.
- **El grupo por vendedor es la unidad visual**, no la fila: encabezado con el nombre del
  vendedor y su insignia si la tiene, sus productos, y el subtotal cerrando el grupo. Que
  serán pedidos distintos tiene que verse, no leerse en una nota al pie.
- **Lo no disponible se distingue sin depender del color**: la fila baja de contraste, el
  precio se tacha y el texto del criterio 22 lo dice con palabras. Un lector de pantalla lo
  anuncia como parte del nombre de la fila, no como un adorno.
- La fila reutiliza lo que ya existe de la tarjeta de producto del catálogo. No estrena nada.
- El estado vacío es una pantalla, no una línea: es lo primero que ve todo el mundo.
- En móvil el subtotal de cada grupo queda pegado a su grupo y no flotando: con dos
  vendedores, un subtotal flotante no se sabe de cuál es.

## Notas técnicas

- **La ficha pública no debe volverse dependiente de la sesión**, igual que en HU-011.
  `GET /listings/{id}` se queda como está y el estado del carrito se pide aparte desde el
  navegador después de hidratar. Sin sesión ni siquiera hay petición: lo sabe el navegador.
- **Endpoints nuevos**, los cinco bajo `FEATURE_CHECKOUT` y colgando de `/users/me` por lo
  mismo que los favoritos —el carrito es de la persona, no de la publicación, y allí la regla
  de seguridad ya es «autenticado»—:
  - `GET /api/v1/users/me/cart` — el carrito entero, ya agrupado por vendedor y con los
    subtotales calculados en el servidor. **Sin paginación**: el tope de RN-097 la hace
    innecesaria, y una suma paginada no es una suma.
  - `PUT /api/v1/users/me/cart/items/{listingId}` — agrega. Idempotente, criterio 4.
  - `DELETE /api/v1/users/me/cart/items/{listingId}` — quita. Idempotente también.
  - `GET /api/v1/users/me/cart/items/{listingId}` — la lectura puntual del criterio 1, que
    responde sin traerse el carrito entero. Es lo que HU-011 acabó necesitando para el
    control de la ficha.
  - `POST /api/v1/users/me/cart` — la fusión del criterio 9, con la lista de identificadores
    del navegador. Es unión, así que repetirla no hace daño; devuelve el carrito ya fusionado
    y lo que no cupo por el criterio 10. **No lleva verbo en la ruta** —no hay `/merge`—
    porque el contrato no los admite y esto es «agregar varios a este recurso».
- **El carrito sin sesión necesita leer varias publicaciones por identificador, y hoy no hay
  ninguna ruta que lo haga.** Es lo único de esta historia sin precedente en el proyecto.
  **Decidido el 10 de septiembre de 2026:** un parámetro `ids` repetible en
  `GET /api/v1/listings`, excluyente con los parámetros de búsqueda —pedir por identificador
  y filtrar a la vez es 400, no una intersección— y con tope de 20, el de RN-097. Se enciende
  con el **mismo patrón de bean testigo que `SearchFeature`**: la ruta es también el catálogo
  de HU-009 y tiene que seguir respondiendo con `FEATURE_CHECKOUT` apagada, así que lo que
  desaparece no es la ruta sino la capacidad de pedir por identificador, y con la bandera
  apagada `ids` responde 404 con `COMMON_NOT_FOUND` igual que los parámetros de búsqueda sin
  la suya. Devuelve solo lo `PUBLISHED`; los que falten son los del criterio 21, y el
  navegador ya sabe cuáles pidió. **Cambia `contrato-api.md`**, que es la ruta que ya
  comparten HU-009 y HU-014.

  No nace un `/listings/batch` ni nada parecido, por lo mismo que no nació un `/search`:
  pedir varias publicaciones es listar el mismo recurso con otra condición, y una ruta aparte
  duplicaría la forma de la respuesta, la paginación y la bandera.
- **El carrito del navegador guarda una copia para pintar**, no solo identificadores: título,
  precio, imagen y vendedor. Sin ella no se puede pintar la fila del criterio 21 de algo que
  el servidor ya no devuelve. Es copia y se trata como tal: la verdad es lo que responda el
  servidor.
- **Tabla nueva**, migración `V19` —la última aplicada es `V18__catalog_search.sql`—:
  `cart_items`, con el identificador de la persona, el de la publicación, la fecha en que se
  agregó y **el precio con el que entró**, que existe solo para el aviso del criterio 18 y
  nunca para cobrar. **Unicidad sobre el par** persona-publicación, que es lo que hace
  idempotente al criterio 4 con `ON CONFLICT DO NOTHING` sin leer antes de escribir. Índice
  por persona y fecha descendente.
- **No hay tabla `carts`.** Un carrito es «las filas de esta persona», como los favoritos.
  Una tabla de cabecera no guardaría ni un dato que no se deduzca de las filas, y obligaría a
  crearla antes de la primera.
- **La lectura cruza `cart_items` con `listings`** y **no filtra `PUBLISHED`**: lo trae todo y
  marca cuál está disponible. Es la diferencia exacta con la lista de favoritos, donde el
  filtro era la regla; aquí filtrar sería el defecto del criterio 21.
- **El precio se suma con decimales exactos** (RN-029). Ningún subtotal se calcula en el
  navegador: llega calculado y el navegador lo pinta.
- **La fusión del criterio 12 usa el pase de un solo uso de ADR-0029**, el que HU-011 tuvo que
  añadir cuando la revisión de seguridad encontró que una persona podía quedarse con el
  favorito que pidió otra. Aquí el problema es el mismo y más caro: no es un favorito ajeno,
  es un carrito ajeno.
- **Claves de Transloco nuevas** en `es.json` y `en.json`: el control y sus dos estados, el
  título de la página, el encabezado de grupo, el subtotal, la frase del envío del criterio
  16, la del criterio 15, la de no disponible del criterio 22, el aviso de precio del 18, el
  tope del 7 y del 10, y el estado vacío.
- **`/carrito` va en `app.routes.server.ts`.** Es lo que se olvidó en `/mis-favoritos` y lo
  cazó `rutas.spec.ts`.

## Pruebas requeridas

- **De dominio**: que el par persona-publicación es único; que agregar lo propio no es
  representable o se rechaza en el borde del dominio; que el subtotal de un grupo suma con
  decimales exactos y **excluye lo no disponible**; que un grupo entero no disponible da cero
  y no desaparece.
- **De aplicación**: que agregar es idempotente; que se rechaza sobre lo que no está
  `PUBLISHED` y sobre lo propio; que la lectura devuelve **también** lo no disponible, que es
  lo contrario de lo que prueba la lista de favoritos; que la fusión es unión y no reemplazo;
  que la fusión respeta el tope conservando lo más reciente; y que el tope se comprueba en el
  servidor.
- **De seguridad**: que nadie lee ni escribe el carrito de otra persona ni pasando el
  identificador ajeno; que una cuenta cerrada con token vivo no puede escribir; y que el
  carrito del navegador **no cambia de dueño** (criterio 12), con prueba propia y no como
  efecto secundario de otra.
- **De componente**: que el control refleja el estado que llega y no el que se supone; que el
  doble pulsado no manda dos peticiones; que un fallo devuelve el control a su estado
  anterior; que sin sesión funciona sin pedir entrar (criterio 8); que en la propia
  publicación no se ofrece; y que la palabra «total» no aparece en ninguna plantilla de esta
  historia —el criterio 16 se puede probar así de literal, y conviene—.
- **De la fusión**: que se consume una sola vez, que borra el carrito del navegador al
  terminar, y que abandonar el ingreso no deja nada fusionado. Es donde es más fácil dejar un
  fantasma, como en HU-011.
- **De la bandera**: que con `FEATURE_CHECKOUT` apagada los cinco endpoints y la ruta
  responden 404 con `COMMON_NOT_FOUND`, byte por byte igual al de un controlador que no
  existe, y que el parámetro `ids` del catálogo también.
- **Extremo a extremo en `e2e-completo/`**: sin sesión, agregar dos productos de dos
  vendedores distintos, comprobar los dos grupos y los dos subtotales, entrar y comprobar que
  el carrito sobrevivió a la fusión; después vender o pausar uno de los dos desde la cuenta
  del vendedor y comprobar que la fila sigue ahí, apagada y fuera del subtotal, sin que nadie
  la haya quitado.

## Lo que habría que agregar, y no se agrega aquí

**Reglas de negocio.** Ninguna existe: `reglas-negocio.md` **no menciona la palabra carrito**,
y la última regla escrita es RN-088. Esta historia obliga a escribir nueve:

- **RN-089 — El carrito no reserva nada.** Agregar un producto no lo aparta ni lo saca del
  catálogo: la publicación se marca vendida cuando el pago queda aprobado (RN-035) y no
  antes. Dos personas pueden llevar el mismo producto en el carrito y solo una lo compra. Es
  la regla que hace honesto todo lo demás, y la que obliga al criterio 21 a existir.
- **RN-090 — Un carrito admite varios vendedores y se agrupa por vendedor.** Cada grupo será
  un pedido, porque un pedido es la compra de uno o varios productos **a un mismo vendedor**
  (glosario). La división se le enseña al comprador desde el carrito y no se le descubre al
  pagar.
- **RN-091 — En el carrito un producto entra una sola vez y sin cantidad.** El producto es
  único y su existencia es siempre 1. No hay selector de cantidad en ninguna pantalla.
- **RN-092 — Nadie agrega al carrito su propia publicación.** Es RN-072 aplicada a comprar en
  vez de a guardar, y por una razón más fuerte: comprarse a sí mismo movería dinero y
  comisión en círculo. Se comprueba en el servidor.
- **RN-093 — El precio del carrito es el vigente, y solo se congela al crear el pedido**
  (RN-030). Si cambió desde que se agregó, se avisa; lo que manda es el de ahora. El precio
  con el que entró se guarda **solo para poder avisar**, nunca para cobrar.
- **RN-094 — Lo que deja de estar disponible se queda a la vista, apagado, fuera del subtotal
  y con un solo motivo.** Es la excepción deliberada a RN-071, y la diferencia es el momento:
  un favorito que desaparece es un misterio menor, un carrito que adelgaza en silencio justo
  antes de pagar es otra cosa. El límite se lo pone RN-068: se dice que ya no está
  disponible y **no se dice por qué**, porque distinguir «se vendió» de «lo pausó su
  vendedor» publica el movimiento del catálogo y las decisiones de un vendedor a cualquiera
  que apunte un identificador en su carrito.
- **RN-095 — El carrito sin sesión vive en el navegador, se fusiona por unión al entrar y no
  cambia de dueño.** Se consume una sola vez y se borra. Si entra otra persona en ese mismo
  navegador, no hereda nada.
- **RN-096 — El carrito no muestra total.** Solo subtotales de precio base por vendedor, y
  dice que el envío se calcula al comprar. El total con sus tres cifras nace cuando exista el
  costo de envío (RN-076, RN-077).
- **RN-097 — El carrito admite hasta 20 productos**, a diferencia de los favoritos, donde
  RN-073 decidió a propósito que no hubiera tope. La diferencia no es de gusto: el carrito
  sin sesión lo arma el navegador y **el servidor lo recibe entero en la fusión**, así que
  sin tope hay un cuerpo de tamaño arbitrario que alguien manda sin haber entrado.

  **Por qué veinte.** Porque un carrito lleno **cabe en una sola página del catálogo**: el
  `limit` va por omisión en 24 y admite hasta 50 (`ListCatalogQuery.LIMITE_MAXIMO`), de modo
  que la lectura por `ids` nunca necesita una segunda petición ni paginar, que es lo que
  sostiene que `GET /users/me/cart` devuelva el carrito entero —una suma paginada no es una
  suma—. Es además el mismo veinte de las dos colas del moderador, así que no estrena un
  número en el proyecto, y está muy por encima de cualquier carrito real de productos únicos
  de segunda.

  El rechazo se ve y no se descarta nada en silencio (criterios 7 y 10). El número va como
  constante con nombre junto a `LIMITE_MAXIMO`, nunca suelto en el código.

**Glosario.** Faltan tres entradas, y la sección donde van es Transacción, junto a `Order` y
`OrderItem`:

- **Carrito** / `Cart` — lo que alguien reunió y todavía no ha comprado. Es la palabra del
  alcance de la Fase 3 y la que verá quien compra.
- **Ítem del carrito** / `CartItem` — un producto dentro del carrito, en paralelo exacto a
  «Ítem de pedido» / `OrderItem`, que ya existe. Conviene notar la tensión aparente con
  «Palabras que no se usan», que prohíbe «artículo» e «ítem» **como sinónimos de producto en
  texto visible**; el nombre compuesto de una línea de un documento es otra cosa y el
  glosario ya lo admite para el pedido. Si se prefiere evitarla, la alternativa es no nombrar
  la línea en español y dejarla solo en el código.
- **Grupo del carrito** / `CartGroup` — los productos de un mismo vendedor dentro del
  carrito, con su subtotal. Es lo que se convertirá en un pedido (RN-090), y tiene nombre
  propio porque es la unidad de la pantalla.

**Modelo de datos.** La tabla `cart_items` y su migración `V19`, con la unicidad sobre el par,
el precio de entrada y el índice por persona y fecha. Llega con la implementación, no antes.

**Contrato de API.** `docs/arquitectura/contrato-api.md` gana el parámetro `ids` de
`GET /api/v1/listings` —decidido, con tope 20 y excluyente con los de búsqueda— y las cinco
rutas de `/users/me/cart`. Lo primero toca la ruta que ya comparten HU-009 y HU-014, así que
se escribe en el mismo commit que lo implementa y no después.

**Datos personales.** `docs/operacion/datos-personales.md` no contempla el carrito. Lo es a
efectos de la Ley 1581 por la misma razón que los favoritos —dice qué le interesa a una
persona identificada, y aquí además qué estuvo a punto de comprar—, así que hay que incluirlo
en la descarga de datos y en el borrado al cerrar la cuenta. El carrito **sin sesión** no lo
es mientras viva solo en el navegador y no se asocie a nadie; deja de no serlo en el instante
de la fusión, y eso conviene decirlo en el documento.

**`CLAUDE.md`.** La frase «nada de carrito, pagos ni envíos se implementa todavía» hay que
acotarla a pagos y envíos, que son los que de verdad esperan a un tercero.

## Cuándo revisar

- **Cuando exista el proceso de compra**, RN-096 se reabre entera: aparece el costo de envío,
  y con él las tres cifras de RN-076 y el total de verdad. El carrito es el sitio donde eso
  se nota primero.
- **Cuando exista el pedido**, hay que decidir qué le pasa al carrito al comprarlo: si las
  filas se borran, si se borran solo las del grupo que se pagó, y qué queda si el pago falla
  a medias. Esta historia no lo puede contestar porque el pedido no existe.
- **Si `cart_items` crece sin control**, RN-097 ya puso el tope por persona; lo que queda por
  vigilar es el número de personas con carrito abierto y desde cuándo. Limpiar filas viejas
  es una decisión que hoy no hay con qué tomar.
- **Si algún día lo vendido se queda visible en el catálogo** —RN-068 lo decide hoy al revés y
  dice explícitamente que cambiarlo es cambiar esa regla—, entonces RN-094 se puede aflojar y
  el carrito podría decir «se vendió» en vez de «ya no está disponible». Son la misma
  decisión vista por sus dos lados.

---

## Cómo quedó

**Hecha el 10 de septiembre de 2026.** Los veintisiete criterios están implementados y
probados. Las nueve reglas que la historia obligaba a escribir —RN-089 a RN-097— están en
`reglas-negocio.md`, el glosario estrena **Carrito** / `Cart`, **Ítem del carrito** /
`CartItem`, **Grupo del carrito** / `CartGroup` y **Subtotal**, y las dos decisiones que
aparecieron al planear quedaron en **ADR-0036** y **ADR-0037**.

### Las decisiones que se tomaron al planearla

- **El tope es 20.** Porque un carrito lleno cabe en una sola lectura: el `limit` del
  catálogo va por omisión en 24 y admite hasta 50, así que leer un carrito entero nunca
  necesita paginar —y una suma paginada no es una suma—. Es además el mismo veinte de las
  dos colas del moderador.

- **La lectura anónima es un recurso propio y no un parámetro del catálogo.** La historia
  proponía `ids` en `GET /api/v1/listings` y al planear se vio que eso obligaba al
  navegador a agrupar y sumar por su cuenta, contradiciendo otra línea de la propia
  historia. `GET /api/v1/carts?ids=…` devuelve la misma forma que el carrito de la cuenta,
  y los dos caminos comparten `Cart.de`: la suma vive en un solo sitio (ADR-0037).

- **Los criterios 9 y 12 no se podían cumplir los dos.** Uno pedía fusión automática al
  entrar y el otro que otra persona no heredara el carrito de ese navegador; siendo
  anónimo, nadie puede distinguir «vuelve el mismo» de «llega otro». Se pregunta una vez.

- **El carrito vive en `catalog` y no en `order`** (ADR-0036). Un carrito no tiene ciclo
  de vida y lo que devuelve son agregados `Listing`. `order` sigue vacío.

### Las decisiones que se tomaron al implementarla

- **`Money` aprendió a sumar.** Sabía comparar y no sumar, porque hasta hoy ninguna regla
  lo había pedido: la comisión se calcula sobre un solo precio y el catálogo no totaliza
  nada. El subtotal de RN-096 es la primera cifra del proyecto que nace de sumar varias.
  No se agregó resta: abriría la puerta a un negativo que el constructor rechaza y hoy
  ninguna regla resta.

- **El tope se comprueba en el caso de uso y no en el dominio.** Un `CartItem` no puede
  saber cuántos hermanos tiene. Se cuenta antes de escribir y se acepta lo que implica:
  dos pestañas que agregan a la vez con diecinueve productos pueden dejar veintiuno. Es un
  desbordamiento de uno, acotado y sin consecuencia —el tope existe para que el cuerpo de
  la fusión no sea arbitrario—, y cerrarlo exigiría bloquear la cuenta entera en cada
  petición.

- **`CartView` es dos cosas y no una.** `Cart` agrupa por `SellerId` y no sabe cómo se
  llama nadie: el nombre y la insignia son de `identity`. El carrito se puede armar sin un
  solo nombre; la pantalla no se puede pintar sin ellos, y esa diferencia es la clase.

- **El número del tope vive en dos sitios**, el dominio del backend y el del frontend. No
  es evitable: el carrito sin sesión lo arma el navegador y ahí no hay servidor que lo haga
  cumplir. El mensaje que lo nombra también lo escribe el frontend, porque el cuerpo de
  error del proyecto lleva `code` y `traceId` y nada más.

### Lo que apareció al escribirla, y no era de esta historia

- **`GET /api/v1/carts` sin `ids` respondía 500.** Ningún parámetro de consulta del
  proyecto era obligatorio hasta hoy —`limit`, `cursor` y los filtros tienen omisión o son
  opcionales— así que `MissingServletRequestParameterException` no la lanzaba nadie y caía
  en el manejador de `Exception`. Es el mismo defecto que HU-011 encontró con el `limit`
  del catálogo, en su otra forma. Arreglado en el manejador global.

- **Una consulta deshabilitada se queda en `isPending` para siempre**, así que quien
  llegaba por primera vez al carrito —sin sesión y sin nada guardado— veía el esqueleto
  eternamente. `isLoading` es «pendiente **y** pidiendo», que es lo que significa cargar.

- **El hook de convenciones rechazaba seis archivos que ya estaban en el repositorio.** Su
  regla de API del navegador se escribió el 8 de septiembre y nunca se ejecutó sobre
  `favorite-intent.ts`, `session.store.ts` ni los tres de idioma, que hacen exactamente lo
  que su mensaje pide. Se acotó: no dispara cuando el archivo aísla la plataforma, ni en
  las pruebas unitarias, que nunca se renderizan en el servidor. Lo que sigue cazando es un
  componente que toca `window.` sin aislar nada.

### Lo que queda fuera, y por qué

- **El proceso de compra entero.** Estaba fuera del alcance y sigue fuera: crear el pedido,
  la dirección, la cotización del envío, el pago y la división esperan a Wompi y a
  Skydropx.
- **El total.** No es una carencia: es RN-096 hasta que exista el costo de envío.
- **El enlace de navegación a `/carrito`.** No hay enlace desde ninguna parte, igual que
  pasó con `/mis-favoritos`: `FEATURE_CHECKOUT` está apagada y HU-004 y HU-005 prohíben
  enlazar a algo que no funciona. El enlace entra cuando la bandera se encienda.
- **El control en las tarjetas del catálogo**, vaciar el carrito de una vez, y avisar por
  correo de que algo se vendió o cambió de precio. Los cuatro estaban fuera del alcance.

## Cuándo revisar, con lo que se sabe ahora

Además de lo que ya decía la historia:

- **Si el tope de RN-097 cambia**, hay que cambiarlo en dos sitios y **no hay ningún
  guardián que avise** si solo se cambia uno. Es la deuda más concreta que deja esta
  historia.
- **Ningún endpoint fuera de `/api/v1/auth/*` tiene límite de tasa**, y ahora hay uno
  público más: `GET /api/v1/carts`. Su única defensa es el tope de veinte. Si se decide
  poner límites de tasa al resto de la API, esta es de las primeras rutas que los necesita.
- **Cuando exista el pedido**, hay que decidir qué le pasa al carrito al comprarlo: si las
  filas se borran, si solo las del grupo que se pagó, y qué queda si el pago falla a
  medias. Esta historia no lo puede contestar porque el pedido no existe.
