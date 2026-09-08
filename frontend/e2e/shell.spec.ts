import { expect, test } from '@playwright/test';

/**
 * Estas si necesitan navegador. Si falla con "Executable doesn't exist", falta
 * el paso explicito: npx playwright install chromium
 *
 * El idioma se fija a proposito. Chromium arranca con Accept-Language: en-US y
 * el servidor le sirve la pagina en ingles, que es justo lo que debe hacer; sin
 * fijarlo, estas pruebas dependerian del idioma con el que venga el navegador
 * del runner. Que la negociacion de idioma funciona lo demuestra ssr.spec.ts,
 * que es donde corresponde.
 */
test.use({ locale: 'es-CO' });

test.describe('cascaron del sitio', () => {
  test('el enlace de salto lleva el foco al contenido', async ({ page }) => {
    await page.goto('/');
    await page.keyboard.press('Tab');

    const skip = page.getByRole('link', { name: 'Saltar al contenido' });
    await expect(skip).toBeFocused();

    await page.keyboard.press('Enter');
    await expect(page.locator('#contenido')).toBeFocused();
  });

  test('el conmutador de tema cambia el modo y lo recuerda', async ({ page }) => {
    await page.goto('/');
    const root = page.locator('html');
    await expect(root).toHaveAttribute('data-tema', 'claro');

    await page.getByRole('button', { name: 'Cambiar a modo oscuro' }).click();
    await expect(root).toHaveAttribute('data-tema', 'oscuro');

    // Al recargar, el servidor ya lo sabe por la cookie: llega oscuro de origen.
    await page.reload();
    await expect(root).toHaveAttribute('data-tema', 'oscuro');
  });

  test('el selector de idioma cambia el texto y lo recuerda', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Compra y vende');

    await page.getByLabel('Idioma').selectOption('en');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Buy and sell');

    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('lang', 'en');
  });

  test('hay un solo h1 en la pagina', async ({ page }) => {
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
  });

  /**
   * La reticula de iconos del manual: trazo de 2 y terminaciones RECTAS, la
   * misma decision que los cortes rectos del isotipo. Se mide el estilo
   * CALCULADO y no el atributo del SVG a proposito: el atributo es lo que hay
   * que dejar de escribir, y una prueba que lo buscara premiaria justo el error.
   * Los cuatro iconos de la cabecera iban a 1.75 con terminaciones por omision,
   * y como son los unicos del sitio, el set entero estaba fuera de norma sin que
   * nada lo dijera.
   */
  test('los iconos siguen la reticula de marca', async ({ page }) => {
    await page.goto('/');

    const iconos = page.locator('header svg');
    await expect(iconos).not.toHaveCount(0);

    for (const icono of await iconos.all()) {
      const trazo = await icono.evaluate((elemento) => {
        const estilo = getComputedStyle(elemento);
        return {
          grosor: estilo.strokeWidth,
          terminacion: estilo.strokeLinecap,
          union: estilo.strokeLinejoin,
        };
      });

      expect(trazo).toEqual({ grosor: '2px', terminacion: 'butt', union: 'miter' });
    }
  });
});
