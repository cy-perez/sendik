import { expect, test } from '@playwright/test';

/**
 * No llaman al backend a proposito: esta suite corre en la canalizacion, donde
 * no hay ni base de datos ni API. Lo que se comprueba aqui es lo que el
 * frontend decide por su cuenta: que la pagina llega renderizada, que la
 * validacion de cliente evita el viaje y que los dos consentimientos estan
 * separados.
 *
 * El camino completo con servidor se recorre a mano y se cubrira con una prueba
 * de integracion cuando exista el entorno de dev.
 */
test.use({ locale: 'es-CO' });

test.describe('registro', () => {
  test('el formulario llega renderizado desde el servidor', async ({ request }) => {
    const response = await request.get('/registro');
    const html = await response.text();

    expect(response.status()).toBe(200);
    // Se busca el titulo renderizado, no la cadena suelta: el estado transferido
    // lleva todas las traducciones y encontrarla ahi no probaria nada.
    expect(html).toMatch(/<h1[^>]*>Crea tu cuenta<\/h1>/);
    // Sin ejecutar JavaScript: los campos vienen en el HTML, no los pinta el
    // navegador despues (ADR-0006).
    expect(html).toContain('id="correo"');
    expect(html).toContain('id="nacimiento"');
  });

  // Ley 1581: una sola casilla para los dos documentos no es consentimiento
  // valido (docs/operacion/datos-personales.md).
  test('pide los dos consentimientos por separado', async ({ page }) => {
    await page.goto('/registro');

    await expect(page.getByRole('checkbox')).toHaveCount(2);
    await expect(page.getByLabel('He leído y acepto los términos y condiciones.')).toBeVisible();
    await expect(
      page.getByLabel(
        'Autorizo de forma libre, previa y expresa el tratamiento de mis datos personales para las finalidades descritas arriba y en la política de tratamiento de datos.',
      ),
    ).toBeVisible();
  });

  test('no muestra errores hasta el primer intento', async ({ page }) => {
    await page.goto('/registro');

    await expect(page.locator('[role="alert"]')).toHaveCount(0);
  });

  test('senala cada campo que falta al enviar vacio', async ({ page }) => {
    await page.goto('/registro');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect(page.locator('[aria-invalid="true"]')).toHaveCount(6);
    await expect(page.getByText('Escribe un correo válido, como nombre@correo.com.')).toBeVisible();
  });

  test('avisa de la edad minima sin llamar al servidor RN-008', async ({ page }) => {
    await page.goto('/registro');

    await page.getByLabel('Correo electrónico').fill('ana@correo.co');
    await page.getByLabel('Nombre', { exact: true }).fill('Ana Maria');
    await page.getByLabel('Contraseña').fill('una contrasena larga');
    await page.getByLabel('Fecha de nacimiento').fill('2015-01-01');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    await expect(
      page.getByText('Debes tener 18 años cumplidos para crear una cuenta.'),
    ).toBeVisible();
  });

  test('cada campo anuncia su error al lector de pantalla', async ({ page }) => {
    await page.goto('/registro');
    await page.getByRole('button', { name: 'Crear cuenta' }).click();

    // Con `getAttribute` y un `expect` normal esto era una foto sin reintento: se
    // leia el atributo una sola vez, y si la deteccion de cambios todavia no
    // habia pintado el error, la prueba caia por milisegundos. Fallaba solo con
    // la maquina cargada, que es justo cuando corre la integracion continua.
    await expect(page.getByLabel('Correo electrónico')).toHaveAttribute(
      'aria-describedby',
      /correo-error/,
    );
    await expect(page.locator('#correo-error')).toHaveAttribute('role', 'alert');
  });

  /**
   * Regresion. La verificacion consume el token, que es de un solo uso (RN-003).
   * Si corriera durante el renderizado en servidor, la vista previa de un enlace
   * en WhatsApp lo gastaria antes del primer clic y la persona abriria un enlace
   * ya usado. Aqui se pide el HTML sin ejecutar JavaScript: tiene que quedarse
   * en el estado inicial y no resolver nada.
   *
   * Se mira el titulo renderizado y no el documento entero: el estado
   * transferido lleva el archivo de traducciones completo, asi que todas las
   * cadenas aparecen en el HTML aunque no se muestre ninguna.
   */
  test('no verifica durante el renderizado en servidor RN-003', async ({ request }) => {
    const response = await request.get('/verificar-correo?token=un-token-cualquiera');
    const titulos = [...(await response.text()).matchAll(/<h1[^>]*>([^<]*)<\/h1>/g)].map(
      ([, texto]) => texto,
    );

    expect(titulos).toEqual(['Confirmando tu correo']);
  });

  test('un enlace de verificacion sin token lo dice, no falla en silencio', async ({ page }) => {
    await page.goto('/verificar-correo');

    await expect(page.getByRole('heading', { level: 1 })).toContainText('Falta el enlace');
  });

  test('solo hay un h1 en cada pantalla nueva', async ({ page }) => {
    for (const ruta of ['/registro', '/verificar-correo']) {
      await page.goto(ruta);
      await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    }
  });
});
