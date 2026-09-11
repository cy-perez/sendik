import { expect, test } from '@playwright/test';

import { correoNuevo, ingresar, publicarYAprobar, registrar, salirSiHaySesion } from './recorridos';

/**
 * El carrito, de punta a punta. HU-015.
 *
 * <p><strong>Lo que solo se puede demostrar aquí y en ningún otro sitio:</strong>
 *
 * <ul>
 *   <li>Que el carrito sin sesión sobrevive a la recarga y a la navegación real, y que los
 *       grupos y subtotales que ve quien no ha entrado los arma <strong>el servidor</strong>
 *       por la ruta pública. Una prueba de componente simula esa respuesta y no demuestra que
 *       exista.
 *   <li>Que la fusión se pregunta y, al aceptarla, el carrito pasa de verdad a la cuenta.
 *       Entre pulsar y entrar hay una recarga: el token vive en memoria y se pierde, y la
 *       sesión se recupera con la cookie de refresco. Una prueba de componente pone la sesión
 *       a mano y nunca pasa por ese hueco.
 *   <li>Que RN-094 se cumple contra la base real: se pausa la publicación desde la cuenta de
 *       quien vende y la fila <strong>sigue en el carrito</strong>, apagada y fuera del
 *       subtotal, sin que nadie la haya quitado. Con dobles, eso solo demostraría que el
 *       doble no filtra.
 *   <li>Que el acuerdo sobre el 403 de RN-092 entre las dos mitades se sostiene.
 * </ul>
 */
test.use({ locale: 'es-CO' });

const RUTA_CARRITO = '/carrito';

test.describe('carrito', () => {
  /**
   * El ciclo entero, en una sola prueba porque cada paso necesita el estado del anterior.
   * Trocearlo obligaría a fabricar ese estado llamando a la API, que es lo que esta suite
   * existe para no hacer.
   */
  test('se arma sin sesion, se fusiona al entrar y conserva a la vista lo que deja de estar disponible', async ({
    page,
  }) => {
    const marca = Date.now();
    const tituloUna = `Camisa de carrito ${marca}`;
    const tituloOtra = `Camisa de carrito otra ${marca}`;

    // Dos publicaciones de dos vendedoras distintas: es lo que hace falta para el criterio
    // 14 -un grupo por vendedor- y para el 15 -el aviso de que seran dos pedidos-.
    const vendedoraUna = await publicarYAprobar(page, tituloUna, `carrito-una-${marca}`);
    await publicarYAprobar(page, tituloOtra, `carrito-otra-${marca}`);
    await salirSiHaySesion(page);

    // --- Sin sesión: se agrega y no se pide entrar (criterio 8) ---
    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: tituloUna }).first().click();
    await expect(page.getByRole('heading', { name: tituloUna })).toBeVisible();

    await page.getByRole('button', { name: 'Agregar al carrito' }).click();

    // Sigue en el mismo sitio: no hubo ingreso de por medio, que es toda la diferencia con
    // el control de favorito de HU-011.
    await expect(page.getByRole('button', { name: 'Quitar del carrito' })).toBeVisible();

    // Y sobrevive a la recarga: vive en el navegador, no en memoria (criterio 8).
    await page.reload();
    await expect(page.getByRole('button', { name: 'Quitar del carrito' })).toBeVisible();

    // El segundo producto, de la otra vendedora.
    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: tituloOtra }).first().click();
    await page.getByRole('button', { name: 'Agregar al carrito' }).click();
    await expect(page.getByRole('button', { name: 'Quitar del carrito' })).toBeVisible();

    // --- El carrito sin sesión: dos grupos, dos subtotales (criterios 13, 14 y 15) ---
    await page.goto(RUTA_CARRITO);
    await expect(page.getByRole('heading', { name: 'Tu carrito' })).toBeVisible();
    await expect(page.getByText(tituloUna)).toBeVisible();
    await expect(page.getByText(tituloOtra)).toBeVisible();
    await expect(page.getByText('Cada vendedor es un pedido')).toBeVisible();

    // Criterio 16: subtotal y nunca total, y la frase del envio.
    await expect(page.getByText('Subtotal').first()).toBeVisible();
    await expect(page.getByText('El costo de envío se calcula al comprar').first()).toBeVisible();
    await expect(page.getByText(/\bTotal\b/)).toHaveCount(0);

    // --- Se entra, y se pregunta antes de fusionar (criterios 9 y 12) ---
    const compradora = correoNuevo('compradora-carrito');
    await registrar(page, compradora, 'Quien Compra');
    await ingresar(page, compradora);

    await page.goto(RUTA_CARRITO);

    // **Se pregunta y no se hace sola.** Es ADR-0037: siendo el carrito anonimo, el servidor
    // no puede distinguir «vuelve el mismo» de «llega otro», asi que la union la dispara una
    // persona.
    const pregunta = page.getByText('Encontramos');
    await expect(pregunta).toBeVisible();

    await page.getByRole('button', { name: 'Agregarlos' }).click();

    // Y ahora si esta en la cuenta: sobrevive a la recarga con sesion, que es lo que separa
    // «se pinto» de «se guardo».
    await expect(page.getByText(tituloUna)).toBeVisible();
    await page.reload();
    await expect(page.getByText(tituloUna)).toBeVisible();
    await expect(page.getByText(tituloOtra)).toBeVisible();

    // La pregunta no vuelve: el carrito del navegador se consumio y se borro (criterio 11).
    await expect(page.getByText('Encontramos')).toHaveCount(0);

    // --- Se pausa una publicación y la fila sigue a la vista (criterios 21 y 22) ---
    //
    // Es la diferencia exacta con la lista de favoritos, donde RN-071 la esconde. Aqui
    // RN-094 la conserva apagada: un carrito que adelgaza en silencio justo antes de pagar
    // no es lo mismo que un favorito que desaparece.
    await salirSiHaySesion(page);
    await ingresar(page, vendedoraUna);

    // Se pausa desde la fila de la lista, acotado a esta publicacion: la cuenta puede tener
    // otras y un `getByRole` suelto pausaria la primera que encuentre.
    await page.goto('/mis-publicaciones');
    const fila = page.getByRole('listitem').filter({ hasText: tituloUna });
    await fila.getByRole('button', { name: 'Pausar' }).click();
    await expect(fila.getByRole('button', { name: 'Reactivar' })).toBeVisible();

    await salirSiHaySesion(page);
    await ingresar(page, compradora);
    await page.goto(RUTA_CARRITO);

    await expect(page.getByText(tituloUna)).toBeVisible();
    await expect(page.getByText('Ya no está disponible')).toBeVisible();

    // Y se puede quitar: un carrito donde lo vendido no se pudiera sacar acumularia basura.
    //
    // **Acotado a su fila y no `.first()`.** El orden es lo mas reciente primero, asi que la
    // pausada -que se agrego antes- es la segunda: un `.first()` quitaba la que si seguia
    // disponible y la prueba fallaba diciendo lo contrario de lo que pasaba.
    const apagada = page.getByRole('listitem').filter({ hasText: 'Ya no está disponible' });
    await apagada.getByRole('button', { name: 'Quitar' }).click();

    await expect(page.getByText('Ya no está disponible')).toHaveCount(0);
    // Y lo que seguia disponible sigue ahi: se quito una fila, no el carrito.
    await expect(page.getByText(tituloOtra)).toBeVisible();
  });

  /**
   * RN-092 contra el servidor de verdad.
   *
   * <p>Una prueba de componente inventa ese 403 al simular la respuesta; esto comprueba que
   * el acuerdo entre las dos mitades existe.
   */
  test('no ofrece el control sobre la publicacion propia', async ({ page }) => {
    const marca = Date.now();
    const titulo = `Camisa propia ${marca}`;
    const vendedora = await publicarYAprobar(page, titulo, `carrito-propia-${marca}`);

    await ingresar(page, vendedora);
    await page.goto('/catalogo');
    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    // El control no se ofrece, y eso lo decide el servidor: la sesion que guarda el
    // navegador no lleva el identificador de la cuenta, asi que la pantalla no puede
    // comparar contra el vendedor.
    await expect(page.getByRole('button', { name: 'Agregar al carrito' })).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Quitar del carrito' })).toHaveCount(0);
  });

  /** El carrito vacío es una pantalla, y lo ve todo el mundo la primera vez (criterio 19). */
  test('la pantalla vacia se ve sin sesion y sin pedir nada', async ({ page }) => {
    await salirSiHaySesion(page);

    await page.goto(RUTA_CARRITO);

    await expect(page.getByRole('heading', { name: 'Tu carrito está vacío' })).toBeVisible();
    await expect(page.getByText('quien pague primero se lo lleva')).toBeVisible();
  });
});
