# ADR-0037 — El carrito sin sesión vive en el navegador y se fusiona preguntando

**Fecha:** 2026-09-10 · **Estado:** aceptada

## Contexto

HU-015 decidió que el carrito funcione sin sesión: quien no ha entrado lo arma igual y
lo encuentra al día siguiente en ese mismo navegador (criterio 8). De ahí salieron dos
problemas que la historia no había visto, y los dos aparecieron al planear.

**El primero: dos criterios que no se pueden cumplir a la vez.** El criterio 9 pide que
al entrar el carrito del navegador se fusione con el de la cuenta, automáticamente. El
criterio 12 pide que si entra **otra persona** en ese navegador no herede nada. El
carrito sin sesión es anónimo: el servidor no tiene con qué distinguir «vuelve el mismo»
de «llega otro». Los dos criterios, tal como estaban escritos, se contradicen.

El pase de un solo uso de ADR-0029 no sirve aquí. Aquel ata una **intención** a un
recorrido de ingreso concreto y muere con la pestaña; esto es estado que vive semanas y
que nadie ha declarado querer entregar a nadie.

**El segundo: dónde se suma el dinero.** El carrito de quien no ha entrado tiene que
enseñar los mismos grupos por vendedor y los mismos subtotales que el de quien sí entró.
El plan original era leer las publicaciones con un parámetro `ids` en
`GET /api/v1/listings`, que devuelve un tramo plano de catálogo. Con eso, agrupar y sumar
le tocaba al navegador, y la misma cifra quedaba escrita dos veces: una en Java para
quien entró y otra en TypeScript para quien no.

## Opciones

**Para la fusión:**

1. **Automática, y el criterio 12 se relaja.** Es lo que hace casi todo el comercio
   electrónico. Cubre el caso secuencial —A entra, se fusiona y se borra; B entra
   después y ya no hay nada— y deja fuera el que importa: A arma carrito, nunca entra, y
   B hereda lo que A eligió.
2. **Preguntar una vez.** Al entrar con carrito en el navegador se ofrece agregarlo, con
   dos botones. Cumple los dos criterios: sigue siendo unión y nadie hereda sin decir que
   sí. Cuesta un paso en el caso común.
3. **Sin carrito anónimo**, como los favoritos: el control lleva a entrar. Se cae el
   criterio 8 y con él la mitad de la historia.

**Para la suma:**

1. **`ids` en `GET /api/v1/listings`.** Una ruta menos. Obliga al patrón del bean testigo
   de `SearchFeature` —esa ruta es también el catálogo de HU-009 y la búsqueda de
   HU-014— y, sobre todo, devuelve un tramo plano: la suma se escribe en el navegador.
2. **`GET /api/v1/carts?ids=…`, recurso propio y público.** Devuelve la misma forma que
   `/users/me/cart`. Los dos caminos comparten el armador en la capa de aplicación, así
   que la suma vive en un solo sitio.

## Decisión

El carrito sin sesión vive en `localStorage`, y al entrar **se pregunta** antes de
fusionarlo. La unión es un `POST /api/v1/users/me/cart` que dispara una persona.

Y la lectura de ese carrito la sirve un recurso propio y público,
`GET /api/v1/carts?ids=…`, con la misma forma que el carrito de la cuenta.

## Motivo

**La fusión se pregunta porque no hay forma de saber quién vuelve.** Un navegador
compartido —una casa, un locutorio, un computador de oficina— es exactamente el caso que
el criterio 12 describe, y ahí heredar en silencio el carrito de otra persona no es una
molestia: es escribir en la cuenta de alguien una intención de compra que no tuvo. La
revisión de HU-011 encontró ese mismo fallo en una escala menor —una persona se quedaba
con el favorito que pidió otra— y costó una corrección de seguridad. Aquí se decide
antes.

Preguntar cuesta un paso, y ese paso además informa: quien lo ve entiende que lo que
tenía sin entrar no estaba en su cuenta.

**El recurso propio existe para que la suma se escriba una sola vez.** Dos
implementaciones de la misma cifra divergen en cuanto una de las dos cambie, y la que
divergiría es la que ve quien todavía no ha entrado: la peor de las dos para equivocarse.
Que el peso no tenga decimales hace la suma en JavaScript exacta hoy; no hace que dos
sumas separadas sigan dando lo mismo dentro de un año.

Y sale mejor en tres cosas más que no eran el objetivo: no toca la ruta que ya comparten
HU-009 y HU-014, no necesita el bean testigo —al ser ruta propia, con la bandera apagada
el controlador no se crea y el 404 aparece solo—, y `contrato-api.md` gana un recurso en
vez de modificar uno compartido por tres historias.

**No nace un `/listings/batch`**, por lo mismo que ADR-0035 no hizo nacer un `/search`:
pedir varias publicaciones es listar el mismo recurso con otra condición. Lo que justifica
la ruta nueva no es pedir por identificador, es que lo que sale es un carrito.

## Consecuencias

Lo que se gana: los criterios 9 y 12 se cumplen los dos, la suma vive en un solo sitio, y
el carrito anónimo no obliga a tocar nada de lo que ya existía.

Lo que se acepta perder:

- **Un paso más en el caso común.** Quien arma su carrito y entra en su propio computador
  tiene que decir que sí a algo que daría por hecho.
- **Una ruta pública más que mantener.** `GET /api/v1/carts` no revela nada que el
  catálogo no revelara ya por identificador, pero es superficie, y superficie sin token.
  Su única defensa contra una petición arbitraria es el tope de veinte de RN-097.
- **El tope vive en dos sitios**, el dominio del backend y el del frontend. Sin sesión no
  hay servidor que lo haga cumplir, así que la duplicación no es evitable; lo que sí se
  puede es que se note cuando una de las dos cambie, y de eso no hay guardián.
- **La copia local de cada producto.** El navegador guarda título, precio, imagen y
  vendedor además del identificador, porque es lo único que permite pintar apagado lo que
  el servidor ya no devuelve (RN-094 con RN-068). Es una copia que puede quedar vieja, y
  la regla es que la verdad es siempre lo que responda el servidor.

## Cuándo revisar

- **Si aparece alguna forma de identificar el navegador antes del ingreso** —una que no
  sea seguir a nadie— la pregunta de la fusión se podría ahorrar en el caso común. Hoy no
  la hay y no se va a inventar aquí.
- **Cuando exista el pedido**, `GET /api/v1/carts` se queda como está: el checkout exige
  sesión y no pasa por ahí. Si alguna vez se plantea comprar sin cuenta, esta ADR es la
  primera que hay que releer.
- **Si el tope de RN-097 cambia** y las dos copias del número se desincronizan sin que
  nada falle, hace falta el guardián que hoy no existe.
