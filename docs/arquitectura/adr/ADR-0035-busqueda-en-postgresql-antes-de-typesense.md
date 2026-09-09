# ADR-0035 — La búsqueda arranca en PostgreSQL, detrás del puerto de ADR-0008

**Fecha:** 2026-09-08 · **Estado:** aceptada

## Contexto

La Fase 3 se abre hoy y arranca por la búsqueda (HU-014). ADR-0008 decidió
Typesense en agosto y dejó una puerta abierta con estas palabras: «como está tras
un puerto, la primera implementación puede incluso ser PostgreSQL mientras el
catálogo sea pequeño», y en su apartado de cuándo revisar, «si en Fase 3 el
catálogo aún es pequeño, puede posponerse manteniendo la implementación con
PostgreSQL detrás del mismo puerto».

Esa condición se cumple, y no de forma discutible:

- **`prod` no se ha desplegado nunca.** No hay un solo producto real a la venta.
- **En `dev` las publicaciones son las del recorrido del 5 de septiembre**: un
  puñado, creadas a mano para comprobar el ciclo.
- Las tres banderas de la Fase 2 siguen apagadas en `prod`, así que encender el
  catálogo allí es todavía otra decisión.

Levantar Typesense hoy significa un servicio más que operar, entre 0 y 25 USD al
mes según `entornos.md`, y —lo que de verdad cuesta— un índice que hay que
mantener sincronizado con la base por eventos de publicación, edición, venta y
despublicación. Sincronizar un índice para catorce productos de prueba es
construir la parte cara antes que la parte que da valor.

Lo que obliga a decidir ahora es que la puerta que ADR-0008 dejó abierta **no la
cruzó nadie**: mientras no esté escrito, quien lea el repositorio concluye que
Sendik opera un Typesense que no existe, y quien implemente HU-014 no sabe con
qué debe implementarla.

## Opciones

1. **Typesense administrado desde el primer día.** Es lo que ADR-0008 decidió.
   Tolerancia a errores de escritura y facetas de fábrica, y relevancia
   ajustable. Cuesta dinero desde el primer mes, suma un servicio que operar y
   obliga a escribir la sincronización del índice antes de tener nada que
   indexar.
2. **Typesense autoalojado.** Quita la factura mensual y la cambia por operación:
   una instancia que mantener, respaldar y vigilar, en un proyecto que hoy
   presume de que `dev` cuesta cero.
3. **PostgreSQL con búsqueda de texto completo, detrás del puerto
   `SearchEngine`.** Cero infraestructura nueva, cero costo, y **ningún índice
   que sincronizar**: se consulta la misma base que ya es la fuente de verdad, de
   modo que no existe la ventana en la que el índice y la base discrepan. A
   cambio no tolera errores de escritura y no da facetas con conteo.

## Decisión

La búsqueda de HU-014 se implementa contra PostgreSQL, detrás del puerto
`SearchEngine` que ADR-0008 definió, y Typesense queda aplazado —no descartado—
con una señal escrita de cuándo entra.

## Motivo

Porque es la opción que ADR-0008 previó para exactamente esta situación, y porque
el puerto hace que elegir mal hoy sea barato: la implementación vive entera en
`infrastructure` y cambiarla no toca el dominio, ni el caso de uso, ni el
endpoint, ni la pantalla.

El argumento con el que ADR-0008 eligió Typesense sigue siendo cierto —«en moda,
quien busca "camisa oxford azul" y no encuentra nada, se va»— pero **habla de un
catálogo con gente buscando dentro**. Hoy no hay ni catálogo ni gente. La decisión
correcta no cambia; cambia cuándo se paga.

Y hay un motivo que solo se ve con el proyecto delante: la parte cara de Typesense
no es Typesense, es la sincronización. Escribirla ahora significa escribir la
reacción a publicar, editar, aprobar, pausar, vender y archivar, y probar cada una
de esas seis contra un índice externo, cuando las seis ya funcionan contra la base
y no hay nada que reconciliar.

## Consecuencias

Lo que se gana: la Fase 3 arranca sin infraestructura nueva, sin factura nueva y
sin un segundo sitio donde la verdad pueda quedar desfasada.

Lo que se acepta perder, y hay que decirlo con las mismas palabras que estarán en
la pantalla:

- **No hay tolerancia a errores de escritura.** Quien escriba «camisa oxfrod» no
  encuentra nada. Es literalmente el ejemplo con el que ADR-0008 argumentó a
  favor de Typesense, y se acepta a sabiendas.
- **No hay facetas con conteo.** Ninguna opción de filtro dice cuántos resultados
  tiene detrás, así que se puede filtrar hacia un vacío. Lo compensa a medias el
  vacío honesto de RN-086: se dice qué filtros están puestos y cómo quitarlos.
- **La relevancia es la de `ts_rank` y no se calibra.** No hay pesos por campo
  ajustables ni sinónimos. Es una razón más para que RN-082 deje la descripción
  fuera del texto buscable: sin control de relevancia, un campo largo y libre
  domina el orden.
- **Hace falta normalizar acentos**, porque «camisón» y «camison» tienen que dar
  lo mismo (HU-014, criterio 5). Se resuelve con la extensión `unaccent` o, si el
  proveedor gestionado no la permite, con una columna normalizada e indexada. Hay
  que comprobarlo contra la base de `dev` **antes** de escribir la migración: es
  el único punto de esta decisión que depende de algo que no controlamos.
- **El diccionario es `spanish` y el sitio se sirve en dos idiomas.** Lo que el
  vendedor escribió está en el idioma en que lo escribió, y buscar en inglés sobre
  publicaciones en español no se promete. Con Typesense tampoco se prometía, pero
  aquí queda más cerca de la superficie.

## Cuándo revisar

Cualquiera de estas tres reabre la decisión, y la primera es la que se puede medir
sin construir nada:

1. **Que el catálogo vivo pase de las 5.000 publicaciones `PUBLISHED`.** Se cuenta
   con una consulta y sin instrumentación nueva. El número es una estimación de
   ingeniería y no una regla de negocio: es el orden de magnitud en el que ordenar
   por `ts_rank` deja de resolverse cómodamente con un índice.
2. **Que se decidan las facetas con conteo.** Construirlas sobre PostgreSQL es
   consulta de agregación por cada filtro; sobre Typesense vienen en la misma
   respuesta. El día que se decidan, la cuenta cambia de lado.
3. **Que se compruebe que la gente no encuentra lo que sí existe.** Y aquí hay que
   ser honestos: **hoy no hay forma de saberlo.** Nada registra qué se buscó ni
   cuántos resultados dio, y registrarlo no es gratis —lo que alguien busca es un
   dato personal y entraría por `docs/operacion/datos-personales.md`—. Mientras esa
   medición no exista, esta tercera señal solo puede llegar como reporte de
   alguien, no como número.

Lo que **no** es señal para revisar: que Typesense siga siendo mejor. Ya se sabe
que lo es; eso lo decidió ADR-0008 y esta ADR no lo discute.
