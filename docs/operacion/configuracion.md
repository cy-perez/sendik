# Configuración y variables

Regla general: **nada quemado en el código**. Ninguna URL, ninguna clave, ningún
correo, ningún NIT, ningún porcentaje de comisión.

## Cómo funciona

- El backend declara la configuración en clases `@ConfigurationProperties`
  tipadas y validadas dentro de `infrastructure`. No se usa `@Value` disperso.
- Si falta una variable obligatoria, la aplicación **no arranca**. Es preferible
  fallar al iniciar que descubrirlo con un usuario adentro.
- El frontend recibe su configuración en tiempo de ejecución desde el servidor,
  no incrustada en el paquete compilado. Así el mismo artefacto sirve para `dev`
  y para `prod`.
- Los secretos nunca están en el repositorio. En local, en `.env` ignorado por
  Git. En la nube, en Secret Manager.
- `.env.example` sí se versiona, con todas las claves y valores de ejemplo, nunca
  reales.

## Backend

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local`, `dev`, `prod` | sí |
| `DB_URL` | `jdbc:postgresql://localhost:5432/sendik` | sí |
| `DB_USERNAME` | `sendik` | sí |
| `DB_PASSWORD` | | sí |
| `SERVER_PORT` | `8080` | no, 8080 por omisión |
| `JWT_ISSUER` | `https://api.sendik.co` | sí |
| `JWT_SECRET` | clave de 256 bits como mínimo | sí |
| `JWT_ACCESS_TTL` | `PT15M` | sí |
| `JWT_REFRESH_TTL` | `P30D` | sí |
| `JWT_REFRESH_GRACE` | `PT10S` | no, `PT10S` por omisión |
| `SESSION_COOKIE_NAME` | `sendik_refresh` | no, `sendik_refresh` por omisión |
| `SESSION_COOKIE_PATH` | `/api/v1/auth` | no, `/api/v1/auth` por omisión |
| `SESSION_COOKIE_SECURE` | `true` | no, `true` por omisión |
| `RATE_LIMIT_CREDENTIALS_MAX` | `10` | no, `10` por omisión |
| `RATE_LIMIT_CREDENTIALS_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_SESSION_MAX` | `60` | no, `60` por omisión |
| `RATE_LIMIT_SESSION_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_ACCOUNT_MAX` | `120` | no, `120` por omisión |
| `RATE_LIMIT_ACCOUNT_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_LISTINGS_MAX` | `90` | no, `90` por omisión |
| `RATE_LIMIT_LISTINGS_WINDOW` | `PT1M` | no, `PT1M` por omisión |
| `RATE_LIMIT_MAX_KEYS` | `50000` | no, `50000` por omisión |
| `APP_BASE_URL` | `https://sendik.co` | sí |
| `APP_API_BASE_URL` | `https://api.sendik.co/api/v1` | sí |
| `APP_TIME_ZONE` | `America/Bogota` | no, `America/Bogota` por omisión |
| `CORS_ALLOWED_ORIGINS` | lista separada por comas | sí |
| `COMMISSION_RATE` | `0.05` | sí |
| `CLAIM_WINDOW_DAYS` | `3` | Fase 3 |
| `MAIL_PROVIDER` | `resend` o `console` | no, `resend` por omisión |
| `GCP_PROJECT_ID` | `sendik-col` | sí, si la cola está encendida |
| `MAIL_QUEUE_ENABLED` | `true` en Cloud Run | no, `false` por omisión |
| `MAIL_QUEUE_LOCATION` | `us-east1` | sí, si la cola está encendida |
| `MAIL_QUEUE_NAME` | `correo-transaccional` | sí, si la cola está encendida |
| `MAIL_QUEUE_HANDLER_URL` | `https://api-dev.sendik.co/internal/mail/deliveries` en `dev`; sobre `api.sendik.co` en `prod` | sí, si la cola está encendida |
| `MAIL_QUEUE_SERVICE_ACCOUNT` | cuenta que firma el token OIDC | sí, si la cola está encendida |
| `MAIL_QUEUE_AUDIENCE` | vacío | no, por omisión la propia `MAIL_QUEUE_HANDLER_URL` |
| `MAIL_PROVIDER_API_KEY` | clave de Resend, ver ADR-0012 | sí |
| `MAIL_FROM` | `hola@sendik.co` | sí |
| `MAIL_API_URL` | `https://api.resend.com/emails` | no |
| `MAIL_VERIFICATION_PATH` | `/verificar-correo` | no |
| `MAIL_PASSWORD_RESET_PATH` | `/restablecer-contrasena` | no |
| `MAIL_EMAIL_CHANGE_PATH` | `/confirmar-correo-nuevo` | no |
| `LEGAL_TERMS_VERSION` | `2026-08-01` | sí |
| `LEGAL_PRIVACY_VERSION` | `2026-08-01` | sí |
| `LEGAL_COOKIES_VERSION` | `2026-08-01` | no, solo frontend |
| `PASSWORD_BREACH_CHECK_ENABLED` | `true` | no, `true` por omisión |
| `PASSWORD_BREACH_CHECK_TIMEOUT` | `PT2S` | no |
| `PASSWORD_BREACH_API_URL` | `https://api.pwnedpasswords.com/range` | no |
| `STORAGE_PROVIDER` | `local` o `gcs` | no, `local` por omisión |
| `STORAGE_PUBLIC_BUCKET` | `sendik-publico` | sí, con `gcs` |
| `STORAGE_RESTRICTED_BUCKET` | `sendik-reservado` | sí, con `gcs` |
| `STORAGE_PROJECT_ID` | `sendik-col` | no |
| `VERIFICATION_REVIEW_DAYS` | `2` | no, `2` por omisión |
| `WOMPI_PUBLIC_KEY` | | Fase 3 |
| `WOMPI_PRIVATE_KEY` | | Fase 3 |
| `WOMPI_EVENTS_SECRET` | | Fase 3 |
| `WOMPI_INTEGRITY_SECRET` | firma de la transacción | Fase 3 |
| `WOMPI_BASE_URL` | permite apuntar a pruebas | Fase 3 |
| `TYPESENSE_HOST` | | Fase 3 |
| `TYPESENSE_PORT` | `8108` | Fase 3 |
| `TYPESENSE_API_KEY` | | Fase 3 |
| `SHIPPING_PROVIDER_API_KEY` | clave de Skydropx Colombia, ver ADR-0034 | Fase 3 |
| `SHIPPING_PROVIDER_BASE_URL` | permite apuntar al entorno de pruebas | Fase 3 |
| `SHIPPING_PROVIDER_WEBHOOK_SECRET` | firma de los eventos de seguimiento | Fase 3 |
| `SHIPPING_ORIGIN_POSTAL_CODE` | origen por omisión si el vendedor no lo tiene | Fase 3 |
| `COMPANY_NAME` | `Sendik` | sí |
| `COMPANY_TAX_ID` | `1054994043-9` | sí |
| `COMPANY_ADDRESS` | | sí |
| `SUPPORT_EMAIL` | | sí |

Las cuatro de envío sustituyen a `CARRIER_*_API_KEY`, que suponía una clave por
transportadora. Con el agregador es **una sola integración** (ADR-0034), y las
transportadoras dejan de ser configuración de Sendik: las habilita el proveedor.

`SHIPPING_PROVIDER_WEBHOOK_SECRET` no es opcional aunque lo parezca. De los eventos
de seguimiento sale la fecha de entrega, y de ella cuelgan la ventana de reclamo y
la liberación del pago (RN-079). Un evento de entrega que cualquiera pueda enviar
sin firma es una forma de que le paguen a un vendedor antes de tiempo. Es el mismo
razonamiento de `WOMPI_EVENTS_SECRET` y RN-037.

`COMMISSION_RATE` y `CLAIM_WINDOW_DAYS` son valores de negocio que el sitio
informativo **anuncia** (RN-026, RN-051). En Colombia lo anunciado es exigible,
así que la cifra no puede vivir en dos sitios: ni en una plantilla de Angular, ni
en un archivo de traducción. El texto lleva el marcador y el valor lo interpola
la aplicación.

Las dos se exponen al frontend desde HU-005, junto con los cuatro campos de
empresa: `readAppConfig` las lee, las valida y las deja en el estado transferido,
y las páginas informativas las interpolan en el texto. Si faltan, se sirve el
valor de la regla de negocio —5% y 3 días— porque es el correcto; lo único que se
pierde es poder cambiarlo sin desplegar. Lo que no se acepta es una cifra
distinta de la regla sin que nadie se entere: los rangos se validan al arrancar y
un valor fuera de ellos no levanta el servidor.

Las tres filas de empresa y `SUPPORT_EMAIL` figuran como obligatorias porque el
backend no arranca sin ellas. El frontend las trata como opcionales por lo que se
explica más abajo, en su propia tabla.

`CLAIM_WINDOW_DAYS` se cuenta en días **hábiles** desde la entrega y gobierna dos
cosas: hasta cuándo puede reportar el comprador y cuándo se da la entrega por
confirmada si no hace nada (RN-051, RN-052). Cambiarla mueve las dos.

**Lo que ya no gobierna es cuándo cobra el vendedor.** Desde RN-075 esas son dos
fechas distintas: la entrega se confirma al vencer esta ventana, pero el pago no se
libera hasta que pasan los cinco días hábiles del derecho de retracto. Antes eran la
misma fecha, y por eso los días cuarto y quinto el comprador conservaba un derecho
legal sobre un dinero que ya se había entregado.

**Hoy la lee solo el frontend**, para escribir la cifra en las páginas
informativas. En el backend figura como de Fase 3 porque no hay ninguna clase de
propiedades que la lea todavía: la ventana no se aplica hasta que existan los
pedidos. Cuando entre, las dos tienen que valer lo mismo, igual que las versiones
de los documentos legales: una página que anuncie una ventana de reclamo de tres
días y un sistema que la cierre a los cinco es publicidad engañosa. Ojo con no
confundirlas: que el pago se libere al quinto día hábil no es la ventana de reclamo,
es RN-075, y los términos lo explican por separado.

`APP_TIME_ZONE` no es cosmética: RN-008 compara fechas de calendario, no
instantes. Con UTC, alguien en Colombia cumpliría 18 años cinco horas antes de
que aquí sea su cumpleaños.

`JWT_REFRESH_GRACE` es la ventana de gracia de RN-007 (ADR-0014). Durante ese
tiempo después de rotar un token, y sólo mientras el que salió de la rotación
siga sin usarse, recibirlo de nuevo se trata como una carrera entre dos pestañas
y no como reutilización: se rechaza la petición pero no se revoca la familia ni
se avisa al titular. Se mide en segundos porque cubre una ida y vuelta, no una
sesión. Subirla a minutos le regala ese mismo tiempo a un token robado para
reproducirse sin levantar la alarma; ponerla en `PT0S` devuelve el comportamiento
anterior a ADR-0014, con sus falsos avisos de incidente.

Las variables `RATE_LIMIT_*` limitan cuántas peticiones se aceptan en las rutas de
cuenta. RN-006 protege una cuenta; esto protege el endpoint, que es otra
cosa: sin ello, cinco intentos por cuenta no impiden probar una contraseña común
contra todas las cuentas que se quiera, ni usar el registro como emisor de correo
gratuito contra la cuota de Resend, ni mantener bloqueada indefinidamente la
cuenta de alguien cuyo correo se conozca.

Son **tres** grupos porque las rutas no se parecen. `CREDENTIALS` cubre ingreso,
registro, verificación y recuperación, que son actos humanos y poco frecuentes.
`SESSION` cubre refresco y cierre, que los dispara el navegador solo y con varias
pestañas abiertas se repiten sin que nadie haga nada raro. Un límite único
tendría que ser el más flojo de los dos.

`ACCOUNT` es el tercero y llegó después: cubre `/api/v1/users`, las rutas que
exigen sesión. Hasta entonces **ninguna ruta autenticada tenía tope**, así que
cualquier cuenta registrada podía repetir sin freno una lectura que ejecuta un
agregado. Va holgado —120 por minuto— porque son lecturas normales de la
aplicación: el panel del vendedor pide dos por cada guardado y una persona
trabajando no se acerca a esa cifra. Lo que corta es el guion que repite.

**Y se cuenta por sujeto del token, no por origen.** En `auth` se cuenta por IP
porque en el registro todavía no hay cuenta a la que atribuir la petición; aquí sí
la hay. Contar por IP en estas rutas dejaría sin servicio a una oficina o a un
operador móvil entero por lo que hiciera una sola persona —la misma razón por la
que cada ruta lleva su cuenta aparte— y además le regalaría cupo nuevo a quien
cambie de salida.

**La cuenta es de cada instancia.** Con dos réplicas detrás de un balanceador,
cada una permite el máximo por separado y el límite real se duplica. Es aceptable
mientras el despliegue sea de una sola instancia (ADR-0009); al escalar
horizontalmente el conteo tiene que mudarse a un almacén compartido o al
balanceador.

`MAIL_PROVIDER=console` sustituye el envío real por un adaptador que imprime el
enlace de verificación en el registro de la aplicación. Es lo que permite
recorrer HU-001 entera sin credenciales, y por eso es el valor del perfil
`local`. En `dev` y `prod` no se usa: imprimiría un token de un solo uso, que es
una credencial.

`LEGAL_TERMS_VERSION` y `LEGAL_PRIVACY_VERSION` identifican el texto que la
persona acepta al registrarse. Se guardan con el consentimiento y son la prueba
de a qué dijo que sí: una versión equivocada invalida esa prueba. Cambian cada
vez que se publica un texto nuevo.

**Las lee también el frontend, y tienen que valer lo mismo en los dos.** El
backend las guarda como evidencia y el frontend las usa para elegir qué archivo
de texto sirve: si no coinciden, se muestra un documento distinto del que quedó
escrito y la prueba deja de valer. El nombre del archivo lleva la versión
(`frontend/public/legal/<documento>.<versión>.<idioma>.html`), así que cambiar la
variable sin subir el texto nuevo da un error visible y no un texto viejo con
etiqueta nueva. El procedimiento completo está en
`docs/operacion/textos-legales.md`.

`LEGAL_COOKIES_VERSION` es solo del frontend: nadie consiente cookies en un
formulario, así que el backend no guarda nada de ella y existe únicamente para
versionar el archivo de ese texto.

Si no se declaran, las tres caen en `borrador-local`, que es la versión de los
textos de relleno. No se exigen para no romper el arranque en la máquina de quien
programa; un despliegue que las olvide sirve el borrador, y el borrador dice en
su primera línea que no tiene valor legal, además de que la página muestra un
aviso.

`PASSWORD_BREACH_CHECK_ENABLED` apaga la consulta a Have I Been Pwned
(ADR-0013). Se apaga en las pruebas de extremo a extremo y en desarrollo sin
red; el mínimo de diez caracteres de RN-005 se sigue comprobando siempre, porque
esa regla vive en el dominio y no depende de nadie.

La tasa de comisión es configurable a propósito: es un número de negocio que
puede cambiar, y no debería exigir un despliegue de código.

Los datos de la empresa también: hoy la operación es como persona natural y más
adelante puede constituirse una sociedad. Ese cambio debe ser una variable, no
una búsqueda de texto por todo el repositorio.

### Verificación de vendedor

| Variable | Qué es | Por omisión |
|---|---|---|
| `VERIFICATION_REVIEW_DAYS` | Días hábiles que se promete tardar en revisar | `2` |

La dicen en voz alta la pantalla y el correo de «solicitud recibida», así que cambiarla
no puede exigir un despliegue de código. **Nadie la hace cumplir**: una solicitud que
tarda más no cambia de estado sola ni avisa a nadie. Si eso hace falta, es una regla
nueva y hay que escribirla en `reglas-negocio.md`.

El backend la valida entre 1 y 20. El cero está cerrado a propósito: prometer revisar
«en cero días hábiles» no significa nada, y en Colombia lo anunciado es exigible.

### Quién es moderador

| Variable | Qué es | Por omisión |
|---|---|---|
| `SECURITY_BOOTSTRAP_MODERATORS` | Correos, separados por comas, que reciben el rol `MODERATOR` al arrancar | vacía |

Responde a una pregunta que HU-002 dejó abierta: **cómo existe el primer moderador**
en un entorno nuevo. Hasta ahora el rol solo se concedía con un `INSERT` escrito a
mano contra la base, lo que obliga a entrar a la base de producción para dar de alta
a la primera persona que puede aprobar verificaciones.

Vacía —que es lo que hay por omisión— no hace absolutamente nada.

Viaja desde `despliegue.yml` como variable del entorno de GitHub, con el delimitador
alterno de `gcloud` (`^;^`) porque el valor lleva comas y sin él una lista de dos
moderadores se partiría en dos variables. **Va junta con `FEATURE_PUBLISHING`**: un
entorno con la bandera encendida y esta vacía deja publicar y no deja aprobar.

**Lo que no hace, y es la mitad importante:**

- **No crea cuentas.** Si el correo no tiene una, se registra un aviso y se sigue.
  Si de aquí pudiera salir una cuenta nueva con rol de moderador, esto sería una
  puerta trasera y no una forma de conceder un rol.
- **Exige el correo verificado.** Registrarse solo demuestra que alguien sabe
  escribir una dirección; verificarla demuestra que controla el buzón, y este rol
  da acceso a las cédulas y selfies de todos los vendedores pendientes. A quien
  esté configurado y todavía no haya verificado, el rol le llega en cuanto abra su
  enlace. **Esto importa:** sin la comprobación, quien se adelantara a la persona
  legítima —el correo de moderación de un marketplace es adivinable— se llevaba el
  rol sin tocar ese buzón, y entraba con él, porque una cuenta sin verificar entra
  igual (criterio 13 de HU-001).
- **No abre sesiones** ni salta ninguna comprobación de autenticación.
- **No concede `ADMIN`.** Solo `MODERATOR`.
- **No revoca.** Quitar un correo de la lista no le quita el rol a nadie. Un
  despliegue con la variable mal puesta degradaría a todos los moderadores en
  silencio, y quedarse sin quien apruebe es peor que un rol de más. Retirar el rol
  sigue siendo una operación deliberada, a mano, hasta el panel administrativo de la
  Fase 4.

Es idempotente: arrancar cien veces deja lo mismo que arrancar una.

**Siempre deja rastro en el registro del arranque.** Otorgar autorización en silencio
es lo que convierte un mecanismo legítimo en algo indistinguible de una puerta
trasera: si alguien pregunta por qué esa cuenta modera, la respuesta tiene que estar
en el log. Un correo mal escrito no detiene el arranque —lo que está en juego es que
una persona se quede sin un rol, no que el servicio funcione— y se registra como
aviso, distinguiendo la errata del correo sin cuenta, porque se corrigen en sitios
distintos.

Recuerda RN-060: un moderador no puede decidir sobre su propia solicitud de
verificación. Poner aquí a alguien que además va a venderse no le sirve para
auto-aprobarse.

### Cifrado de datos sensibles

Las columnas cifradas de la verificación de vendedor: número de documento y número
de cuenta bancaria (RN-046, ADR-0020). Los archivos **no** pasan por aquí; la cédula
y la selfie van al almacén reservado.

| Variable | Qué es | Por omisión |
|---|---|---|
| `CRYPTO_DATA_KEY_V1` | Clave de cifrado AES-256-GCM de la versión 1, en base64 | **obligatoria** |
| `CRYPTO_CURRENT_KEY_VERSION` | Con qué versión se cifra lo nuevo. Tiene que existir en el mapa de claves | `1` |
| `CRYPTO_LOOKUP_KEY` | Clave del HMAC-SHA256 con el que se compara sin descifrar, en base64 | **obligatoria** |

Las dos obligatorias lo son en `dev` y en `prod`: sin ellas la aplicación no
arranca. El perfil `local` trae claves de desarrollo escritas en
`application-local.yaml`, que están en el repositorio y justo por eso no sirven
para nada fuera de localhost.

**Son dos claves independientes y no es ceremonia.** Si el HMAC de búsqueda usara la
clave de cifrado, filtrar una daría las dos capacidades a la vez: descifrar y
confirmar adivinaciones. Y adivinar es barato, porque una cédula colombiana es un
número de ocho a diez dígitos. La aplicación **no arranca** si `CRYPTO_LOOKUP_KEY`
coincide con alguna clave de cifrado.

Las claves de datos son un mapa de versión a clave, y no una sola clave, para poder
rotar sin reescribir la tabla de golpe: cada fila guarda con qué versión se cifró
—`document_number_key_version`— y esa clave tiene que seguir configurada mientras
exista una fila que la use. Rotar es agregar `CRYPTO_DATA_KEY_V2` y mover
`CRYPTO_CURRENT_KEY_VERSION` a `2`; retirar la versión 1 es un cambio posterior, y
solo después de migrar las filas que todavía la referencian.

Cambiar `CRYPTO_LOOKUP_KEY` es distinto y **no es una rotación sin más**: el índice
único que impide que un mismo documento quede verificado en dos cuentas (criterio 5
de HU-002) está construido sobre ese HMAC. Cambiar la clave invalida todos los
valores ya guardados y hay que recalcularlos, o el índice deja de detectar
duplicados sin que falle nada.

### Almacenamiento de archivos

Dos almacenes con garantías distintas (ADR-0018): el **público** sirve la foto de
perfil y, en Fase 2, las tomas de producto; el **reservado** guarda la cédula y la
selfie, que nunca se sirven por una dirección pública (RN-046).

| Variable | Qué es | Por omisión |
|---|---|---|
| `STORAGE_PROVIDER` | `local` o `gcs` | `local` |
| `STORAGE_LOCAL_PATH` | Raíz de los dos almacenes con `local` | `./archivos-locales` |
| `STORAGE_PUBLIC_BASE_URL` | Desde dónde se sirven los archivos públicos | obligatoria en dev y prod |
| `STORAGE_MAX_IMAGE_BYTES` | Tope por imagen | `8388608` (8 MB) |
| `STORAGE_AVATAR_MIN_WIDTH` | Ancho mínimo de la foto de perfil | `200` |
| `STORAGE_AVATAR_MIN_HEIGHT` | Alto mínimo de la foto de perfil | `200` |
| `STORAGE_LISTING_MIN_WIDTH` | Ancho mínimo de una toma de producto (RN-019) | `900` |
| `STORAGE_LISTING_MIN_HEIGHT` | Alto mínimo de una toma de producto (RN-019) | `1200` |
| `STORAGE_MAX_UPLOAD_SIZE` | Tope del cuerpo multipart | `10MB` |
| `STORAGE_PUBLIC_BUCKET` | Cubo de lo que cualquiera ve | vacía; obligatoria con `gcs` |
| `STORAGE_RESTRICTED_BUCKET` | Cubo de la cédula y la selfie | vacía; obligatoria con `gcs` |
| `STORAGE_PROJECT_ID` | Proyecto de Google Cloud | vacía; la toma de las credenciales |

Las tres últimas son solo de `gcs` y se ignoran con `local`.

**Los dos mínimos de la toma de producto no son un valor de arranque como los del
avatar: son RN-019.** Bajarlos incumple la regla. Existen como variable porque el
resto del almacenamiento lo es, no porque se esperen otros valores. La proporción
3:4 de RN-018 **no** se parametriza: se calcula de estos dos, para que no puedan
contradecirse entre sí.

**`STORAGE_MAX_UPLOAD_SIZE` es del resolvedor multipart y va por encima de
`STORAGE_MAX_IMAGE_BYTES`**, para dejar sitio a la envoltura del multipart. Sin
declararlo regía el 1 MB por omisión de Spring Boot, y con ese tope el de la
imagen era inalcanzable: cualquier foto de teléfono moría en el resolvedor antes
de que ninguna política la mirara, con un 500 que nadie podía explicar. Quien
decide si el archivo se acepta sigue siendo la política de imagen; esto solo evita
que el servidor se coma un cuerpo enorme antes de mirarlo.

**Los dos cubos no pueden ser el mismo, y la aplicación no arranca si lo son.** La
comprobación está en `StorageProperties` y existe porque es el error que no avisa:
con un solo cubo todo funciona igual, y la cédula de la primera persona que se
verifique queda en un cubo con lectura pública (RN-046). Los nombres son variables y
no constantes del código porque el nombre de un cubo es único en todo Google: si
`sendik-publico` estuviera tomado, el cubo se llama de otra forma y eso no puede
exigir tocar el código.

**Con `gcs` no hace falta ninguna clave.** Las credenciales son las de aplicación por
omisión: dentro de Cloud Run, la cuenta de servicio del servicio, sin ningún secreto
que rotar; en una máquina de desarrollo, lo que deja `gcloud auth
application-default login`. Un archivo de clave JSON descargado no se necesita en
ninguno de los dos casos, y es justo el que acaba subido a un repositorio por
accidente. El procedimiento para probar en local está en `despliegue.md`, paso 3.

`STORAGE_PROVIDER=local` guarda en el sistema de archivos y es el equivalente del
proveedor de correo de consola: permite recorrer el criterio 21 completo sin
credenciales de ningún proveedor. **En la nube no vale**, porque el sistema de
archivos de Cloud Run es efímero y lo guardado desaparece con la instancia.

`STORAGE_PUBLIC_BASE_URL` no se guarda en la base de datos. Si se guardara la
dirección completa en cada fila, cambiar de CDN obligaría a reescribir la tabla; lo
que se guarda es la clave, y la dirección se compone al servir.

Subir `STORAGE_MAX_IMAGE_BYTES` por encima de 32 MB no sirve de nada: Cloud Run no
acepta peticiones más grandes.

**El mínimo del avatar no es RN-019.** Esa regla fija 900 × 1200 y es de las tomas
de producto (HU-003). Aplicársela a la foto de perfil rechazaría casi cualquier foto
que alguien tenga a mano, así que el avatar tiene su propio mínimo. Nadie ha
decidido cuál debe ser: los 200 × 200 son un valor de arranque que solo evita que se
suba un icono de 16 px, no una regla de negocio. Si se decide una, entra en una
historia y se anota en `reglas-negocio.md`.

## Solo para desarrollo local

Estas dos no las lee la aplicación: las lee `docker-compose.yml` para crear la
base de datos. Viven en el mismo `.env` por comodidad, y en la nube no existen.

| Variable | Ejemplo |
|---|---|
| `DB_NAME` | `sendik` |
| `DB_PORT` | `5432` |

El backend siempre se conecta por `DB_URL`. Si se cambia `DB_PORT`, hay que
cambiar también el puerto dentro de `DB_URL`: son dos valores distintos a
propósito, porque en la nube el contenedor no existe y solo queda la URL.

## Frontend

Las lee el servidor de renderizado y viajan al navegador dentro del HTML, en el
estado transferido. No se compilan dentro del paquete: el mismo artefacto sirve
para `dev` y para `prod`.

| Variable | Ejemplo | Obligatoria |
|---|---|---|
| `API_BASE_URL` | `https://api.sendik.co/api/v1` | sí, es `APP_API_BASE_URL` con otro nombre |
| `NG_ALLOWED_HOSTS` | `sendik.co,www.sendik.co` | sí |
| `DEFAULT_LOCALE` | `es` | no, `es` por omisión |
| `AVAILABLE_LOCALES` | `es,en` | no, `es,en` por omisión |
| `PORT` | `4000` | no, 4000 por omisión |
| `SENTRY_DSN` | opcional | no |
| `ENABLE_DEVTOOLS` | `false` en producción | no |
| `COMPANY_NAME` | `Sendik S.A.S.` | no, el pie lo omite si falta |
| `COMPANY_TAX_ID` | `1054994043-9` | no, el pie lo omite si falta |
| `COMPANY_ADDRESS` | `Medellín, Colombia` | no, el pie lo omite si falta |
| `SUPPORT_EMAIL` | `hola@sendik.co` | no, el pie lo omite si falta |
| `COMMISSION_RATE` | `0.05` | no, RN-026 por omisión |
| `CLAIM_WINDOW_DAYS` | `3` | no, RN-051 por omisión |
| `LISTING_REVIEW_DAYS` | `2` | no, 2 por omisión |

Las dos últimas son las cifras que el sitio informativo **anuncia**: la comisión
en el recorrido del vendedor y la ventana de reclamo en el del comprador
(HU-005). Viajan al navegador porque las páginas las dicen en voz alta, y no son
secretas: cualquiera que entre las lee.

**`LISTING_REVIEW_DAYS` es del frontend y no del backend, a diferencia de
`VERIFICATION_REVIEW_DAYS`, que está en los dos.** El motivo es concreto: el plazo
de la verificación lo dice la pantalla **y** el correo de «recibimos tu solicitud»,
así que el backend también lo necesita; el de la publicación hoy solo lo dice la
pantalla, porque no se manda ningún correo al enviar a revisión. Declararlo también
en el backend sería configuración que nadie lee. El día que exista ese correo, entra
allí con el mismo nombre.

Se validan al leerlas, no al pintarlas. `COMMISSION_RATE` es una **fracción**
mayor que `0` y hasta `0.5`: `0.05` es el 5%, quien declare `5` estaría
anunciando un 500% y quien declare `0`, que no se cobra nada, así que el servidor
no arranca en ninguno de los tres casos. El cero importa tanto como el 5: un
despliegue a medias que deje la variable en cero publica «no cobramos comisión»
en las cuatro páginas, y en Colombia lo anunciado es exigible frente a RN-026.
`CLAIM_WINDOW_DAYS` tiene que ser un entero de días hábiles entre `1` y `30`; el
techo es de cordura, no una regla: la ventana son 3 días (RN-051) y una de meses
retiene el dinero del comprador todo ese tiempo. Una promesa incorrecta publicada
no es un problema de maquetación.

Si faltan, se sirve el valor de las reglas de negocio, que es el correcto; lo
único que se pierde es poder cambiarlo sin desplegar.

Las cuatro últimas son **las mismas variables que lee el backend**, no unas
paralelas: es la misma empresa y no tendría sentido que el pie del sitio dijera
un NIT y los correos otro. Arriba figuran como obligatorias porque el backend no
arranca sin ellas; aquí no lo son porque el frontend solo las pinta, y tumbar el
renderizado entero por una dirección que falta cambiaría un pie incompleto por un
sitio caído. `SUPPORT_EMAIL` merece atención especial: es el canal por el que se
ejercen los derechos del titular de los datos, así que un pie sin él incumple
`docs/operacion/datos-personales.md`.

**`API_BASE_URL` y `APP_API_BASE_URL` son el mismo valor con dos nombres**, y por
eso se declara uno solo por entorno: el flujo de despliegue le pasa
`APP_API_BASE_URL` a las dos piezas y el frontend lo recibe como `API_BASE_URL`,
que es el nombre con el que lo lee `read-app-config.ts`. **Incluye el prefijo
`/api/v1`**: el frontend cuelga las rutas de ahí sin agregar nada, así que sin el
prefijo todas las peticiones caen en 404. Es lo que ya hacen las dos pruebas de
extremo a extremo en `playwright.completo.config.ts`.

`NG_ALLOWED_HOSTS` es la lista de dominios a los que el servidor acepta
responder y protege contra falsificación de peticiones del lado del servidor. La
lee `@angular/ssr`, no la aplicación. Merece atención especial porque **si falta,
Angular no falla**: registra un aviso y entrega la página sin renderizar, para
que la pinte el navegador. El sitio parece funcionar mientras el buscador y la
vista previa de WhatsApp reciben un documento vacío, que es justo lo que
ADR-0006 existe para impedir. Por eso `src/server.ts` la exige al arrancar y no
levanta el servidor sin ella.

En local, `npm start` y `npm run build` leen el `.env` de la raíz del repositorio
con `--env-file-if-exists`. **Ese archivo no se versiona: hay que crearlo copiando
`.env.example` antes del primer arranque** (`README.md`).

Si falta, `npm start` levanta el servidor igual —el `if-exists` hace lo que
promete— y cada página responde **500 con el motivo escrito**, no un error al
llamar a la API. La diferencia importa porque durante un tiempo no fue así: la
aplicación arrancaba con la configuración de relleno de
`readAppConfigForBootstrap`, con `apiBaseUrl` vacía, el interceptor lanzaba
dentro del renderizado y la petición se quedaba colgada para siempre sin una
línea en el registro. La validación estricta existía en `src/server.ts` pero solo
corría bajo `isMainModule`, y bajo `ng serve` ese bloque no se ejecuta nunca: el
CLI importa `reqHandler`. Es decir, el único entorno donde alguien programa era
justo el que no validaba nada. Ahora hay una guarda propia en el manejador, antes
del renderizado, que cubre los dos casos.

## Banderas de funcionalidad

Se manejan como configuración, no como ramas de Git de larga vida:

| Bandera | Efecto |
|---|---|
| `FEATURE_SELLER_VERIFICATION` | Habilita el flujo de verificación |
| `FEATURE_PUBLISHING` | Habilita publicar prendas |
| `FEATURE_CATALOG` | Habilita el catálogo público: listado, categorías, ficha y perfil del vendedor |
| `FEATURE_CHECKOUT` | Habilita el proceso de compra |
| `FEATURE_SEARCH` | Habilita la búsqueda y los filtros del catálogo (HU-014). Contra PostgreSQL, no Typesense (ADR-0035) |
| `FEATURE_SPIN_VIEWER` | Habilita el visor 360 |

Se enciende cada una cuando su funcionalidad exista y no cuando empiece la fase
que la contiene. Es lo que permite desplegar código incompleto sin exponerlo.

**El valor por omisión de las seis es `false`, y se decide por entorno.** Cuatro
viajan desde `despliegue.yml` como variables del entorno de GitHub, así que `dev` y
`prod` se deciden por separado y sin tocar código: las tres de la Fase 2
—`FEATURE_SELLER_VERIFICATION`, `FEATURE_PUBLISHING` y `FEATURE_CATALOG`— y
`FEATURE_SEARCH`, que se sumó el 10 de septiembre de 2026 al integrar HU-014.
`FEATURE_CHECKOUT` y `FEATURE_SPIN_VIEWER` ni siquiera se pasan: no hay nada que
encender todavía.

**`FEATURE_SEARCH` está encendida en `dev` y apagada en `prod`**, que es donde
estaban las tres anteriores. En `prod` lo está por omisión —la variable no existe
allí— y no hace falta más: mientras `prod` no se despliegue, definirla sería decidir
algo que nadie ha decidido.

**No va emparejada, pero sí ordenada, y la dirección que importa es la contraria a la
que parece.** Encender la búsqueda con el catálogo apagado no rompe nada: la ruta que
las dos comparten responde 404 de todos modos, así que la búsqueda sola no se nota.
Lo que sí deja pantalla rota es **el catálogo encendido con la búsqueda apagada**: el
frontend no conoce las banderas —no hay mecanismo para ello y nunca lo ha habido—, así
que pinta la caja de búsqueda y el panel de filtros igual, y en cuanto alguien los usa
el backend responde 404, que la pantalla enseña como error.

Por eso el orden al encenderlas en un entorno nuevo es: **la búsqueda se enciende con
el catálogo o después, nunca el catálogo solo**. Hoy no afecta a nadie —en `dev` las
cuatro están encendidas y `prod` no se ha desplegado— pero es justo el estado en el que
quedaría `prod` el día que alguien encienda allí las tres de la Fase 2 siguiendo la
tabla de `docs/operacion/despliegue.md`.

Van con respaldo explícito (`${{ vars.X || 'false' }}`) y no a secas. Una variable
que no está definida se expande a cadena vacía, y ahí el `${FEATURE_CATALOG:false}`
de `application.yaml` no ayuda: el valor por omisión entra cuando la variable **no
está**, no cuando está y vale vacío. Un booleano vacío tumba el arranque al enlazarlo.

**`FEATURE_PUBLISHING` no se enciende sola.** Sin `SECURITY_BOOTSTRAP_MODERATORS`
en el mismo entorno no hay nadie con el rol para aprobar, así que se puede publicar
y nada sale de `PENDING_REVIEW`. Es el callejón sin salida que HU-008 vino a cerrar,
y encender una sin la otra lo reabre.

`FEATURE_CATALOG` es distinta de las demás en una cosa: es la única que enciende
páginas para quien **no tiene cuenta**. Encenderla no es exponer una funcionalidad
más, es abrir la tienda. Con ella apagada los endpoints del catálogo responden 404
y sus rutas del sitio muestran el estado de no encontrado, que es lo mismo que hace
`FEATURE_PUBLISHING` con las pantallas del vendedor.

«Sin exponerlo» es literal en el caso de `FEATURE_SELLER_VERIFICATION`: con la bandera
apagada, el controlador de la verificación no se crea y sus rutas responden 404. No
rechazan la petición, no existen. Un 403 le confirmaría a cualquiera que la
funcionalidad está ahí esperando.

## Rotación de secretos

Toda clave se puede rotar sin cambiar código. La clave de firma de tokens admite
dos valores simultáneos durante la rotación, para no invalidar las sesiones
activas de golpe.
