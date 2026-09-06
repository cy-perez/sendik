import org.gradle.testing.jacoco.tasks.JacocoReportBase
import org.springframework.boot.gradle.tasks.run.BootRun

// El unico modulo que conoce a todos: contiene el main, la configuracion, las
// migraciones y las pruebas que verifican el cableado completo.
// Nadie depende de bootstrap.

plugins {
    id("sendik.spring-conventions")
    alias(libs.plugins.spring.boot)
}

dependencies {
    implementation(project(":domain"))
    implementation(project(":application"))
    implementation(project(":infrastructure"))
    implementation(project(":presentation"))

    implementation(libs.spring.boot.starter.base)
    implementation(libs.spring.boot.starter.actuator)

    // El cableado de los casos de uso necesita ver los puertos y la
    // configuracion tipada, que son api de application e infrastructure.
    implementation(libs.spring.boot.starter.validation)

    // Para declarar el origen permitido de CORS a partir de la configuracion.
    implementation(libs.spring.web)

    // Para servir los archivos del almacen local en desarrollo (LocalFilesWiring).
    // No agrega nada al classpath de ejecucion: `presentation` ya trae este starter,
    // solo que como `implementation`, asi que no se hereda al compilar. Se declara
    // aqui por el mismo motivo que spring-web arriba: un modulo declara lo que usa.
    implementation(libs.spring.boot.starter.webmvc)

    // Flyway gobierna el esquema y sus migraciones viven en este modulo. Ningun
    // codigo importa Flyway: es puro arranque, por eso runtimeOnly.
    runtimeOnly(libs.spring.boot.starter.flyway)
    runtimeOnly(libs.flyway.database.postgresql)
    runtimeOnly(libs.postgresql)

    // Las pruebas de este modulo si necesitan JDBC y Validation al compilar: la
    // de migraciones consulta el historial de Flyway con SQL explicito, y la de
    // contexto lee las clases de configuracion validadas. Los modulos internos
    // los declaran como `implementation`, o sea que no se heredan: se piden
    // aqui, que es justo lo que se quiere.
    testImplementation(libs.spring.boot.starter.data.jdbc)
    testImplementation(libs.spring.boot.starter.validation)

    // Solo para sustituir el cliente de Cloud Storage por un doble en la prueba que
    // comprueba que `provider=gcs` cablea los adaptadores de la nube. No entra en el
    // classpath de ejecucion: `infrastructure` ya lo trae, y lo trae como
    // `implementation` justamente para que la dependencia del proveedor no se herede
    // ni se pueda importar desde otro modulo por descuido (ADR-0018).
    testImplementation(libs.google.cloud.storage)

    // Y lo mismo con el cliente de Cloud Tasks, por el mismo motivo y con la misma
    // forma: la prueba que levanta la aplicacion con la cola encendida (ADR-0031)
    // necesita sustituirlo por un doble, porque crear el de verdad resuelve
    // credenciales de Google al construir el bean.
    testImplementation(libs.google.cloud.tasks)

    // Los post-procesadores `jwt()` con los que se prueba la autorizacion por rol de
    // las rutas de revision (HU-002). Es la unica forma de comprobar @PreAuthorize y la
    // regla de la cadena: sin contexto de seguridad, un montaje autonomo responde 200 a
    // todo y la prueba no probaria nada.
    testImplementation(libs.spring.security.test)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)

    // Segunda linea de defensa de la arquitectura, despues del grafo de Gradle
    // (docs/arquitectura/vision-tecnica.md).
    testImplementation(libs.archunit.junit5)
}

// La clase de arranque no entra en la medicion de cobertura: es configuracion de
// framework sin logica propia, y docs/arquitectura/pruebas.md dice expresamente
// que eso no se prueba. Que el cableado funciona lo demuestra
// ApplicationContextTest levantando el contexto completo, no un porcentaje.
val clasesMedibles = the<SourceSetContainer>()
    .named("main")
    .get()
    .output
    .classesDirs
    .asFileTree
    .matching { exclude("co/sendik/SendikApplication.class") }

tasks.withType<JacocoReportBase>().configureEach {
    classDirectories.setFrom(clasesMedibles)
}

// `gradlew.bat bootRun` arranca en el perfil local sin pedir nada mas. El
// artefacto empaquetado no lleva perfil por omision a proposito: en la nube
// SPRING_PROFILES_ACTIVE es obligatorio y olvidarlo debe romper el arranque.
tasks.named<BootRun>("bootRun") {
    systemProperty("spring.profiles.active", providers.systemProperty("spring.profiles.active").getOrElse("local"))

    // Se ejecuta desde backend/, que es donde el README dice que uno se para.
    // Asi el `optional:file:../.env` de application.yaml apunta al .env de la
    // raiz del repositorio, el mismo que lee docker compose. Sin esto, la
    // aplicacion usa sus valores por omision y la base los del .env: la
    // contrasena no coincide y el arranque falla por una causa que no es real.
    workingDir = rootProject.projectDir
}
