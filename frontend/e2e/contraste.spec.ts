import { expect, test, type Page } from '@playwright/test';

/**
 * El contraste de la paleta, medido sobre lo que el navegador resuelve.
 *
 * **Por que existe.** Hasta la ADR-0032 esto lo comprobaba `verificar.py`, que
 * leia los tokens generados y los contrastaba contra una lista de pares. Al
 * sacar el color del generador esa lista se quedo sin fuente y el informe
 * `docs/ui/contraste.md` dejo de reflejar lo que se pinta. La ADR lo anoto como
 * deuda con nombre: hasta rehacer esta comprobacion, la unica garantia era axe.
 *
 * **Por que axe no basta.** Axe mide lo que hay pintado en la pantalla que
 * audita, asi que solo ve los pares que esa pantalla usa en ese estado. Un color
 * que todavia no se usa en ninguna parte, o que solo aparece en un estado de
 * error que la auditoria no provoca, no lo mira nadie. Esto comprueba la PALETA,
 * que es donde se decide, y avisa antes de pintar.
 *
 * **Por que lee del navegador y no del archivo.** Asi se comprueba la cascada de
 * verdad —el modo oscuro por clase y las variables que la franja de tinta
 * redefine dentro de su bloque— y no una copia de los numeros que podria
 * quedarse vieja.
 */

/** Texto normal. */
const AA_TEXTO = 4.5;
/** Texto grande, iconos y bordes de control (WCAG 1.4.11). */
const AA_GRANDE = 3;

type Par = readonly [string, string, string, number];

const VARIABLES = [
  '--brand-background',
  '--brand-surface',
  '--brand-text',
  '--brand-text-muted',
  '--brand-primary',
  '--brand-on-primary',
  '--brand-primary-soft',
  '--brand-accent',
  '--brand-on-accent',
  '--brand-success',
  '--brand-warning',
  '--brand-error',
  '--brand-focus',
  '--brand-control-border',
  '--brand-ink',
  '--brand-on-ink',
];

const PARES: readonly Par[] = [
  ['Texto principal sobre el fondo', '--brand-text', '--brand-background', AA_TEXTO],
  ['Texto principal sobre tarjeta', '--brand-text', '--brand-surface', AA_TEXTO],
  ['Texto secundario sobre el fondo', '--brand-text-muted', '--brand-background', AA_TEXTO],
  ['Texto secundario sobre tarjeta', '--brand-text-muted', '--brand-surface', AA_TEXTO],
  ['Enlace y estructura sobre el fondo', '--brand-primary', '--brand-background', AA_TEXTO],
  ['Texto del boton primario', '--brand-on-primary', '--brand-primary', AA_TEXTO],
  ['Etiqueta sobre fondo de etiqueta', '--brand-text', '--brand-primary-soft', AA_TEXTO],

  ['Mensaje de exito sobre tarjeta', '--brand-success', '--brand-surface', AA_TEXTO],
  ['Mensaje de aviso sobre tarjeta', '--brand-warning', '--brand-surface', AA_TEXTO],
  ['Mensaje de error sobre tarjeta', '--brand-error', '--brand-surface', AA_TEXTO],
  // Y los mismos sobre el fondo de pagina, que es donde caen en las pantallas de
  // publicacion: alli el mensaje no vive dentro de una tarjeta.
  ['Mensaje de aviso sobre el fondo', '--brand-warning', '--brand-background', AA_TEXTO],
  // HU-003: el nivel del asistente de captura dice «Nivelado» en verde sobre el
  // fondo de pagina. El exito estaba solo como borde a 3:1; como texto no se
  // comprobaba.
  ['Mensaje de exito sobre el fondo', '--brand-success', '--brand-background', AA_TEXTO],
  ['Mensaje de error sobre el fondo', '--brand-error', '--brand-background', AA_TEXTO],

  ['Anillo de foco sobre el fondo', '--brand-focus', '--brand-background', AA_GRANDE],
  ['Anillo de foco sobre tarjeta', '--brand-focus', '--brand-surface', AA_GRANDE],
  ['Borde de campo de formulario', '--brand-control-border', '--brand-surface', AA_GRANDE],
  // HU-008. La confirmacion y las marcas de la cola son cajas con borde de
  // control que se apoyan sobre el fondo de PAGINA, no dentro de una tarjeta.
  ['Borde de control sobre el fondo', '--brand-control-border', '--brand-background', AA_GRANDE],
  ['Borde de aviso propio sobre tarjeta', '--brand-primary', '--brand-surface', AA_GRANDE],
  // Las cajas de aviso y de error llevan fondo de tarjeta pero se apoyan sobre
  // el fondo de pagina: su borde linda con los dos y es informacion no textual.
  ['Borde de aviso sobre el fondo', '--brand-warning', '--brand-background', AA_GRANDE],
  ['Borde de error sobre el fondo', '--brand-error', '--brand-background', AA_GRANDE],
  ['Borde de exito sobre el fondo', '--brand-success', '--brand-background', AA_GRANDE],
  // El boton secundario solo se identifica como control por su borde: eso es
  // informacion no textual y va contra el fondo de PAGINA, que es donde se apoya.
  ['Borde de boton secundario sobre el fondo', '--brand-primary', '--brand-background', AA_GRANDE],

  // ---- El bronce. Dos tonos, cada uno con su fondo, y nunca al reves ----
  //
  // Umbral de 3:1 y no 4.5:1, y no es una rebaja para que pase: el bronce NUNCA
  // es texto. En la insignia de vendedor verificado es una linea de 2px y un
  // icono de 16px —objetos graficos, WCAG 1.4.11— y el texto de la insignia va
  // en el texto suave, que si se comprueba como texto mas arriba.
  //
  // Que quede claro cual es el margen real: sobre la tarjeta oscura el bronce da
  // 4.22:1. Cumple de sobra como objeto grafico, pero si alguna vez se usa como
  // texto ahi, incumple. Si eso llega a hacer falta, no se sube el umbral: se
  // pide a diseno un tercer tono, porque el bronce de marca no da.
  ['Insignia verificada sobre el fondo', '--brand-accent', '--brand-background', AA_GRANDE],
  ['Insignia verificada sobre tarjeta', '--brand-accent', '--brand-surface', AA_GRANDE],
  // El relleno de acento si lleva texto encima, y ahi el umbral entero aplica.
  ['Texto sobre relleno de acento', '--brand-on-accent', '--brand-accent', AA_TEXTO],
];

/**
 * Los pares de la franja de tinta. Se leen de un elemento DENTRO de la franja,
 * porque es ahi donde las variables valen lo que valen: la franja redefine el
 * foco, el primario y el acento en su propio bloque, y esa redefinicion es
 * justamente el mecanismo que hay que comprobar.
 */
const PARES_TINTA: readonly Par[] = [
  ['Texto sobre la franja de tinta', '--brand-on-ink', '--brand-ink', AA_TEXTO],
  ['Insignia verificada sobre la tinta', '--brand-accent', '--brand-ink', AA_GRANDE],
  // El anillo se dibuja con outline-offset de 2px, o sea SEPARADO del control
  // por un hueco que deja ver el fondo. Por eso se compara contra el fondo de
  // alrededor y no contra el relleno del control.
  ['Anillo de foco sobre la franja de tinta', '--brand-focus', '--brand-ink', AA_GRANDE],
  // Dentro de la franja el boton principal se invierte: relleno claro con tinta
  // encima. Un boton en tinta sobre fondo de tinta no se ve, y el manual prohibe
  // resolverlo con bronce, asi que la salida es invertirlo.
  ['Texto del boton dentro de la franja', '--brand-on-primary', '--brand-primary', AA_TEXTO],
  ['Relleno del boton contra la franja', '--brand-primary', '--brand-ink', AA_GRANDE],
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
  await expect(page.locator('html')).toHaveClass(/dark/);
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
