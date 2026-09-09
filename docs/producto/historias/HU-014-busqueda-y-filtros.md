# HU-014 — Búsqueda y filtros del catálogo

**Fase:** 3 | **Estado:** pendiente
**Reglas que aplica:** RN-064, RN-068, RN-081, RN-082, RN-083, RN-084, RN-085,
RN-086, RN-087, RN-088

Es la primera historia de la Fase 3, que se abrió el 8 de septiembre de 2026. Se
eligió esta para arrancar porque es **la única línea de la fase que no espera a
nadie**: el pago necesita a Wompi y el envío necesita las cuatro respuestas de
Skydropx que `entrega-textos-legales-2026-09-08b.md` dejó sin contestar. La
búsqueda solo necesita lo que ya está en la base de datos.

## Objetivo

Quien entra a Sendik puede escribir lo que busca y acotar por lo que le importa
—categoría, condición, talla, color y precio— en vez de recorrer el árbol de
categorías hasta encontrarlo. Hoy la única forma de llegar a un producto es
navegar seis familias y treinta y una categorías, y quien busca algo concreto no
lo hace así.

## Por qué ahora

HU-009 dejó el catálogo navegable y escribió en su propio alcance que la búsqueda
«es Fase 3 con Typesense, y así lo dice `alcance.md`». Los campos por los que se
filtra se declararon de lista cerrada desde HU-007 **justamente para esto**: el
comentario de `Color` dice, con esas palabras, que «el color es filtro de catálogo
y en texto libre no filtra». La decisión de acotarlos está tomada desde agosto; lo
que faltaba era la pantalla que los usa.

Y hay una razón de secuencia: la búsqueda no toca el pedido, ni el pago, ni el
envío. Se puede entregar entera y quedarse quieta mientras el resto de la fase
espera a terceros.

## Alcance

Entra:

- **Caja de búsqueda por texto**, sobre el título y la marca del producto
  (RN-082).
- **Filtros combinables** sobre lo que el vendedor ya declara: categoría,
  condición, talla, color y rango de precio.
- **Orden**: relevancia, más recientes primero, precio de menor a mayor y de
  mayor a menor (RN-088).
- **Los filtros y el texto viven en la dirección**, para que un resultado se
  pueda compartir y recargar, y para que el renderizado en servidor de ADR-0025
  siga sirviendo la primera pantalla ya resuelta.
- **El vacío honesto**: cuando no hay resultados se dice, con la salida de quitar
  filtros a la vista (RN-086).

No entra:

- **Typesense.** La primera implementación va contra PostgreSQL detrás del puerto
  `SearchEngine`, que es la salida que ADR-0008 dejó escrita y que ADR-0035 toma.
  **Con ello no hay tolerancia a errores de escritura**: quien escriba «camisa
  oxfrod» no encuentra nada. Está aceptado a sabiendas y con una señal de
  revisión, no olvidado.
- **Facetas con conteo.** Ninguna opción de filtro dice cuántos resultados tiene
  detrás. Es lo que evitaría filtrar hacia un vacío, y es justo lo que Typesense
  da de fábrica y PostgreSQL cobra en consultas de agregación aparte. Se decide
  cuando haya catálogo real que contar.
- **Filtro por marca.** La marca **no es lista cerrada**: es texto libre y
  opcional, porque mucha prenda de segunda no tiene marca legible
  (`V9__catalog.sql`). Un filtro sobre texto libre no filtra —«Nike», «nike» y
  «NIKE» serían tres— y por eso la marca entra en la búsqueda por texto y no como
  filtro (RN-085).
- **Búsqueda de vendedores.** Se busca producto, no persona. El perfil público del
  vendedor se alcanza desde una ficha, como hoy.
- **Historial de búsquedas, sugerencias y autocompletado.** Guardar lo que alguien
  buscó es un dato personal nuevo y no hay ninguna finalidad autorizada que lo
  cubra: entraría por `docs/operacion/datos-personales.md` y no por esta historia.
- **Ordenar por popularidad o por favoritos.** RN-070 dice que los favoritos son
  privados y que no existe cifra pública derivada de ellos. Un orden por favoritos
  es esa cifra por la puerta de atrás.
- **Guardar una búsqueda y avisar cuando aparezca algo.** Necesita correo
  recurrente y una preferencia de contacto que nadie ha decidido.

## Criterios de aceptación

### La búsqueda por texto

1. Dado cualquiera, con sesión o sin ella, cuando busca un texto que aparece en el
   título de una publicación `PUBLISHED`, entonces esa publicación está entre los
   resultados.
2. Dado un texto que aparece en la **marca** de una publicación `PUBLISHED`,
   cuando se busca, entonces esa publicación está entre los resultados (RN-082).
3. Dado un texto que aparece **solo en la descripción** de una publicación, cuando
   se busca, entonces esa publicación **no** está entre los resultados (RN-082).
   El criterio se escribe sobre una ausencia a propósito: es la consecuencia
   aceptada de la regla y tiene que ser visible el día que alguien la cambie.
4. Dada una publicación en cualquiera de los otros seis estados de RN-061, cuando
   se busca un texto que sí aparece en su título, entonces no está entre los
   resultados (RN-081), tampoco para su propio dueño con la sesión abierta.
5. Dado un texto con tildes o sin ellas, cuando se busca «camisón» o «camison»,
   entonces los resultados son los mismos: la acentuación no parte el resultado.
6. Dado un texto en mayúsculas, minúsculas o mezclado, cuando se busca, entonces
   los resultados son los mismos.
7. Dado alguien que está navegando una categoría, cuando escribe en la caja de
   búsqueda, entonces busca **en todo el catálogo** y no dentro de esa categoría
   (RN-083). La categoría deja de estar aplicada y se puede volver a poner como
   filtro.
8. Dada una búsqueda sin texto y sin filtros, cuando se abre, entonces se ve el
   catálogo de HU-009 tal cual: lo publicado, lo más reciente primero. Buscar nada
   no es una pantalla distinta.

### Los filtros

9. Dado un filtro de categoría, cuando se aplica sobre una familia, entonces se
   ven las publicaciones de todas las categorías publicables que cuelgan de ella,
   igual que hace hoy el catálogo, porque no se publica en una familia sino en una
   categoría suya.
10. Dado un filtro de condición, cuando se eligen una o varias de las cuatro de
    RN-064, entonces se ven solo las publicaciones que declaran alguna de ellas.
11. Dado un filtro de color, cuando se eligen uno o varios de los quince valores
    cerrados, entonces se ven solo las publicaciones que declaran alguno.
12. Dado un filtro de talla, cuando se elige una talla, entonces se ven solo las
    publicaciones de **ese mismo sistema de talla** con ese valor (RN-087): una M
    por letra y una 38 numérica no son comparables y no se mezclan.
13. Dado un rango de precio, cuando se fija un mínimo, un máximo o los dos,
    entonces se ven solo las publicaciones cuyo precio base cae dentro, con los dos
    extremos incluidos.
14. Dados varios filtros a la vez, cuando se aplican, entonces se combinan
    **exigiendo todos**: quien pide azul y talla M no recibe lo azul de otra talla.
    Dentro de un mismo filtro con varios valores basta con uno: azul o verde.
15. Dado cualquier conjunto de filtros y texto, cuando se aplica, entonces la
    dirección del navegador los lleva, y abrirla de nuevo en otra pestaña devuelve
    exactamente el mismo resultado.

### El orden

16. Dada una búsqueda con texto y sin orden pedido, cuando se resuelve, entonces
    los resultados vienen por relevancia (RN-088).
17. Dada una búsqueda sin texto y sin orden pedido, cuando se resuelve, entonces
    los resultados vienen por publicación más reciente primero, que es el orden del
    catálogo.
18. Dado un orden por precio, cuando se pide de menor a mayor o de mayor a menor,
    entonces los resultados vienen así, y el empate entre dos precios iguales se
    resuelve siempre igual, de modo que paginar no repite ni se salta ninguna.
19. Dada cualquier búsqueda, cuando se resuelve, entonces **ninguna publicación
    aparece antes por haberlo pagado** (RN-084). No hay resultado patrocinado ni
    orden comprado, y no hay campo en la respuesta que pueda expresarlo.

### El vacío y la paginación

20. Dada una búsqueda que no casa con nada, cuando se resuelve, entonces se dice
    que no hay resultados para eso, se ve qué filtros están puestos y hay una
    salida para quitarlos (RN-086). No se rellena con resultados aproximados ni con
    «quizás te interese».
21. Dada una búsqueda con más resultados de los que caben en una página, cuando se
    pide la siguiente, entonces continúa por cursor sin repetir ni saltarse
    ninguna, igual que el catálogo.
22. Dado un cursor creado con un orden, cuando se manda pidiendo otro orden
    distinto, entonces se responde 400 y no un listado incoherente: el cursor solo
    tiene sentido dentro del orden con el que nació.
23. Dado un `limit` fuera de rango, un cursor ilegible, un color que no está en la
    lista o un precio que no es un número entero de pesos, entonces se responde 400
    y no se ignora el parámetro en silencio.

### La pantalla

24. Dada una búsqueda con texto o con cualquier filtro puesto, cuando la sirve el
    servidor, entonces la página **no se indexa**: lleva `noindex`. Las categorías
    y las fichas siguen indexadas, que es de donde vienen las visitas.
25. Dada la primera pantalla de una búsqueda, cuando llega desde el servidor,
    entonces los resultados vienen **en el HTML** y no en un esqueleto que se
    rellena después (ADR-0025).
26. Dado que `FEATURE_SEARCH` está apagada, cuando se pide la ruta o el endpoint
    con parámetros de búsqueda, entonces no existe: 404 y no 403, como hacen las
    otras tres banderas.

## Casos borde

- **Texto solo con espacios, o con signos de puntuación y nada más.** Se trata como
  búsqueda sin texto: el catálogo, no un vacío.
- **Texto larguísimo.** Se acota en el borde con un tope declarado y se responde
  400 por encima; no se recorta en silencio.
- **Precio mínimo mayor que el máximo.** Es 400, no un vacío: un vacío haría pensar
  que no hay nada de ese precio.
- **Precio negativo o con decimales.** El peso colombiano se guarda como entero
  (RN-029) y aquí se valida igual.
- **Una categoría que ya no está en el árbol.** 404, como en el catálogo, y no un
  listado vacío que se leería como «existe y no tiene nada».
- **Una publicación que se vende o se archiva mientras alguien pagina.** Deja de
  casar y desaparece de las páginas siguientes. Es lo mismo que ya pasa en el
  catálogo y es la razón de paginar por cursor.
- **Dos resultados con la misma relevancia**, que con `ts_rank` es lo normal y no la
  excepción. El identificador desempata, como hace hoy el catálogo con
  `published_at`.
- **Una publicación sin marca.** La marca es opcional: no entra en el texto buscable
  de esa publicación y no la excluye de nada.
- **Búsqueda en inglés sobre publicaciones escritas en español.** No se traduce nada
  y no se promete que funcione: el sitio se sirve en dos idiomas, pero lo que el
  vendedor escribió está en el idioma en que lo escribió.

## Diseño

- La caja de búsqueda vive en la cabecera del catálogo, y los filtros en un panel
  lateral en escritorio y en una hoja que se abre desde un botón en móvil. Nada de
  esto es acento: el bronce sigue apareciendo una sola vez por pantalla y en lo de
  siempre.
- **Los filtros puestos se ven siempre**, como fichas que se quitan una a una,
  también cuando el panel está cerrado en móvil. Un filtro activo e invisible es la
  causa número uno de «aquí no hay nada».
- Estados de la pantalla: cargando, con resultados, **vacío** —con los filtros
  puestos a la vista y la salida para quitarlos— y error de red, que se distingue
  del vacío. Que no haya resultados y que la consulta haya fallado no se pueden ver
  igual.
- Los resultados reutilizan `product-card`, que ya existe desde HU-009.
- Teclado y lector de pantalla: la caja de búsqueda se alcanza con tabulador, el
  número de resultados se anuncia al cambiar, y cada filtro quitable es un botón con
  nombre accesible que dice qué quita. Lo audita `revisor-accesibilidad` y la suite
  de axe de ADR-0016.

## Notas técnicas

- **Puerto `SearchEngine` en `application`**, con una implementación en
  `infrastructure` contra PostgreSQL (ADR-0035). El caso de uso no sabe con qué se
  busca.
- **Endpoint:** se extiende `GET /api/v1/listings`, que ya es el catálogo, con `q`,
  `category`, `condition`, `sizeSystem`, `size`, `color`, `minPrice`, `maxPrice` y
  `sort`. No nace una ruta nueva: buscar es listar el mismo recurso con más
  condiciones, y un `/search` aparte obligaría a duplicar la paginación, la bandera
  y la forma de la respuesta. `contrato-api.md` se actualiza al implementarlo.
- **El cursor cambia de forma.** Hoy lleva `(published_at, id)`. Con orden por precio
  o por relevancia tiene que llevar la clave del orden que corresponda y **el orden
  con el que nació**, para poder rechazar con 400 el cursor que llega bajo otro orden
  (criterio 22). Sigue siendo opaco en el borde y tipado dentro.
- **Migración nueva, `V18`:** índice para la búsqueda por texto sobre título y marca,
  sin acentos y sin distinguir mayúsculas, y los índices que necesiten los filtros y
  el orden por precio. Parcial sobre `status = 'PUBLISHED'`, por lo mismo que `V14`:
  de los siete estados la búsqueda solo mira uno.
- **Bandera `FEATURE_SEARCH`**, apagada por omisión, con el mismo patrón que las tres
  que ya existen: con la bandera apagada el controlador no se crea y la ruta responde
  404.
- **Frontend:** el texto y los filtros son parámetros de consulta sobre las rutas de
  catálogo que ya existen. Escribir texto desde una categoría navega a
  `/catalogo?q=…`, que es RN-083 hecha visible. La etiqueta `noindex` se enciende en
  cuanto la dirección lleva texto o cualquier filtro, y las rutas de categoría
  desnudas siguen indexadas.
- **Claves de Transloco nuevas** para la caja, cada filtro, cada orden, el vacío y el
  error, en los dos idiomas. Ningún texto en la plantilla.

## Pruebas requeridas

- **Dominio y aplicación.** Cada regla nueva con su prueba y su nombre:
  `deberia_cumplir_RN_081_…` y las demás. La combinación de filtros, el rango de
  precio con sus dos extremos incluidos, y el rechazo del cursor bajo otro orden.
- **Integración con base de datos real**, no con dobles: es donde se comprueba que la
  búsqueda sin tildes encuentra lo acentuado y que el índice se usa. Una búsqueda que
  pase por el índice equivocado da el mismo resultado y no se nota en ninguna otra
  prueba.
- **Contrato del endpoint**: los 400 del criterio 23, el 404 con la bandera apagada, y
  que ninguna publicación que no esté `PUBLISHED` aparezca nunca.
- **Extremo a extremo**, en `e2e-completo/`: publicar, aprobar, buscar por el título y
  encontrarla; filtrar hasta el vacío y ver la salida; compartir la dirección y
  recibir el mismo resultado.
- **Renderizado en servidor**: que los resultados de la primera pantalla estén en el
  HTML que llega, y que la etiqueta `noindex` esté cuando toca y no esté cuando no.
- **Accesibilidad** con axe sobre WCAG 2.2 AA en los dos modos, incluida la pantalla
  vacía, que es la que suele quedarse sin revisar.

## Decisiones tomadas al escribirla

Las cuatro se tomaron el 8 de septiembre de 2026 y quedan como reglas para que no
haya que volver a tomarlas:

1. **El texto busca en título y marca, no en la descripción** (RN-082). La
   descripción es texto libre y largo; que decida quién aparece primero es invitar a
   rellenarla de palabras, y hoy no hay ni regla ni control que lo frene.
2. **La búsqueda es sobre todo el catálogo** (RN-083), no dentro de donde estabas.
   Evita el vacío engañoso de quien busca «tenis» sin darse cuenta de que estaba
   dentro de Accesorios.
3. **Se ofrecen cuatro órdenes** (RN-088): relevancia, recientes y precio en las dos
   direcciones. En un sitio de segunda mano el precio es por donde la gente ordena.
4. **Los resultados de búsqueda no se indexan** (criterio 24). Son combinaciones casi
   infinitas de filtros que producen páginas repetidas y casi vacías, y eso perjudica
   al sitio entero. Las categorías y las fichas, que es de donde vienen las visitas,
   siguen indexadas.

Y una que se tomó contra lo previsto: **la marca no es filtro** (RN-085). Se planeó
como uno de los cinco filtros y el código dijo lo contrario —`brand` es texto libre y
opcional—, así que se queda en el texto buscable.

## Lo que hay que agregar antes de implementar

- **Ocho reglas nuevas**, RN-081 a RN-088, en `docs/producto/reglas-negocio.md`.
- **Cuatro entradas de glosario**: búsqueda, consulta, motor de búsqueda y filtro.
- **Ninguna columna nueva en el modelo de datos.** Se busca y se filtra sobre lo que
  ya se declara. `V18` agrega índices, no campos.
