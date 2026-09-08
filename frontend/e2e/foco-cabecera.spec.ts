import { expect, test, type Page } from '@playwright/test';

/**
 * El foco atrapado en el menu compacto de la cabecera (HU-005).
 *
 * Por que esta aqui y no en una prueba de componente: jsdom no implementa el
 * tabulador. Pulsar Tab no mueve el foco, asi que una prueba de componente solo
 * puede comprobar que el codigo llama a .focus(), no que el recorrido sea el que
 * se cree. Con cdkTrapFocus eso ya ni siquiera vale, porque el CDK deja que el
 * navegador lleve el foco a unas anclas invisibles y jsdom no lo hace nunca.
 *
 * Lo que se comprueba es el recorrido de verdad, incluido el caso que la version
 * anterior fallaba: con el ciclo limitado al panel, el idioma, el tema y la
 * sesion se veian en movil pero no se alcanzaban con teclado.
 */
test.use({ locale: 'es-CO', viewport: { width: 390, height: 800 } });

const abrirMenu = async (page: Page): Promise<void> => {
  await page.goto('/');
  await page.getByRole('button', { name: /abrir el men/i }).click();
  await expect(page.getByRole('button', { name: /cerrar el men/i })).toBeVisible();
};

/** Que tiene el foco ahora mismo, en algo que se pueda leer al fallar. */
const enfocado = (page: Page): Promise<string> =>
  page.evaluate(() => {
    const activo = document.activeElement as HTMLElement | null;
    if (activo === null) return 'ninguno';
    return activo.getAttribute('aria-label') ?? activo.id ?? activo.textContent?.trim() ?? '';
  });

test.describe('foco atrapado en el menu compacto', () => {
  /**
   * La regresion que motivo ampliar la region al conjunto de la barra. El
   * selector de idioma esta VISIBLE en movil y vive fuera del panel; con el
   * ciclo limitado al panel, el tabulador desde el ultimo enlace volvia al boton
   * y no habia forma de llegar a el sin cerrar el menu.
   */
  test('el idioma se alcanza con el tabulador sin cerrar el menu', async ({ page }) => {
    await abrirMenu(page);
    await page.locator('#menu-principal a[href]').last().focus();

    await page.keyboard.press('Tab');

    expect(await enfocado(page)).toBe('language-picker');
  });

  test('el conmutador de tema tambien entra en el ciclo', async ({ page }) => {
    await abrirMenu(page);
    await page.locator('#menu-principal a[href]').last().focus();

    await page.keyboard.press('Tab');
    await page.keyboard.press('Tab');

    expect(await enfocado(page)).toMatch(/modo (oscuro|claro)/i);
  });

  /**
   * El foco no sale de la cabecera mientras el menu este abierto: eso es lo que
   * significa atraparlo. Se tabula mas veces que elementos hay para forzar la
   * vuelta.
   */
  test('el tabulador no se escapa de la cabecera', async ({ page }) => {
    await abrirMenu(page);

    for (let vuelta = 0; vuelta < 12; vuelta++) {
      await page.keyboard.press('Tab');

      const dentro = await page.evaluate(() => {
        const cabecera = document.querySelector('header');
        return cabecera !== null && cabecera.contains(document.activeElement);
      });

      expect(dentro, `salio de la cabecera en la vuelta ${vuelta}`).toBe(true);
    }
  });

  /**
   * Escape cierra y devuelve el foco al boton. Sin lo segundo el foco se queda
   * en un elemento que acaba de ocultarse y el navegador lo manda al principio
   * del documento.
   */
  test('Escape cierra y devuelve el foco al boton', async ({ page }) => {
    await abrirMenu(page);
    await page.locator('#menu-principal a[href]').first().focus();

    await page.keyboard.press('Escape');

    await expect(page.getByRole('button', { name: /abrir el men/i })).toBeFocused();
  });
});

test.describe('en escritorio no se atrapa nada', () => {
  test.use({ viewport: { width: 1280, height: 800 } });

  /** El tabulador tiene que poder salir de la cabecera con normalidad. */
  test('el tabulador sale de la cabecera', async ({ page }) => {
    await page.goto('/');
    await page.locator('header a[href]').first().focus();

    let salio = false;
    for (let vuelta = 0; vuelta < 25 && !salio; vuelta++) {
      await page.keyboard.press('Tab');
      salio = await page.evaluate(() => {
        const cabecera = document.querySelector('header');
        return cabecera !== null && !cabecera.contains(document.activeElement);
      });
    }

    expect(salio).toBe(true);
  });
});
