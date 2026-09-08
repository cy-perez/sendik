# Alcance por fases

Regla: no se implementa funcionalidad de una fase posterior, aunque el diseño ya
la contemple y aunque sea fácil. Lo que no está en la fase actual, se anota.

## Fase 1 — cimientos, cuentas y sitio informativo

**Cerrada el 21 de agosto de 2026.** Todo lo que sigue está implementado y probado.
Quedan dos cosas fuera de esa afirmación, las dos por decisión y las dos anotadas
donde corresponde:

- ~~**El primer despliegue está aplazado**~~ **Resuelto el 26 de agosto de 2026.**
  Se contrató `sendik.co` en GoDaddy y con eso se cerró la decisión que faltaba:
  el sitio se hospeda en Cloud Run junto al backend, y GoDaddy queda como
  registrador y DNS (ADR-0024). `dev` se despliega desde la integración continua,
  con las dos piezas, y sigue costando cero. **Producción sigue aplazada**, ahora
  por lo que de verdad la bloquea: los textos legales del punto siguiente y las
  piezas que cobran por hora encendida (`docs/operacion/entornos.md`).
- ~~**Los tres textos legales siguen siendo `borrador-local`**~~ **Redactados y
  publicados el 5 de septiembre de 2026**, versión `2026-09-05`, en los seis archivos
  y en los dos idiomas (`docs/operacion/textos-legales.md`). Cubren identificación,
  condición de portal de contacto, cuenta, verificación, publicación, compra, envío,
  Respaldo, retracto, garantías, comisión, datos personales y cookies.

  **Publicados sin revisión de abogado colegiado, por decisión expresa.** Al dejar de
  ser `borrador-local` desaparece el aviso de «sin valor legal», así que el texto se
  presenta como vigente. Lo que sigue abierto —cinco puntos de criterio profesional,
  las excepciones al retracto sin transcribir, y los campos sin dato— está en
  `docs/operacion/entrega-textos-legales-2026-09-05.md`. Falta además el aviso de
  privacidad y el texto de las casillas de autorización.

**Plataforma**
- Monorepo con backend y frontend, Gradle multi-módulo y Angular con SSR.
- Sistema de diseño implementado: tokens, componentes base, modo oscuro y
  selector de idioma.
- Controles y garantías de accesibilidad. La línea decía «controles de
  accesibilidad» sin definir cuáles, y una frase así no se puede dar por
  cumplida ni por incumplida. Son estos cinco, y están:
  enlace de salto al contenido, foco visible de 3px que nunca se elimina,
  conmutador de tema claro y oscuro, selector de idioma, y respeto de las
  preferencias del sistema (`prefers-reduced-motion` y el tema preferido). La
  lista completa y comprobable está en `docs/ui/accesibilidad.md`, y la audita
  `frontend/e2e/accesibilidad.spec.ts` con axe sobre WCAG 2.2 AA en los dos
  modos (ADR-0016). No entra ningún control de tamaño de texto: nadie lo ha
  decidido y no se inventa aquí.
- Internacionalización ES/EN funcionando con SSR.
- Canalización de integración continua: compilar, probar, analizar y desplegar.
  Los cuatro pasos existen: `.github/workflows/verificacion.yml` compila, prueba
  y analiza, y `despliegue.yml` publica **las dos piezas** —backend y frontend con
  renderizado en servidor— en `dev` con cada integración a `main`, y en `prod` con
  etiqueta y aprobación. El despliegue del frontend se escribió el 26 de agosto de
  2026, cuando contratar `sendik.co` cerró la decisión de hospedaje: va a Cloud Run
  junto al backend y GoDaddy queda como registrador y DNS (ADR-0024). **`prod`
  sigue aplazado por decisión**, no por falta de trabajo, y ahora por lo que de
  verdad lo bloquea: los textos legales y las piezas que cobran por hora encendida.
  El motivo está en `docs/operacion/entornos.md` y el procedimiento en
  `docs/operacion/despliegue.md`.
- Base de datos con migraciones y esquema inicial de usuarios.

**Cuentas**
- Registro de comprador y de vendedor con correo y contraseña.
- Verificación de correo.
- Inicio y cierre de sesión con JWT propio y refresco rotatorio.
- Recuperación de contraseña.
- Perfil básico editable.
- Cierre de cuenta y descarga de datos personales.

**Sitio informativo**
- Portada con la propuesta de valor y las tres tarjetas de confianza (HU-004).
- Cómo funciona, para comprador y para vendedor (HU-005).
- Sobre Sendik (HU-005).
- Preguntas frecuentes (HU-005).
- Contacto (HU-005).
- Términos y condiciones, política de tratamiento de datos, política de cookies.
  La política de devoluciones y retracto va dentro de los términos mientras no
  se decida separarla en documento propio (`docs/operacion/textos-legales.md`).

El texto de las cinco páginas vive en `docs/producto/textos-web.md`, que es la
fuente de la que salen las claves de Transloco.

**Fuera de esta fase:** publicar productos, catálogo, búsqueda, carrito, pagos,
envíos, verificación de identidad. El sitio informativo **describe** todo eso en
presente, porque es lo que convence a alguien de registrarse, pero no anuncia
fechas ni deja enlaces a rutas que no existen.

## Fase 2 — publicación y catálogo

**Cerrada el 5 de septiembre de 2026.** Empezó el 21 de agosto. Las trece historias
están implementadas, probadas y desplegadas, y **las tres banderas están encendidas
en `dev`** desde ese día: `FEATURE_SELLER_VERIFICATION`, `FEATURE_PUBLISHING` y
`FEATURE_CATALOG`. En `prod` siguen apagadas, que es otra decisión.

Encenderlas destapó una cosa que ninguna suite podía ver, y queda anotada abajo en
«lo que el encendido en `dev` destapó». No cambia el cierre de la fase —el código
de Fase 2 está completo— pero sí la lista de lo que bloquea el lanzamiento.

- **Hecho.** Verificación de vendedor: identidad, selfie y cuenta bancaria
  (HU-002). Incluye los cinco endpoints de revisión del moderador; la bandeja
  con la que se usan es el punto de más abajo. Detrás de
  `FEATURE_SELLER_VERIFICATION`, hoy apagada.
- Asistente de captura de fotos con overlay, nivelador y recorte en cliente
  (HU-003). **Hecho el 28 de agosto de 2026**, en `/publicar/:id/capturar`: ocho pasos
  con nombre, silueta y cuadrícula, nivel de 5 grados, recorte a 3:4 en un worker y
  subida con progreso real. Es enteramente frontend: el backend ya tenía el endpoint
  desde HU-007.
- Visor 360º en la ficha de producto (HU-003). **Hecho el mismo día**, en `shared/ui`
  y sin ninguna librería. Se ofrece solo con la secuencia completa de ocho.
  La salvedad que quedaba —el recorrido de extremo a extremo con cámara simulada— **se cerró
  ese mismo 28 de agosto de 2026**, unas horas después: `e2e-completo/captura-y-visor.spec.ts`
  recorre las ocho tomas con la cámara falsa, aprueba, y comprueba sobre la ficha pública lo
  que hasta entonces no verificaba nada —el criterio 18 en el HTML que sale del servidor y el
  recorte a 3:4 sobre los píxeles que de verdad se guardaron—. Con eso la historia queda sin
  deuda.
- Publicación de producto con las cuatro tomas obligatorias y las intermedias, en
  las dos familias: moda —nueva y de segunda— y **tecnología, solo nueva**
  (RN-064). La tecnología se agregó al alcance el 24 de agosto de 2026 y entra en
  esta fase porque HU-007 todavía no tenía código: meterla entonces costaba menos
  que reabrir el catálogo después. Moverla a una fase posterior es cambiar esta
  línea. Trae también los endpoints de decisión del moderador sobre
  publicaciones, y con ella quedaron cerradas las reglas que faltaban: RN-061,
  RN-062 y RN-063.
  **HU-007 está hecha**: el backend el 25 de agosto de 2026 y la interfaz el 26.
  Están el dominio con su máquina de estados, los diecisiete casos de uso, la
  persistencia, el aviso por correo al vendedor, los diecisiete endpoints y las
  tres pantallas del vendedor —`/publicar`, `/publicar/:id` y
  `/mis-publicaciones`—, todo detrás de `FEATURE_PUBLISHING`, hoy apagada. Las
  cuatro decisiones de producto que la historia enumeraba se tomaron el 26 de
  agosto. **Lo que impide encender la bandera ya no es HU-007 sino HU-008:** sin
  bandeja del moderador, nada puede salir de `PENDING_REVIEW`.
- Panel de moderación y flujo de aprobación o rechazo con motivo. **Hecho a
  medias**: HU-006 entrega la bandeja de verificaciones de vendedor, y con ella
  `FEATURE_SELLER_VERIFICATION` ya se puede encender. La moderación de
  publicaciones —RN-015, la otra mitad de este punto— tiene sus reglas y sus
  endpoints de decisión en HU-007, y **HU-008 la cerró el 27 de agosto de 2026**: la
  cola, la bandeja y el detalle donde se decide. Con ella este punto queda completo.
  El recorrido de punta a punta del ciclo está escrito en `e2e-completo/`; las tres pruebas
  que nacieron en rojo se arreglaron el 27 de agosto de 2026 y el diagnóstico quedó en la
  historia. Fuera de las dos historias
  quedaban revocar un sello ya otorgado y bajar una publicación ya visible (RN-024):
  los endpoints existían, pero no había forma de llegar a esos identificadores desde la
  interfaz. **Las recogió HU-010, y las dos juntas**: esta línea decía que cada una
  esperaba su propia historia, y el 28 de agosto de 2026 se decidió lo contrario —el
  mismo rol, el mismo gesto de deshacer y la misma pantalla de confirmación—.
  **HU-010 quedó hecha el 1 de septiembre de 2026**: bajar desde la ficha pública,
  revocar desde el perfil del vendedor, la lectura nueva
  `GET /api/v1/verifications/by-seller/{sellerId}` para llegar de un vendedor a su
  verificación, y los dos ciclos recorridos por la interfaz en `e2e-completo/`.
  Escribirla dejó a la vista que la lista de motivos de revocación no existía: el
  endpoint reutilizaba la del rechazo, y sus cuatro valores hablan de la solicitud, no de
  un sello ya otorgado. La fija RN-069 y le da columna propia
  `V15__revocation_reason.sql`. **Con esto se suelta el freno que la propia historia
  señalaba** para `FEATURE_CATALOG`: la moderación ya tiene el camino de ida y el de
  vuelta. Encenderla sigue siendo una decisión aparte; las tres banderas
  —`seller-verification`, `publishing` y `catalog`— siguen apagadas por omisión.
- Catálogo, categorías, ficha de producto y favoritos. **HU-009 lo cerró el 27 de
  agosto de 2026**, salvo los favoritos: listado paginado por cursor, navegación
  por el árbol en sus dos niveles, ficha de producto y perfil público del
  vendedor, todo detrás de `FEATURE_CATALOG` y todo aplicando RN-068 —en el
  catálogo se ve solo lo `PUBLISHED`—. Con ella el ciclo de la fase deja de ser un
  callejón sin salida: lo que un moderador aprueba ya lo ve alguien sin cuenta.
  **Los favoritos salieron a su propia historia**, HU-011, porque el catálogo es
  anónimo y de lectura y ellos son de cuenta y de escritura, con reglas que no
  existían. **Quedó hecha el 2 de septiembre de 2026** y con ella este punto se
  cierra entero: el control en la ficha, la lista propia en `/mis-favoritos`
  paginada por cursor, y la intención que sobrevive al ingreso —quien no tiene
  sesión pulsa, entra, y vuelve con el favorito ya guardado (ADR-0029)—. Escribirla
  obligó a fijar las cuatro reglas que faltaban: RN-070 a RN-073, que responden por
  escrito lo que HU-009 dejó abierto —si un favorito sobrevive a que la publicación
  se archive o se venda, y si hay tope—. La tabla es `V16__favorites.sql`, con clave
  primaria sobre el par, que es lo que hace idempotente marcar dos veces sin leer
  antes de escribir.

  Destapó además dos defectos que estaban en producción y no eran suyos: el límite
  de los listados por cursor respondía 500 en vez del 400 que exige
  `contrato-api.md`, y cerrar sesión no borraba de la caché del navegador el perfil,
  las sesiones abiertas ni la lista de favoritos. Los dos arreglados, cada uno con
  la prueba que sí puede verlos.

  Queda fuera todavía la frase de la garantía del fabricante en la ficha (RN-067),
  aplazada a la tanda legal por decisión del 26 de agosto.
- Panel del vendedor con sus publicaciones y su estado. **Sigue a medias, pero por
  una sola cosa.** `/mis-publicaciones` llegó con HU-007 y da la lista con el
  estado de cada una, que es lo que hacía falta para retomar un borrador. Lo que
  faltaba era el panel: «ni cifras, ni ventas, ni el rastro de lo que pasó con cada
  publicación», y de esas tres ya solo falta una.

  **Las cifras las cerró HU-012 el 4 de septiembre de 2026**: una por cada estado de
  RN-061 encima de la lista, contadas por el servidor —`GET
  /api/v1/users/me/listings/summary`— y no sobre la página ya cargada, que habría
  atado la cifra al tamaño de esa página el día que la lista crezca. El cero se dice
  en vez de esconderse, porque omitir «0 en revisión» obliga a deducir por ausencia y
  quien deduce por ausencia no distingue «no tengo ninguna» de «esto no cargó». La
  fila es una lectura aparte de la lista, así que una cifra que no llega no puede
  tapar las publicaciones.

  La ruta no es la que la historia proponía. Decía `/api/v1/listings/mine/summary` y
  ese prefijo no existe: las publicaciones propias viven bajo `/api/v1/users/me`
  desde HU-007, y de ahí heredan la regla de seguridad que exige token.

  **Las ventas son Fase 3** y quedan fuera por alcance, no por olvido. **El rastro lo
  cerró HU-013 el 5 de septiembre de 2026**, y con él este punto queda completo: quien
  vende abre, dentro de cada publicación, qué le pasó y por qué —`GET
  /api/v1/listings/{id}/moderation-history`— sin que en ningún sitio aparezca quién lo
  decidió, que es la regla nueva que obligó a escribir, RN-074.

  La decisión de fondo fue que el rastro cuenta **lo que le pasó a la publicación** y no
  solo lo que hizo Sendik: el envío a revisión se anota como evento, porque
  `listings.submitted_at` guarda uno solo y sin eso una publicación rechazada y reenviada
  no deja ver las dos vueltas. Se anota por los dos caminos que llevan a `PENDING_REVIEW`,
  incluido el que RN-062 abre al editar una publicación viva. `V17` reconstruye desde
  `submitted_at` los envíos anteriores.

  Destapó además un defecto que estaba en producción y no era suyo: el envío se fechaba con
  el reloj de la aplicación y las decisiones del moderador con el `now()` de la tabla, y dos
  relojes escribiendo en un mismo registro ordenado se cruzan. Arreglado, con la prueba de
  recorrido que sí puede verlo.

**Con esto la Fase 2 queda cerrada en cuanto a código.**

### Lo que el encendido en `dev` destapó

Las tres banderas se encendieron en `dev` el 5 de septiembre de 2026, junto con
`SECURITY_BOOTSTRAP_MODERATORS`, y se recorrió el entorno con datos reales. Lo que
funcionó y lo que no:

**Funciona, comprobado contra `dev` y no contra una suite**

- El catálogo público responde sin sesión, pagina por cursor y rechaza con 400 un
  cursor inválido o un `limit` fuera de rango.
- El árbol de categorías llega sembrado, con sus seis familias.
- La ficha de una publicación que no existe **llega renderizada desde el servidor**
  diciendo «Esta publicación ya no está disponible» (ADR-0025, RN-068). No es un
  esqueleto que se rellena después: el texto viene en el HTML.
- `ModeratorBootstrap` anota en el arranque lo que hizo y lo que no pudo hacer.
- El rodeo de seguridad que HU-013 cerró —un identificador con `%3F` para saltar la
  regla autenticada— responde 401 contra el Tomcat real de Cloud Run, que es la
  única forma de comprobarlo: MockMvc no decodifica la URI.

**El ciclo completo, recorrido de punta a punta**

Con las tres cuentas verificadas por correo real: la vendedora entregó cédula, selfie
y cuenta bancaria, la moderadora le dio el sello, publicó con las ocho tomas, se la
rechazaron, la corrigió, la reenvió y se la aprobaron. La publicación aparece en el
catálogo público sin sesión y su ficha llega **renderizada desde el servidor** con el
título real en `<title>`, en el `<h1>` y en `og:image` (ADR-0025).

Dos cosas que solo se pueden comprobar así:

- **RN-046 sobre datos reales.** Ni la respuesta de la verificación ni la bandeja del
  moderador devuelven la cédula ni la cuenta completas: solo los cuatro últimos
  dígitos. La cédula y la selfie viajaron al bucket reservado y no salen por ninguna
  lectura.
- **El rastro de HU-013 con sus dos vueltas.** Envío, rechazo con motivo, reenvío y
  aprobación, en orden, sin quién decidió y sin la nota interna. 401 sin sesión y
  **404 —no 403—** con la cuenta de otra persona.

Y salieron los ocho correos que el ciclo debe generar: tres verificaciones, el acuse
de la solicitud, el sello concedido, el rechazo con su motivo y su nota, y la
publicación aprobada.

**Lo que costó llegar hasta ahí: el correo transaccional no salía**

Registrar una cuenta dejaba esto en el registro de Cloud Run:

```
ERROR c.s.identity.client.ResendMailSender : El proveedor rechazo un correo transaccional con estado 403
```

El dominio `sendik.co` se contrató el 26 de agosto y **nunca se le añadieron los
registros que Resend exige** para verificar un remitente. Se añadieron ese mismo 5 de
septiembre y con eso el 403 desapareció. **Nadie lo había visto porque nadie se había
registrado nunca contra `dev`**: el perfil `local` usa `MAIL_PROVIDER=console` y las
suites leen el enlace del registro de la aplicación, así que las dos mitades de HU-001
estaban probadas sin que saliera un solo correo de verdad.

**Y debajo había otro, que sigue abierto: Cloud Run congela el envío**

`AsyncMailSender` difiere el envío a un ejecutor, así que el correo sale **después** de
que la petición haya respondido. El servicio corre con la asignación de CPU por
omisión —`cpu-throttling` sin fijar, o sea activa— y con `minScale` sin fijar, de modo
que Cloud Run **solo asigna CPU mientras procesa una petición**. Devuelto el 202, el
contenedor se congela y la llamada saliente a Resend muere a medias con
`ResourceAccessException`.

Se demostró, no se dedujo: el mismo envío aislado que a las 16:01 murió por red salió
a las 16:03 sin más cambio que mantener el contenedor atendiendo peticiones de salud
durante quince segundos. El recorrido entero de arriba se hizo con ese truco.

**Una petición aislada es el caso normal**: alguien que se registra solo. Los
reintentos que se añadieron ese día no lo salvan, porque los tres ocurren en el mismo
hilo congelado.

**Decidido e implementado el 5 de septiembre de 2026: ADR-0031, Cloud Tasks.** El
envío ocurre dentro de una petición al propio backend, que es donde Cloud Run sí
asigna CPU, y los reintentos los hace el servicio gestionado en vez de un hilo
congelado —con lo que se cierra de paso el buzón de reintentos que faltaba—.

El código está completo: `AsyncMailSender` se retiró, la composición del texto se
separó del transporte, el encolado entra detrás del puerto `MailTransport` y el
endpoint `/internal/mail/deliveries` entrega verificando el token OIDC de la cuenta de
servicio. La infraestructura se creó el 6 de septiembre de 2026 —la cola, la cuenta
`sendik-cola` y las cuatro variables del entorno `dev`, en `docs/operacion/entornos.md`—.

**Comprobado de punta a punta el 8 de septiembre de 2026, y hasta ese día no lo estaba.**
Que el servicio arrancara solo probaba que la configuración estaba completa, porque
`MailQueueProperties` valida al construirse; no probaba que saliera un correo. Entre el 6
y el 8 la cola registró **cero tareas** y el endpoint no recibió una sola petición de
Cloud Tasks: las únicas cuatro que le llegaron fueron `GET` de rastreadores de internet,
rechazadas con 401. Nadie se había registrado contra `dev` desde que se encendió.

El disparo fue un `POST /api/v1/auth/forgot-password` contra una cuenta existente, que es
el caso que ADR-0031 llama normal —una petición aislada, con el correo pendiente después
de responder—:

| Hora (UTC) | Eslabón | Resultado |
|---|---|---|
| 13:39:10 | `forgot-password` | 202 en 1,95 s |
| 13:39:12 | Cloud Tasks entrega la tarea | `POST /internal/mail/deliveries`, agente `Google-Cloud-Tasks`, 204 |
| 13:39:13 | Resend entrega | correo recibido, de `no-responder@sendik.co` |

Tres segundos, sin reintentos y sin una sola advertencia en el registro. El 204 por sí
solo no bastaba —el controlador lo devuelve tanto si el correo salió como si el proveedor
lo rechazó en firme—, y es el correo recibido lo que lo desambigua. Que el token OIDC
valida se ve en que respondió 204 y no el 401 que reciben los rastreadores.

**Para `dev` esto deja de bloquear el lanzamiento. Para `prod` no, y es configuración:**
el entorno `prod` de GitHub **no tiene ninguna de las cuatro variables** `MAIL_QUEUE_*`, y
el flujo respalda la bandera con `|| 'false'`. Un despliegue a `prod` hoy no falla al
arrancar: se va con la cola apagada y el correo se entrega en el hilo de la petición. Eso
ya no reproduce el fallo original —lo causaba el `AsyncMailSender` que se retiró— pero
tampoco es lo que ADR-0031 decidió, y hace la petición de registro tan lenta como tarde el
proveedor. Falta crear las cuatro variables de `prod`, con su propia
`MAIL_QUEUE_HANDLER_URL` sobre `api.sendik.co`.

**Y el envío real destapó lo que ninguna suite podía ver: los correos en español salen sin
tildes ni eñes.** No es codificación: están así en el código, en tres archivos —los diez
asuntos de `ResendMailSender`, los cuatro de `ListingMailTexts` y los motivos de rechazo
de `ListingRejectionTexts`, que viajan dentro del correo—. En dos de ellos cambia el
significado: «Tu contrasena cambio» y «Alguien intento registrarse» dejan un sustantivo
donde debía haber un verbo. Queda anotado y sin arreglar aquí.

### Lo que no entra en el cierre

- **Las tres banderas en `prod`.** Siguen apagadas y encenderlas es una decisión
  aparte, después de los textos legales.
- **La frase de la garantía del fabricante en la ficha (RN-067)**, aplazada a la
  tanda legal el 26 de agosto y que arrastran HU-009 y esta línea.
- ~~**Límite de tasa sobre `/api/v1/listings/**`.**~~ **Hecho el 5 de septiembre de
  2026.** El prefijo entra en el interceptor como un cuarto grupo, contado por sujeto
  del token y con **una sola cuenta para todas sus rutas**, no una por ruta: el bucle
  enviar → retirar → enviar recorre dos URI distintas, así que contar por ruta le
  habría dado el cupo entero a cada mitad del ciclo. Entra también la colección
  `POST /api/v1/listings`, porque crear borradores sin cota es el mismo problema.

  Escribirlo destapó un defecto que no era suyo: `sujetoDelToken()` daba por bueno el
  token anónimo. Mientras el único grupo por sujeto fue `/api/v1/users` no se notaba,
  porque ahí nadie entra sin sesión; con las publicaciones sí, porque la ficha pública
  cuelga del mismo prefijo, y todo el catálogo anónimo habría compartido un único cupo
  bajo `anonymousUser`. Arreglado, con la prueba que sí puede verlo.

## Fase 3 — transacción

- Búsqueda y filtros con Typesense.
- Carrito y proceso de compra.
- Pago con Wompi: PSE, Nequi, tarjetas, Bancolombia a la mano y Addi.
- División del pago y retención de la comisión del 5%.
- Cotización de envíos con **Skydropx Colombia**, el agregador (ADR-0034, RN-038).
  Una integración, no una por transportadora.
- **Precio base y costo de envío como cifras separadas**, con el envío a cargo del
  comprador y el total a la vista antes de pagar (RN-076, RN-077).
- **Seguimiento del envío**: guía y eventos visibles para comprador y vendedor, y
  la fecha de entrega tomada del evento de entrega, que es la que arranca los
  plazos del dinero (RN-079).
- Estados del pedido, confirmación de entrega por el comprador, ventana de
  reclamo de 3 días hábiles y liberación del pago (RN-034, RN-051, RN-052).
- Correos transaccionales de cada cambio de estado.
- Reseñas del vendedor.

## Fase 4 — operación y crecimiento

- Disputas y devoluciones: bandeja de reportes, resolución y reintegro. Las
  reglas que gobiernan el reporte existen desde Fase 1 porque el sitio las
  anuncia (RN-050 a RN-058); lo que llega aquí es el flujo que las ejecuta.
- Mensajería entre comprador y vendedor.
- Panel administrativo completo y reportes.
- Aplicación móvil.
- Notificaciones push.

## Decisiones aplazadas

Se documentan aquí para no reabrirlas por accidente:

| Tema | Estado |
|---|---|
| Días hábiles que tarda el desembolso en llegar a la cuenta del vendedor | Se define en Fase 3 con asesoría. La retención sí está definida: hasta que el comprador confirma o vence la ventana (RN-034) |
| Proveedor de envíos | **Decidido el 8 de septiembre de 2026**: Skydropx Colombia como agregador (ADR-0034). Sustituye la integración directa con Envía, Coordinadora e Interrapidísimo, que nunca se llegó a construir. La política de tratamiento de datos ya lo nombraba desde el 6 de septiembre sin que ninguna otra parte del proyecto lo respaldara |
| Quién contrata el transporte | **Decidido el 8 de septiembre de 2026**: el vendedor es el remitente y Sendik emite la guía por su cuenta (RN-078). Se descartó vender el envío en nombre propio, que habría hecho de Sendik proveedor del servicio de transporte ante el consumidor |
| Quién asume la diferencia entre la cotización y el costo real | **Decidido el 8 de septiembre de 2026**: Sendik (RN-077). Recotizar después de pagar no era una opción disponible —artículo 26 de la Ley 1480—, y descontárselo al vendedor deja su ingreso sin predecir |
| Cobertura de envío | **Sin comprobar.** El texto legal anuncia todo el territorio nacional desde el 5 de septiembre y nadie lo ha contrastado con las transportadoras que el agregador habilite (RN-080) |
| Separar la política de devoluciones de los términos y condiciones | Sin decidir. Requiere variable de versión propia |
| Árbol de categorías del catálogo | **Decidido el 24 de agosto de 2026**, en `docs/producto/categorias.md`: seis familias y treinta y una categorías, por tipo de producto y sin eje de género, incluida la familia de tecnología. "Dama" y "Caballero" siguen sin ser categorías del proyecto |
| Facturación electrónica de la comisión ante la DIAN | Se define en Fase 3 |
| Vendedores con figura de empresa | Fuera de alcance por ahora |
| Pago contraentrega | Descartado por ahora |
| Regateo y ofertas | Sin decidir |
| Suscripción o destacados de pago | Sin decidir |
