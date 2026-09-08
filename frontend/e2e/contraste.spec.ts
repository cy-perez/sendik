import { expect, test, type Page } from '@playwright/test';

/**
 * El contraste de la paleta, medido sobre lo que el navegador resuelve.
 *
 * **Por que existe.** Esto ya lo comprueba `docs/ui/generador/verificar.py`, que
 * lee las tres hojas y contrasta la misma lista de pares en los dos modos. Pero
 * ese script **hay que lanzarlo a mano**: no esta en `verificacion.yml` ni en
 * ningun script de npm, asi que hoy nada impide fusionar un cambio que rompa un
 * par. Esta prueba corre en cada pull request, que es la diferencia entera.
 *
 * **Por que no basta con axe.** Axe mide lo que hay pintado en la pantalla que
 * audita, asi que solo ve los pares que esa pantalla usa en ese estado. Un color
 * que todavia no se usa en ninguna parte, o que solo aparece en un estado de
 * error que la auditoria no provoca, no lo mira nadie. Esto comprueba la PALETA,
 * que es donde se decide, y avisa antes de pintar.
 *
 * **Por que lee del navegador y no del archivo.** `verificar.py` resuelve a mano
 * las redefiniciones de la franja de tinta: da por hecho que dentro de ella
 * `--color-foco` vale `--color-foco-tinta` y compara ese par directamente. Aqui
 * se mide desde un elemento que esta DENTRO de la franja, asi que lo que se
 * comprueba es que el selector aplica de verdad, no solo que la variable existe.
 * Si alguien renombra la clase o mueve el bloque, el script sigue en verde y esta
 * prueba no.
 */

/** Texto normal. */
const AA_TEXTO = 4.5;
/** Texto grande, iconos y bordes de control (WCAG 1.4.11). */
const AA_GRANDE = 3;

type Par = readonly [string, string, string, number];

const VARIABLES = [
  '--color-fondo',
  '--color-superficie',
  '--color-texto',
  '--color-texto-suave',
  '--color-primario',
  '--color-sobre-primario',
  '--color-primario-suave',
  '--color-acento',
  '--color-sobre-acento',
  '--color-exito',
  '--color-aviso',
  '--color-error',
  '--color-foco',
  '--color-borde-control',
  '--color-tinta',
  '--color-sobre-tinta',
];

const PARES: readonly Par[] = [
  ['Texto principal sobre el fondo', '--color-texto', '--color-fondo', AA_TEXTO],
  ['Texto principal sobre tarjeta', '--color-texto', '--color-superficie', AA_TEXTO],
  ['Texto secundario sobre el fondo', '--color-texto-suave', '--color-fondo', AA_TEXTO],
  ['Texto secundario sobre tarjeta', '--color-texto-suave', '--color-superficie', AA_TEXTO],
  ['Enlace y estructura sobre el fondo', '--color-primario', '--color-fondo', AA_TEXTO],
  ['Texto del boton primario', '--color-sobre-primario', '--color-primario', AA_TEXTO],
  ['Etiqueta sobre fondo de etiqueta', '--color-texto', '--color-primario-suave', AA_TEXTO],

  ['Mensaje de exito sobre tarjeta', '--color-exito', '--color-superficie', AA_TEXTO],
  ['Mensaje de aviso sobre tarjeta', '--color-aviso', '--color-superficie', AA_TEXTO],
  ['Mensaje de error sobre tarjeta', '--color-error', '--color-superficie', AA_TEXTO],
  // Y los mismos sobre el fondo de pagina, que es donde caen en las pantallas de
  // publicacion: alli el mensaje no vive dentro de una tarjeta.
  ['Mensaje de aviso sobre el fondo', '--color-aviso', '--color-fondo', AA_TEXTO],
  // HU-003: el nivel del asistente de captura dice «Nivelado» en verde sobre el
  // fondo de pagina. El exito estaba solo como borde a 3:1; como texto no se
  // comprobaba.
  ['Mensaje de exito sobre el fondo', '--color-exito', '--color-fondo', AA_TEXTO],
  ['Mensaje de error sobre el fondo', '--color-error', '--color-fondo', AA_TEXTO],

  ['Anillo de foco sobre el fondo', '--color-foco', '--color-fondo', AA_GRANDE],
  ['Anillo de foco sobre tarjeta', '--color-foco', '--color-superficie', AA_GRANDE],
  ['Borde de campo de formulario', '--color-borde-control', '--color-superficie', AA_GRANDE],
  // HU-008. La confirmacion y las marcas de la cola son cajas con borde de
  // control que se apoyan sobre el fondo de PAGINA, no dentro de una tarjeta.
  ['Borde de control sobre el fondo', '--color-borde-control', '--color-fondo', AA_GRANDE],
  ['Borde de aviso propio sobre tarjeta', '--color-primario', '--color-superficie', AA_GRANDE],
  // Las cajas de aviso y de error llevan fondo de tarjeta pero se apoyan sobre
  // el fondo de pagina: su borde linda con los dos y es informacion no textual.
  ['Borde de aviso sobre el fondo', '--color-aviso', '--color-fondo', AA_GRANDE],
  ['Borde de error sobre el fondo', '--color-error', '--color-fondo', AA_GRANDE],
  ['Borde de exito sobre el fondo', '--color-exito', '--color-fondo', AA_GRANDE],
  // El boton secundario solo se identifica como control por su borde: eso es
  // informacion no textual y va contra el fondo de PAGINA, que es donde se apoya.
  ['Borde de boton secundario sobre el fondo', '--color-primario', '--color-fondo', AA_GRANDE],

  // ---- El bronce. Dos tonos, cada uno con su fondo, y nunca al reves ----
  //
  // Umbral de 3:1 y no 4.5:1, y no es una rebaja para que pase: el bronce NUNCA
  // es texto. En la insignia de vendedor verificado es una linea de 2px y un
  // icono de 16px —objetos graficos, WCAG 1.4.11— y el texto de la insignia va
  // en el texto suave, que si se comprueba como texto mas arriba.
  //
  // Cruzar los dos tonos da 2.97:1, que es el numero que CLAUDE.md nombra: por
  // eso `tokens.css` alterna el correcto por modo y esta prueba corre en los dos.
  ['Insignia verificada sobre el fondo', '--color-acento', '--color-fondo', AA_GRANDE],
  ['Insignia verificada sobre tarjeta', '--color-acento', '--color-superficie', AA_GRANDE],
  // El relleno de acento si lleva texto encima, y ahi el umbral entero aplica.
  ['Texto sobre relleno de acento', '--color-sobre-acento', '--color-acento', AA_TEXTO],
];

/**
 * Los pares de la franja de tinta. Se leen de un elemento DENTRO de la franja,
 * porque es ahi donde las variables valen lo que valen: `marca.css` redefine el
 * foco, el acento y el primario en el propio bloque de `.franja-tinta`, y esa
 * redefinicion es justamente el mecanismo que hay que comprobar.
 */
const PARES_TINTA: readonly Par[] = [
  ['Texto sobre la franja de tinta', '--color-sobre-tinta', '--color-tinta', AA_TEXTO],
  ['Insignia verificada sobre la tinta', '--color-acento', '--color-tinta', AA_GRANDE],
  // El anillo se dibuja con outline-offset, o sea SEPARADO del control por un
  // hueco que deja ver el fondo. Por eso se compara contra el fondo de alrededor
  // y no contra el relleno del control.
  ['Anillo de foco sobre la franja de tinta', '--color-foco', '--color-tinta', AA_GRANDE],
  // Dentro de la franja el boton principal se invierte: relleno claro con tinta
  // encima. Un boton en tinta sobre fondo de tinta no se ve, y el manual prohibe
  // resolverlo con bronce, asi que la salida es invertirlo.
  ['Texto del boton dentro de la franja', '--color-sobre-primario', '--color-primario', AA_TEXTO],
  ['Relleno del boton contra la franja', '--color-primario', '--color-tinta', AA_GRANDE],
];

/** Canal a luz lineal, segun WCAG 2.x. */
const lineal = (canal: number): number => {
  const c = canal / 255;
  return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
};

const luminancia = (color: string): number => {
  const partes = color.match(/\d+(\.\d+)?/g);
  if (partes === null || partes.length < 3) {
    throw new Error('No se pudo leer el color: ' + color);
  }
  const canales = partes.slice(0, 3).map(Number);
  return 0.2126 * lineal(canales[0]!) + 0.7152 * lineal(canales[1]!) + 0.0722 * lineal(canales[2]!);
};

const contraste = (frente: string, fondo: string): number => {
  const a = luminancia(frente);
  const b = luminancia(fondo);
  const claro = Math.max(a, b);
  const oscuro = Math.min(a, b);
  return Math.round(((claro + 0.05) / (oscuro + 0.05)) * 100) / 100;
};

/**
 * Lee la paleta resuelta de un elemento. Se mide el color COMPUTADO de una sonda
 * y no el valor declarado: asi llega ya en rgb() y no hay que volver a
 * interpretar hexadecimales a mano, que es donde se cuelan los errores.
 */
const leerPaleta = (page: Page, selector: string): Promise<Record<string, string>> =>
  page.evaluate(
    ({ selector: sel, variables }) => {
      const elemento = document.querySelector(sel);
      if (elemento === null) {
        throw new Error('No existe ' + sel);
      }

      const sonda = document.createElement('span');
      elemento.appendChild(sonda);

      const paleta: Record<string, string> = {};
      for (const nombre of variables) {
        sonda.style.color = 'var(' + nombre + ')';
        paleta[nombre] = getComputedStyle(sonda).color;
      }

      sonda.remove();
      return paleta;
    },
    { selector, variables: VARIABLES },
  );

/** Los que no llegan a su umbral, ya con el numero medido para poder leerlo. */
const incumplen = (paleta: Record<string, string>, pares: readonly Par[]): string[] =>
  pares
    .map((par) => ({ par, medido: contraste(paleta[par[1]]!, paleta[par[2]]!) }))
    .filter(({ par, medido }) => medido < par[3])
    .map(({ par, medido }) => par[0] + ': ' + medido + ':1 (min ' + par[3] + ')');

const enOscuro = async (page: Page): Promise<void> => {
  await page.getByRole('button', { name: /oscuro/i }).click();
  await expect(page.locator('html')).toHaveAttribute('data-tema', 'oscuro');
};

test.use({ locale: 'es-CO' });

for (const modo of ['claro', 'oscuro'] as const) {
  test.describe('contraste de la paleta en modo ' + modo, () => {
    test('todos los pares del sistema cumplen su umbral', async ({ page }) => {
      await page.goto('/');
      if (modo === 'oscuro') {
        await enOscuro(page);
      }

      const fallas = incumplen(await leerPaleta(page, 'html'), PARES);

      expect(fallas, 'pares que no cumplen en modo ' + modo).toEqual([]);
    });

    /**
     * La franja es tinta en los DOS modos, asi que sus pares se comprueban en los
     * dos: en oscuro la tinta queda un paso por encima del fondo de pagina y se
     * sigue leyendo como franja, pero el texto y el anillo de dentro tienen que
     * cumplir igual.
     */
    test('los pares de la franja de tinta cumplen su umbral', async ({ page }) => {
      await page.goto('/');
      if (modo === 'oscuro') {
        await enOscuro(page);
      }

      const fallas = incumplen(await leerPaleta(page, '.franja-tinta'), PARES_TINTA);

      expect(fallas, 'pares de la franja que no cumplen en modo ' + modo).toEqual([]);
    });
  });
}
