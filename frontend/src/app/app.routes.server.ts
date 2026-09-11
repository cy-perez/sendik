import { RenderMode, type ServerRoute } from '@angular/ssr';

import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from './core/routes/content-routes';

/**
 * Todo se renderiza en cada peticion, no se prerenderiza: el idioma, el tema y
 * la configuracion salen de las cabeceras y las cookies de quien pide la
 * pagina, y una version generada al construir seria la misma para todo el mundo.
 *
 * La ruta comodin devuelve 404 de verdad. Un "no existe" servido con 200 es un
 * soft 404: el buscador lo indexa como pagina buena y termina ofreciendo
 * direcciones rotas.
 *
 * <p><strong>Toda ruta que exista tiene que estar en esta lista.</strong> La que
 * falte cae en el comodin y se sirve con estado 404 aunque la pagina se pinte
 * entera: el visitante no nota nada y el buscador la descarta. Es lo que le
 * pasaba a /ingresar.
 */
export const serverRoutes: ServerRoute[] = [
  { path: '', renderMode: RenderMode.Server },
  { path: 'registro', renderMode: RenderMode.Server },
  { path: 'ingresar', renderMode: RenderMode.Server },
  { path: 'verificar-correo', renderMode: RenderMode.Server },
  { path: 'mi-cuenta', renderMode: RenderMode.Server },
  // HU-002. Faltaba, y el sintoma es el que este archivo describe arriba: la pagina
  // se pintaba entera y se servia con 404. No se nota mirandola, solo midiendo el
  // estado, que es lo que hace ahora `rutas.spec.ts`.
  { path: 'verificacion-de-vendedor', renderMode: RenderMode.Server },
  { path: 'recuperar-contrasena', renderMode: RenderMode.Server },
  { path: 'restablecer-contrasena', renderMode: RenderMode.Server },
  { path: 'confirmar-correo-nuevo', renderMode: RenderMode.Server },
  // Las cuatro informativas de HU-005. Son las que mas dependen de esto: existen
  // para que alguien que duda las encuentre y las lea, asi que servirlas con 404
  // las dejaria fuera del buscador aunque se pintaran enteras.
  //
  // Derivadas de la constante y no escritas otra vez: el modulo de core existe
  // precisamente para que la direccion se declare en un solo sitio, y una lista
  // repetida a mano es la forma exacta en que /ingresar se quedo fuera.
  ...PAGINAS_DE_CONTENIDO.map((pagina): ServerRoute => ({
    path: RUTAS_CONTENIDO[pagina].slice(1),
    renderMode: RenderMode.Server,
  })),
  // Los legales importan aqui mas que ninguna: son las que una autoridad revisa
  // y las que un buscador tiene que poder indexar.
  { path: 'terminos', renderMode: RenderMode.Server },
  { path: 'tratamiento-de-datos', renderMode: RenderMode.Server },
  { path: 'politica-de-cookies', renderMode: RenderMode.Server },
  // HU-006, la bandeja del moderador. Se renderizan en servidor como todo lo demas, y
  // no porque su contenido deba salir de ahi: `exigirRol` DENIEGA en el servidor, asi
  // que lo que se sirve es la pagina de "no existe" y el titulo de la bandeja no viaja
  // en el HTML de nadie (criterio 2). Al hidratar, el guard vuelve a correr en el
  // navegador y quien tenga el rol entra.
  //
  // Se probo `RenderMode.Client`, que seria lo natural para una pantalla interna, y no
  // sirve: APP_CONFIG llega por el estado transferido del renderizado en servidor, y sin
  // el la aplicacion no arranca. ADR-0021.
  { path: 'moderacion/verificaciones', renderMode: RenderMode.Server },
  { path: 'moderacion/verificaciones/:id', renderMode: RenderMode.Server },
  // HU-007, las tres del vendedor. **Faltaban desde que se escribieron**: las paginas se
  // pintaban enteras y se servian con 404, que es exactamente el sintoma que este archivo
  // describe arriba y que no se nota mirando, solo midiendo el estado. Lo delato
  // `rutas.spec.ts` al agregar las de HU-008.
  { path: 'publicar', renderMode: RenderMode.Server },
  { path: 'publicar/:id', renderMode: RenderMode.Server },
  { path: 'mis-publicaciones', renderMode: RenderMode.Server },
  // HU-011, la lista propia de favoritos. En servidor como todo lo demas: alli no hay
  // cookie de nadie, asi que lo que sale es el esqueleto y la lista llega al hidratar,
  // que es lo correcto para algo privado. No se usa RenderMode.Client aunque seria lo
  // natural, por lo mismo que anota `role.guard.ts`: APP_CONFIG llega por el estado
  // transferido y una ruta que no se renderiza en servidor arranca sin configuracion.
  { path: 'mis-favoritos', renderMode: RenderMode.Server },
  // HU-015, el carrito. Mismo razonamiento: es dato de una persona -o de un navegador- asi
  // que el HTML servido no lo lleva dentro; sale el esqueleto y el carrito llega al hidratar.
  // Sin esta linea la pagina se pinta entera y se sirve con 404, que es lo que `rutas.spec.ts`
  // ya cazo en /mis-favoritos, en /ingresar y en las tres rutas de HU-007.
  { path: 'carrito', renderMode: RenderMode.Server },
  // HU-003, el asistente de captura. Cuelga del formulario y se renderiza en servidor por
  // lo mismo que el: APP_CONFIG llega por el estado transferido y sin el no arranca.
  { path: 'publicar/:id/capturar', renderMode: RenderMode.Server },
  // HU-008, la bandeja de moderacion de publicaciones. Mismo razonamiento que la de
  // HU-006: se renderizan en servidor porque APP_CONFIG llega por el estado transferido,
  // y `exigirRol` deniega alli, asi que lo que se sirve a quien no modera es la pagina de
  // "no existe" (criterio 2).
  { path: 'moderacion/publicaciones', renderMode: RenderMode.Server },
  { path: 'moderacion/publicaciones/:id', renderMode: RenderMode.Server },
  // HU-009, el catalogo publico. **Es donde este archivo importa mas que en ninguna otra
  // ruta.** Las demas se sirven a quien ya decidio entrar; estas tres existen para que un
  // buscador las indexe y traiga compradores, asi que servirlas con 404 -aunque se pinten
  // enteras- las dejaria fuera del unico canal que las justifica.
  { path: 'catalogo', renderMode: RenderMode.Server },
  { path: 'catalogo/:familia', renderMode: RenderMode.Server },
  { path: 'catalogo/:familia/:categoria', renderMode: RenderMode.Server },
  // La ficha es la pagina que un buscador tiene que poder leer entera: el titulo, la
  // descripcion y la foto salen del producto y se resuelven aqui, en el servidor.
  { path: 'producto/:id', renderMode: RenderMode.Server },
  { path: 'vendedor/:id', renderMode: RenderMode.Server },
  {
    path: '**',
    renderMode: RenderMode.Server,
    status: 404,
  },
];
