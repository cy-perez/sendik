import { expect, test } from '@playwright/test';

import { publicarYAprobar } from './recorridos';

/**
 * La búsqueda y los filtros, de punta a punta. HU-014.
 *
 * <p>Lo que solo se puede demostrar aquí y en ningún otro sitio:
 *
 * <ul>
 *   <li>Que lo que se escribe en la caja llega a PostgreSQL y encuentra lo que hay. Las
 *       pruebas de componente simulan la respuesta y las de aplicación usan un doble que
 *       ni lematiza ni puntúa; ninguna de las dos ve el contrato entre las dos mitades.
 *   <li>Que la dirección se puede compartir: abrirla de nuevo devuelve exactamente lo
 *       mismo, que es el criterio 15 y no se puede probar montando un componente.
 *   <li>Que el vacío honesto de RN-086 sale con los filtros a la vista y con la salida
 *       para quitarlos, contra datos de verdad y no contra un tramo vacío inventado.
 * </ul>
 *
 * <p>Todo sin sesión: RN-081 dice que en la búsqueda se ve lo mismo con cuenta y sin ella.
 */
test.use({ locale: 'es-CO' });

const RUTA_CATALOGO = '/catalogo';

test.describe('búsqueda del catálogo', () => {
  test('lo aprobado se encuentra escribiendo, y la dirección se puede compartir', async ({
    page,
  }) => {
    // Una palabra inventada en el título. La base arrastra publicaciones de otras
    // corridas, y buscar «camisa» traería las de todas.
    const distintivo = `zurdaco${Date.now()}`;
    const titulo = `Camisa ${distintivo} de lino`;

    await publicarYAprobar(page, titulo, 'busqueda');

    // Sin sesión desde aquí: `publicarYAprobar` acaba de cerrar la de quien moderaba.
    await page.goto(RUTA_CATALOGO);
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();

    await page.getByRole('searchbox', { name: 'Buscar en el catálogo' }).fill(distintivo);
    await page.getByRole('button', { name: 'Buscar' }).click();

    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    // Criterio 15: la dirección lleva lo que se pidió, y abrirla otra vez lo devuelve.
    expect(page.url()).toContain(`q=${distintivo}`);

    const compartida = page.url();
    await page.goto(RUTA_CATALOGO);
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(1);

    await page.goto(compartida);
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();
  });

  /**
   * RN-086. El vacío se dice, se ve qué está puesto y hay por dónde salir.
   *
   * <p>Se filtra sobre una búsqueda que sí tiene resultados, y no sobre una que no tiene
   * ninguno: así lo que vacía la pantalla es el filtro y no el texto, que es el caso que la
   * regla describe —«un filtro activo e invisible se lee como Sendik no tiene nada»—.
   */
  test('filtrar hasta el vacío lo dice y ofrece la salida', async ({ page }) => {
    const distintivo = `nubrafo${Date.now()}`;
    const titulo = `Camisa ${distintivo} de lino`;

    // El recorrido publica en beige: filtrar por negro tiene que dejarlo fuera.
    await publicarYAprobar(page, titulo, 'vacio');

    await page.goto(`${RUTA_CATALOGO}?q=${distintivo}`);
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    await page.goto(`${RUTA_CATALOGO}?q=${distintivo}&color=BLACK`);

    await expect(page.getByText('No encontramos nada para')).toBeVisible();

    // El filtro puesto se ve, aunque el panel esté cerrado.
    const ficha = page.getByRole('button', { name: /Quitar el filtro Negro/ });
    await expect(ficha).toBeVisible();

    // Y la salida devuelve lo que había, conservando lo que se estaba buscando.
    await page.getByRole('button', { name: 'Quitar los filtros' }).first().click();

    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();
    expect(page.url()).toContain(`q=${distintivo}`);
    expect(page.url()).not.toContain('color=');
  });

  /**
   * RN-083. Escribir en la caja busca en todo el catálogo, no dentro de donde se estaba.
   *
   * <p>Es el vacío engañoso que la regla evita: quien busca sin darse cuenta de que estaba
   * dentro de una categoría leería «no hay» donde sí hay.
   */
  test('buscar desde una categoria sale de ella', async ({ page }) => {
    const distintivo = `tulqueno${Date.now()}`;
    const titulo = `Camisa ${distintivo} de lino`;

    await publicarYAprobar(page, titulo, 'todocat');

    // Una familia donde no está lo que se publicó: la publicación va a camisas.
    await page.goto(`${RUTA_CATALOGO}/footwear`);
    await page.getByRole('searchbox', { name: 'Buscar en el catálogo' }).fill(distintivo);
    await page.getByRole('button', { name: 'Buscar' }).click();

    await expect(page).toHaveURL(new RegExp(`/catalogo\\?q=${distintivo}$`));
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();
  });
});
