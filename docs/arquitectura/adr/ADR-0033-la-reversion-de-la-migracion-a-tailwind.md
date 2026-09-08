# ADR-0033 — La reversión de la migración a Tailwind, y qué se recupera de ella

**Fecha:** 2026-09-08
**Estado:** aceptada. Deja la ADR-0032 **revertida, no rechazada**

## Contexto

El 7 de septiembre de 2026 se migró el sistema de diseño a Tailwind 4, CDK y Lucide
(ADR-0032, PR #59), y encima de ella entró un arreglo del anillo de foco (PR #61). Esa
misma noche, a las 23:57, el árbol se devolvió entero al último estado limpio de `main`
—`a8836fc`— con el commit `6a5845f`. El motivo, en sus propias palabras: **el sitio quedó
descuadernado**.

La reversión fue correcta y no se discute aquí. Lo que hay que decidir es lo que quedó
sin decidir esa noche, que son tres cosas distintas.

**Primera: no hay registro de la decisión.** El revert se llevó por delante la propia
ADR-0032, así que el repositorio pasó de tener una decisión escrita a no tener ninguna.
Hoy nadie que lea `docs/arquitectura/adr/` se entera de que la migración existió, de que
se probó, ni de por qué se volvió atrás. Un día de trabajo quedó colgando de la rama
`refactor/tailwind-cdk-design-system` sin nada encima que diga qué es.

**Segunda: la verificación estaba en verde y el sitio estaba roto.** El PR #59 se fusionó
con la canalización pasando entera, incluida la auditoría de axe sobre WCAG 2.2 AA en los
dos modos. Eso no fue un fallo de la suite: **axe no mide maquetación**. Comprueba
contraste, nombres accesibles, jerarquía de encabezados y orden de foco, y ninguna de esas
cosas se rompe porque un espaciado, un ancho o una alineación cambien. El sistema de
comprobaciones no tenía —ni tiene— forma de ver lo único que de verdad falló.

**Tercera, y es la que costó dinero: el revert se llevó cobertura que no era de Tailwind.**
Tres archivos de pruebas nacieron en esa rama y desaparecieron con ella:

| Archivo | Qué auditaba | ¿Depende de Tailwind? |
|---|---|---|
| `e2e/contraste.spec.ts` | Los pares de la paleta en los dos modos, los dos tonos del bronce y la franja de tinta | No. Mide variables CSS resueltas por el navegador |
| `e2e/movimiento-reducido.spec.ts` | Que `prefers-reduced-motion` apague las transiciones y detenga el brillo del esqueleto | No. Mide la regla, sobre una sonda insertada |
| `e2e/foco-cabecera.spec.ts` | El recorrido del tabulador en el menú compacto | No. Mide comportamiento de teclado |

Las tres comprueban reglas de este proyecto que valen igual con `tokens.css` que con
cualquier otro motor: los dos tonos de bronce que no se cruzan, el respeto de las
preferencias del sistema y la navegación completa por teclado. Se perdieron por estar en la
misma rama que la migración, no por hablar de ella.

**Y una de ellas documentaba un fallo real que sigue en `main`.** `foco-cabecera.spec.ts`
nació para cubrir una regresión que la propia migración arregló: con el ciclo del tabulador
limitado al panel, el selector de idioma y el conmutador de tema —que en móvil **se ven**,
porque viven en `.acciones`, fuera de `#menu-principal`— no se alcanzan con teclado mientras
el menú está abierto. El revert devolvió el fallo y se llevó la prueba que lo veía. Es un
incumplimiento de WCAG 2.1.1 sobre dos de los cinco controles que
`docs/ui/accesibilidad.md` da por entregados desde la Fase 1.

## Opciones

**A. Reintentar la migración ya, arreglando lo visual sobre la rama.** Aprovecha que el
trabajo está fresco. Cuesta repetir el mismo riesgo sin haber añadido antes ninguna forma de
verlo venir: la única evidencia disponible seguiría siendo mirar el sitio a mano, que es
exactamente lo que no se hizo la primera vez.

**B. Descartar Tailwind y quedarse en `tokens.css` y `marca.css` para siempre.** Cierra el
tema. Descarta por un fallo de ejecución —una migración fusionada sin comprobación visual—
una decisión que se tomó por sus méritos técnicos y que ADR-0032 argumentaba entera. El
fallo no fue elegir Tailwind: fue no mirar el resultado.

**C. Dejar en pie la reversión, registrar por qué, y recuperar de la rama solo lo que no
depende del motor de estilos.** La migración queda aplazada y su rama conservada como punto
de partida. Lo que vuelve hoy son las tres pruebas y el arreglo del foco, que son deuda de
accesibilidad y no deuda de Tailwind.

## Decisión

**C.** La reversión se mantiene, Tailwind queda **aplazado y no descartado**, y las tres
pruebas de accesibilidad y el arreglo del foco de la cabecera se recuperan ahora, traducidos
al sistema que hoy está en pie.

## Motivo

Separar las dos cosas que la noche del 7 de septiembre quedaron pegadas. Que la maquetación
saliera mal no dice nada sobre si el bronce cumple su contraste, sobre si alguien que pide
menos movimiento lo recibe, ni sobre si el teclado llega al selector de idioma. Esas tres
preguntas tienen la misma respuesta con Tailwind y sin él, y desde el revert **no las
contesta nadie**.

Y no se reintenta hoy porque la lección de la primera vez no es «hubo mala suerte» sino que
la migración se fusionó sin una sola evidencia visual. Mientras no exista esa evidencia
—alguien mirando el sitio o una comparación de capturas—, un segundo intento corre el mismo
riesgo con el mismo instrumental, y la suite volvería a decir que todo está bien.

## Consecuencias

- **Se acepta que el sistema de diseño se queda como está**, con `tokens.css` generado y de
  solo lectura y `marca.css` para lo propio. Las reglas de `CLAUDE.md` y de
  `frontend/CLAUDE.md` que el revert restauró son las vigentes.
- **La rama `refactor/tailwind-cdk-design-system` no se borra.** Es el punto de partida de un
  segundo intento y la única copia del trabajo. Queda nombrada aquí para que no se pierda
  por limpieza.
- **ADR-0032 vuelve al repositorio marcada como revertida.** Borrarla iba contra la regla
  que el propio índice de este directorio fija: «no se borra nunca; una decisión que se
  revierte no se elimina, se marca». Vuelve con su texto íntegro y un encabezado que avisa
  de que el código no la cumple, para que nadie la lea como vigente. Con ella vuelve
  también la ADR-0011 a estar vigente entera: la 0032 la sustituía en la propiedad del
  color y de las medidas, y esa sustitución no llegó a consolidarse.
- **Se asume una duplicación consciente en la comprobación del contraste.**
  `docs/ui/generador/verificar.py` ya mide los mismos pares leyendo las hojas de estilo, pero
  **no corre en la integración continua**: hay que lanzarlo a mano. `e2e/contraste.spec.ts`
  mide lo que el navegador resuelve —la cascada de verdad, incluidas las redefiniciones que
  `.franja-tinta` hace en su propio bloque— y corre en cada pull request. Se quedan los dos:
  el segundo comprueba que el selector aplica, no solo que la variable existe.
- **Lo que sigue sin comprobar nadie es la maquetación**, que es justo lo que falló. Esta ADR
  no lo resuelve y no pretende hacerlo; lo deja anotado como la condición de entrada del
  segundo intento.

## Cuándo revisar

Cuando exista una forma de ver una regresión visual antes de fusionar. Ese es el disparador
para reabrir la ADR-0032 y no otro: ni que la rama se quede vieja, ni que Tailwind saque
versión. Sin esa condición, un segundo intento repetiría el primero.

### La condición se cumplió el 8 de septiembre de 2026

`frontend/e2e/maquetacion.spec.ts` compara doce capturas de página completa —tres pantallas
públicas, los dos modos, dos anchos— contra imágenes de referencia generadas en el
contenedor oficial de Playwright, que es el mismo Linux donde corre la canalización. Cuando
algo se mueve, las imágenes de diferencia viajan en el artefacto que el flujo ya subía, así
que **la regresión se ve antes de fusionar y no después**. El detalle está en
`docs/ui/regresion-visual.md`.

Con esto **la ADR-0032 se puede reabrir**, y reabrirla sigue siendo una decisión aparte que
nadie ha tomado todavía. Lo que cambia es que el segundo intento ya no correría el mismo
riesgo con el mismo instrumental: esta vez la suite sí puede contradecir a quien diga que
todo está bien.

Dos límites que conviene tener presentes antes de confiarse. El primero: la comparación
cubre las pantallas **públicas**, y las que piden sesión —publicación, moderación, panel del
vendedor— siguen sin que nadie mire su maquetación. El segundo: una captura dice que algo
cambió, no que algo esté mal; sigue haciendo falta que una persona mire el resultado y
decida. Lo que se acabó es fusionar sin que nadie se entere.
