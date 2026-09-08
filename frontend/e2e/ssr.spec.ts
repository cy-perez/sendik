import { expect, test } from '@playwright/test';

/**
 * Estas comprobaciones se hacen sobre el HTML crudo, sin ejecutar JavaScript.
 * Es la unica forma de demostrar lo que promete ADR-0006: que el buscador y la
 * vista previa de WhatsApp reciben la pagina ya montada. Un `page.goto` pasaria
 * igual aunque el servidor entregara un documento vacio y todo lo pintara el
 * navegador despues.
 */
test.describe('renderizado en servidor', () => {
  /**
   * HU-009, criterio 16. El catalogo es la primera pagina que existe para que un buscador
   * la lea, asi que su titulo y su descripcion tienen que venir en el HTML servido y no
   * ponerse al hidratar.
   */
  test('el catalogo llega con su titulo y su descripcion resueltos', async ({ request }) => {
    const respuesta = await request.get('/catalogo');
    const html = await respuesta.text();

    expect(respuesta.status()).toBe(200);
    expect(html).toContain('<title>Catálogo</title>');
    expect(html).toContain('Moda nueva y de segunda y tecnología nueva');
  });

  /**
   * La direccion canonica, en toda pagina.
   *
   * <p>Se comprueba con una cadena de consulta puesta a proposito: sin recortarla, la
   * misma pagina alcanzada desde una campana se indexa como varias y el buscador reparte
   * entre ellas la relevancia que deberia ir a una sola. Es justo lo que el canonico
   * existe para evitar, asi que la prueba que no lleve parametros no probaria nada.
   */
  test('sirve una sola direccion canonica, sin la cadena de consulta', async ({ request }) => {
    const html = await (await request.get('/catalogo?utm_source=una-campana')).text();

    const canonico = /<link[^>]+rel="canonical"[^>]*>/.exec(html)?.[0] ?? '';

    expect(canonico).toContain('/catalogo');
    expect(canonico).not.toContain('utm_source');
  });

  test('entrega el HTML en espanol por omision', async ({ request }) => {
    const response = await request.get('/');
    const html = await response.text();

    expect(response.status()).toBe(200);
    expect(html).toContain('lang="es"');
    expect(html).toContain('Compra y vende de forma ágil y segura');
  });

  /**
   * HU-004, criterio 15. Sin esto, mover los bloques a @defer vaciaria el HTML
   * servido y toda la suite seguiria en verde: las pruebas de componente montan
   * el componente, y las de navegador ejecutan JavaScript. Esta es la unica que
   * mira lo que recibe un buscador.
   */
  test('la portada llega entera en el HTML, sin ejecutar JavaScript', async ({ request }) => {
    const html = await (await request.get('/')).text();

    // Hero
    expect(html).toContain('Compra y vende de forma ágil y segura');
    expect(html).toContain('Publicar es gratis');
    // Los tres pasos
    expect(html).toContain('Publicas tu prenda');
    expect(html).toContain('Alguien la compra');
    expect(html).toContain('Cobras al confirmarse la entrega');
    // Las tres tarjetas de confianza
    expect(html).toContain('El pago queda retenido');
    expect(html).toContain('Vendedores verificados');
    expect(html).toContain('Publicaciones revisadas');
    // Pie
    expect(html).toContain('Sendik S.A.S.');
    expect(html).toContain('000000000-0');
    expect(html).toContain('mailto:soporte@example.test');
  });

  /**
   * Criterio 2, sobre la pagina completa y no solo sobre la portada: una sola
   * llamada a la accion por pantalla, cabecera y pie incluidos. Es la erosion
   * que el criterio quiere evitar y que una prueba de componente no ve.
   */
  test('solo hay una llamada a la accion en toda la pagina', async ({ request }) => {
    const html = await (await request.get('/')).text();
    const elementos = html.match(/<[a-z]+[^>]*\bclass="[^"]*\bbtn-primario\b[^"]*"/g) ?? [];

    expect(elementos).toHaveLength(1);
  });

  /**
   * El bronce es el acento de Sendik y solo puede estar en un sitio: la insignia
   * de vendedor verificado. Que no aparezca como relleno de ningun boton es lo
   * que hace que signifique algo cuando aparezca de verdad.
   *
   * <p>Se comprueba sobre el HTML servido porque el fallo tipico es copiar una
   * clase de una pantalla a otra, y eso se ve en el marcado antes que en pantalla.
   */
  test('ningun boton lleva el acento bronce', async ({ request }) => {
    const html = await (await request.get('/')).text();

    expect(html).not.toMatch(/class="[^"]*\bbtn-cta\b/);
    expect(html).not.toMatch(/class="[^"]*\bbtn-acento\b/);
  });

  /**
   * Criterio 9, tambien en el arbol ingles: alli tampoco se promete lo que no hay.
   *
   * <p>Se mira el texto visible, no el HTML crudo. El documento trae los scripts
   * de Angular y el estado transferido, y ahi la palabra `return` de cualquier
   * funcion haria fallar la prueba sin que la portada prometa nada.
   */
  test('la portada no promete devoluciones en ninguno de los dos idiomas', async ({ request }) => {
    for (const idioma of ['es', 'en']) {
      const html = await (
        await request.get('/', { headers: { 'Accept-Language': idioma } })
      ).text();
      const visible = html
        .slice(html.indexOf('<body'))
        .replace(/<script[\s\S]*?<\/script>/gi, ' ')
        .replace(/<style[\s\S]*?<\/style>/gi, ' ')
        .replace(/<[^>]+>/g, ' ');

      expect(visible, idioma).not.toMatch(/devoluci[oó]n|reembolso|\brefunds?\b|\breturns?\b/i);
    }
  });

  test('entrega el HTML ya traducido segun Accept-Language', async ({ request }) => {
    const response = await request.get('/', {
      headers: { 'Accept-Language': 'en-US,en;q=0.9' },
    });
    const html = await response.text();

    expect(response.status()).toBe(200);
    expect(html).toContain('lang="en"');
    expect(html).toContain('Buy and sell quickly and safely');
    expect(html).not.toContain('Compra y vende');
  });

  /**
   * Criterio 16, la mitad que la paridad de traducciones no puede ver: que la
   * portada entera este traducida, no solo el titular, y que la direccion no
   * cambie con el idioma.
   */
  test('la portada entera llega traducida al ingles, en la misma direccion', async ({
    request,
  }) => {
    const response = await request.get('/', { headers: { 'Accept-Language': 'en' } });
    const html = await response.text();

    expect(new URL(response.url()).pathname).toBe('/');
    expect(html).toContain('You list your item');
    expect(html).toContain('Reviewed listings');
    expect(html).toContain('Tax ID');
    // Ningun texto en espanol se quedo sin traducir por el camino.
    expect(html).not.toContain('Publicas tu prenda');
    expect(html).not.toContain('Publicaciones revisadas');
  });

  test('el idioma elegido gana sobre el del navegador', async ({ request }) => {
    const response = await request.get('/', {
      headers: { 'Accept-Language': 'en-US,en;q=0.9', Cookie: 'sendik_locale=es' },
    });

    expect(await response.text()).toContain('lang="es"');
  });

  test('el tema llega resuelto en el HTML, sin parpadeo', async ({ request }) => {
    const claro = await request.get('/');
    expect(await claro.text()).toContain('data-tema="claro"');

    const oscuro = await request.get('/', { headers: { Cookie: 'sendik_theme=dark' } });
    expect(await oscuro.text()).toContain('data-tema="oscuro"');
  });

  test('los metadatos de la pagina salen traducidos', async ({ request }) => {
    const response = await request.get('/', { headers: { 'Accept-Language': 'en' } });
    const html = await response.text();

    expect(html).toContain('<meta name="description" content="Buy and sell quickly and safely');
    expect(html).toContain('<meta property="og:locale" content="en">');
  });

  // Un "no existe" servido con 200 es un soft 404: el buscador lo indexa como
  // pagina buena y termina ofreciendo direcciones rotas.
  test('una direccion inexistente responde 404 de verdad', async ({ request }) => {
    const response = await request.get('/esta-ruta-no-existe');

    expect(response.status()).toBe(404);
    expect(await response.text()).toContain('Esta página no existe');
  });

  /**
   * El juego de cabeceras que Cloud Run pone delante de cada peticion, entero.
   *
   * <p>**Entero es la palabra.** `@angular/ssr` renuncia al renderizado en servidor si
   * ve UNA sola cabecera `x-forwarded-*` que no este declarada como confiable, asi que
   * una prueba que mande menos de las que manda el proxy real pasa sin demostrar nada.
   * Es exactamente lo que ocurrio el 2 de septiembre de 2026: la primera version de
   * esta prueba mandaba solo `x-forwarded-for`, quedo verde, y el despliegue siguiente
   * seguia sirviendo la pagina vacia por `x-forwarded-proto`.
   *
   * <p>Al agregar una cabecera aqui hay que agregarla tambien a `trustProxyHeaders` en
   * src/server.ts, o esta prueba se pone roja. Que es justo lo que tiene que pasar.
   */
  const CABECERAS_DE_CLOUD_RUN = {
    'X-Forwarded-For': '203.0.113.7',
    'X-Forwarded-Proto': 'https',
  };

  /**
   * Detras de un proxy la pagina tiene que seguir llegando renderizada.
   *
   * <p>Es el fallo que dejo `dev.sendik.co` en blanco: sin renderizado en servidor la
   * raiz llega vacia. El resto de la suite no lo vio porque pide sin proxy delante.
   *
   * <p>No basta con mirar el estado: la respuesta es 200 en los dos casos. Lo que
   * distingue el fallo es que dentro de `<sendik-root>` no hay nada.
   */
  test('la pagina llega renderizada aunque venga por un proxy', async ({ request }) => {
    const respuesta = await request.get('/', { headers: CABECERAS_DE_CLOUD_RUN });
    const html = await respuesta.text();

    expect(respuesta.status()).toBe(200);
    expect(html).not.toContain('<sendik-root></sendik-root>');
    expect(html).toContain('Compra y vende de forma ágil y segura');
  });

  /**
   * La otra mitad de lo mismo, y la que de verdad explica la pagina en blanco:
   * `APP_CONFIG` viaja en el estado transferido y sin el la aplicacion no arranca
   * en el navegador (ADR-0021). Si el estado no viene, lo que ve el visitante no es
   * una pagina a medias sino ninguna.
   */
  test('el estado transferido viaja en el HTML aunque venga por un proxy', async ({ request }) => {
    const html = await (await request.get('/', { headers: CABECERAS_DE_CLOUD_RUN })).text();

    expect(html).toContain('ng-state');
  });

  /**
   * Cada cabecera por separado, y no solo el juego completo.
   *
   * <p>Con las dos juntas, olvidar una en `trustProxyHeaders` se nota; pero si un dia
   * se declara una de mas y otra de menos, el juego completo seguiria fallando sin
   * decir cual es. Esta dice cual.
   */
  for (const [cabecera, valor] of Object.entries(CABECERAS_DE_CLOUD_RUN)) {
    test(`la pagina llega renderizada con ${cabecera}`, async ({ request }) => {
      const html = await (await request.get('/', { headers: { [cabecera]: valor } })).text();

      expect(html, cabecera).not.toContain('<sendik-root></sendik-root>');
    });
  }

  // Sin Vary, una cache intermedia le daria a un visitante la pagina
  // renderizada para otro, en otro idioma y con otro tema.
  test('declara Vary para que ninguna cache mezcle idiomas', async ({ request }) => {
    const response = await request.get('/');
    const vary = response.headers()['vary'] ?? '';

    expect(vary).toContain('Cookie');
    expect(vary).toContain('Accept-Language');
  });
});
