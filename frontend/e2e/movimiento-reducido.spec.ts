import { expect, test, type Page } from '@playwright/test';

/**
 * Quien pide menos movimiento lo recibe.
 *
 * **Por que existe.** Es uno de los cinco controles de accesibilidad que
 * `docs/ui/accesibilidad.md` da por entregados desde la Fase 1, y era el unico
 * sin una prueba que lo mirara: la regla vive en `tokens.css` y en `marca.css` y
 * se daba por hecha.
 *
 * **Y darla por hecha ya salio mal una vez.** En la migracion a Tailwind
 * —revertida, ADR-0033— la regla global desaparecio con la hoja que la
 * contenia y solo quedo detenido el brillo del esqueleto; las transiciones
 * siguieron corriendo. **No lo vio nadie**, porque axe no evalua movimiento y
 * ninguna prueba abria el sitio con la preferencia puesta. Esta prueba nacio
 * alli, y se recupera precisamente porque no depende del motor de estilos: mide
 * lo que el navegador computa, venga de donde venga la regla.
 *
 * <p>Se mide sobre una sonda que se inserta en la pagina y no sobre un elemento
 * de la interfaz, y es a proposito: lo que se comprueba es la REGLA, que aplica
 * a todo el documento. Colgar la prueba de un boton concreto la ata a que esa
 * pantalla siga teniendo ese boton, y a que ese boton siga estando en el estado
 * que la hace visible.
 */

/** Lo que el navegador computa sobre una sonda con las clases que se le den. */
const medir = (page: Page, clases: string): Promise<{ animacion: string; transicion: string }> =>
  page.evaluate((lista) => {
    const sonda = document.createElement('div');
    sonda.className = lista;
    // Una transicion declarada, para tener algo que medir: sin ella la duracion
    // seria cero pasara lo que pasare y la prueba no demostraria nada.
    sonda.style.transitionProperty = 'opacity';
    sonda.style.transitionDuration = '400ms';
    document.body.appendChild(sonda);

    const estilo = getComputedStyle(sonda);
    const medida = { animacion: estilo.animationName, transicion: estilo.transitionDuration };

    sonda.remove();
    return medida;
  }, clases);

test.use({ locale: 'es-CO' });

test.describe('con la preferencia de menos movimiento', () => {
  // Con emulateMedia y no con test.use({ reducedMotion }): la opcion de contexto
  // no llegaba al navegador en esta configuracion —matchMedia seguia diciendo
  // que no— y la prueba habria pasado midiendo el modo normal, que es el peor
  // resultado posible: verde y sin comprobar nada.
  test.beforeEach(async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
  });

  test('las transiciones quedan en practicamente nada', async ({ page }) => {
    await page.goto('/');

    // Que la preferencia llega de verdad. Sin esto, un cambio en la
    // configuracion dejaria las dos comprobaciones de abajo midiendo el modo
    // normal y seguirian en verde.
    expect(await page.evaluate(() => matchMedia('(prefers-reduced-motion: reduce)').matches)).toBe(
      true,
    );

    const { transicion } = await medir(page, '');

    // 0.01ms y no 0: una duracion nula cancela el evento `transitionend`, y hay
    // codigo que espera por el. El movimiento desaparece sin romper a quien lo
    // escucha, asi que lo que se afirma es «practicamente nada», no «cero».
    expect(Number.parseFloat(transicion)).toBeLessThan(0.001);
  });

  /**
   * El brillo del esqueleto se detiene DEL TODO y no se acorta: a 0.01ms estaria
   * parpadeando cien veces por segundo, que es peor que el movimiento original.
   * Por eso `marca.css` le pone `animation: none` aparte de la regla global.
   */
  test('el brillo del esqueleto se detiene, no se acelera', async ({ page }) => {
    await page.goto('/');

    const { animacion } = await medir(page, 'esqueleto');

    expect(animacion).toBe('none');
  });
});

/**
 * El contraste. Sin esto, una regla que apagara la animacion siempre pasaria las
 * pruebas de arriba y el esqueleto se habria quedado quieto para todo el mundo,
 * que es una regresion distinta y igual de silenciosa.
 */
test.describe('sin la preferencia', () => {
  test('el esqueleto si brilla y las transiciones duran', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await page.goto('/');

    const { animacion, transicion } = await medir(page, 'esqueleto');

    expect(animacion).not.toBe('none');
    expect(Number.parseFloat(transicion)).toBeGreaterThan(0.1);
  });
});
