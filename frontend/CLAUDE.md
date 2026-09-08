# Frontend — convenciones

Angular 21 con SSR e hidratación · TypeScript estricto · Transloco · TanStack
Query · Vitest · **Tailwind 4 + Angular CDK** sobre las primitivas de
`shared/ui` (ADR-0032).

Lee primero `../CLAUDE.md`. Aquí solo está lo específico del frontend.

## Estructura

Las mismas cuatro capas del backend, aplicadas por funcionalidad:

```
src/app/
  core/                       transversal, se carga una vez
    config/                   configuración por entorno
    http/                     interceptores: auth, errores, idioma
    i18n/                     configuración de Transloco
    theme/                    modo claro y oscuro
  shared/                     lo que usan dos funcionalidades y no es de ninguna
    ui/                       primitivas: botón, icono, campo, tarjeta, modal,
                              sello, visor 360. Tailwind para el estilo y CDK
                              para el comportamiento. Son componentes tontos:
                              NO dependen de Transloco ni de TanStack Query,
                              reciben texto ya traducido por input()
    domain/                   vocabulario común: TypeScript puro, sin Angular
    infrastructure/           servicios que hablan con el navegador, no con la red:
                              cámara, acelerómetro, normalizador de fotos (ADR-0026)
    directives/  pipes/
  features/<funcionalidad>/
    domain/                   modelos y reglas puras. Sin Angular.
    application/              casos de uso, puertos, estado
    infrastructure/           adaptadores HTTP, mapeadores DTO a dominio
    presentation/             componentes y rutas
```

- `domain` de una funcionalidad no importa `@angular/*` ni `rxjs`. Es TypeScript
  puro y se prueba sin TestBed.
- Los DTO de la API viven en `infrastructure` y se mapean a modelos de dominio.
  Un tipo generado desde OpenAPI nunca llega a una plantilla.
- `features/x` no importa de `features/y`. Lo compartido sube a `shared` o
  `core`.

## Componentes

- Standalone siempre. Sin `NgModule`.
- `changeDetection: ChangeDetectionStrategy.OnPush` en todos.
- Entradas y salidas con las funciones `input()`, `output()` y `model()`, no con
  los decoradores `@Input` y `@Output`.
- Estado local con `signal` y `computed`. `effect` solo para sincronizar con algo
  externo al marco, nunca para derivar valores.
- Inyección con la función `inject()`, no por constructor.
- Flujo de control de plantilla: `@if`, `@for`, `@switch`, `@defer`. Las
  directivas `*ngIf` y `*ngFor` no se usan en este proyecto.
- `@for` siempre con `track` sobre un identificador estable.
- Un componente de presentación no llama HTTP. Llama a un caso de uso.

Y desde la ADR-0032, para todo componente nuevo:

- **La estructura sale de `shared/ui`**, el estilo de utilidades de Tailwind y
  el comportamiento del CDK. No se escribe un botón, un campo, una tarjeta ni un
  modal desde cero: ya existen como `[sendikButton]`, `<sendik-text-field>`,
  `<sendik-card>` y `<sendik-icon>`.
- **No hay primitiva de modal, y es una decisión.** Se construyó una en la Fase 2
  y se retiró en la Fase 4 sin haberla usado nunca: ninguna pantalla del producto
  necesita una superposición. Las cuatro confirmaciones del sitio —cerrar cuenta,
  decidir una moderación, deshacer una decisión y ver una imagen de identidad—
  son **en línea a propósito**, y cada una lo dice por escrito en su plantilla.
  Antes de escribir un modal, lee esos cuatro motivos: si el tuyo no los
  contradice, probablemente tampoco necesitas uno. Si de verdad hace falta, se
  escribe entonces, con un caso real que lo guíe y sobre `Dialog` del CDK, que
  ya está instalado.
- **Nada de archivos `.css` por componente** ni de estilos en línea. Lo que no
  se pueda expresar con utilidades se declara con `@utility` en `tema.css`.
- **`shared/ui` es tonta y se mantiene tonta.** No importa Transloco, ni
  TanStack Query, ni nada de `features`. Recibe por `input()` y emite por
  `output()`; el texto le llega **ya traducido**, no como clave. Quien traduce es
  quien la usa. Una primitiva que traduce por dentro no se puede probar sin
  montar el catálogo entero ni reutilizar con un texto que venga del servidor.
- Preferir **directiva sobre el elemento nativo** antes que componente que lo
  envuelve, cuando se trata de un control. `[sendikButton]` es una directiva
  justamente por eso: envolver un `<button>` obliga a reenviar a mano `type`,
  `form`, `disabled` y el foco, y cualquier olvido produce un botón que no envía
  el formulario o que no se puede deshabilitar.

## Datos remotos

- TanStack Query para todo lo que venga del servidor: `injectQuery` y
  `injectMutation`, envueltos en un servicio de la capa `application`. Los
  componentes no ven la librería.
- El paquete es `@tanstack/angular-query-experimental` y sigue en estado
  experimental: la versión va **fijada exacta** en `package.json`, sin `^`. Solo
  se sube con una ADR.
- Claves de consulta centralizadas por funcionalidad en un objeto `queryKeys`.
  Nunca un arreglo literal suelto.
- Toda pantalla que carga datos define sus tres estados: cargando (con el
  esqueleto de `marca.css`), vacío y error. La guía visual ya los especifica.

## Estilos

El motor es **Tailwind 4** (ADR-0032). No hay `tailwind.config.ts`: Tailwind 4 se
configura desde CSS, y lo que en la versión 3 era `darkMode: 'class'` y
`theme.colors` aquí son `@custom-variant` y `@theme`, en `src/styles/tema.css`.

- **`src/styles/tema.css` es la fuente de verdad** del color, el espaciado, el
  radio y la sombra. Si falta un valor, se nombra ahí y se documenta.
- **El orden de `src/styles.css` no se altera**, y la primera línea menos que
  ninguna: `@layer theme, base, legacy, components, utilities`. En CSS lo que va
  sin capa gana a lo que va con capa, así que las hojas heredadas van en
  `layer(legacy)`; sacarlas de ahí deja a Tailwind sin poder sobrescribir nada y
  la migración se detiene en seco, sin ningún error visible.
- **No queda ni un `.css` de componente.** Ninguno. Si estás creando uno, para:
  el estilo va en la plantilla y lo que no se pueda expresar así se declara con
  `@utility` en `tema.css`.
- **`marca.css` y `tokens.css` están retiradas.** Ya no se importan. Las seis
  piezas de marca que seguían vivas —`.esqueleto`, `.vacio`, `.regla-corte`,
  `.insignia-verificado`, `.paginacion` y `.franja-tinta`— viven en `tema.css`
  como `@utility`, con sus reglas de contraste intactas. `tokens.css` sigue en
  disco porque es de escritura denegada, pero **no entra en la compilación**: su
  escala de tipo se movió a `tipografia.css` y su color, espaciado y radio los
  tiene `tema.css`.
- `tipografia.css` **no está retirada**: es la única fuente de verdad del texto y
  ahora también de la escala. Va en `layer(roles)` —antes `legacy`— para que las
  utilidades puedan sobrescribirla; el nombre describe lo que es, no de dónde
  viene.
- **La franja de tinta redefine las variables de marca dentro de su bloque**, y
  ahí está el mecanismo: como `@theme inline` mapea cada utilidad a su
  `--brand-*`, todo lo que entra en la franja se invierte solo. El botón
  principal pasa a relleno claro con tinta encima sin una regla propia. Los
  enlaces de la franja se recolorean **menos los botones** (`a:not([data-variant])`):
  sin esa exclusión la regla le gana a `text-on-primary` y el botón queda
  ilegible.
- **La escala numérica de espaciado se calcula, no se declara.** `--spacing-6` no
  existe: dentro de un `@utility` va `calc(var(--spacing) * 6)`. Escribirlo como
  variable deja la declaración inválida y el navegador la descarta entera, sin
  error y sin que ninguna prueba lo vea.
- **Nada de archivos `.css` por componente.** El estilo va en la plantilla con
  utilidades. Si una regla no se puede expresar con utilidades —geometría de
  trazo SVG, por ejemplo— se declara una vez con `@utility` en `tema.css`, no en
  una hoja suelta.
- **El tipo se aplica con clases de rol**, y esto no cambió: `.tipo-h1`,
  `.tipo-cuerpo`, `.tipo-titulo-tarjeta`, `.precio`, `.tipo-secundario`.
  `tipografia.css` sigue siendo la única fuente de verdad del texto y un hook
  rechaza cualquier `font-size` o `font-family` fuera de ella. Dos familias y
  solo dos: **Archivo** para titulares y precios grandes, **Inter** para todo lo
  demás. Tres pesos: 400, 500 y 600. **No se usan las utilidades de tamaño de
  texto de Tailwind** (`text-lg`, `text-2xl`): serían una segunda fuente de
  verdad del tipo.
- **Ningún HEX ni medida suelta.** Todo por utilidad (`bg-surface`,
  `text-text-muted`, `p-4`, `rounded-md`) o por `var(--brand-*)`. Un valor
  arbitrario como `bg-[#fff]` es la misma fuga por otra puerta y el hook lo
  bloquea igual.
- **Modo oscuro con la clase `.dark`** en el elemento raíz, y la variante
  `dark:` en las plantillas. La preferencia se guarda en cookie y, si no la hay,
  se sigue la del sistema; se resuelve en el servidor antes de pintar. Mientras
  `tokens.css` siga importado se escribe **también** `data-tema`: son dos
  marcadores y `ThemeService` pone los dos. Escribir uno solo deja media
  pantalla en el modo contrario.
- **Dos puntos de quiebre y solo dos**: `sm:` (640px) y `lg:` (1024px). Los
  demás están borrados con `--breakpoint-*: initial`, así que `md:` no compila.
  Es deliberado: el sistema define dos y un tercero no lo decidió nadie.
- Medidas fijas del sistema: cabecera 72px en escritorio y 56px en móvil, logo a
  34px (y solo el isotipo a 32px por debajo de 640px, porque el lockup tiene un
  mínimo de 130px de ancho), ancho máximo de contenido 1140px.
- Destinos táctiles de 44px como mínimo (`min-h-touch`). Sin excepción.
- **El bronce no es el botón.** El acento aparece una sola vez por pantalla y
  siempre en la insignia de vendedor verificado, nunca como relleno grande ni
  como color de texto. Por eso `[sendikButton]` tiene cuatro variantes
  —`primary`, `secondary`, `text` y `ghost`— y **ninguna de acento**: el valor
  no existe en el tipo, así que un botón bronce no compila.
- `ghost` es el control neutro: caja de 44 con borde de control y fondo de
  superficie. Es el botón de icono de la cabecera, las acciones del menú de
  sesión y las de `/mi-cuenta`. Entró porque esas mismas diez declaraciones
  estaban copiadas a mano en tres hojas distintas —`avatar-form.css` lo decía
  por escrito—, y una regla copiada tres veces se corrige en dos.
- **Un token de color no cambia de papel entre modos.** `--brand-primary` es
  tinta en claro y un gris medio en oscuro, donde sirve de **fondo** de botón,
  no de color de texto: sobre la superficie oscura da 3.66:1 y no pasa. Es el
  mismo error que cruzar los dos bronces, y ya se cometió una vez en el enlace
  del aviso de privacidad. Lo caza axe, no el compilador.
- **El bronce tiene dos tonos y no se cruzan.** `#8A6428` solo sobre fondo claro
  y `#B4884A` solo sobre fondo oscuro. `tema.css` alterna el correcto por modo;
  dentro de `.franja-tinta` lo sigue haciendo `marca.css` hasta que se migre.
- La regla de corte (`.regla-corte`) es el único elemento decorativo y va una
  sola vez por pieza. Si ya hay una insignia de verificado a la vista, la regla
  no.
- **Los iconos van con `<sendik-icon [icon]="...">`**, nunca con
  `<svg lucideIcon>` suelto ni con atributos propios de trazo. El envoltorio
  existe por una razón concreta: **Lucide dibuja con terminaciones redondeadas y
  el sistema las quiere rectas** (`butt`/`miter`), que es la misma decisión que
  los cortes rectos del isotipo. La geometría la impone el componente y no hay
  entrada para quitarla; mezclar grosores o terminaciones es lo que hace que un
  set de iconos se vea amateur. El icono entra como dato
  (`LucideSearch.icon`), no como cadena: así el compilador avisa de uno que no
  existe y el empaquetador no incluye los mil del paquete. Es decorativo salvo
  que se le dé `label`. Dos variantes y solo dos: `large` sube el área viva a 24
  y `filled` rellena el trazo —esta última solo donde un mismo icono tiene que
  decir «puesto» y «no puesto» sin depender del color—.

## Accesibilidad

Es requisito de aceptación, no un extra. Cada componente entra con:

- HTML semántico antes que ARIA. `aria-*` solo cuando no hay elemento nativo.
- Un solo `h1` por página y sin saltos de nivel.
- Foco visible de 3px en todo lo interactivo. No se elimina el `outline`.
- Todo formulario con `label` asociado; los errores con `aria-describedby` y
  `aria-invalid`.
- Toda imagen con `alt` real, o `alt=""` si es decorativa.
- Navegación completa por teclado, incluido el menú móvil y el visor 360.
- Respeto a `prefers-reduced-motion`, ya contemplado en los tokens.
- Comportamiento complejo con **Angular CDK**, no a mano: `cdkTrapFocus` para
  ventanas modales, `LiveAnnouncer` para lo que cambia sin mover el foco,
  `Overlay` para lo que flota. Una trampa de foco escrita a mano funciona con
  ratón y falla con teclado, que es justo a quien sirve.
- Contraste mínimo 4.5:1 en texto normal y 3:1 en texto grande, iconos y bordes
  de control.
- **Atención, y es deuda declarada (ADR-0032):** `../docs/ui/contraste.md` ya no
  refleja lo que se pinta, porque el color salió del generador y ahora vive en
  `src/styles/tema.css`. La lista `PARES` de `verificar.py` tampoco se actualiza.
  Hasta que esa verificación se rehaga sobre la paleta nueva, **la única garantía
  de contraste es axe** sobre WCAG 2.2 AA en `e2e/accesibilidad.spec.ts`
  (ADR-0016), que comprueba lo que de verdad se renderiza en los dos modos.
  Comprueba más, pero solo después de pintar: ya no hay aviso previo. Un color
  nuevo en `tema.css` se valida ejecutando esa suite, no leyendo un informe.
- Dentro de la franja del hero y del pie se usa la clase `.franja-tinta`, que
  redefine dentro del bloque el anillo de foco, el acento y el botón primario.
  Sin ella el foco de la acción principal es invisible —tinta sobre tinta—, el
  bronce se queda con el tono equivocado y el botón desaparece contra el fondo.

## Internacionalización

- Transloco con `es` por defecto y `en` disponible. Ningún texto visible se
  escribe en una plantilla o en un `.ts`.
- Claves jerárquicas por funcionalidad: `catalog.product.publishButton`. Nada de
  claves con la frase completa.
- El idioma se resuelve en el servidor durante el SSR, no en el cliente, para
  que el HTML llegue ya traducido.
- Fechas, números y precios con las API de `Intl` y la configuración regional
  activa. Los precios usan `.precio`, que es Inter con numerales tabulares.
- Los términos de dominio se traducen según `../docs/producto/glosario.md`, que
  es bilingüe justamente para esto.

## SSR

- El código de servidor no puede tocar `window`, `document`, `localStorage` ni
  `navigator`. Si algo los necesita, se aísla y se protege con `afterNextRender`
  o una comprobación de plataforma.
- La ficha de producto y el listado deben renderizarse en servidor con metadatos
  completos: título, descripción, `og:image` y datos estructurados de producto.
  De esto vive el posicionamiento del marketplace.
- El estado transferido del servidor al cliente no incluye datos privados.

## Pruebas

- Vitest con el constructor `@angular/build:unit-test`. Karma y Jasmine no están
  en el proyecto.
- `domain` y `application` se prueban sin TestBed: son funciones puras.
- Componentes: se prueba comportamiento observable por el usuario, no métodos
  internos. Consultas por rol y por texto accesible antes que por selector CSS.
- HTTP siempre simulado. Ninguna prueba sale a la red.
- Extremo a extremo con Playwright, en **dos suites** que no se mezclan:
  `e2e/` comprueba el HTML que sale del servidor sin llamar a la API (ADR-0006), y
  `e2e-completo/` levanta el backend y PostgreSQL de verdad y recorre los caminos
  de cuentas, la verificación de vendedor, **el ciclo de publicación y moderación** y
  la captura asistida de las ocho tomas por la interfaz. La segunda existe porque
  la primera no puede ver un contrato roto entre las dos mitades: el acuerdo sobre el código `CATALOG_LISTING_INVALID_STATE`
  entre el backend y `ListingReviewStore` solo se comprueba ahí, porque una prueba de
  componente inventa ese código ella misma al simular la respuesta. La compra llega
  con su fase.
- **La cámara en `e2e-completo/` es la falsa de Chromium**, con
  `--use-fake-device-for-media-stream` y el permiso concedido en el proyecto. Su
  patrón tiene zonas de degradado suave y algunos fotogramas caen por debajo del
  umbral de nitidez: el ayudante reintenta, acotado. Bajar el umbral para que pase a
  la primera sería cambiar una regla del producto para acomodar una prueba.
- **Una prueba de componente que pone la sesión antes de crear el componente no
  prueba la carga real.** En una carga de página el componente nace primero y la
  sesión llega después, por la cookie de refresco. Es la diferencia que dejó el
  perfil de `/mi-cuenta` sin cargarse nunca sin que ninguna prueba lo viera; la
  regresión está fijada en `account-page.spec.ts`.
- Cobertura mínima: 80% global, 90% en `domain` y `application`.

## Rendimiento

- Rutas con carga diferida. `@defer` para lo que está bajo el pliegue.
- Imágenes con `NgOptimizedImage`, dimensiones explícitas y formato moderno. La
  foto de producto es 3:4 (`--relacion-foto`); el visor 360 usa los mismos
  fotogramas. El kit de interfaz propone 1:1 y aquí manda ADR-0010: la
  divergencia está anotada en `../docs/ui/ubicacion-de-activos.md`.
- Presupuesto inicial: 200KB comprimido en la ruta principal. Si un cambio lo
  excede, se justifica o se revierte.
