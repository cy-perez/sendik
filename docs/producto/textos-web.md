# Textos web

Fuente de la que salen las claves de Transloco del sitio informativo. No es un
documento para copiar y pegar en plantillas: **ningún texto visible se escribe en
el código** (`CLAUDE.md`). Cada bloque de aquí lleva su clave; el texto vive en
`frontend/src/i18n/es.json` y `en.json`, y la plantilla solo referencia la clave.

Historias que consume: HU-004 (portada) y HU-005 (las cuatro páginas
informativas). Reglas que respeta: las de `reglas-negocio.md`, citadas donde
importan.

## Cómo leer las marcas

| Marca | Significado |
|---|---|
| `clave.de.transloco` | La clave exacta bajo la que va el texto |
| `{{variable}}` | Sale de configuración, **nunca de la plantilla**: comisión, datos de empresa, plazos |
| **Fase 2** / **Fase 3** | El texto está redactado pero no se publica hasta esa fase, porque describe algo que no existe |
| ⚠️ | Falta un dato o una decisión. La lista completa está al final |

Todo bloque necesita su gemelo en inglés antes de publicarse. Una prueba compara
los dos árboles y falla si alguna clave existe en uno y no en el otro (HU-004).

---

## Decisiones de voz

**Mensaje central.** Sendik reúne la moda nueva y de segunda que hoy se vende
dispersa en redes, con vendedores verificados, publicaciones revisadas y un pago
que no llega al vendedor hasta que el comprador confirma la entrega.

**Promesa.** Seguridad, no precio bajo (`vision.md`). Nada comunica ganga,
liquidación ni descuento.

**Pronombre.** Tú, en todo el sitio, botones y mensajes de error incluidos.

**Voz.** Cercana, sobria, sencilla. Sin signos de exclamación, sin euforia.

**Palabra clave: respaldo.** Es el término del glosario (`Backing`) y es el que
se repite. **No** se dice "compra protegida", "protección", "te protegemos",
"garantía", "seguro", "custodia" ni "escrow": las cuatro últimas describen
figuras financieras que Sendik no ejerce y tienen lectura regulatoria en Colombia
(RN-031). Tampoco se dice "plata": el registro coloquial choca con una promesa
de seguridad.

**Lo que Sendik nunca dice.** "La mejor plataforma", "tu aliado en moda",
"transforma tu clóset".

**Con quién se compara.** El competidor real no es Mercado Libre: es la venta por
Instagram, los grupos de Facebook y los chats de WhatsApp, donde hoy ocurre la
mayor parte de la moda de segunda en Colombia y donde no hay verificación, ni
revisión de lo que se publica, ni retención del pago. Los diferenciadores están
escritos contra ese punto de comparación.

**Frase que no puede escribirse nunca:** "Sendik guarda tu dinero". Sendik no lo
guarda. Lo recauda y lo retiene **la pasarela** (RN-031, RN-033). La forma
correcta es "el pago queda retenido" o "el pago no llega al vendedor hasta que
confirmas".

---

## Mapa del sitio en Fase 1

| # | Ruta | Para quién | A qué pregunta responde |
|---|---|---|---|
| 1 | `/` | Vendedor, sobre todo | ¿Qué es esto y por qué publico aquí? |
| 2 | `/como-funciona` | Ambos, en dos recorridos | ¿Cómo funciona exactamente, de cada lado? |
| 3 | `/sobre-sendik` | Ambos | ¿Quién está detrás? |
| 4 | `/preguntas-frecuentes` | Ambos | ¿Y lo que aún me preocupa? |
| 5 | `/contacto` | Ambos | ¿Cómo los ubico y cómo ejerzo mis derechos? |

Más los tres documentos legales, que tienen su propio mecanismo versionado
(`docs/operacion/textos-legales.md`).

**Navegación principal** (`layout.nav.*`): Cómo funciona · Sobre Sendik ·
Preguntas frecuentes · Contacto. A la derecha, Entrar y Crear cuenta.

Ningún enlace del encabezado lleva a catálogo, categorías ni búsqueda: no
existen (HU-005, criterio 25). No hay menú de Nuevo / De segunda / categorías
hasta Fase 2, y el árbol de categorías todavía no está decidido.

**A quién le habla la portada.** Al vendedor. El sitio no arranca sin inventario
(`vision.md`), y un catálogo vacío quema la primera visita. El comprador tiene su
recorrido completo en `/como-funciona`, y la portada lo convence con las tres
tarjetas de confianza. Es la decisión contraria a la de un marketplace maduro y
se revisa cuando la oferta supere a la demanda.

---

# 1. Portada

`meta.home.title` · `meta.home.description`

**Título:** Sendik
**Descripción:** Compra y vende de forma ágil y segura.

La descripción ya no arrastra el descriptor viejo de marca: adopta el eslogan
aprobado (ADR-0022), que no nombra la categoría y por eso no se queda corto ahora
que el catálogo incluye tecnología.

⚠️ El **título** sigue siendo de marcador de posición. Propuesta, dentro de los
límites de buscador: `Vende moda nueva y de segunda con respaldo | Sendik`. No se
cambia todavía porque nombra solo una de las dos categorías.

## 1.1 Hero — `home.hero.*`

Texto vigente, ya implementado. Cualquier cambio aquí reabre HU-004.

| Clave | Texto |
|---|---|
| `home.hero.title` | Compra y vende de forma ágil y segura |
| `home.hero.body` | El pago queda retenido hasta que confirmas que la prenda llegó como la viste. |
| `home.hero.cta` | Crear cuenta |
| `home.hero.note` | Publicar es gratis. Solo cobramos cuando vendes. |

El botón dice **crear cuenta**, no publicar, porque publicar no existe hasta
Fase 2 y un botón nombra lo que ocurre al pulsarlo (HU-004, criterio 3). Es la
única llamada a la acción de la pantalla, rellena en tinta. El porcentaje no se
escribe: `home.hero.note` lo omite a propósito (RN-026, HU-004 criterio 4).

*Nota de diseño:* la primera pantalla en móvil contiene titular, apoyo y botón.

## 1.2 Cómo funciona en tres pasos — `home.steps.*`

Texto vigente, ya implementado.

| Clave | Título | Texto |
|---|---|---|
| `home.steps.publish` | Publicas tu prenda | Fotos, talla y medidas reales. Revisamos cada publicación antes de que aparezca. |
| `home.steps.sell` | Alguien la compra | El comprador paga y el pago queda retenido mientras la prenda viaja. |
| `home.steps.charge` | Cobras al confirmarse la entrega | Cuando el comprador confirma que recibió la prenda, se libera tu pago. |

Encabezado del bloque: `home.steps.title` → **Cómo funciona**.
Enlace de cierre a `/como-funciona`; no se renderiza mientras la ruta no exista.

## 1.3 Tarjetas de confianza — `home.trust.*`

Texto vigente, ya implementado. Exactamente tres, y cada una apunta a una regla.

| Clave | Título | Texto | Regla |
|---|---|---|---|
| `home.trust.hold` | El pago queda retenido | El dinero no llega al vendedor hasta que confirmas que recibiste la prenda. | RN-034 |
| `home.trust.verified` | Vendedores verificados | Validamos identidad y cuenta bancaria antes de la primera publicación. | RN-011 |
| `home.trust.moderated` | Publicaciones revisadas | Cada publicación pasa por revisión antes de aparecer. Sin excepciones. | RN-015 |

Encabezado: `home.trust.title` → **Por qué es seguro**.

Ninguna tarjeta enuncia plazos: ni los días de la ventana de reclamo, ni tiempos
de entrega, ni de desembolso (HU-004, criterio 9). Un plazo suelto en portada se
lee como promesa y se vuelve exigible sin el contexto que lo condiciona; su sitio
es `/como-funciona` y los términos.

## 1.4 Pie de página — `layout.footer.*`

| Clave | Texto |
|---|---|
| `layout.footer.tagline` | Compra y vende de forma ágil y segura. |
| `layout.footer.company.taxId` | NIT — es la única etiqueta traducible; el número, la razón social y la dirección son valores de `AppConfig` y se pintan tal cual |
| `layout.footer.contactLabel` | Contacto |
| `layout.footer.legalLabel` | Documentos legales |

Enlaces: Cómo funciona · Sobre Sendik · Preguntas frecuentes · Contacto ·
Términos y condiciones · Política de tratamiento de datos · Política de cookies.

Razón social, NIT y dirección salen de configuración y se omiten si faltan;
nunca se escriben en la plantilla (HU-004, criterios 11 y 12).

© `{{year}}` Sendik.

**Fase 3.** Cuando existan los pagos y los envíos, el pie suma dos filas de
logos: medios de pago (PSE · Nequi · Bancolombia a la mano · Tarjetas · Addi) y
transportadoras. En Colombia esos logos hacen más por la confianza que cualquier
sello de "sitio seguro". Hoy no van: anunciarían una funcionalidad que no existe.

La fila de transportadoras **no se puede escribir todavía**, y no es olvido. Desde
RN-038 Sendik no contrata transportadoras: cotiza con Skydropx Colombia, que decide
cuáles habilita. Los logos que van ahí son los de las que el agregador tenga
habilitadas el día que se encienda, y esa lista **no la sabe nadie aún**. Poner las
tres de antes —Envía, Coordinadora, Interrapidísimo— sería anunciar transportadoras
que quizá no entreguen ni un paquete.

---

# 2. Cómo funciona — `/como-funciona`

`meta.howItWorks.title` → **Cómo funciona**
`meta.howItWorks.description` → Cómo se compra y cómo se vende en Sendik:
verificación del vendedor, publicaciones revisadas, pago retenido hasta que el
comprador confirma la entrega y qué hacer si lo recibido no corresponde.

**H1** (`howItWorks.title`): Cómo funciona Sendik

**Entrada** (`howItWorks.intro`): Comprar o vender moda por internet en Colombia
suele significar transferirle a un desconocido y esperar, o despachar y esperar.
Sendik se pone en medio con reglas iguales para los dos lados.

Los dos recorridos van uno tras otro, ambos visibles y rotulados. Ninguno queda
detrás de una pestaña ni de un acordeón que un buscador no pueda seguir
(HU-005, criterio 5).

## 2.1 Si vas a comprar — `howItWorks.buyer.*`

**H2:** Si vas a comprar

| # | Clave | Título | Texto |
|---|---|---|---|
| 1 | `.browse` | Eliges una prenda | Cada publicación pasó por revisión antes de aparecer y muestra la prenda desde ocho ángulos, con su talla, sus medidas reales en centímetros y su condición declarada. Una prenda, una publicación: lo que ves es la pieza exacta que recibes. |
| 2 | `.pay` | Pagas el producto más el envío | Ves el precio del producto, el costo del envío y el total antes de confirmar. **Lo que ves del envío es lo que pagas.** La comisión de Sendik no se te suma: la asume el vendedor. |
| 3 | `.hold` | El pago queda retenido | Tu pago lo recauda y lo retiene la pasarela. No llega al vendedor mientras la prenda viaja. |
| 4 | `.confirm` | Confirmas y se libera | Cuando recibes la prenda y confirmas que corresponde a lo publicado, el pago se libera. Si no confirmas ni reportas nada dentro de la ventana de reclamo, se da por confirmada. |

Reglas: RN-016 a RN-021 (paso 1), RN-027, RN-076 y RN-077 (paso 2), RN-031 y
RN-033 (paso 3), RN-034 y RN-052 (paso 4).

El paso 2 **ya no dice «aproximado»**, y el cambio es del 8 de septiembre de 2026.
Lo decía porque RN-038 obligaba a rotularlo así cuando la cotización era
orientativa; desde RN-077 la cifra que se muestra es la que se cobra, y llamarla
aproximada sería lo contrario de lo que se promete. Sigue sin anunciar plazo de
entrega: RN-080 fija uno estimado por envío, y un plazo estimado no se puede
escribir en un texto fijo. El paso 3 dice "la pasarela", no "Sendik".

### Si lo que recibes no es lo publicado — `howItWorks.buyer.claim.*`

**H3:** Si lo que recibes no es lo publicado

**Texto:** Tienes `{{claimWindowDays}}` días hábiles desde la entrega para
reportarlo desde tu pedido, con fotos de lo que recibiste. Mientras el reporte
esté abierto el pago sigue retenido, así que el reintegro sale de ese dinero y no
depende de que el vendedor colabore. Si el reporte se acepta, se te devuelve el
valor de la prenda y el envío que pagaste, y el flete de regreso lo asume el
vendedor.

**Ojo:** confirmar la entrega cierra la ventana. Si algo no cuadra, repórtalo
**antes** de confirmar.

**H3:** Qué no entra en el reporte

**Texto:** Que la talla no siente como esperabas o que el color se vea distinto
en pantalla no es un producto no conforme, siempre que la prenda corresponda a lo
publicado. Por eso cada publicación lleva medidas reales en centímetros: mídete
una prenda que te quede bien y compárala antes de comprar.

Aparte de esto existe el derecho de retracto que fija la ley colombiana, con sus
propios plazos y excepciones. Está en los
[Términos y condiciones](/terminos-y-condiciones).

Reglas: RN-050 a RN-058. La cifra de la ventana sale de configuración. Los plazos
del retracto **no se escriben aquí**: viven en el documento legal versionado y
esta página enlaza (RN-057).

*Nota de redacción:* decir qué **no** cubre el respaldo es lo que hace creíble lo
que sí cubre. Una garantía que suena ilimitada no se la cree nadie y genera
reclamos que no se pueden atender.

## 2.2 Si vas a vender — `howItWorks.seller.*`

**H2:** Si vas a vender

| # | Clave | Título | Texto |
|---|---|---|---|
| 1 | `.verify` | Te verificas | Validas tu documento de identidad, envías una selfie y registras una cuenta bancaria a tu nombre. Es gratis y se hace una sola vez. Solo personas naturales mayores de edad pueden vender. |
| 2 | `.publish` | Publicas | El asistente de captura te guía para tomar la prenda desde ocho ángulos y recorta todo a la misma proporción, así que tus fotos se ven como las del resto del catálogo. Declaras talla, medidas en centímetros y condición. Publicar no cuesta. |
| 3 | `.review` | Revisamos | Cada publicación pasa por revisión antes de aparecer. Si algo falta, te decimos qué corregir y la reenvías. |
| 4 | `.ship` | Despachas | Cuando alguien compra, el pago ya está retenido. Despachas con la transportadora y registras la guía. |
| 5 | `.charge` | Cobras | Recibes el valor de la prenda menos la comisión del `{{commissionRate}}`, en la cuenta bancaria que verificaste. Se libera cuando el comprador confirma la entrega o cuando vence la ventana de reclamo. |

Reglas: RN-008 y RN-010 a RN-012 (paso 1), RN-016 a RN-021 y RN-025 (paso 2),
RN-015 y RN-022 (paso 3), RN-034 y RN-052 (paso 5).

El paso 5 dice "en la cuenta bancaria que verificaste", no "a tu saldo": Sendik
no tiene billetera ni saldo interno (glosario). Y **no dice en cuántos días
hábiles llega**: ese plazo se decide en Fase 3 y hasta entonces no se escribe en
ninguna parte (HU-005, criterio 17).

### Qué asumes como vendedor — `howItWorks.seller.duties.*`

**H3:** Lo que asumes

- Describir la condición real, incluidas las manchas, el desgaste y las costuras
  sueltas. Las medidas en centímetros son obligatorias: son la causa número uno
  de reclamo en moda de segunda.
- Si la prenda no corresponde a lo publicado o llega con un daño que no
  declaraste, asumes el flete de regreso y el comprador recibe su reintegro.
- No se publican réplicas ni falsificaciones, ropa interior usada, productos que
  no sean moda ni tecnología, ni prendas con daño no declarado.
- La tecnología se vende **solo nueva**. Lo usado se vende únicamente en moda.

Reglas: RN-021, RN-024, RN-055, RN-064.

*Nota de redacción:* esta sección parece un obstáculo y es lo contrario: atrae al
vendedor serio y ahuyenta al que genera los reclamos. Un marketplace de segunda
se muere por los malos vendedores, no por los pocos vendedores.

### Por qué aquí y no en un grupo de Facebook — `howItWorks.seller.why.*`

- **El comprador ya pagó.** No despachas contra una promesa de transferencia.
- **No cuesta publicar.** Puedes tener prendas publicadas sin vender y no pagas
  nada. Solo se cobra cuando vendes.
- **Tus fotos se ven bien.** El asistente de captura fuerza el encuadre y la
  proporción, y con esas mismas tomas se arma la vista giratoria.
- **Cobras a tu cuenta.** El desembolso llega a la cuenta bancaria que
  verificaste.

**Cierre de página** (`howItWorks.cta`): botón **Crear cuenta**. Es la única
llamada a la acción de la página, rellena en tinta (HU-005, Diseño).

---

# 3. Sobre Sendik — `/sobre-sendik`

`meta.about.title` → **Qué es Sendik**
`meta.about.description` → Sendik reúne la moda nueva y de segunda que hoy se
vende dispersa en redes, con vendedores verificados, publicaciones revisadas y el
pago retenido hasta que el comprador confirma la entrega.

**H1** (`about.title`): Por qué existe Sendik

**Texto** (`about.body`):

En Colombia se compra y se vende muchísima ropa de segunda, pero ocurre en grupos
de Facebook, en historias de Instagram y en chats de WhatsApp. El comprador
transfiere y espera. El vendedor despacha y espera. Los dos asumen un riesgo que
nadie está cubriendo.

Sendik reúne esa oferta dispersa en un solo catálogo y pone cuatro cosas que en
un chat no existen: vendedores verificados con documento de identidad, cada
publicación revisada antes de aparecer, fotos que muestran la prenda desde ocho
ángulos, y un pago que no llega al vendedor hasta que el comprador confirma que
recibió lo que se publicó.

No fabricamos ropa ni la revendemos, y no tocamos el dinero: lo recauda y lo
retiene la pasarela de pagos. Somos el lugar donde una persona le compra a otra
con reglas claras para las dos.

**Datos de empresa** (`about.company.*`): razón social `{{companyName}}`, NIT
`{{companyTaxId}}`, dirección `{{companyAddress}}`. De configuración; se omiten
si faltan (HU-005, criterio 10).

⚠️ **Falta lo que hace creíble esta página: quién está detrás.** Sendik es una
marca nueva sin trayectoria, y en ese caso la confianza viene de las personas.
Dos o tres líneas con nombre, ciudad y por qué se montó valen más que cualquier
párrafo de misión.

*Nota de diseño:* nada de fotos de banco de imágenes con modelos sonriendo. Foto
real de quien está detrás, o ninguna foto.

*Nota de redacción:* esta página **no lleva** misión, visión ni valores. No los
lee nadie y en una marca nueva suenan a relleno. Tampoco comunica ganga,
liquidación ni precio bajo (HU-005, criterio 11).

---

# 4. Preguntas frecuentes — `/preguntas-frecuentes`

`meta.faq.title` → **Preguntas frecuentes**
`meta.faq.description` → Cómo comprar y vender en Sendik: verificación de
vendedores, publicaciones revisadas, pago retenido, comisión, medios de pago y
qué hacer si lo recibido no corresponde.

**H1** (`faq.title`): Preguntas frecuentes

Cada pregunta es un `details`/`summary` con identificador propio, para poder
enviar una respuesta por enlace. El texto está en el documento aunque esté
plegado (HU-005, criterios 13, 14 y 18).

## 4.1 Si vas a comprar — `faq.buyer.*`

**¿Cómo sé que el vendedor es real?** (`.real`)
Todos los vendedores validan su documento de identidad, una selfie y una cuenta
bancaria a su nombre antes de poder publicar. No se puede vender de forma
anónima. (RN-011, RN-012)

**¿A quién le pago?** (`.whoGetsPaid`)
El pago lo recauda y lo retiene la pasarela, no Sendik. El vendedor no lo recibe
mientras la prenda viaja. (RN-031, RN-033)

**¿Cuándo se le paga al vendedor?** (`.release`)
Cuando confirmas que recibiste la prenda y corresponde a lo publicado. Si no
confirmas ni reportas nada dentro de la ventana de reclamo, se da por confirmada
y el pago se libera. (RN-034, RN-052)

**¿Y si me llega algo distinto a lo publicado?** (`.claim`)
Mientras no confirmes la entrega el pago sigue retenido. Tienes
`{{claimWindowDays}}` días hábiles desde la entrega para reportarlo desde tu
pedido, con fotos de lo que recibiste. El reintegro sale de ese dinero y no
depende de que el vendedor colabore: si el reporte se acepta, recibes el valor
de la prenda y el envío que pagaste, y el flete de regreso lo asume el vendedor.
Confirmar la entrega cierra la ventana: si algo no cuadra, repórtalo antes de
confirmar. (RN-050 a RN-056)

El orden de los cuatro hechos es el que fija el criterio 16 de HU-005 y no se
reordena al corregir la redacción: primero que el dinero no se ha ido a ninguna
parte, después el plazo. Hay una prueba que falla si se altera.

**¿La talla no me quedó, puedo devolverla?** (`.fit`)
Que la talla no siente o que el color se vea distinto en pantalla no es un
producto no conforme. Por eso cada publicación lleva medidas reales en
centímetros. Aparte existe el derecho de retracto que fija la ley, con sus plazos
y condiciones, en los [Términos y condiciones](/terminos-y-condiciones).
(RN-050, RN-057)

**¿Cuánto cuesta el envío?** (`.shippingCost`)
Depende del destino, del peso y del tamaño. Antes de pagar ves las opciones de
envío con su costo y su plazo estimado, eliges una, y **el valor que elegiste es
el que se te cobra**: no se recalcula después. El envío lo paga el comprador,
aparte del precio del producto, y la comisión de Sendik no se te suma.
(RN-027, RN-039, RN-076, RN-077)

**¿Cómo puedo pagar?** (`.payment`)
Con PSE, Nequi, Bancolombia a la mano y tarjeta débito o crédito a través de
Wompi, o a cuotas con Addi. No hay pago contraentrega. (RN-032)

**¿Puedo comprarle a dos vendedores a la vez?** (`.multiSeller`)
Sí, pero se generan dos pedidos, uno por vendedor, cada uno con su envío y su
propio recorrido. (RN-042)

## 4.2 Si vas a vender — `faq.seller.*`

**¿Cuánto cobran?** (`.commission`)
El `{{commissionRate}}` sobre el valor de la prenda, a cargo del vendedor y solo
cuando vendes. El envío no entra en la base de cálculo. Publicar es gratis.
(RN-026)

**¿Cuándo me pagan?** (`.payout`)
El pago se libera cuando el comprador confirma la entrega, o cuando vence la
ventana de reclamo sin que confirme ni reporte. Se desembolsa a la cuenta
bancaria que verificaste, no a un saldo dentro de la plataforma. (RN-034,
RN-052)

**¿Qué necesito para vender?** (`.requirements`)
Tu documento de identidad, una selfie y una cuenta bancaria a tu nombre. Solo
personas naturales mayores de edad, y la cuenta tiene que ser del titular del
documento. (RN-008, RN-010, RN-011, RN-012)

**¿Qué fotos me piden?** (`.photos`)
Ocho tomas de la prenda a 45 grados, que el asistente de captura te guía a tomar
y recorta a la misma proporción. De ahí salen las cuatro vistas obligatorias
—frente, los dos lados y espalda— y la vista giratoria. (RN-016 a RN-019)

**¿Puedo vender productos nuevos?** (`.new`)
Sí. Sendik tiene catálogo de nuevo y de segunda, y cada publicación indica su
condición. (RN-024 para lo que no se puede publicar)

**¿Puedo vender tecnología?** (`.tech`)
Sí, y solo nueva: celulares, computadores, televisores y demás se publican sin
uso. Lo de segunda se vende únicamente en moda. Si el producto está sellado, se
publica con cuatro fotos del empaque y puedes añadir imágenes de referencia del
fabricante, que salen siempre marcadas como tales. (RN-064, RN-065, RN-066)

**¿Qué no puedo publicar?** (`.forbidden`)
Réplicas o falsificaciones, ropa interior usada, tecnología usada, productos que
no sean moda ni tecnología, y prendas con daño que no declares. (RN-024, RN-064)

**¿Por qué revisan mi publicación?** (`.moderation`)
Porque un catálogo limpio es lo que hace que valga la pena comprar aquí. Toda
publicación pasa por revisión, sin excepciones. Si se rechaza, se te dice el
motivo y qué corregir, y la puedes reenviar. (RN-015, RN-022)

**¿Puedo publicar dos prendas iguales en una sola publicación?** (`.stock`)
No. Una prenda, una publicación. Si tienes dos iguales, son dos publicaciones.
(RN-025)

**¿Qué pasa si el comprador reclama?** (`.dispute`)
Si la prenda no corresponde a lo publicado o llegó con un daño que no declaraste,
asumes el flete de regreso y el comprador recibe su reintegro. Describir bien la
condición real es la mejor forma de que no ocurra. (RN-055)

---

# 5. Contacto — `/contacto`

`meta.contact.title` → **Contacto**
`meta.contact.description` → Cómo comunicarte con Sendik y cómo ejercer tus
derechos sobre tus datos personales.

**H1** (`contact.title`): Escríbenos

**Texto** (`contact.intro`): Si tienes un problema con un pedido, escríbenos
desde el correo con el que compraste y con el número de pedido a la mano. Así
respondemos más rápido.

**Correo** (`contact.email`): `{{supportEmail}}`, como enlace `mailto:` y como
texto legible. Si no está configurado, la sección no se pinta y el servidor lo
registra como aviso: sin ese canal se incumple una obligación legal (HU-005,
criterio 19 y casos borde).

**Si ya tienes cuenta** (`contact.account`): Tu nombre, ciudad, teléfono y correo
los cambias tú desde [Mi cuenta](/mi-cuenta), sin escribirnos.

**Tus datos personales** (`contact.rights`):
`.channel`: Por este mismo canal ejerces tus derechos a conocer, actualizar,
rectificar y suprimir tus datos, y a revocar la autorización que nos diste.
`.deadlines`: Respondemos una consulta en diez días hábiles y un reclamo en
quince, prorrogables una vez.
Los detalles están en la
[política de tratamiento de datos](/politica-de-tratamiento-de-datos).

Van en dos claves y no en una porque la primera frase afirma que hay un canal.
Sin `SUPPORT_EMAIL` configurado esa sección no se pinta, y la frase quedaba
señalando a un canal inexistente: se omite. Los plazos y el enlace a la política
se enuncian siempre.

Fuente: `docs/operacion/datos-personales.md`, sección Derechos del titular.

**Canales adicionales** (`contact.channels.*`): solo se muestran si están
configurados; si no, la sección no aparece. Ninguno se escribe en la plantilla
(HU-005, criterio 22).

⚠️ Hoy no existe ninguna variable de configuración para WhatsApp, horario de
atención ni redes. Si se quieren mostrar, hay que agregarlas a
`docs/operacion/configuracion.md`. No se inventan aquí.

## Formulario de contacto — fuera de alcance

HU-005 lo excluye: implica endpoint, antispam y aviso de privacidad propio. El
texto queda escrito para cuando se decida, y hasta entonces no se implementa.

- Campos: Nombre · Correo · Número de pedido *(opcional)* · Mensaje
- Junto al botón: ⚠️ *sin plazo de respuesta hasta que exista uno comprometido*
- Botón: Enviar mi mensaje
- Casilla de autorización, **sin premarcar** y obligatoria:

> ☐ Autorizo a `{{companyName}}`, NIT `{{companyTaxId}}`, a tratar mis datos
> personales para responder mi solicitud, conforme a su
> [política de tratamiento de datos](/politica-de-tratamiento-de-datos).

El enlace va **fuera** de la etiqueta y abre en pestaña nueva: dentro de la
etiqueta, pulsarlo marcaría la casilla además de abrir el documento, y se
aceptaría sin haber leído con un solo gesto (`datos-personales.md`). La política
tiene que estar publicada antes de activar el formulario, y la redacción la
ajusta un abogado.

---

# Microcopy transversal

## Botones

Un verbo por acción, en todo el sitio.

| Lugar | Texto | Fase |
|---|---|---|
| Hero de portada y cierre de páginas | Crear cuenta | 1 |
| Encabezado | Entrar | 1 |
| Vendedor, una vez exista publicar | Publicar mi primera prenda | 2 |
| Ficha de producto | Comprar | 3 |
| Carrito | Comprar y pagar | 3 |
| Cotizador de envío | Calcular mi envío | 3 |
| Pedido recibido | Confirmar que recibí la prenda | 3 |
| Pedido con problema | Reportar un problema | 4 |

Nunca: "Enviar", "Más información", "Haz clic aquí", "Adquirir", "Ordenar",
"Solicitar".

## Mensajes — Fase 1

**Búsqueda sin resultados** ⚠️ *no aplica hasta Fase 3*

**Error de campo** (`form.errors.*`): Falta tu correo para poder responderte.
Nunca "Campo inválido".

**Página 404** (`notFound.*`): Esta página no existe o la movimos. Vuelve al
inicio o escríbenos si buscabas algo puntual.

## Verificación de vendedor — Fase 2

El texto de HU-002. Las claves van bajo `sellerVerification.*`, y los mensajes de
error no están aquí: salen del código de error, en `errors.byCode.*`.

**Encabezado** (`sellerVerification.intro.*`):
`.title`: Verifícate para vender
`.body`: Necesitamos confirmar quién eres antes de que publiques. Son tres pasos y
puedes salir y volver cuando quieras: guardamos lo que ya hiciste.
`.reviewTime`: Revisamos tu solicitud en máximo `{{días}}` días hábiles.

**Los tres pasos** (`sellerVerification.steps.*`). Cada uno con su estado:

| Clave | Texto |
|---|---|
| `.document.title` | Tu documento de identidad |
| `.document.body` | Una foto del frente y otra del reverso. Que se lea todo, sin brillos ni dedos encima. |
| `.selfie.title` | Una foto de tu cara |
| `.selfie.body` | Se toma en el momento con tu cámara. No se puede subir desde la galería. |
| `.bank.title` | Dónde recibes tu dinero |
| `.bank.body` | La cuenta tiene que estar a tu nombre, el mismo del documento. |
| `.done` | Listo |
| `.pending` | Falta |

**Estados de la solicitud** (`sellerVerification.status.*`). La clave es el valor del
estado, en mayúsculas, porque la plantilla la compone con el dato que llega:
`.IN_PROGRESS`: Te falta algo por entregar.
`.PENDING_REVIEW`: Estamos revisando tu solicitud.
`.VERIFIED`: Ya eres vendedor verificado.
`.REJECTED`: No pudimos verificarte.
`.REVOKED`: Tu verificación se revocó.

**Enviar** (`sellerVerification.submit.*`):
`.action`: Enviar para revisión
`.blocked`: Completa los tres pasos para poder enviar.
`.attempts`: Te quedan `{{intentos}}` intentos.
`.exhausted`: Usaste tus tres intentos. Escríbenos y lo revisamos a mano.

**Rechazo** (`sellerVerification.rejected.*`):
`.reasonLabel`: Motivo
`.noteLabel`: Nota de quien revisó
`.retry`: Corregir y volver a enviar

Los motivos son lista cerrada y se traducen por su código
(`sellerVerification.reasons.*`): fotos ilegibles → «Las fotos no se pueden leer»;
documento vencido → «El documento está vencido»; titular distinto → «El titular de la
cuenta no coincide con tu documento»; documento ya verificado → «Ese documento ya está
verificado en otra cuenta»; requisitos → «No cumples los requisitos para vender».

**Lo que no se escribe aquí, y por qué.** No hay texto que prometa cuándo estará
aprobada más allá del plazo configurable, ni que anuncie qué se puede hacer al
verificarse: publicar llega con su propia historia y anunciarlo antes deja un enlace a
una ruta que no existe, que es lo que HU-004 y HU-005 prohíben.

## Bandeja del moderador — Fase 2

El texto de HU-006. Las claves van bajo `verificationReview.*`.

**Es la primera pantalla interna del sitio**, y eso cambia el tono: quien la usa
trabaja aquí, revisa muchas al día y no necesita que se le explique el producto. El
texto es corto y dice qué hace cada cosa. Nada de «¡Listo!» ni de acompañamiento.

**La bandeja** (`verificationReview.inbox.*`):
`.title`: Verificaciones pendientes
`.waitingSince`: Espera desde hace `{{tiempo}}`
`.attempt`: Intento `{{intento}}` de 3
`.noName`: Sin nombre todavía — cuando la solicitud aún no tiene documento entregado, que
es el único caso en que llega sin nombre.
`.empty.title`: No hay nada por revisar
`.empty.body`: Cuando alguien envíe su solicitud, aparece aquí.
`.error.title`: No pudimos cargar la bandeja
`.error.retry`: Reintentar

**El detalle** (`verificationReview.detail.*`):

| Clave | Texto |
|---|---|
| `.documentSection` | Documento de identidad |
| `.bankSection` | Cuenta bancaria |
| `.holder` | Titular |
| `.lastFour` | Termina en `{{dígitos}}` |
| `.documentType` | Tipo de documento |
| `.bank` | Entidad |
| `.accountType` | Tipo de cuenta |
| `.holderMismatch` | El titular de la cuenta no coincide con el del documento |
| `.back` | Volver a la bandeja |

`.holderMismatch` es el criterio 7 y **no es decorativo**: es la comprobación de
RN-012 dicha en palabras, para que no dependa de que alguien note un color.

**Las imágenes** (`verificationReview.images.*`):
`.front`: Frente del documento
`.back`: Reverso del documento
`.selfie`: Selfie
`.reveal`: Ver
`.missing`: Esta imagen no está disponible
`.notice`: Cada vez que abres una imagen queda registrado que la viste.

`.notice` se muestra siempre y no es una advertencia legal de relleno: la bitácora
existe (RN-046) y quien revisa tiene derecho a saber que también se le registra a él.

**Decidir** (`verificationReview.decision.*`):
`.approve`: Aprobar
`.reject`: Rechazar
`.reasonLabel`: Motivo del rechazo
`.reasonPlaceholder`: Elige un motivo
`.noteLabel`: Nota para la persona (opcional)
`.noteHint`: La lee quien envió la solicitud. No escribas información de terceros ni
datos de procesos judiciales.
`.confirmApprove`: ¿Aprobar esta verificación? La persona queda como vendedora
verificada y recibe un correo.
`.confirmReject`: ¿Rechazar esta verificación? La persona recibe un correo con el
motivo y podrá corregir si le quedan intentos.
`.confirm`: Confirmar
`.cancel`: Cancelar
`.approved`: Verificación aprobada
`.rejected`: Verificación rechazada
`.alreadyResolved`: Otra persona ya resolvió esta solicitud.
`.ownApplication`: Esta solicitud es tuya. La revisa otra persona.

`.ownApplication` es el criterio 12 y RN-060, dicho **antes** de que se pulse nada. El
servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse después de
pulsar, con un correo ya prometido, no hace falta. Es también el único sitio donde la
pantalla sabe de quién es una solicitud, y solo eso: si es tuya o no.

`.noteHint` va donde se escribe la nota y no en una ayuda escondida: es la única
barrera que tiene esa regla, porque el campo es texto libre y ninguna validación
puede imponerla.

`.alreadyResolved` es el criterio 11. Se dice qué pasó, no «error inesperado».

**Los motivos de rechazo** se reutilizan de `sellerVerification.reasons.*` **solo si
sirven tal cual**, y no sirven: están escritos para quien recibe el rechazo, en
segunda persona. El moderador elige de una lista y necesita etiquetas cortas, así que
van aparte en `verificationReview.reasons.*`:

| Código | Texto |
|---|---|
| `ILLEGIBLE_PHOTOS` | Fotos ilegibles |
| `EXPIRED_DOCUMENT` | Documento vencido |
| `HOLDER_MISMATCH` | Titular no coincide |
| `DOCUMENT_ALREADY_VERIFIED` | Documento ya verificado en otra cuenta |
| `REQUIREMENTS_NOT_MET` | No cumple los requisitos |

**Sin acceso** (`verificationReview.forbidden.*`). Criterio 2: quien no es moderador
no puede enterarse de que esta pantalla existe, así que **no hay texto propio**. Se
reutiliza la página 404 (`notFound.*`), y esto es una decisión de seguridad, no un
ahorro de trabajo: un «no tienes permiso» confirma que hay algo detrás.

**Lo que no se escribe aquí, y por qué.** No hay texto de revocación: el endpoint
existe pero la acción quedó fuera de HU-006 porque no hay forma de llegar a una
verificación ya aprobada desde la interfaz. Tampoco hay texto para conceder el rol de
moderador: eso sigue siendo un `INSERT` a mano hasta el panel administrativo de la
Fase 4.

## Publicación de producto — Fase 2

El texto de HU-007, del lado del vendedor. Las claves van bajo `listing.*`. Los
mensajes de error no están aquí: salen del código de error, en `errors.byCode.*`,
y los nueve códigos `CATALOG_*` están al final de esta sección.

**Detrás de `FEATURE_PUBLISHING`, hoy apagada.** Mientras lo esté, ninguna de estas
claves se muestra y la entrada a publicar no aparece en ningún menú ni ruta
(criterio 3).

Rutas, con el mismo criterio que las de Fase 1 —en español, en minúscula y con
guion—: `/publicar` para el formulario y `/mis-publicaciones` para el listado
propio.

### Entrada y encabezado — `listing.intro.*`

`.title`: Publica tu producto
`.body`: Cuéntanos qué vendes y muéstralo bien. Puedes salir a la mitad y volver
cuando quieras: guardamos lo que llevas.
`.notVerified`: Verifícate como vendedor para poder publicar.
`.notVerifiedAction`: Empezar mi verificación

La última pareja es lo que ve quien no tiene el sello (RN-011). Es la única
promesa cruzada entre las dos historias, y va en este sentido y no en el otro:
desde la verificación **no** se anuncia que luego se podrá publicar, porque esa
pantalla existía antes que esta.

### Los datos del producto — `listing.form.*`

| Clave | Etiqueta | Ayuda |
|---|---|---|
| `.category` | Categoría | Elige dónde encaja. De ella dependen las medidas que te pedimos. |
| `.title` | Título | Qué es, en pocas palabras. Máximo 120 caracteres. |
| `.description` | Descripción | Cuenta el estado real, incluido lo que no está perfecto. |
| `.brand` | Marca | Opcional. |
| `.condition` | Condición | — |
| `.size` | Talla | — |
| `.measurements` | Medidas | En centímetros, sobre la prenda estirada. |
| `.color` | Color | El que más se ve. |
| `.price` | Precio | En pesos, sin centavos. |
| `.shipping` | Envío | Peso y medidas de la caja con el producto dentro. |

`.optional`: Opcional
`.saved`: Guardado
`.saving`: Guardando…

**Condición** (`listing.condition.*`), las cuatro del glosario y ninguna más:
`.NEW`: Nuevo · `.LIKE_NEW`: Como nuevo · `.GOOD`: Buen estado ·
`.WITH_FLAWS`: Con detalles.

`.usedNotAllowed`: En esta categoría solo se publica lo nuevo.

Ese último aparece cuando la categoría no admite lo usado (RN-064). El formulario
esconde las otras tres, y el mensaje explica por qué: sin él, quien viene de
publicar ropa ve tres opciones menos y no sabe si es un error.

**Sistema de talla** (`listing.sizeSystem.*`):
`.ALPHA`: Letra (XS a XXL) · `.NUMERIC_CO`: Número (talla colombiana) ·
`.WAIST_INCHES`: Cintura en pulgadas · `.FOOTWEAR_CO`: Calzado (talla colombiana) ·
`.ONE_SIZE`: Talla única.

**Medidas** (`listing.measurement.*`). Cuáles se piden lo decide el grupo de medida
de la categoría, no el formulario:
`.CHEST`: Pecho · `.WAIST`: Cintura · `.HIP`: Cadera · `.RISE`: Tiro ·
`.SHOULDERS`: Hombros · `.SLEEVE`: Manga · `.LENGTH`: Largo ·
`.INSOLE`: Plantilla · `.HEIGHT`: Alto · `.WIDTH`: Ancho · `.DEPTH`: Fondo.

**Color** (`listing.color.*`), lista cerrada de quince:
`.BLACK`: Negro · `.WHITE`: Blanco · `.GRAY`: Gris · `.BEIGE`: Beige ·
`.BROWN`: Café · `.RED`: Rojo · `.PINK`: Rosado · `.ORANGE`: Naranja ·
`.YELLOW`: Amarillo · `.GREEN`: Verde · `.BLUE`: Azul · `.PURPLE`: Morado ·
`.GOLD`: Dorado · `.SILVER`: Plateado · `.MULTICOLOR`: Multicolor.

**Envío** (`listing.shipping.*`):
`.weight`: Peso en gramos · `.length`: Largo · `.width`: Ancho · `.height`: Alto ·
`.help`: Mide la caja con el producto dentro, no el producto suelto.

### Las tomas — `listing.shots.*`

`.title`: Las fotos
`.body`: Ocho tomas girando el producto, una cada 45 grados. Son las que dejan ver
lo que una sola foto esconde.
`.sealedBody`: Cuatro tomas del empaque cerrado: frente, lado, atrás y el otro lado.
`.position`: Toma `{{n}}` de `{{total}}`
`.canonical`: Esta no puede faltar
`.replace`: Reemplazar
`.remove`: Quitar
`.requirements`: Vertical, mínimo 900 × 1200 píxeles.
`.fromGallery`: Elegir de la galería

Las cuatro canónicas —0, 90, 180 y 270 grados— se rotulan aparte porque son las
únicas obligatorias por sí mismas (RN-016, RN-017):
`.front`: Frente · `.side`: Lado · `.back`: Atrás · `.otherSide`: El otro lado.

**Lo que no se dice aquí.** Nada sobre el asistente de captura, el nivelador ni el
recorte: eso es HU-003 y todavía no existe. Mientras tanto la única vía es la
galería, y el texto no promete otra.

### Solo en tecnología — `listing.tech.*`

`.sealed`: Está sellado, sin abrir
`.sealedHelp`: Si lo declaras sellado te pedimos cuatro tomas del empaque en vez de
ocho, y puedes agregar imágenes del fabricante.
`.warranty`: Meses de garantía del fabricante
`.reference`: Imagen de referencia
`.referenceHelp`: Del fabricante, para mostrar el producto por dentro. Nunca cuenta
como una de tus tomas: sin fotos reales no publicamos.

~~⚠️ **Cómo se enuncia la garantía en la ficha se redacta al final del proyecto,
junto con los textos legales** (decisión del 26 de agosto de 2026).~~ **Escrito el 8
de septiembre de 2026**, en esa tanda, que es lo que la decisión pedía. Vive en «La garantía del fabricante — `catalog.detail.warranty.*`», dentro
de «Catálogo público — Fase 2», y se apoya en el numeral 15.2 de los términos
publicados en vez de inventar una frase nueva.

Lo de aquí arriba es el rótulo del campo del formulario, que es otra cosa: nombra
el dato, no promete nada. **El formulario nunca se bloqueó por esto**; la ficha de
producto, que es otra historia, sí lo estuvo, y ya no.

⚠️ **El rótulo de la imagen de referencia en la ficha y en el carrusel tampoco.**
`.reference` es del formulario. RN-066 exige rotularlas también donde se ven, y eso
llega con la ficha de producto, que es otra historia.

### Enviar a revisión — `listing.submit.*`

`.action`: Enviar a revisión
`.body`: Un moderador la revisa antes de que se vea en Sendik.
`.incomplete`: Te falta completar algo antes de enviar.
`.shotsIncomplete`: Te faltan tomas. Necesitamos `{{exigidas}}` y llevas `{{presentes}}`.
`.sent`: Enviada a revisión
`.withdraw`: Retirar de revisión
`.withdrawHelp`: Puedes retirarla mientras nadie la haya revisado.

`.reviewTime`: Te respondemos en máximo `{{dias}}` días hábiles.

**Decidido el 26 de agosto de 2026: dos días hábiles**, y por eso este texto ya
existe. El valor llega por `LISTING_REVIEW_DAYS`, nunca escrito en la frase: en
Colombia lo anunciado es exigible, así que un plazo quemado en un archivo de
traducción es una promesa que no se puede corregir sin desplegar.

Es una variable propia y no la de la verificación de vendedor, aunque hoy las dos
valgan dos: son dos promesas distintas a dos personas en dos momentos distintos, y
atarlas obligaría a mover las dos para cambiar una.

### Estados — `listing.status.*`

La clave es el valor del estado, en mayúsculas, porque la plantilla la compone con
el dato que llega:

| Clave | Etiqueta | Explicación (`listing.statusHelp.*`) |
|---|---|---|
| `.DRAFT` | Borrador | Solo la ves tú. Envíala a revisión cuando esté lista. |
| `.PENDING_REVIEW` | En revisión | Un moderador la está mirando. Mientras tanto no se puede editar. |
| `.PUBLISHED` | Publicada | Cualquiera puede verla y comprarla. |
| `.REJECTED` | Rechazada | No pudimos publicarla. Abajo te decimos por qué. |
| `.PAUSED` | Pausada | Deja de verse hasta que la reactives. Nadie más la ve. |
| `.SOLD` | Vendida | Ya se vendió. No se puede volver a publicar. |
| `.ARCHIVED` | Archivada | La retiraste para siempre. No vuelve. |

### Rechazo y corrección — `listing.rejected.*`

`.title`: No pudimos publicarla
`.reasonLabel`: Motivo
`.noteLabel`: Nota de quien revisó
`.retry`: Corregir y volver a enviar
`.retryHelp`: Conserva tus datos y tus fotos. Puedes reenviarla las veces que haga
falta.

Los siete motivos son lista cerrada y se traducen por su código
(`listing.rejectionReason.*`):

| Clave | Texto |
|---|---|
| `.PHOTOS_UNUSABLE` | Las fotos no se pueden usar: están borrosas, muy oscuras o no cumplen el mínimo |
| `.PHOTOS_MISMATCH` | Las fotos no corresponden con lo que describe la publicación |
| `.MEASUREMENTS_UNRELIABLE` | Las medidas faltan o no son creíbles |
| `.CONDITION_MISDECLARED` | La condición declarada no es la que se ve en las fotos |
| `.PROHIBITED_ITEM` | El producto no se puede vender en Sendik |
| `.SUSPECTED_COUNTERFEIT` | Sospechamos que no es original |
| `.PRICE_OUT_OF_RANGE` | El precio está fuera del rango razonable para esa categoría |

Son los mismos siete textos que salen por correo, y no es casualidad: quien recibe
el correo y luego entra a corregir tiene que leer lo mismo en los dos sitios. La
copia del correo vive en el backend porque un buzón no tiene quien lo traduzca
(`ListingRejectionTexts`); **si uno de los dos cambia, cambian los dos**.

### Después de publicada — `listing.live.*`

`.editPrice`: Cambiar el precio
`.editShipping`: Cambiar el envío
`.noReview`: El precio y el envío no pasan por revisión.
`.backToReview`: Si cambias las fotos o lo que describe el producto, vuelve a
revisión y deja de verse hasta que la aprueben.
`.pause`: Pausar
`.resume`: Reactivar
`.archive`: Archivar
`.archiveConfirm`: Archivar es para siempre. La publicación no vuelve y sus fotos se
borran. ¿Seguimos?

La confirmación de archivar es la única de toda la historia, y está porque es la
única acción del vendedor que no se puede deshacer.

### Mis publicaciones — `listing.mine.*`

`.title`: Mis publicaciones
`.empty`: Todavía no has publicado nada.
`.emptyAction`: Publicar mi primer producto
`.new`: Publicar
`.attention`: Necesita atención

`.attention` es lo que ve el vendedor cuando su publicación quedó marcada
(RN-020). **No dice por qué**: el motivo es para el moderador, y anunciarle al
vendedor «tu precio está fuera de rango» antes de que nadie lo mire invita a
cambiarlo para esquivar la revisión.

### Códigos de error — `errors.byCode.*`

Los nueve de `CATALOG_`, que hoy no tienen texto:

| Código | Texto |
|---|---|
| `CATALOG_SELLER_NOT_VERIFIED` | Necesitas ser vendedor verificado para publicar. |
| `CATALOG_LISTING_INVALID_STATE` | Esta publicación cambió mientras la editabas. Vuelve a cargarla. |
| `CATALOG_LISTING_INCOMPLETE` | Faltan datos para enviarla a revisión. |
| `CATALOG_LISTING_NOT_EDITABLE` | No puedes editarla mientras está en revisión. |
| `CATALOG_SHOTS_INCOMPLETE` | Te faltan tomas para enviarla a revisión. |
| `CATALOG_CONDITION_NOT_ALLOWED` | En esta categoría solo se publica lo nuevo. |
| `CATALOG_REFERENCE_IMAGE_NOT_ALLOWED` | Las imágenes de referencia solo se pueden agregar en tecnología sellada. |
| `CATALOG_UNKNOWN_CATEGORY` | Esa categoría ya no está disponible. Elige otra. |
| `CATALOG_SELF_MODERATION_FORBIDDEN` | No puedes decidir sobre tu propia publicación. |

⚠️ **`FILE_DIMENSIONS_TOO_SMALL` se queda corto y ya no es solo cosa del catálogo.**
Su texto dice «La imagen es muy pequeña y se vería borrosa. Sube una más grande», y
desde HU-007 ese mismo código sale también cuando la imagen es grande pero no es
vertical 3:4 (RN-018). A quien suba una foto apaisada de 4000 píxeles le estamos
diciendo que es pequeña. Propuesta: **«La imagen tiene que ser vertical y de al
menos 900 × 1200 píxeles.»** Toca un texto de Fase 1, así que se cambia con quien
lleve la copia, no de paso.

### Lo que no se escribe aquí, y por qué

- **Ningún límite de publicaciones activas.** Decidido el 26 de agosto de 2026:
  **no hay límite, para empezar.** Y por eso no hay texto: una regla que no existe
  no se anuncia, y un texto que la insinúe la crearía por la puerta de atrás.
- **Ningún plazo de despacho.** Es de Fase 3 y sigue sin decidir.
- **Nada sobre el visor 360.** La ficha con el visor es HU-003; aquí solo se suben
  las tomas que ese visor usará.
- **Nada que enlace al catálogo público.** No existe todavía, y HU-004 y HU-005 ya
  fijaron que no se enlaza a rutas que no están.

## Captura asistida y visor 360º — Fase 2

El texto de HU-003, que tiene dos mitades y dos dueños: el **asistente de captura**
lo usa el vendedor y va bajo `listing.capture.*`; el **visor giratorio** lo usa
cualquiera en la ficha y va bajo `catalog.viewer.*`.

**Cada mitad va detrás de su bandera.** El asistente detrás de `FEATURE_PUBLISHING`
y el visor detrás de `FEATURE_CATALOG`, que son las que ya cubren las pantallas
donde cada uno aparece. No estrena bandera propia: no hay ningún escenario en el
que tenga sentido encender el catálogo sin su visor.

Ruta del asistente: `/publicar/:id/capturar`, colgada del formulario que ya existe.

### La entrada al asistente — `listing.capture.entry.*`

`.action`: Tomar las fotos con la cámara
`.hint`: Te guiamos las ocho tomas, una por cada giro.
`.resume`: Sigue donde ibas: te faltan {{cuantas}}.
`.unsupported`: Este dispositivo no tiene cámara disponible. Puedes subir las fotos
desde tu galería.

`.action` nombra la cámara y no «el asistente», que no significa nada para quien
llega. `.hint` dice **por qué son ocho** en una línea: porque el producto gira.

### Los estados de la pantalla — `listing.capture.*`

`.loading`: Cargando la publicación
`.notFound`: No pudimos abrir esta publicación. Puede que ya no exista o que no sea tuya.
`.backToListings`: Volver a mis publicaciones
`.notForSealed`: Este producto no usa el asistente de captura.

`.notForSealed` va seguido de `listing.shots.sealedBody`, que ya explica cuáles son las
cuatro tomas. Se dice **qué pasa** antes de **qué hacer**: quien llega aquí desde un enlace
necesita primero entender por qué la pantalla no es la que esperaba.

### Los ocho pasos — `listing.capture.shot.*`

En el orden del giro, empezando por el frente. Las cuatro en negrita son las
canónicas de RN-016, las que no pueden faltar.

| Clave | Grados | Nombre |
|---|---|---|
| `.front` | 0 | **Frente** |
| `.frontRight` | 45 | Frente y lado derecho |
| `.right` | 90 | **Lado derecho** |
| `.backRight` | 135 | Espalda y lado derecho |
| `.back` | 180 | **Espalda** |
| `.backLeft` | 225 | Espalda y lado izquierdo |
| `.left` | 270 | **Lado izquierdo** |
| `.frontLeft` | 315 | Frente y lado izquierdo |

**Se nombran, no se numeran.** El criterio 1 pide el nombre de cada toma, y «paso 4
de 8» no le dice a nadie hacia dónde girar el producto. Las intermedias se nombran
por las dos que tienen a cada lado, que es como se explicaría de viva voz.

**Derecha e izquierda son las de quien mira**, no las de la prenda. Es la
convención de una fotografía y la que evita que alguien voltee una camisa buscando
«su» manga derecha.

### El progreso — `listing.capture.progress.*`

`.label`: Toma {{numero}} de {{total}}
`.aria`: {{hechas}} de {{total}} tomas listas
`.canonical`: Esta no puede faltar
`.done`: Ya tienes las ocho. Revísalas y súbelas.

### El nivel — `listing.capture.level.*`

`.ok`: Nivelado
`.tilted`: Endereza el teléfono para tomar la foto.
`.tiltedHint`: Está inclinado más de 5 grados y las tomas quedarían desalineadas.

`.tilted` manda hacer algo y `.tiltedHint` explica por qué; separadas porque la
primera va junto al obturador deshabilitado y la segunda solo hace falta si la
persona se queda mirando sin entender. **Ninguna de las dos culpa a la persona**:
«está inclinado» y no «lo tienes torcido».

### Los sensores en iOS — `listing.capture.sensors.*`

`.request`: Activar el nivel
`.requestHint`: Usamos el sensor de movimiento para avisarte si el teléfono está
inclinado. No sale del dispositivo.
`.denied`: Seguimos sin el nivel. Las tomas pueden quedar desalineadas entre sí.

⚠️ Las tres son de un permiso que **solo pide iOS**. `.requestHint` dice para qué
se usa y que no se envía a ninguna parte, que es lo que la Ley 1581 pide de
cualquier dato que se recoja, aunque este no se guarde.

**`.denied` no es un error y no ofrece reintentar.** El criterio 4 es explícito:
si se niega, el asistente sigue y nunca se bloquea la publicación. El texto avisa
de la consecuencia real —desalineadas *entre sí*, que es lo que estropea el giro—
y no insiste.

### La galería — `listing.capture.gallery.*`

`.action`: Subir desde la galería
`.hint`: La recortamos igual que si la hubieras tomado aquí.
`.attention`: Las fotos que subes desde la galería pasan una revisión más atenta.

`.attention` es el aviso del criterio 8, y **se dice antes y no después**: enterarse
de que tu publicación va a mirarse con lupa cuando ya la enviaste es lo que hace
sentir engañado a alguien. No dice «sospechosa»; dice lo que pasa.

### Cuando la foto no sirve — `listing.capture.rejected.*`

`.RESOLUCION_INSUFICIENTE`: Esta foto es muy pequeña. Necesitamos al menos 900 ×
1200 píxeles después de recortarla a vertical.
`.NO_SE_PUDO_COMPRIMIR`: No pudimos preparar esta foto. Intenta con otra.
`.IMAGEN_ILEGIBLE`: No pudimos leer este archivo. Asegúrate de que sea una imagen.
`.SIN_SOPORTE`: Este navegador no puede preparar la foto. Prueba con otro o actualízalo.

El primero es el único que se ve a menudo, y **dice «después de recortarla»** a
propósito: una foto cuadrada de 1000 píxeles pasa los dos mínimos por separado y
su recorte vertical no, y sin esa frase el número parece mentir.

`.SIN_SOPORTE` existe para no mentir en el otro sentido. Cuando el navegador no tiene el
worker que recorta, el problema no es la foto, y decir «no pudimos leer este archivo» manda
a la persona a buscarle un defecto a una imagen que está perfecta.

⚠️ Esto **no sustituye** a `FILE_DIMENSIONS_TOO_SMALL`, que sigue saliendo del
servidor y sigue mal redactado (ver el aviso de la sección anterior). Lo que hace
es que casi nunca se llegue a él: ahora el recorte se decide en el dispositivo y
lo que no cumple no se sube.

### La subida — `listing.capture.upload.*`

`.progress`: Subiendo {{porcentaje}}%
`.retry`: Reintentar esta toma
`.failed`: No se pudo subir esta toma.
`.done`: Listo
`.saved`: Toma {{nombre}} lista

`.saved` no se ve: es lo que se le anuncia a un lector de pantalla cuando una toma acaba de
subir. Sin él, quien no ve la pantalla no tiene forma de saber si la foto entró —cambian el
encabezado y la barra, y ninguno de los dos se anuncia solo—.

`.retry` dice «esta toma» porque el criterio 10 pide reintentar **solo la que
falló**, y un botón que dijera «reintentar» a secas haría temer que se repitan las
ocho.

### El visor giratorio — `catalog.viewer.*`

`.label`: Vista giratoria del producto
`.roleDescription`: visor giratorio
`.instructions`: Arrastra para girar, o usa las flechas del teclado.
`.loading`: Cargando la vista giratoria
`.frame`: Vista a {{grados}} grados
`.fallbackAlt`: {{titulo}}, vista frontal

`.roleDescription` es lo que un lector de pantalla dice en lugar de «control deslizante»:
el visor se marca con ese rol porque es el único que hace que el lector **ceda las flechas**
al componente, pero «control deslizante» no describe lo que hay. `.label` queda para el
título del producto. `.instructions` da las dos formas de usarlo —dedo y teclado— porque el
criterio 15 exige que las dos existan y quien navega con teclado no tiene cómo adivinarlo;
mientras el visor no puede girar todavía, en su lugar se dice `.loading`, porque prometer
unas flechas que aún no responden es peor que no decir nada.

`.fallbackAlt` es el `alt` de la imagen que el servidor entrega antes de que el
visor se active (criterio 18). Lleva el título del producto porque esa imagen es lo
que un buscador indexa, y «vista frontal» a secas no describe nada.

En inglés se conserva la distinción entre girar y desplazar: `Drag to rotate, or
use the arrow keys.`

### Lo que no se escribe aquí, y por qué

- **Ningún texto de la tecnología sellada.** El asistente no aplica ahí: son cuatro
  tomas del empaque y no hay giro que guiar (RN-065). Esa pantalla sigue siendo la
  rejilla de HU-007, con sus textos.
- **Ninguna instrucción de fondo, luz o distancia.** Sería prometer un resultado
  que el asistente no comprueba: mide inclinación y encuadre, no iluminación.
  Escribir «usa luz natural» convierte en regla lo que hoy es un consejo sin nadie
  detrás.
- **Nada sobre cuánto pesa una foto ni cuánto se comprime.** Es una decisión
  técnica (criterio 9), no una promesa al vendedor, y anunciarla la volvería
  exigible.

## Moderación de publicaciones — Fase 2

El texto de HU-008. Las claves van bajo `listingReview.*`.

**Segunda pantalla interna del sitio**, y hereda el tono de la primera: quien la usa
trabaja aquí, revisa muchas al día y no necesita que se le explique el producto. Texto
corto, que dice qué hace cada cosa. Nada de acompañamiento.

**Es la contraparte de `listing.*` y no su reemplazo.** Los mismos siete motivos de
rechazo tienen dos redacciones y las dos existen a propósito: `listing.rejectionReason.*`
está escrita para el vendedor que la recibe y explica qué corregir; esta es una etiqueta
corta para elegir de una lista. Cambiar el sentido de una obliga a cambiar la otra.

**La bandeja** (`listingReview.inbox.*`):
`.title`: Publicaciones pendientes
`.waitingSince`: Espera desde hace `{{tiempo}}`
`.price`: `{{precio}}`
`.attention`: Necesita atención
`.empty.title`: No hay nada por revisar
`.empty.body`: Cuando alguien envíe una publicación, aparece aquí.
`.error.title`: No pudimos cargar la bandeja
`.error.retry`: Reintentar

`.attention` es el criterio 6 en la lista, y es **texto y no un punto de color**: quien
revisa tiene que poder ordenar su trabajo sin distinguir tonos. El motivo concreto no se
dice aquí sino en el detalle, porque en una fila no cabe y porque saberlo no cambia si se
abre o no.

**El detalle** (`listingReview.detail.*`):

| Clave | Texto |
|---|---|
| `.productSection` | El producto |
| `.measurementsSection` | Medidas |
| `.shotsSection` | Las ocho tomas |
| `.category` | Categoría |
| `.condition` | Condición |
| `.brand` | Marca |
| `.size` | Talla |
| `.color` | Color |
| `.price` | Precio |
| `.description` | Descripción |
| `.sealed` | Producto sellado |
| `.warranty` | Garantía de `{{meses}}` meses |
| `.shotMissing` | Esta toma no está disponible |
| `.back` | Volver a la bandeja |

`.shotMissing` es el caso borde del archivo que falta. Se dice y no se esconde: una toma
ausente es motivo suficiente para rechazar por fotos inservibles, y el moderador tiene
que poder distinguirla de una que sí está y se ve mal.

**Las marcas de atención** (`listingReview.attention.*`). Son los dos valores de
`AttentionReason`, traducidos por su código:

| Clave | Texto |
|---|---|
| `.title` | Por qué necesita atención |
| `.PRICE_OUT_OF_RANGE` | El precio está fuera del rango habitual |
| `.GALLERY_UPLOAD` | Alguna toma se cargó desde la galería, no se capturó |

**Aquí sí se dice el motivo, y al vendedor no.** `listing.mine.attention` le dice que su
publicación necesita atención y calla por qué, para no invitarlo a cambiar el precio y
esquivar la revisión. El moderador es justo quien necesita el dato: es lo que RN-020
llama revisión manual.

`.GALLERY_UPLOAD` está redactada sin acusar. Lo declara el cliente y no lo comprueba el
servidor, así que **no es prueba de nada**: es una señal para mirar con más cuidado, y el
texto no puede sonar a que ya se decidió.

**Decidir** (`listingReview.decision.*`):
`.approve`: Aprobar
`.reject`: Rechazar
`.reasonLabel`: Motivo del rechazo
`.reasonPlaceholder`: Elige un motivo
`.noteLabel`: Nota para el vendedor (opcional)
`.noteHint`: La lee quien publicó. Dile qué corregir. No escribas información de terceros
ni datos de procesos judiciales.
`.confirmApprove`: ¿Aprobar esta publicación? Queda visible para cualquiera y el vendedor
recibe un correo.
`.confirmReject`: ¿Rechazar esta publicación? El vendedor recibe un correo con el motivo
y podrá corregirla y volver a enviarla.
`.confirm`: Confirmar
`.cancel`: Cancelar
`.approved`: Publicación aprobada
`.rejected`: Publicación rechazada
`.alreadyResolved`: Esta publicación ya no está en revisión.
`.ownListing`: Esta publicación es tuya. La revisa otra persona.

`.noteHint` dice «dile qué corregir» y no solo lo prohibido: en el rechazo de una
publicación la nota tiene un uso concreto que en la verificación no tenía, porque aquí
casi siempre se puede arreglar y reenviar. Es la diferencia con
`verificationReview.decision.noteHint`, que solo advierte.

`.ownListing` es el criterio 12 y RN-063, dicho **antes** de que se pulse nada. El
servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse después de
pulsar, con un correo ya prometido, no hace falta.

`.alreadyResolved` cubre dos cosas con el mismo texto y es deliberado: que otro moderador
decidiera antes (criterio 11) y que el vendedor la retirara de revisión (criterio 13). Al
moderador le pasa lo mismo en los dos casos —ya no le toca— y distinguirlos solo serviría
para contar qué hizo otra persona.

**Los motivos de rechazo** (`listingReview.reasons.*`), los siete de
`ListingRejectionReason` como etiqueta corta para elegir:

| Código | Texto |
|---|---|
| `PHOTOS_UNUSABLE` | Fotos inservibles |
| `PHOTOS_MISMATCH` | Las fotos no corresponden |
| `MEASUREMENTS_UNRELIABLE` | Medidas poco creíbles |
| `CONDITION_MISDECLARED` | Condición mal declarada |
| `PROHIBITED_ITEM` | Producto prohibido |
| `SUSPECTED_COUNTERFEIT` | Sospecha de réplica |
| `PRICE_OUT_OF_RANGE` | Precio fuera de rango |

**Sin acceso** (`listingReview.forbidden.*`). Criterio 2, y se resuelve igual que en
HU-006: **no hay texto propio**. Se reutiliza la página 404 (`notFound.*`), porque un «no
tienes permiso» confirma que hay algo detrás.

**Lo que no se escribe aquí, y por qué.** No hay texto para bajar una publicación ya
visible: el endpoint `removal` existe pero la acción quedó fuera de HU-008, por el mismo
motivo que la revocación quedó fuera de HU-006. Tampoco hay texto de historial ni de
métricas de moderación.

## Catálogo público — Fase 2

El texto de HU-009. Es lo primero que ve alguien que **no tiene cuenta**, así que
es el único bloque de este documento que se escribe para un desconocido y no para
alguien que ya decidió entrar. Las claves van bajo `catalog.*`; los mensajes de
error salen del código, en `errors.byCode.*`.

**Detrás de `FEATURE_CATALOG`, hoy apagada.** Es la única bandera que enciende
páginas para quien no tiene cuenta: encenderla no expone una funcionalidad más,
abre la tienda.

Rutas, con el mismo criterio que las demás —en español, en minúscula y con guion—:
`/catalogo` para el listado, `/catalogo/:familia` y `/catalogo/:familia/:categoria`
para la navegación por el árbol, `/producto/:id` para la ficha y `/vendedor/:id`
para el perfil.

### El listado — `catalog.list.*`

`.title`: Qué se está vendiendo
`.intro`: Todo lo que ves pasó por revisión antes de publicarse.
`.empty`: Todavía no hay nada publicado.
`.emptyInCategory`: Todavía no hay nada publicado en esta categoría.
`.loading`: Cargando el catálogo
`.error`: No pudimos cargar el catálogo. Inténtalo de nuevo.
`.retry`: Reintentar
`.more`: Ver más

`.intro` es la única línea del listado que vende algo, y vende lo que Sendik hace
distinto de un grupo de Facebook: que alguien miró la publicación antes (RN-015).
No dice «seguro» ni «protegido», que son las palabras que las decisiones de voz
prohíben.

**El estado vacío no se disculpa ni promete.** «Vuelve pronto» sería una promesa
sobre cuándo habrá producto, y no hay ninguna regla detrás.

### La navegación por categorías — `catalog.categories.*`

`.title`: Categorías
`.all`: Todo
`.family`: Familias
`.backToAll`: Ver todo el catálogo

Los nombres de las seis familias y de las treinta y una categorías **no se
escriben aquí**: viven en la base de datos, en los dos idiomas, desde la migración
que siembra el árbol. Duplicarlos en el archivo de traducciones es garantizar que
un día digan cosas distintas.

### La tarjeta del producto — `catalog.card.*`

La tarjeta muestra la toma frontal, el título, el precio y la condición. La
condición reusa `listing.condition.*`, que ya existe: son las cuatro del glosario
y no hay una quinta.

`.a11yLink`: Ver {{titulo}}

**La insignia de vendedor verificado no va en la tarjeta.** El acento bronce
aparece una vez por pantalla, y veinte tarjetas con insignia son veinte acentos.
Va en la ficha y en el perfil, que es donde la confianza se decide.

### La ficha — `catalog.detail.*`

`.declaredBy`: Lo que declara el vendedor
`.measurements`: Medidas
`.measurementsHint`: En centímetros, tomadas por el vendedor.
`.brand`: Marca
`.noBrand`: Sin marca
`.notFound`: Esta publicación ya no está disponible.
`.notFoundBody`: Puede que se haya vendido o que el vendedor la haya retirado.
`.backToCatalog`: Volver al catálogo

`.loading`: Cargando la publicación
`.gallery`: Fotos del producto
`.shot`: Toma {{grados}} grados

Las tres últimas casi no se ven y son las que más falta hacen: `.gallery` nombra la
región del carrusel y `.shot` es el texto alternativo de cada toma, así que son lo único
que un lector de pantalla tiene para recorrer las ocho fotos. El alternativo describe el
ángulo y no el producto —el producto ya lo dice el título, repetirlo ocho veces es ruido—,
que es la excepción a la regla de «describir la prenda real» de más abajo: aquí lo que
cambia entre una foto y la siguiente es desde dónde se tomó.

`.notFound` dice **«ya no está disponible»** y no «no existe», y las dos frases son
verdad a la vez: RN-068 hace que un identificador inexistente y algo que dejó de
estar publicado respondan igual, y el texto no puede distinguir lo que la API no
distingue. `.notFoundBody` enumera las dos causas probables sin afirmar ninguna.

Las etiquetas que ya estaban escritas —«Vendido por {{sellerName}} · Vendedor
verificado» y la escala de condición— están en la sección siguiente y no se
duplican aquí.

### El rótulo de las imágenes de referencia — `catalog.referenceImage.*`

Lo exige RN-066 **en la ficha y en el carrusel**, y era el pendiente que este
documento anotaba como «llega con la ficha».

`.label`: Imagen de referencia
`.hint`: No la tomó el vendedor. Es del fabricante y muestra el modelo, no el
producto que vas a recibir.

**El rótulo dice de quién es la foto, no que sea bonita.** La regla existe porque
una imagen de fabricante junto a fotos reales lleva a creer que el producto se ve
así; el rótulo es lo que impide que la promesa sea publicidad engañosa. Por eso
`.hint` nombra las dos cosas: quién la tomó y qué muestra.

Va en los dos idiomas desde el primer día. En inglés se conserva la misma
distinción: `Reference image` / `The seller did not take this photo. It is the
manufacturer's and shows the model, not the item you will receive.`

### La garantía del fabricante — `catalog.detail.warranty.*`

Lo exige RN-067 y era el último pendiente de redacción de la ficha. **Escrito el 8
de septiembre de 2026**, en la misma tanda que los documentos legales, que es lo que
la decisión del 26 de agosto pedía.

`.label`: Garantía del fabricante
`.months`: {{meses}} meses, declarados por el vendedor
`.month`: 1 mes, declarado por el vendedor
`.who`: Quien responde por esta garantía es el vendedor, no Sendik.
`.link`: Leer los términos y condiciones

**No es copia legal nueva y por eso se pudo escribir.** El numeral 15.2 de los
términos publicados —versión `2026-09-08b`— ya dice esto mismo con estas palabras:
el vendedor declara la garantía y por cuántos meses, y quien responde por ella es él
y no Sendik. Lo de aquí lo enuncia en llano y **enlaza al documento**, que es lo que
RN-057 manda: los plazos y las condiciones viven en un solo sitio versionado y las
páginas apuntan allí en vez de repetirlos.

**Sin dato no se pinta nada, y nunca «sin garantía».** La garantía legal de la Ley
1480 de 2011 rige sobre todo producto nuevo, la traiga o no de fábrica, así que una
ficha que dijera «sin garantía» estaría afirmando algo falso sobre los derechos de
quien compra. Cuando el vendedor no declaró meses, la ficha calla y el numeral 15.1
sigue siendo verdad.

**El singular va en su propia clave.** «1 meses» en la pantalla que más gente lee sin
tener cuenta es de las cosas que se notan. `listingReview.detail.warrantyMonths`
arrastra ese defecto, pero eso lo ve un moderador y se corrige aparte.

**Nunca la palabra Respaldo ni nada que se le parezca**, que es la condición literal
que RN-067 imponía. El glosario admite «garantía» exactamente en esta forma —referida
al dispositivo y a lo que responde su vendedor— y la prohíbe para nombrar lo que
ofrece Sendik. `.who` es la frase que mantiene las dos cosas separadas, y por eso
nombra a Sendik: decir solo «responde el vendedor» deja al lector suponiendo quién más.

En inglés: `Manufacturer warranty` / `{{meses}} months, as declared by the seller` /
`1 month, as declared by the seller` / `The seller answers for this warranty, not
Sendik.` / `Read the terms and conditions`.

### El perfil del vendedor — `catalog.seller.*`

`.title`: {{nombre}}
`.verified`: Vendedor verificado
`.verifiedHint`: Sendik confirmó su identidad y su cuenta bancaria.
`.listings`: Lo que vende
`.empty`: Ahora mismo no tiene nada publicado.
`.notFound`: No encontramos a este vendedor.
`.loading`: Cargando el perfil
`.soldBy`: Vendido por

`.soldBy` es la mitad de la línea que este documento ya tenía escrita en «Etiquetas de
ficha de producto»: «Vendido por {{sellerName}} · Vendedor verificado». La otra mitad es
`.verified`, y van en dos claves porque el sello solo aparece cuando lo hay.

`.verifiedHint` dice exactamente qué se confirmó y nada más. No dice que Sendik
responda por el producto ni que el vendedor sea de fiar: lo que se verificó es
identidad y cuenta (HU-002), y decir más sería prometer algo que ninguna regla
sostiene.

El perfil **no muestra reseñas** —son Fase 3— ni ningún dato personal más allá
del nombre público y la foto.

### Descripciones para buscadores — `meta.*`

Son la razón por la que este bloque se renderiza en el servidor: lo que un
buscador indexa es lo único que trae compradores.

| Clave | Título | Descripción |
| --- | --- | --- |
| `meta.catalog` | Catálogo | Moda nueva y de segunda y tecnología nueva, con vendedores verificados y publicaciones revisadas. |
| `meta.catalogCategory` | {{categoria}} | {{categoria}} en Sendik. Publicaciones revisadas y vendedores verificados. |
| `meta.product` | {{titulo}} | {{titulo}}. {{condicion}}, publicado por un vendedor de Sendik. |
| `meta.sellerProfile` | {{nombre}} | Lo que vende {{nombre}} en Sendik. |

Los cuatro llevan marcador y no texto fijo: una descripción igual para todas las
fichas es una descripción que ningún buscador usa.

## Etiquetas de ficha de producto — Fase 2

- **Condición:** Nuevo · Como nuevo · Buen estado · Con detalles. Son las cuatro
  del glosario; no hay una quinta y no se inventa otra escala.
- **Vendido por:** `{{sellerName}}` · Vendedor verificado
- **Bajo el botón de compra:** Tu pago queda retenido hasta que confirmes que
  recibiste la prenda.

## Checkout — Fase 3

- **Paso de envío:** Elige cómo quieres recibirlo. Cada opción muestra su
  transportadora, su costo y su plazo estimado. **Lo que elijas es lo que pagas.**
- **Si no hay opciones:** No pudimos cotizar el envío a esa dirección. Prueba con
  otra o vuelve a intentarlo en un momento. (RN-040)
- **Paso de pago:** El pago lo recauda y lo retiene la pasarela. Se libera al
  vendedor cuando confirmes que recibiste la prenda.
- **Antes de confirmar:** Precio + envío = total. Sin costos adicionales.
- **Botón final:** Comprar y pagar

## Textos alternativos de imagen

Describir la prenda real: *Bolso de cuero café con correa larga, vista frontal*.
Nunca *imagen1* ni cadenas de palabras clave.

---

# Pendientes reales

Los que estaban en la versión anterior de este documento y **ya están
resueltos** no aparecen aquí: la escala de condición existe (glosario), la lista
de productos prohibidos existe (RN-024), la ventana de reclamo y el reintegro
existen (RN-050 a RN-058), y quién confirma la entrega está decidido (RN-034: el
comprador).

## Tecnología en el catálogo — pendiente de redacción

El catálogo admite tecnología nueva desde el 24 de agosto de 2026 (RN-064 a
RN-067). Las reglas están escritas; **el texto del sitio, en su mayor parte, no**,
y hasta que lo esté no se toca `src/i18n`: inventar copia de cara al público en un
archivo de traducciones es exactamente lo que este documento existe para evitar.

Lo que ya se corrigió aquí es lo que había quedado **falso**: la lista de
productos prohibidos en «Qué asumes como vendedor» y en las preguntas frecuentes,
más la pregunta nueva sobre tecnología.

Lo que falta escribir:

- [x] ~~**El descriptor de marca dice «Compra y vende moda con respaldo»** y el
      catálogo ya no es solo moda.~~ **Resuelto por la marca Sendik** (ADR-0022):
      el eslogan es ahora **«Compra y vende de forma ágil y segura»**, que no
      nombra la categoría y por eso no vuelve a quedarse corto. Ya está en
      `layout.footer.tagline`, en el logo con eslogan y en el manual.
      **Sigue pendiente el titular del hero y la descripción para buscadores**,
      que son redacción y no marca: ver abajo.
- [x] ~~**El titular del hero y `meta.home.description` siguen diciendo «moda con
      respaldo».**~~ **Decidido el 25 de agosto de 2026:** ambos adoptan el
      eslogan tal cual, «Compra y vende de forma ágil y segura», y con ellos
      `meta.register.description` y `meta.login.description`, que arrastraban el
      mismo descriptor. Se descartó la variante de la maqueta del kit —«Compra y
      vende moda y tecnología de forma ágil y segura»—, que el propio kit marca
      como texto de muestra y que vuelve a nombrar las categorías. En inglés se
      reusa la fórmula del pie, `Buy and sell quickly and safely`, para que marca
      y copy no diverjan entre idiomas. Reabre y cierra HU-004.
- [ ] Los tres pasos de la portada y el recorrido de `/como-funciona` hablan solo
      de prendas, tallas y medidas. Hay que decidir si se generalizan o si se
      separan los dos recorridos.
- [x] Los nombres visibles de las siete categorías de tecnología, en los dos
      idiomas. **Ya estaban** en la migración que las siembra, junto con los de las
      otras veinticuatro. Lo que faltaba era la ortografía del español, corregida
      en `V11__category_names_with_accents.sql`.
- [x] ~~El rótulo de las imágenes de referencia **en la ficha y en el carrusel**, que
      RN-066 exige en los dos idiomas.~~ **Escrito el 27 de agosto de 2026** en
      «Catálogo público — Fase 2», junto con la historia que lo necesitaba:
      `catalog.referenceImage.label` y `.hint`, en español y en inglés.
- [x] ~~Cómo se enuncia la garantía del fabricante en la ficha sin usar la palabra
      Respaldo ni parecerse a ella (RN-067).~~ **Escrito el 8 de septiembre de 2026**,
      en la tanda de los documentos legales tal como pedía la decisión del 26 de
      agosto, en «Catálogo público — Fase 2». Lo que lo desbloqueó no fue una frase
      nueva: el numeral 15.2 de los términos `2026-09-08b` ya reparte la
      responsabilidad por escrito, así que la ficha lo enuncia en llano y enlaza al
      documento (RN-057) en vez de decidir nada por su cuenta.

## Decisiones de producto

- [ ] Especificaciones de tecnología: pulgadas, capacidad, memoria, modelo. Hoy
      no hay dónde guardarlas y el catálogo de tecnología no se puede filtrar por
      nada que le importe a quien compra un dispositivo.
- [x] Árbol de categorías del catálogo. **Decidido el 24 de agosto de 2026** en
      `docs/producto/categorias.md`: seis familias y treinta y una categorías, por
      tipo de producto. "Dama" y "Caballero" siguen sin ser categorías del
      proyecto. Faltan los nombres visibles en inglés.
- [ ] Plazo máximo de despacho del vendedor.
- [x] Si hay límite de publicaciones activas por vendedor. **Decidido el 26 de
      agosto de 2026: no hay límite, para empezar.** No se implementa nada y ningún
      texto lo menciona; el día que se ponga uno entra por regla de negocio.
- [ ] Si se le exige al vendedor entregar la prenda limpia.
- [ ] Si existe chat comprador–vendedor. Hoy es Fase 4 (`alcance.md`), así que
      ningún texto puede decir "pregúntale al vendedor".
- [ ] Plazo de respuesta comprometido para el canal de contacto.

## Datos que faltan

- [x] `LISTING_REVIEW_DAYS`. **Decidido el 26 de agosto de 2026: dos días
      hábiles.** Ya está escrito, en `listing.submit.reviewTime`, con el valor por
      variable.

- [ ] Días hábiles que tarda el desembolso en llegar a la cuenta del vendedor.
      Decidido en Fase 3 (`alcance.md`); hasta entonces ningún texto lo enuncia.
- [ ] Si la comisión incluye IVA y si la comisión de la pasarela se descuenta
      aparte. Si el vendedor recibe menos de lo que dice el texto, es publicidad
      engañosa.
- [ ] Si se anuncia algún plazo de entrega general. Hoy no. RN-080 da un plazo
      estimado **por envío**, que sale del agregador y no cabe en un texto fijo, y
      encima de él rige el máximo legal de 30 días calendario. Lo que falta decidir
      es si alguno de los dos se enuncia en las páginas informativas.
- [ ] Qué cobertura de envío se anuncia. Los términos dicen «todo el territorio
      nacional» desde el 5 de septiembre y **nadie lo ha comprobado** contra las
      transportadoras que el agregador habilite (RN-080).
- [ ] Quién está detrás de Sendik: nombre, ciudad y por qué se montó.
- [ ] Dirección y correo de soporte reales (`COMPANY_ADDRESS`, `SUPPORT_EMAIL`).
- [ ] Variables de configuración para canales adicionales, si se quieren mostrar.

## Revisión legal antes de abrir

- [ ] Términos y condiciones, con el derecho de retracto redactado según la ley.
- [ ] Política de tratamiento de datos publicada antes de activar cualquier
      formulario o registro.
- [ ] Política de devoluciones y reclamos, dentro de los términos
      (`docs/operacion/textos-legales.md`).
- [ ] **Qué responsabilidad tiene Sendik frente al comprador si el reintegro
      falla.** RN-054 lo mitiga —el dinero sigue retenido en la pasarela, así
      que no depende de que el vendedor colabore—, pero el Estatuto del
      Consumidor impone deberes propios a quien opera una plataforma de comercio
      electrónico. Es el punto que un abogado debe revisar primero.
- [ ] **La garantía legal de un producto de tecnología nuevo.** La Ley 1480 de
      2011 la impone sobre todo producto nuevo, y RN-067 dice que responde el
      vendedor y no Sendik. **Bloquea abrir la venta de tecnología**, no el resto
      del sitio.

      **La redacción quedó hecha el 8 de septiembre de 2026** y con ella se
      desbloquea la ficha de producto: los términos `2026-09-08b` lo dicen en el
      numeral 15.2 —15.1 recoge además la garantía legal y sus términos— y la ficha
      lo enuncia y enlaza sin rozar la palabra Respaldo.

      **Lo que sigue abierto es lo otro que esta línea pedía, y es de criterio
      profesional:** si repartir así la responsabilidad es sostenible para una
      plataforma que además cobra comisión. Eso no lo cierra una redacción y va
      donde van sus hermanos, en
      `docs/operacion/entrega-textos-legales-2026-09-08b.md`, junto al alcance de la
      responsabilidad como portal de contacto. Publicar sin abogado colegiado sigue
      siendo la decisión expresa del 5 de septiembre.

## Traducción

- [ ] Versión en inglés de todo lo anterior. La estructura multi-idioma existe
      desde el día uno (`alcance.md`) y una prueba compara los dos árboles.

---

Recuerda: en Colombia lo que se anuncia es exigible. Cada plazo, cada porcentaje
y cada promesa de este documento se vuelve un compromiso el día que se publique.
Por eso las cifras salen de configuración y no de una plantilla, y por eso no se
escribe un plazo que no tenga una regla de negocio detrás.
