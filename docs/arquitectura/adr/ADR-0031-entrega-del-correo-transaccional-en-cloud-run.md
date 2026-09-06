# ADR-0031 — La entrega del correo transaccional se saca del contenedor: Cloud Tasks

**Fecha:** 2026-09-05
**Estado:** aceptada

## Contexto

El 5 de septiembre de 2026, al encender las tres banderas de la Fase 2 en `dev` y
registrarse por primera vez contra ese entorno, el correo transaccional no salió.
Debajo del primer fallo —el dominio sin verificar en Resend, que se arregló ese mismo
día añadiendo los registros DNS— apareció un segundo que no es de configuración sino
de diseño, y que **bloquea el lanzamiento**: `prod` tiene exactamente la misma
configuración de Cloud Run.

`AsyncMailSender` difiere el envío a un ejecutor de dos hilos, de modo que el correo
sale **después** de que la petición haya respondido. El servicio corre con
`run.googleapis.com/cpu-throttling` sin fijar —o sea, activa— y con
`CLOUD_RUN_MIN_INSTANCES` en `0`, así que Cloud Run **solo asigna CPU mientras se
procesa una petición**. Devuelto el 202, el contenedor se congela y la llamada
saliente a Resend que estaba a medias expira:

```
ERROR c.s.identity.client.ResendMailSender : No se pudo enviar un correo transaccional: ...ResourceAccessException
```

**Está demostrado, no deducido.** El mismo envío aislado que a las 16:01 murió por red
salió a las 16:03 sin más cambio que mantener el contenedor atendiendo peticiones a
`/actuator/health` durante quince segundos. El patrón era nítido: las ráfagas de tres
peticiones llegaban a Resend, las peticiones sueltas morían. **Y la petición suelta es
el caso normal**: alguien que se registra solo.

Tres restricciones acotan la decisión:

1. **Los reintentos que se añadieron ese día no salvan nada.** Los tres ocurren en el
   mismo hilo congelado. Y lo que sigue sin haber es un buzón: si los tres fallan, el
   correo se pierde y el registro lo dice.
2. **`AsyncMailSender` existe por seguridad, no por latencia.** Su javadoc lo dice y el
   criterio 11 lo exige: un correo que no existe y una contraseña equivocada deben
   tardar lo mismo. Con el envío en el hilo de la petición, el quinto intento fallido
   contra una cuenta que sí existe manda el aviso de bloqueo y espera al proveedor,
   cientos de milisegundos más que contra un correo sin cuenta. Cinco peticiones
   bastaban para enumerar quién tiene cuenta en Sendik.
3. **`dev` cuesta cero, y eso no es una concesión sino una consecuencia**
   (`docs/operacion/entornos.md`): todo lo que lo compone escala a cero o entra en capa
   gratuita.

Un dato que acota el alcance: `AsyncMailSender` es **el único** trabajo en segundo
plano de todo el backend. No hay ningún `@Scheduled`, ningún otro ejecutor y ningún
`CompletableFuture` fuera de las pruebas. La congelación afecta a una sola cosa y no
hay más víctimas latentes esperando a que alguien las descubra en `prod`.

## Opciones

**A. `--no-cpu-throttling` en el despliegue.** Una bandera en `despliegue.yml` y
`AsyncMailSender` se queda tal cual: cero código nuevo y arreglado hoy. El costo es que
se paga CPU mientras el contenedor viva y no solo mientras atiende, lo que rompe el
«`dev` cuesta cero». Conviene dejar anotado el error de razonamiento que estuvo cerca de
colarse: **subir `CLOUD_RUN_MIN_INSTANCES`, hoy `0`, no es un sustituto**, porque con la
limitación de CPU activa una instancia ociosa tampoco tiene CPU. Son dos ajustes
distintos y solo uno de los dos resuelve esto.

**B. Enviar de forma síncrona.** Desaparece `AsyncMailSender` y con él el problema
entero, porque el envío pasa a ocurrir mientras hay CPU garantizada. **Descartada, y no
por la latencia:** reabre el oráculo de tiempos del criterio 11 descrito arriba. Es
cambiar un fallo de entrega por una fuga de información, y la fuga es peor porque no
deja registro. Que además la respuesta de un ingreso pase a durar lo que dure el
proveedor es un motivo secundario.

**C. Cloud Tasks.** El caso de uso encola una tarea; Cloud Tasks llama de vuelta a un
endpoint del propio backend y el envío ocurre **dentro de una petición**, que es
exactamente la condición bajo la cual Cloud Run asigna CPU. Los reintentos con espera
exponencial los hace Cloud Tasks y no un hilo congelado. Capa gratuita de un millón de
operaciones al mes. Costo: una dependencia nueva, un endpoint protegido y configuración
de IAM.

**D. Tabla de salida en PostgreSQL drenada por Cloud Scheduler.** El correo se inserta
en una tabla dentro de la misma transacción del caso de uso y un disparo periódico
golpea un endpoint que la drena. Es la opción más rigurosa de las cuatro: el encolado es
atómico con la escritura de negocio y prácticamente gratis en tiempo, así que respeta el
criterio 11 mejor que ninguna, y cierra el correo perdido por construcción. Se descarta
por el conjunto de su costo: tabla, migración, control de concurrencia —`max-instances`
es 2, así que hace falta `SELECT ... FOR UPDATE SKIP LOCKED` para que dos instancias no
manden el mismo correo dos veces— y hasta unos sesenta segundos de latencia, que en el
correo de verificación de alguien que acaba de registrarse se nota. El intervalo mínimo
de Cloud Scheduler es de un minuto y no baja.

## Decisión

El correo transaccional se entrega con **Cloud Tasks**: el caso de uso encola, Cloud
Tasks reintenta, y el envío al proveedor ocurre dentro de una petición HTTP al propio
backend, donde sí hay CPU.

## Motivo

**Porque ataca la causa y no el síntoma.** El problema no es que falte CPU: es que el
trabajo estaba fuera del único momento en el que Cloud Run promete dársela. La opción A
compra CPU permanente para poder seguir trabajando fuera de la petición; la C mueve el
trabajo a donde la CPU ya está garantizada. La segunda sigue siendo correcta el día que
alguien cambie la configuración del servicio sin leer esta ADR, y la primera no.

**Porque cierra el buzón de reintentos que hoy falta**, que era una deuda aparte y
anotada. Los tres reintentos actuales viven en el hilo que se congela, así que no
protegen del caso que importa; los de Cloud Tasks son de un servicio gestionado que
sobrevive al contenedor, con espera exponencial y durante horas si hace falta. Se
resuelven dos cosas con un solo mecanismo, y el `INTENTOS = 3` de `ResendMailSender`
deja de ser la última línea de defensa para pasar a ser un atajo ante un hipo de un
segundo.

**Porque `dev` sigue costando cero.** Un millón de operaciones al mes en capa gratuita
frente a un volumen que hoy es de decenas de correos. Es la única de las dos opciones
vivas que no obliga a renegociar la línea de `entornos.md`, y renegociarla por un correo
habría sido caro en lo que de verdad cuesta: la instancia siempre encendida es después
el argumento para encender otras cosas.

**Y no se elige D pese a ser más correcta**, que es la parte de esta ADR que conviene
leer dentro de un año. La atomicidad de la tabla de salida es una garantía real que
Cloud Tasks no da: si la transacción se deshace después de crear la tarea, sale un
correo por algo que no ocurrió. Se acepta porque en los envíos que existen hoy la
escritura de negocio ya está confirmada cuando se encola, y porque el precio de D
—concurrencia entre dos instancias y un minuto de espera en el correo de verificación—
es alto para cerrar un caso que hoy no se da. **Si aparece un envío que deba encolarse
dentro de una transacción que aún puede fallar, D vuelve a estar sobre la mesa**, y
entonces se combina: la tabla de salida delante y Cloud Tasks detrás.

## Consecuencias

**Se gana** que el envío deje de depender de que el contenedor siga vivo, que los
reintentos sobrevivan al apagado, y que `prod` no herede el fallo. Se gana también algo
que no se buscaba: la composición del texto se separa del envío. Como la tarea viaja con
el mensaje ya armado —destinatario, asunto y cuerpo—, que es justo la forma del puerto
`MailTransport` de ADR-0023, el encolado se enchufa en ese puerto sin tocar a ninguno de
los dos lados.

**Se acepta perder** cuatro cosas, y la primera es de seguridad:

- **El token en claro viaja en el cuerpo de la tarea.** El enlace de verificación y el
  de restablecimiento van dentro del HTML ya compuesto, así que quedan en el
  almacenamiento de Cloud Tasks hasta que la tarea se completa y se borra. En la base de
  datos ese token está resumido y aquí no puede estarlo, porque es el que la persona
  tiene que recibir. Se acota con lo que hay: la cola es del proyecto y no es pública, la
  tarea se borra al entregarse, y el registro no imprime ni el destinatario ni el enlace
  —eso ya lo cumple `AsyncMailSender` y la clase que lo sustituya lo hereda—. **Queda
  anotado como el punto que hay que revisar si alguna vez se comparte el proyecto de
  Google Cloud.**
- **Un endpoint nuevo que no es para personas.** Recibe la llamada de Cloud Tasks y debe
  verificar el token OIDC de la cuenta de servicio que lo firma: el servicio corre con
  `--allow-unauthenticated` porque el catálogo es público, así que la protección tiene
  que estar en el endpoint y no en Cloud Run. Si eso se hace mal, cualquiera manda correo
  con la papelería de Sendik.
- **Entrega al menos una vez, no exactamente una.** Cloud Tasks puede repetir una tarea
  cuya entrega no alcanzó a confirmarse, así que alguien puede recibir dos veces el mismo
  aviso. Para un correo es tolerable; se anota para que nadie lo descubra como un fallo.
- **`AsyncMailSender` se retira.** La razón por la que existía —sacar el proveedor del
  hilo de la petición para no filtrar por tiempos— la cumple igual el encolado, y
  mantener los dos mecanismos sería dejar dos formas de mandar un correo.

**No cambia** el proveedor: ADR-0012 sigue vigente y Resend sigue siendo quien entrega.
**No cambia** el grafo de módulos de Gradle ni la dirección de las dependencias: el
encolado es un adaptador de `infrastructure` detrás del puerto `MailTransport` que ya
está en `application`, y el endpoint es un controlador de `presentation`. **No sustituye
a ADR-0023**, cuya decisión —el transporte de correo vive en `shared`— es justo la que
hace barato este cambio; lo que cambia es cuál es el adaptador, no cuál es el puerto.

## Cuándo revisar

Tres señales, cualquiera de las tres:

- **Aparece un correo que debe encolarse dentro de una transacción que todavía puede
  deshacerse.** Ahí la falta de atomicidad deja de ser teórica y entra la opción D
  delante de esta.
- **El proyecto de Google Cloud deja de ser de una sola persona.** El token en claro en
  el cuerpo de la tarea es aceptable mientras nadie más pueda leer la cola.
- **El correo deja de ser el único canal.** Si entran notificaciones push o mensajes
  dentro de la aplicación, la decisión ya no es de transporte sino de enrutamiento, y es
  la misma señal de revisión que anota ADR-0023.
