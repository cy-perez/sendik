import { expect, test, type Page } from '@playwright/test';

/**
 * El logo de la cabecera, en las cuatro combinaciones de tema y ancho.
 *
 * Por que existe: la alternancia por tema vivia en site-header.css, la hoja del
 * componente, y no funcionaba. La encapsulacion emulada de Angular le anade
 * `[_ngcontent-x]` a todos los compuestos del selector, incluido
 * `[data-tema="oscuro"]`, que esta en el <html> y no lleva ese atributo: la
 * regla se compilaba a algo que no casa nunca. El resultado era el logo en tinta
 * sobre el fondo oscuro, casi invisible, y **ninguna prueba lo veia** porque
 * todas miraban clases y atributos, que estaban bien.
 *
 * Se comprueba lo unico que importa: que se vea exactamente una, y que sea la
 * que le corresponde al tema y al ancho.
 */
test.use({ locale: 'es-CO' });

const visibles = (page: Page): Promise<string[]> =>
  page
    .locator('header .logo-sitio img')
    .evaluateAll((imagenes) =>
      imagenes
        .filter((imagen) => getComputedStyle(imagen).display !== 'none')
        .map((imagen) => imagen.getAttribute('src') ?? ''),
    );

const aOscuro = async (page: Page): Promise<void> => {
  await page.getByRole('button', { name: /oscuro/i }).click();
  await expect(page.locator('html')).toHaveAttribute('data-tema', 'oscuro');
};

test.describe('logo de la cabecera', () => {
  test('escritorio en claro: el lockup en tinta', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');

    expect(await visibles(page)).toEqual(['/logo-horizontal.svg']);
  });

  test('escritorio en oscuro: el lockup mono negativo', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/');
    await aOscuro(page);

    expect(await visibles(page)).toEqual(['/logo-mono-negativo.svg']);
  });

  /**
   * Por debajo de 640px entra el isotipo: el lockup mide 169px de ancho y el
   * manual prohibe usarlo por debajo de 130px, ademas de que el ancho de la
   * cabecera en un marketplace es del buscador.
   */
  test('movil en claro: el isotipo en tinta', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 800 });
    await page.goto('/');

    expect(await visibles(page)).toEqual(['/isotipo.svg']);
  });

  test('movil en oscuro: el isotipo negativo', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 800 });
    await page.goto('/');
    await aOscuro(page);

    expect(await visibles(page)).toEqual(['/isotipo-negativo.svg']);
  });
});
