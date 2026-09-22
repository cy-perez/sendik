# HU-004 — Portada

**Fase:** 1 | **Estado:** hecha
**Reglas que aplica:** RN-011, RN-015, RN-026, RN-031, RN-034
**Textos:** `docs/producto/textos-web.md`, sección Portada

## Objetivo

Quien llega a Sendik por primera vez entiende en la primera pantalla qué es el
sitio, por qué es seguro comprar y vender aquí, y qué se espera que haga a
continuación.

## Alcance

Entra: portada completa (hero, tres pasos de cómo funciona, tres tarjetas de
confianza), pie de página completo con datos de la empresa, y navegación
principal del encabezado apuntando a las páginas informativas.

No entra: rejilla de productos, buscador, categorías, formulario de publicación,
captación de correos. Todo eso llega con el catálogo en Fase 2. La portada de
esta historia debe poder recibir la rejilla debajo del hero sin rehacerse.

## Criterios de aceptación

**Hero**

1. Dado un visitante que abre `/`, cuando la página termina de renderizarse,
   entonces ve un `h1` con la propuesta de valor y un texto de apoyo que explica
   el respaldo del pago, ambos dentro de una franja con la clase
   `.franja-tinta`.
2. Dado el hero, cuando se inspecciona la página completa, entonces existe
   **exactamente un** elemento con relleno de acción principal: el botón
   principal. Es comprobable contando los elementos con esa clase en el DOM
   renderizado.
3. Dado el botón principal, cuando el visitante lo pulsa, entonces navega a
   `/registro`. Su etiqueta anuncia crear cuenta, no publicar: publicar no
   existe hasta Fase 2 y un botón nombra lo que ocurre al pulsarlo.
4. Dado el hero, cuando se lee el texto bajo el botón, entonces dice que
   publicar es gratis y que solo se cobra cuando se vende. El porcentaje de la
   comisión no se escribe en la plantilla (RN-026).

**Cómo funciona en tres pasos**

5. Dado el bloque bajo el hero, cuando se renderiza, entonces muestra tres pasos
   numerados que describen publicar, vender y cobrar al confirmarse la entrega,
   en ese orden.
6. Dado el bloque de tres pasos, cuando el visitante pulsa su enlace de cierre,
   entonces navega a la página de cómo funciona (HU-005). Si esa ruta todavía no
   existe, el enlace no se renderiza: no se deja un enlace roto en portada.
7. Dado el bloque, cuando se separa visualmente del siguiente, entonces la
   separación la da el espaciado de los bloques. **Ya no lleva regla de corte
   propia** (cambio de ADR-0022): el manual de Sendik permite el corte del
   isotipo una sola vez por pieza, y en el sitio esa vez es el borde superior del
   pie, que sale en todas las pantallas. Con una regla aquí, la portada mostraba
   dos.

**Tarjetas de confianza**

8. Dada la portada, cuando se renderiza el bloque de confianza, entonces muestra
   exactamente tres tarjetas: el pago queda retenido hasta que el comprador
   confirma la entrega (RN-034), los vendedores están verificados (RN-011), y
   toda publicación pasa por moderación antes de aparecer (RN-015).
9. Dadas las tres tarjetas, cuando se leen sus textos, entonces ninguno enuncia
   plazos: ni los 3 días hábiles de la ventana de reclamo, ni tiempos de entrega,
   ni días de desembolso. La política de devoluciones ya existe (RN-050 a
   RN-058) pero su lugar es `/como-funciona` y los términos, no una tarjeta de
   tres líneas: un plazo suelto en portada se lee como promesa y se vuelve
   exigible sin el contexto que lo condiciona. Los tiempos de entrega y de
   desembolso siguen sin definirse y no se escriben en ninguna parte.
10. Dadas las tres tarjetas en una ventana de 360px de ancho, cuando se
    renderizan, entonces se apilan en una columna sin desplazamiento
    horizontal.

**Pie de página**

11. Dado el pie, cuando se renderiza, entonces muestra razón social, NIT y
    dirección de la empresa, tomados de la configuración y nunca escritos en la
    plantilla.
12. Dado el pie, cuando se renderiza, entonces incluye el enlace visible a la
    política de tratamiento de datos, junto a términos y política de cookies.
13. Dado el pie, cuando se renderiza, entonces incluye un enlace al canal de
    contacto y a las páginas informativas de HU-005 que ya existan.
14. Dado el pie en modo oscuro, cuando se compara con el modo claro, entonces
    conserva la franja oscura y el logo monocromo negativo en ambos.

**Transversales**

15. Dada la portada, cuando se solicita el HTML al servidor sin ejecutar
    JavaScript, entonces el titular, los tres pasos, las tres tarjetas y el pie
    ya vienen dentro del documento. De esto vive el posicionamiento.
16. Dada la portada en español y en inglés, cuando se cambia de idioma, entonces
    cambia el texto y no la dirección, y ningún texto queda sin traducir.
17. Dada la portada, cuando se audita con un verificador de accesibilidad,
    entonces hay un solo `h1`, ningún salto de nivel de encabezado, contraste
    mínimo de 4.5:1 en texto normal y foco visible de 3px en todo lo
    interactivo.

### Criterios ajustados después de cerrar la historia

- **El hero cambió de composición el 22 de septiembre de 2026.** Pasa de texto
  centrado a dos columnas en escritorio: texto a la izquierda y una foto a la
  derecha (`frontend/public/hero.webp`, precargada como imagen principal de la
  primera pantalla, con `alt` real en `home.hero.imageAlt`). En móvil y tableta
  se apila con el texto arriba. La foto va en rectángulo y no con corte
  diagonal: el corte del isotipo es la firma y en esta pantalla ya la gasta el
  borde superior del pie.
- **Dos botones en vez de uno, y el criterio 2 se mantiene.** «Empieza a
  vender» es el primario y el único relleno de la pantalla; sigue llevando a
  `/registro`. «Empieza a comprar» es el secundario, de contorno, lleva a
  `/catalogo` y solo se pinta con `FEATURE_CATALOG` encendida (ADR-0041).
  Reemplaza al enlace de texto que entró con esa ADR.
- **La etiqueta del criterio 3 ya no dice «crear cuenta».** La prohibición de
  nombrar «publicar» existía porque publicar no existía en Fase 1; desde la
  Fase 2 existe. El destino no cambia.

### Criterios retirados durante la implementación

- **El botón principal según la sesión.** Decía que con sesión abierta llevara a
  `/mi-cuenta`. Es incompatible con el criterio 15: el servidor no puede saber si
  hay sesión, porque el token vive en memoria del cliente y se recupera con la
  cookie de refresco ya en el navegador. Cualquier destino condicional deja el
  hero sin botón en el HTML servido o lo cambia al hidratar, que es el parpadeo
  que los casos borde prohíben. El CTA lleva siempre a `/registro`; quien ya
  entró tiene su cuenta en la cabecera, que es donde el sitio la muestra.
- **Navegación del encabezado y menú móvil.** Pasan a HU-005. Deben llevar a las
  páginas informativas, y hasta que esas páginas existan sería un componente
  vacío que no se puede probar.

## Casos borde

- **Configuración incompleta.** Si falta el NIT o la dirección, el pie omite ese
  dato en vez de pintar `undefined`, y el servidor lo registra como aviso.
- **Sesión que se recupera tarde.** El estado del botón principal no debe
  parpadear entre "crear cuenta" y "mi cuenta" durante la hidratación: se
  resuelve antes de pintar o se muestra el estado neutro hasta saberlo.
- **Ventana de 320px.** Ningún desplazamiento horizontal, incluido el titular
  largo en inglés.
- **`prefers-reduced-motion`.** Cualquier animación de entrada se desactiva.
- **Modo oscuro en el primer render.** La franja oscura no debe cambiar de color
  al hidratar.

## Diseño

La maqueta de referencia es `docs/ui/index.html:450-505`. Se toma de ahí el
hero, el pie y la rejilla de tarjetas; **no** se toma la rejilla "Recién
publicado", que es de Fase 2, ni la tercera tarjeta de devoluciones.

- Franja de tinta del hero y del pie con `.franja-tinta`, que redefine el anillo
  de foco: sin ella el foco del botón principal es tinta sobre tinta.
- Titular con `.tipo-display`, apoyo con `.tipo-entradilla`, tarjetas con
  `.tipo-titulo-tarjeta` y `.nota`. Ningún `font-size` propio.
- Logo horizontal a 34px en escritorio, isotipo solo en móvil, monocromo
  negativo en el pie y en modo oscuro.
- La portada no carga datos remotos, así que no tiene estado de carga ni de
  error. El único estado condicional es el del botón principal según haya sesión
  o no.
- Puntos de quiebre en 640px y 1024px. Destinos táctiles de 44px como mínimo.
- Si el bloque de tres pasos o el de tarjetas queda bajo el pliegue, se carga con
  `@defer`.

## Notas técnicas

Sin backend nuevo: no hay endpoints, tablas ni migraciones en esta historia.

Configuración: `AppConfig` del frontend necesita cuatro campos que hoy solo
existen en el backend (`docs/operacion/configuracion.md:68-71`):
`companyName`, `companyTaxId`, `companyAddress` y `supportEmail`. Viajan en el
estado transferido, como el resto de `AppConfig`. Son datos públicos de la
empresa, no personales. Habría que agregarlos también a la tabla de variables
del frontend en `configuracion.md`, porque hoy la sección solo lista las siete
que lee el servidor de renderizado.

La comisión del criterio 4 sale de `COMMISSION_RATE`, que ya existe en el
backend y tendría que exponerse igual al frontend, o bien el texto se redacta
sin cifra. Lo segundo es más simple y evita un valor de negocio más viajando al
navegador; la decisión se toma al implementar.

Claves de Transloco nuevas bajo `home.*`: `home.hero.*`, `home.steps.*`,
`home.trust.*`. El pie amplía `layout.footer.*` con `company`, `contact` y los
títulos de columna. La clave `home.underConstruction` desaparece.

Componentes: el bloque de tres pasos y la tarjeta de confianza son de portada,
así que viven en `features/home/presentation`. Solo suben a `shared/ui` si
HU-005 los reutiliza; no se generalizan por adelantado.

El pie y el encabezado ya existen en `shared/ui/layout` y se amplían ahí mismo.
Los enlaces a las páginas informativas salen de un módulo de rutas en `core`,
igual que `core/routes/legal-routes.ts`, para que no pueda existir un enlace que
apunte a una ruta que no está.

## Pruebas requeridas

- Componente (Vitest, sin red): existe un solo `h1`; hay exactamente un elemento
  con el relleno de acción principal; el botón apunta siempre a `/registro`; se
  renderizan tres tarjetas y tres pasos; el enlace a cómo funciona no aparece
  mientras la ruta no exista; ningún texto dice que Sendik guarde o custodie el
  dinero (RN-031).
- Pie: renderiza los datos de empresa que vienen de `AppConfig` y los omite
  cuando faltan; los tres documentos legales están presentes y apuntan a
  `RUTAS_LEGALES`.
- Servidor: el HTML servido contiene el titular, los tres pasos, las tres
  tarjetas y el pie antes de cualquier hidratación.
- Traducción: toda clave usada en las plantillas existe en `es.json` y en
  `en.json`. Una prueba que compare los dos árboles evita el texto sin traducir.
- Extremo a extremo (Playwright): portada a registro por el botón principal;
  portada legible y sin desplazamiento horizontal a 360px; navegación completa
  por teclado del encabezado y del menú móvil. **Hecho.** Los dos primeros en
  `e2e/portada.spec.ts`, que además baja a 320px y comprueba el foco visible y
  que ningún enlace esté roto. El tercero vive en `e2e/contenido.spec.ts`, con la
  navegación del encabezado y el menú móvil, porque la navegación se construyó en
  HU-005: aquí no había todavía ninguna página a la que llevar.
- Accesibilidad automatizada sobre la portada en los dos modos, claro y oscuro.
  **Hecho.** En `e2e/accesibilidad.spec.ts`, con axe-core sobre WCAG 2.2 AA y
  sin ninguna regla desactivada. El motor se decidió en ADR-0016. Conviene
  recordar lo que esa ADR deja escrito: un motor automático no dice si el orden
  de lectura tiene sentido ni si la página se puede usar con un lector de
  pantalla, así que la revisión a mano sigue haciendo falta.

## Qué habría que agregar antes de implementar

No lo agrego todavía; queda para decidir contigo.

**Reglas de negocio.** Ninguna nueva es imprescindible, pero las tres tarjetas
prometen cosas en portada y conviene que cada promesa apunte a una regla. Hoy la
retención del pago está en RN-034 y la moderación en RN-015; la verificación del
vendedor está repartida entre RN-011 y RN-013. Si se quiere una sola regla que
diga qué se le promete públicamente al visitante, habría que escribirla.

**Modelo de datos.** Nada. Esta historia no guarda información de nadie.

**Glosario.** Resuelto: **respaldo** entra como `Backing`, y con él la retención
y la liberación del pago y la moderación. `Escrow`, custodia y garantía quedan
en la lista de palabras que no se usan, porque describen figuras financieras que
Sendik no ejerce (RN-031). **Tarjeta de confianza** y **franja de tinta** no van
al glosario: son términos de interfaz, no de dominio, y viven donde se definen,
`frontend/src/styles/marca.css`. La franja se llamaba «franja oscura» hasta el
cambio de identidad; hoy es `.franja-tinta` (ADR-0022).

**Configuración.** Los cuatro campos de empresa y la decisión sobre
`COMMISSION_RATE` descritos en las notas técnicas.
