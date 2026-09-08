import { AxeBuilder } from '@axe-core/playwright';
import { expect, test, type Page } from '@playwright/test';

import { ETIQUETAS_WCAG, MODOS, informe } from '../e2e-comun/axe';
import { THEME_COOKIE } from '../src/app/core/theme/theme';
import {
  RUTA_MIS_PUBLICACIONES,
  dejarUnaVendedoraVerificada,
  publicarYEnviarARevision,
  retirarDeRevision,
} from './recorridos';

/**
 * El panel del vendedor **con datos dentro**, auditado con axe. HU-012.
 *
 * <p><strong>Existe porque la cobertura de la otra suite era aparente.</strong>
 * `e2e/accesibilidad.spec.ts` incluye `/mis-publicaciones`, pero corre sin backend y sin
 * sesion: lo unico que axe recorria de esta pantalla era su invitacion a entrar. La fila de
 * cifras, su estado de error y su boton de reintentar -que es interfaz nueva y la que mas
 * decisiones de accesibilidad tomo- no se auditaban nunca, y la linea de aquella lista
 * hacia creer que si.
 *
 * <p>Aqui hay backend, base de datos y sesion, asi que se puede llegar al panel lleno. Es
 * el mismo reparto que ya justifica que esta suite exista (ADR-0017): lo que solo se puede
 * ver cruzando las dos mitades, se prueba cruzandolas.
 *
 * <p>En los dos modos, como la otra suite. Un contraste se rompe al ajustar un token del
 * modo oscuro, no al escribir la plantilla.
 */
test.use({ locale: 'es-CO' });

/** Deja el panel cargado y con una publicacion dentro, en el modo que se pida. */
async function abrirElPanelEn(page: Page, modo: (typeof MODOS)[number]): Promise<string> {
  await page
    .context()
    .addCookies([{ name: THEME_COOKIE, value: modo.cookie, domain: 'localhost', path: '/' }]);

  await dejarUnaVendedoraVerificada(page, 'a11y-panel');
  const id = await publicarYEnviarARevision(page, `Camisa auditada ${Date.now()}`);

  await page.goto(RUTA_MIS_PUBLICACIONES);
  await expect(page.locator('html')).toHaveAttribute('data-tema', modo.atributo);

  // Se espera a las cifras y no al titular: el titular esta desde el primer pintado, y
  // auditar antes de que lleguen seria auditar los esqueletos otra vez.
  await expect(page.getByRole('term')).toHaveCount(7);

  return id;
}

async function auditar(page: Page): Promise<void> {
  // Ninguna regla desactivada, igual que en la suite publica: una violacion se corrige en
  // el codigo (ADR-0016).
  const { violations } = await new AxeBuilder({ page }).withTags(ETIQUETAS_WCAG).analyze();
  expect(violations, `\n${informe(violations)}`).toEqual([]);
}

for (const modo of MODOS) {
  test.describe(`accesibilidad del panel en modo ${modo.modo}`, () => {
    test('el panel con sus cifras no incumple ningun criterio WCAG 2.2 AA', async ({ page }) => {
      const id = await abrirElPanelEn(page, modo);

      await auditar(page);

      // Lo que espera revision se recoge, o se queda en la cola para siempre.
      await retirarDeRevision(page, id);
    });

    /**
     * La fila de cifras en su estado de error, que es donde vive el boton de reintentar.
     *
     * <p>Se fuerza cortando la peticion del resumen: es la unica forma de llegar a esa rama,
     * y sin ella el boton -su jerarquia visual, su foco, su contraste- no lo audita nadie.
     */
    test('la fila de cifras rota tampoco incumple ningun criterio', async ({ page }) => {
      const id = await abrirElPanelEn(page, modo);

      await page.route('**/users/me/listings/summary', (ruta) =>
        ruta.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'COMMON_UNEXPECTED' }),
        }),
      );
      await page.reload();

      await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
      await auditar(page);

      await page.unroute('**/users/me/listings/summary');
      await retirarDeRevision(page, id);
    });

    /**
     * El rastro desplegado. HU-013.
     *
     * <p>Nace plegado, asi que la auditoria del panel recorre la pantalla con la lista de
     * pasos, su desplegable y sus cuatro estados **fuera del DOM**. Es el mismo hueco que
     * HU-012 cerro para las cifras: lo que se auditaba eran siete esqueletos con
     * `aria-hidden`, no las cifras.
     */
    test('el rastro desplegado no incumple ningun criterio', async ({ page }) => {
      const id = await abrirElPanelEn(page, modo);

      await page.getByRole('button', { name: 'Ver qué ha pasado' }).first().click();

      // Con datos de verdad: la publicacion se envio a revision, asi que tiene al menos un
      // paso. Auditar el estado vacio dejaria la lista sin mirar.
      await expect(page.getByText('La enviaste a revisión').first()).toBeVisible();
      await auditar(page);

      await retirarDeRevision(page, id);
    });

    /**
     * Y su rama de error, que es donde vive el otro boton de reintentar.
     *
     * <p>Se fuerza cortando la peticion del rastro, por lo mismo que con las cifras: sin
     * esto, ese boton -su foco, su contraste, su jerarquia- no lo audita nadie.
     */
    test('el rastro roto tampoco incumple ningun criterio', async ({ page }) => {
      const id = await abrirElPanelEn(page, modo);

      await page.route('**/moderation-history', (ruta) =>
        ruta.fulfill({
          status: 500,
          contentType: 'application/json',
          body: JSON.stringify({ code: 'COMMON_UNEXPECTED' }),
        }),
      );

      await page.getByRole('button', { name: 'Ver qué ha pasado' }).first().click();
      await expect(page.getByRole('button', { name: 'Reintentar' })).toBeVisible();
      await auditar(page);

      await page.unroute('**/moderation-history');
      await retirarDeRevision(page, id);
    });
  });
}
