---
name: cerrar-vacios-legales
description: Cierra los vacíos que quedan marcados en un documento legal ya redactado — los `[[ ]]`, los «a confirmar con abogado», los «transcribir aquí la lista literal», los plazos sin dato — separando los que se resuelven con el texto de la norma, los que solo puede contestar un tercero y los que son de verdad una decisión de riesgo. Busca también el vacío al revés: la cláusula publicada que afirma algo que ninguna regla, decisión ni configuración del proyecto respalda. Úsala cuando un documento legal esté publicado o listo pero con huecos; cuando alguien pregunte «¿esto ya se puede publicar?», «¿qué falta de los términos?», «cierra los pendientes legales», «resuelve lo que quedó marcado»; cuando haya que transcribir la lista literal de un artículo; cuando haya que averiguar si un caso concreto encaja en una categoría legal; o cuando un documento ya publicado nombre un proveedor, un plazo o una operación que no aparece en ninguna otra parte del proyecto. Closes open markers and pending-lawyer items in Colombian legal documents by classifying them as transcription, subsumption, third-party fact or risk decision, and sweeps the other way for published claims nothing in the project backs.
---

# Cerrar vacíos legales

Esta skill empieza donde termina `textos-legales-comerciales`. Aquella redacta el
documento y deja marcado lo que no puede resolver. Esta coge ese documento con
sus marcas y **cierra todo lo que se puede cerrar de verdad**, que casi siempre es
más de lo que la marca sugiere.

El encargo típico llega así: «quedaron cinco puntos pendientes de abogado». Y al
mirarlos, dos no eran de abogado: eran de ir a leer la norma. Un marcador que
dice «transcribir aquí la lista literal del artículo 47» no pide criterio
profesional; pide una fuente. Confundir las dos cosas deja documentos parados
durante meses esperando una consulta que, para esa parte, no hacía falta.

## La idea central: no todos los pendientes son lo mismo

Antes de tocar nada, **clasifica cada vacío en una de estas cuatro clases**. Toda
la skill depende de hacerlo bien.

### Clase 1 — Transcripción

La respuesta es el texto de una norma. No hay margen: o se copia bien o se copia
mal. «La lista literal de excepciones del artículo 47», «el plazo del artículo
58», «los años de conservación contable».

**Se cierran siempre**, y quedan cerradas. Lo único que hace falta es la fuente
primaria y copiar sin resumir. No requieren abogado, y decir que sí lo requieren
es lo que mantiene un documento bloqueado sin motivo.

### Clase 2 — Subsunción

Preguntan si un hecho concreto cae dentro de una categoría legal. «¿La ropa es un
bien de uso personal?», «¿esto es transferencia o transmisión internacional?»,
«¿un vendedor particular responde por garantía?».

Tienen respuesta razonada —hay doctrina, conceptos de la autoridad,
jurisprudencia— pero admiten discusión. **Se cierran con la respuesta, la
autoridad que la sostiene y el riesgo que queda.** Lo que no se hace es zanjarlas
en silencio: quien lea el documento tiene que poder ver de dónde salió.

Si buscas y no encuentras autoridad, baja el punto a Clase 3. No inventes
doctrina.

### Clase 3 — Decisión de riesgo

No hay respuesta correcta, hay una elección sobre cuánta exposición se acepta.
«¿Presentamos el respaldo como cortesía o como obligación exigible?», «¿cuántos
días hábiles tarda el desembolso?».

**Estas no las cierras tú, y tampoco las cierra un abogado**: las cierra el
titular del negocio, con el abogado explicándole qué compra y qué paga con cada
opción. Lo que aportas es la disyuntiva bien planteada, con las consecuencias de
cada rama, para que la decisión se tome en cinco minutos y no en tres reuniones.

Un error frecuente es marcar como Clase 3 lo que es Clase 2 por pereza de buscar.
Otro, tratar como Clase 2 lo que es Clase 3 y colar una decisión de negocio
disfrazada de conclusión jurídica. El segundo es peor.

### Clase 4 — Dato de un tercero

La respuesta no está en la norma ni en la cabeza del titular: **la tiene alguien
de fuera**, y hasta que la dé no hay nada que decidir. «¿Desde qué entidad y qué
país opera este proveedor?», «¿su contrato lo pone como transportador o como
intermediario?», «¿a nombre de quién factura?», «¿su adenda de tratamiento de
datos cubre lo que exige el artículo 25?».

Se reconocen por una prueba corta: **si la pregunta tiene una respuesta correcta
y el negocio no la puede inventar, es Clase 4.** El titular no elige desde qué
país opera su proveedor; lo averigua.

Estas **no se cierran leyendo**, se cierran **pidiendo**, y por eso se registran
distinto de todas las demás:

| Qué se anota | Por qué |
|---|---|
| La pregunta, redactada tal como se le va a mandar al tercero | Para que la mande cualquiera sin volver a pensarla |
| **Quién** tiene la respuesta, con nombre | Un pendiente sin destinatario no se persigue |
| Dónde debería estar por escrito | Contrato, adenda, términos del proveedor, certificado |
| Qué dice el documento **mientras tanto** | Y la respuesta correcta casi siempre es: nada |

Ese último renglón es el que de verdad importa. **Mientras el dato no llegue, el
documento no afirma nada sobre él.** No se pone el valor probable, ni el que
aparece en la página de mercadeo del proveedor, ni «Colombia» porque la marca
comercial dice Colombia. Una afirmación pública sobre un tercero que nadie ha
comprobado es exactamente lo que la autoridad va a pedir que se sustente.

**El error que esta clase evita es enterrarlas en la Clase 3.** Un dato de un
tercero metido entre las decisiones de riesgo se lee como «esto lo tiene que
pensar el dueño», el dueño lo mira, ve que no es una decisión suya, y lo deja
para después. Así se quedan años. La Clase 3 se cierra en una reunión; la Clase 4
se cierra con un correo, y hay que decir a quién.

## Lo que esta skill no hace

No sustituye la firma de un abogado colegiado, y no la finge. Lo que hace es
**reducir el encargo**: un abogado que recibe un documento con las Clases 1 y 2
resueltas y con fuente revisa en una hora y decide sobre lo que de verdad exige su
criterio, en vez de empezar por buscar el texto de un artículo.

Dilo una vez, al entregar, sin dramatizar y sin repetirlo en cada párrafo.

---

## El flujo: inventariar → clasificar → resolver → registrar

### Fase 1 — Inventaria todos los vacíos

Búscalos en todos los archivos y en todos los idiomas. Un documento bilingüe con
el marcador cerrado en español y abierto en inglés está abierto.

Los marcadores no siempre son `[[ ]]`. Busca también: «a confirmar», «pendiente»,
«por definir», «TBD», «revisar con», un plazo en blanco, una lista anunciada que
no aparece, una cifra redonda sospechosa.

**Cuenta los multilínea.** Una búsqueda por líneas no encuentra un marcador que
ocupa dos, y ese es justo el que lleva dentro la advertencia larga. Busca con el
equivalente a `re.S`.

#### Y después búscalos al revés: las afirmaciones sin respaldo

Un hueco marcado avisa de sí mismo. **El vacío caro es el que no dejó marca**: la
cláusula que afirma algo con toda naturalidad —un proveedor con nombre propio, un
plazo, una cobertura, un país— cuando nadie en el proyecto puede decir de dónde
salió eso.

Es el mismo fallo que el `[[ ]]`, en peor estado: el `[[ ]]` avisa de que falta un
dato y este ya lo dio por bueno, en público y sin fuente. Un marcador cerrado a las
prisas con lo primero que sonaba plausible se ve exactamente así.

El barrido es al contrario que el otro, y es rápido:

1. Saca del documento publicado **cada afirmación comprobable**: nombres propios de
   proveedores, países, plazos, porcentajes, coberturas, listas cerradas.
2. Busca cada una **en el resto del proyecto** —reglas de negocio, decisiones de
   arquitectura, configuración, historias, entregas anteriores—. Un nombre propio se
   busca en un minuto y el resultado no admite interpretación.
3. **La que aparece solo en el documento legal es el hallazgo.** Nada la respalda:
   o es una decisión que se tomó y no se escribió, o es un dato que nadie verificó.

Las dos salidas son distintas y hay que decir cuál es. Si la decisión existió, se
escribe donde le corresponde y el documento deja de estar solo. Si no existió,
**el documento está afirmando algo que el negocio no ha decidido**, y eso se
retira o se decide; no se deja publicado a ver si nadie pregunta.

Un dato de un tercero que llegó así —sin comprobar— vuelve a ser Clase 4, aunque
lleve meses publicado. Llevar tiempo escrito no es haberlo verificado.

Entrega el inventario antes de resolver nada: cuántos vacíos, en qué archivo y con
qué clase propuesta, **y los dos barridos por separado**, porque no se arreglan
igual. Si la clasificación cambia el alcance del trabajo, confírmala.

### Fase 2 — Ve a la fuente primaria

Reglas duras, y la primera es la que más se incumple:

- **Un resumen no es una transcripción.** Si una herramienta te devuelve el texto
  «procesado» por otro modelo, eso sirve para orientarte y **no** para copiarlo al
  documento. Para texto literal, baja el HTML crudo y léelo tú.
- **Nunca de memoria.** Ni un número de artículo, ni un plazo, ni el orden de una
  lista. Si no lo verificaste, no va.
- **Comprueba la vigencia, no solo el texto.** Un artículo puede estar modificado
  por una ley posterior, y los compiladores oficiales lo señalan con notas al pie.
  Copiar el texto original de un inciso ya sustituido es peor que no citar nada.
- **Nunca traigas redacción del RGPD europeo.** Colombia tiene régimen propio y
  conceptos que no se corresponden.

`references/fuentes-oficiales.md` dice cuáles funcionan y cómo llegar a ellas,
incluidas las que fallan y por qué.

### Fase 3 — Resuelve y deja el rastro

Cada vacío cerrado se registra con:

| Qué | Para qué |
|---|---|
| El texto que entra | Es lo que se publica |
| La clase | Para saber si hay que volver a mirarlo |
| La fuente, con dirección y fecha de consulta | Para que otro lo verifique sin repetir la búsqueda |
| El riesgo que queda, si es Clase 2 | Para que nadie lo lea como cerrado del todo |
| A quién se le pidió y qué se le pidió, si es Clase 4 | Para que se pueda insistir, y para que se note si nadie preguntó |

Sin ese rastro el documento vuelve a estar bloqueado en seis meses, porque nadie
recordará si aquello se decidió o se olvidó.

### Fase 4 — Di qué queda

La entrega termina con la lista de lo que **sigue abierto**, con nombre y clase.
Si no queda nada, dilo. Si queda, no lo escondas dentro de un párrafo: es lo
primero que mira quien decide si se publica.

Y comprueba lo evidente antes de cantar victoria: que la versión con los vacíos
cerrados sea **la que el sitio sirve de verdad**. Un documento corregido en el
repositorio y una variable de entorno apuntando todavía a la versión anterior es
un documento no corregido.

---

## Cómo se escribe lo que entra

Hereda las reglas de `textos-legales-comerciales`, y añade tres propias.

**Al transcribir, transcribe.** Sin mejorar la redacción, sin corregir la
puntuación de la ley, sin cambiar el orden. Si la ley numera con `1.` y tu
documento usa viñetas, se conserva la numeración de la ley y se dice de dónde
sale. La gracia de una lista literal es que sea literal.

**Al subsumir, muestra el trabajo.** Primero la categoría legal y su fuente,
después el hecho del negocio, después la conclusión. Nunca al revés: una
conclusión con la norma pegada detrás como adorno es lo que hace que nadie pueda
comprobarla.

**Cuando falte un dato de un tercero, reescribe la cláusula para no necesitarlo.**
Casi siempre se puede: en vez de nombrar al proveedor y su país, se describe la
categoría —«la empresa transportadora que realice la entrega»— y se remite a donde
la lista se mantenga al día. Queda una cláusula verdadera hoy y que no hay que
volver a tocar cuando cambie el proveedor. Lo que no se hace es dejar el hueco a la
vista ni rellenarlo con una suposición: son las dos únicas salidas peores que
reescribir.

## Errores que salen caros

- **Enumerar de memoria una lista cerrada.** El más caro de todos: una excepción
  de más o de menos cambia a quién se le debe dinero.
- **Cerrar en un idioma y dejarlo abierto en el otro.**
- **Dar por cerrada una Clase 2 sin dejar la fuente.** Dentro de un año nadie
  sabrá si aquello se investigó o se supuso.
- **Publicar con la marca puesta.** Si el documento sale con `[[ ]]` a la vista, el
  usuario lo lee. Es peor que el hueco: enseña que el negocio no sabe qué
  prometió.
- **Cerrar un marcador con lo que sonaba plausible.** Un `[[PROVEEDOR]]` relleno con
  un nombre que nadie verificó queda idéntico a uno decidido, y ya no se distingue
  nunca más. Si el dato es de Clase 4 y no ha llegado, la cláusula se reescribe para
  no necesitarlo; rellenarla es convertir un hueco visible en una afirmación falsa.
- **Dar por respaldado lo que solo está publicado.** Que una frase lleve meses en el
  sitio no prueba que alguien la decidiera. Prueba que nadie la ha mirado.
