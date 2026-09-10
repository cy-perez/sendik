# ADR-0036 — El carrito vive en `catalog` y no en `order`

**Fecha:** 2026-09-10 · **Estado:** aceptada

## Contexto

HU-015 implementa el carrito, y la tabla de contextos de `vision-tecnica.md` anuncia
`order` para la Fase 3: «pedidos y su ciclo de vida». Lo natural sería leer que el
carrito, siendo el paso previo al pedido, es de `order`.

No lo es, y la razón se ve en cuanto se escribe la primera clase.

Lo que el carrito guarda es un par persona-publicación. Lo que devuelve al leerse son
agregados `Listing` —título, precio, portada, vendedor, estado— agrupados y sumados. Un
`order/application` que hiciera eso tendría que importar `co.sendik.catalog.model.Listing`,
que es exactamente lo que `ArchitectureTest` prohíbe: el dominio y la aplicación de un
contexto no conocen otro.

Y hay algo más de fondo: **un carrito no tiene ciclo de vida.** No tiene estados, no
tiene transiciones y no reserva nada (RN-089). Un producto está o no está. Todo lo que
`order` promete administrar —los ocho estados del pedido, la retención, la liberación—
empieza cuando el carrito ya terminó.

Es la misma situación que ADR-0035 encontró con la búsqueda, y por eso esta decisión se
parece tanto a aquella.

## Opciones

1. **`order`.** Es lo que la tabla de contextos anuncia. Obliga a importar el modelo del
   catálogo desde otro contexto —lo que la prueba de arquitectura rechaza— o a duplicar
   una forma reducida de `Listing` que habría que mantener sincronizada con la de verdad.
2. **Un contexto propio, `cart`.** Evita el import prohibido de la opción 1 solo si
   duplica el modelo, así que hereda su peor mitad, y agrega un contexto entero para dos
   tablas de cuatro columnas y ocho casos de uso.
3. **`catalog`.** Cero contextos nuevos, cero imports entre contextos, y el agregado
   `Listing` a mano sin rodeos. A cambio, `catalog` crece: ya tenía publicaciones,
   moderación, búsqueda y favoritos, y ahora también el carrito.

## Decisión

El carrito —dominio, casos de uso, persistencia y controladores— vive en `catalog`. En
el frontend, por lo mismo, vive en `features/catalog`.

## Motivo

Porque el carrito es una forma de mirar el catálogo, no un pedido a medias. Lo que hace
es cruzar unas filas propias con publicaciones y presentarlas agrupadas; el día que
exista el pedido, lo que le entregará son grupos ya armados y ahí terminará su trabajo.

Es el mismo razonamiento con el que ADR-0035 decidió que la búsqueda no fuera un contexto
propio, escrito con otras palabras: **un contexto que solo puede llenarse rompiendo la
regla de dependencias no es un contexto, es un paquete vacío con nombre.**

Y en el frontend hay una razón adicional que no admite discusión: `features/x` no importa
de `features/y`. La fila del carrito reutiliza piezas de la tarjeta de producto, así que
un `features/cart` obligaría a mover la tarjeta a `shared/ui` —un refactor que nadie pidió
y que HU-015 no tenía por qué arrastrar—.

## Consecuencias

Lo que se gana: la historia se implementa sin tocar el grafo de contextos, sin duplicar
modelo y sin una sola excepción a `ArchitectureTest`.

Lo que se acepta perder: `catalog` es ya el contexto más grande del proyecto con
diferencia, y esta decisión lo hace crecer otra vez. La tabla de contextos queda diciendo
que `order` es de Fase 3 y `order` sigue vacío, que es confuso hasta que exista el pedido
—y por eso `vision-tecnica.md` lo anota.

El día que `catalog` haya que partir, el carrito será una de las líneas de corte, y no
será gratis: comparte con las publicaciones el mapeador de filas y la carga de portadas.

## Cuándo revisar

- **Cuando exista el pedido.** Si resulta que el carrito y el pedido comparten más de lo
  previsto —la dirección de entrega, la cotización de envío guardada entre visitas— la
  frontera se movió y esta decisión se relee.
- **Si `catalog` tiene que escalar solo.** Es el contexto con el catálogo público dentro,
  que es lo que recibe tráfico anónimo; el carrito con sesión no tiene por qué compartir
  esa suerte.
