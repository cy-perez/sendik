import { expect, test, type Page } from '@playwright/test';

import { tomarUnaFoto } from './camara';
import { enlacesVistos, esperarEnlace, rutaRelativa } from './correo-de-consola';

/**
 * El recorrido de verificacion de vendedor por el navegador, con el backend y PostgreSQL
 * de verdad. HU-002, pruebas requeridas.
 *
 * <p>Se prueba por la interfaz y no llamando a la API, por lo mismo que el ciclo de vida
 * de una cuenta: lo que hay que demostrar es que las dos mitades encajan. Las pruebas de
 * componente usan HTTP simulado y las de backend usan MockMvc o Testcontainers; ninguna de
 * las dos ve un campo renombrado en un DTO, un codigo de error que el frontend no traduce
 * o un `multipart` que el servidor recibe como texto.
 *
 * <p><strong>Con camara, la falsa de Chromium.</strong> El dispositivo sintetico entrega un
 * patron con bordes marcados, asi que pasa la deteccion de desenfoque igual que una foto
 * nitida. Es la unica forma de recorrer los criterios 2 y 3 sin un humano delante, y va
 * configurada en `playwright.completo.config.ts`.
 *
 * <p><strong>Donde se detiene esta suite y por que.</strong> Llega hasta
 * `PENDING_REVIEW`. Aprobar exige un moderador, y el rol se otorga con una sentencia SQL a
 * mano porque no hay pantalla que lo haga —decision anotada en HU-002—; darle a esta suite
 * acceso a la base significaria agregarle al frontend un cliente de PostgreSQL. La cadena
 * hasta el sello la cubre `SellerVerificationJourneyTest`, en `bootstrap`, contra
 * PostgreSQL real.
 */

// El idioma lo resuelve el servidor por `Accept-Language`, asi que sin esto el HTML
// llega en ingles y ninguna etiqueta de abajo existe. Igual que en `cuentas.spec.ts`.
test.use({ locale: 'es-CO' });

const RUTA = '/verificacion-de-vendedor';

/** Un correo distinto por prueba, como en `cuentas.spec.ts`: la base no se limpia. */
function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

/**
 * Un documento distinto por prueba, de doce digitos.
 *
 * <p>Empieza por 20 para no cruzarse con la secuencia de las pruebas de backend, que usan
 * la misma base cuando se corren en la misma maquina.
 */
function cedulaNueva(): string {
  const sufijo = Math.floor(Math.random() * 10_000)
    .toString()
    .padStart(4, '0');

  return `20${Date.now().toString().slice(-6)}${sufijo}`;
}

/**
 * Registro, verificacion del correo y sesion puesta, que es el punto de partida del
 * criterio 1.
 *
 * <p>Se espera al nombre en la cabecera y no solo a que la pagina cargue: la cookie de
 * refresco llega con la respuesta de la verificacion, y navegando en ese hueco la pagina
 * siguiente sale anonima. Es el mismo cuidado que documenta `cuentas.spec.ts`.
 */
async function registrarseYEntrar(page: Page, correo: string): Promise<void> {
  const yaVistos = enlacesVistos('/verificar-correo');

  await page.goto('/registro');

  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Nombre').fill('Ana María');
  await page.getByLabel('Contraseña').fill('una-contrasena-larga-de-verdad');
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
  await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();
}

/** Los datos del documento, con sus dos fotos. Criterio 2. */
async function llenarElDocumento(page: Page, tipo: string, cedula: string): Promise<void> {
  await page.getByLabel('Tipo de documento').selectOption(tipo);
  await page.getByLabel('Número del documento').fill(cedula);
  await page.getByLabel('Nombre completo, como aparece en el documento').fill('Ana Maria Garcia');

  await tomarUnaFoto(page);
  await tomarUnaFoto(page);

  await page.getByRole('button', { name: 'Guardar el documento' }).click();
  await expect(page.getByText('Guardamos tu documento.')).toBeVisible();
}

test.describe('verificacion de vendedor', () => {
  /**
   * El camino principal completo. Va en una sola prueba y no en cuatro porque cada paso
   * necesita el estado del anterior, y trocearlo obligaria a recrear ese estado llamando a
   * la API, que es justo lo que esta prueba no debe hacer.
   */
  test('recorrido completo hasta quedar en revision', async ({ page }) => {
    const cedula = cedulaNueva();
    await registrarseYEntrar(page, correoNuevo('vendedora'));

    await page.goto(RUTA);

    // Criterio 6: el plazo prometido sale de configuracion, no del codigo, y es el mismo
    // numero que promete el correo del backend.
    await expect(page.getByText('Revisamos tu solicitud en máximo 2 días hábiles.')).toBeVisible();
    await page.getByRole('button', { name: 'Empezar' }).click();

    // Los tres pasos, ninguno entregado.
    await expect(page.getByText('Te falta algo por entregar.')).toBeVisible();
    await expect(page.locator('.paso .estado', { hasText: 'Falta' })).toHaveCount(3);

    // --- Criterios 2 y 3: el documento por las dos caras y la selfie --------
    await llenarElDocumento(page, 'CC', cedula);

    await tomarUnaFoto(page);
    await page.getByRole('button', { name: 'Guardar la foto' }).click();
    await expect(page.getByText('Guardamos tu foto.')).toBeVisible();

    // --- Criterio 4: donde recibe el dinero ---------------------------------
    // La entidad sale del catalogo que siembra la migracion V7, no de una lista escrita
    // en el frontend: si el catalogo no llego, este `selectOption` no encuentra la opcion.
    await page.getByLabel('Entidad').selectOption('bancolombia');
    await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
    await page.getByLabel('Número de cuenta').fill('91500123456');
    await page.getByLabel('Nombre del titular').fill('Ana Maria Garcia');
    await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
    await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

    // --- Criterio 6: enviar a revision --------------------------------------
    await expect(page.locator('.paso .estado', { hasText: 'Listo' })).toHaveCount(3);
    await page.getByRole('button', { name: 'Enviar para revisión' }).click();

    await expect(page.getByText('Estamos revisando tu solicitud.')).toBeVisible();

    // Enviada, no se ofrece enviarla otra vez ni tocar nada: RN-059 no deja salir de
    // PENDING_REVIEW por voluntad de la persona, y la pantalla no ofrece lo que el
    // servidor va a negar.
    await expect(page.getByRole('button', { name: 'Enviar para revisión' })).toBeHidden();
    await expect(page.getByRole('button', { name: 'Abrir la cámara' })).toHaveCount(0);
    await expect(page.getByLabel('Número de cuenta')).toHaveCount(0);
  });

  /**
   * El caso borde que pide la historia: se sale a la mitad y se retoma donde iba. Se
   * comprueba recargando de verdad, porque el avance tiene que venir del servidor y no de
   * una senal que sigue viva en memoria.
   */
  test('el avance sobrevive a recargar la pagina', async ({ page }) => {
    await registrarseYEntrar(page, correoNuevo('retoma'));

    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Empezar' }).click();

    await page.getByLabel('Entidad').selectOption('bancolombia');
    await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
    await page.getByLabel('Número de cuenta').fill('91500123456');
    await page.getByLabel('Nombre del titular').fill('Ana Maria Garcia');
    await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
    await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

    await page.reload();

    // El paso bancario aparece hecho, los otros dos siguen pendientes, y la pantalla dice
    // que falta en vez de ofrecer un envio que el servidor rechazaria.
    const pasos = page.locator('.paso');
    await expect(pasos.nth(2)).toContainText('Listo');
    await expect(pasos.nth(0)).toContainText('Falta');
    await expect(pasos.nth(1)).toContainText('Falta');
    await expect(page.getByText('Completa los tres pasos para poder enviar.')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Enviar para revisión' })).toHaveCount(0);
  });

  /**
   * Nequi es una billetera y no tiene cuenta de ahorros. Ofrecerla seria ofrecer algo que
   * no existe, y el desembolso de la Fase 3 fallaria contra un tipo de cuenta que la
   * entidad no reconoce. Que sea billetera lo dice el catalogo del servidor, asi que esto
   * comprueba que ese dato cruza entero.
   */
  test('una billetera solo ofrece deposito electronico', async ({ page }) => {
    await registrarseYEntrar(page, correoNuevo('billetera'));

    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Empezar' }).click();

    await page.getByLabel('Entidad').selectOption('nequi');

    await expect(
      page.getByText('Las billeteras solo reciben en depósito electrónico.'),
    ).toBeVisible();
    await expect(page.getByLabel('Tipo de cuenta')).toHaveValue('ELECTRONIC_DEPOSIT');
  });

  /**
   * RN-012 de punta a punta. El servidor normaliza acentos y espacios y es quien decide;
   * lo que se comprueba aqui es que su negativa llegue traducida a la pantalla y no como
   * un codigo, que es exactamente lo que ninguna suite de una sola mitad puede ver.
   */
  test('rechaza una cuenta a nombre de otra persona, con el mensaje traducido', async ({
    page,
  }) => {
    await registrarseYEntrar(page, correoNuevo('titular-distinto'));

    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Empezar' }).click();

    await llenarElDocumento(page, 'CE', cedulaNueva());

    await page.getByLabel('Entidad').selectOption('bancolombia');
    await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
    await page.getByLabel('Número de cuenta').fill('91500123456');
    await page.getByLabel('Nombre del titular').fill('Pedro Ramirez');
    await page.getByRole('button', { name: 'Guardar la cuenta' }).click();

    await expect(
      page.getByText('El titular de la cuenta tiene que ser el mismo nombre del documento.'),
    ).toBeVisible();
    await expect(page.getByText('Guardamos tu cuenta.')).toHaveCount(0);
  });

  /**
   * Criterio 11 sobre la respuesta de verdad y no sobre una simulada: en lo que llega al
   * navegador estan los cuatro ultimos digitos y no el numero completo, y no aparece
   * ninguna clave del almacen reservado.
   *
   * <p>Se recarga antes de mirar para que lo que se lea venga del servidor y no del
   * formulario que la persona acaba de llenar.
   */
  test('cumple el criterio 11 en lo que llega al navegador', async ({ page }) => {
    const cedula = cedulaNueva();
    await registrarseYEntrar(page, correoNuevo('criterio-once'));

    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Empezar' }).click();

    await llenarElDocumento(page, 'PPT', cedula);
    await page.reload();

    await expect(page.getByText(`····${cedula.slice(-4)}`)).toBeVisible();

    const html = await page.content();

    expect(html).not.toContain(cedula);
    // Las carpetas del almacen reservado: `documentos/` y `selfies/`. Una clave filtrada
    // no da acceso por si sola, pero decir donde vive la cedula de alguien no es
    // informacion que la pantalla necesite.
    expect(html).not.toContain('documentos/');
    expect(html).not.toContain('selfies/');
  });
});
