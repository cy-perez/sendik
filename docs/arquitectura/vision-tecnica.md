# Visión técnica

## Panorama

```
Navegador
   |
   |  HTML renderizado en servidor + hidratación
   v
Angular 21 SSR  (Cloud Run, ADR-0024)
   |
   |  REST /api/v1  ·  JWT  ·  JSON
   v
Spring Boot 4.1  (Cloud Run)
   |
   +--> PostgreSQL 17        datos transaccionales
   +--> Cloud Storage        imágenes de producto y verificación
   +--> Wompi                recaudo y división de pago
   +--> (la búsqueda va contra ese mismo PostgreSQL, no contra Typesense: ADR-0035)
   +--> Skydropx Colombia    cotización, guías y seguimiento (Fase 3)
   +--> Proveedor de correo  transaccionales
```

## Las cuatro capas

La regla es una sola: **las dependencias apuntan hacia adentro**. El dominio no
sabe que existe una base de datos, ni un navegador, ni HTTP.

```
        presentation
              |
              v
        application  ---- define puertos ---->  (interfaces)
              |                                      ^
              v                                      |
          domain                              infrastructure
                                              implementa puertos
```

**domain.** El corazón. Entidades, objetos de valor, eventos, invariantes,
excepciones de negocio. Sin anotaciones, sin librerías, sin nada que se pueda
reemplazar. Si mañana cambia la base de datos, el marco web o la pasarela, esta
capa no se toca. Se prueba con JUnit puro en milisegundos.

**application.** Los casos de uso. Orquesta el dominio, abre transacciones,
publica eventos y declara **puertos de salida**: `UserRepository`, `MailSender`,
`PublicFileStore` y `RestrictedFileStore`; con las fases siguientes llegan
`PaymentGateway` y los demás. Son interfaces que esta capa define según lo que
necesita, no según lo que la tecnología ofrece.

Que los archivos sean **dos** puertos y no uno con un parámetro de visibilidad es
el ejemplo de esa frase: la capa necesita distinguir «guarda esto para que
cualquiera lo vea» de «guarda esta cédula», y con un solo puerto esa diferencia
quedaría a un argumento de distancia (ADR-0018).

**infrastructure.** Los adaptadores. Implementa cada puerto contra la tecnología
real: repositorios con Spring Data JDBC, cliente de Cloud Storage, cliente HTTP de
Resend y, en Fase 3, el de Wompi. Aquí vive todo lo sucio y todo lo reemplazable.
Cada puerto de archivos tiene dos adaptadores —sistema de archivos y Cloud
Storage— y los elige una variable, no un despliegue.

**presentation.** El borde. Controladores REST del lado backend, componentes y
rutas del lado Angular. Traduce entre el mundo exterior y los casos de uso. No
decide nada de negocio.

### Cómo se verifica que nadie la rompe

1. **Gradle.** `domain` no declara dependencias de framework; el módulo
   literalmente no puede importar Spring.
2. **ArchUnit.** Pruebas que fallan si aparece un import prohibido, si un
   controlador llama a un repositorio, o si una entidad de tabla se filtra a la
   API.
3. **Subagente revisor.** `.claude/agents/arquitecto.md` revisa cada
   cambio contra estas reglas.

Las tres capas de defensa son deliberadas: la primera evita el error, la segunda
lo detecta, la tercera lo explica.

## Contextos

El código se organiza por contexto de negocio, no por tipo de artefacto. Dentro
de cada contexto se repiten las cuatro capas.

| Contexto | Responsabilidad | Fase |
|---|---|---|
| `identity` | Cuentas, credenciales, sesiones, verificación de vendedor | 1 y 2 |
| `catalog` | Prendas, publicaciones, imágenes, moderación | 2 |
| ~~`search`~~ | **No existe.** La búsqueda vive dentro de `catalog` desde HU-014 | 3 |
| `order` | Pedidos y su ciclo de vida | 3 |
| `payment` | Intentos de pago, división, desembolsos | 3 |
| `shipping` | Cotización, guías y seguimiento, contra un agregador (ADR-0034) | 3 |
| `shared` | Objetos de valor comunes: dinero, identificadores, fechas | 1 |

**Por qué la búsqueda no es un contexto propio.** Esta tabla la anunciaba como uno desde
la Fase 1, y se escribió cuando la búsqueda iba a ser un índice externo con su propio
modelo. ADR-0035 quitó el índice: se consulta la misma base, y lo que la búsqueda devuelve
son agregados `Listing`, que son de `catalog`. Un `search/application` que importara
`co.sendik.catalog.model.Listing` rompería la regla de abajo, y es lo que `ArchitectureTest`
prohíbe. Así que el puerto `SearchEngine` que ADR-0008 definió vive en
`catalog/port/out` y su implementación en `catalog/persistence`. El día que Typesense
llegue con su índice y su modelo propio, el contexto vuelve a tener sentido; hoy sería un
paquete vacío que obliga a romper la regla para llenarlo.

`backend/CLAUDE.md` ya enumeraba los contextos sin `search`: los dos documentos se
contradecían y esto lo resuelve.

Un contexto no llama al repositorio de otro. Si necesita algo, es por un caso de
uso público o por un evento de dominio. Esto mantiene abierta la puerta a
separar servicios más adelante, sin pagar hoy el costo de hacerlo.

**Con una salvedad, desde HU-011:** `catalog` e `identity` se preguntan cosas en
las dos direcciones —aquel necesita saber si alguien está verificado y quién es un
vendedor; este necesita alcanzar los favoritos para la descarga de datos y el
cierre de cuenta—. Las cuatro conversaciones entran por la puerta pública del otro
y todas viven en `infrastructure`, pero el grafo entre contextos deja de ser
acíclico y con él la frase de arriba: hoy ninguno de los dos se puede extraer sin
volver asíncrona una de las dos direcciones. Está anotado en la deuda aceptada.

## Comunicación entre contextos

- El estado de un contexto no se consulta leyendo las tablas de otro. Nunca.
- **Cuándo vale cada mecanismo**, que estaba dicho a medias y se aclaró con HU-011:
  un **caso de uso público** cuando la respuesta hace falta ahora —una lectura, o
  una escritura que tiene que ser atómica con la del contexto que llama, como el
  borrado de favoritos dentro del cierre de cuenta—; un **evento de dominio tras
  confirmar la transacción** cuando el otro contexto solo tiene que enterarse. La
  frase anterior decía que dentro del mismo proceso se habla por eventos, y el
  código nunca lo hizo así: no hay bus de eventos y las tres conversaciones que
  existen son llamadas a casos de uso.
- **El acoplamiento entre contextos vive en `infrastructure`.** Un adaptador puede
  conocer a los dos; el dominio y la aplicación de un contexto no conocen otro. Lo
  vigilan dos reglas de `ArchitectureTest`, y es lo que hace que separar dos
  contextos algún día cueste reescribir adaptadores y no modelo.
- Ejemplo: cuando `payment` confirma un pago, publica `PaymentApproved`;
  `catalog` marca la prenda vendida y `order` avanza el pedido. Ninguno de los
  tres conoce a los otros dos.

## Flujo de una petición

```
POST /api/v1/auth/register
  |
  presentation   RegisterRequest -> valida formato -> RegisterSellerCommand
  |
  application    RegisterSellerUseCase
  |                 - pide el puerto UserRepository
  |                 - construye User (el dominio valida las invariantes)
  |                 - guarda, publica UserRegistered
  |
  domain         User, Email, Password: se niegan a existir en estado inválido
  |
  infrastructure UserJdbcRepository escribe en PostgreSQL
                 EmailSenderAdapter envía la verificación
```

Ningún objeto de dominio cruza hacia afuera. `presentation` devuelve su propio
DTO.

## Errores

- El dominio lanza excepciones de negocio con significado:
  `EmailAlreadyRegisteredException`, no `IllegalStateException`.
- Un manejador global en `presentation` las traduce a `ProblemDetail` con el
  código HTTP y el código de error del catálogo definido en `contrato-api.md`.
- El frontend nunca muestra un mensaje del backend directamente: recibe un código
  y lo traduce con Transloco. Así los mensajes existen en ES y EN.

## Configuración

Nada quemado en el código. Todo por variable de entorno, tipado y validado al
arrancar. Si falta una variable obligatoria, la aplicación no arranca: es
preferible a descubrirlo en producción. Ver `docs/operacion/configuracion.md`.

## Deuda aceptada conscientemente

| Decisión | Costo que se acepta | Cuándo se revisa |
|---|---|---|
| Monolito modular en un solo despliegue | Escala vertical al principio | Cuando un contexto necesite escalar solo |
| Mapeo manual entre dominio y tablas | Más código repetitivo | Si el volumen lo hace insostenible |
| Sin caché distribuida | Más consultas a la base | Cuando la latencia lo exija |
| Ciclo entre `catalog` e `identity` | Ninguno de los dos se puede extraer a un servicio sin volver asíncrona una de las dos conversaciones | Cuando alguno tenga que escalar solo |
| TanStack Query en estado experimental | Rupturas posibles al actualizar | Cuando el adaptador de Angular sea estable |
