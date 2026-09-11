# Backend — convenciones

Java 25 · Spring Boot 4.1.1 · Gradle 9 multi-módulo · PostgreSQL 17 ·
Spring Data JDBC · Flyway.

Lee primero `../CLAUDE.md`. Aquí solo está lo específico del backend.

## Módulos de Gradle

El grafo de dependencias de Gradle es lo que impide romper la arquitectura: un
módulo no puede importar lo que no declara.

```
domain          sin dependencias de framework
application     depende de: domain
infrastructure  depende de: application, domain
presentation    depende de: application, domain
bootstrap       depende de: todos. Contiene el main y el cableado.
```

Reglas:

- `domain` no declara **ninguna** dependencia salvo JSpecify para anotaciones de
  nulidad. Sin Spring, sin Jackson, sin JDBC, sin Lombok.
- `application` puede declarar solo `spring-tx` si necesita `@Transactional`.
  Nada más de Spring.
- Nadie depende de `bootstrap`.
- Cambiar el grafo de dependencias exige una ADR.

Lo común vive en `buildSrc`, en dos plugins de convención:

| Plugin | Qué configura | Quién lo aplica |
|---|---|---|
| `sendik.java-conventions` | Toolchain 25, `-Xlint:all -Werror -parameters`, JUnit 5, Spotless, JaCoCo | los cinco módulos |
| `sendik.spring-conventions` | La plataforma de versiones de Spring Boot | los cuatro que no son `domain` |

`domain` no recibe **ni el BOM**. Una plataforma no aporta clases, pero
declararla en el módulo que debe estar limpio confunde a quien lo lea después.

La prueba `ArchitectureTest`, en `bootstrap`, comprueba dieciséis reglas y falla si
alguien lo intenta. No la desactives.

El JDK no se instala a mano: `gradle/gradle-daemon-jvm.properties` fija Temurin
25 y Gradle lo descarga solo en la primera construcción.

## Estructura de paquetes

Raíz: `co.sendik.<contexto>`. Los contextos actuales son `identity`, `catalog`,
`order`, `payment`, `shipping`, `shared`.

```
domain/co/sendik/identity/
  model/          entidades y objetos de valor
  event/          eventos de dominio
  exception/      excepciones de negocio
  service/        lógica que no cabe en una sola entidad

application/co/sendik/identity/
  usecase/        un caso de uso por clase, un método público
  port/out/       interfaces que la infraestructura implementa
  dto/            comandos y resultados de casos de uso

infrastructure/co/sendik/identity/
  persistence/    repositorios, entidades de tabla y mapeadores
  client/         clientes HTTP externos
  config/         beans de infraestructura

presentation/co/sendik/identity/
  rest/           controladores
  rest/dto/       cuerpos de petición y respuesta, con validación
  rest/mapper/    conversión entre DTO de API y DTO de aplicación
```

Nunca se filtra un tipo de `domain` hacia la API pública. La API tiene sus
propios DTO aunque al principio parezcan idénticos.

## Dominio

- Objetos inmutables. `record` para objetos de valor, clases con constructor
  privado y factorías con nombre para entidades.
- El dominio se valida a sí mismo: un objeto no puede existir en estado
  inválido. Ejemplo: `Money.of(-1)` lanza excepción, no devuelve null.
- Dinero: **nunca** `double` ni `float`. Usa el objeto de valor `Money` con
  `BigDecimal` en pesos colombianos, sin decimales (el peso no los usa en
  precios de venta).
- Identificadores: objetos de valor tipados (`SellerId`, `ProductId`), nunca
  `UUID` ni `Long` sueltos en las firmas.
- Enumeraciones para estados. Las transiciones válidas se definen en el propio
  enum o en un servicio de dominio, no en el controlador.
- Sin anotaciones de framework. Ni una.

## Casos de uso

- Un caso de uso por clase: `RegisterSellerUseCase`, con un único método público
  `execute`.
- Recibe un comando (record), devuelve un resultado (record). Nunca recibe ni
  devuelve tipos de HTTP.
- `@Transactional` va aquí, nunca en el controlador ni en el repositorio.
- Los efectos externos (correo, pasarela, almacenamiento) se invocan por puerto,
  nunca por clase concreta.

## Persistencia

Spring Data JDBC, no JPA. El motivo está en `ADR-0004`. Consecuencias prácticas:

- Un repositorio por **agregado**, no por tabla.
- Sin carga perezosa, sin sesión, sin caché de primer nivel. Si necesitas datos
  de otro agregado, haz otra consulta explícita.
- Las entidades de tabla viven en `infrastructure` y son distintas de las del
  dominio. Hay un mapeador entre ambas. Sí, es más código; a cambio el esquema
  puede cambiar sin tocar el dominio.
- Consultas complejas y de lectura: `JdbcClient` con SQL explícito. No hay
  problema con escribir SQL; sí lo hay con esconderlo.
- Toda tabla nueva o cambio de columna entra por una migración de Flyway en
  `bootstrap/src/main/resources/db/migration`, con nombre
  `V<n>__descripcion_en_ingles.sql`. **Nunca** se edita una migración ya
  aplicada: se crea la siguiente.
- Nada de `ddl-auto`. Está desactivado y debe seguirlo.

## API REST

Las convenciones completas están en `../docs/arquitectura/contrato-api.md`.
Lo mínimo:

- Rutas en inglés, plural, minúsculas: `/api/v1/sellers/{id}/products`.
- Versión en la ruta desde el día uno.
- Errores siempre con `ProblemDetail` (RFC 9457). Nunca un `Map<String,String>`
  improvisado ni un texto plano.
- Validación con Jakarta Validation en el DTO de entrada, más la validación del
  dominio. Las dos, no una.
- Paginación por cursor en listados de catálogo, no por número de página.
- Un controlador delega y traduce. Si tiene un `if` de negocio, está mal ubicado.

## Seguridad

- Spring Security 7.1, configuración con la DSL de lambdas y beans
  `SecurityFilterChain`. La clase `WebSecurityConfigurerAdapter` no existe hace
  años: no la escribas.
- JWT propio: token de acceso corto en memoria del cliente y token de refresco
  rotatorio en cookie `HttpOnly`, `Secure`, `SameSite=Strict`. Detalle en
  `ADR-0003`.
- Contraseñas con Argon2id. No BCrypt.
- El identificador del usuario autenticado se obtiene del contexto de seguridad,
  jamás de un parámetro de la petición.
- Cada endpoint declara su autorización explícitamente. Nada queda abierto por
  omisión.

## Errores propios de Spring Boot 4

Estas API cambiaron respecto a Spring Boot 3 y son la fuente más común de código
incorrecto:

| No uses | Usa |
|---|---|
| `com.fasterxml.jackson.*` | `tools.jackson.*` (Jackson 3) |
| `javax.*` | `jakarta.*` |
| `RestTemplate` | `RestClient` o una interfaz `@HttpExchange` |
| `@MockBean`, `@SpyBean` | `@MockitoBean`, `@MockitoSpyBean` |
| Inyección por campo con `@Autowired` | Constructor, sin anotación |
| `spring-boot-starter-web` monolítico | El starter específico que necesites |
| `@RequestMapping` genérico | `@GetMapping`, `@PostMapping`, etc. |

Ante la duda sobre una API, revisa cómo está resuelto en el código existente
antes de escribir desde cero.

Los starters en uso, ya verificados contra el BOM 4.1.1:
`spring-boot-starter-webmvc`, `-validation`, `-data-jdbc`, `-flyway`,
`-actuator`, `-security`, `-oauth2-resource-server`, `-json`, `-test` y
`spring-boot-starter` a secas en `bootstrap`.

`-json` está en `infrastructure` porque `RestClient` necesita un convertidor para
serializar el cuerpo que va a Resend. Se usaba sin declararlo: llegaba de rebote
por el starter de webmvc de `presentation`. Un módulo declara lo que usa, y de
esto en concreto depende que salga cada correo transaccional —`enviar()` se traga
la excepción y solo la registra, así que sin convertidor no saldría ninguno y el
build seguiría en verde.

Spring Security ya está en el proyecto desde HU-001, con su bean
`SecurityFilterChain` en `presentation` (`SecurityConfig`). Cada endpoint declara
su autorización y nada queda abierto por omisión.

## Pruebas

- Unitarias de `domain` y `application`: JUnit 5, sin Spring, sin base de datos.
  Rápidas. Cobertura mínima 90% en `domain`.
- `infrastructure` tiene pruebas propias de todo lo que no necesita base de datos:
  Argon2, el generador de tokens, el emisor de JWT, los enlaces de correo y los
  dos clientes externos, estos contra un servidor HTTP local. Los seis
  repositorios JDBC **no** se pueden probar desde aquí: necesitan el esquema, y el
  esquema lo define Flyway con las migraciones de `bootstrap`. Sus pruebas de
  integración viven allí, con Testcontainers y PostgreSQL 17. Nunca H2: se
  comporta distinto y esconde errores reales.
- `@SpringBootTest` solo en `bootstrap` y solo para caminos completos.
- Nombres de prueba en español descriptivo:
  `deberia_rechazar_registro_cuando_el_correo_ya_existe`.
- Datos de prueba por constructores de objetos (`SellerBuilder`), no por SQL
  suelto repetido en cada prueba.
- La cobertura mínima la exige `gradlew.bat check`, no es una aspiración: 90% en
  `domain`, 80% en el resto. El 80% se mide **sobre los cinco módulos juntos**
  (`verificarCoberturaAgregada`, en `backend/build.gradle.kts`) y no módulo a
  módulo: medido por módulo, lo que `bootstrap` ejercita de `infrastructure` no
  contaba, y un módulo sin pruebas propias no tenía datos de ejecución y se
  saltaba la verificación entera sin decir nada. Ver
  `docs/arquitectura/pruebas.md`.
- La regla del módulo `infrastructure` mide **todas** sus clases, y para eso lee
  dos archivos de ejecución: el suyo y el de `bootstrap`. Así, un repositorio JDBC
  cubierto por una prueba de integración cuenta como cubierto. Antes la regla
  excluía `identity/persistence` en vez de leer ese segundo archivo; la exclusión
  quedó desactualizada en cuanto llegó `catalog/persistence` y el build empezó a
  fallar con un 57% que no reflejaba lo que estaba probado.
- Testcontainers 2 movió las clases de sitio. Es
  `org.testcontainers.postgresql.PostgreSQLContainer`, sin parámetro de tipo, no
  `org.testcontainers.containers.PostgreSQLContainer<?>` de la versión 1.
- La inyección por constructor ya está activada en las pruebas
  (`spring.test.constructor.autowire.mode=all`, en `sendik.spring-conventions`).
  Una prueba de Spring recibe sus dependencias por constructor, igual que el
  código de producción.

### Sobre `ArchitectureTest`

- Sus patrones se anclan a `co.sendik..`. Sin anclar, `..client..` también casa
  con `org.springframework.web.client` y la regla acusa a quien no debe.
- Es el único archivo `.java` exento del hook `revisar-convenciones.mjs`, porque
  tiene que nombrar las API prohibidas para poder prohibirlas. La exención va por
  ruta completa: un `ArchitectureTest.java` en otra carpeta sí se revisa.
- En Fase 1 varios módulos están vacíos y las reglas llevan
  `allowEmptyShould(true)`. Cuando un módulo deje de estarlo, quítaselo: una
  regla que no evalúa nada no protege nada.
- Regla nueva que agregues, regla que debes ver fallar antes de darla por buena.

## Configuración

- `application.yaml` con perfiles `local`, `dev` y `prod`.
- Todo valor externo se declara en una clase `@ConfigurationProperties` tipada y
  validada, dentro de `infrastructure`. Nada de `@Value` esparcido.
- Los secretos llegan por variable de entorno. En producción, desde Secret
  Manager. Nunca en el YAML.
