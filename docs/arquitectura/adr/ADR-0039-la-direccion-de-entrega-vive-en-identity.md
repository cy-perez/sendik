# ADR-0039 — La dirección de entrega vive en `identity`, y se cifra

**Fecha:** 2026-09-11 · **Estado:** aceptada

## Contexto

HU-016 implementa la libreta de direcciones de entrega, y la tabla de contextos de
`vision-tecnica.md` anuncia `shipping` para la Fase 3. Lo natural sería leer que la
dirección de entrega, siendo del envío, es de `shipping`.

El nombre engaña. Lo que esta historia guarda es **dónde vive una persona, a nombre
de quién y con qué teléfono**: un dato de la cuenta, que existe antes de que haya
pedido, antes de que haya cotización y antes de que exista ninguna guía. El envío lo
usará, igual que usará el nombre de quien compra, y nadie propondría por eso mover el
nombre a `shipping`.

Hay además una consecuencia práctica que decide sola. Las direcciones tienen que
entrar en la descarga de datos del criterio 22 de HU-001 y el cierre de cuenta tiene
que borrarlas en la misma transacción que anonimiza (RN-102). Los dos casos de uso que
hacen eso viven en `identity`, y si la tabla fuera de otro contexto haría falta un
puerto entre contextos —como `UserFavorites` y `UserCart`, que existen exactamente por
eso— para algo que no lo necesita.

La segunda mitad de esta decisión es **cómo se guarda**. La clasificación de
`datos-personales.md` pone la dirección en nivel Interno, junto al teléfono, el
carrito y los favoritos, y ninguno de esos se cifra; el cifrado está reservado a
Sensible —cédula, selfie, cuenta bancaria—. Pero `modelo-datos.md` ya decía, desde
antes de esta historia, que la copia que el pedido hará de la dirección
—`orders.shipping_address`— va cifrada.

## Opciones

**Dónde vive:**

1. **`shipping`.** Es lo que sugiere el nombre. Obliga a estrenar un contexto entero
   para guardar siete campos de texto, y a crear un puerto hacia `identity` para el
   cierre de cuenta y la descarga de datos.
2. **`order`.** Peor: la dirección existe sin que haya pedido, y una cuenta que nunca
   compra puede tener libreta.
3. **`identity`.** Es de la persona. No estrena contexto, no estrena puerto, y el
   cierre de cuenta la alcanza directamente.

**Cómo se guarda:**

1. **En claro**, coherente con su nivel en la clasificación. Más simple, y deja abierta
   la incoherencia con `orders.shipping_address`.
2. **Cifrada, campo por campo**, como `seller_verifications`: seis pares
   `_cipher`/`_key_version`, doce columnas.
3. **Cifrada, en un solo documento**: los seis campos libres como JSON dentro de una
   columna, con su versión de clave.

## Decisión

La dirección de entrega vive en `identity`, y sus seis campos libres se guardan
cifrados en una sola columna.

## Motivo

**`identity` porque una dirección es de la persona, no del envío.** El criterio no es
quién la usa —eso llevaría el nombre y el correo a media docena de contextos— sino de
quién es. Es la misma pregunta que resolvió ADR-0036 con el carrito, contestada con el
mismo método y con la respuesta contraria: allí lo que se devolvía al leer eran
agregados `Listing`, así que el contexto era `catalog`; aquí lo que se guarda es de la
cuenta, así que es `identity`.

**Un solo documento cifrado porque los seis campos se leen y se escriben siempre
juntos.** En `seller_verifications` cada dato sensible se lee por separado —el número de
documento tiene además su huella para el criterio 5 de HU-002 y sus últimos cuatro para
la pantalla— y por eso allí hace falta una columna por dato. Aquí no hay ninguna
consulta que quiera el teléfono sin la calle: juntos son una dirección. Seis pares
serían doce columnas que nunca se consultan por separado.

**Y se cifra, aunque sea Interno, porque la copia ya lo estaba.** Cifrar
`orders.shipping_address` dejando la fuente en claro no protege nada: quien pudiera leer
la tabla de direcciones tendría lo mismo. A eso se suma RN-104: cuando quien recibe no es
el titular, ahí hay el nombre y el teléfono de un tercero que nunca abrió una cuenta.

## Consecuencias

- Los códigos de error de la libreta llevan el prefijo **`USER_`**, que el contrato ya
  declaraba y que hasta hoy no usaba nadie —todo lo de la cuenta salía como `AUTH_`
  porque todo lo de la cuenta era autenticarse—. `SHIPPING_` queda para el envío de
  verdad.
- **No nace ningún puerto entre contextos.** El cierre de cuenta y la descarga de datos
  llaman al repositorio directamente, sin el rodeo que `UserFavorites` y `UserCart`
  necesitan.
- **Fuera del cifrado quedan el municipio, la marca de predeterminada y las fechas**, que
  es lo único por lo que la tabla se consulta. Si algún día hiciera falta buscar por
  calle o por teléfono, no se podrá sin descifrar la tabla entera; hoy ninguna pantalla lo
  pide y ninguna regla lo sugiere.
- **Un mapeador más**, entre el documento JSON y los objetos de valor del dominio. Es el
  mismo patrón de `MeasurementsJson` y cuesta lo mismo: un archivo.
- `shipping` **sigue vacío**, y nacerá cuando exista Skydropx. Lo que irá allí es la
  cotización, la guía y el seguimiento: cosas con ciclo de vida.

## Cuándo revisar

- **Cuando nazca `shipping`.** Si al escribir el cotizador resulta que necesita leer o
  escribir direcciones —y no solo leer un código de municipio—, la frontera está mal
  puesta y hay que mirarla otra vez.
- **Cuando exista el pedido.** Habrá que decidir cómo se copia la dirección al pedido
  (RN-030 aplicado al destino) y si esa copia comparte este mismo cifrado. Ese día RN-102
  también se revisa: el borrado inmediato al cerrar la cuenta choca con los diez años de
  conservación contable de las órdenes.
- **Si alguna consulta necesita filtrar por un campo cifrado.** Sería la señal de que el
  documento único se quedó corto, y la salida sería una huella determinista como la del
  número de documento, no descifrar la tabla.
