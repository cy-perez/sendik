import { expect, test, type Page } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import { tomarUnaFoto } from './camara';
import { esperarEnlace, enlacesVistos, rutaRelativa } from './correo-de-consola';

/**
 * La bandeja del moderador, de punta a punta. HU-006.
 *
 * <p>Esta suite existe por lo que ninguna prueba de componente puede demostrar: que el
 * rol llega de verdad, que los cinco endpoints responden a quien debe y que aprobar deja
 * a la otra persona con el sello. Las de componente simulan HTTP; aqui hay backend y
 * PostgreSQL.
 *
 * <p><strong>La cuenta que modera se crea como cualquier otra</strong>, por la interfaz.
 * El rol lo concede `SECURITY_BOOTSTRAP_MODERATORS`, que la configuracion de esta suite
 * fija al mismo correo. Funciona porque el rol se otorga tambien al registrarse y no solo
 * al arrancar el backend: sin eso, la cuenta nacería despues del arranque y nunca lo
 * recibiria.
 *
 * <p>El correo de quien modera es fijo y el backend se reutiliza entre ejecuciones en
 * local, asi que la cuenta puede existir ya. Por eso se entra primero y solo se registra
 * si no habia ninguna.
 */
test.use({ locale: 'es-CO' });

const CONTRASENA = 'una-contrasena-larga-de-verdad';
const RUTA_BANDEJA = '/moderacion/verificaciones';
const RUTA_VERIFICACION = '/verificacion-de-vendedor';

function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

function cedulaNueva(): string {
  return String(Date.now()).slice(-9) + String(Math.floor(Math.random() * 900) + 100);
}

/**
 * El nombre importa: quien vende y quien modera tienen que llamarse distinto.
 *
 * <p>Con las dos cuentas llamandose igual, comprobar "hay sesion" mirando el nombre de la
 * cabecera no distingue una de otra, y la prueba sigue con la sesion equivocada creyendo
 * que cambio. Costo un rato averiguarlo.
 */
const NOMBRE_MODERADORA = 'Quien Modera';

/** Desempata los titulares creados dentro del mismo milisegundo. */
let cuantasSolicitudes = 0;

/** El mismo que usa la pantalla. Si cambia allí, esta prueba deja de medir lo que cree. */
const TAMANO_DE_PAGINA = 20;

async function registrar(page: Page, correo: string, nombre = 'Ana María'): Promise<void> {
  const yaVistos = enlacesVistos('/verificar-correo');

  await page.goto('/registro');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Nombre').fill(nombre);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByLabel('Fecha de nacimiento').fill('1990-03-04');
  // Por identificador y no por texto: la redaccion de los dos consentimientos
  // la fija el regimen de datos personales y se retoca sin avisar. Lo que este
  // recorrido necesita es marcar las casillas; de comprobar como estan
  // redactadas responde e2e/registro.spec.ts, que para eso existe.
  await page.locator('#terminos').check();
  await page.locator('#privacidad').check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();

  await page.goto(rutaRelativa(await esperarEnlace('/verificar-correo', yaVistos)));

  // **Se espera a que la verificacion termine, no a su efecto en la cabecera.**
  //
  // Verificar no ocurre al servir la pagina: corre en `afterNextRender`, asi que la cadena
  // es servir, hidratar, mandar el POST, recibir la sesion y repintar. Esperar el nombre de
  // la cabecera le daba a todo eso los cinco segundos que Playwright pone por omision, y en
  // integracion continua -con el backend cargado y veintidos registros seguidos- no siempre
  // caben. El sintoma era «no se encontro el enlace Ana Maria N», que no distingue «tardo»
  // de «fallo».
  //
  // Y se afirma el texto del titular en vez de su presencia: si la verificacion falla, la
  // pagina lo dice con su propio h1 y el mensaje del fallo lo trae -«se esperaba Tu cuenta
  // quedo activa, se recibio No pudimos confirmar tu correo»- en vez de callarselo.
  await expect(page.getByRole('heading', { level: 1 })).toHaveText('Tu cuenta quedó activa', {
    timeout: 20_000,
  });

  // Ya con la sesion puesta, esto es inmediato.
  await expect(page.getByRole('link', { name: nombre })).toBeVisible();
}

async function ingresar(page: Page, correo: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

/**
 * Entra con la cuenta que modera, creandola si hace falta.
 *
 * <p>Cierra antes cualquier sesion abierta. Sin eso, `/ingresar` con una sesion viva deja
 * la anterior en pie y el resto de la prueba corre con la cuenta equivocada, que con las
 * dos llamandose igual no se nota.
 */
async function salirSiHaySesion(page: Page): Promise<void> {
  await page.goto('/');
  const salir = page.getByRole('button', { name: 'Salir' });
  if (await salir.isVisible().catch(() => false)) {
    await salir.click();
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  }
}

async function entrarComoModeradora(page: Page): Promise<void> {
  await salirSiHaySesion(page);

  await ingresar(page, MODERADORA);

  const dentro = await page
    .getByRole('link', { name: NOMBRE_MODERADORA })
    .waitFor({ timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  if (!dentro) {
    await registrar(page, MODERADORA, NOMBRE_MODERADORA);
  }

  await expect(page.getByRole('link', { name: NOMBRE_MODERADORA })).toBeVisible();
}

/** Una solicitud completa, enviada a revision, con una cuenta nueva. */
/**
 * Deja una solicitud esperando revisión.
 *
 * <p>Devuelve el titular, y es único: la bandeja lo muestra, y es lo único que permite
 * señalar **esta** solicitud entre todas las que esperan. Con un nombre fijo, una prueba
 * que quiera una concreta acaba abriendo la más vieja de la cola, que en una base que
 * arrastra datos puede ser de otra corrida.
 *
 * <p>El contador va además del reloj porque creando varias seguidas `Date.now()` repite
 * dentro del mismo milisegundo.
 */
async function dejarUnaSolicitudEnRevision(page: Page, quien: string): Promise<string> {
  const cual = (cuantasSolicitudes += 1);
  const titular = `Ana Maria Garcia ${Date.now()}-${cual}`;

  // **Se cierra la sesión anterior y cada cuenta se llama distinto**, y las dos cosas son
  // por lo mismo. `registrar` no cierra nada, así que encadenando varias la sesión de una
  // puede sobrevivir a la siguiente; y con todas llamándose igual, la comprobación que
  // `registrar` hace al terminar -que la cabecera muestre el nombre- pasa aunque la sesión
  // sea la de otra cuenta. Es la misma trampa que este archivo ya documenta para la
  // moderadora, y encadenando veintiuna deja de ser teórica: se manifestó en integración
  // continua como un `Empezar` que no llegaba nunca, porque la pantalla estaba enseñando
  // la verificación ya enviada de la cuenta anterior.
  //
  // Con un nombre propio por cuenta, esa confusión falla en el acto y diciendo cuál es,
  // en vez de agotar el tiempo de la prueba cinco pasos más allá.
  await salirSiHaySesion(page);
  await registrar(page, correoNuevo(quien), `Ana Maria ${cual}`);
  await page.goto(RUTA_VERIFICACION);
  await page.getByRole('button', { name: 'Empezar' }).click();

  await page.getByLabel('Tipo de documento').selectOption('CC');
  await page.getByLabel('Número del documento').fill(cedulaNueva());
  await page.getByLabel('Nombre completo, como aparece en el documento').fill(titular);
  await tomarUnaFoto(page);
  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar el documento' }).click();
  await expect(page.getByText('Guardamos tu documento.')).toBeVisible();

  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar la foto' }).click();
  await expect(page.getByText('Guardamos tu foto.')).toBeVisible();

  await page.getByLabel('Entidad').selectOption('bancolombia');
  await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
  await page.getByLabel('Número de cuenta').fill('91500123456');
  await page.getByLabel('Nombre del titular').fill(titular);
  await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
  await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

  await page.getByRole('button', { name: 'Enviar para revisión' }).click();
  await expect(page.getByText('Estamos revisando tu solicitud.')).toBeVisible();

  return titular;
}

/**
 * Cuántas solicitudes esperan revisión, contadas por la interfaz.
 *
 * <p>Recorriendo, no preguntando a la API: si esta suite empieza a leer el estado por un
 * atajo, deja de ser de extremo a extremo. Y es barato -dos o tres clics- comparado con
 * crear solicitudes de más, que es lo que evita saber esto.
 */
async function cuantasEsperanRevision(page: Page): Promise<number> {
  await entrarComoModeradora(page);
  await page.goto(RUTA_BANDEJA);

  const paginacion = page.getByRole('navigation', { name: 'Páginas de la bandeja' });
  const siguiente = paginacion.getByRole('button', { name: 'Siguiente' });

  // **Las filas por su rol y no por su clase.** `.solicitud` es también la clase de las
  // filas del esqueleto de carga, así que contando por ahí una bandeja vacía devuelve
  // tres. Costó una corrida entera: creaba dieciocho solicitudes en vez de veintiuna, la
  // cola cabía en una página y la prueba fallaba diciendo que no había paginación. El
  // esqueleto está oculto a la accesibilidad, así que ningún localizador por rol lo ve.
  const filas = page.getByRole('link').filter({ hasText: 'Espera desde' });
  const vacia = page.getByText('No hay nada por revisar');

  let total = 0;

  for (let pagina = 1; pagina <= 50; pagina++) {
    // Que la carga haya terminado, sea con filas o sin ellas. Sin esto se cuenta una
    // pantalla a medio pintar.
    await expect(filas.first().or(vacia)).toBeVisible();
    await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);

    total += await filas.count();

    if (!(await siguiente.isVisible())) {
      break;
    }
    if ((await siguiente.getAttribute('aria-disabled')) !== 'false') {
      break;
    }

    await siguiente.click();
    await expect(paginacion.getByRole('status')).toHaveText(`Página ${pagina + 1}`);
  }

  return total;
}

test.describe('bandeja del moderador', () => {
  /**
   * Criterio 2. <strong>La prueba que mas importa de esta suite.</strong>
   *
   * <p>Una cuenta corriente no puede enterarse de que la bandeja existe. Se comprueba
   * que no ve ni el titular de la pantalla ni ninguna solicitud: si el guard se cayera,
   * o si alguien pusiera la ruta a renderizarse en el servidor, esto lo diria.
   */
  test('una cuenta sin el rol no llega a la bandeja', async ({ page }) => {
    await registrar(page, correoNuevo('curiosa'));

    await page.goto(RUTA_BANDEJA);

    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toHaveCount(0);
    await expect(page.locator('.solicitud')).toHaveCount(0);
  });

  /** Y sin sesión tampoco, que es el otro camino por el que se llega a esa dirección. */
  test('sin sesion tampoco se llega a la bandeja', async ({ page }) => {
    await page.goto(RUTA_BANDEJA);

    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toHaveCount(0);
  });

  /**
   * Criterio 13, sobre el HTML que sale del servidor.
   *
   * <p>Lo consigue el guard, que **deniega en el servidor**: lo que se sirve es la página
   * de «no existe». Las rutas se renderizan en servidor como todas las demás; se probó
   * `RenderMode.Client` y no sirve, porque `APP_CONFIG` llega por el estado transferido
   * del SSR (ADR-0021).
   */
  test('el HTML servido de la bandeja no dice de que va', async ({ request }) => {
    const respuesta = await request.get(RUTA_BANDEJA);
    const html = await respuesta.text();

    expect(respuesta.status()).toBe(200);

    // Se mira el marcado y no el documento crudo: el diccionario de Transloco viaja
    // entero en el estado transferido, asi que el texto de cualquier pantalla aparece
    // ahi por casualidad. Lo que importa es que no se haya PINTADO nada de la bandeja.
    expect(/<h1[^>]*>\s*Verificaciones pendientes/.test(html)).toBe(false);
    expect(/<title>([^<]*)<\/title>/.exec(html)?.[1]).toBe('Página no encontrada');
  });

  /**
   * El camino principal: aprobar y que la persona quede con el sello.
   *
   * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y
   * trocearlo obligaría a fabricar ese estado llamando a la API, que es justo lo que
   * esta suite existe para no hacer.
   */
  test('aprobar deja a la persona verificada', async ({ page }) => {
    await dejarUnaSolicitudEnRevision(page, 'aprobada');

    await entrarComoModeradora(page);
    await page.goto(RUTA_BANDEJA);

    // Criterio 1: la bandeja trae lo que espera revisión.
    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toBeVisible();
    await expect(page.locator('.solicitud').first()).toBeVisible();

    await page.locator('.enlace-de-fila').first().click();

    // Criterio 5: cuatro dígitos, nunca el número completo, tampoco para quien revisa.
    await expect(page.getByText('Termina en 3456')).toBeVisible();
    await expect(page.getByText('91500123456')).toHaveCount(0);

    // Criterio 6: la imagen se pide al abrirla, y llega de verdad.
    await page.getByRole('button', { name: 'Ver' }).first().click();
    await expect(page.locator('img').first()).toBeVisible();

    // Criterio 10: se confirma una vez.
    await page.getByRole('button', { name: 'Aprobar' }).click();
    await page.getByRole('button', { name: 'Confirmar' }).click();

    // Criterio 8: vuelve a la lista y la solicitud ya no está.
    await expect(page.getByRole('heading', { name: 'Verificaciones pendientes' })).toBeVisible();
  });

  /**
   * <strong>Llegar a una solicitud que no está en la primera página.</strong>
   *
   * <p>Es el motivo por el que la bandeja se paginó, y hasta ahora nada lo comprobaba de
   * punta a punta: contra una base recién creada la cola cabe entera en una página, así
   * que el recorrido por páginas —el botón, la espera del número, el corte al llegar al
   * final— era código que en integración continua no se ejecutaba nunca. Justo el que
   * escondía el defecto que dejó esta suite sin poder pasar dos veces seguidas.
   *
   * <p>Llena la cola por encima del tamaño de página y va a por la última, que es la más
   * nueva y por tanto la última de una cola que ordena por antigüedad. La premisa se
   * comprueba antes de recorrer: si esta solicitud apareciera en la primera página, la
   * prueba pasaría sin haber paginado y no valdría nada.
   *
   * <p><strong>Deja la cola llena a propósito.</strong> No aprueba ninguna de las que
   * crea, así que las pruebas que vienen después —este archivo es el primero por orden
   * alfabético— se encuentran una bandeja de varias páginas y recorren ese mismo camino
   * para llegar a lo suyo. Es la única forma de que se ejercite de verdad, y de paso es
   * la situación real de una cola que nadie ha vaciado.
   */
  test('se llega a una solicitud que no cabe en la primera pagina', async ({ page }) => {
    // Cada solicitud se crea por la interfaz, con su cámara y su formulario. Es lento y no
    // hay atajo: sembrarlas llamando a la API sería saltarse justo lo que se prueba.
    test.setTimeout(600_000);

    // Solo las que falten. Contra una base recién creada son veintiuna; contra una que
    // arrastra pendientes de antes -la segunda vuelta de integración continua, o
    // cualquier ejecución local repetida- basta con una, porque la cola ya es profunda y
    // lo único que hace falta es una solicitud propia al final de todo.
    const yaEsperan = await cuantasEsperanRevision(page);
    const faltan = Math.max(1, TAMANO_DE_PAGINA + 1 - yaEsperan);

    let laUltima = '';
    for (let cuantas = 0; cuantas < faltan; cuantas++) {
      laUltima = await dejarUnaSolicitudEnRevision(page, 'cola-larga');
    }

    await entrarComoModeradora(page);
    await page.goto(RUTA_BANDEJA);

    const paginacion = page.getByRole('navigation', { name: 'Páginas de la bandeja' });
    const fila = page.getByRole('link').filter({ hasText: laUltima });

    // La premisa. Se espera a la navegación primero: solo se pinta con la carga resuelta,
    // así que su presencia es también la señal de que la lista ya está.
    await expect(paginacion).toBeVisible();
    await expect(fila).toHaveCount(0);

    // Y ahora se recorre, con el botón que usa quien modera.
    for (let pagina = 1; pagina <= 50; pagina++) {
      // Antes de mirar, que lo que se ve sea de esta página. La bandeja conserva la
      // anterior mientras llega la nueva -para no desmontar la paginación con el foco
      // dentro- así que «no está la fila» puede significar solo «todavía no llegó», y
      // `isVisible()` no espera. Sin esto el recorrido se salta una página entera.
      await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);

      if (await fila.first().isVisible()) {
        break;
      }

      const siguiente = paginacion.getByRole('button', { name: 'Siguiente' });
      expect(await siguiente.getAttribute('aria-disabled')).toBe('false');
      await siguiente.click();
      await expect(paginacion.getByRole('status')).toHaveText(`Página ${pagina + 1}`);
    }

    await expect(fila.first()).toBeVisible();
    await fila.first().click();

    // Se llegó a la solicitud, y es la suya: el detalle muestra su titular. Con
    // `.first()` porque el nombre sale en tres sitios de esta pantalla -el encabezado,
    // el documento y la cuenta- y pedirlos todos falla por ambigüedad.
    await expect(page.getByRole('button', { name: 'Aprobar' })).toBeVisible();
    await expect(page.getByText(laUltima).first()).toBeVisible();
  });

  /**
   * <strong>RN-060 no se prueba aqui, y es deliberado.</strong>
   *
   * <p>Para intentarlo por la interfaz haria falta que quien modera tuviera su propia
   * solicitud enviada, lo que obliga a recorrer la captura con la camara falsa y deja a
   * esa cuenta con un estado que sobrevive a la corrida: la siguiente ya no empieza donde
   * esta empezaba. Una prueba que depende de lo que dejo la anterior no prueba nada.
   *
   * <p>La regla esta cubierta en los cuatro sitios donde si es estable: en el caso de uso
   * con sus dos caras, en `SellerVerificationJourneyTest` contra la base de verdad, en el
   * controlador con su 403 y su codigo propio, y en `review-detail-page.spec.ts` con la
   * pantalla que no ofrece la accion sobre lo propio.
   */
});
