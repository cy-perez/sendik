# Entornos y despliegue

Dos entornos: `dev` y `prod`. Sin entorno intermedio: con un solo desarrollador,
un tercer entorno es sobre todo mantenimiento sin beneficio.

## Etapa actual: `dev` en pie, `prod` todavía no

El 26 de agosto de 2026 se contrató **`sendik.co` en GoDaddy**, y con eso se cerró
la única decisión que quedaba abierta: dónde se hospeda el sitio. La respuesta está
en ADR-0024 y es **Cloud Run, junto al backend**; GoDaddy queda como registrador y
servidor de DNS, que es lo que mejor hace.

**Lo que cambia:** `dev` deja de ser una etapa aplazada y pasa a estar desplegado.
Las dos piezas van a Cloud Run desde el mismo commit verificado, con el mismo flujo
(`despliegue.yml`) y en la misma región.

**Lo que no cambia:** producción sigue esperando, y por el mismo motivo de siempre.
Un despliegue de producción pide atención continua —secretos que rotar, respaldos
que verificar, alertas que atender— y arrastra las piezas que sí cuestan: la
instancia mínima siempre activa y Cloud SQL, que cobra por hora encendida aunque
nadie lo use. Nada de eso hace falta para tener `dev` en pie.

**Y `dev` sigue costando cero.** No es una concesión: es consecuencia de que todo
lo que lo compone escala a cero o entra en capa gratuita.

### El correo transaccional: dos fallos, uno resuelto y otro abierto

**Anotado el 5 de septiembre de 2026**, al encender las tres banderas de la Fase 2 en
`dev` y registrarse por primera vez contra ese entorno.

**Resuelto: el dominio no estaba verificado en Resend.** `dev` y `prod` mandan con
`MAIL_FROM=no-responder@sendik.co`, y al dominio —contratado el 26 de agosto— nunca se
le añadieron los registros que Resend exige. Cada envío moría con un 403 del proveedor.
Se añadieron ese mismo día —MX y SPF en `send.sendik.co`, DKIM en
`resend._domainkey.sendik.co`— y con «Domain verification» y «Enable Sending» en
*Verified*, el correo sale. «Enable Receiving» queda en *Pending* y así se deja: es el
MX de entrada del dominio raíz y Sendik no recibe correo.

**Nadie lo había visto porque nadie se había registrado nunca contra `dev`.** El perfil
`local` usa `MAIL_PROVIDER=console` y las suites leen el enlace del registro de la
aplicación, así que las dos mitades de HU-001 estaban probadas sin que saliera un solo
correo de verdad ni una sola vez.

**Cloud Run congela el envío. Resuelto y comprobado en `dev` el 8 de septiembre de 2026;
en `prod` la configuración quedó lista ese mismo día y la entrega sigue sin comprobar.**

`AsyncMailSender` difiere el envío a un ejecutor de dos hilos, de modo que el correo
sale **después** de que la petición haya respondido. El servicio corre con
`run.googleapis.com/cpu-throttling` sin fijar —o sea, activa— y con
`CLOUD_RUN_MIN_INSTANCES` en `0`, así que Cloud Run **solo asigna CPU mientras procesa
una petición**. Devuelto el 202, el contenedor se congela, y la llamada saliente a
Resend que estaba a medias expira:

```
ERROR c.s.identity.client.ResendMailSender : No se pudo enviar un correo transaccional: ...ResourceAccessException
```

**Está demostrado, no deducido.** El mismo envío aislado que a las 16:01 murió por red
salió a las 16:03 sin más cambio que mantener el contenedor atendiendo peticiones a
`/actuator/health` durante quince segundos. El patrón era nítido: las ráfagas de tres
peticiones llegaban a Resend, las peticiones sueltas morían.

**Y la petición suelta es el caso normal**: alguien que se registra solo. Los reintentos
de `ResendMailSender` no lo salvan, porque los tres ocurren en el mismo hilo congelado.

**Decidido el 5 de septiembre de 2026 en ADR-0031: Cloud Tasks.** El caso de uso
encola, Cloud Tasks reintenta con espera exponencial, y el envío al proveedor ocurre
dentro de una petición HTTP al propio backend, que es la única condición bajo la cual
Cloud Run asigna CPU. **La decisión está tomada y el código está escrito.** Lo que falta es la
infraestructura, y hasta que exista el fallo sigue abierto:

```
gcloud tasks queues create correo-transaccional --location=us-east1

gcloud iam service-accounts create sendik-cola \
  --display-name="Firma las tareas de correo"

# La cuenta del backend puede encolar.
gcloud tasks queues add-iam-policy-binding correo-transaccional \
  --location=us-east1 \
  --member=serviceAccount:sendik-backend@sendik-col.iam.gserviceaccount.com \
  --role=roles/cloudtasks.enqueuer

# Y puede pedir que la tarea se firme como la cuenta de la cola.
gcloud iam service-accounts add-iam-policy-binding \
  sendik-cola@sendik-col.iam.gserviceaccount.com \
  --member=serviceAccount:sendik-backend@sendik-col.iam.gserviceaccount.com \
  --role=roles/iam.serviceAccountUser
```

**Creada el 6 de septiembre de 2026**: la API de Cloud Tasks estaba sin habilitar en
`sendik-col`, y con ella se crearon la cola, la cuenta `sendik-cola` y las dos
asignaciones de arriba.

Y las variables del entorno `dev`: `MAIL_QUEUE_ENABLED=true`,
`MAIL_QUEUE_NAME=correo-transaccional`,
`MAIL_QUEUE_HANDLER_URL=https://api-dev.sendik.co/internal/mail/deliveries` y
`MAIL_QUEUE_SERVICE_ACCOUNT=sendik-cola@sendik-col.iam.gserviceaccount.com`. La
región la hereda de `GCP_REGION`.

**Y el proyecto lo hereda de `GCP_PROJECT_ID`, que hasta el 6 de septiembre de 2026 no
llegaba al contenedor**: el flujo la usaba para armar la ruta de la imagen y para decidir
si había dónde publicar, pero nunca la pasaba como variable del servicio. Sin ella
`QueueName.of` no puede nombrar la cola, así que encender la bandera habría tumbado el
arranque igual que una cola inexistente.

**Encenderla sin la cola creada no arranca el servicio**, y es a propósito:
`MailQueueProperties` exige lo que falta al construirse. Descubrir un fallo de
configuración al mandar el primer correo es exactamente como se perdió el primero.

### Comprobado con un correo de verdad el 8 de septiembre de 2026

Que el servicio arrancara con la bandera encendida solo probaba que las cinco variables
estaban; no probaba que saliera un correo. **Y entre el 6 y el 8 no salió ninguno**: la
cola registró cero tareas y el endpoint no recibió una sola petición de Cloud Tasks —las
únicas cuatro fueron `GET` de rastreadores de internet, rechazados con 401—. Nadie se
había registrado contra `dev` desde que se encendió, que es la misma razón por la que el
403 de Resend tardó dos semanas en verse.

Se disparó un `POST /api/v1/auth/forgot-password` contra una cuenta existente: una
petición aislada, que responde 202 y deja el correo pendiente. Es el caso que la ADR llama
normal y el que antes se perdía siempre.

| Hora (UTC) | Eslabón | Resultado |
|---|---|---|
| 13:39:10 | `forgot-password` | 202 en 1,95 s |
| 13:39:12 | Cloud Tasks entrega la tarea | `POST /internal/mail/deliveries`, agente `Google-Cloud-Tasks`, 204 |
| 13:39:13 | Resend entrega | correo recibido, de `no-responder@sendik.co` |

Tres segundos, sin reintentos y sin una advertencia en el registro. **El 204 no bastaba
como prueba** —el controlador lo devuelve tanto si el correo salió como si el proveedor lo
rechazó en firme—: lo que lo desambigua es el correo recibido. Y que el token OIDC valida
se ve en que respondió 204 y no el 401 de los rastreadores.

### `prod`: las cuatro variables creadas, la entrega sin comprobar

**Creadas el 8 de septiembre de 2026.** El entorno `prod` de GitHub ya tiene las cuatro, y
lo único que las separa de las de `dev` es la dirección del manejador:

```
MAIL_QUEUE_ENABLED=true
MAIL_QUEUE_NAME=correo-transaccional
MAIL_QUEUE_HANDLER_URL=https://api.sendik.co/internal/mail/deliveries
MAIL_QUEUE_SERVICE_ACCOUNT=sendik-cola@sendik-col.iam.gserviceaccount.com
```

La región la hereda de `GCP_REGION` y el proyecto de `GCP_PROJECT_ID`, que en `prod` ya
valen `us-east1` y `sendik-col`. **En GCP no hubo nada que crear:** la cola y la cuenta
`sendik-cola` sirven para los dos entornos —se comprobó, la cola responde `RUNNING`— y lo
que separa a uno de otro es la dirección que Cloud Tasks llama.

Hasta aquí, un despliegue a `prod` no fallaba al arrancar: el flujo respalda la bandera con
`|| 'false'`, así que se habría ido con la cola apagada y el correo entregado en el hilo de
la petición. Eso ya no reproducía el fallo original —lo causaba el `AsyncMailSender` que se
retiró— pero no era lo que decidió ADR-0031 y dejaba la petición de registro esperando al
proveedor.

**Lo que sigue sin comprobar, y hay que decirlo así.** Que las cuatro variables existan es
exactamente lo que se sabía de `dev` el 6 de septiembre, y aquellos dos días la cola
registró cero tareas. `api.sendik.co` todavía no responde: `prod` no se ha desplegado nunca
y su mapeo de dominio no existe. La entrega en `prod` queda por comprobar el día del primer
despliegue, y se comprueba igual que en `dev`: un `POST /api/v1/auth/forgot-password` contra
una cuenta existente y los tres eslabones seguidos —el 202, la entrega con agente
`Google-Cloud-Tasks` y su 204, y el correo recibido—. **El 204 por sí solo no basta.**

Lo que se descartó, y por qué, está entero en la ADR. En resumen:

| Opción | Por qué no |
|---|---|
| `--no-cpu-throttling` | Se paga CPU mientras el contenedor viva, no solo mientras atiende. Rompe el «`dev` cuesta cero» de más abajo. **Subir `CLOUD_RUN_MIN_INSTANCES` no es un sustituto**: con la limitación activa, una instancia ociosa tampoco tiene CPU |
| Enviar de forma síncrona | Reabre el oráculo de tiempos del criterio 11, que es justo lo que `AsyncMailSender` vino a cerrar. Se cambiaría un fallo de entrega por una fuga de información |
| Tabla de salida drenada por Cloud Scheduler | Más correcta —el encolado sería atómico con la transacción—, pero cuesta tabla, migración, concurrencia entre las dos instancias y hasta un minuto de espera en el correo de verificación. Queda como la opción a la que se vuelve si aparece un envío dentro de una transacción que aún puede deshacerse |

**Un fallo transitorio ya no pierde el correo**, que es otra cosa. `enviar()` registraba
el fallo y seguía, sin reintentar, y quien esperaba el enlace no tenía salida porque el
reenvío exige el token caducado que viajaba en ese correo. Desde el 5 de septiembre se
reintenta tres veces lo transitorio —cortes de red y 5xx— y **nunca un 4xx**, que es
configuración. Lo que sigue sin haber es un buzón de reintentos: si los tres fallan, el
correo se pierde y el registro lo dice.

| Pieza | Dónde | Costo en `dev` |
|---|---|---|
| Dominio `sendik.co` | GoDaddy, registrador y DNS | Ya pagado |
| Frontend Angular SSR | Cloud Run, escalado a cero (ADR-0024) | 0 |
| Backend Spring Boot | Cloud Run, escalado a cero | 0 |
| Certificado de `dev.sendik.co` | Gestionado por Cloud Run | 0 |
| PostgreSQL | Neon o Supabase, capa gratuita | 0 |
| Imágenes | Cloud Storage, capa gratuita 5GB | 0 |
| Secretos | Secret Manager | 0 en capa gratuita |
| Correo transaccional | Resend o Brevo, capa gratuita | 0 |
| Registro y métricas | Cloud Logging, capa gratuita | 0 |
| Errores del frontend | Sentry, capa gratuita | 0 |
| Repositorio y CI | GitHub Actions, minutos gratuitos | 0 |

**El alojamiento compartido de GoDaddy no aparece en la tabla porque no ejecuta
nada.** Está pagado y sin usar, y esa es una consecuencia asumida en ADR-0024: no
puede servir un sitio con renderizado en servidor, y montarlo encima costaría más
—en configuración frágil y en tiempo de operación— que el propio plan.

**Primer arranque en frío.** Con escalado a cero, la primera petición tras un
periodo inactivo tarda varios segundos, y ahora son dos servicios los que arrancan.
Para `dev` es aceptable y es justo lo que lo mantiene en cero. Al lanzar se
configura una instancia mínima siempre activa en las dos piezas.

## Etapa de lanzamiento

| Pieza | Servicio | Costo mensual estimado |
|---|---|---|
| Frontend y backend | Cloud Run, mínimo 1 instancia cada uno (ADR-0024) | 15 a 40 USD |
| Base de datos | Cloud SQL PostgreSQL 17, la más pequeña | 25 a 50 USD |
| Almacenamiento e imágenes | Cloud Storage y CDN | 5 a 15 USD |
| Balanceador y certificado | Load Balancer con certificado administrado. **Solo si hace falta**: el mapeo de dominios de Cloud Run ya da certificado gestionado sin costo, y en `dev` es lo que se usa | 0 a 25 USD |
| Búsqueda | Typesense administrado o en instancia propia | 0 a 25 USD |
| Correo | Plan de pago según volumen | 0 a 20 USD |
| Dominio | `sendik.co` en GoDaddy, ya contratado | Anual |

Rango realista de arranque: 60 a 150 USD al mes. Cifras orientativas de agosto de
2026; hay que confirmarlas con la calculadora de precios antes de comprometer
presupuesto.

**Región:** `us-east1`. Frente a `southamerica-east1`, la latencia hacia Colombia
es similar o mejor y los precios son bastante más bajos. Si más adelante aparece
un requisito de residencia de datos, se revisa.

## Cómo se despliega

Todo por integración continua. Nadie despliega desde su máquina.

> **Estado a septiembre de 2026.** El flujo está escrito completo y cubre las dos
> piezas: `.github/workflows/verificacion.yml` compila, prueba y analiza en cada
> pull request y en cada integración a `main`, y `despliegue.yml` publica **el
> backend y el frontend** en `dev` con cada integración a `main`, y en `prod` con
> etiqueta de versión y aprobación manual. El despliegue **llama** a la
> verificación en lugar de repetir sus pasos, así que nada se publica sin pasarla
> entera.
>
> Los dos trabajos se omiten mientras no exista `GCP_PROJECT_ID`, para que la
> canalización diga la verdad —no hay dónde desplegar— en vez de quedarse roja y
> dejar de leerse.
>
> **`dev` está en pie desde el 2 de septiembre de 2026**, y con eso deja de ser
> teoría: `https://dev.sendik.co` y `https://api-dev.sendik.co` responden con
> certificado gestionado y siguen costando cero. Las cuentas que hacían falta —el
> proyecto de Google Cloud, la base gestionada, los secretos, la federación de
> identidades y el DNS— están creadas, y lo que costó cada una quedó anotado en
> `despliegue.md`.
>
> Lo que falta ahora es **`prod`**, que es otra vuelta a esa misma lista.

```
rama de trabajo -> pull request -> verificación -> main -> dev automático
                                                       -> prod con aprobación manual
```

- `dev` se despliega en cada integración a `main`.
- `prod` requiere una etiqueta de versión y una aprobación explícita.
- Las migraciones de base de datos corren antes de arrancar la nueva versión y
  deben ser compatibles hacia atrás: una versión anterior de la aplicación tiene
  que poder seguir funcionando durante el despliegue.
- Toda migración destructiva se hace en dos pasos separados por al menos un
  despliegue: primero se deja de usar la columna, después se elimina.

## Retorno a una versión anterior

- Aplicación: se redirige el tráfico a la revisión previa de Cloud Run. Es
  inmediato.
- Base de datos: no se revierte una migración. Se corrige hacia adelante con una
  migración nueva. Por eso las migraciones destructivas van en dos pasos.

## Respaldos

- Copia diaria automática con retención de 7 días en `dev` y de 30 días en `prod`.
- Una restauración de prueba antes del lanzamiento, y luego cada trimestre. Un
  respaldo que nunca se ha restaurado no es un respaldo.

## Vigilancia

Alertas que deben existir antes de abrir al público:

- Tasa de errores 5xx por encima del 1% durante 5 minutos.
- Latencia del percentil 95 por encima de 2 segundos.
- Fallo de la verificación de estado de salud.
- Errores en el procesamiento de eventos de la pasarela.
- Uso de base de datos por encima del 80%.
- Gasto acumulado del mes por encima del presupuesto definido.

La última no es menor: un error de configuración en la nube puede costar dinero
real en horas.
