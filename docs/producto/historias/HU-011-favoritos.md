# HU-011 — Favoritos

**Fase:** 2 | **Estado:** hecha el 2 de septiembre de 2026
**Reglas que aplica:** RN-023, RN-061, RN-068, y cuatro reglas nuevas que esta historia
obliga a escribir (RN-070 a RN-073, al final).

> **Es lo único que HU-009 dejó a deber.** El catálogo quedó cerrado el 27 de agosto de
> 2026 salvo esto, y no por falta de tiempo: «el catálogo es anónimo y de lectura; los
> favoritos son de cuenta y de escritura, y sus reglas no existen». Meterlos allí habría
> sido inventar reglas de negocio en la historia que menos las necesitaba.
>
> Aquí se escriben esas reglas. Son las dos preguntas que HU-009 dejó por nombre —si
> sobreviven a que la publicación se archive o se venda, y si hay tope— más dos que
> aparecieron al responderlas.

## Objetivo

Quien tiene cuenta puede guardar una publicación para volver a ella, y encontrarla después
en un solo sitio sin tener que recordar por dónde llegó.

## Alcance

Entra:

- El control de favorito en la ficha de producto `/producto/:id`.
- La lista propia en `/mis-favoritos`, paginada como el catálogo.
- Marcar desde la ficha sin haber entrado todavía: el control se ofrece a quien no tiene
  sesión, lo lleva a entrar y **al volver el favorito ya está guardado**.
- Una tabla nueva y los endpoints que la mueven.

No entra:

- **El control en las tarjetas del catálogo y del perfil del vendedor.** Se decidió dejarlo
  fuera y merece explicación, porque es lo primero que se echa de menos: esas listas son
  anónimas, de lectura y renderizadas en el servidor, y meter en cada tarjeta un control
  que depende de la sesión obliga a resolver el estado de veinticuatro publicaciones antes
  de pintar la primera. La ficha es una sola y ese problema no lo tiene. Cuando el gesto
  esté probado en la ficha, llevarlo a la tarjeta es una historia corta; al revés es una
  historia larga con el gesto sin probar.
- **Avisar de que un favorito bajó de precio o se vendió.** Es lo que casi todo el mundo
  espera de una lista de deseos, y no entra: no hay sistema de notificaciones al comprador,
  y montarlo por esto es construir la mitad de una funcionalidad de Fase 4 para adornar
  una de Fase 2.
- **Compartir la lista, o que alguien la vea.** Los favoritos son privados (RN-070). Que
  sean públicos algún día es una decisión de producto que nadie ha tomado, y tomarla aquí
  cambiaría también la respuesta sobre marcar lo propio.
- **Carpetas, notas o etiquetas sobre el favorito.** Una lista, sin estructura.
- **Contador de cuánta gente marcó una publicación**, ni en la ficha ni para el vendedor.
  Es una señal pública derivada de un dato privado, y quien la ve la interpreta como
  demanda. Necesita su propia decisión.

## Criterios de aceptación

### Marcar y quitar, desde la ficha

1. Dado alguien con la sesión abierta, cuando abre la ficha de una publicación `PUBLISHED`
   que no es suya, entonces ve el control de favorito **con su estado real**: marcado si ya
   lo estaba, sin marcar si no.
2. Dado que el control está sin marcar, cuando lo pulsa, entonces la publicación queda
   guardada, el control pasa a marcado, y **sigue marcado después de recargar la página**.
3. Dado que el control está marcado, cuando lo pulsa, entonces el favorito se quita, y
   sigue quitado después de recargar.
4. Dado que ya está marcado, cuando la petición de marcar se repite —un reintento, dos
   pestañas—, entonces el resultado es el mismo y no hay dos favoritos: la operación es
   idempotente y no falla por repetirse.
5. Dado que la publicación es suya, cuando abre su ficha, entonces el control **no se
   ofrece**; y si la petición se manda de todos modos, el servidor la rechaza (RN-072). No
   basta con esconder el control.
6. Dado que la publicación no está `PUBLISHED` en el momento de marcarla, cuando se manda
   la petición, entonces el servidor la rechaza. No se guarda lo que no se puede ver.

### Marcar sin haber entrado

7. Dado alguien **sin sesión**, cuando abre la ficha de una publicación `PUBLISHED`,
   entonces ve el control de favorito, sin marcar.
8. Dado que lo pulsa, entonces se le lleva a entrar, y **cuando termina vuelve a la ficha de
   esa publicación con el favorito ya guardado**, sin tener que volver a pulsar.
9. Dado que abandona el ingreso —vuelve atrás, cierra, entra con otra cuenta a otra cosa—,
   entonces no se guarda ningún favorito y la intención se descarta.
10. Dado que la intención quedó pendiente y quien entra resulta ser el dueño de esa
    publicación, entonces no se guarda nada y se le explica por qué (RN-072). El criterio 5
    no se salta por haber pasado por el ingreso.

### La lista

11. Dado alguien con la sesión abierta y con favoritos, cuando abre `/mis-favoritos`,
    entonces los ve, **el marcado más recientemente primero**.
12. Dado que tiene más favoritos de los que caben en una página, entonces la lista pagina
    por cursor, igual que el catálogo, y avanzar no repite ni salta publicaciones.
13. Dado que una publicación favorita **ya no está `PUBLISHED`** —se vendió, se pausó, la
    bajó un moderador—, cuando abre la lista, entonces esa publicación **no aparece**
    (RN-071). La lista aplica RN-068 sin excepción.
14. Dado que una publicación favorita estaba `PAUSED` y su vendedor vuelve a publicarla,
    cuando abre la lista, entonces **vuelve a aparecer**: el favorito no se había borrado,
    solo no se mostraba.
15. Dado que no tiene ningún favorito —o ninguno visible—, entonces ve el estado vacío, que
    explica para qué sirve la lista y lleva al catálogo.
16. Dado alguien sin sesión, cuando pide `/mis-favoritos`, entonces no ve la lista de nadie
    y se le ofrece entrar.

### Las dos

17. El control se recorre con el teclado, dice si está marcado o no de una forma que un
    lector de pantalla anuncie, y el cambio se comunica sin depender solo del color.
18. Ningún texto de esta historia vive en una plantilla: todo por clave de Transloco, en
    español y en inglés.

## Casos borde

- **La bandera está apagada.** Sin `catalog` no hay ficha ni catálogo, así que no hay dónde
  marcar: la ruta `/mis-favoritos` no existe y los endpoints responden 404, como el resto
  de HU-009.
- **Doble envío.** Pulsar dos veces seguidas no deja el estado invertido ni manda dos
  peticiones que se pisen. El criterio 4 cubre el servidor; esto es la pantalla.
- **La publicación se vende mientras está en pantalla.** El control sigue pintado como
  estaba y el intento cae en el criterio 6: se explica y la ficha se actualiza. No se
  reintenta solo.
- **La sesión expiró al pulsar.** Se renueva con la cookie de refresco y se reintenta una
  vez; si tampoco, se dice, y el control vuelve a su estado anterior en vez de quedarse
  mintiendo.
- **Marcar y quitar en dos pestañas.** Gana la última petición que llega. No hay conflicto
  que resolver: el estado final es el que se pidió al final.
- **El favorito que nunca volverá.** Una publicación `SOLD` o `ARCHIVED` no vuelve a
  `PUBLISHED` (RN-023, RN-061), así que su fila deja de mostrarse para siempre. **Se
  conserva igual**, y es una decisión, no un olvido: borrarla obliga a que archivar una
  publicación escriba en la tabla de todas las personas que la habían guardado, y eso es
  un trabajo que crece con la popularidad de lo que se archiva. Lo que hay que vigilar está
  en «Cuándo revisar», más abajo.
- **La cuenta se cierra.** Los favoritos se van con ella. Es dato personal asociado a la
  cuenta y `docs/operacion/datos-personales.md` manda: hay que comprobar que el cierre los
  arrastra y que la descarga de datos los incluye.

## Diseño

- **El favorito no va en bronce.** El acento aparece una vez por pantalla y siempre en la
  insignia de vendedor verificado; en la ficha de producto esa insignia ya existe. El
  control de favorito va en tinta, y su estado marcado se distingue por relleno y por
  forma, no por un color nuevo.
- El estado marcado **no se comunica solo por color** (criterio 17): el icono cambia de
  contorno a relleno, y el texto accesible cambia con él.
- Estados de carga, error y el instante entre pulsar y confirmar. El control puede
  adelantarse al servidor, pero si la petición falla vuelve atrás y lo dice.
- En móvil el control va donde se ve sin desplazar: junto al título, no al fondo de la
  ficha, y con área de toque suficiente aunque el icono sea pequeño.
- La lista reutiliza la tarjeta de producto del catálogo y su rejilla. No estrena nada.
- El estado vacío es una pantalla, no una línea de texto: es lo primero que ve todo el
  mundo, porque todo el mundo empieza sin favoritos.

## Notas técnicas

- **La ficha pública no debe volverse dependiente de la sesión.** Es lo delicado de esta
  historia. `GET /listings/{id}` responde hoy lo mismo para cualquiera y se renderiza en el
  servidor; añadirle un campo «esto es favorito tuyo» la vuelve distinta por persona y
  arruina esa propiedad. El estado del favorito se pide **aparte y desde el navegador**,
  después de hidratar, y la ficha se queda como está. El control se pinta en su estado
  neutro y se corrige al llegar la respuesta, que es exactamente lo que ya hace falta para
  el criterio 7.
- **Endpoints nuevos**, los tres bajo la bandera `catalog`:
  - `PUT /api/v1/users/me/favorites/{listingId}` — marca. Idempotente, criterio 4.
  - `DELETE /api/v1/users/me/favorites/{listingId}` — quita. Idempotente también: quitar lo
    que no está no es un error.
  - `GET /api/v1/users/me/favorites` — la lista, paginada por cursor con `limit` y `cursor`,
    igual que `GET /api/v1/listings` y con el mismo tope. El patrón está en
    `docs/arquitectura/contrato-api.md`.
  - Para el criterio 1 hace falta además saber si **una** publicación concreta está marcada.
    Lo más barato es que la lectura de la lista sirva también para eso, pero no lo es cuando
    hay muchos favoritos: conviene una lectura puntual, `GET /api/v1/users/me/favorites/{listingId}`,
    que responda sí o no sin traerse nada más.
- **Cuelgan de `/users/me/**` a propósito**, y no de `/listings/{id}/favorite`. El favorito
  es del usuario y no de la publicación: bajo `/users/me` la regla de seguridad ya es
  «autenticado» y no hay que inventar ninguna, y la ruta dice de quién es el dato. Es la
  misma razón por la que `SellerVerificationController` está bajo `/api/v1/users/me`.
- **Tabla nueva**, en la migración `V16`: `favorites`, con el identificador de la persona,
  el de la publicación, la fecha en que se marcó, y **unicidad sobre el par** —que es lo que
  hace idempotente al criterio 4 sin comprobar antes de escribir—. Índice por persona y
  fecha descendente, que es la consulta del criterio 11. La fecha es la del favorito y no la
  de la publicación: el orden que pide el criterio 11 es el del gesto.
- **La lectura de la lista cruza `favorites` con `listings` y filtra `PUBLISHED`**, que es
  como el criterio 13 sale gratis y el 14 también: nada se borra, solo deja de casar con el
  filtro.
- **La intención a través del ingreso** (criterios 8, 9 y 10) es lo único sin precedente en
  el proyecto. `exigirRol` redirige a «no encontrado» y no sabe volver, así que aquí no
  sirve. Hay que guardar la publicación pendiente en el navegador antes de ir a `/ingresar`
  y consumirla al volver, **una sola vez**. Dos avisos: el token de acceso vive en memoria y
  se pierde al recargar —la sesión se recupera luego, en el `provideAppInitializer` que
  describe `role.guard.ts`—, así que la intención tiene que sobrevivir a esa recarga y
  esperar a que la sesión esté resuelta antes de dispararse; y hay que borrarla siempre, o
  el favorito reaparecerá la próxima vez que alguien entre desde ese navegador.
- **Claves de Transloco nuevas** en `es.json` y `en.json`: el control y sus dos estados, el
  título de la lista, el estado vacío, y los mensajes de los criterios 6 y 10.

## Pruebas requeridas

- **De dominio**: que el par persona-publicación es único, y que marcar lo propio no es
  representable —o se rechaza en el borde del dominio, no solo en el controlador—.
- **De aplicación**: que marcar es idempotente; que se rechaza sobre una publicación que no
  está `PUBLISHED`; que se rechaza sobre la propia; y que la lista solo devuelve lo
  `PUBLISHED` aunque la tabla tenga más filas —el criterio 13 y el 14, que es la misma
  consulta vista dos veces—.
- **De seguridad**: que nadie lee ni escribe los favoritos de otra persona, ni pasando el
  identificador ajeno. Es una lista privada (RN-070) y el borde tiene que sostenerlo.
- **De componente**: que el control refleja el estado que llega y no el que se supone; que
  el doble pulsado no manda dos peticiones; que un fallo devuelve el control a su estado
  anterior; que sin sesión el control se ofrece igual (criterio 7) y que en la propia
  publicación no se ofrece (criterio 5).
- **De la intención a través del ingreso**: que se guarda, que se consume una sola vez, y
  que abandonar el ingreso no deja nada guardado. Es donde es más fácil dejar un fantasma.
- **Extremo a extremo en `e2e-completo/`**, el ciclo entero por la interfaz: sin sesión,
  marcar desde la ficha, entrar, comprobar que el favorito quedó guardado y que aparece en
  `/mis-favoritos`; después pausar esa publicación desde la cuenta del vendedor y comprobar
  que desaparece de la lista sin haberla desmarcado nadie.

## Lo que habría que agregar, y no se agrega aquí

**Reglas de negocio.** Ninguna existe: `reglas-negocio.md` no menciona los favoritos, y la
última regla escrita es RN-069. Esta historia obliga a escribir cuatro, y las cuatro son
respuestas a preguntas que HU-009 dejó abiertas por escrito:

- **RN-070 — Los favoritos son privados.** Son de quien los marca. Nadie más los ve, ni el
  vendedor de la publicación marcada, ni en agregado. De aquí sale que no haya contador
  público y que compartir la lista quede fuera.
- **RN-071 — En la lista de favoritos se ve solo lo que está `PUBLISHED`.** Es RN-068
  aplicada a la lista propia. Lo que deja de estarlo desaparece **sin borrarse**: si vuelve
  a publicarse, vuelve a verse. Que quien la mire no entienda por qué algo se fue es el
  precio aceptado a cambio de no inventar estados intermedios en una lista personal.
- **RN-072 — Nadie marca como favorita su propia publicación.** No significa nada, y el día
  que exista cualquier señal derivada de los favoritos sería la forma más barata de
  inflarla. Se comprueba en el servidor.
- **RN-073 — No hay tope de favoritos por cuenta.** Se decidió a propósito: cualquier número
  que se ponga es arbitrario y molesta a quien lo alcanza sin proteger de nada que un tope
  alto detenga. Lo que sí hay que vigilar es que la tabla crece sin techo.

**Glosario.** Falta la entrada: **Favorito** / `Favorite`. Conviene decidir de paso si se
llama así o «guardado», porque es la palabra que verá la persona y la que llevará la clase
en el código.

**Modelo de datos.** La tabla `favorites` y su migración `V16`, con la unicidad sobre el par
y el índice por persona y fecha. Llega con la implementación, no antes.

**Datos personales.** `docs/operacion/datos-personales.md` no contempla los favoritos.
Habría que decidir si son dato personal a efectos de la Ley 1581 —lo son: dicen qué le
interesa a una persona identificada— y, si lo son, incluirlos en la descarga de datos y en
el borrado al cerrar la cuenta.

## Cuándo revisar

- **Si la tabla `favorites` crece más rápido de lo previsto**, RN-073 se reabre: no para
  poner un tope arbitrario, sino para decidir si se limpian las filas cuya publicación
  quedó `SOLD` o `ARCHIVED` hace mucho, que hoy se conservan a propósito.
- **Si alguna vez hay contador público de favoritos o listas compartidas**, hay que releer
  RN-070 y RN-072 juntas: son la misma decisión vista por sus dos lados.
- **Si el control llega a las tarjetas del catálogo**, vuelve el problema que esta historia
  esquivó —resolver el estado de veinticuatro publicaciones antes de pintar—, y esa es la
  historia donde hay que resolverlo, no antes.


---

## Cómo quedó

**Hecha el 2 de septiembre de 2026.** Los dieciocho criterios están implementados y
probados. Las cuatro reglas que la historia obligaba a escribir —RN-070 a RN-073— están en
`reglas-negocio.md`, el glosario estrena **Favorito** / `Favorite` y **Comprador** /
`BuyerId`, y la vuelta al sitio después de ingresar quedó decidida en **ADR-0029**.

### Las decisiones que se tomaron al implementarla

- **Los favoritos viven en el contexto `catalog`**, aunque la ruta cuelgue de
  `/api/v1/users/me/favorites`. La lista cruza `favorites` con `listings` y filtra
  `PUBLISHED`, y RN-072 compara contra el vendedor: los dos son datos del catálogo. Que la
  ruta diga `/users/me` es transporte, igual que `SellerVerificationController`, que vive
  en `identity` colgando de la misma base.

- **El criterio 5 se resolvió sin tocar el contrato de la sesión.** La sesión que guarda el
  navegador no lleva el identificador de la cuenta, así que la ficha no puede comparar
  contra el vendedor. En vez de agregarlo —que era cambiar HU-001 para que una pantalla
  haga una comprobación que RN-072 pone en el servidor de todas formas—, la lectura puntual
  responde `{ favorite, eligible }`. La regla se queda donde debe y la pantalla obedece.

- **Marcar lo que no está `PUBLISHED` responde 404 y no 422.** Es lo que ya hace
  `ListingNotFoundException`: no existe y no es para ti responden igual. Un 422 diría «este
  identificador es una publicación real que no está publicada», que es justo lo que RN-068
  existe para no decir. El orden de las dos comprobaciones —primero el estado, después el
  dueño— es lo que impide que un 403 confirme la existencia del borrador de otra persona, y
  tiene prueba propia.

- **La clave primaria de `favorites` es el par**, y esa restricción es la que hace
  idempotente al criterio 4 con `ON CONFLICT DO NOTHING`. Comprobar antes de escribir no
  bastaba: entre la lectura y la escritura cabe la petición de la otra pestaña.

- **`identity` le pregunta a `catalog` por un caso de uso público**, con el puerto
  `UserFavorites` y el adaptador `CatalogUserFavorites`. Es el mismo patrón que el catálogo
  ya usaba en la dirección contraria para saber si un vendedor está verificado. No es un
  ciclo: son dos conversaciones distintas, cada una por la puerta pública del otro.

### Lo que apareció al escribirla, y no era de esta historia

- **`GET /api/v1/listings?limit=500` respondía 500 y no 400**, desde HU-009 y en
  producción. `@Validated` hace que la restricción del parámetro la evalúe el proxy de
  validación, que lanza `ConstraintViolationException`; el manejador global solo cubría la
  de los cuerpos. **La prueba de HU-009 no podía verlo y sigue en verde**: el montaje
  autónomo de MockMvc no crea ese proxy, así que allí la excepción que rompe la respuesta
  nunca se lanza. Arreglado en commit aparte, con `CatalogLimitTest` en `bootstrap`, que es
  donde sí se ve.

- **Salir no borraba de la caché lo que el servidor había respondido.** El perfil y las
  sesiones abiertas se quedaban ahí desde HU-001, y quien entrara después en el mismo
  navegador —sin recargar— los veía un instante. Con los favoritos era peor: heredaban el
  minuto de frescura del catálogo, así que ni siquiera se revalidaban enseguida. RN-070 no
  se sostenía. Ahora al salir se olvida todo lo cacheado y las dos consultas privadas van
  sin frescura.

- **`/mis-favoritos` faltaba en `app.routes.server.ts`**: la página se pintaba entera y se
  servía con 404. Lo delató `rutas.spec.ts`, que es el guard que existe para esto y que ya
  cazó lo mismo en `/ingresar` y en las tres rutas de HU-007.

### Lo que queda fuera, y por qué

- **El control en las tarjetas del catálogo y del perfil del vendedor.** Estaba fuera desde
  el alcance y sigue fuera. Ahora que el gesto está probado en la ficha, llevarlo a la
  tarjeta es la historia corta que la historia anticipaba.
- **La entrada de navegación a `/mis-favoritos`.** No hay enlace desde ninguna parte, igual
  que las rutas de HU-002, HU-006 y HU-007: `FEATURE_CATALOG` está apagada y HU-004 y HU-005
  prohíben enlazar a algo que no funciona. El enlace entra cuando la bandera se encienda.
- **Avisar de que un favorito bajó de precio o se vendió**, compartir la lista, carpetas y
  el contador público. Los cuatro estaban fuera del alcance y siguen fuera.
- ~~**La frase de la garantía del fabricante en la ficha** (RN-067) sigue aplazada a la tanda
  legal, como la dejó HU-009.~~ **Escrita el 8 de septiembre de 2026** en esa tanda, sobre la
  ficha y sin tocar nada de esta historia.

### Lo que cambió la revisión

Cuatro subagentes revisaron la rama terminada —arquitectura, pruebas, accesibilidad y
seguridad— y encontraron cosas que la autorrevisión no vio. Lo que se corrigió:

- **El mensaje del criterio 10 no se podía ver nunca.** Lo encontraron accesibilidad y
  pruebas por separado, en la misma línea. El bloque del error vivía dentro del `@if` del
  control, así que en el camino exacto que el criterio describe —vuelvo del ingreso con una
  intención pendiente sobre mi propia publicación, el servidor responde `eligible: false`—
  el control dejaba de pintarse y se llevaba el mensaje por delante. La prueba del 403 no lo
  veía porque montaba con `eligible: true`, que es el único caso en que el estado no
  contradice al error.

- **Una persona podía quedarse con el favorito que pidió otra.** Lo encontró seguridad. A
  pulsa «Guardar» sin sesión y no entra; B entra después en esa misma pestaña y abre esa
  ficha por su cuenta: la intención se disparaba y B acababa con un favorito que nunca
  pidió, que es dato personal escrito en nombre de otra persona. Ahora la intención lleva un
  **pase de un solo uso** que viaja en la dirección de vuelta y sin el cual no se consume.
  El pase no es una credencial y no vale por sí solo —un enlace con un pase inventado y sin
  intención local no marca nada—, así que ADR-0029 se sostiene: la acción sigue sin viajar
  en el enlace.

- **Una cuenta ya cerrada podía escribir favoritos durante quince minutos.** El token
  sobrevive al cierre (ADR-0003) y todas las demás rutas de `/users/me` recargan la cuenta y
  responden 401; estas no. Quedaba dato personal vivo justo después de ejercer el derecho de
  supresión, y nada volvía a borrarlo porque el cierre ya había pasado. Lo comprueba ahora
  el puerto `BuyerAccounts`, y solo en el caso de uso que escribe: los otros tres no crean
  dato ni revelan nada sobre una cuenta cerrada.

- **`errors[].field` publicaba el nombre del argumento Java, en español.** El cliente manda
  `?limit=500` y recibía `{"field": "limite"}`. Lo encontraron arquitectura y seguridad. Es
  un defecto que introduje al arreglar el 500 de HU-009, y mi propia prueba lo consagraba.
  Los argumentos de esos parámetros pasan a llamarse como el parámetro HTTP, que es lo que
  hace correcto lo que sale.

- **RN-072 estaba escrita dos veces**, y el argumento con que justifiqué la copia era falso:
  `Listing.laPublico(ModeratorId)` ya resolvía esa forma para RN-063. Ahora la comparación
  vive en `Listing.esDe(BuyerId)` y los dos sitios preguntan por ella.

- **`[disabled]` mataba el foco.** El botón se deshabilitaba en el mismo tick del clic, con
  el foco dentro, y el navegador lo mandaba a `body`: quien pulsa con teclado tenía que
  tabular desde el principio del documento. El doble pulsado ya lo bloquea el almacén
  contando peticiones, así que el atributo no aportaba nada.

- **`aria-pressed` con nombre variable se contradecía**: un lector leía «Quitar de
  favoritos, botón de alternancia, pulsado». Se quitó `aria-pressed` y el estado lo lleva el
  nombre, como manda la APG de ARIA.

- **Las dos «señales que no son color» eran inertes**: en modo claro `--color-texto` y
  `--color-primario` son el mismo valor, y `.btn` ya trae el peso fuerte. El comentario que
  lo afirmaba era falso. El estado se distingue por el relleno del icono y por el texto.

- **Dos cosas ya existían y no las busqué**: `--medida: 70ch` —el ancho de lectura del
  sistema, que además crece con el texto— y `.vacio`, el estado vacío que ya usan la bandeja
  y la cola. Sobraban el token nuevo en px y el bloque paralelo.

- **`/mis-favoritos` se quedó fuera de la auditoría de axe**, que advierte en su propio
  archivo que una página que no está en la lista no se audita y nadie se entera.

- **Salir limpiaba la caché solo por una de las tres puertas.** El cierre de cuenta y el
  refresco que caduca se quedaban fuera. La limpieza se movió a `SessionStore.clear()`, que
  es por donde pasan las tres, y solo cuando de verdad había sesión: purgar en cada visita
  anónima tiraría la caché que llega del renderizado en servidor.

Y en pruebas, los huecos que señaló el revisor: la idempotencia concurrente se afirmaba en
cinco sitios y se probaba secuencialmente en todos —ahora hay dos hilos con su propia
conexión—; el adaptador que cruza contextos no tenía ninguna prueba, así que el cierre y la
descarga estaban probados por mitades que no se tocaban; el cursor nunca daba la vuelta
completa por HTTP; los umbrales se probaban por un solo lado; el índice de `V16` se podía
borrar sin que fallara nada; una prueba afirmaba que un UUID es igual a sí mismo; y el
riesgo que la propia historia declara como principal —que la ficha pública no se vuelva
dependiente de la sesión— no tenía ningún guardián.

## Cuándo revisar, con lo que se sabe ahora

Además de lo que ya decía la historia:

- **Ningún endpoint fuera de `/api/v1/auth/*` tiene límite de tasa.** No lo introduce esta
  historia —vale igual para todas las escrituras de HU-007 y HU-002— pero aquí se junta con
  RN-073, que decide a propósito no poner tope de favoritos: una cuenta autenticada puede
  hacer crecer la tabla sin techo y sin nada que la frene. Si se decide poner límites de
  tasa al resto de la API, esta es una de las rutas que los necesita.
- **La suite `e2e-completo` no es repetible contra una base sucia.** La cola del moderador
  pagina de veinte en veinte, así que con más de veinte publicaciones pendientes acumuladas
  de corridas anteriores el ayudante no encuentra la suya y fallan nueve pruebas que no
  tienen nada roto. No es de esta historia, pero se descubrió aquí: contra una base limpia
  pasan las treinta y ocho.
