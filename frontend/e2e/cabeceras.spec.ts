import { expect, test } from '@playwright/test';

/**
 * Las cabeceras del sitio, comprobadas sobre la respuesta real del servidor.
 *
 * <p>Existen porque estas decisiones vivian en `vercel.json` y ahi no las
 * comprobaba nada: eran configuracion de un proveedor, y la unica forma de saber
 * si seguian puestas era mirar el archivo. Al pasar al servidor de SSR (ADR-0019)
 * se pueden probar, y esta es la prueba: si alguien quita una cabecera, esto se
 * pone rojo en lugar de descubrirse en una auditoria.
 *
 * <p>Se piden por `request` y no por `page` a proposito: lo que importa es lo que
 * manda el servidor, no lo que el navegador acabe haciendo con ello.
 */
test.describe('cabeceras de seguridad', () => {
  test('la pagina las lleva todas', async ({ request }) => {
    const cabeceras = (await request.get('/')).headers();

    expect(cabeceras['x-content-type-options']).toBe('nosniff');
    // `strict-origin` y no `strict-origin-when-cross-origin`: el segundo manda la
    // direccion entera en las peticiones del mismo origen, y con HU-014 eso es el texto
    // que alguien busco viajando en cada recurso de la pagina.
    expect(cabeceras['referrer-policy']).toBe('strict-origin');
    expect(cabeceras['x-frame-options']).toBe('DENY');
    expect(cabeceras['strict-transport-security']).toBe('max-age=31536000; includeSubDomains');
  });

  /**
   * El archivo estatico no lo renderiza Angular: lo sirve `express.static`, que es
   * otro camino dentro del servidor. Es el caso que se olvida cuando las cabeceras
   * se ponen al lado del renderizado, y por eso se comprueba aparte.
   */
  test('el archivo estatico tambien las lleva', async ({ request }) => {
    const cabeceras = (await request.get('/fuentes/inter-latin.woff2')).headers();

    expect(cabeceras['x-content-type-options']).toBe('nosniff');
    expect(cabeceras['x-frame-options']).toBe('DENY');
  });

  /**
   * La respuesta de error tampoco se salta las cabeceras. Un 404 sirve HTML, y un
   * HTML sin `X-Frame-Options` se puede enmarcar igual que cualquier otro.
   */
  test('una direccion inexistente tambien las lleva', async ({ request }) => {
    const response = await request.get('/esta-ruta-no-existe');

    expect(response.status()).toBe(404);
    expect(response.headers()['x-frame-options']).toBe('DENY');
  });
});

test.describe('politicas de cache', () => {
  /**
   * Un anio e `immutable`: el nombre del archivo lleva la familia, asi que cambiar
   * de tipografia produce otro archivo y este no cambia nunca.
   *
   * <p>Se pide el archivo real y no uno cualquiera de la carpeta: si el nombre
   * deja de existir —como paso al cambiar a las variables de Sendik— la cabecera
   * llega indefinida y el caso tiene que verlo.
   */
  test('la fuente se cachea un anio y no se revalida', async ({ request }) => {
    const respuesta = await request.get('/fuentes/inter-latin.woff2');
    const cache = respuesta.headers()['cache-control'];

    expect(respuesta.status()).toBe(200);

    expect(cache).toContain('max-age=31536000');
    expect(cache).toContain('immutable');
  });

  /**
   * Cinco minutos, y esto es lo que la prueba protege de verdad: el documento legal
   * esta en la misma carpeta publica que las fuentes, asi que la regla general le
   * daria un anio. Un texto legal con un anio de cache es servir una version que ya
   * no rige despues de cambiarla.
   */
  test('el documento legal se cachea cinco minutos, no un anio', async ({ request }) => {
    const response = await request.get('/legal/privacy.borrador-local.es.html');
    const cache = response.headers()['cache-control'] ?? '';

    expect(response.status()).toBe(200);
    expect(cache).toContain('max-age=300');
    expect(cache).not.toContain('immutable');
  });
});
