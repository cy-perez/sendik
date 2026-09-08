import { expect, test, type Page } from '@playwright/test';

/**
 * La maquetacion, comparada contra una imagen de referencia.
 *
 * **Por que existe.** Es la unica prueba del repositorio que mira donde estan las
 * cosas. El 7 de septiembre de 2026 una migracion del sistema de diseno se
 * fusiono con la canalizacion entera en verde y dejo el sitio descuadernado
 * (ADR-0033). No fallo la suite: **axe no mide maquetacion**. Comprueba
 * contraste, nombres accesibles, jerarquia de encabezados y orden de foco, y
 * ninguna de esas cosas se rompe porque un ancho, un espaciado o una alineacion
 * cambien.
 *
 * **Que es capaz de ver.** Un elemento que se sale de su caja, una columna que
 * se apila cuando no debia, un espaciado que se duplica, un ancho maximo que
 * deja de aplicarse, una franja que pierde el fondo. Todo eso mueve pixeles.
 *
 * **Que NO es capaz de ver, y conviene no confundirlo.** Que el resultado sea
 * bonito, que la jerarquia visual tenga sentido o que el cambio fuera deseado.
 * Cuando esta prueba se pone roja **no dice que algo este mal**: dice que algo
 * cambio y que hay que mirarlo. Si el cambio era el que se buscaba, se
 * regeneran las referencias y el commit las lleva, que es lo que convierte el
 * cambio visual en algo revisable en un pull request.
 *
 * **Por que estas tres paginas.** Cubren las tres formas distintas que tiene el
 * sitio: la portada, que es la unica con franja de tinta y tarjetas; una pagina
 * larga de contenido; y un formulario. Anadir mas paginas del mismo molde
 * multiplica el mantenimiento sin anadir cobertura.
 *
 * **Por que los dos modos y los dos anchos.** El modo oscuro no es la misma
 * pantalla con otros colores: `marca.css` redefine cosas dentro de
 * `[data-tema='oscuro']`, y ahi es donde se han roto ya varias veces. Y el ancho
 * de escritorio esta por encima de `--ancho-max` a proposito, para que se vea si
 * el centrado deja de aplicarse.
 */

/** Por encima de `--ancho-max` (1140px), para que se compruebe el centrado. */
const ESCRITORIO = { width: 1280, height: 900 } as const;
/** El ancho estrecho corriente, donde el menu compacto y el apilado entran en juego. */
const MOVIL = { width: 390, height: 844 } as const;

const PAGINAS = [
  { nombre: 'portada', ruta: '/' },
  { nombre: 'como-funciona', ruta: '/como-funciona' },
  { nombre: 'registro', ruta: '/registro' },
] as const;

const ANCHOS = [
  { nombre: 'escritorio', tamano: ESCRITORIO },
  { nombre: 'movil', tamano: MOVIL },
] as const;

/**
 * El mismo gesto que usa `contraste.spec.ts`: se pulsa el conmutador y se espera
 * al atributo. No se escribe `data-tema` a mano porque entonces se comprobaria
 * una pantalla que ningun usuario puede alcanzar.
 */
const enOscuro = async (page: Page): Promise<void> => {
  await page.getByRole('button', { name: /oscuro/i }).click();
  await expect(page.locator('html')).toHaveAttribute('data-tema', 'oscuro');
};

/**
 * Sin esto la captura sale a veces con la tipografia de reserva.
 *
 * Las fuentes son propias —`.woff2` servidos por el sitio, no del sistema— y se
 * cargan despues del primer pintado. Una captura tomada en ese hueco mide con
 * otra letra, otra anchura y otro alto de linea: seria una prueba que falla una
 * de cada tantas veces sin que nadie haya tocado nada, que es la peor clase de
 * prueba.
 */
const conLasFuentesListas = async (page: Page): Promise<void> => {
  await page.evaluate(() => document.fonts.ready.then(() => undefined));
};

test.use({ locale: 'es-CO' });

for (const { nombre: ancho, tamano } of ANCHOS) {
  test.describe('maquetacion en ' + ancho, () => {
    test.use({ viewport: tamano });

    for (const { nombre: pagina, ruta } of PAGINAS) {
      for (const modo of ['claro', 'oscuro'] as const) {
        test(pagina + ' en modo ' + modo, async ({ page }) => {
          await page.goto(ruta);

          if (modo === 'oscuro') {
            await enOscuro(page);
          }

          await conLasFuentesListas(page);

          // La pagina entera y no solo lo visible: un descuadre debajo del pliegue
          // es igual de descuadre, y es donde menos mira nadie.
          await expect(page).toHaveScreenshot(pagina + '-' + modo + '-' + ancho + '.png', {
            fullPage: true,
          });
        });
      }
    }
  });
}
