# Accesibilidad: qué hay y cómo se comprueba

`docs/producto/alcance.md` pedía «controles de accesibilidad» sin decir cuáles.
Una línea así no se puede dar por cumplida ni por incumplida, así que aquí está la
lista concreta. Cada punto es comprobable, y casi todos los comprueba una prueba.

Las reglas de escritura de componentes están en `frontend/CLAUDE.md`. Este
documento no las repite: dice qué existe hoy y dónde se verifica.

## Los cinco controles

Cinco cosas que la persona usa o que el sitio respeta por ella.

| Control | Dónde | Se comprueba en |
|---|---|---|
| Enlace de salto al contenido | Primer elemento enfocable de cada página (`site-header.html`) | `e2e/shell.spec.ts` |
| Foco visible de 3px | `tokens.css`, con anillo propio dentro de `.franja-tinta` | `e2e/portada.spec.ts`; su contraste, `e2e/contraste.spec.ts` |
| Conmutador de tema claro y oscuro | Cabecera, con `aria-pressed` | `e2e/shell.spec.ts`, `core/theme/theme.spec.ts` |
| Selector de idioma ES/EN | Cabecera, con etiqueta para lector de pantalla | `e2e/shell.spec.ts` |
| Respeto de las preferencias del sistema | `prefers-reduced-motion` en `tokens.css` y `marca.css`; tema preferido si no hay elección guardada | `e2e/movimiento-reducido.spec.ts`, `core/theme/theme.spec.ts` |

**Lo que no hay: control de tamaño de texto.** No está y no se añade por iniciativa
propia: sería funcionalidad de producto que nadie ha decidido. El navegador ya
permite ampliar, y el sistema tipográfico está en unidades relativas para que
ampliar funcione. Si algún día se decide, entra como criterio de una historia.

## Las garantías del sistema

No son controles: son propiedades que todo componente cumple.

- **Un solo `h1` por página** y sin saltos de nivel. Lo comprueban
  `e2e/shell.spec.ts` y `e2e/registro.spec.ts`.
- **Contraste mínimo** 4.5:1 en texto normal y 3:1 en texto grande, iconos y
  bordes de control. Se comprueba en tres pasos y no son el mismo:

  1. `contraste.md` es el informe que escribe el generador del kit, y cubre los
     pares del **modo claro** que el kit conoce.
  2. `generador/verificar.py` no escribe informe: mide 25 pares **en los dos
     modos** leyendo las hojas tal como las sirve el sitio —`tokens.css` más
     `tipografia.css` y `marca.css`— y devuelve código de error si alguno falla.
  3. `e2e/contraste.spec.ts` mide los mismos pares sobre lo que **el navegador
     resuelve**, en los dos modos.

  **El único que impide fusionar es el tercero**, y conviene no confundirlos: los
  dos primeros hay que lanzarlos a mano, así que un cambio que rompa un par pasa
  por delante de ellos sin enterarse. El tercero corre en cada pull request.

  Y no miden lo mismo aunque compartan la lista. `verificar.py` resuelve a mano
  las redefiniciones de la franja de tinta —da por hecho que dentro de ella
  `--color-foco` vale `--color-foco-tinta`—; la prueba lee la paleta desde un
  elemento que está **dentro** de `.franja-tinta`, con lo que comprueba que el
  selector aplica y no solo que la variable existe.
- **Formularios** con `label` asociado, errores con `aria-describedby` y
  `aria-invalid`. Cada formulario de cuentas tiene su prueba de componente.
- **Navegación completa por teclado**, menú móvil incluido, con `Escape` para
  cerrarlo. Lo comprueban `e2e/foco-cabecera.spec.ts` —el recorrido de verdad, que
  una prueba de componente no puede ver porque jsdom no implementa el tabulador— y
  `shared/ui/layout/site-header.spec.ts`, que fija los extremos del ciclo.

  **Con el menú abierto, la región que atrapa el foco es la barra entera y no el
  panel.** El idioma, el tema y la sesión viven fuera de `#menu-principal` y en
  móvil se ven: un ciclo limitado al panel los dejaba visibles y fuera del alcance
  del teclado, que es WCAG 2.1.1. Ocurrió, y está contado en la ADR-0033.
- **Destinos táctiles de 44px** como mínimo.
- **Imágenes** con `alt` real o `alt=""` si son decorativas.

## La auditoría automática

`frontend/e2e/accesibilidad.spec.ts` pasa axe-core sobre WCAG 2.2 AA en todas las
páginas públicas y en los dos modos. Lo decidió ADR-0016. Un fallo de contraste o
de foco rompe la construcción igual que un error de compilación, y ninguna regla
de axe se desactiva para que la suite pase.

**Las pantallas con sesión se auditan en la otra suite**, y esto no es un detalle
de organización. Aquella corre sin backend, así que de una pantalla que necesita
sesión solo puede recorrer su rama anónima: incluirla en su lista da una cobertura
que parece real y no lo es. El primer caso fue el panel del vendedor —lo que se
auditaba eran siete esqueletos con `aria-hidden`, no las cifras—, y desde HU-012 se
audita lleno, y también con su fila de cifras rota, en
`frontend/e2e-completo/accesibilidad-del-panel.spec.ts`.

El nivel que se audita y el formato del informe viven en `frontend/e2e-comun/axe.ts`,
compartidos por las dos: las suites no se mezclan, pero **el objetivo no puede ser
dos cosas**. Con la lista de etiquetas escrita en cada sitio, subirlo en una y
olvidarlo en la otra deja media auditoría en la versión vieja y las dos en verde.

Dos límites que conviene no olvidar:

1. **Un motor automático encuentra una parte de los problemas, no todos.** La
   suite en verde no significa que el sitio sea accesible. La revisión de teclado
   a mano sigue haciendo falta en cada componente nuevo.
2. **Una página que no está en la lista de rutas auditadas no se audita**, y nadie
   se entera. La lista sale de `content-routes.ts` y `legal-routes.ts`, así que una
   página informativa o legal nueva entra sola. Una que no viva en esas constantes
   —la portada, el registro, cualquier pantalla de cuenta— hay que agregarla a
   mano.

## Cuando se agrega un componente

1. Teclado a mano: llegar, activar y salir sin ratón.
2. Si usa una combinación de colores que no existía, agregar ese par a `PARES` en
   **los dos sitios**: `generador/verificar.py` y `e2e/contraste.spec.ts`. Un par
   que no está en la lista no se comprueba, y solo el segundo corre en la
   integración continua.
3. Si es una página pública nueva, agregarla a la lista de
   `e2e/accesibilidad.spec.ts`.
