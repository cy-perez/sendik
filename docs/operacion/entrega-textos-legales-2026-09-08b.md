# Entrega — El envío pasa a cobrarse aparte, versión `2026-09-08b`

**Fecha:** 8 de septiembre de 2026
**Documentos:** términos y condiciones, y política de tratamiento de datos, en los dos
idiomas. **La política de cookies no cambia** y se queda en `2026-09-06`: nada de esto la
toca.
**Método:** skills `textos-legales-comerciales` y `cerrar-vacios-legales`.
**Origen:** la decisión de integrar Skydropx Colombia y de cobrar el envío aparte del
precio del producto (ADR-0034).

> **El nombre de versión lleva letra a propósito.** La convención es la fecha, y el 8 de
> septiembre ya se había publicado `2026-09-08` esa misma mañana. Sobrescribir un archivo
> con consentimientos apuntando a él no es una opción, así que la versión nueva es
> `2026-09-08b`. El formato del nombre de archivo lo admite sin cambiar nada.

## Lo que cambió en el negocio, en una frase

El producto tiene un **precio base** y el **envío lo paga el comprador aparte**, cotizado
con un agregador —Skydropx Colombia— en vez de con tres transportadoras. Eso mueve dos
cosas del documento legal: **cómo se informa el precio** y **quién hace y responde por el
transporte**.

---

## Fase 1 · Inventario

La skill exige dos barridos y se hicieron los dos. **El segundo dio más que el primero**,
que es la novedad de esta entrega.

### Barrido directo: marcadores abiertos

**Cero.** Se buscaron `[[ ]]`, «a confirmar», «pendiente», «por definir», «TBD»,
«revisar con» y «transcribir», en multilínea, sobre los seis archivos publicados y en los
dos idiomas. Los cuatro aciertos son falsos positivos —«para confirmar que quien vende es
quien dice ser», «cada finalidad es independiente»—.

La versión `2026-09-08` sostiene lo que prometía: no queda un marcador abierto.

### Barrido inverso: afirmaciones sin respaldo

Se sacó cada afirmación comprobable del documento publicado —proveedores con nombre
propio, países, plazos, coberturas— y se buscó en el resto del proyecto. **Cuatro no
aparecían en ninguna otra parte.**

| Afirmación publicada | ¿Qué la respaldaba? | Clase |
|---|---|---|
| «Skydropx Colombia y las transportadoras que habilite» (privacidad 10) | **Nada.** RN-038 decía Envía, Coordinadora e Interrapidísimo | Decisión sin registrar + Clase 4 |
| «Colombia», como país de ese proveedor (privacidad 10) | **Nada.** Nadie lo verificó | Clase 4 |
| «Cobertura de envío: todo el territorio nacional colombiano» (términos 12.5) | **Nada.** Ninguna regla habla de cobertura | Clase 4 |
| «Plazo máximo de entrega de treinta (30) días calendario» (términos 12.2) | La ley sí; **el proyecto no**. `textos-web.md` decía a la vez que el sitio no anuncia plazo «porque no hay regla que lo respalde» | Clase 1, incompleta |

**El primero es el hallazgo de la entrega.** El marcador `[[TRANSPORTADORAS CONTRATADAS]]`
se cerró el 6 de septiembre con un nombre propio que no estaba escrito en ningún otro
sitio del repositorio, y la entrega del 5 de septiembre había anotado para ese hueco algo
distinto: «Envía, Coordinadora, Interrapidísimo, las que se contraten». Durante dos días
**el único documento del proyecto que sabía quién entregaba los pedidos era el que lee el
público**.

No es un error de redacción: es un hueco cerrado con lo que sonaba plausible, que es
justo lo que un marcador relleno deja de poder distinguirse de una decisión. La decisión
ahora existe de verdad y está en ADR-0034.

### Vacíos nuevos que abre el cambio

Ocho más, los que trae cobrar el envío aparte. Van resueltos abajo.

---

## Fase 2 · Lo que se cerró, y contra qué

Todas las consultas son del **8 de septiembre de 2026**. La Ley 1480, el Código de
Comercio y el Estatuto Tributario se leyeron en el Régimen Legal de Bogotá
(`alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=`, con `44306`, `41102` y `6533`), en
HTML crudo y decodificando de ISO-8859-1, no a través de ninguna herramienta que resuma.

### 1 · Cómo se informa un precio partido en dos — Clase 1

**Dónde:** términos, numerales 9 y 10.

La duda era si separar el precio del envío es admisible o si hay que anunciar una cifra
única. La contesta el **artículo 50, literal c), inciso segundo** de la Ley 1480, con las
dos mitades juntas:

> Igualmente deberá informar el precio total del producto incluyendo todos los impuestos,
> costos y gastos que deba pagar el consumidor para adquirirlo. En caso de ser procedente,
> se debe informar adecuadamente y por separado los gastos de envío.

**Separado y total, no una cosa u otra.** El numeral 9 ahora muestra las tres cifras
—precio base, costo de envío y total— y lo dice así.

El **literal d)** del mismo artículo añadió dos obligaciones que el documento no
recogía y que son trabajo de Fase 3:

- Un **resumen del pedido** antes de concluir la transacción, con la descripción completa,
  el precio de cada bien, los gastos de envío y la suma total, **descargable e
  imprimible**, y con derecho a cancelar hasta antes de concluirla.
- Un **acuse de recibo** a más tardar el día calendario siguiente al pedido, con el tiempo
  de entrega, el precio exacto, los gastos de envío y la forma de pago.

Las dos entran en el numeral 10 y en RN-076.

### 2 · Si el envío lleva IVA — Clase 1

**Dónde:** términos, numeral 9.

Si el costo de envío es un cobro adicional al consumidor, hay que saber si va gravado
para poder decir que el precio incluye los impuestos. Lo contesta el **artículo 476,
numeral 9, del Estatuto Tributario**, en el texto modificado por el artículo 11 de la Ley
2010 de 2019, que excluye del impuesto:

> El servicio de transporte público, terrestre, fluvial y marítimo de personas en el
> territorio nacional, y el de transporte público o privado nacional e internacional de
> carga marítimo, fluvial, terrestre y aéreo.

**Es el numeral 9, no el 2**, que es donde suele citarse de memoria. El costo de envío no
lleva IVA añadido y el documento lo dice.

### 3 · Si se puede recotizar después de pagar — Clase 1, y cierra una decisión

**Dónde:** términos, numeral 9. **Regla:** RN-077.

El **artículo 26** de la Ley 1480 no deja margen:

> El proveedor está obligado a informar al consumidor en pesos colombianos el precio de
> venta al público, incluidos todos los impuestos y costos adicionales de los productos.
> El precio debe informarse visualmente y el consumidor solo estará obligado a pagar el
> precio anunciado.

No es que cobrar la diferencia sea arriesgado: **no es una opción disponible**. Con eso, la
pregunta de negocio se reduce a quién la asume, y se decidió que Sendik (RN-077). También
cae aquí el rótulo de «valor aproximado» que RN-038 exigía: era legítimo mientras nadie
pagaba esa cifra y deja de serlo cuando alguien la paga.

### 4 · Quién es parte del contrato de transporte — Clase 2, subsunción

**Dónde:** términos, numeral 12. **Regla:** RN-078.

**La categoría legal.** El **artículo 1008 del Código de Comercio**, modificado por el
artículo 18 del Decreto 01 de 1990: «Se tendrá como partes en el contrato de transporte de
cosas el transportador y el remitente. Hará parte el destinatario cuando acepte el
respectivo contrato.»

**El hecho.** Sendik emite la guía a través del agregador, pero quien entrega la mercancía
al transportador es el vendedor, y la decisión de ADR-0034 es que la emita **por cuenta
del vendedor**, no en nombre propio.

**La conclusión.** El remitente es el vendedor, y por tanto **es él quien le reclama a la
transportadora** por pérdida, avería o retardo (art. 1030). El comprador es destinatario.
Que no tenga que perseguir a nadie es lo que cubre el Respaldo, porque su dinero sigue
retenido (RN-054).

**El riesgo que queda**, y es el que un abogado tiene que mirar: emitir guías por cuenta
de un tercero es una figura que depende de lo que permita el contrato del proveedor, y ese
contrato no se ha leído. Va como pendiente C.

### 5 · Qué valor se declara en la guía — Clase 1, con consecuencia de dinero

**Dónde:** RN-078, numeral 2. No aparece en el texto público.

El **artículo 1031 del Código de Comercio**: «En caso de pérdida total de la cosa
transportada, el monto de la indemnización a cargo del transportador igual al valor
declarado por el remitente para la carga afectada.»

Es el techo de lo que paga la transportadora. Si el vendedor declarara de menos para
abaratar el flete y el paquete se pierde, **Sendik ya le habría devuelto al comprador el
valor completo por RN-054** y recuperaría solo lo declarado. Por eso el valor lo escribe
el sistema con el precio base congelado del pedido y no es un campo del formulario.

### 6 · El plazo de entrega y qué pasa si se incumple — Clase 1, estaba incompleto

**Dónde:** términos, numeral 12.

El texto anunciaba los treinta días pero **le faltaban tres cosas** del **artículo 50,
literal h)** en su redacción vigente, modificada por el artículo 4 de la Ley 2439 de 2024:

| Lo que faltaba | Qué dice la norma |
|---|---|
| Desde cuándo se cuentan | Desde **el día siguiente** a aquel en que el consumidor comunicó su pedido. El documento decía «desde la confirmación del pedido» |
| El plazo de la devolución | **Quince (15) días calendario**. No estaba |
| De quién es el deber de avisar | La falta de disponibilidad debe informarla el proveedor **y el portal de contacto**. O sea, también Sendik, y de forma directa |

El literal confirma además que la devolución por entrega tardía cubre «todas las sumas
pagadas sin que haya lugar a retención o descuento alguno», envío incluido.

### 7 · Quién paga qué transporte al retractarse — Clase 1, y un hallazgo de vigencia

**Dónde:** términos, numeral 14.

Con el envío cobrado aparte hay dos fletes distintos y el documento los mezclaba en uno.
El **artículo 47**, inciso segundo —no modificado—, resuelve el de regreso:

> El consumidor deberá devolver el producto al productor o proveedor por los mismos medios
> y en las mismas condiciones en que lo recibió. Los costos de transporte y los demás que
> conlleve la devolución del bien serán cubiertos por el consumidor.

Y el inciso primero resuelve el de ida: al retractarse «se resolverá el contrato y se
deberá reintegrar **el dinero que el consumidor hubiese pagado**», que incluye el envío que
pagó. El numeral 14 ahora dice las dos cosas por separado, porque son distintas y el
comprador tiene derecho a no confundirlas.

**Y aquí salió un texto derogado dentro del documento.** El numeral 14.5 decía «No se hacen
descuentos ni retenciones de ningún tipo sobre la suma devuelta», que es literalmente la
redacción del inciso **que la Ley 2439 de 2024 sustituyó**. El compilador la conserva bajo
«El texto original del inciso era el siguiente», y el texto vigente ya no la contiene: lo
que trae ahora son los quince días, las dos condiciones del consumidor y el instrumento de
pago.

La frase **no se retira** —es más favorable al consumidor y Sendik la sostiene— pero deja
de presentarse como si fuera la ley: ahora se dice expresamente que es un compromiso de
Sendik y por qué.

Es el mismo error que la entrega anterior evitó con el plazo de treinta días, en el otro
sentido: aquella vez el plazo derogado circulaba fuera y el documento tenía el bueno; esta
vez lo derogado estaba dentro.

### 8 · El enlace a la autoridad — Clase 1, no es de envíos y apareció de camino

**Dónde:** términos, numeral 24.

El **parágrafo del artículo 50** exige «un enlace visible, fácilmente identificable, que le
permita al consumidor ingresar a la página de la autoridad de protección al consumidor de
Colombia». El documento nombraba a la Superintendencia **en texto plano, sin enlace**.

Salió del barrido inverso, no del encargo. Cerrarlo costaba una línea y se cerró.

---

## Fase 3 · Lo que sigue abierto

Cinco, y **cuatro de los cinco son de la misma clase**: datos que solo tiene el proveedor.
Ninguno se cierra leyendo y ninguno lo decide el titular del negocio.

### A · Cuatro preguntas para Skydropx Colombia — Clase 4

**Destinatario:** Skydropx Colombia, por el canal comercial con el que se contrate.
**Dónde debería estar por escrito:** su contrato de servicio y su adenda de tratamiento de
datos personales.

| # | Pregunta, tal como se manda | Por qué importa |
|---|---|---|
| 1 | ¿Bajo qué razón social y qué NIT o identificación fiscal se contrata el servicio, y desde qué país opera esa entidad? | Decide si hay transmisión internacional. La política **ya no afirma el país** |
| 2 | ¿El contrato permite emitir guías por cuenta de un tercero, figurando como remitente el vendedor y no Sendik? | Es lo que sostiene RN-078 y con ella la posición de portal de contacto |
| 3 | ¿A nombre de quién se factura el flete y qué documento recibe el comprador por él? | Sin esto no se puede cerrar cómo se factura una compra con envío |
| 4 | ¿Su adenda de tratamiento cumple el artículo 25 del Decreto 1377 de 2013, y **alcanza a las transportadoras** que ustedes habilitan? | La política afirma que Sendik solo usa encargados con ese contrato. La cadena tiene un eslabón que Sendik no elige |

**Qué dice el documento mientras tanto: nada.** La política nombra al proveedor —eso sí
está decidido y registrado en ADR-0034— y **describe a las transportadoras por su papel**,
sin lista de nombres y sin país. Es la cláusula reescrita para no necesitar el dato, que es
lo que evita tener que volver a tocarla cuando el proveedor cambie de transportadoras.

**Si la respuesta a la primera es México, no hay trámite adicional.** Se comprobó por
adelantado: la Circular Externa 005 de 2017 de la SIC, numeral 3.2, incluye a **México**
entre los países con nivel adecuado de protección, y su parágrafo 4 admite expresamente la
transmisión a esos países. Fuente: normograma del Ministerio de Relaciones Exteriores,
`cancilleria.gov.co/sites/default/files/Normograma/docs/circular_superindustria_0005_2017.htm`,
consultado el 8 de septiembre de 2026, con última actualización de 30 de septiembre de
2024. Si la respuesta fuera otro país, hay que volver a mirar la lista.

### B · La cobertura de envío — Clase 4, y estaba publicada

Los términos anunciaban **todo el territorio nacional** desde el 5 de septiembre, sin que
nadie lo hubiera contrastado con ninguna transportadora. Lo anunciado es exigible: una
ciudad prometida a la que no llega nadie es un incumplimiento por escrito.

**Cerrado de la única forma honesta mientras el dato no exista:** el numeral 12 ya no
promete cobertura nacional. Dice que la cobertura es la de las transportadoras disponibles
y que **se comprueba contra la dirección de destino al cotizar**, que es verdad hoy y
seguirá siéndolo. RN-080 lo recoge y RN-040 dice qué pasa cuando no hay opción: no se
vende, y se dice.

Cuando el proveedor entregue su cobertura real, se puede volver a anunciar.

### C · Emitir la guía por cuenta del vendedor — Clase 3, para el abogado

Es la decisión de riesgo de esta tanda, y no la cierra una búsqueda.

Sendik es portal de contacto en el sentido del numeral 18 del artículo 5 de la Ley 1480
—añadido por el artículo 6 de la Ley 2439 de 2024— y todo el numeral 2 de los términos se
apoya en eso. Emitir la guía por cuenta del vendedor **preserva** esa posición; venderle el
envío al comprador la rompería, porque haría de Sendik proveedor de un servicio ante el
consumidor.

Lo que hay que validar es si **la combinación** de cobrarle el flete al comprador, elegir
al agregador, asumir la diferencia de la cotización (RN-077) y ofrecer el Respaldo deja a
Sendik tan cerca de ser proveedor del transporte que la distinción no se sostenga en la
práctica. Es hermano del punto abierto B de la entrega anterior —si ofrecer el Respaldo
convierte a Sendik en responsable solidario— y conviene mirarlos juntos.

### D · Los dos de la entrega del 8 de septiembre siguen donde estaban

No los toca nada de esto y siguen abiertos: si las prendas de vestir son «bienes de uso
personal» a efectos de la excepción 7 del artículo 47 —Clase 2, con el concepto SIC
`12-27958` localizado y sin abrir—, y el alcance de la responsabilidad como portal de
contacto —Clase 3—. Ver `entrega-textos-legales-2026-09-08.md`.

---

## Hallazgos de coherencia

Lo que el documento ya promete y la operación todavía tiene que sostener. Todo es Fase 3,
que **no ha empezado**: el documento se escribe ahora para que sea verdad cuando se
construya, no al revés.

1. **El resumen del pedido descargable e imprimible, y el acuse dentro del día siguiente**
   (art. 50 lit. d). Son dos obligaciones nuevas en el documento y ninguna existe. El acuse
   tiene que llevar cuatro datos concretos: plazo de entrega, precio exacto, gastos de envío
   y forma de pago.

2. **El deber propio de Sendik de avisar de la falta de disponibilidad de inmediato.** El
   literal h) se lo impone al portal de contacto, no solo al vendedor. Hoy no hay ningún
   camino por el que Sendik se entere de eso ni por el que lo comunique.

3. **La fecha de entrega tiene que venir del seguimiento y no poder editarse** (RN-079). De
   ella cuelgan la ventana de reclamo y la liberación del pago. Es el mismo razonamiento
   que llevó a firmar los eventos de la pasarela (RN-037): un evento de entrega que
   cualquiera pueda enviar sin firma es una forma de que le paguen a un vendedor antes de
   tiempo. Por eso `SHIPPING_PROVIDER_WEBHOOK_SECRET` no es opcional.

4. **«El costo que ve es el que se le cobra» es ahora una promesa pública** (RN-077). El
   día que el costo real supere la cotización, la diferencia sale de Sendik. RN-041 guarda
   todas las opciones cotizadas —no solo la elegida— para poder medir cuánto cuesta.

5. **La dirección completa solo se manda al emitir la guía**, no al cotizar, que se
   conforma con la ciudad. Está en `datos-personales.md` y encaja con RN-048.

---

## Lo que no cambia

- Las versiones `2026-09-05`, `2026-09-06`, `2026-09-08` y los `borrador-local`
  **siguen en el repositorio y no se tocan**: hay consentimientos cuya evidencia apunta a
  ellas.
- **La política de cookies se queda en `2026-09-06`.** Su variable es independiente y nada
  de esto la afecta.
- **Los textos siguen sin revisión de abogado colegiado**, y publicarlos sin ella sigue
  siendo la decisión expresa del 5 de septiembre. Lo que esta tanda hace es lo de siempre:
  dejarle al abogado lo que de verdad exige su criterio —el punto C— en vez de la búsqueda
  de un artículo.

## Y el paso que publica

**Los archivos nuevos no son la publicación.** Como en la versión `2026-09-06`, que estuvo
dos días en el repositorio sin que la viera nadie, lo que publica es mover la variable:

```
gh variable set LEGAL_TERMS_VERSION   --env dev --body 2026-09-08b
gh variable set LEGAL_PRIVACY_VERSION --env dev --body 2026-09-08b
```

Comprobado el 8 de septiembre de 2026: `dev` sirve todavía `2026-09-08` en las dos. Hasta
que esas dos variables cambien, **esta versión no está publicada** y lo que el sitio
entrega es la anterior.
