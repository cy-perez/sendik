import { readFile } from 'node:fs/promises';

import { expect, test, type Page } from '@playwright/test';

import { enlacesVistos, esperarEnlace, rutaRelativa } from './correo-de-consola';
import { pngDe } from './png';

/**
 * El ciclo de vida de una cuenta, con backend y base de datos de verdad.
 *
 * <p>Es lo que pedian `docs/arquitectura/pruebas.md` y las pruebas requeridas de
 * HU-001 y no existia: registro, verificacion, ingreso, cierre y recuperacion de
 * extremo a extremo. Estaban probados **por mitades** —MockMvc en `presentation`,
 * Testcontainers en `bootstrap`, componentes con HTTP simulado en el frontend— y
 * ninguna de esas suites puede ver un contrato roto entre las dos: un nombre de
 * campo que cambia en un DTO, un codigo de error que el frontend no traduce, una
 * cookie con un atributo que el navegador rechaza. Todas pasan verdes y la
 * pantalla falla.
 *
 * <p>Se prueba por la interfaz, no llamando a la API, porque lo que se quiere
 * demostrar es que las dos mitades encajan. Una prueba que llamara a la API
 * directamente volveria a probar el backend, que ya esta probado.
 *
 * <p>El correo se recupera del registro del backend: ver `correo-de-consola.ts`.
 */
test.use({ locale: 'es-CO' });

/** La misma que usa playwright.completo.config.ts para el servidor de renderizado. */
const BASE_URL = 'http://localhost:4174';

const CONTRASENA = 'una-contrasena-larga-de-verdad';
const CONTRASENA_NUEVA = 'otra-contrasena-igual-de-larga';

/**
 * Un correo distinto por prueba. La base no se limpia entre pruebas a proposito:
 * limpiarla obligaria a la suite a conocer el esquema, y con correos unicos no
 * hace falta. Ademas deja el rastro completo si algo falla.
 */
function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

async function registrarse(page: Page, correo: string): Promise<void> {
  await page.goto('/registro');

  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Nombre').fill('Ana María');
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByLabel('Fecha de nacimiento').fill('1990-03-04');
  // Por identificador y no por texto: la redaccion de los dos consentimientos
  // la fija el regimen de datos personales y se retoca sin avisar. Lo que este
  // recorrido necesita es marcar las casillas; de comprobar como estan
  // redactadas responde e2e/registro.spec.ts, que para eso existe.
  await page.locator('#terminos').check();
  await page.locator('#privacidad').check();

  await page.getByRole('button', { name: 'Crear cuenta' }).click();

  // La respuesta es la misma exista o no el correo (criterio 2): siempre esta
  // pantalla, que no dice si la cuenta se creo.
  await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();
}

/**
 * Abre el enlace de verificacion y **espera a que la sesion este puesta**.
 *
 * <p>Lo segundo no es adorno. `goto` vuelve cuando el documento cargo, pero la
 * verificacion es una peticion posterior y la cookie de refresco llega con su
 * respuesta. Navegando en ese hueco, la pagina siguiente carga sin cookie y sale
 * anonima: el sintoma es una prueba que falla una vez de cada tantas, por una
 * causa que no tiene nada que ver con lo que comprueba. El nombre en la cabecera
 * es la senal de que la sesion ya esta.
 */
async function verificarElCorreo(page: Page, enlace: string): Promise<void> {
  await page.goto(rutaRelativa(enlace));
  await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();
}

async function ingresar(page: Page, correo: string, contrasena: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(contrasena);
  await page.getByRole('button', { name: 'Entrar' }).click();
}

test.describe('ciclo de vida de una cuenta', () => {
  /**
   * El camino principal completo. Va en una sola prueba y no en cinco porque cada
   * paso necesita el estado del anterior, y trocearlo obligaria a recrear ese
   * estado llamando a la API, que es justo lo que esta prueba no debe hacer.
   */
  test('registro, verificacion del correo y sesion abierta', async ({ page }) => {
    const correo = correoNuevo('ciclo');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);

    // Criterio 9: al verificar, la cuenta queda activa y la persona entra
    // directamente, sin escribir la contrasena otra vez.
    const enlace = await esperarEnlace('/verificar-correo', yaVistos);
    await page.goto(rutaRelativa(enlace));

    await expect(page.getByRole('heading', { name: 'Tu cuenta quedó activa' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();
  });

  /** Criterio 7: el enlace sirve una sola vez. */
  test('el enlace de verificacion no sirve dos veces', async ({ page }) => {
    const correo = correoNuevo('unavez');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    const enlace = await esperarEnlace('/verificar-correo', yaVistos);

    await page.goto(rutaRelativa(enlace));
    await expect(page.getByRole('heading', { name: 'Tu cuenta quedó activa' })).toBeVisible();

    // Segunda vez, en una sesion limpia para que no sea la sesion la que decide.
    await page.context().clearCookies();
    await page.goto(rutaRelativa(enlace));

    await expect(
      page.getByRole('heading', { name: 'No pudimos confirmar tu correo' }),
    ).toBeVisible();
  });

  test('entrar y salir con la sesion que emite el backend', async ({ page }) => {
    const correo = correoNuevo('sesion');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.getByRole('button', { name: 'Salir' }).click();
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();

    await ingresar(page, correo, CONTRASENA);
    await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();
  });

  /**
   * La sesion sobrevive a una recarga completa. Es lo que demuestra que la cookie
   * de refresco viaja con los atributos correctos y que el token de acceso se
   * recupera: si `SameSite` o `Path` estuvieran mal, el navegador no la mandaria y
   * esto seria lo unico que lo notaria.
   */
  test('la sesion sigue abierta despues de recargar', async ({ page }) => {
    const correo = correoNuevo('recarga');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.goto('/mi-cuenta');
    await page.reload();

    // `level: 1` porque "Cerrar mi cuenta" es tambien un encabezado que casa con
    // el nombre. Sin acotarlo, el localizador encuentra dos y falla por ambiguo.
    await expect(page.getByRole('heading', { name: 'Mi cuenta', level: 1 })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();
  });

  test('recuperar la contrasena y entrar con la nueva', async ({ page }) => {
    const correo = correoNuevo('recuperar');
    const vistosVerificacion = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', vistosVerificacion));
    await page.getByRole('button', { name: 'Salir' }).click();

    const vistosReset = enlacesVistos('/restablecer-contrasena');

    await page.goto('/recuperar-contrasena');
    await page.getByLabel('Correo electrónico').fill(correo);
    await page.getByRole('button', { name: 'Enviar el enlace' }).click();
    await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();

    await page.goto(rutaRelativa(await esperarEnlace('/restablecer-contrasena', vistosReset)));
    await page.getByLabel('Contraseña nueva').fill(CONTRASENA_NUEVA);
    await page.getByRole('button', { name: 'Cambiar la contraseña' }).click();

    await expect(page.getByRole('heading', { name: 'Listo, ya cambió' })).toBeVisible();

    // La nueva sirve.
    await ingresar(page, correo, CONTRASENA_NUEVA);
    await expect(page.getByRole('link', { name: 'Ana María' })).toBeVisible();

    // Y la vieja ya no. Criterio 20: cambiarla cierra todo y la anterior muere.
    await page.getByRole('button', { name: 'Salir' }).click();
    await ingresar(page, correo, CONTRASENA);
    await expect(page.getByRole('link', { name: 'Ana María' })).toHaveCount(0);
  });

  /**
   * Criterio 22 y el defecto que esta prueba habria atrapado: el archivo tiene que
   * llevar **todos** los datos personales, ciudad y telefono incluidos. Se
   * comprueba sobre el archivo que descarga el navegador, que es el que la persona
   * recibe.
   */
  test('la descarga de datos lleva todo lo que se guarda de la persona', async ({ page }) => {
    const correo = correoNuevo('descarga');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    // Primero se pone ciudad y telefono, que es lo que faltaba en el archivo.
    await page.goto('/mi-cuenta');
    await page.getByLabel('Ciudad').fill('Medellín');
    await page.getByLabel('Teléfono').fill('3001234567');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Guardado.')).toBeVisible();

    const descarga = page.waitForEvent('download');
    await page.getByRole('button', { name: 'Descargar' }).click();
    const archivo = await descarga;

    const contenido = JSON.parse(await readFile(await archivo.path(), 'utf8'));

    expect(contenido.cuenta.correo).toBe(correo);
    expect(contenido.cuenta.nombre).toBe('Ana María');
    expect(contenido.cuenta.ciudad).toBe('Medellín');
    expect(contenido.cuenta.telefono).toBe('3001234567');
    // La evidencia de los dos consentimientos, cada uno con su version.
    expect(contenido.consentimientos).toHaveLength(2);
    expect(contenido.consentimientos.map((uno: { documento: string }) => uno.documento)).toEqual(
      expect.arrayContaining(['TERMS', 'PRIVACY']),
    );
    // Ni hashes ni IP: son secretos del sistema, no datos de la persona.
    expect(JSON.stringify(contenido)).not.toContain('hash');
  });

  /** Criterio 23: cerrar la cuenta exige confirmacion escrita y deja de poder entrar. */
  test('cerrar la cuenta y no poder volver a entrar', async ({ page }) => {
    const correo = correoNuevo('cierre');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.goto('/mi-cuenta');
    await page.getByRole('button', { name: 'Quiero cerrar mi cuenta' }).click();
    await page.getByLabel('Escribe tu correo para confirmar').fill(correo);
    await page.getByRole('button', { name: 'Cerrar mi cuenta definitivamente' }).click();

    // Al cerrarse, la pantalla lleva a la portada y la sesion desaparece: el
    // mensaje de despedida de la propia pantalla no se llega a ver, porque ya se
    // navego. Lo que importa es que la sesion se cerro con la cuenta.
    await expect(page).toHaveURL(new RegExp(`^${BASE_URL}/?$`));
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();

    // Y no se puede volver a entrar: la cuenta ya no existe.
    await page.context().clearCookies();
    await ingresar(page, correo, CONTRASENA);

    await expect(page.getByRole('link', { name: 'Ana María' })).toHaveCount(0);
    await expect(page.locator('[role="alert"]')).toBeVisible();
  });

  /**
   * Criterio 2: registrarse con un correo que ya existe responde exactamente igual
   * que si no existiera. Es lo que evita que el formulario sirva para averiguar
   * quien tiene cuenta, y solo se puede comprobar con el backend de verdad.
   */
  test('registrarse con un correo que ya existe responde igual', async ({ page }) => {
    const correo = correoNuevo('repetido');

    await registrarse(page, correo);
    // Misma pantalla, sin ninguna pista de que ya existe.
    await registrarse(page, correo);

    await expect(page.locator('[role="alert"]')).toHaveCount(0);
  });
  /**
   * Criterio 21 completo: la foto de perfil, de punta a punta.
   *
   * <p>Es la unica prueba que recorre lo que ADR-0018 promete de verdad. Se sube un
   * PNG hecho aqui mismo, el servidor lo decodifica, lo vuelve a codificar, lo
   * guarda con una clave opaca y devuelve una direccion; el navegador la pide y la
   * imagen llega. Ninguna prueba de componente puede ver eso: en todas ellas el
   * servidor es un simulacro.
   */
  test('subir la foto de perfil y volver a verla al recargar', async ({ page }) => {
    const correo = correoNuevo('foto');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.goto('/mi-cuenta');
    await expect(page.getByRole('heading', { name: 'Tu foto' })).toBeVisible();

    // Un PNG de verdad, generado en el navegador: el servidor lo rechazaria si no
    // pudiera decodificarlo, asi que no vale un archivo con la firma y basura.
    await page
      .locator('input[type="file"]')
      .setInputFiles({ name: 'mi-foto.png', mimeType: 'image/png', buffer: pngDe(300, 300) });

    const foto = page.getByRole('img', { name: 'Tu foto de perfil' });
    await expect(foto).toBeVisible();

    // La direccion no lleva el nombre del archivo que se subio: la clave es opaca.
    const direccion = await foto.getAttribute('src');
    expect(direccion).not.toContain('mi-foto');

    // Y la imagen se sirve de verdad, no es solo un `src` puesto en el DOM.
    const servida = await page.request.get(direccion ?? '');
    expect(servida.status()).toBe(200);

    // Sobrevive a una carga completa: quedo guardada, no solo en memoria.
    await page.reload();
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toBeVisible();
  });

  /** El tipo se decide por el contenido: un archivo renombrado no pasa. */
  test('rechaza un archivo que no es una imagen aunque se llame png', async ({ page }) => {
    const correo = correoNuevo('falsa');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.goto('/mi-cuenta');
    await expect(page.getByRole('heading', { name: 'Tu foto' })).toBeVisible();

    // El navegador lo declara como PNG, asi que la comprobacion del cliente lo deja
    // pasar. Lo rechaza el servidor mirando los bytes, que es donde importa.
    await page.locator('input[type="file"]').setInputFiles({
      name: 'trampa.png',
      mimeType: 'image/png',
      buffer: Buffer.from('<html><script>alert(1)</script></html>', 'utf8'),
    });

    await expect(page.getByRole('alert')).toContainText('no es una imagen JPG o PNG');
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toHaveCount(0);
  });

  test('quitar la foto la borra de verdad', async ({ page }) => {
    const correo = correoNuevo('quitar');
    const yaVistos = enlacesVistos('/verificar-correo');

    await registrarse(page, correo);
    await verificarElCorreo(page, await esperarEnlace('/verificar-correo', yaVistos));

    await page.goto('/mi-cuenta');
    await page
      .locator('input[type="file"]')
      .setInputFiles({ name: 'mi-foto.png', mimeType: 'image/png', buffer: pngDe(300, 300) });

    const foto = page.getByRole('img', { name: 'Tu foto de perfil' });
    await expect(foto).toBeVisible();
    const direccion = (await foto.getAttribute('src')) ?? '';

    await page.getByRole('button', { name: 'Quitar la foto' }).click();
    await page.getByRole('button', { name: 'Sí, quitarla' }).click();

    await expect(foto).toHaveCount(0);

    // El archivo tambien: no basta con soltar la referencia de la fila.
    await expect(async () => {
      expect((await page.request.get(direccion)).status()).toBe(404);
    }).toPass();

    await page.reload();
    await expect(page.getByRole('img', { name: 'Tu foto de perfil' })).toHaveCount(0);
  });
});
