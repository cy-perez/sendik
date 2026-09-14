# ADR-0041 — Las banderas llegan al frontend y deciden qué se enlaza

**Fecha:** 2026-09-14 · **Estado:** aceptada

## Contexto

El 14 de septiembre de 2026 se recorrió el sitio entero, con sesión y sin ella, a
360, 768 y 1280 píxeles, para comprobar que funcionara como lo que es: un
marketplace para quien compra y para quien vende. El hallazgo más grave no fue de
maquetación. **No había forma de llegar a la tienda.** La cabecera enlazaba las
cuatro páginas informativas de HU-005 y nada más; la portada solo ofrecía crear
cuenta; `/mi-cuenta` administraba el perfil y las sesiones y no enlazaba favoritos,
carrito, direcciones ni publicaciones; y quien modera no tenía un enlace a sus
bandejas. El catálogo, el carrito, la libreta de direcciones y el panel del vendedor
existían y funcionaban, pero solo para quien escribiera la dirección a mano.

No era un descuido: era una consecuencia documentada. HU-004 y HU-005 prohíben
enlazar lo que no funciona, y con una bandera apagada la API responde 404, así que
HU-009, HU-011, HU-015 y HU-016 dejaron el enlace de navegación fuera «hasta que la
bandera se encienda». Las banderas se encendieron en `dev` el 5 y el 10 de
septiembre, y los enlaces no entraron, porque no podían: `docs/operacion/configuracion.md`
lo dice con estas palabras, «el frontend no conoce las banderas —no hay mecanismo
para ello y nunca lo ha habido—». La misma ausencia explica la pantalla rota que ese
documento describe, el catálogo encendido con la búsqueda apagada.

Restricciones: las rutas del frontend existen siempre, con bandera o sin ella, y esa
decisión se mantiene —`rutas.spec.ts` las recorre sin saber qué bandera está
encendida, y la pantalla de error es la que enseña el 404—. Lo que falta no es
esconder rutas sino saber qué enlazar. Y el frontend ya recibe del entorno, por el
estado transferido, todo lo que el sitio dice en voz alta (ADR-0019): las banderas
son otro dato de esa clase, no un secreto.

## Opciones

1. **Un endpoint público `GET /api/v1/features`** que el frontend consulte al
   arrancar. Ventaja: una sola fuente de verdad, el backend. Costo: una petición más
   en cada renderizado en servidor, una ruta nueva que mantener, y el HTML servido
   dependería de una respuesta de la API para pintar la cabecera; con la API caída,
   el sitio informativo —que no la necesita— perdería su navegación.
2. **Las mismas variables `FEATURE_*` en el entorno del servidor de renderizado**,
   leídas por `read-app-config.ts` como el resto de la configuración y transferidas
   al navegador. Ventaja: cero peticiones, cero rutas nuevas, el mismo mecanismo que
   ya lleva la comisión y las versiones legales, y el mismo nombre de variable que el
   backend, declarado una vez por entorno en el flujo de despliegue. Costo: son dos
   copias de la misma decisión, y pueden desalinearse si alguien enciende una
   bandera en un servicio y no en el otro.
3. **Enlazar siempre y dejar que la pantalla de error hable.** Ventaja: nada que
   configurar. Costo: rompe HU-004 y HU-005 y sirve desde la cabecera de todo el
   sitio un enlace que en `prod` lleva a un 404.

## Decisión

Las banderas `FEATURE_CATALOG`, `FEATURE_CHECKOUT`, `FEATURE_PUBLISHING`,
`FEATURE_SELLER_VERIFICATION` y `FEATURE_SEARCH` se leen también en el servidor de
renderizado, viajan al navegador en `AppConfig.features` y **deciden qué se enlaza,
no qué existe**: la cabecera enlaza el catálogo, publicar y el carrito; la portada, el
catálogo; y `/mi-cuenta` ofrece los atajos a favoritos, carrito, direcciones,
publicaciones, verificación y, a quien modera, sus dos bandejas. Cada enlace aparece
solo con su bandera encendida. Las rutas siguen existiendo siempre. `FEATURE_SEARCH`
es la única que decide un control y no un enlace: apagada, el catálogo no pinta la
caja de búsqueda ni los filtros, porque cualquiera de los dos llevaría al 404 del
criterio 26 de HU-014.

## Motivo

Es la opción 2 porque el mecanismo ya existe y ya se confía en él para cifras que
son exigibles legalmente; porque el HTML servido no debe depender de la API para
tener navegación; y porque el riesgo de desalineación se contiene en un solo sitio,
`despliegue.yml`, que pasa cada variable a los dos servicios desde la misma
`vars.FEATURE_*`. El desalineamiento posible es además benigno en las dos
direcciones: una bandera encendida en el frontend y apagada en el backend pinta un
enlace que lleva a la pantalla de error de siempre; al revés, la funcionalidad
existe y no se enlaza, que es exactamente lo que había hasta hoy.

Se decide además **dónde** va cada enlace, porque las historias no lo decían salvo
en un caso: HU-016 fijó `/mi-cuenta` como «el sitio natural» de las direcciones, y
por coherencia es el de todo lo que pertenece a la persona. La cabecera lleva lo
que es de todo el mundo —comprar, vender, el carrito— y el carrito va fuera del menú
compacto, a un toque, porque en un teléfono es la acción que cierra la compra.

## Consecuencias

- `AppConfig` gana `features`, con las cuatro banderas apagadas por omisión y
  también en la configuración de relleno de la compilación. `test-providers.ts` las
  trae encendidas, que es el estado de `dev`; la prueba que necesite verlas apagadas
  sobrescribe `APP_CONFIG` en su propio `TestBed`.
- El criterio 25 de HU-005 —«ninguno lleva a catálogo, publicación ni búsqueda»— se
  lee desde hoy como «con la bandera apagada». La prueba de la cabecera lo comprueba
  en los dos estados.
- `playwright.completo.config.ts` pasa las banderas también al servidor de
  renderizado, así que esa suite ve la navegación completa. `playwright.config.ts`
  pasa solo `FEATURE_SEARCH`, porque `ssr.spec.ts` comprueba que la caja de búsqueda
  llega en el HTML servido; las otras cuatro no, porque sus capturas de maquetación
  se generaron sin tienda y con ellas cambiaría la cabecera.
- `despliegue.yml` pasa al frontend las mismas cuatro banderas que ya pasaba al
  backend. `FEATURE_CHECKOUT` sigue sin pasarse a ninguno de los dos: entra en los
  dos a la vez el día que haya proceso de compra.
- `FEATURE_SEARCH` viaja también, y con ella se cierra el estado roto que
  `configuracion.md` describía —el catálogo encendido con la búsqueda apagada—: sin
  bandera, el catálogo es un listado con sus categorías y sin buscador.
- La comprobación del rol de moderación, que estaba copiada en la ficha del
  catálogo, en la moderación y en la cuenta, pasa a `SessionStore.esModerador`.
- La cabecera cambia de forma cuando hay tienda. Seis enlaces, el carrito, el
  idioma, el tema y una sesión abierta piden 1241px de los 1092 del carril, así que
  en escritorio la navegación pasa a su propia fila debajo de las utilidades —108px
  en total— y en móvil el logo, el carrito y el menú van arriba y las acciones
  debajo, sin fijarse al desplazarse. Con las banderas apagadas la cabecera de
  escritorio es la de siempre; la de móvil no: las acciones van siempre en su segunda
  fila, con o sin tienda, porque envolver «cuando no quepa» daba una fila en unos
  teléfonos y tres en otros. Eso mueve unos píxeles todo lo que hay debajo, así que
  **las seis capturas móviles de `e2e/maquetacion.spec.ts` hay que regenerarlas** en
  el contenedor oficial (`docs/ui/regresion-visual.md`); las de escritorio siguen
  valiendo.
- Se acepta la copia: dos servicios leen la misma variable. El precio de olvidarla
  en uno es un enlace de más o de menos, nunca un dato expuesto.

## Cuándo revisar

Si aparece una tercera pieza que necesite las banderas —un servicio de correo con
enlaces al catálogo, por ejemplo—, tres copias ya son motivo para el endpoint de la
opción 1. Y si alguna bandera llega a decidir algo que no sea un enlace o un control de
pantalla —esconder una ruta, cambiar una regla—, esa decisión no cabe aquí y
necesita su propia ADR.
