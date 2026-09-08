# Frontend — convenciones

Angular 21 con SSR e hidratación · TypeScript estricto · Transloco · TanStack
Query · Vitest · CSS propio sobre los tokens de marca.

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
    ui/                       botón, campo, tarjeta, sello, visor 360
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

- El orden lo fija `src/styles.css` y no se altera: `fuentes.css`, `tokens.css`,
  `tipografia.css`, `marca.css`, antes de cualquier estilo del proyecto.
  **Solo `tokens.css` es generado**: lo escribe `../docs/ui/generador/` y lo copia
  `publicar.py`. Las otras tres son del proyecto y se editan aquí mismo.
  Ubicación de cada activo en `../docs/ui/ubicacion-de-activos.md`.
- **El tipo se aplica con clases de rol**, nunca con `font-size` propio:
  `.tipo-h1`, `.tipo-cuerpo`, `.tipo-titulo-tarjeta`, `.precio`,
  `.tipo-secundario`. Dos familias y solo dos: **Archivo** para titulares y
  precios grandes, **Inter** para todo lo demás, incluidas las cifras. No hay
  familia monoespaciada: los precios alinean con `font-variant-numeric:
tabular-nums` sobre Inter. Tres pesos: 400, 500 y 600.
  `tipografia.css` es la única fuente de verdad del texto y un hook rechaza
  cualquier `font-size` o `font-family` fuera de ella. El nivel del encabezado lo
  decide la estructura del documento; el tamaño, la clase.
- **Ningún HEX ni píxel suelto.** Todo por variable: `var(--color-superficie)`,
  `var(--esp-16)`, `var(--radio-md)`. Un hook bloquea la escritura si aparece un
  color literal.
- Modo oscuro con `data-tema="oscuro"` en el elemento raíz. La preferencia del
  usuario se guarda y, si no la hay, se sigue la del sistema. En SSR se resuelve
  antes de pintar para evitar el parpadeo.
- Medidas fijas del sistema: cabecera 72px en escritorio y 56px en móvil, logo a
  34px (y solo el isotipo a 32px por debajo de 640px, porque el lockup tiene un
  mínimo de 130px de ancho), ancho máximo de contenido 1140px, puntos de quiebre
  en 640px y 1024px.
- Destinos táctiles de 44px como mínimo. Sin excepción.
- **El bronce no es el botón.** El acento bronce aparece una sola vez por
  pantalla y siempre en lo mismo: la insignia de vendedor verificado
  (`.insignia-verificado`), como línea superior de 2px y un icono, nunca como
  relleno grande ni como color de texto. El botón principal va en tinta
  (`.btn-primario`).
- **El bronce tiene dos tonos y no se cruzan.** `#8A6428` solo sobre fondo claro
  y `#B4884A` solo sobre fondo oscuro. `tokens.css` alterna el correcto por modo;
  dentro de `.franja-tinta`, que es oscura en los dos modos, lo hace `marca.css`.
- La regla de corte (`.regla-corte`) es el único elemento decorativo: es el corte
  del isotipo repetido fuera del logo. No se sustituye por una línea continua, y
  va una sola vez por pieza —si ya hay una insignia de verificado a la vista, la
  regla no.
- **Los iconos se dibujan con `.icono`, nunca con atributos propios de trazo.**
  Retícula de 24, área viva de 20, trazo de 2 y terminaciones **rectas**
  (`butt` / `miter`), que es la misma decisión que los cortes rectos del
  isotipo; `stroke="currentColor"` para que hereden el color y funcionen en
  claro, oscuro y deshabilitado sin variantes. Todo eso lo pone la clase, que
  vive en `marca.css`. Un `stroke-width` en el SVG no compite con ella —el CSS
  gana— pero deja la duda de dónde se decide, así que no se escribe. Mezclar
  grosores o terminaciones es lo que hace que un set de iconos se vea amateur.
  Hay dos variantes y solo dos: `.icono-lg` sube el área viva a 24, y
  `.icono-relleno` rellena el trazo con `currentColor`. La segunda existe por una
  razón concreta de accesibilidad y no por gusto: un mismo icono que tiene que
  decir «puesto» y «no puesto» —el control de favorito de HU-011— no puede
  distinguir los dos estados por color. No se usa para nada más.

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
- Contraste mínimo 4.5:1 en texto normal y 3:1 en texto grande, iconos y bordes
  de control. El informe está en `../docs/ui/contraste.md` y lo regenera
  `verificar.py` en los dos modos.
- **Si un componente nuevo usa una combinación de colores que no existía**, se
  agrega ese par a la lista `PARES` de `../docs/ui/generador/verificar.py`. Un
  par que no está en la lista no se está comprobando, y por ahí se cuela un texto
  ilegible.
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
