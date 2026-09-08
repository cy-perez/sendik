# HU-009 — Catálogo público

**Fase:** 2 | **Estado:** hecha el 27 de agosto de 2026.
Al implementarla apareció que **ninguna pantalla del proyecto renderizaba datos remotos en
servidor**: el catálogo, la ficha y el perfil se servían con su esqueleto de carga. Lo
destapó la prueba de extremo a extremo de esta historia y lo resuelve ADR-0025.
**Reglas que aplica:** RN-015, RN-016, RN-017, RN-020, RN-021, RN-024, RN-025,
RN-061, RN-064, RN-066, RN-067

## Objetivo

Cualquiera, con sesión o sin ella, puede navegar lo que se vende en Sendik, abrir
un producto y ver quién lo vende. Hasta hoy una publicación aprobada no la veía
nadie: el ciclo terminaba en `PUBLISHED` y no había dónde mostrarla.

## Por qué ahora

HU-007 y HU-008 dejaron el ciclo completo —un vendedor publica, un moderador
decide— y con eso todo el trabajo de la fase queda **detrás de una puerta sin
sala**. `alcance.md` tenía el catálogo bloqueado por dos cosas: que el árbol de
categorías se aprobara y que existiera HU-007. Las dos se cumplieron.

Es además la primera pantalla del proyecto que sirve a alguien que no tiene
cuenta, y por eso es la primera donde el renderizado en servidor deja de ser una
decisión técnica y pasa a ser el producto: lo que un buscador indexa es lo único
que trae compradores.

## Alcance

Entra:

- **Listado del catálogo.** Lo publicado, lo más reciente primero, paginado.
- **Navegación por el árbol de categorías**, en sus dos niveles: familia y
  categoría (RN-064). El árbol ya existe y se sirve desde HU-007.
- **Ficha de producto.** Las tomas, lo que el vendedor declaró —condición, talla,
  medidas, color, marca—, el precio y quién lo vende.
- **Perfil público del vendedor.** Nombre, foto, insignia de verificado y sus
  publicaciones vivas.
- **El rótulo de las imágenes de referencia** que RN-066 exige en la ficha y en el
  carrusel, en los dos idiomas. Es el pendiente que `textos-web.md` dejaba
  anotado como «llega con la ficha».

No entra:

- **Búsqueda y filtros.** Son Fase 3 con Typesense, y así lo dice `alcance.md`.
  Aquí se navega por categorías; no hay caja de búsqueda ni filtro por color,
  talla o precio, por mucho que el color sea de lista cerrada justamente para
  poder filtrarlo algún día.
- **Favoritos.** Salen a su propia historia. El catálogo es anónimo y de lectura;
  los favoritos son de cuenta y de escritura, y sus reglas no existen: nadie ha
  decidido si sobreviven a que la publicación se archive o se venda, ni si hay
  tope. Meterlos aquí sería inventar reglas de negocio en la historia que menos
  las necesita.
- **Carrito, compra y envío.** Fase 3 entera. La ficha no lleva botón de comprar,
  y eso incluye el texto «Tu pago queda retenido…» que `textos-web.md` ya tiene
  redactado para debajo de ese botón: se escribe cuando el botón exista.
- **Reseñas del vendedor.** Fase 3. El perfil dice quién es y qué vende, no qué
  tal le fue a nadie.
- **Visor 360.** Es HU-003 y va detrás de `FEATURE_SPIN_VIEWER`. La ficha nace con
  un carrusel de las ocho tomas; cuando HU-003 llegue, el visor sustituye al
  carrusel sin tocar esta historia.
- **La frase de la garantía del fabricante en la ficha (RN-067).** No fue un olvido:
  `textos-web.md` la tenía aplazada por decisión del 26 de agosto de 2026 a la
  tanda de los documentos legales, porque reparte responsabilidad entre vendedor,
  comprador y una plataforma que no la asume. Ese documento decía, con esas
  palabras, que **bloquea la ficha de producto**. La ficha de tecnología se entregó
  sin esa línea y con el campo ya disponible en la API.

  **Se agregó el 8 de septiembre de 2026**, en esa misma tanda y sobre esta pantalla,
  sin reabrir la historia: el numeral 15.2 de los términos `2026-09-08b` ya reparte la
  responsabilidad por escrito, así que la ficha lo enuncia en llano y enlaza al
  documento (RN-057). Las claves son `catalog.detail.warranty.*`.

## Criterios de aceptación

### El listado

1. Dado cualquiera, con sesión o sin ella, cuando abre la ruta del catálogo,
   entonces ve las publicaciones en estado `PUBLISHED` ordenadas de la publicada
   más recientemente a la más antigua.

2. Dado un catálogo con publicaciones en otros estados —`DRAFT`,
   `PENDING_REVIEW`, `REJECTED`, `PAUSED`, `SOLD` o `ARCHIVED`—, cuando alguien
   abre el listado, entonces **ninguna** de ellas aparece, ni siquiera la del
   propio vendedor con su sesión abierta.

3. Dado el listado, cuando se pide un tramo, entonces la respuesta trae como
   máximo el `limit` pedido y el cuerpo tiene la forma que `contrato-api.md`
   reserva para el catálogo: `items`, `nextCursor` y `hasMore`. **Por cursor y no
   por número de página**, porque el contrato lo dice con su motivo: sobre
   contenido que se inserta constantemente, la paginación por desplazamiento
   repite y salta elementos. Un `limit` por encima de 50 se rechaza con 400, no se
   recorta en silencio.

4. Dado un cursor que no se puede descifrar o que no corresponde a este listado,
   cuando se pide con él, entonces la respuesta es 400 y no un tramo arbitrario.

5. Dado un catálogo sin ninguna publicación visible, cuando alguien abre el
   listado, entonces ve un estado vacío que lo dice, y no una página en blanco ni
   un error.

6. Dada una publicación en el listado, cuando se pinta su tarjeta, entonces
   muestra su toma frontal —la de posición 0 (RN-016)—, el título, el precio en
   pesos sin decimales (RN-020) y la condición declarada, con una de las cuatro
   etiquetas del glosario y ninguna quinta.

### Las categorías

7. Dado el árbol de categorías, cuando alguien abre el catálogo, entonces puede
   navegar por las seis familias y, dentro de una, por sus categorías.

8. Dada una categoría elegida, cuando se muestra el listado, entonces solo
   aparecen las publicaciones de esa categoría, y la dirección de la página la
   identifica: compartir el enlace lleva al mismo sitio.

9. Dada una categoría retirada del árbol —`active` en falso—, cuando alguien
   intenta abrirla por su dirección, entonces recibe la página de no encontrado,
   no un listado vacío que parezca un error del sitio.

10. Dada una familia, cuando se abre, entonces se ven las publicaciones de **todas
    sus categorías hijas**, porque no se publica en una familia sino en una
    categoría suya (glosario).

### La ficha

11. Dada una publicación publicada, cuando alguien abre su ficha, entonces ve
    todas sus tomas en un carrusel, empezando por la frontal.

12. Dada una ficha, cuando se pinta, entonces muestra lo que el vendedor declaró:
    título, descripción, marca si la hay, condición, talla con su sistema,
    medidas en centímetros (RN-021), color y precio.

13. Dada una publicación que **no** está publicada, o un identificador que no
    existe, cuando alguien abre su ficha, entonces recibe la página de no
    encontrado. Las dos situaciones responden igual y no se distinguen desde
    fuera.

14. Dada una publicación de tecnología declarada sellada, cuando su ficha muestra
    una imagen de referencia, entonces esa imagen va **rotulada como referencia**
    tanto en la ficha como en el carrusel, en el idioma activo (RN-066).

15. Dada una ficha, cuando se pinta, entonces dice quién vende y, si el vendedor
    está verificado, muestra la insignia de vendedor verificado. Es la única
    aparición del acento bronce de la pantalla.

16. Dada una ficha, cuando llega el HTML del servidor, entonces el título, la
    descripción para buscadores y la dirección canónica ya vienen resueltos y
    describen ese producto, no la plantilla.

17. Dada la toma de un producto, cuando se pinta, entonces su texto alternativo
    describe el producto real y no `imagen1` ni una cadena de palabras clave.

### El perfil del vendedor

18. Dado un vendedor, cuando alguien abre su perfil, entonces ve su nombre, su
    foto si la tiene, su insignia si está verificado, y sus publicaciones
    `PUBLISHED`, con la misma regla de visibilidad del criterio 2.

19. Dado el perfil de un vendedor, cuando se pinta, entonces **no** aparece ningún
    dato personal más allá del nombre público y la foto: ni correo, ni documento,
    ni cuenta bancaria, ni fecha de nacimiento.

20. Dado un vendedor sin ninguna publicación visible, cuando alguien abre su
    perfil, entonces ve un estado vacío, no un error.

21. Dada una ficha, cuando alguien pulsa el nombre del vendedor, entonces llega a
    su perfil.

### La bandera

22. Dada `FEATURE_CATALOG` apagada, cuando alguien llama a cualquiera de los
    endpoints de esta historia, entonces recibe 404: no es que rechacen, es que no
    están. El controlador no se crea, igual que hacen `FEATURE_PUBLISHING` y
    `FEATURE_SELLER_VERIFICATION`.

23. Dada `FEATURE_CATALOG` apagada, cuando alguien abre una de las rutas del sitio,
    entonces la página responde 200 y muestra su estado de no encontrado. **Las
    rutas del frontend no se esconden y esto no es una excepción**: es lo que ya
    hace `/publicar` con `FEATURE_PUBLISHING` apagada, y lo que permite que
    `e2e/rutas.spec.ts` recorra todas las rutas declaradas sin saber qué bandera
    está encendida. Lo que no existe sin bandera son los datos, no el enrutador.

## Casos borde

- **Una publicación se aprueba mientras alguien mira el listado.** No aparece
  hasta que se recargue. No hay tiempo real y no hace falta.
- **Una publicación se vende o se archiva mientras alguien tiene su ficha
  abierta.** Al recargar recibe el no encontrado del criterio 13. No se avisa: no
  hay a quién avisar en una página anónima.
- **Un enlace compartido de algo ya vendido.** Es el caso anterior visto desde
  fuera y es el precio de haber elegido que lo vendido desaparece. Si algún día
  se decide conservarlo con un sello «Vendido», es un cambio de esta regla y de
  lo que el backend responde a quien no es el dueño.
- **Cursor de un elemento que ya no está publicado.** El tramo siguiente se
  calcula igual y no se rompe: el cursor ordena, no exige que su elemento siga ahí.
  Es la mitad del argumento por el que el contrato pide cursor en el catálogo.
- **Fin del listado.** El último tramo responde `hasMore` en falso y `nextCursor`
  nulo, no un 404 ni un tramo vacío de más.
- **Una publicación con menos de ocho tomas.** No debería existir —RN-017 lo
  exige para enviar a revisión— pero la sellada legítimamente tiene cuatro
  (RN-065). El carrusel se dimensiona por las que hay, no por ocho fijas.
- **Un vendedor que cerró su cuenta.** Cerrar anonimiza, no borra, y sus
  publicaciones no se archivan solas. Hay que decidir qué muestra el perfil de una
  cuenta anonimizada; ver «Lo que habría que agregar».
- **El árbol de categorías no responde.** El listado sigue sirviendo lo publicado
  y la navegación por categorías muestra su error propio, sin tumbar la página.
- **Precio de siete cifras.** Se formatea con la configuración regional activa y
  no se recorta ni se abrevia.

## Diseño

Se reutiliza lo que ya existe y no se inventa nada nuevo de marca:

- La rejilla de carriles de `<main>` de `src/styles.css`. La tarjeta de producto
  es la primera pieza que justifica el ancho de contenido de 1140px del kit.
- Los roles de tipo de `tipografia.css`. Ningún `font-size` propio.
- **El acento bronce aparece una vez por pantalla y es la insignia de vendedor
  verificado**, en la ficha y en el perfil. En la tarjeta del listado no: veinte
  tarjetas con insignia son veinte acentos en una pantalla.
- La proporción de las tomas es 3:4 (RN-018), así que la tarjeta y el carrusel se
  dimensionan con esa proporción y no hay salto al cargar la imagen.
- `NgOptimizedImage` en la toma frontal de la tarjeta, que es lo que decide la
  mayor pintura con contenido del listado.

Estados de carga, vacío y error para el listado, la ficha y el perfil. En móvil el
listado es de una columna y el carrusel se desplaza con el dedo.

## Notas técnicas

**Endpoints nuevos**, todos públicos y todos detrás de `FEATURE_CATALOG`:

| Método y ruta                                    | Devuelve                                |
| ------------------------------------------------ | --------------------------------------- |
| `GET /api/v1/listings?limit&cursor&category`     | Tramo de publicadas, forma pública      |
| `GET /api/v1/sellers/{id}`                       | Nombre, foto e insignia                 |
| `GET /api/v1/sellers/{id}/listings?limit&cursor` | Tramo de las publicadas de ese vendedor |

**Las rutas no se inventan aquí: `contrato-api.md` ya las reservó.** El ejemplo de
paginación por cursor de ese documento es literalmente
`GET /api/v1/listings?limit=24&cursor=…`, y su nota explica por qué la cola del
moderador tuvo que irse a `/moderation`: son dos listas del mismo recurso con
paginación distinta, y juntarlas obligaría a que la autorización dependiera de un
parámetro de consulta. Colgar el catálogo de `/catalog/listings` habría dejado esa
decisión sin efecto y el contrato con dos rutas para lo mismo.

`GET /api/v1/listings/{id}` **ya existe** y ya responde la forma pública a quien no
es dueño ni moderador: la ficha lo usa tal cual y no hay endpoint nuevo para ella.
`ListingResponses.publica` y `PublicListingResponse` también existen, escritos en
HU-007 pensando en esto.

**El árbol de categorías está hoy detrás de `FEATURE_PUBLISHING`**, y el catálogo
lo necesita. Con las dos banderas separadas, un catálogo encendido y una
publicación apagada dejarían el listado sin categorías. Hay que decidirlo al
implementar: o el árbol responde con cualquiera de las dos, o se acepta que el
catálogo exige `FEATURE_PUBLISHING` encendida.

**Persistencia.** Una consulta nueva por estado y fecha de publicación, con su
índice parcial, igual que hizo V12 con la cola del moderador: la cola filtra por
`PENDING_REVIEW` y ordena por espera; esto filtra por `PUBLISHED` y ordena por
`published_at`. La consulta por categoría va contra `products.category_id`, que ya
tiene índice desde V9.

**Rutas nuevas del frontend**, que `e2e/rutas.spec.ts` recoge solas al leerlas de
`app.routes.ts`: el listado, el listado por categoría, la ficha y el perfil.

**Claves de traducción nuevas** en los dos idiomas, y **ninguna se escribe en
`src/i18n` antes de estar en `textos-web.md`**: las etiquetas del listado y del
estado vacío, el rótulo de imagen de referencia (RN-066) y los `meta.*` de las
cuatro rutas.

## Pruebas requeridas

**Dominio y aplicación.** Que el caso de uso del listado devuelve solo lo
publicado, y que la página respeta el tope de 50. Que el perfil de un vendedor no
expone nada más que nombre, foto e insignia.

**Integración (`presentation`).** Los tres endpoints nuevos: forma de la
respuesta, paginación, 400 por `size` fuera de rango, 404 con la bandera apagada.
Y que la forma pública no filtra la cocina de la moderación, que es lo que
`PublicListingResponse` existe para impedir.

**Persistencia (`bootstrap`, con PostgreSQL real).** Que la consulta del listado
ordena por `published_at` descendente y que la de categoría trae las hijas de una
familia. Contra datos sembrados en los siete estados, para que el criterio 2 se
demuestre y no se afirme.

**Componente (Vitest).** El listado con sus tres estados; la tarjeta; el carrusel;
la ficha con un producto sellado, comprobando el rótulo de RN-066; el perfil.

**Extremo a extremo sin API (`e2e/`).** Que el HTML servido de la ficha ya trae el
título, la descripción y el canónico resueltos (criterio 16), que es lo que
ninguna prueba de componente puede demostrar. Las rutas nuevas entran solas en la
prueba del carril de contenido.

**Extremo a extremo completo (`e2e-completo/`).** El recorrido que cierra la
fase: un vendedor publica, un moderador aprueba, y **un visitante sin sesión abre
el catálogo y encuentra el producto**. Hoy ese recorrido termina en la bandeja del
moderador.

## Lo que habría que agregar antes de implementar

Nada de esto se agrega con esta historia; se listan para decidirlos.

**Reglas de negocio.**

- **Qué estados son visibles en el catálogo.** Se decidió aquí que solo
  `PUBLISHED`, y ninguna regla lo dice todavía. RN-061 enumera los siete estados y
  sus transiciones, pero no cuáles se ven. Merece una RN propia porque es la regla
  que contesta «¿por qué desapareció mi publicación?».
- **Qué se ve de un vendedor con la cuenta cerrada.** Cerrar anonimiza y no
  archiva sus publicaciones, así que hoy quedarían visibles con un vendedor sin
  nombre. Toca `docs/operacion/datos-personales.md` tanto como al catálogo.
- **Orden por omisión del listado.** Aquí es «lo más reciente primero», que es lo
  razonable sin buscador. Cuando llegue Typesense en Fase 3 habrá relevancia y esa
  decisión se reabre.

**Glosario.** No hace falta ninguna entrada nueva: producto, publicación,
categoría, familia, condición, toma e imagen de referencia ya están. Sí conviene
resolver la fila «Tienda, catálogo del vendedor → Perfil del vendedor», que hoy
está en la tabla de términos que no se usan y con esta historia pasa a ser una
pantalla real.

**Configuración.** `FEATURE_CATALOG` en `application.yaml` y en
`docs/operacion/configuracion.md`. **No** en `despliegue.yml` ni en el ensayo de
`verificacion.yml`: hoy ninguna de las seis banderas viaja en esas listas y todas
llegan apagadas por omisión, que es justo lo que se quiere de una bandera hasta
que alguien decida encenderla. Encender el catálogo en un entorno es agregar la
variable ese día, no dejarla puesta desde ahora.

**Textos.** La sección del catálogo en `textos-web.md`, que hoy no existe: las
etiquetas del listado, el estado vacío, el rótulo de RN-066 y las cuatro
descripciones para buscadores. Queda **fuera** la frase de la garantía del
fabricante, aplazada a la tanda legal.
