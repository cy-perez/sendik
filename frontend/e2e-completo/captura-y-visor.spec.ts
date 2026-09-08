import { expect, test } from '@playwright/test';

import { publicarYAprobar } from './recorridos';

/**
 * La captura asistida y el visor giratorio, de punta a punta. HU-003.
 *
 * <p><strong>Es la prueba que la historia se dejo sin escribir.</strong> HU-003 se cerro el
 * 28 de agosto de 2026 con una salvedad anotada: el recorrido con camara simulada no se
 * escribio porque en aquella maquina no habia ni Java 25 ni Docker levantado, y una prueba
 * de Playwright que nadie ha visto pasar no vale mas que ninguna prueba. Lo unico que
 * cambio hoy es que las dos piezas estan.
 *
 * <p>Las tres cosas que hasta ahora no verificaba nada, y que solo se pueden verificar
 * aqui:
 *
 * <ul>
 *   <li><strong>El criterio 18 de verdad.</strong> Que el fotograma frontal salga del
 *       servidor ya pintado, con su `alt`. `spin-viewer.spec.ts` monta el componente en un
 *       TestBed de cliente y pasaria igual si el visor no llegara a renderizarse en
 *       servidor jamas, que es justo lo que el criterio prohibe.
 *   <li><strong>El recorte sobre pixeles de verdad.</strong> `photo-crop.spec.ts` prueba la
 *       aritmetica y el worker esta doblado en todo lo demas, asi que nadie habia visto una
 *       imagen entrar por la camara y salir recortada a 3:4 al otro lado del backend.
 *   <li><strong>Que la camara falsa de Chromium pase RN-019.</strong> Es la hipotesis por
 *       la que `CameraService.abrir` pide 1200 x 1600 en vertical. Se afirma dentro de
 *       `capturarLasOchoTomas`, que es quien tiene el `<video>` delante.
 * </ul>
 *
 * <p>Va en una sola prueba, como la del catalogo y por lo mismo: cada paso necesita el
 * estado del anterior, y trocearlo obligaria a fabricar ese estado llamando a la API, que
 * es justo lo que esta suite existe para no hacer.
 */
test.use({ locale: 'es-CO' });

/** Lo que deja el normalizador: 3:4 exactos, en el minimo de RN-019 (`photo-crop.ts`). */
const RECORTE = { ancho: 900, alto: 1200 };

test.describe('captura asistida y visor giratorio', () => {
  test('las ocho tomas del asistente llegan al visor, y la frontal sale del servidor', async ({
    page,
  }) => {
    const titulo = `Camisa capturada ${Date.now()}`;

    // Las ocho por el asistente, no por el campo de archivo. Es la unica diferencia con el
    // recorrido de HU-009, y es toda la razon de ser de esta suite.
    await publicarYAprobar(page, titulo, 'captura', 'camara');

    // Sin sesion desde aqui: `publicarYAprobar` acaba de cerrar la de quien modera, y la
    // ficha es publica.
    await page.goto('/catalogo');
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();

    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    const ficha = page.url();

    /*
     * Criterio 18, contra el HTML crudo.
     *
     * Se pide la misma direccion por fuera del navegador y se mira el texto que llega, sin
     * ejecutar una linea de JavaScript. Es lo unico que distingue «lo renderizo el
     * servidor» de «lo pinto la hidratacion», y la diferencia importa porque el criterio
     * existe para un buscador, que no hidrata nada.
     */
    const respuesta = await page.request.get(ficha);
    expect(respuesta.ok(), `La ficha respondio ${respuesta.status()}`).toBe(true);

    const html = await respuesta.text();

    // Sobre la etiqueta y no sobre el documento entero. `toContain('visor__foto')` a secas
    // pasaria con el visor sin renderizar: Angular incrusta el CSS critico en la cabecera,
    // y el nombre de la clase esta ahi tambien. Lo que no puede estar en ninguna hoja de
    // estilos es el `alt` con el titulo de este producto.
    const etiqueta = html.match(/<img[^>]*visor__foto[^>]*>/);

    expect(etiqueta, 'El HTML del servidor no trae el <img> del visor').not.toBeNull();
    expect(etiqueta?.[0]).toContain(`${titulo}, vista frontal`);

    // El visor se ofrece, y con la secuencia completa: ocho fotogramas son de 0 a 7.
    const visor = page.getByRole('slider', { name: titulo });
    await expect(visor).toBeVisible();
    await expect(visor).toHaveAttribute('aria-valuemax', '7');
    await expect(visor).toHaveAttribute('aria-valuenow', '0');

    /*
     * El recorte, sobre los pixeles que de verdad se guardaron.
     *
     * `naturalWidth` es lo que mide el archivo que sirvio el backend, no lo que la
     * plantilla declara en `width`: esos son 900 y 1200 fijos y estarian ahi aunque la
     * imagen midiera cualquier otra cosa. Este es el viaje entero —camara, worker, subida,
     * validacion del servidor, disco y vuelta— medido en el unico sitio donde se puede.
     */
    const foto = visor.locator('img.visor__foto');
    const medida = await foto.evaluate(async (imagen: HTMLImageElement) => {
      await imagen.decode();
      return { ancho: imagen.naturalWidth, alto: imagen.naturalHeight };
    });

    expect(medida).toEqual(RECORTE);
  });
});
