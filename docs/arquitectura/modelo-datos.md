# Modelo de datos

PostgreSQL 17. Migraciones con Flyway en
`backend/bootstrap/src/main/resources/db/migration`.

## Convenciones

- Tablas y columnas en inglés, minúsculas, con guion bajo. Tablas en plural.
- Clave primaria `id` de tipo `uuid`, generada por la aplicación con UUID v7
  (ordenable por tiempo, buen comportamiento en índices).
- `created_at` y `updated_at` de tipo `timestamptz`, siempre en UTC.
- Borrado lógico solo donde el negocio lo exige, con `deleted_at`. En el resto,
  borrado real.
- Dinero: `numeric(12,0)` en pesos colombianos, sin decimales. Jamás `float`.
- Estados en columnas `text` con restricción `check`, no en tipos `enum` de
  PostgreSQL: cambiar un `enum` en producción es doloroso.
- Todo campo con datos personales sensibles se cifra a nivel de aplicación antes
  de guardarse.
- Nada de `ddl-auto`. El esquema lo define exclusivamente Flyway.

## Fase 1

**users**

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| email | citext | único, normalizado |
| email_verified_at | timestamptz | nulo mientras no verifique |
| display_name | text | |
| birth_date | date | RN-008: solo mayores de 18. Se guarda la fecha, no el resultado |
| city | text | opcional. Dato público: sale junto a las publicaciones |
| phone | text | opcional. Dato interno: nunca en un perfil público |
| avatar_key | text | opcional. Clave del archivo en el almacén público; la dirección se construye en el borde (V6, ADR-0018) |
| avatar_url | text | **sin uso.** La sustituyó `avatar_key` en V6 y se elimina en una migración posterior: lo destructivo va en dos pasos |
| locale | text | `es` o `en` |
| status | text | `ACTIVE`, `BLOCKED`, `CLOSING`, `CLOSED` |
| closed_at | timestamptz | nulo mientras la cuenta siga abierta (V4, criterio 23) |
| created_at, updated_at | timestamptz | |

Una cuenta cerrada conserva la fila anonimizada en vez de borrarse: quedan el
identificador, la fecha de creación y el estado, y el correo se sustituye por uno
del dominio reservado `.invalid` para que RN-001 no impida a esa persona volver a
registrarse con su propia dirección.

**user_roles**: `user_id`, `role` (`BUYER`, `SELLER`, `MODERATOR`, `ADMIN`),
`granted_at`. Clave primaria compuesta.

**user_credentials**: `user_id` (PK y FK), `password_hash` (Argon2id),
`password_updated_at`, `failed_attempts`, `locked_until`.

**refresh_tokens**: `id`, `user_id`, `token_hash` (nunca el token), `family_id`,
`expires_at`, `revoked_at`, `replaced_by`, `user_agent`, `ip_hash`,
`created_at`. El `family_id` permite revocar toda una cadena al detectar
reutilización, y `created_at` es lo que permite mostrarle a alguien desde cuándo
está abierta cada una de sus sesiones.

**verification_tokens**: `id`, `user_id`, `purpose` (`EMAIL_VERIFICATION`,
`PASSWORD_RESET`, `EMAIL_CHANGE`), `token_hash`, `expires_at`, `used_at`,
`created_at`, `new_email`. La fecha de creación es la que permite contar los
reenvíos de la última hora: HU-001 limita a tres y sin ella no hay con qué
contarlos.

`new_email` es la dirección pendiente de confirmar y solo existe en los tokens de
cambio de correo; una restricción `CHECK` obliga a que esté exactamente cuando el
propósito es `EMAIL_CHANGE`. Vive aquí y no en `users` porque el token **es** el
cambio pendiente: caduca con él, se consume con él y desaparece con él. En
`users` habría que limpiarlo cuando el enlace venciera, y nadie limpia lo que
vence solo.

**login_attempts**: `id`, `email_hash`, `ip_hash`, `succeeded`, `created_at`.
Se conserva 90 días.

**consents**: `id`, `user_id`, `document` (`TERMS`, `PRIVACY`), `version`,
`accepted_at`, `ip_hash`. Es la prueba del consentimiento y por eso se guarda la
versión exacta del documento aceptado.

## Fase 2

Las tres primeras ya están aplicadas (`V7` y `V8`, HU-002). El resto de la fase
sigue sin migración y se describe como intención.

**financial_institutions** (`V7`)

| Columna | Tipo | Notas |
|---|---|---|
| code | text | PK. Forma `^[a-z0-9]+(-[a-z0-9]+)*$`, la misma que valida `BankCode` en el dominio |
| name | text | Lo que cambia cuando dos entidades se fusionan. Nunca se guarda en la fila del vendedor |
| kind | text | `BANK` (ahorros y corriente) o `WALLET` (solo depósito electrónico) |
| active | boolean | Una entidad que deja de operar se desactiva, no se borra: hay filas apuntando a ella |
| created_at | timestamptz | |

Es una tabla y no una enumeración del código porque agregar un banco no puede
exigir un despliegue, y porque la Fase 3 necesita el código de cada entidad para
el desembolso.

**seller_verifications** (`V8`)

Una fila por persona (`UNIQUE (user_id)`), no una por intento: los reintentos de
RN-014 se cuentan en `attempts` y sobrescriben la misma fila.

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| user_id | uuid | FK a `users`, único |
| status | text | `NOT_STARTED`, `IN_PROGRESS`, `PENDING_REVIEW`, `VERIFIED`, `REJECTED`, `REVOKED`. Transiciones en RN-059 |
| document_type | text | `CC`, `CE`, `PPT` |
| document_number_cipher | text | AES-256-GCM con nonce y etiqueta (ADR-0020) |
| document_number_key_version | smallint | Con qué versión de clave se cifró |
| document_number_lookup | bytea | HMAC-SHA256 con clave propia, distinta de la de cifrado. Es lo único comparable: el cifrado no se puede indexar |
| document_number_last_four | text | En claro a propósito: es lo único que la pantalla muestra (RN-046) |
| document_holder_name | text | El nombre tal como aparece en el documento |
| document_front_key, document_back_key | text | Claves del cubo privado, no URL (ADR-0018) |
| selfie_key | text | Íd. |
| bank_code | text | FK a `financial_institutions` |
| bank_account_type | text | `SAVINGS`, `CHECKING`, `ELECTRONIC_DEPOSIT` |
| bank_account_cipher | text | Mismo formato de ADR-0020 |
| bank_account_key_version | smallint | |
| bank_account_last_four | text | |
| bank_account_holder_name | text | Debe coincidir con `document_holder_name` (criterio 4) |
| attempts | smallint | RN-014: máximo tres. El cuarto exige que una persona intervenga |
| rejection_reason | text | `ILLEGIBLE_PHOTOS`, `EXPIRED_DOCUMENT`, `HOLDER_MISMATCH`, `DOCUMENT_ALREADY_VERIFIED`, `REQUIREMENTS_NOT_MET` |
| revocation_reason | text | (`V15`) `DOCUMENT_NOT_ITS_HOLDER`, `BANK_ACCOUNT_NOT_HOLDER`, `REPEATED_PROHIBITED_LISTINGS`, `HOLDER_REQUEST`, `REQUIREMENTS_NO_LONGER_MET`. Lista cerrada propia (RN-069): **no es la del rechazo**. Excluyente con `rejection_reason`, y reintentar limpia las dos |
| rejection_note | text | Nota libre y opcional del moderador |
| created_at, updated_at | timestamptz | |

No hay `reviewed_by` ni `reviewed_at`: **quién decidió y cuándo vive en
`verification_access_log`**, que es la única fuente de esa información y guarda
además los accesos que no cambian el estado. Duplicarlo en dos sitios dejaría
que se contradijeran.

Tampoco hay `birth_date`: la edad ya está en `users` (RN-008) y no se copia.

**verification_access_log** (`V8`)

| Columna | Tipo | Notas |
|---|---|---|
| id | uuid | PK |
| verification_id | uuid | FK a `seller_verifications` |
| actor_id | uuid | FK a `users`. No admite nulo: un acceso sin actor no es una bitácora |
| action | text | `VIEW_DOCUMENT_FRONT`, `VIEW_DOCUMENT_BACK`, `VIEW_SELFIE`, `VIEW_BANK_ACCOUNT`, `APPROVE`, `REJECT`, `REVOKE` |
| reason | text | Motivo declarado por quien accede. Nunca contiene el dato al que se accedió |
| created_at | timestamptz | |

Es la razón de que la cédula y la selfie no se sirvan por URL firmada: un enlace
que funciona por sí solo no puede registrar quién lo usó (ADR-0018).

**products**: `id`, `seller_id`, `title`, `description`, `category_id`,
`brand`, `condition`, `size_system`, `size_value`, `measurements` (`jsonb`),
`color`, `price`, `weight_grams`, `length_cm`, `width_cm`, `height_cm`,
`is_sealed`, `manufacturer_warranty_months`, `created_at`, `updated_at`.
`is_sealed` y `manufacturer_warranty_months` solo tienen sentido en tecnología y
van en nulo en moda. El primero habilita las imágenes de referencia y rebaja las
tomas exigidas a cuatro (RN-065); el segundo es la garantía que declara el
vendedor y por la que responde el vendedor (RN-067).
Qué claves lleva `measurements` no es libre: lo determina el
`measurement_group` de la categoría, y el dominio valida que estén todas y sean
números positivos en centímetros (RN-021). `size_system` es el que el vendedor
eligió de entre los que admite la categoría, y se guarda en el producto para que
cambiar la categoría después no reinterprete una talla ya declarada. `brand` es texto libre y opcional; `color` es lista cerrada.
**Salvo `id`, `seller_id`, `category_id` y `measurements`, todas las columnas
admiten nulo.** No es laxitud: el criterio 5 de HU-007 dice que un borrador
incompleto se guarda sin exigir que esté completo, y una columna `NOT NULL` no
puede distinguir un borrador a medias de una publicación que se quiere publicar
sin terminar. Lo obligatorio lo exige el dominio al enviar a revisión, con una
entrada en `errors` por cada campo que falta (criterio 6). `measurements` se
queda `NOT NULL` porque el modelo nunca la produce nula: un producto sin medidas
declaradas tiene el mapa vacío y se guarda como `{}`.

**listings**: `id`, `product_id`, `status`, `submitted_at`, `published_at`,
`sold_at`, `moderated_by`, `moderated_at`, `rejection_reason`, `rejection_note`,
`requires_attention`, `attention_reasons`, `version`.
La publicación se separa del producto para que el ciclo de moderación no
contamine los datos de la prenda.
`requires_attention` y `attention_reasons` son la marca de revisión más atenta:
la pone un precio fuera del rango de RN-020 o una toma cargada desde galería en
vez de capturada (HU-003 criterio 8). No cambia el estado ni bloquea nada; solo
hace que el moderador la vea destacada.
**Es un arreglo (`text[]`) y no una columna de un valor**, porque las dos marcas
conviven: una misma publicación puede tener el precio fuera de rango **y** una toma
de galería, y con una sola columna la segunda borraba a la primera. Nació singular
y `V10__catalog_fixes.sql` la sustituyó por el arreglo, migrando lo que hubiera y
retirando el `CHECK` de la vieja.
`submitted_at` es cuándo entró a revisión, y existe porque `updated_at` no sirve
para ordenar la cola del moderador: una publicación que espera turno **puede
cambiar de precio** —RN-062 y RN-030 lo permiten a propósito, el precio no pasa
por moderación— y eso movería su turno y reiniciaría el «espera desde hace» de la
pantalla. Lo sella el dominio en toda entrada a `PENDING_REVIEW`, no solo la
primera: RN-062 también devuelve a la cola lo que se edita, y una publicación que
vuelve con el sello viejo se quedaría a la cabeza para siempre. Es nulo mientras
nunca haya entrado (V12, HU-008).
`version` es el bloqueo optimista, y no es decorativo: el vendedor y el moderador
escriben sobre la misma fila a la vez con normalidad.

**product_images**: `id`, `product_id`, `kind`, `object_key`, `position` (0 a 7),
`angle_degrees`, `is_canonical`, `width`, `height`, `bytes`, `content_type`.
Restricción única sobre (`product_id`, `kind`, `position`).
`kind` distingue la toma que hizo el vendedor de la **imagen de referencia** que
no hizo (RN-066). No es un detalle de presentación: el visor 360 y el conteo de
tomas obligatorias solo miran las del vendedor, y la ficha tiene que rotular las
otras. Con una sola clase de imagen, una publicación de ocho fotos del fabricante
pasaría todas las validaciones.

**categories**: `id`, `parent_id`, `slug`, `name_es`, `name_en`, `size_systems`,
`measurement_group`, `allows_used`, `active`, `position`.
`allows_used` es lo que impide vender tecnología de segunda (RN-064): en falso, la
única condición admisible es nueva. Vive en la categoría y no en una constante del
código porque es un dato del árbol, y el árbol se siembra con una migración.
El árbol aprobado está en `docs/producto/categorias.md` y se siembra con una
migración, como las entidades financieras de `V7`: son datos, van a crecer y
ninguna enumeración de Java las lista.
`size_systems` es **plural**: una misma categoría admite más de una escala —unos
jeans se venden en talla numérica y en pulgadas de cintura— y el vendedor elige
una de las admisibles, que es la que queda en `products.size_system`.
`measurement_group` decide qué medidas son obligatorias.
`active` permite retirar una categoría del formulario sin tocar las publicaciones
que ya la tienen.
`name_es` y `name_en` se quedan en la tabla: los nombres de categoría los traduce
el servidor por `Accept-Language`, que es lo que `contrato-api.md` ya decía.

**moderation_events**: `id`, `listing_id`, `actor_id`, `action`, `reason`,
`notes`, `created_at`.

`action` admite cuatro valores desde `V17`: `SUBMITTED`, `APPROVED`, `REJECTED` y
`ARCHIVED`. **`SUBMITTED` lo escribe el vendedor**, no un moderador, y con él la
tabla deja de contar «lo que hizo Sendik» para contar «lo que le pasó a esta
publicación» (HU-013). Sin él, el rastro de una publicación rechazada y reenviada
no deja ver las dos vueltas: `listings.submitted_at` guarda un solo envío, el
último, porque se sobrescribe en cada entrada a `PENDING_REVIEW`. Se anota por los
**dos** caminos que llevan ahí: enviar el borrador, y editar una publicación viva,
que RN-062 devuelve a la cola. `V17` reconstruye desde `submitted_at` los envíos
anteriores a ella, uno por publicación, que es todo lo que esa columna sabe.

`actor_id` es quien hizo eso: el moderador que decidió, o el vendedor que envió.
**Nunca sale en una respuesta de la API** (RN-074), ni tampoco `notes`, que se
escribió para Sendik. La lectura del rastro no las selecciona.

`created_at` lo escribe siempre la aplicación y ya no el `now()` del motor. Era
inofensivo mientras la tabla solo se auditaba por consulta directa; al leerla en
orden dejó de serlo, porque el envío se fechaba con el reloj de la aplicación y las
decisiones con el de la base, y dos relojes en un mismo registro ordenado se cruzan.
Es el mismo instante con el que el caso de uso sella `listings.moderated_at`.

`ARCHIVED` aquí significa siempre **retiro del moderador** por RN-024. Archivar es
del vendedor, termina en el mismo estado de `listings` y no escribe en esta tabla:
es lo que permite que el rastro distinga las dos manos sin una columna más.

**favorites** (`V16`, HU-011)

| Columna | Tipo | Notas |
|---|---|---|
| user_id | uuid | FK a `users`. Parte de la clave primaria |
| listing_id | uuid | FK a `listings`. La otra parte |
| created_at | timestamptz | Cuándo se marcó. Es la fecha del **gesto**, no la de la publicación: es por lo que ordena la lista (RN-071, criterio 11 de HU-011) |

**La clave primaria es el par y no un `id` propio.** Es la excepción que ya sienta
`user_roles`: aquí la identidad de la fila *es* la pareja, y esa misma restricción es
lo que hace idempotente a marcar dos veces —un `ON CONFLICT DO NOTHING` en vez de leer
antes de escribir, que entre dos pestañas no bastaría—.

No hay `updated_at`: un favorito no se modifica, se pone y se quita.

**Nada borra estas filas cuando la publicación deja de estar `PUBLISHED`**, y es una
decisión (RN-071): la lista las filtra al leer, así que pausar y volver a publicar
devuelve el favorito sin haberlo tocado. Borrarlas obligaría a que archivar una
publicación escribiera en la fila de todas las personas que la habían guardado, un
trabajo que crece con la popularidad de lo que se archiva.

Lo que sí las borra es **cerrar la cuenta**: son dato personal —dicen qué le interesa a
una persona identificada— y `docs/operacion/datos-personales.md` manda. El cierre las
arrastra y la descarga de datos las incluye.

## Fase 3

**orders**: `id`, `buyer_id`, `seller_id`, `status`, `product_amount`,
`shipping_amount`, `commission_amount`, `total_amount`, `shipping_address`
(`jsonb`, cifrado), `created_at`, `expires_at`.

**order_items**: `id`, `order_id`, `product_id`, `unit_price`, `title_snapshot`,
`image_snapshot`. Los campos con sufijo `snapshot` congelan lo que el comprador
vio: la publicación puede cambiar después, el pedido no.

**payments**: `id`, `order_id`, `provider`, `provider_reference`, `method`,
`status`, `amount`, `raw_response` (`jsonb`), `created_at`, `updated_at`.

**payment_events**: `id`, `payment_id`, `provider_event_id` (único),
`signature_valid`, `payload` (`jsonb`), `processed_at`.
El identificador único del proveedor es lo que garantiza idempotencia.

**payouts**: `id`, `order_id`, `seller_id`, `gross_amount`, `commission_amount`,
`net_amount`, `status`, `released_at`.

**shipping_quotes**: `id`, `order_id`, `provider_quote_id`, `carrier`, `service`,
`amount`, `estimated_days`, `chosen` (`boolean`), `quoted_at`, `raw_response`
(`jsonb`).

Se guarda **una fila por opción devuelta por el agregador**, no solo la elegida, y
`chosen` marca cuál se cobró. Guardar solo la elegida deja sin explicar por qué se
le ofreció ese precio a esa persona, que es justo lo que hay que poder reconstruir
si alguien reclama. `provider_quote_id` es el identificador que devolvió Skydropx y
es lo que ata la cotización a la guía que se emita después (RN-041).

**shipments**: `id`, `order_id`, `provider_shipment_id`, `carrier`,
`tracking_code`, `declared_value`, `status`, `shipped_at`, `delivered_at`.

`declared_value` es el precio base congelado del pedido y lo escribe el sistema
(RN-078): es el techo de la indemnización si la transportadora pierde el paquete.

`delivered_at` **no se edita a mano**: lo fija el evento de entrega del
seguimiento (RN-079), y de él cuelgan la ventana de reclamo y la liberación del
pago. Un campo que alguien pueda mover a mano es un campo que mueve dinero.

**shipment_events**: `id`, `shipment_id`, `occurred_at`, `received_at`, `status`,
`description`, `raw_payload` (`jsonb`).

Es el rastro del envío, y guarda **dos fechas distintas a propósito**:
`occurred_at` es cuando la transportadora dice que pasó y `received_at` cuando
Sendik se enteró. Con una sola no se puede explicar por qué un plazo se contó
desde un día y no desde otro cuando el agregador reporta con retraso.

**order_status_history**: `id`, `order_id`, `from_status`, `to_status`,
`actor_id`, `reason`, `created_at`. Ningún estado cambia sin dejar rastro.

## Índices que no pueden faltar

- `users(email)` único.
- `refresh_tokens(token_hash)` único, y `refresh_tokens(user_id, family_id)`.
- `listings(status, published_at desc)` para el catálogo.
- `products(seller_id)`.
- `product_images(product_id, position)` único.
- `seller_verifications(document_number_lookup)` único **parcial**, solo sobre
  `status = 'VERIFIED'`. Es la lectura literal del criterio 5 de HU-002: dos
  personas pueden tener el mismo documento en revisión —pasa cuando alguien
  intenta usar la cédula de otro— y lo que no puede pasar es que las dos queden
  verificadas. `REVOKED` no bloquea.
- `seller_verifications(updated_at)` parcial sobre `status = 'PENDING_REVIEW'`,
  para la bandeja del moderador.
- `listings(submitted_at)` parcial sobre `status = 'PENDING_REVIEW'`, para la
  bandeja de moderación de publicaciones. Parcial porque la cola solo mira uno de
  los siete estados, así que el índice no crece con el catálogo publicado.
- `favorites(user_id, created_at desc, listing_id desc)` para la lista propia. Es
  el criterio 11 de HU-011 escrito como índice: ordena por el gesto y desempata por
  publicación, que es lo que el cursor compara.
- `payment_events(provider_event_id)` único.
- `orders(buyer_id, created_at desc)` y `orders(seller_id, created_at desc)`.

## Reglas de integridad

- La suma `product_amount + shipping_amount` debe igualar `total_amount`.
  `product_amount` es el **precio base** congelado (RN-030) y `shipping_amount` es
  el de la cotización elegida, que es la que se cobra y no se recalcula (RN-077).
- `commission_amount` debe ser el 5% de `product_amount` redondeado al peso.
  Se guarda calculado, no se recalcula al leer. **Nunca sobre `total_amount`**: el
  envío no entra en la base (RN-026).
- La fila de `shipping_quotes` con `chosen` en cierto es única por pedido, y su
  `amount` tiene que coincidir con `orders.shipping_amount`. Si no coinciden, se
  le cobró al comprador algo distinto de lo que eligió.
- Una publicación en `PUBLISHED` exige exactamente ocho imágenes del vendedor y
  cuatro canónicas. **Excepción única:** la tecnología con `is_sealed` en cierto
  exige cuatro y solo cuatro, todas canónicas (RN-065). Las imágenes de
  referencia no cuentan para ninguno de los dos conteos y no pueden ser las
  únicas de una publicación.
- Un pedido no puede referenciar un producto que ya está vendido en otro pedido
  pagado. Se resuelve con bloqueo al confirmar el pago, no con una consulta
  previa optimista.
