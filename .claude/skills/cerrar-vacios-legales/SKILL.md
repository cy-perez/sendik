---
name: cerrar-vacios-legales
description: Cierra los vacíos que quedan marcados en un documento legal ya redactado — los `[[ ]]`, los «a confirmar con abogado», los «transcribir aquí la lista literal», los plazos sin dato — separando los que se resuelven con el texto de la norma de los que son de verdad una decisión de riesgo. Úsala cuando un documento legal esté publicado o listo pero con huecos; cuando alguien pregunte «¿esto ya se puede publicar?», «¿qué falta de los términos?», «cierra los pendientes legales», «resuelve lo que quedó marcado»; cuando haya que transcribir la lista literal de un artículo; o cuando haya que averiguar si un caso concreto encaja en una categoría legal. Closes open markers and pending-lawyer items in Colombian legal documents by classifying them as transcription, subsumption or risk decision.
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

Antes de tocar nada, **clasifica cada vacío en una de estas tres clases**. Toda la
skill depende de hacerlo bien.

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

Entrega el inventario antes de resolver nada: cuántos vacíos, en qué archivo y con
qué clase propuesta. Si la clasificación cambia el alcance del trabajo,
confírmala.

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

Hereda las reglas de `textos-legales-comerciales`, y añade dos propias.

**Al transcribir, transcribe.** Sin mejorar la redacción, sin corregir la
puntuación de la ley, sin cambiar el orden. Si la ley numera con `1.` y tu
documento usa viñetas, se conserva la numeración de la ley y se dice de dónde
sale. La gracia de una lista literal es que sea literal.

**Al subsumir, muestra el trabajo.** Primero la categoría legal y su fuente,
después el hecho del negocio, después la conclusión. Nunca al revés: una
conclusión con la norma pegada detrás como adorno es lo que hace que nadie pueda
comprobarla.

## Errores que salen caros

- **Enumerar de memoria una lista cerrada.** El más caro de todos: una excepción
  de más o de menos cambia a quién se le debe dinero.
- **Cerrar en un idioma y dejarlo abierto en el otro.**
- **Dar por cerrada una Clase 2 sin dejar la fuente.** Dentro de un año nadie
  sabrá si aquello se investigó o se supuso.
- **Publicar con la marca puesta.** Si el documento sale con `[[ ]]` a la vista, el
  usuario lo lee. Es peor que el hueco: enseña que el negocio no sabe qué
  prometió.
