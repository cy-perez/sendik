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

**Cómo bajarla.** No uses la herramienta que resume: descarga el HTML y léelo tú.

```
curl -sL --max-time 60 "https://www.alcaldiabogota.gov.co/sisjur/normas/Norma1.jsp?i=44306" -o ley.html
```

**Viene en ISO-8859-1, no en UTF-8.** Si la decodificas como UTF-8 revienta.
Decodifica en `latin-1` y guarda en UTF-8 antes de trabajar. Quitar etiquetas y
deshacer las entidades HTML deja un texto plano que se busca bien.

Cuidado al buscar: el compilador escribe **`Artículo 47`**, con minúsculas y
tilde. Buscar `ARTÍCULO 47` no encuentra nada. Busca sin distinguir mayúsculas y
contempla las dos formas.

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

### 4. Secretaría del Senado

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
