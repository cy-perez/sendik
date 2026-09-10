# Despliegue: qué hay que crear una vez

El flujo ya está escrito: `.github/workflows/despliegue.yml`. Lo que falta no es
código, son las cuentas y los permisos. Este documento es la lista, en orden, de
lo que hay que crear a mano una sola vez.

La arquitectura y los costos están en `entornos.md`. Aquí solo está el
procedimiento.

## Cuándo se ejecuta esta lista

**Ya se ejecutó, para `dev`.** El 2 de septiembre de 2026 quedó en pie:
`https://dev.sendik.co` sirve la portada renderizada en servidor y
`https://api-dev.sendik.co` responde, las dos con certificado gestionado por Google
y sin costo. Los nueve pasos están hechos para ese entorno.

Esta sigue siendo la lista que hay que seguir para levantar **`prod`**, y por eso
está escrita en presente. Lo que cambió es que ahora está recorrida entera al menos
una vez, y lo que costó cada paso está anotado donde ocurrió.

El dominio `sendik.co` se contrató en GoDaddy el 26 de agosto de 2026 y con eso se
cerró la última decisión que faltaba, la del hospedaje (ADR-0024). **La lista se
sigue entera**, de principio a fin.

Los nueve pasos hacen falta para levantar `dev`: el proyecto (1), la base de datos
en capa gratuita (2), los dos cubos (3), los secretos en Secret Manager (4), las
cuentas de servicio (5), la federación de identidades con GitHub (6), el hospedaje
y el DNS (7), los entornos de GitHub con sus variables (8) y la comprobación (9).

Del paso 8, lo único que es exclusivo de producción es el revisor obligatorio y la
columna `prod` de las tablas de variables: se pueden dejar para después sin que
`dev` se resienta, aunque cuesta menos configurarlas de una vez.

**Y sigue costando cero.** Cloud Run escala a cero en las dos piezas, la base de
datos es Neon o Supabase en capa gratuita, Cloud Storage entra en sus 5GB
gratuitos, el certificado lo gestiona Google sin cobrar y el DNS va incluido con el
dominio. Lo único ya pagado es el dominio, que era la condición para llegar aquí.

Lo que **sigue esperando** es producción: la instancia mínima siempre activa, Cloud
SQL —que cobra por hora encendida aunque nadie lo use— y el dominio raíz
`sendik.co`, que no se apunta a ningún sitio hasta que haya algo terminado detrás.

> **Antes de desplegar a producción de verdad**, falta una cosa que no es
> técnica: los tres textos legales siguen siendo `borrador-local`, que es relleno
> sin valor legal (`textos-legales.md`). Publicar el registro con un consentimiento
> que apunta a un borrador no es aceptable. **Para `dev` no bloquea mientras solo
> entre quien lo construye**; en cuanto entre alguien más, sí.

## Lo que hace el flujo

```
main            -> verificación -> dev  (automático)
etiqueta vX.Y.Z -> verificación -> prod (con aprobación de una persona)
```

El despliegue **llama** a `verificacion.yml` en lugar de repetir sus pasos, así
que nada se publica sin compilar, pasar las pruebas de los cinco módulos, la
cobertura agregada, el linter, las pruebas de accesibilidad y las de extremo a
extremo completas.

**Mientras no haya nada configurado, los dos trabajos de despliegue se omiten** y
la ejecución sale en verde con un aviso que apunta a este documento. Es
deliberado: si fallaran, cada integración a `main` dejaría una ejecución roja, y
una canalización que está roja siempre deja de leerse justo antes del día en que
se rompe de verdad. El interruptor es la presencia de `GCP_PROJECT_ID`; en cuanto exista, **las dos
piezas empiezan a desplegarse**: el backend en Java y el frontend con renderizado
en servidor, las dos a Cloud Run y desde el mismo commit verificado (ADR-0024).

Los secretos no pasan por GitHub Actions. Cloud Run los lee de Secret Manager y
el flujo solo dice qué secreto va en qué variable de entorno. Consecuencia
práctica: rotar la clave de Resend no toca el repositorio, y el registro de una
ejecución no puede contener una contraseña.

## 1. Google Cloud

Crea el proyecto y anota su identificador. **El del proyecto es `sendik-col`**, y
hoy es uno solo para todo: separar `dev` y `prod` en dos proyectos es una decisión
del lanzamiento, y mientras no haya nada desplegado no habría qué separar.

```bash
gcloud projects create sendik-col --name="Sendik"
gcloud config set project sendik-col

gcloud services enable \
  run.googleapis.com \
  artifactregistry.googleapis.com \
  secretmanager.googleapis.com \
  iamcredentials.googleapis.com
```

El repositorio de imágenes, en `us-east1` (la región la decide `entornos.md`):

```bash
gcloud artifacts repositories create sendik \
  --repository-format=docker \
  --location=us-east1 \
  --description="Imágenes de Sendik"
```

### El registro no guarda el texto de las búsquedas

Los registros de peticiones de Cloud Run llevan el URL completo —con su cadena de
consulta— en la misma entrada que la IP de quien pidió. Desde HU-014 ese URL puede
traer `q`, que es lo que alguien escribió para buscar. Guardado junto a la IP, eso
reconstruye por accidente el historial de búsquedas que la historia decidió **no**
tener (`docs/operacion/datos-personales.md`).

Se descarta antes de almacenarse, con una exclusión en el sink `_Default`:

```bash
gcloud logging sinks update _Default \
  --add-exclusion='name=peticiones-con-texto-de-busqueda,description=HU-014: el texto que alguien busca no se conserva junto a su IP. Descarta la entrada de registro de peticiones cuyo URL lleve el parametro q. Las peticiones sin texto siguen registradas y las metricas de Cloud Run no se ven afectadas.,filter=logName:"run.googleapis.com%2Frequests" AND httpRequest.requestUrl=~"[?&]q="'
```

**Va sin `service_name`, y es a propósito:** al ser del proyecto cubre
`sendik-backend-dev`, `sendik-web-dev` y los dos servicios de `prod` el día que
existan, sin que nadie tenga que acordarse de repetirla. Es también la razón de que
esté en el paso 1 y no en el 7.

El filtro pide `[?&]q=` y no `q=` suelto: sin los delimitadores se llevaría por
delante cualquier URL que contenga esas dos letras dentro de otro parámetro.

**Lo que se pierde y lo que no.** Se pierde la entrada de registro de las peticiones
**con texto**: su estado y su latencia dejan de poder mirarse una por una. No se pierde
nada más. Las peticiones sin `q` —el catálogo, los filtros, las fichas— siguen
registradas enteras, y las métricas de Cloud Run son otra cosa que una exclusión de
registros no toca: el conteo y la latencia de la búsqueda se siguen viendo ahí, que es
con lo que habrá que responder si la búsqueda pública necesita límite de tasa.

Comprobarlo, después de hacer una búsqueda de verdad contra el entorno:

```bash
# No debe traer nada. Si trae algo, la exclusión no está puesta.
gcloud logging read \
  'logName:"run.googleapis.com%2Frequests" AND httpRequest.requestUrl=~"[?&]q="' \
  --limit 5 --freshness=1h

# Y el control: una petición al mismo endpoint sin texto sí queda registrada.
gcloud logging read \
  'logName:"run.googleapis.com%2Frequests" AND httpRequest.requestUrl=~"/api/v1/listings"' \
  --limit 5 --freshness=1h --format='value(httpRequest.requestUrl)'
```

## 2. La base de datos

En `dev` es Neon o Supabase en capa gratuita; al lanzar `prod`, Cloud SQL. Lo único que necesita el backend es la cadena de conexión JDBC, el usuario y
la contraseña.

Crea la base y guarda los tres valores como secretos. **Con Cloud SQL hace falta
además el conector**, y entonces el `DB_URL` cambia de forma; con Neon o Supabase
basta la cadena normal con `sslmode=require`.

### La cadena del proveedor no es la que va en el secreto

Neon entrega una sola cadena, en formato libpq, con todo dentro:

```
postgresql://ROL:CONTRASEÑA@ep-algo-pooler.REGION.aws.neon.tech/BASE?sslmode=require&channel_binding=require
```

De ahí salen los tres secretos, y traducirla de memoria es donde se pierde el
tiempo:

| Secreto | De dónde sale |
|---|---|
| `sendik-db-url` | `jdbc:postgresql://` + el host + `/` + **la base tal como viene** + `?sslmode=require` |
| `sendik-db-username` | el **rol** de la cadena, no el nombre del proyecto |
| `sendik-db-password` | la contraseña de la cadena |

**El rol y la base no se llaman `sendik`.** Neon crea por omisión `neondb_owner`
y `neondb`. Suponer otra cosa deja el arranque en

```
FlywaySqlUnableToConnectToDbException: Unable to obtain connection from database:
ERROR: password authentication failed for user 'sendik'
SQL State : 28P01
```

que se lee como una contraseña equivocada y es un rol que no existe. Costó un
diagnóstico entero el 1 de septiembre de 2026: la contraseña estaba bien desde el
principio.

**`channel_binding=require` no se copia.** Es un parámetro de libpq, no de JDBC:
el driver de PostgreSQL negocia SCRAM con enlace de canal por su cuenta sobre
SSL, y pasárselo en la URL lo rechaza como propiedad desconocida.

**El host `-pooler` sirve para la aplicación y no para migrar.** Neon desaconseja
su agrupador de conexiones para migraciones, y Flyway corre al arrancar cada
revisión. Hoy funciona; si algún día se queja de sentencias preparadas o de
transacciones, la salida es el endpoint directo, que es el mismo host sin
`-pooler`.

**El rol tiene que poder crear en el esquema `public`.** Flyway crea su tabla
`flyway_schema_history` antes de la primera migración, y desde PostgreSQL 15 un rol
que no es dueño del esquema no tiene ese permiso aunque la base se haya creado para
él. El arranque se cae con

```
ERROR: permission denied for schema public
```

dentro de `JdbcTableSchemaHistory.create`, así que ninguna migración llega a
ejecutarse y el error no señala a ningún archivo. Lo más simple es que el rol de la
aplicación sea el dueño; si se creó un rol aparte, se le concede desde el dueño de
la base, en el editor SQL del proveedor y conectado a esa base:

```sql
ALTER SCHEMA public OWNER TO EL-ROL;
GRANT ALL ON SCHEMA public TO EL-ROL;
```

Un rol de aplicación con menos permisos que estos tendrá sentido el día que las
migraciones dejen de correr al arrancar. Hoy corren, y el rol que se conecta es el
que migra.

**La salida más limpia es crear la base con su propio rol y no reutilizar el que
viene.** Es lo que se hizo en `dev` el 2 de septiembre de 2026: una base `sendik`
creada por un rol `sendik`. Entonces el dueño de `public` es `pg_database_owner`,
que resuelve al dueño de la base, y el `GRANT` de arriba no hace falta:

```
duenio_de_public = pg_database_owner
has_schema_privilege(current_user,'public','CREATE') = t
```

Comprobarlo antes de desplegar cuesta una consulta y ahorra el diagnóstico entero:
el error de permisos no aparece hasta que Flyway intenta crear su tabla, dentro de
un contenedor que Cloud Run reporta como "no escuchó en el puerto".

### Corregir un valor después

Cada corrección es una versión nueva del secreto y no un secreto nuevo. Como
Cloud Run monta `:latest`, el despliegue siguiente la toma sin tocar nada más:

```bash
printf '%s' 'EL-VALOR-REAL' | gcloud secrets versions add sendik-db-password --data-file=-
```

`printf` y no `echo`, que agrega un salto de línea: un salto de línea dentro de
una contraseña da otra vez `28P01` y parece exactamente el mismo problema.

**Una credencial que pasa por un chat, un correo o un ticket queda escrita ahí.**
Copiar la cadena completa a cualquiera de esos sitios para que otro la traduzca no
es reversible: se rota en la consola del proveedor y se agrega la versión nueva
con el comando de arriba. Lo correcto es ejecutar ese comando donde vive la
credencial y no moverla.

## 3. El almacén de archivos

Dos cubos con garantías distintas (ADR-0018): el **público** sirve la foto de perfil
y, en Fase 2, las tomas de producto; el **reservado** guarda la cédula y la selfie,
que no se sirven por ninguna dirección pública (RN-046).

> **El adaptador de Cloud Storage ya está escrito** (`GcsPublicFileStore`,
> `GcsRestrictedFileStore`), así que `STORAGE_PROVIDER=gcs` funciona. `local` sigue
> siendo el valor de desarrollo y sirve para recorrer la subida sin credenciales,
> pero **no vale en la nube**: el sistema de archivos de Cloud Run es efímero y se
> lleva las fotos con la instancia.
>
> Estos dos cubos se crean ya, aunque el sitio no se despliegue: son capa gratuita y
> son contra lo que se prueba en local (ver «Cuándo se ejecuta esta lista», arriba).
> Cómo apuntar la máquina de desarrollo a ellos está al final de este paso.

### Los dos cubos

```bash
PROYECTO=sendik-col
REGION=us-east1

# El público. Acceso uniforme a nivel de cubo, no ACL por objeto: con ACL, un solo
# objeto mal marcado queda expuesto o inaccesible y nadie lo nota.
gcloud storage buckets create gs://sendik-publico   --project=$PROYECTO --location=$REGION --uniform-bucket-level-access

# El reservado. Mismo comando y una diferencia que es todo el punto: este nunca
# recibe el permiso de lectura pública del paso siguiente.
gcloud storage buckets create gs://sendik-reservado   --project=$PROYECTO --location=$REGION --uniform-bucket-level-access
```

Misma región que Cloud Run. Un cubo en otra región se paga en latencia en cada
imagen del catálogo y en tráfico entre regiones.

### Lectura pública, solo en uno

```bash
gcloud storage buckets add-iam-policy-binding gs://sendik-publico   --member=allUsers --role=roles/storage.objectViewer
```

`allUsers` da miedo escrito así y es lo correcto **para este cubo**: son las imágenes
de un catálogo, tienen que verse sin credenciales. Lo que protege lo demás es que
este comando no se ejecuta nunca sobre `sendik-reservado`. Que sean dos cubos y no
dos carpetas del mismo es exactamente lo que permite eso.

Conviene comprobarlo después, porque es el error que no avisa:

```bash
# Debe decir allUsers.
gcloud storage buckets get-iam-policy gs://sendik-publico --format=json | grep allUsers

# Y aquí no debe decir nada. Si dice algo, la cédula de alguien es pública.
gcloud storage buckets get-iam-policy gs://sendik-reservado --format=json | grep allUsers
```

### Los permisos de la aplicación

La cuenta que ejecuta (`sendik-backend`, del paso siguiente) necesita distinto
permiso en cada cubo, y ahí está la diferencia que importa:

```bash
CUENTA=serviceAccount:sendik-backend@$PROYECTO.iam.gserviceaccount.com

# Público: crear y borrar objetos.
gcloud storage buckets add-iam-policy-binding gs://sendik-publico   --member=$CUENTA --role=roles/storage.objectAdmin

# Reservado: lo mismo, y nada más. No se le da `admin` sobre el cubo, así que no
# puede cambiar su política de acceso ni hacerlo público por error.
gcloud storage buckets add-iam-policy-binding gs://sendik-reservado   --member=$CUENTA --role=roles/storage.objectAdmin
```

### Borrado y versiones

**Sin versionado de objetos en ninguno de los dos.** Es lo contrario de lo habitual
y es deliberado: con versionado, borrar un objeto guarda una copia anterior, así que
el derecho de eliminación de la Ley 1581 dejaría la selfie de alguien en una versión
retenida. Cerrar la cuenta borra el archivo y tiene que borrarlo de verdad
(`datos-personales.md`).

Una regla de ciclo de vida sí conviene, para limpiar lo que quede huérfano cuando un
borrado falle:

```bash
cat > ciclo.json <<'JSON'
{
  "rule": [
    {
      "action": {"type": "Delete"},
      "condition": {"daysSinceNoncurrentTime": 1, "isLive": false}
    }
  ]
}
JSON
gcloud storage buckets update gs://sendik-publico --lifecycle-file=ciclo.json
```

### CORS: no hace falta

Las imágenes se cargan con `<img src>`, y eso no está sujeto a CORS. Configurarlo
«por si acaso» abre el cubo a lecturas desde JavaScript de cualquier origen sin que
nadie lo necesite. Si algún día el visor 360 lee píxeles con `canvas`, entonces sí,
y solo para el cubo público y solo para el dominio del sitio.

### Las variables

| Variable | Valor | Obligatoria |
|---|---|---|
| `STORAGE_PROVIDER` | `gcs` | sí, para usar Cloud Storage |
| `STORAGE_PUBLIC_BUCKET` | `sendik-publico` | sí, con `gcs` |
| `STORAGE_RESTRICTED_BUCKET` | `sendik-reservado` | sí, con `gcs` |
| `STORAGE_PUBLIC_BASE_URL` | `https://storage.googleapis.com/sendik-publico` | sí |
| `STORAGE_PROJECT_ID` | `sendik-col` | no |
| `STORAGE_LOCAL_PATH` | no se usa con `gcs` | no |

Los nombres de los cubos son variables y no constantes del código porque un nombre
de cubo es único en todo Google: si `sendik-publico` estuviera tomado, el cubo se
llama de otra forma y eso no puede exigir tocar el código.

**Los dos cubos no pueden ser el mismo.** La aplicación no arranca si lo son, y esa
comprobación existe porque es el error que no avisa: con un solo cubo todo funciona,
y la cédula de la primera persona que se verifique queda donde `allUsers` puede
leerla (RN-046).

`STORAGE_PROJECT_ID` es opcional. Sin él, la librería toma el proyecto de las
credenciales, que es lo correcto dentro de Cloud Run. Conviene ponerlo en una
máquina donde `gcloud` apunte a otro proyecto.

En producción conviene que `STORAGE_PUBLIC_BASE_URL` sea un dominio propio detrás
del CDN y no `storage.googleapis.com`: la dirección de cada imagen queda escrita en
el HTML que sirve el renderizado, y cambiar de proveedor después obliga a que todas
esas direcciones sigan resolviendo.

### Probar desde la máquina de desarrollo

No hace falta ninguna clave descargada, y es mejor que no la haya: un archivo de
clave JSON es justo el que acaba subido a un repositorio por accidente. La librería
usa las credenciales de aplicación por omisión, así que basta con:

```bash
gcloud auth application-default login
gcloud auth application-default set-quota-project sendik-col
```

Eso deja las credenciales de **tu** usuario, no de la cuenta de servicio, así que tu
usuario necesita poder escribir en los cubos. Si eres quien creó el proyecto, ya
eres `owner` y no hay que hacer nada más. Si no:

```bash
CUENTA=user:tu-correo@ejemplo.com

gcloud storage buckets add-iam-policy-binding gs://sendik-publico   --member=$CUENTA --role=roles/storage.objectAdmin
gcloud storage buckets add-iam-policy-binding gs://sendik-reservado   --member=$CUENTA --role=roles/storage.objectAdmin
```

Después, en el `.env` de la raíz del repositorio:

```
STORAGE_PROVIDER=gcs
STORAGE_PROJECT_ID=sendik-col
STORAGE_PUBLIC_BUCKET=sendik-publico
STORAGE_RESTRICTED_BUCKET=sendik-reservado
STORAGE_PUBLIC_BASE_URL=https://storage.googleapis.com/sendik-publico
```

Y se comprueba subiendo una foto de perfil en `/mi-cuenta`: la dirección de la
imagen tiene que ser la de `storage.googleapis.com` y el objeto tiene que aparecer
en el cubo.

```bash
gcloud storage ls gs://sendik-publico/avatares/
```

**Volver a `local` es cambiar una variable.** `STORAGE_PROVIDER=local` y ya: los
beans de Cloud Storage no se crean y no se toca la red. Las dos suites de pruebas
siguen corriendo con `local`, y eso es a propósito: la verificación no debe depender
de una cuenta de nube ni de que haya red.

## 4. Los secretos

Ocho secretos, con estos nombres exactos porque son los que nombra el flujo:

```bash
crear_secreto() {
  printf '%s' "$2" | gcloud secrets create "$1" --data-file=- --replication-policy=automatic
}

# Los tres de la base salen de la cadena del proveedor y no se inventan: el rol y
# el nombre de la base rara vez son 'sendik' (paso 2).
crear_secreto sendik-db-url      'jdbc:postgresql://HOST/BASE?sslmode=require'
crear_secreto sendik-db-username 'EL-ROL'
crear_secreto sendik-db-password 'LA-CONTRASEÑA'
crear_secreto sendik-jwt-issuer  'https://sendik.co'
crear_secreto sendik-mail-api-key 're_LA-CLAVE-DE-RESEND'

# La clave de firma de los tokens. Se genera, no se elige: 32 bytes de verdad.
crear_secreto sendik-jwt-secret "$(openssl rand -base64 48)"

# Las dos de cifrado en columna (RN-046, ADR-0020). También se generan, y tienen
# que ser DISTINTAS entre sí: la aplicación no arranca si son iguales.
crear_secreto sendik-crypto-data-key-v1 "$(openssl rand -base64 32)"
crear_secreto sendik-crypto-lookup-key "$(openssl rand -base64 32)"
```

`JWT_SECRET` pide mínimo 32 caracteres y lo valida al arrancar. Cambiarlo
invalida todos los tokens de acceso emitidos: la gente tiene que volver a entrar.
No es motivo para no rotarlo, sí para hacerlo a una hora tranquila.

**Las dos de cifrado no se rotan igual, y perderlas no es lo mismo.** Cambiar
`JWT_SECRET` obliga a volver a entrar; perder `CRYPTO_DATA_KEY_V1` deja ilegibles
para siempre el número de documento y el de cuenta bancaria ya guardados, porque
nada más los puede descifrar. Rotarla es agregar la versión siguiente al mapa y
mover `CRYPTO_CURRENT_KEY_VERSION`, dejando la vieja mientras exista una fila que
la use; el detalle está en `docs/operacion/configuracion.md`.

## 5. Las dos cuentas de servicio

Dos, y no una, porque hacen cosas distintas: una despliega y la otra ejecuta. Si
fueran la misma, la aplicación en marcha tendría permiso para desplegarse a sí
misma.

```bash
PROYECTO=sendik-col
NUMERO=$(gcloud projects describe $PROYECTO --format='value(projectNumber)')

# La que ejecuta la aplicación. Solo lee secretos.
gcloud iam service-accounts create sendik-backend \
  --display-name="Backend de Sendik en ejecución"

for secreto in sendik-db-url sendik-db-username sendik-db-password \
               sendik-jwt-secret sendik-jwt-issuer sendik-mail-api-key \
               sendik-crypto-data-key-v1 sendik-crypto-lookup-key; do
  gcloud secrets add-iam-policy-binding $secreto \
    --member="serviceAccount:sendik-backend@$PROYECTO.iam.gserviceaccount.com" \
    --role="roles/secretmanager.secretAccessor"
done

# La que despliega desde GitHub.
gcloud iam service-accounts create sendik-despliegue \
  --display-name="Despliegue desde GitHub Actions"

for papel in roles/run.admin roles/artifactregistry.writer roles/iam.serviceAccountUser; do
  gcloud projects add-iam-policy-binding $PROYECTO \
    --member="serviceAccount:sendik-despliegue@$PROYECTO.iam.gserviceaccount.com" \
    --role="$papel"
done
```

## 6. Federación de identidades

Es lo que permite que GitHub se autentique **sin ninguna clave JSON guardada en
el repositorio**. Una clave JSON en los secretos de GitHub es una credencial
permanente que no caduca y que cualquiera con acceso al repositorio puede copiar;
la federación entrega un token de minutos, atado a este repositorio.

```bash
gcloud iam workload-identity-pools create github \
  --location=global --display-name="GitHub"

gcloud iam workload-identity-pools providers create-oidc sendik \
  --location=global --workload-identity-pool=github \
  --display-name="Repositorio de Sendik" \
  --issuer-uri="https://token.actions.githubusercontent.com" \
  --attribute-mapping="google.subject=assertion.sub,attribute.repository=assertion.repository" \
  --attribute-condition="assertion.repository == 'TU-USUARIO/sendik'"

# Solo este repositorio puede usar la cuenta de despliegue. Sin esta condición,
# cualquier repositorio de GitHub podría pedir el token.
gcloud iam service-accounts add-iam-policy-binding \
  sendik-despliegue@$PROYECTO.iam.gserviceaccount.com \
  --role=roles/iam.workloadIdentityUser \
  --member="principalSet://iam.googleapis.com/projects/$NUMERO/locations/global/workloadIdentityPools/github/attribute.repository/TU-USUARIO/sendik"
```

Cambia `TU-USUARIO/sendik` por el repositorio real en los dos sitios. El
`attribute-condition` es la pieza que importa: es lo que impide que otro
repositorio pida el mismo token.

## 7. El hospedaje del sitio y el dominio

**Decidido el 26 de agosto de 2026 (ADR-0024): el frontend va a Cloud Run, junto
al backend, y GoDaddy es el registrador de `sendik.co` y el servidor de DNS.**

El plan de alojamiento compartido de GoDaddy **no ejecuta el sitio** y no hace
falta tocarlo: su cPanel no trae Node de forma nativa y este frontend no es un
sitio estático —`server.ts` resuelve el idioma y el tema antes de pintar, manda
las cuatro cabeceras de seguridad y decide la caché de `/fuentes/` y de
`/legal/`—. El motivo largo está en ADR-0024.

### 7.1 El servicio de Cloud Run del frontend

Es un segundo servicio en el mismo proyecto y la misma región que el backend. La
imagen la construye `frontend/Dockerfile`, en dos etapas: Node 22 para construir,
Node 22 sin las dependencias de desarrollo para ejecutar, sin privilegios y
leyendo `PORT`.

No hay nada que crear a mano: el primer despliegue crea el servicio. Lo que sí hay
que crear una vez es su **cuenta de servicio**, que es distinta de la del backend y
mucho más pobre —el frontend no toca la base de datos, ni los cubos, ni los
secretos—:

```bash
gcloud iam service-accounts create sendik-frontend \
  --display-name "Frontend de Sendik en Cloud Run"
```

**Sin un solo permiso, y no es un olvido.** El servidor de renderizado solo habla
HTTP con la API pública; darle acceso a algo de GCP sería ampliar la superficie sin
motivo. Si algún día necesita leer un secreto, se le concede ese secreto y nada
más.

Y hay que dejar que el desplegador publique en su nombre, igual que con el backend:

```bash
gcloud iam service-accounts add-iam-policy-binding \
  sendik-frontend@sendik-col.iam.gserviceaccount.com \
  --member "serviceAccount:sendik-despliegue@sendik-col.iam.gserviceaccount.com" \
  --role roles/iam.serviceAccountUser
```

### 7.2 Los dos nombres, mapeados a los dos servicios

Cloud Run mapea un dominio propio y **gestiona el certificado sin costo**. Se hace
una vez por nombre y por entorno:

```bash
# Primero hay que demostrar que el dominio es tuyo. Abre lo que diga el comando y
# sigue el proceso; deja un registro TXT que se agrega en GoDaddy (paso 7.3).
gcloud domains verify sendik.co

# El sitio de dev y su API.
gcloud beta run domain-mappings create \
  --service sendik-web-dev --domain dev.sendik.co --region us-east1

gcloud beta run domain-mappings create \
  --service sendik-backend-dev --domain api-dev.sendik.co --region us-east1
```

Cada comando responde con **los registros DNS que hay que crear**. Anótalos: son
los del paso siguiente y no se inventan, se copian de esa salida.

> **El servicio tiene que existir antes que su mapeo, y si no, el mapeo se queda
> muerto.** Un `domain-mappings create` contra un servicio que todavía no se ha
> desplegado se acepta sin protestar y queda en
>
> ```
> Route sendik-web-dev does not exist.   reason: RouteMissing
> ```
>
> Lo que engaña es que no se arregla solo cuando el servicio aparece: el reintento
> se espacia hasta 24 horas y el mapeo sigue en ese estado con el servicio ya en
> pie. Por fuera se ve un `404` servido por `ghs` y un `https://` que ni siquiera
> negocia TLS, porque sin ruta no se emite certificado.
>
> Se sale recreándolo, y entonces tarda minutos:
>
> ```bash
> gcloud beta run domain-mappings delete --domain dev.sendik.co --region us-east1
> gcloud beta run domain-mappings create \
>   --service sendik-web-dev --domain dev.sendik.co --region us-east1
> ```
>
> El DNS no hay que tocarlo: ya está puesto y verificado. Pasó con los dos nombres
> el 2 de septiembre de 2026.
>
> **El orden que evita esto** es dejar el paso 7.2 para después del primer
> despliegue, que es el que crea los dos servicios de Cloud Run. Si ya se hizo
> antes, basta con recrear los mapeos al terminar.

> **Si el mapeo de dominios no está disponible en la región**, la alternativa sin
> costo es Firebase Hosting delante de Cloud Run, que da el mismo dominio propio y
> el mismo certificado gestionado. No se documenta aquí porque hoy no hace falta;
> si algún día hace falta, es un cambio de este paso y de nada más: la aplicación
> no se entera.

### 7.3 El DNS, en GoDaddy

En **GoDaddy → Mis productos → `sendik.co` → DNS → Administrar zonas**, agrega lo
que devolvió el paso anterior. Para un subdominio, Cloud Run pide un `CNAME`:

| Tipo | Nombre | Valor | Para qué |
|---|---|---|---|
| `CNAME` | `dev` | `ghs.googlehosted.com.` | El sitio de dev |
| `CNAME` | `api-dev` | `ghs.googlehosted.com.` | La API de dev |
| `TXT` | `@` | el que dé `gcloud domains verify` | Demostrar que el dominio es tuyo |

**El dominio raíz `sendik.co` se queda como está por ahora.** En `dev` no se usa, y
tocarlo antes de tener `prod` solo consigue que el sitio que la gente encuentre sea
uno a medias. El día del lanzamiento, la raíz necesita registros `A` y `AAAA` —un
`CNAME` no se puede poner en la raíz de una zona— y esos también los da
`gcloud beta run domain-mappings describe`.

**Quita antes lo que GoDaddy pone por su cuenta.** Un plan de alojamiento agrega
registros que apuntan a sus propios servidores, y un `A` de GoDaddy conviviendo con
lo de Cloud Run manda una parte del tráfico a un sitio vacío. Revisa que en la zona
no quede ningún `A`, `AAAA` o `CNAME` apuntando a GoDaddy para los nombres de la
tabla.

### 7.4 Comprobarlo

El DNS tarda: de minutos a un par de horas. Mientras tanto, el servicio ya responde
por su dirección de `run.app`, que es la que usa la comprobación del flujo.

```bash
# Que el nombre resuelva a Google y no a GoDaddy.
nslookup dev.sendik.co

# Que responda por el nombre propio, con su certificado.
curl -I https://dev.sendik.co

# Y que las cabeceras sigan puestas al pasar por el nombre propio: es lo que
# ADR-0019 exige del hospedaje, y ahora se puede comprobar de verdad.
curl -sI https://dev.sendik.co | grep -iE 'x-frame|x-content|strict-transport|referrer'
```

Las cuatro cabeceras y las dos políticas de caché son de la aplicación, no del
hospedaje: viven en `frontend/src/server.ts` y las comprueba
`frontend/e2e/cabeceras.spec.ts`. Cloud Run no las toca, que era el sexto requisito
de ADR-0019.

**Un 200 no demuestra que la página se vea.** Detrás de un proxy, `@angular/ssr`
puede servir la versión de renderizado en cliente —raíz vacía, sin estado
transferido— con estado 200 y sin un error en ninguna parte. El sitio queda en
blanco y todas las señales dicen que está bien. La comprobación de verdad mira el
cuerpo:

```bash
# Con contenido dentro de la raíz, no solo <sendik-root></sendik-root>.
curl -s https://dev.sendik.co/ | grep -c '<sendik-root></sendik-root>'   # debe dar 0

# Y con el estado transferido, que es por donde llega APP_CONFIG.
curl -s https://dev.sendik.co/ | grep -c 'ng-state'                      # debe dar 1 o más
```

Una portada renderizada pesa unos 75KB; el cascarón sin renderizar, unos 6KB. Es la
diferencia más rápida de ver. El detalle de por qué ocurría está más abajo, en la
tabla de síntomas, y el flujo ya lo comprueba solo.

## 8. Los entornos de GitHub

En **Settings → Environments**, dos entornos: `dev` y `prod`.

En `prod`, marca **Required reviewers** y añádete. Ahí vive la aprobación manual
que pide `entornos.md`, y vive ahí a propósito y no en un `if` del flujo: un
archivo se cambia en un commit, un revisor obligatorio no.

Conviene también marcar en `prod` que solo puede desplegarse desde etiquetas.

### Secretos

Ninguno. La autenticación contra Google es por federación de identidades y no
lleva clave (paso 6), y el frontend se publica por la misma vía: al ir también a
Cloud Run no hace falta ningún token de un proveedor externo (ADR-0024). Es una
consecuencia agradable de la decisión y no su motivo.

### Variables

Van como *variables*, no como secretos, porque ninguna lo es: son direcciones y
datos públicos de la empresa. Ponerlas como secretos solo conseguiría que
aparezcan tachadas en los registros cuando haga falta leerlas.

> **`GCP_PROJECT_ID` va además como variable del repositorio, y no solo de los dos
> entornos.** Es la única que se repite, y sin esa copia **no se despliega nada**:
> los tres `if: vars.GCP_PROJECT_ID` de `despliegue.yml` se evalúan *antes* de que
> GitHub resuelva el `environment` del trabajo, así que ahí las variables de entorno
> todavía no existen y la condición lee la cadena vacía. El síntoma es engañoso y
> costó un despliegue entero: la ejecución termina **en verde**, con los dos trabajos
> de Cloud Run omitidos y el aviso «Falta configurar el proyecto de Google Cloud»,
> que es exactamente lo que se ve cuando de verdad no hay nada configurado.
>
> ```bash
> gh variable set GCP_PROJECT_ID --body sendik-col
> ```
>
> La del entorno se mantiene igual: dentro del trabajo, que sí declara
> `environment`, es la que gana. La del repositorio existe solo para que la
> condición pueda leerla.

> **Una variable cuyo valor lleve coma se escribe distinto en el flujo.**
> `gcloud run deploy --set-env-vars` usa la coma para separar pares `clave=valor`,
> así que un valor que la contenga se parte y `gcloud` rechaza el resto como un par
> mal formado:
>
> ```
> AVAILABLE_LOCALES=es,en                               -> Bad syntax for dict arg: [en]
> COMPANY_ADDRESS=Cra. 26C # 38b-31. Medellín, Colombia -> Bad syntax for dict arg: [ Colombia]
> ```
>
> La salida es el delimitador alternativo de `gcloud`: escribir el argumento como
> `--set-env-vars "^;^CLAVE=$VALOR"` cambia el separador a `;` **solo en ese
> argumento**. `despliegue.yml` ya lo lleva en las cinco que pueden traer coma
> —`CORS_ALLOWED_ORIGINS`, `NG_ALLOWED_HOSTS`, `AVAILABLE_LOCALES`, `COMPANY_NAME` y
> `COMPANY_ADDRESS`—, incluidas las que hoy no la traen pero la traerían en cuanto
> haya un segundo origen o un segundo nombre.
>
> **Al agregar una variable a estas tablas, la pregunta es si su valor podrá llevar
> coma algún día.** Si podrá, va con `^;^` desde el primer momento: el fallo no
> aparece hasta que alguien pone el segundo valor, y para entonces nadie relaciona
> las dos cosas. Lo que ya no puede contener es un punto y coma.

| Variable | `dev` | `prod` |
|---|---|---|
| `GCP_PROJECT_ID` | `sendik-col` | `sendik-col` (uno solo, ver paso 1) |
| `GCP_REGION` | `us-east1` | `us-east1` |
| `GCP_WORKLOAD_IDENTITY_PROVIDER` | ruta completa del proveedor del paso 6 | ídem |
| `GCP_DEPLOY_SERVICE_ACCOUNT` | `sendik-despliegue@…` | ídem |
| `CLOUD_RUN_SERVICE` | `sendik-backend-dev` | `sendik-backend` |
| `CLOUD_RUN_SERVICE_ACCOUNT` | `sendik-backend@…` | ídem |
| `CLOUD_RUN_MIN_INSTANCES` | `0` | `1` |
| `CLOUD_RUN_MAX_INSTANCES` | `2` | `10` |
| `CLOUD_RUN_MEMORY` | `512Mi` | `1Gi` |
| `APP_BASE_URL` | `https://dev.sendik.co` | `https://sendik.co` |
| `APP_API_BASE_URL` | `https://api-dev.sendik.co/api/v1` | `https://api.sendik.co/api/v1` |
| `CORS_ALLOWED_ORIGINS` | `https://dev.sendik.co` | `https://sendik.co` |
| `SUPPORT_EMAIL` | `soporte@sendik.co` | ídem |
| `MAIL_FROM` | `no-responder@sendik.co` | ídem |
| `MAIL_QUEUE_ENABLED`, `MAIL_QUEUE_NAME`, `MAIL_QUEUE_SERVICE_ACCOUNT` | `true`, `correo-transaccional`, `sendik-cola@…` | ídem: la cola y la cuenta son las mismas |
| `MAIL_QUEUE_HANDLER_URL` | `https://api-dev.sendik.co/internal/mail/deliveries` | `https://api.sendik.co/internal/mail/deliveries` |
| `COMPANY_NAME`, `COMPANY_TAX_ID`, `COMPANY_ADDRESS` | los reales | ídem |
| `COMMISSION_RATE` | `0.05` | `0.05` |
| `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION` | `2026-09-08b` desde el 8 de septiembre de 2026 | `borrador-local` todavía: publicar en `prod` es otra decisión |
| `LEGAL_COOKIES_VERSION` | `2026-09-06`, y su variable es independiente de las otras dos | ídem |
| `STORAGE_PROVIDER` | `gcs` | `gcs` |
| `STORAGE_PUBLIC_BUCKET` | `sendik-publico` | ídem |
| `STORAGE_RESTRICTED_BUCKET` | `sendik-reservado` | ídem |
| `STORAGE_PUBLIC_BASE_URL` | `https://storage.googleapis.com/sendik-publico` | el dominio del CDN |
| `FEATURE_SELLER_VERIFICATION`, `FEATURE_PUBLISHING`, `FEATURE_CATALOG` | `true` las tres desde el 5 de septiembre de 2026 | sin definir, que es apagadas |
| `FEATURE_SEARCH` | `true` desde el 10 de septiembre de 2026, al integrar HU-014 | sin definir, que es apagada |

**Las banderas no estaban en esta tabla y ahora sí.** Su efecto se explica en
`docs/operacion/configuracion.md`; lo que faltaba aquí era su valor por entorno, que
es lo que hay que poner al crear el entorno de GitHub. Una bandera sin definir se
expande a cadena vacía en el flujo y el respaldo `|| 'false'` la deja apagada, así
que dejarla fuera de `prod` es la forma de tenerla apagada allí y no un olvido.

Y las del frontend, que desde ADR-0024 también se despliega desde aquí. Van en el
mismo sitio y por la misma razón: ninguna es secreta —el sitio las dice en voz alta
o son direcciones públicas—, y el mismo artefacto sirve para los dos entornos
porque la configuración llega por entorno y no compilada dentro.

| Variable | `dev` | `prod` |
|---|---|---|
| `CLOUD_RUN_WEB_SERVICE` | `sendik-web-dev` | `sendik-web` |
| `CLOUD_RUN_WEB_SERVICE_ACCOUNT` | `sendik-frontend@…` | ídem |
| `CLOUD_RUN_WEB_MIN_INSTANCES` | `0` | `1` |
| `CLOUD_RUN_WEB_MAX_INSTANCES` | `2` | `10` |
| `CLOUD_RUN_WEB_MEMORY` | `512Mi` | `1Gi` |
| `NG_ALLOWED_HOSTS` | `dev.sendik.co` **y el host de `run.app`**, separados por coma | ídem con los de `prod` |
| `DEFAULT_LOCALE`, `AVAILABLE_LOCALES` | `es`, `es,en` | ídem |
| `CLAIM_WINDOW_DAYS` | `3` | `3` |
| `VERIFICATION_REVIEW_DAYS` | `2` | `2` |
| `LISTING_REVIEW_DAYS` | `2` | `2` |
| `LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION`, `LEGAL_COOKIES_VERSION` | `borrador-local` hasta que existan los textos | la versión real |

El frontend reusa `APP_API_BASE_URL` —que le llega como `API_BASE_URL`, el nombre
con el que lo lee `read-app-config.ts`—, `SUPPORT_EMAIL`, `COMPANY_*` y
`COMMISSION_RATE` de la tabla de arriba: son el mismo valor para las dos piezas, y
declararlos dos veces es la forma de que un día dejen de coincidir.

> **Los dos nombres de cubo son obligatorios con `gcs`, y esta tabla no los tenía.**
> El paso 3 ya los daba por sabidos, pero el paso 8 no los pedía y `despliegue.yml` no
> los pasaba, así que el backend se desplegaba y **no arrancaba**:
> `IllegalStateException: Falta STORAGE_PUBLIC_BUCKET, que es obligatoria con
> sendik.storage.provider=gcs`. Cuesta encontrarlo porque el fallo llega tarde —después
> de que Flyway aplique las quince migraciones sin una queja— y Cloud Run lo reporta como
> un contenedor que no escuchó en el puerto a tiempo, que suena a otra cosa.
>
> Ojo también al nombre: el cubo es `sendik-publico`, **sin sufijo de entorno**, así que
> `STORAGE_PUBLIC_BASE_URL` termina en `sendik-publico` y no en `sendik-publico-dev`,
> como decía esta tabla hasta hoy.

> **`NG_ALLOWED_HOSTS` tiene que incluir la dirección de `run.app`, y no solo el
> nombre propio.** La comprobación del flujo pide la portada por `status.url`, que es
> la de `run.app`, y `@angular/ssr` responde **400** a un `Host` que no esté en la
> lista. No es un render a medias: es un rechazo, y con solo `dev.sendik.co` el
> frontend se despliega bien y el trabajo se pone rojo en el último paso.
>
> Esa dirección la asigna Cloud Run y no se conoce antes del primer despliegue, así
> que este valor se completa cuando el servicio ya existe:
>
> ```bash
> gcloud run services describe sendik-web-dev \
>   --region us-east1 --format 'value(status.url)'
> ```
>
> El valor final es la lista con los dos nombres separados por coma —de ahí que esta
> variable vaya con `^;^` en el flujo—, y el del nombre propio se deja puesto aunque
> el DNS todavía no resuelva: no estorba y evita tener que volver aquí en el paso 7.

`STORAGE_PUBLIC_BASE_URL` **no** va al frontend: las direcciones de las imágenes
llegan ya formadas en la respuesta de la API y el navegador no compone ninguna.

**Las versiones de los legales tienen que valer lo mismo en las dos piezas.** El
backend guarda la versión con el consentimiento y el frontend sirve el texto: si no
son la misma, la prueba del consentimiento no vale
(`docs/operacion/datos-personales.md`). Las dos piezas leen el mismo nombre
—`LEGAL_TERMS_VERSION`, `LEGAL_PRIVACY_VERSION`, `LEGAL_COOKIES_VERSION`—, así que
se declara una vez por entorno y las dos lo reciben.

`min-instances = 0` en `dev` es lo que hace que no cueste nada: Cloud Run escala
a cero y sin tráfico no cobra. En `prod` se pone 1 para no pagar el arranque en
frío en la primera visita del día, como dice `entornos.md`.

`CLOUD_RUN_MIN_INSTANCES = 0` implica arranque en frío de varios segundos, y con
las dos piezas escaladas a cero son dos los que pueden arrancar. En `dev` es
aceptable, y es justo lo que lo mantiene sin costo.

## 9. Comprobarlo

El primer despliegue conviene hacerlo a `dev` y mirándolo:

```bash
git switch main && git pull
git commit --allow-empty -m "chore: primer despliegue a dev"
git push
```

El flujo hace la comprobación de estado por su cuenta y falla si la aplicación no
responde, así que un trabajo verde significa que responde de verdad y no solo que
`gcloud` no dio error.

En el frontend comprueba además **que la página llegó renderizada**: que
`<sendik-root>` no venga vacía y que el HTML traiga el estado transferido. Un 200
con el cuerpo vacío es un despliegue roto que antes pasaba en verde, y es lo que
dejó el sitio en blanco el 2 de septiembre de 2026. Se pide por la dirección de
`run.app`, que pasa por el proxy de Google y por tanto trae las cabeceras que
provocan el fallo: es el mismo camino que recorre un visitante.

Para producción:

```bash
git tag v0.1.0 && git push origin v0.1.0
```

Y entonces hay que entrar a aprobarlo en la pestaña de Actions.

### Cuando falla, el síntoma apunta a otro sitio

Levantar `dev` costó ocho fallos distintos entre el 1 y el 2 de septiembre de
2026, y ninguno decía lo que de verdad pasaba. Esta tabla es para buscar por
síntoma, que es lo único que hay cuando ocurre:

| Lo que se ve | Lo que es | Dónde |
|---|---|---|
| La ejecución termina **en verde** sin desplegar nada, con los dos trabajos omitidos y el aviso «Falta configurar el proyecto de Google Cloud» | `GCP_PROJECT_ID` no está como variable **del repositorio**, solo de los entornos | paso 8 |
| `Bad syntax for dict arg: [en]` al desplegar | Una variable cuyo valor lleva coma, sin el delimitador `^;^` | paso 8 |
| `password authentication failed for user '…'` (`28P01`) | El rol o la base no son los que da la cadena del proveedor; también lo da un salto de línea colado en la contraseña | paso 2 |
| `permission denied for schema public`, sin que corra ninguna migración | El rol no puede crear en `public`, así que Flyway no puede ni crear su propia tabla | paso 2 |
| «El contenedor no escuchó en el puerto a tiempo», después de que Flyway aplique las migraciones sin una queja | Falta una variable obligatoria del arranque. La primera vez fueron los dos cubos | paso 8 |
| El frontend se despliega bien y la comprobación devuelve **400** | Falta el host de `run.app` en `NG_ALLOWED_HOSTS` | paso 8 |
| El mapeo de dominio no sale de `RouteMissing` aunque el servicio ya exista, y el nombre propio da `404` de `ghs` sin llegar a negociar TLS | El mapeo se creó antes que el servicio y su reintento se espació a 24 horas. Se recrea | paso 7.2 |
| Todo en verde, el sitio responde **200** y la página se ve **en blanco** | `@angular/ssr` renunció al renderizado en servidor y sirvió la versión de cliente: raíz vacía, sin estado transferido, sin `APP_CONFIG` y sin arranque | ver abajo |

Seis de los ocho eran huecos de esta lista o del flujo —el proyecto, las comas,
los cubos, el host de `run.app`, el orden del mapeo y el proxy— y no errores de
quien la seguía; los otros dos venían de traducir a mano la cadena del proveedor.
Lo que comparten es que el trabajo que falla no es el que tiene la culpa, así que
**el primer sitio donde mirar no es el registro de Actions sino el del
contenedor**:

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.revision_name="LA-REVISION"' \
  --project sendik-col --limit 60 --format 'value(textPayload)'
```

El nombre de la revisión lo dice el propio error de `gcloud run deploy`, en el
enlace a los registros que imprime al fallar.

### La página en blanco que responde 200

Es el más difícil de los ocho, porque todas las señales dicen que está bien: el
despliegue en verde, el estado 200, el HTML servido con sus cabeceras y su idioma
resuelto. Y la página no se ve.

Lo que pasa es que `@angular/ssr` **renuncia al renderizado en servidor** en cuanto
recibe una cabecera `x-forwarded-*` que no esté declarada como confiable. No lanza
ni registra un error: borra la cabecera, deja un `console.warn` y sirve la versión
de renderizado en cliente. Está en `sanitizeRequestHeaders`:

```js
if (lowerKey.startsWith('x-forwarded-') && !isProxyHeaderAllowed(lowerKey, trustProxyHeaders)) {
  console.warn(`Received "${key}" header but "trustProxyHeaders" was not set up to allow it.`);
  deoptToCSR = true;
}
...
if (deoptToCSR) {
  return serverApp.serveClientSidePage();
}
```

Cloud Run pone siempre `x-forwarded-for` y `x-forwarded-proto`, así que esto ocurre
en Cloud Run y **no en local**, que es donde nadie lo ve venir.

Y el cascarón vacío no degrada a una página que se pinte en el navegador: se queda
en blanco. `APP_CONFIG` viaja en el estado transferido del renderizado en servidor
(ADR-0021), así que sin él la aplicación ni siquiera arranca. En la consola del
navegador queda `La configuracion no llego en el estado transferido`.

La corrección es declarar las dos cabeceras en `frontend/src/server.ts`:

```ts
const angularApp = new AngularNodeAppEngine({
  trustProxyHeaders: ['x-forwarded-for', 'x-forwarded-proto'],
});
```

**`x-forwarded-host` se queda fuera a propósito.** Es la que elegiría el nombre de
dominio con el que se renderiza la página, o sea la falsificación de peticiones del
lado del servidor contra la que existe `NG_ALLOWED_HOSTS` (ADR-0006).

Para saber qué cabecera falta, el registro lo dice sin adivinar: el aviso se emite
**una vez por cada cabecera no confiable**, así que declarar una y ver cuál queda
sola en el registro fija el conjunto exacto.

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="sendik-web-dev"' \
  --project sendik-col --limit 200 --format 'value(textPayload)' --freshness=30m \
  | grep -oE 'Received "x-forwarded-[a-z]+"' | sort | uniq -c
```

**Cuidado al leerlo: la ventana de tiempo mezcla revisiones.** Justo después de
desplegar la corrección, ese comando sigue devolviendo los avisos de la revisión
anterior, que todavía está dentro de `--freshness` y ya no sirve tráfico. Antes de
concluir nada hay que ver de qué revisión salen:

```bash
gcloud logging read \
  'resource.type="cloud_run_revision" AND resource.labels.service_name="sendik-web-dev"' \
  --project sendik-col --limit 300 \
  --format 'value(resource.labels.revision_name,textPayload)' --freshness=30m \
  | grep -iE 'Received "x-forwarded' | sed -E 's/.*(sendik-web-dev-[0-9]+).*/\1/' \
  | sort | uniq -c
```

La revisión que sirve hoy la dice `gcloud run services describe sendik-web-dev
--region us-east1 --format 'value(status.latestReadyRevisionName)'`. Con la
corrección puesta, esa revisión no aparece en la lista.

> **Declarar las cabeceras a medias sale igual de mal, y no se nota.** La primera
> corrección declaró solo `x-forwarded-for`, y la prueba que la acompañaba mandaba
> solo esa cabecera: quedó verde y el despliegue siguiente seguía sirviendo la
> página vacía por `x-forwarded-proto`. **Basta una cabecera sin declarar para
> perder el renderizado entero**, así que una prueba que mande menos de las que
> manda el proxy real no demuestra nada. `frontend/e2e/ssr.spec.ts` las tiene ahora
> en una constante única, `CABECERAS_DE_CLOUD_RUN`, y prueba el juego completo y
> cada una por separado.

## Volver atrás

Está en `entornos.md`: se redirige el tráfico a la revisión previa de Cloud Run y
es inmediato.

```bash
gcloud run services update-traffic sendik-backend \
  --region us-east1 --to-revisions REVISION-ANTERIOR=100
```

La base de datos no se revierte nunca: se corrige hacia adelante con una
migración nueva. Por eso toda migración destructiva va en dos pasos separados por
al menos un despliegue.

## Lo que este flujo todavía no hace

Se anota para no confundir lo que falta con lo que está:

- **Producción entera.** `dev` está en pie desde el 2 de septiembre de 2026; `prod`
  no. Falta el revisor obligatorio, la columna `prod` de las tablas de variables,
  Cloud SQL y los tres textos legales, que siguen en `borrador-local`.
- **El dominio raíz `sendik.co`.** No apunta a ningún sitio todavía, a propósito:
  en `dev` no se usa, y apuntarlo antes de tener `prod` haría que lo que la gente
  encuentre sea un sitio a medias. Necesita registros `A` y `AAAA`, no un `CNAME`
  (paso 7.3).
- **Registro y métricas.** Cloud Logging recoge la salida sin configurar nada,
  pero no hay ninguna alerta. Sentry para los errores del frontend está en
  `entornos.md` y aún no está conectado.
- **Respaldos.** Los de la capa gratuita de Neon o Supabase, sin verificar. La
  copia diaria con retención de 7 días que pide `entornos.md` hay que
  comprobarla, y una copia que nadie ha restaurado nunca no es una copia.
