# ADR-0036 — La dirección del cliente se cuenta desde el final de `X-Forwarded-For`

**Fecha:** 2026-09-10
**Estado:** aceptada

## Contexto

El límite de peticiones de `/api/v1/auth` cuenta por origen, y la constancia de
consentimiento que exige la Ley 1581 guarda el hash de la dirección de quien
acepta. Las dos cosas dependen de una sola pregunta: **cuál de las direcciones que
llegan en la petición es la de quien llama.**

Hasta hoy la respuesta era «la primera de `X-Forwarded-For`». No se sostiene, y
está comprobado contra `dev`, no deducido:

- Doce peticiones a `/api/v1/auth/verify-email`, cada una con un
  `X-Forwarded-For` distinto: **ninguna 429**. Las mismas doce sin tocar la
  cabecera: 429 en la undécima, que es lo que manda `RATE_LIMIT_CREDENTIALS_MAX`.
  Cloud Run **no reemplaza la cabecera**: conserva delante lo que mande el cliente.
- Trece peticiones con la misma cabecera `X-Forwarded-For: , 8.8.8.8`: **ninguna
  429 tampoco**. Con la primera entrada vacía el hash salía nulo y el interceptor
  lo leía como «no hay a quien contar», así que una sola cabecera fija no abría un
  contador nuevo: **quitaba el límite entero**. Por el mismo camino,
  `AuthController` guardaba `null` como evidencia de consentimiento.
- El registro de Cloud Run anota `remoteIp` con la dirección real pese a la
  cabecera falsa, y seis peticiones con `X-Forwarded-For` igual a esa dirección
  comparten contador con seis sin cabecera —429 exactamente en la undécima—. La
  entrada que añade la plataforma es la primera de las suyas.

Dos defensas quedaban vacías: `CREDENTIALS`, que existe para que cinco intentos
por cuenta (RN-006) no permitan probar una contraseña común contra todas las
cuentas que se quiera, y `SESSION`. Y una evidencia legal dejaba de probar nada,
que es de las primeras cosas que revisa una autoridad.

`prod` no se ha desplegado nunca, así que **ninguna constancia de consentimiento
real está comprometida**: lo registrado en `dev` es de pruebas.

## Opciones

**1. Declarar proxies de confianza y descartar la cabecera si la conexión no viene
de uno.** Es lo correcto en una red que uno controla.

- A favor: no hay que contar nada; la confianza se declara por dirección.
- En contra: las direcciones del frontend de Google no son estables ni están
  publicadas como lista corta, y en Cloud Run la conexión al contenedor no llega
  con la dirección del proxy de forma utilizable.

**2. `server.forward-headers-strategy: NATIVE`,** que activa `RemoteIpValve` de
Tomcat.

- A favor: es la pieza estándar, probada, y arregla `getRemoteAddr()` para todo el
  mundo de una vez.
- En contra: **su modelo de confianza no es el de Cloud Run.** `RemoteIpValve`
  recorre la cabecera desde la derecha saltando lo que case con `internalProxies`,
  que por omisión son los rangos privados, y se queda con la primera entrada
  pública. El frontend de Google es público: si algún día añadiera una entrada
  suya después de la del cliente, la válvula se quedaría con **esa** y todo el
  tráfico compartiría un identificador. Afinarlo exige la misma medición que la
  opción 3 y encima por regex.

**3. Contar entradas desde el final, con el número por configuración.**

- A favor: es exactamente la garantía que da un proxy —lo que él añade va al
  final, y quien llama no lo puede borrar— y el número queda fuera del código.
- En contra: hay que medir el número, y equivocarlo rompe en los dos sentidos.

## Decisión

La dirección de quien llama es la entrada que está `sendik.client-ip.trusted-hops`
posiciones **desde el final** de `X-Forwarded-For`, con `1` en Cloud Run y `0` en
`local`, donde la cabecera se ignora entera. `server.forward-headers-strategy` se
queda sin poner, a propósito.

**Se leen todas las ocurrencias de la cabecera, no solo la primera.** `getHeader`
devuelve una sola línea por contrato del servlet, y contar desde el final solo protege
si lo que añade la infraestructura está en la misma lista: un salto que escribiera lo
suyo en una línea aparte dejaría a la vista una lista escrita entera por quien llama, y
la evasión volvería con el arreglo puesto. Aplanarlas en orden es la semántica de lista
de RFC 9110 y no depende de cómo las emita cada salto.

**Y la entrada se normaliza antes de usarla**: se le quita el puerto, se le quitan los
corchetes de IPv6 y se pasa a minúscula. No es cosmético, y falla en los dos sentidos:
un proxy que escriba `ip:puerto` le da a cada petición un identificador distinto —el
puerto efímero cambia— y el límite desaparece; y rechazar `[2001:db8::1]` por los
corchetes manda toda la familia IPv6 al respaldo, que es lo de abajo.

## Motivo

Lo que un atacante controla es el **principio** de la cabecera, y lo que la
infraestructura escribe va **después**. Contar desde el final es contar desde la
única parte que quien llama no puede tocar; contar desde el principio es dejar que
elija su propio identificador. No es una cuestión de grado entre las dos: son la
defensa y su ausencia.

El número es de topología y no de negocio —depende de cuántos saltos hay delante,
no de lo que haga Sendik—, así que es configuración y no una constante. El día que
haya un balanceador delante de Cloud Run, la cifra sube y no se toca una línea de
código.

Y cuando el cálculo no cuadra —la cabecera no llega, trae menos entradas de las
declaradas, o lo que hay en esa posición no se parece a una dirección— se cae a la
dirección de la **conexión** y nunca a otra entrada de la cabecera. Las dos salidas son
malas y no lo son igual: tomar una entrada elegida por quien llama es no tener límite.

**Pero el respaldo no es un límite estricto de más, y conviene no endulzarlo:** detrás
de Cloud Run la conexión viene del proxy, así que es **una sola clave para todo el
mundo** —diez por minuto para el sitio entero— y una constancia de consentimiento
idéntica para cuantas personas se registren mientras dure. Por eso caer ahí **avisa a
WARN**, que es el único nivel que se ve en `prod`, y no a DEBUG.

## Consecuencias

- El límite por IP vuelve a serlo, y la constancia de consentimiento vuelve a
  probar de dónde vino la aceptación.
- **Lo que no arregla, y hay que decirlo porque toca lo mismo:** el hash que se guarda
  es SHA-256 **sin clave** sobre un espacio de 2^32, así que recorrerlo es cuestión de
  minutos y `ip_hash` es la dirección en claro para quien tenga un volcado. Es anterior
  a esta decisión y no la cierra: pasar a HMAC con clave arrastra gestión de clave y la
  comparabilidad de las constancias ya guardadas. Queda abierto, y mientras tanto ni la
  clase ni `datos-personales.md` afirman que esto sea anonimato.
- **Una variable más que puede estar mal**, y equivocarla no falla al arrancar:
  quedarse corto deja la evasión intacta; pasarse deja fuera a todo el mundo a la
  vez, y desde un solo cliente eso se ve idéntico a que funcione. Por eso el borde
  registra a DEBUG la **forma** de la cabecera —cuántas entradas trae, cuál se toma
  y si coincide con la de la conexión—, con números y booleanos y **nunca con una
  dirección**: la promesa de `ClientIpHasher` es que la IP en claro no sale de ahí.
- `ClientIpHasher` deja de ser un `@Component` y pasa a construirse en `bootstrap`,
  que es el único módulo que ve a la vez la configuración tipada de
  `infrastructure` y el tipo de `presentation`. Es el mismo camino que ya siguen
  `RateLimitSettings` y `RefreshCookies`.
- `getRemoteAddr()` sigue devolviendo la dirección de la conexión y no la del
  cliente, porque no se activa la estrategia de Tomcat. Hoy nadie más la lee; quien
  la lea mañana tiene que pasar por `ClientIpHasher`.
- `ArchitectureTest` gana una regla: ningún campo con `@Value`. El grafo de Gradle
  impide que el borde importe la clase de configuración, pero no impedía el atajo
  equivalente —devolverle el `@Component` al hasher y leer el valor con `@Value`, que
  `spring-beans` permite desde `presentation`—, y con eso esta decisión quedaba solo
  escrita.
- El tope por IP en rutas autenticadas, que el PR #41 dejó fuera a propósito,
  **deja de estar bloqueado**. Sigue sin decidirse si conviene: contar por sujeto
  del token es correcto y no depende de esto.

## Cuándo revisar

Cuando cambie lo que hay delante de la aplicación: un balanceador de Google, un
CDN, Firebase Hosting delante de Cloud Run —la alternativa que `despliegue.md`
guarda por si el mapeo de dominios no está disponible— o el primer despliegue de
`prod`, que hoy no existe y podría no tener la misma topología que `dev`. En
cualquiera de esos casos hay que volver a medir la forma de la cabecera antes de
dar por buena la cifra.
