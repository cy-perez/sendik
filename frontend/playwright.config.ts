import { defineConfig, devices } from '@playwright/test';

const PORT = 4173;
const BASE_URL = `http://localhost:${PORT}`;

/**
 * Las pruebas de extremo a extremo corren contra el servidor de renderizado
 * real, no contra el de desarrollo: lo que se quiere comprobar es justamente el
 * HTML que sale del servidor.
 *
 * Los navegadores no se descargan solos. La primera vez hace falta:
 *
 *   npx playwright install chromium
 */
export default defineConfig({
  testDir: './e2e',
  fullyParallel: true,
  forbidOnly: Boolean(process.env['CI']),
  retries: process.env['CI'] ? 2 : 0,
  reporter: process.env['CI'] ? 'github' : 'list',

  use: {
    baseURL: BASE_URL,
    trace: 'on-first-retry',
  },

  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  /**
   * Comparacion de capturas para las pruebas de maquetacion (ADR-0033).
   *
   * <strong>El caso que vienen a cubrir.</strong> El 7 de septiembre de 2026 se
   * fusiono una migracion del sistema de diseno con la canalizacion entera en
   * verde y el sitio quedo descuadernado. No fue un fallo de la suite: axe mide
   * contraste, nombres accesibles, jerarquia y orden de foco, y ninguna de esas
   * cosas se rompe porque un ancho, un espaciado o una alineacion cambien. Nadie
   * miraba la maquetacion, y esto es lo que la mira.
   *
   * `maxDiffPixelRatio` en cinco milesimas y no en cero porque el suavizado de
   * los bordes de las letras puede mover un punado de pixeles entre dos maquinas
   * sin que nada este mal. Un descuadre de verdad mueve ordenes de magnitud mas
   * que eso: desplazar una columna repinta media pantalla. El umbral separa las
   * dos cosas con holgura, y si algun dia hay que subirlo, es senal de que hay
   * que regenerar las referencias, no de que el umbral sea corto.
   */
  expect: {
    toHaveScreenshot: {
      animations: 'disabled',
      caret: 'hide',
      maxDiffPixelRatio: 0.005,
    },
  },

  /**
   * El sistema operativo va en la ruta, y es a proposito.
   *
   * Las referencias se generan en el contenedor oficial de Playwright, que es
   * Linux, porque es donde corre la canalizacion. Si alguien lanza estas pruebas
   * desde Windows o macOS, con el sistema en la ruta la prueba dice «falta la
   * referencia» en vez de comparar contra una que no le corresponde y pintar de
   * rojo un cambio que no existe. `docs/ui/regresion-visual.md` cuenta como se
   * generan.
   */
  snapshotPathTemplate: '{testDir}/__capturas__/{platform}/{arg}{ext}',

  webServer: {
    /**
     * En integracion continua no se vuelve a construir: el flujo ya lo hizo en
     * su propio paso, y repetirlo aqui pagaba el doble y ademas lo hacia contra
     * el reloj de este `timeout`. Es lo que agotaba los 180 segundos y ponia en
     * rojo las pruebas de extremo a extremo de main sin que fallara ninguna.
     *
     * En local si se construye, porque nadie quiere acordarse de compilar antes
     * de lanzar las pruebas.
     */
    command: process.env['CI']
      ? 'node dist/sendik/server/server.mjs'
      : 'npm run build && node dist/sendik/server/server.mjs',
    url: BASE_URL,
    reuseExistingServer: !process.env['CI'],
    // Una construccion en frio pasa de los tres minutos, y el primer renderizado
    // del servidor tarda lo suyo. Con el margen anterior fallaba por tiempo justo
    // cuando mas falta hace: la primera ejecucion en una maquina limpia.
    timeout: 420_000,
    env: {
      PORT: String(PORT),
      NG_ALLOWED_HOSTS: 'localhost',
      /**
       * Ninguna prueba de esta carpeta llama a la API. La variable existe porque
       * el servidor no arranca sin ella, que es justo lo que se quiere.
       *
       * <strong>Lo que no puede hacer es apuntar a este mismo servidor.</strong>
       * Antes valia `${BASE_URL}/api/v1`, y entonces cada renderizado pedia a su
       * propia direccion: la peticion volvia a entrar al mismo proceso, que
       * renderizaba otra vez, y el primer render pasaba de dos minutos sin
       * responder. Medido: 0,45s contra mas de 120s. Es la causa de que las
       * pruebas de extremo a extremo llevaran cayendo por tiempo desde siempre.
       *
       * El puerto 9 es el de descarte: no hay nada escuchando, asi que cualquier
       * llamada falla en el acto en vez de colgarse.
       */
      API_BASE_URL: process.env['API_BASE_URL'] ?? 'http://127.0.0.1:9/api/v1',
      // El pie las muestra y ninguna es obligatoria, asi que sin declararlas
      // aqui la prueba no distinguiria "no se pintan" de "no habia que pintar".
      COMPANY_NAME: 'Sendik S.A.S.',
      COMPANY_TAX_ID: '000000000-0',
      COMPANY_ADDRESS: 'Medellin, Colombia',
      SUPPORT_EMAIL: 'soporte@example.test',
    },
  },
});
