// Los adaptadores: base de datos, clientes externos, almacenamiento y la
// configuracion tipada. Todo lo sucio y todo lo reemplazable.

import java.util.concurrent.Callable

plugins {
    id("sendik.spring-conventions")
}

dependencies {
    implementation(project(":application"))
    implementation(project(":domain"))

    // Spring Data JDBC, no JPA (ADR-0004).
    implementation(libs.spring.boot.starter.data.jdbc)

    // Argon2id para las contrasenas, nunca BCrypt (backend/CLAUDE.md). Solo el
    // modulo de criptografia: aqui no se configura ningun filtro HTTP.
    // BouncyCastle no es opcional, Argon2PasswordEncoder lo exige.
    implementation(libs.spring.security.crypto)
    implementation(libs.bouncycastle.provider)

    // RestClient para los dos clientes externos (ADR-0012 y ADR-0013). Solo
    // spring-web: aqui no se atiende ninguna peticion HTTP.
    implementation(libs.spring.web)

    // El convertidor JSON que RestClient usa para el cuerpo que va a Resend. Se
    // estaba usando sin declararlo: llegaba de rebote por el starter de webmvc de
    // `presentation`. Un modulo declara lo que usa, y de esto en concreto depende
    // que salga cada correo transaccional.
    implementation(libs.spring.boot.starter.json)

    // Emision del token de acceso, y el decodificador que consume la cadena de
    // seguridad. Va aqui porque necesita el secreto de firma, que es configuracion
    // y la configuracion vive en esta capa (ADR-0003).
    api(libs.spring.security.oauth2.jose)

    // Cloud Storage: los dos almacenes de archivos de ADR-0018. Solo se carga con
    // `sendik.storage.provider=gcs`; con `local` los beans no se crean y la
    // libreria no se toca.
    implementation(libs.google.cloud.storage)

    // Cloud Tasks: la cola del correo transaccional (ADR-0031). Solo se carga con
    // `sendik.mail.queue.enabled=true`; en local el bean no se crea y la libreria
    // no se toca, igual que Cloud Storage con el almacen local.
    implementation(libs.google.cloud.tasks)

    // La configuracion se declara en clases @ConfigurationProperties validadas:
    // si falta una variable obligatoria, la aplicacion no arranca
    // (docs/operacion/configuracion.md).
    implementation(libs.spring.boot.starter.validation)

    // Integracion contra PostgreSQL real. H2 esta prohibido: se comporta
    // distinto y da falsa confianza (docs/arquitectura/pruebas.md).
    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.spring.boot.testcontainers)
    testImplementation(libs.testcontainers.postgresql)
    testImplementation(libs.testcontainers.junit.jupiter)
    testRuntimeOnly(libs.postgresql)
}

// Los repositorios JDBC de este modulo no se pueden probar desde aqui: necesitan
// el esquema, el esquema lo define Flyway y las migraciones viven en `bootstrap`,
// que esta mas arriba en el grafo. Sus pruebas de integracion estan alli
// (CatalogPersistenceTest, SessionLifecycleTest, SellerVerificationPersistenceTest,
// ProfileAndEmailChangeTest) y su cobertura se anota en el archivo de ejecucion de
// ese modulo, no en el de este.
//
// Antes la regla resolvia eso excluyendo `identity/persistence` de la medicion: lo
// que no podia alcanzar, no lo miraba. Esa lista quedo desactualizada en cuanto
// llego `catalog/persistence`, y una exclusion que hay que recordar ampliar cada
// vez que nace un adaptador no es una salvaguarda, es una trampa que estalla en el
// commit siguiente.
//
// Ahora la regla lee los dos archivos de ejecucion, el propio y el de `bootstrap`,
// y mide todas las clases del modulo. Una clase cubierta desde las pruebas de
// integracion cuenta como lo que es: cubierta. No hay nada que excluir ni que
// mantener al dia.
//
// Todo se resuelve con `Callable`, igual que la agregada de la raiz: los archivos
// `.exec` no existen hasta que las pruebas corren, asi que la ruta se resuelve en
// tiempo de ejecucion y no de configuracion.
val datosDeEjecucion = files(Callable {
    listOf(project, project(":bootstrap")).map { modulo ->
        modulo.fileTree(modulo.layout.buildDirectory.dir("jacoco")) { include("*.exec") }
    }
})

// El informe lee exactamente los mismos datos que la regla. Si solo cambiara la
// regla, el HTML de este modulo seguiria diciendo 57% mientras la verificacion
// pasa, y quien lo abriera no sabria a cual de los dos creerle.
listOf(
    tasks.named<JacocoCoverageVerification>("jacocoTestCoverageVerification"),
    tasks.named<JacocoReport>("jacocoTestReport"),
).forEach { tarea ->
    tarea {
        // Sin esto la tarea leeria un `.exec` de `bootstrap` viejo o inexistente y
        // daria un numero que no corresponde al codigo que se esta verificando.
        dependsOn(":bootstrap:test")
        executionData.setFrom(datosDeEjecucion)
    }
}
