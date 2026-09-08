import { expect, test } from '@playwright/test';

import {
  MESES_DE_GARANTIA,
  RUTA_MIS_PUBLICACIONES,
  dejarUnaVendedoraVerificada,
  ingresar,
  publicarYAprobar,
  publicarYEnviarARevision,
  retirarDeRevision,
  salirSiHaySesion,
} from './recorridos';

/**
 * El catálogo público, de punta a punta. HU-009.
 *
 * <p><strong>Es el recorrido que cierra la Fase 2.</strong> Hasta ahora terminaba en la
 * bandeja del moderador: un vendedor publicaba, alguien aprobaba y ahí se acababa, porque
 * la publicación aprobada no la veía nadie. Aquí sigue un paso más, que es el que da
 * sentido a todo lo anterior — alguien **sin cuenta** entra, la encuentra y la abre.
 *
 * <p>Lo que solo se puede demostrar aquí y en ningún otro sitio:
 *
 * <ul>
 *   <li>Que RN-068 se cumple contra la base de datos real y no contra un doble: lo que no
 *       está aprobado no aparece, ni siquiera para su dueño con la sesión abierta.
 *   <li>Que el HTML que sale del servidor trae el título del producto ya resuelto. Las
 *       pruebas de componente montan el componente y las de navegador ejecutan
 *       JavaScript; ninguna ve lo que recibe un buscador con datos de verdad.
 *   <li>Que las cuatro rutas públicas existen con `FEATURE_CATALOG` encendida y que el
 *       contrato entre las dos mitades —el cursor, la forma del tramo, el perfil sin datos
 *       personales— se sostiene.
 * </ul>
 */
test.use({ locale: 'es-CO' });

const RUTA_CATALOGO = '/catalogo';

test.describe('catálogo público', () => {
  /**
   * El camino que cierra la fase.
   *
   * <p>Va en una sola prueba porque cada paso necesita el estado del anterior, y trocearlo
   * obligaría a fabricar ese estado llamando a la API, que es justo lo que esta suite
   * existe para no hacer.
   */
  test('lo aprobado aparece en el catalogo y lo encuentra alguien sin cuenta', async ({ page }) => {
    const titulo = `Camisa del catalogo ${Date.now()}`;

    await publicarYAprobar(page, titulo, 'catalogo');

    // Sin sesión desde aquí: es la mitad del criterio. Se comprueba de verdad y no se da
    // por hecho, porque `publicarYAprobar` acaba de cerrar la de quien moderaba.
    await page.goto(RUTA_CATALOGO);
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();

    await expect(page.getByRole('heading', { name: 'Qué se está vendiendo' })).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    // Y se abre. La ficha es el destino de todo el recorrido.
    await page.getByRole('link').filter({ hasText: titulo }).first().click();

    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();
    await expect(page.getByText('Usada dos veces, sin manchas ni descosidos.')).toBeVisible();
    await expect(page.getByText('Como nuevo')).toBeVisible();
  });

  /**
   * RN-068 contra la base real.
   *
   * <p>Lo que espera revisión no está en el catálogo **para nadie**, y el «para nadie»
   * incluye a su dueña: el catálogo enseña lo mismo a todo el mundo y ella ve lo suyo en
   * su panel. Es la regla que contesta «¿por qué desapareció mi publicación?», y en un
   * doble de memoria es una línea de Java; aquí es la consulta que de verdad corre.
   */
  test('lo que espera revision no esta en el catalogo, ni para su dueña', async ({ page }) => {
    const titulo = `Camisa sin aprobar ${Date.now()}`;

    const vendedora = await dejarUnaVendedoraVerificada(page, 'sin-aprobar');
    const id = await publicarYEnviarARevision(page, titulo);

    // Con su sesión abierta, que es el caso que más se presta a una excepción.
    await page.goto(RUTA_CATALOGO);
    await expect(page.getByRole('heading', { name: 'Qué se está vendiendo' })).toBeVisible();
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);

    // Y sin sesión tampoco.
    await salirSiHaySesion(page);
    await page.goto(RUTA_CATALOGO);
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);

    // Esta prueba necesita algo esperando revisión, así que lo recoge al terminar: la cola
    // del moderador es compartida y lo que se deja ahí estorba a la corrida siguiente.
    await ingresar(page, vendedora);
    await retirarDeRevision(page, id);
  });

  /**
   * Criterio 16. El HTML servido trae el título del producto, no el de la plantilla.
   *
   * <p><strong>Esta es la prueba por la que existe el renderizado en servidor.</strong> De
   * ese tráfico vive el marketplace: lo que un buscador indexa y lo que se ve al compartir
   * el enlace por WhatsApp es este HTML, sin ejecutar una línea de JavaScript. Se pide con
   * `request` y no con `page` justamente por eso — un `page.goto` pasaría igual aunque el
   * título lo pusiera el navegador al hidratar.
   */
  test('el HTML servido de la ficha trae el titulo del producto', async ({ page, request }) => {
    const titulo = `Camisa indexable ${Date.now()}`;

    await publicarYAprobar(page, titulo, 'indexable');

    await page.goto(RUTA_CATALOGO);
    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    const direccion = new URL(page.url()).pathname;
    const html = await (await request.get(direccion)).text();

    // El cuerpo primero: si esto falla es que el servidor no llego a pedir los datos, y
    // entonces lo del titulo es una consecuencia y no la causa.
    expect(html).toContain('Usada dos veces, sin manchas ni descosidos.');
    expect(/<title>([^<]*)<\/title>/.exec(html)?.[1]).toBe(titulo);

    // El canónico apunta a esta ficha y a ninguna otra.
    const canonico = /<link[^>]+rel="canonical"[^>]*>/.exec(html)?.[0] ?? '';
    expect(canonico).toContain(direccion);
  });

  /**
   * RN-067: la garantía del fabricante, sobre un dispositivo de verdad.
   *
   * <p><strong>Este es el hueco que ninguna otra prueba podía ver.</strong> Las de unidad
   * montan la ficha con una respuesta escrita a mano, así que demuestran que la pantalla
   * pinta lo que le den; lo que no demuestran es que `warrantyMonths` sobreviva el viaje —
   * el formulario que lo declara, la columna que lo guarda, el mapeador de la respuesta
   * pública, el catálogo—. Cualquiera de esos eslabones podría dejarlo caer y las 843
   * pruebas seguirían verdes con la ficha vacía.
   *
   * <p>Se afirma sobre el HTML servido y no sobre la pantalla hidratada por la misma razón
   * que el título: la ficha es lo que se comparte por enlace, y la frase que reparte la
   * responsabilidad no puede aparecer solo cuando corre JavaScript.
   */
  test('la ficha de un dispositivo dice la garantia y quien responde por ella', async ({
    page,
    request,
  }) => {
    const titulo = `Telefono con garantia ${Date.now()}`;

    await publicarYAprobar(page, titulo, 'garantia', 'galeria', 'tecnologia');

    await page.goto(RUTA_CATALOGO);
    await page.getByRole('link').filter({ hasText: titulo }).first().click();
    await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

    // Los meses, la frase y el enlace. Los tres o ninguno: una ficha que anuncia doce
    // meses sin decir de quién son deja al lector suponiendo que responde Sendik, que es
    // exactamente lo que RN-067 prohíbe.
    await expect(page.getByText('Garantía del fabricante')).toBeVisible();
    await expect(
      page.getByText(`${MESES_DE_GARANTIA} meses, declarados por el vendedor`),
    ).toBeVisible();
    await expect(
      page.getByText('Quien responde por esta garantía es el vendedor, no Sendik.'),
    ).toBeVisible();

    // El enlace lleva al documento que tiene la cláusula (RN-057). Acotado a la ficha:
    // el pie del sitio enlaza a los términos en todas las páginas.
    await expect(
      page
        .locator('.ficha__garantia')
        .getByRole('link', { name: 'Leer los términos y condiciones' }),
    ).toHaveAttribute('href', '/terminos');

    // Y en el HTML que sale del servidor, sin ejecutar nada.
    const html = await (await request.get(new URL(page.url()).pathname)).text();
    expect(html).toContain('Garantía del fabricante');
    expect(html).toContain(`${MESES_DE_GARANTIA} meses, declarados por el vendedor`);
    expect(html).toContain('Quien responde por esta garantía es el vendedor, no Sendik.');

    // La palabra que no puede estar cerca (glosario, RN-067). **Acotada a la ficha, no al
    // HTML entero:** el paquete de traducciones viaja incrustado en la página y ahí
    // «Respaldo» está a propósito —es la promesa central del producto y la nombra medio
    // sitio—. Afirmarlo sobre todo el documento no probaría nada y fallaría siempre.
    expect(await page.locator('.ficha').innerText()).not.toMatch(/respaldo/i);
  });

  /**
   * Criterios 15, 18, 19 y 21: de la ficha al perfil, y lo que el perfil enseña.
   *
   * <p>El criterio 19 —que no salga nada personal— se comprueba con el correo de la
   * vendedora, que esta prueba conoce porque acaba de crearla. Es la única forma honesta de
   * comprobarlo: buscar un correo cualquiera no demostraría nada.
   */
  test('de la ficha se llega al perfil del vendedor, y no expone datos personales', async ({
    page,
  }) => {
    const titulo = `Camisa con perfil ${Date.now()}`;
    const correo = await publicarYAprobar(page, titulo, 'perfil');

    await page.goto(RUTA_CATALOGO);
    await page.getByRole('link').filter({ hasText: titulo }).first().click();

    // La insignia: la vendedora está verificada, así que el sello está a la vista.
    await expect(page.getByText('Vendedor verificado').first()).toBeVisible();

    await page.getByRole('link', { name: 'Ana María' }).click();

    await expect(page.getByRole('heading', { name: 'Ana María' })).toBeVisible();
    await expect(page.getByText('Sendik confirmó su identidad y su cuenta bancaria')).toBeVisible();
    // Su escaparate: lo que acaba de publicar está ahí.
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    // Criterio 19. Ni el correo ni el documento salen de identity hacia esta pantalla.
    await expect(page.getByText(correo)).toHaveCount(0);
  });

  /**
   * Criterio 13: una ficha que no existe responde igual que una que dejó de estar
   * publicada, y las dos con la página de no encontrada.
   */
  test('una ficha que no existe lo dice sin confirmar que existio', async ({ page }) => {
    await salirSiHaySesion(page);
    await page.goto('/producto/00000000-0000-7000-8000-000000000000');

    await expect(page.getByText('ya no está disponible')).toBeVisible();
  });

  /**
   * Criterio 8, contra el árbol sembrado: al abrir una categoría solo sale lo suyo.
   *
   * <p>La publicación es de «Camisas y blusas», que cuelga de «tops», así que tiene que
   * aparecer ahí y no en «Jeans», que cuelga de «bottoms». Se eligió una categoría de otra
   * familia a propósito: comprueba de paso que el filtro no se queda en el primer nivel del
   * árbol. Con el filtro roto —o ignorado— cada afirmación pasaría por separado; solo
   * juntas dicen algo.
   */
  test('una categoria trae lo suyo y no lo de su hermana', async ({ page }) => {
    const titulo = `Camisa por categoria ${Date.now()}`;
    await publicarYAprobar(page, titulo, 'categoria');

    await page.goto('/catalogo/tops/camisas-y-blusas');
    await expect(page.getByRole('link').filter({ hasText: titulo }).first()).toBeVisible();

    await page.goto('/catalogo/bottoms/jeans');
    await expect(page.getByRole('link').filter({ hasText: titulo })).toHaveCount(0);
  });

  /**
   * El vendedor sigue viendo lo suyo en su panel aunque el catálogo no lo enseñe.
   *
   * <p>Es la otra mitad de RN-068 y lo que hace que la regla no sea una pérdida: lo que
   * desaparece del escaparate no desaparece de su dueño.
   */
  test('el panel del vendedor si muestra lo que el catalogo esconde', async ({ page }) => {
    const titulo = `Camisa solo mia ${Date.now()}`;

    await dejarUnaVendedoraVerificada(page, 'panel');
    const id = await publicarYEnviarARevision(page, titulo);

    await page.goto(RUTA_MIS_PUBLICACIONES);
    await expect(page.getByText(titulo).first()).toBeVisible();

    // Las cifras del panel, HU-012. La vendedora nace nueva en cada corrida, asi que estas
    // cuentan: acaba de enviar una y no tiene ninguna de lo demas. Los siete estados estan,
    // y **el cero se dice** en vez de desaparecer, que es el criterio 2 visto de punta a
    // punta: la cifra la calcula el servidor y la pinta la pantalla.
    // Por rol y no por clase de CSS: esta suite existe para ver el contrato entre las dos
    // mitades, y amarrarla a un nombre BEM la rompe con un cambio que no toca ese contrato.
    // `dt` y `dd` son `term` y `definition`, y van emparejados por posicion.
    const nombres = page.getByRole('term');
    const numeros = page.getByRole('definition');
    await expect(nombres).toHaveCount(7);

    const cifraDe = async (estado: string) =>
      numeros.nth((await nombres.allInnerTexts()).indexOf(estado)).innerText();

    expect(await cifraDe('En revisión')).toBe('1');
    expect(await cifraDe('Vendida')).toBe('0');

    // Lo mismo: lo que espera revisión se recoge, o se queda en la cola para siempre.
    await retirarDeRevision(page, id);
  });

  /** Criterio 7 de HU-012: sin sesión el panel no existe. Se explica y se ofrece entrar. */
  test('sin sesion el panel no existe y se ofrece entrar, criterio 7', async ({ page }) => {
    await salirSiHaySesion(page);

    await page.goto(RUTA_MIS_PUBLICACIONES);

    await expect(page.getByRole('heading', { name: 'Mis publicaciones' })).toBeVisible();
    await expect(page.getByText('Entra para verlas')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).first()).toBeVisible();

    // Y ninguna cifra: no hay de quien contarlas.
    await expect(page.getByRole('term')).toHaveCount(0);
  });
});
