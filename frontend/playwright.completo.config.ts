import { defineConfig, devices } from '@playwright/test';

/**
 * Pruebas de extremo a extremo que cruzan las dos mitades.
 *
 * <p>`playwright.config.ts` prueba el HTML que sale del servidor de renderizado y
 * ninguna de sus pruebas llama a la API: eso esta bien y sigue igual. Lo que
 * faltaba es esto. `docs/arquitectura/pruebas.md` y las pruebas requeridas de
 * HU-001 piden extremo a extremo sobre registro, verificacion, ingreso, cierre y
 * recuperacion, y hasta ahora esos caminos estaban probados **por mitades**: con
 * MockMvc en `presentation` y con Testcontainers en `bootstrap`, pero nunca
 * unidos. Un contrato roto entre las dos mitades —un nombre de campo cambiado en
 * un DTO, una cookie con un atributo distinto— pasaba las dos suites y fallaba en
 * el navegador.
 *
 * <p>Config aparte y no un proyecto mas dentro de la otra, por dos razones: esta
 * necesita PostgreSQL, Java y el jar del backend, y no debe convertir la suite
 * rapida en una que exija todo eso; y en integracion continua son dos trabajos
 * distintos, porque cuando falla importa muchisimo saber si se rompio el
 * renderizado o el contrato.
 *
 * <p>Antes de correrlas:
 *
 * <pre>
 *   docker compose up -d postgres
 *   cd backend && ./gradlew :bootstrap:bootJar
 *   cd frontend && npm run e2e:completo
 * </pre>
 */
const PUERTO_WEB = 4174;
const PUERTO_API = 8081;

/**
 * Los dos en `localhost` y no uno en `localhost` y otro en `127.0.0.1`.
 *
 * <p>No es cosmetico. Para el navegador son dos anfitriones distintos, asi que la
 * cookie de refresco —`SameSite=Strict` por ADR-0003— se considera de otro sitio y
 * no se manda. El efecto es que todo funciona hasta que se recarga la pagina: el
 * token de acceso vive en memoria y sobrevive a la navegacion del enrutador, pero
 * una carga completa necesita la cookie para recuperar la sesion, y sin ella
 * `/mi-cuenta` se queda cargando para siempre.
 *
 * <p>En produccion son el mismo sitio (sendik.co y su subdominio de API), asi que
 * lo que habia que arreglar era el entorno de la prueba, no el producto. El puerto
 * no cuenta para decidir si dos direcciones son del mismo sitio; el anfitrion, si.
 */
const BASE_URL = `http://localhost:${PUERTO_WEB}`;

/**
 * El correo de quien modera. Lo comparten esta configuracion y la suite de HU-006.
 *
 * <p><strong>Fijo, no generado.</strong> Se intento con un sufijo de `Date.now()` para
 * que la cuenta naciera limpia en cada corrida, y no funciona: este archivo se evalua una
 * vez en el proceso que arranca el backend y otra en cada proceso de trabajo que importa
 * la constante, asi que el correo configurado y el que usa la prueba salian distintos y
 * el rol nunca llegaba. El sintoma era una moderadora con sesion abierta a la que el
 * guard echaba de la bandeja.
 *
 * <p>Con un correo fijo la cuenta sobrevive entre corridas, y las pruebas lo asumen: la
 * primera vez la crean y las siguientes entran. El rol se lo concede el arranque cuando
 * ya existe, y el registro cuando todavia no.
 */
export const MODERADORA = 'quien-modera@sendik.test';
const API_URL = `http://localhost:${PUERTO_API}`;

/**
 * La base de datos. Los valores por omision son los de `docker-compose.yml`, que
 * es lo que hay levantado en local; en integracion continua llegan del servicio
 * de PostgreSQL del trabajo.
 */
const DB_URL = process.env['DB_URL'] ?? 'jdbc:postgresql://127.0.0.1:5432/sendik';
const DB_USERNAME = process.env['DB_USERNAME'] ?? 'sendik';
const DB_PASSWORD = process.env['DB_PASSWORD'] ?? 'sendik';

export default defineConfig({
  testDir: './e2e-completo',
  // A proposito en serie. Las pruebas comparten una base de datos y el limitador
  // de tasa cuenta por origen: en paralelo se estorban entre ellas y el fallo que
  // producen no es el fallo que buscan.
  fullyParallel: false,
  workers: 1,

  /**
   * Un minuto por prueba, y no los treinta segundos que trae Playwright.
   *
   * <p>El valor por omision esta pensado para pruebas de una pantalla, y aqui una prueba
   * es un recorrido entero: dejar una vendedora verificada -registro, correo, documento,
   * selfie, cuenta, y la aprobacion por la bandeja- y encima publicar con sus ocho tomas.
   * Eso ronda los diez segundos en local y el doble o mas en un corredor compartido, asi
   * que estaba rozando el techo y cualquier lentitud lo pasaba.
   *
   * <p><strong>Se nota porque el fallo miente.</strong> Cuando el presupuesto se agota,
   * Playwright culpa a la accion que estaba en vuelo, asi que el mismo problema aparecia
   * unas veces como una espera de respuesta colgada y otras como un boton que no llegaba;
   * dos causas distintas para el mismo intermitente, y las dos falsas. Los temporizadores
   * de los servidores de aqui abajo -180 y 420 segundos- si estan puestos a conciencia;
   * este era el unico que nadie habia elegido.
   *
   * <p>No tapa nada: una prueba que de verdad se cuelgue sigue fallando, solo que treinta
   * segundos mas tarde.
   */
  timeout: 60_000,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 1 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
  },

  projects: [
    {
      name: 'chromium',
      use: {
        ...devices['Desktop Chrome'],
        // La camara, para el recorrido de verificacion de vendedor (HU-002). El
        // dispositivo falso de Chromium entrega un patron sintetico con bordes
        // marcados, que es justo lo que la deteccion de desenfoque necesita para
        // aceptar la foto; y el permiso concedido evita el dialogo del navegador,
        // que ninguna prueba puede pulsar.
        permissions: ['camera'],
        launchOptions: {
          args: ['--use-fake-ui-for-media-stream', '--use-fake-device-for-media-stream'],
        },
      },
    },
  ],

  webServer: [
    {
      // El backend de verdad, con Flyway migrando al arrancar.
      command: 'node e2e-completo/arrancar-backend.mjs',
      // El chequeo de estado de actuator: responde cuando la base ya migro, que es
      // lo que de verdad hay que esperar.
      url: `${API_URL}/actuator/health`,
      reuseExistingServer: !process.env['CI'],
      timeout: 180_000,
      env: {
        SPRING_PROFILES_ACTIVE: 'local',
        SERVER_PORT: String(PUERTO_API),

        DB_URL,
        DB_USERNAME,
        DB_PASSWORD,

        JWT_ISSUER: BASE_URL,
        JWT_SECRET: 'secreto-de-pruebas-de-extremo-a-extremo-largo',
        // Cortos a proposito: el refresco y su rotacion son parte de lo que se
        // prueba, y con quince minutos no se llega a observar nada.
        JWT_ACCESS_TTL: 'PT2M',
        JWT_REFRESH_TTL: 'PT30M',
        JWT_REFRESH_GRACE: 'PT10S',

        // Sin TLS en local: es lo unico que se relaja, y solo aqui.
        SESSION_COOKIE_SECURE: 'false',

        APP_BASE_URL: BASE_URL,
        APP_API_BASE_URL: `${API_URL}/api/v1`,
        CORS_ALLOWED_ORIGINS: BASE_URL,
        SUPPORT_EMAIL: 'soporte@example.test',
        APP_TIME_ZONE: 'America/Bogota',

        // El limitador se sube, no se apaga: apagarlo dejaria sin ejercitar el
        // interceptor, que es codigo de produccion que corre en cada peticion.
        // Subido, la suite entera cabe dentro de la ventana.
        RATE_LIMIT_CREDENTIALS_MAX: '500',
        RATE_LIMIT_SESSION_MAX: '500',

        // El almacen local, con su carpeta dentro de e2e-completo para que se pueda
        // borrar sin tocar nada mas. El backend sirve esa carpeta en /archivos
        // cuando el proveedor es local (LocalFilesWiring), y por eso la direccion
        // publica apunta al backend y no al servidor de renderizado.
        STORAGE_PROVIDER: 'local',
        STORAGE_LOCAL_PATH: './e2e-completo/.archivos',
        STORAGE_PUBLIC_BASE_URL: `${API_URL}/archivos`,
        // Bajo a proposito: 200x200 permite generar imagenes de prueba pequenas y
        // rapidas, y sigue habiendo una prueba de que el minimo se aplica.
        STORAGE_AVATAR_MIN_WIDTH: '200',
        STORAGE_AVATAR_MIN_HEIGHT: '200',

        // Imprime el enlace en el registro en vez de enviarlo. Es como la prueba
        // recupera el token: ver e2e-completo/arrancar-backend.mjs.
        MAIL_PROVIDER: 'console',
        MAIL_FROM: 'no-responder@example.test',
        MAIL_PROVIDER_API_KEY: '',

        // La comprobacion de contrasenas filtradas sale a la red y falla abierto
        // (ADR-0013). Apagada aqui: no se prueba a Have I Been Pwned, y dejarla
        // encendida mete una llamada externa y dos segundos de espera en cada
        // registro.
        PASSWORD_BREACH_CHECK_ENABLED: 'false',

        LEGAL_TERMS_VERSION: 'borrador-local',
        LEGAL_PRIVACY_VERSION: 'borrador-local',

        // HU-002. Sin la bandera, el controlador de verificacion no se crea y sus
        // rutas responden 404: la prueba del recorrido no tendria contra que correr.
        FEATURE_SELLER_VERIFICATION: 'true',

        /**
         * HU-006: quien va a moderar en esta suite.
         *
         * <p>El correo es fijo y la cuenta se crea despues, por la interfaz, como
         * cualquier otra. Funciona porque el rol se concede tambien al registrarse y no
         * solo al arrancar, que es ademas el orden natural de dar de alta a alguien:
         * primero se decide quien modera, despues esa persona crea su cuenta.
         */
        SECURITY_BOOTSTRAP_MODERATORS: MODERADORA,
        VERIFICATION_REVIEW_DAYS: '2',

        // HU-007 y HU-008. Sin la bandera, ni el formulario de publicar ni la bandeja de
        // moderacion de publicaciones existen: sus rutas responden 404 y el recorrido del
        // ciclo completo no tendria contra que correr.
        FEATURE_PUBLISHING: 'true',
        LISTING_REVIEW_DAYS: '2',

        // HU-009. Sin ella el catalogo publico no existe: sus endpoints responden 404 y el
        // recorrido que cierra la fase -alguien sin cuenta encuentra lo que se aprobo- no
        // tendria contra que correr.
        FEATURE_CATALOG: 'true',

        // HU-014. Sin ella la ruta del catalogo sigue existiendo pero los parametros de
        // busqueda responden 404, asi que el recorrido -publicar, aprobar y encontrarlo
        // escribiendo- no tendria contra que correr.
        FEATURE_SEARCH: 'true',

        COMPANY_NAME: 'Sendik S.A.S.',
        COMPANY_TAX_ID: '000000000-0',
        COMPANY_ADDRESS: 'Medellin, Colombia',
        COMMISSION_RATE: '0.05',
      },
    },
    {
      command: process.env['CI']
        ? 'node dist/sendik/server/server.mjs'
        : 'npm run build && node dist/sendik/server/server.mjs',
      url: BASE_URL,
      reuseExistingServer: !process.env['CI'],
      timeout: 420_000,
      env: {
        PORT: String(PUERTO_WEB),
        NG_ALLOWED_HOSTS: 'localhost',
        // Aqui si apunta al backend de verdad. Es toda la diferencia con la otra
        // configuracion.
        API_BASE_URL: `${API_URL}/api/v1`,
        // El mismo numero que el backend. Se declara en los dos lados a proposito: la
        // pantalla lo dice y el correo tambien, y una prueba que confirma el texto contra
        // el valor por omision no comprueba que la configuracion llegue.
        VERIFICATION_REVIEW_DAYS: '2',
        LISTING_REVIEW_DAYS: '2',
        COMPANY_NAME: 'Sendik S.A.S.',
        COMPANY_TAX_ID: '000000000-0',
        COMPANY_ADDRESS: 'Medellin, Colombia',
        SUPPORT_EMAIL: 'soporte@example.test',
        LEGAL_TERMS_VERSION: 'borrador-local',
        LEGAL_PRIVACY_VERSION: 'borrador-local',
        LEGAL_COOKIES_VERSION: 'borrador-local',
      },
    },
  ],
});
