# Fuentes oficiales colombianas: cuáles sirven y cómo llegar a ellas

Comprobado el 8 de septiembre de 2026. Si algo de aquí deja de funcionar,
corrígelo en este archivo: es conocimiento operativo, no doctrina.

## El orden en que se intenta

### 1. Régimen Legal de Bogotá — Secretaría Jurídica Distrital

`https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=<id>`

**Es la que mejor funciona hoy** y la que hay que intentar primero. Compila el
texto con **notas de modificación intercaladas**, que es justo lo que hace falta:
al lado del inciso viejo aparece «Inciso modificado por el art. X, Ley Y» y a
continuación el texto nuevo. Con eso se comprueba vigencia y texto de una vez.

Identificadores útiles:

| Norma | `i=` |
|---|---|
| Ley 1480 de 2011 (Estatuto del Consumidor) | `44306` |
| Decreto 1377 de 2013 (reglamenta la Ley 1581) | `53646` |
| Decreto 410 de 1971 (Código de Comercio) | `41102` |
| Decreto 624 de 1989 (Estatuto Tributario) | `6533` |

Los dos últimos se comprobaron el 8 de septiembre de 2026 y sirven para lo que el
Estatuto del Consumidor no cubre: el **contrato de transporte** —quiénes son parte,
qué declara el remitente y hasta dónde responde el transportador— y **si un servicio
está gravado con IVA**, que es lo que decide si un cobro adicional al consumidor
lleva impuesto o no.

**Cómo bajarla.** No uses la herramienta que resume: descarga el HTML y léelo tú.

```
curl -sL --max-time 60 "https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=44306" -o ley.html
```

**Viene en ISO-8859-1, no en UTF-8.** Si la decodificas como UTF-8 revienta.
Decodifica en `latin-1` y guarda en UTF-8 antes de trabajar. Quitar etiquetas y
deshacer las entidades HTML deja un texto plano que se busca bien.

Cuidado al buscar: el compilador escribe **`Artículo 47`**, con minúsculas y
tilde. Buscar `ARTÍCULO 47` no encuentra nada. Busca sin distinguir mayúsculas y
contempla las dos formas. Y no es uniforme entre normas: el Código de Comercio en
el mismo compilador aparece como **`ARTÍCULO 981`**, en versales. Busca siempre sin
distinguir mayúsculas.

**El corte de línea rompe las búsquedas, y no es que falte el texto.** En las normas
largas —el Estatuto Tributario es el caso extremo— el compilador parte los artículos
en muchas líneas y deja la nota «Modificado por el art. X, Ley Y» **separada del
texto que introduce**. Un `grep` de una frase que en la pantalla se lee seguida no
devuelve nada, y es fácil concluir que la fuente no sirve. No es eso: hay que
localizar el artículo y **leer hacia adelante** dos o tres decenas de líneas. Antes
de dar una fuente por inservible, compruébalo así.

**Al leer un artículo modificado, mira dónde termina el texto nuevo.** El compilador
pone primero «&lt;El nuevo texto es el siguiente&gt;» y después, en letra pequeña,
«El texto original del inciso era el siguiente». Las dos versiones quedan pegadas y
se parecen. Copiar de la segunda es el error que este archivo existe para evitar, y
pasa sobre todo cuando la frase vieja es más redonda que la nueva: el artículo 47 de
la Ley 1480 perdió en 2024 el «sin que proceda a hacer descuentos o retenciones por
concepto alguno», que sigue circulando por todas partes precisamente porque suena
mejor.

### 2. Rama Judicial — textos completos en PDF

`https://lector.ramajudicial.gov.co/SIDN/NORMATIVA/TEXTOS_COMPLETOS/...`

Sirve para leyes recientes tal como se promulgaron. Es el **texto original**, sin
las modificaciones posteriores incorporadas: úsala para leer una ley nueva
entera, no para saber cómo quedó un artículo antiguo después de reformarse.

### 3. Gestor Normativo — Función Pública

`https://www.funcionpublica.gov.co/eva/gestornormativo/norma.php?i=<id>`

Buena fuente, pero **el 8 de septiembre de 2026 su cadena de certificados no
valida** y la descarga falla con «unable to verify the first certificate». Si
vuelve a funcionar es una alternativa de primera. No desactives la verificación
del certificado para entrar: una fuente que no puedes autenticar no sirve para
citar.

### 4. Normogramas de entidades públicas

`https://www.cancilleria.gov.co/sites/default/files/Normograma/docs/<archivo>.htm`

Para **circulares y conceptos de la SIC**, que no están en los compiladores de leyes.
Varias entidades publican su normograma con el texto íntegro en HTML plano, y ahí sí
se puede leer y transcribir. La Circular Externa 005 de 2017 —la de los países con
nivel adecuado de protección de datos— se lee entera en el normograma de la
Cancillería, con su fecha de última actualización al pie, que es el dato que permite
saber si la lista sigue siendo esa.

Vienen igualmente en ISO-8859-1 y con las entidades HTML sin resolver: el mismo
tratamiento que el Régimen Legal de Bogotá.

Localizar el archivo es lo único que cuesta: se busca el nombre de la circular y se
entra por el normograma de cualquier entidad que la haya compilado. Es la misma vía
por la que se llegó a esa circular a través del normograma de la DIAN.

### 5. Secretaría del Senado

`https://www.secretariasenado.gov.co/senado/basedoc/...`

**Rechaza la conexión** desde aquí (`ECONNREFUSED`). Queda anotada para no
perder tiempo intentándola.

## Lo que no es fuente

- **Boletines de firmas de abogados y resúmenes de prensa.** Sirven para
  descubrir que una reforma existe y para saber qué buscar. No sirven para citar
  ni para transcribir: un resumen ya eligió qué omitir.
- **Bases de datos de pago con muro** (vLex y similares). Aparecen en los
  resultados de búsqueda y no se pueden leer enteras.
- **Tu memoria.** Especialmente para listas cerradas y números de artículo.

## Cómo se busca lo que no sabes dónde está

La búsqueda web sirve para **localizar**, no para responder. El patrón que
funciona: buscar «Ley N de AAAA modificó artículo X» para enterarte de qué
reformas hay, y después ir al compilador a leer cómo quedó el texto. Al revés
—leer el resumen y darlo por bueno— es como se cuelan los plazos derogados.

Para doctrina de la Superintendencia de Industria y Comercio, los conceptos
tienen número y fecha (`Concepto NNNNNN de AAAA`). Si citas uno, cita esos dos
datos: sin ellos nadie puede comprobarlo.

## Lo que ninguna fuente oficial te va a contestar

Hay preguntas que parecen jurídicas y no lo son: **desde qué país opera un
proveedor, qué figura asume en su propio contrato, a nombre de quién factura, qué
dice su adenda de tratamiento de datos**. No están en ninguna norma porque no son
derecho: son hechos de un tercero. Son la Clase 4 de la skill.

Buscarlas en un compilador es tiempo perdido, y la página comercial del proveedor
**no es fuente** para esto: dice lo que vende, no bajo qué entidad contrata. Las
únicas fuentes que sirven son sus documentos contractuales, su registro mercantil o
una respuesta suya por escrito. Si no tienes ninguna de las tres, el dato no está
verificado por mucho que lo repita internet.
