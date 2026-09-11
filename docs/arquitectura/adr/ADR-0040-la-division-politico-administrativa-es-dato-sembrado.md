# ADR-0040 — El municipio se elige de una lista sembrada, no se escribe

**Fecha:** 2026-09-11 · **Estado:** aceptada

## Contexto

HU-016 tiene que guardar a qué municipio va una dirección de entrega. El proyecto ya
tiene un campo parecido y resuelto de otra manera: `users.city` es texto libre,
opcional y público, y su objeto de valor `City` documenta por qué —«una lista obliga a
mantenerla y deja fuera a quien viva donde no se pensó; para lo que este dato hace, que
es dar una idea de dónde sale el producto, el texto libre basta»—.

Ese razonamiento es correcto **para lo que aquel campo hace**, y no vale para este. El
municipio de destino no da una idea de nada: es la entrada de la cotización (RN-039) y
lo que decide si hay cobertura (RN-080). Escrito a mano, «Bogotá», «bogota» y «Bogotá
D.C.» son tres destinos distintos y ninguno cotiza.

La restricción real es que la lista existe, es pública y es oficial: el DANE publica la
división político-administrativa —la Divipola— en el portal de datos abiertos del
Estado, y de ahí salen 33 departamentos y 1122 municipios con sus códigos.

## Opciones

1. **Texto libre**, como `users.city`. Barato hoy y coherente con lo que existe.
   Traslada el problema a la cotización: normalizar después lo que se guardó sin
   normalizar es una migración sobre datos personales reales.
2. **Lista cerrada resuelta contra el agregador.** Pedirle a Skydropx su catálogo de
   destinos cada vez. Es la lista que de verdad manda para cotizar, pero ata el
   formulario a un proveedor que todavía no contesta, y deja la libreta inservible
   mientras su API esté caída.
3. **Lista cerrada sembrada en la base**, desde el conjunto oficial del DANE.

## Decisión

Departamentos y municipios se siembran con una migración a partir de los dos conjuntos
que el DANE publica en el portal de datos abiertos, y el municipio de una dirección es
una clave foránea a esa tabla. La lista vive en `shared` y no en `identity`.

## Motivo

**Cerrada, porque de este campo depende que se pueda cotizar.** Es la diferencia con
`users.city`: aquel se lee, este se procesa.

**Del DANE y no del agregador**, aunque el agregador sea quien acaba decidiendo si hay
cobertura. La Divipola es estable, pública, no depende de un contrato que no está
firmado y no se cae. Lo que el agregador diga se contrastará al cotizar —y hasta
entonces RN-103 impide que ninguna pantalla prometa entrega—, pero el formulario tiene
que funcionar hoy.

**En `shared` porque no es de nadie.** Hoy la usa la dirección de entrega y mañana la
usará el cotizador, que estará en `shipping`. Colgarla de `identity` obligaría a ese
contexto futuro a pedirle la lista a identidad, que no tiene nada que ver.

**Y los códigos del DANE resultaron decidir algo más.** Son jerárquicos: los dos
primeros dígitos del código de municipio son los de su departamento, sin una sola
excepción en las 1122 filas —hay una prueba de integración que lo comprueba sobre la
tabla entera—. Eso permite que el cuerpo de la API pida **solo el municipio**: el
departamento se deriva, y un par incoherente no puede existir porque no hay par. El
criterio de HU-016 que pedía rechazar pares incoherentes se cumple por construcción.

## Consecuencias

- **La migración lleva la fuente escrita**: las dos URL del portal, la versión del
  conjunto y la fecha de descarga. Una prueba de integración cuenta las filas contra los
  dos números que ese encabezado declara, que es lo que impide que una resiembra futura
  se deje filas por el camino sin que nadie lo note.
- **Entran los 1122 y no solo los municipios en sentido estricto.** De esas filas, 18 son
  áreas no municipalizadas —Amazonas, Guainía y Vaupés tienen varias— y 1 es una isla.
  Para una dirección de entrega los tres son lo mismo: el sitio al que hay que llevar
  algo. Dejar 19 fuera sería sembrar algo distinto de la fuente sin decirlo, y dejar sin
  dirección a quien vive allí.
- **Los códigos se guardan como texto y no como número.** Varios empiezan por cero
  —Antioquia es `05`, Atlántico `08`— y un entero se los come.
- **Un municipio suprimido se marca inactivo y no se borra**, y la clave foránea va con
  `ON DELETE RESTRICT`: hay direcciones guardadas que lo apuntan. Deja de ofrecerse al
  elegir, y las direcciones que ya lo tenían se siguen leyendo.
- **Hay que resembrar cuando el DANE cambie algo**, con una migración nueva. No es
  frecuente —el último municipio creado en Colombia lo fue hace años— pero es trabajo que
  el texto libre no habría pedido.
- **Los nombres no se traducen.** Son nombres propios y van iguales en español y en
  inglés, así que la tabla tiene una sola columna de nombre y no una por idioma, al revés
  que `categories`.
- **Bogotá D.C. es departamento y municipio a la vez**, y el modelo lo admite sin un `if`
  con nombre propio porque son dos tablas.

## Cuándo revisar

- **Cuando Skydropx conteste.** Si identifica el destino por código postal o por un
  catálogo propio en vez de por código DANE, hay que decidir si se guarda también esa
  correspondencia o si se traduce al cotizar. Es el riesgo concreto que esta decisión
  acepta por empezar antes de tener la respuesta, y es acotado: son columnas de una
  tabla.
- **Si el DANE crea, suprime o renombra un municipio.** Migración nueva, y lo suprimido se
  marca inactivo.
- **Si algún día hace falta el mismo dato fuera de una dirección** —la ciudad del
  vendedor, por ejemplo, que hoy es texto libre en `users.city`—. Unificarlos sería
  sensato, y es una migración sobre datos de gente real: se decide entonces, no ahora.
