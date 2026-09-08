# ADR-0032 — Tailwind como motor de estilos y fin del kit generado

**Fecha:** 2026-09-07
**Estado:** aceptada

Sustituye a la ADR-0011 (sistema tipográfico por roles) en lo relativo a la
propiedad del color y de las medidas. Lo que la ADR-0011 decidió sobre las dos
familias y los tres pesos sigue vigente.

## Contexto

Desde la Fase 1 el frontend se pinta con un sistema propio: `tokens.css`
generado por `docs/ui/generador/kit_ui.py`, más `tipografia.css` y `marca.css`
escritas a mano. El sistema está auditado, tiene informe de contraste en
`docs/ui/contraste.md` y lo verifica `verificar.py` sobre una lista de pares de
color. Funciona.

Lo que obliga a decidir no es un defecto del sistema, sino una decisión de
producto: se quiere construir el sistema de diseño sobre un stack de utilidades
y componentes headless —Tailwind, Angular CDK, Spartan— para acelerar la
construcción de pantallas y no tener que escribir a mano cada primitiva de
interfaz. Con 46 hojas de estilo propias y 54 componentes, seguir escribiendo
CSS a medida por componente tiene un costo creciente.

La restricción dura es que `frontend/src/styles/tokens.css` es **generado y de
solo escritura denegada** en `.claude/settings.json`: no se puede editar, solo
dejar de usar.

## Opciones

**Seguir con el kit propio y añadir solo Angular CDK.** Costo cero en migración,
conserva el informe de contraste y las garantías de accesibilidad de la Fase 1.
No da el vocabulario de utilidades ni los componentes headless que se buscan, y
deja el costo por pantalla donde está.

**Tailwind como puente, mapeado 1:1 a las variables existentes.** Permite usar
utilidades sin mover la propiedad del color. A cambio deja dos nombres para cada
cosa —`--color-fondo` y `bg-background`— sin que ninguno de los dos sea el
verdadero, que es la clase de ambigüedad que se paga durante años.

**Tailwind como motor, con paleta propia.** Una sola fuente de verdad del color
y de la medida, en `styles/tema.css`. Cuesta la migración de las 46 hojas y
obliga a rehacer la verificación de contraste, que hoy lee del generador.

## Decisión

Tailwind 4 pasa a ser el motor de estilos y `frontend/src/styles/tema.css` la
fuente de verdad del color, el espaciado y el radio. `tokens.css` deja de
generarse para el frontend y se retira cuando termine la migración incremental.

## Motivo

Entre las dos primeras, la segunda es peor que cualquiera de sus extremos: dos
vocabularios simultáneos para el mismo color es la ambigüedad que el proyecto
evita en todas partes —el glosario existe justamente para eso—. Y la primera no
entrega lo que se pidió.

Sobre la tercera hay que ser explícito, porque es donde se pierde algo: se
adopta sabiendo que **invalida `docs/ui/contraste.md` como informe vigente** y
que la garantía de contraste queda, hasta que se rehaga, solo en manos de la
auditoría axe de `frontend/e2e/accesibilidad.spec.ts` (ADR-0016). Esa auditoría
comprueba el contraste real de lo que se pinta, en los dos modos, que es una
comprobación más fuerte que la lista de pares; lo que pierde es la capacidad de
avisar antes de pintar. Es un costo aceptado, no un descuido.

Tres detalles técnicos que la decisión fija y conviene no redescubrir:

- **No hay `tailwind.config.ts`.** Tailwind 4 se configura desde CSS. El archivo
  en JavaScript es de la versión 3 y su motor ya casi no lo lee. `darkMode:
"class"` se escribe aquí como `@custom-variant dark`, y `theme.colors` como
  `@theme`.
- **Las hojas heredadas van en `@layer legacy`.** En CSS el estilo sin capa gana
  al estilo con capa, así que con `marca.css` suelta ninguna utilidad de
  Tailwind podría sobrescribirla y la migración incremental sería imposible: se
  vería funcionar en una plantilla nueva y fallar en toda pantalla existente.
- **El modo oscuro escribe clase y atributo a la vez** durante la transición. El
  atributo `data-tema` lo lee `tokens.css`, que no se puede editar; la clase
  `.dark` la lee Tailwind. Los dos se escriben en el servidor antes de pintar,
  que es lo que conserva el mecanismo antiparpadeo.

## Consecuencias

Se gana un vocabulario de utilidades, componentes headless accesibles del CDK y
de Spartan, y una sola fuente de verdad del color.

Se acepta perder:

- El informe `docs/ui/contraste.md` deja de reflejar lo que se pinta. Hasta que
  se rehaga la verificación sobre la paleta nueva, **la única garantía de
  contraste es axe en las pruebas de extremo a extremo**.
- El generador `kit_ui.py` deja de tener consumidor en el frontend.
- **Al migrar aparecio un hueco que el kit generado arrastraba**: `--color-primario-suave`
  nunca se redefinio para el modo oscuro, asi que se quedaba en el gris claro
  `#E8E8EA` y se usaba como fondo de hover del boton secundario, con el texto en
  `--color-primario` encima. Eso da **3:1** y solo lo ve axe con el raton sobre el
  boton, que es por lo que llevaba ahi sin que nadie lo notara. La paleta nueva lo
  nombra (`--brand-primary-soft` en `.dark`), y las variantes `secondary` y `text`
  del boton dejan de usar `--brand-primary` como color de texto en oscuro. Es un
  argumento a favor de la decision: el token faltaba y el generador no lo decia.
- **El presupuesto de bundle sube de 600 kB a 660 kB de aviso**, con el de error
  intacto en 700. No es un ajuste cosmético para callar la advertencia, así que
  van los números medidos: el inicial pasó a 634.16 kB. De ese crecimiento,
  **12.68 kB son las tres hojas heredadas** —medido quitando sus `@import` y
  volviendo a compilar— y vuelven cuando se retiren; el resto es Tailwind, el
  runtime de Lucide y el CDK. Solo la trampa de foco del CDK en la cabecera
  cuesta **8.6 kB** (625.54 → 634.16), y se paga a sabiendas porque corrige un
  fallo real de teclado.

  El margen de 660 no es para gastarlo: es el techo de la transición. Cuando la
  migración termine, el inicial debería bajar a unos 621 kB y **el presupuesto
  tiene que volver a bajar con él**. Si no baja, alguien se gastó el margen.

- Durante la migración conviven dos sistemas. Es deuda con fecha: termina cuando
  la Fase 3 migre la última plantilla y se retiren los tres `@import` de
  `layer(legacy)`.

Dos dependencias del encargo original **no** se adoptaron, y por el mismo motivo
en los dos casos: npm las marca como obsoletas.

- `@angular/animations` está deprecado; Angular 21.2 ya trae `animate.enter` y
  `animate.leave` nativos en la plantilla. Se usan esos.
- `lucide-angular` está deprecado en favor de `@lucide/angular`, que es el que
  se instaló.

La regla de la ADR-0011 sobre los iconos —retícula 24, área viva 20, trazo 2 y
terminaciones rectas— **sigue vigente y Lucide no la cumple por omisión**: sus
iconos vienen con terminaciones redondeadas. El componente envoltorio de la
Fase 2 tiene que forzar `butt` y `miter`, o el set se verá mezclado.

## Estado

**La deuda de contraste está saldada.** `e2e/contraste.spec.ts` comprueba los
treinta pares del sistema en los dos modos, más los cinco de la franja de tinta,
leyendo los valores **que el navegador resuelve** en vez de un archivo: así se
verifica la cascada de verdad, incluidas las variables que la franja redefine
dentro de su bloque. Corre en cada integración, que es más de lo que hacía
`verificar.py`, y avisa antes de pintar, que es justo lo que axe no puede hacer.

Lo primero que encontró al escribirse fue un fallo que llevaba desde la Fase 1:
**el kit generado nunca definió el color de foco para el modo oscuro**, así que
el anillo era tinta sobre fondo oscuro —1.08:1 contra el fondo y 1.32:1 contra
la tarjeta— y por tanto invisible. No lo veía nadie porque axe no evalúa el
contraste del indicador de foco y la única prueba que lo medía lo hacía dentro
de la franja de tinta, donde el anillo es blanco por definición. Es el tercer
hueco de la paleta generada que destapa esta migración.

**La migración terminó.** No queda ningún `.css` de componente, y `marca.css` y
`tokens.css` están retiradas: las seis piezas de marca que quedaban vivas son
ahora `@utility` en `tema.css` y la escala de tipo se movió a `tipografia.css`,
que es su fuente de verdad y se queda. La capa `legacy` se llama ahora `roles`,
porque lo único que contiene es esa hoja y no tiene nada de heredado.

Al retirar `tokens.css` aparecio el segundo hueco de la paleta generada, del
mismo tipo que el de `--color-primario-suave`: la regla global del anillo de foco
seguia leyendo `--color-foco`, que la franja de tinta ya no redefine, y el foco
del boton principal sobre la franja cayo a **1.6:1**. Lo vio
`e2e/portada.spec.ts`, que mide el contraste del anillo de verdad. Es el
argumento mas fuerte a favor de esta decision: dos huecos que el generador no
sabia que tenia.

## Cuándo revisar

**Sobre el presupuesto de bundle, una prediccion que salio mal y conviene no
repetir.** Al medir que las tres hojas heredadas costaban 12.68 kB se anoto que
al retirarlas el inicial bajaria a unos 621 kB. No bajo: quedo en **640.24 kB**.
Esos 12.68 kB eran el coste de la DUPLICACION —las hojas heredadas conviviendo
con Tailwind—, no el del contenido. Al retirarlas su contenido no desaparecio:
se mudo a tema.css como @utility. Lo que se recupero fue la duplicacion, y eso
ya se habia recuperado antes por otras vias.

El presupuesto queda en **650 kB de aviso**, veinte por encima del inicial real y
cincuenta por encima del que tenia el proyecto. Esa diferencia es el precio del
stack —Tailwind, el runtime de Lucide y el CDK— y ya no va a bajar sola. Si
alguien quiere volver a los 600, el sitio donde mirar es el runtime de Lucide en
el bundle inicial, que entra por los cuatro iconos de la cabecera.

Si al terminar la migración la verificación de contraste sobre la paleta nueva
no se ha rehecho, la decisión de retirar `docs/ui/contraste.md` fue un préstamo
que nadie devolvió: hay que reabrirla y decidir si se reconstruye la
verificación o se declara a axe como única garantía, esta vez por escrito.
