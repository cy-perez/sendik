import type { Type } from '@angular/core';
import type { Route, Routes } from '@angular/router';

import {
  PAGINAS_DE_CONTENIDO,
  RUTAS_CONTENIDO,
  type ContentPageId,
} from './core/routes/content-routes';
import { DOCUMENTOS_LEGALES, RUTAS_LEGALES } from './core/routes/legal-routes';
import { exigirRol } from './core/session/role.guard';
import { legalContentResolver } from './features/legal/application/legal-content.resolver';

/**
 * Toda ruta se carga de forma diferida y declara su titulo y su descripcion
 * como claves de Transloco: TranslatedTitleStrategy las resuelve durante el
 * renderizado en servidor.
 *
 * Las direcciones van en espanol, una sola por ruta. Al cambiar a ingles cambia
 * el texto, no la direccion: el trafico de busqueda vendra de Colombia y el
 * ingles existe por accesibilidad, no por posicionamiento.
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'meta.home.title',
    data: { descriptionKey: 'meta.home.description' },
    loadComponent: () => import('./features/home/presentation/home-page').then((m) => m.HomePage),
  },
  // El catalogo publico. HU-009.
  //
  // Tres rutas y un solo componente: son el mismo listado con distinto filtro, y
  // separarlas duplicaria los tres estados de carga y la rejilla.
  //
  // Las tres existen aunque FEATURE_CATALOG este apagada. No se esconden: la API
  // responde 404 y la pantalla muestra su estado de error, que es lo mismo que hace
  // /publicar y lo que permite que rutas.spec.ts las recorra sin saber que bandera
  // esta encendida.
  {
    path: 'catalogo',
    title: 'meta.catalog.title',
    data: { descriptionKey: 'meta.catalog.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'catalogo/:familia',
    title: 'meta.catalog.title',
    data: { descriptionKey: 'meta.catalog.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/catalog-page').then((m) => m.CatalogPage),
  },
  {
    path: 'catalogo/:familia/:categoria',
    title: 'meta.catalog.title',
    data: { descriptionKey: 'meta.catalog.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/catalog-page').then((m) => m.CatalogPage),
  },
  // La ficha y el perfil. Sus titulos y descripciones son los genericos: los de verdad
  // salen del producto y del vendedor, que no se conocen hasta que llega la respuesta, y
  // los ponen las propias pantallas sobre estos.
  {
    path: 'producto/:id',
    title: 'meta.catalog.title',
    data: { descriptionKey: 'meta.catalog.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/product-page').then((m) => m.ProductPage),
  },
  {
    path: 'vendedor/:id',
    title: 'meta.catalog.title',
    data: { descriptionKey: 'meta.catalog.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/seller-page').then((m) => m.SellerPage),
  },
  {
    // HU-011. La lista propia de favoritos.
    //
    // Sin guard, y es deliberado: `exigirRol` sirve una pagina de «no existe» a quien no
    // pasa, y aqui no hay nada que esconder —que exista una lista de favoritos no es un
    // secreto—. Lo que hace falta es explicar y ofrecer entrar, que es el criterio 16, y
    // eso lo hace la pantalla.
    //
    // Existe aunque FEATURE_CATALOG este apagada, igual que las tres del catalogo: la API
    // responde 404 y la pantalla muestra su estado de error.
    path: 'mis-favoritos',
    title: 'meta.favorites.title',
    data: { descriptionKey: 'meta.favorites.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/favorites-page').then((m) => m.FavoritesPage),
  },
  {
    // El carrito. HU-015.
    //
    // **Sin guard, y a diferencia de todo lo demas que es de una persona.** Un carrito no es
    // de una cuenta hasta que alguien entra: quien no ha entrado tiene el suyo en el
    // navegador y esta pantalla se lo pinta igual (criterio 13). Pedir sesion aqui haria
    // imposible el criterio 8.
    //
    // Existe aunque FEATURE_CHECKOUT este apagada, igual que las del catalogo: la API
    // responde 404 y la pantalla muestra su estado de error.
    path: 'carrito',
    title: 'meta.cart.title',
    data: { descriptionKey: 'meta.cart.description' },
    loadComponent: () =>
      import('./features/catalog/presentation/cart-page').then((m) => m.CartPage),
  },
  {
    path: 'registro',
    title: 'meta.register.title',
    data: { descriptionKey: 'meta.register.description' },
    loadComponent: () =>
      import('./features/auth/presentation/register-page').then((m) => m.RegisterPage),
  },
  {
    path: 'ingresar',
    title: 'meta.login.title',
    data: { descriptionKey: 'meta.login.description' },
    loadComponent: () => import('./features/auth/presentation/login-page').then((m) => m.LoginPage),
  },
  {
    path: 'recuperar-contrasena',
    title: 'meta.forgot.title',
    data: { descriptionKey: 'meta.forgot.description' },
    loadComponent: () =>
      import('./features/auth/presentation/forgot-password-page').then((m) => m.ForgotPasswordPage),
  },
  {
    // La ruta la conoce tambien el backend, que la monta en el enlace del correo:
    // MAIL_PASSWORD_RESET_PATH. Si cambia aqui, cambia alli.
    path: 'restablecer-contrasena',
    title: 'meta.reset.title',
    data: { descriptionKey: 'meta.reset.description' },
    loadComponent: () =>
      import('./features/auth/presentation/reset-password-page').then((m) => m.ResetPasswordPage),
  },
  {
    // Tambien la conoce el backend, que la monta en el enlace del correo:
    // MAIL_EMAIL_CHANGE_PATH. Si cambia aqui, cambia alli.
    path: 'confirmar-correo-nuevo',
    title: 'meta.confirmEmail.title',
    data: { descriptionKey: 'meta.confirmEmail.description' },
    loadComponent: () =>
      import('./features/auth/presentation/confirm-email-change-page').then(
        (m) => m.ConfirmEmailChangePage,
      ),
  },
  {
    path: 'mi-cuenta',
    title: 'meta.account.title',
    data: { descriptionKey: 'meta.account.description' },
    loadComponent: () =>
      import('./features/auth/presentation/account-page').then((m) => m.AccountPage),
  },
  {
    // HU-002. Direccion en espanol, como el resto del sitio.
    //
    // **Todavia no hay enlace a esta pantalla desde ninguna parte**, y es deliberado: el
    // backend responde 404 en estas rutas mientras FEATURE_SELLER_VERIFICATION este
    // apagada, y HU-004 y HU-005 prohiben dejar enlaces a algo que no funciona. El punto
    // de entrada entra cuando la bandera se encienda.
    path: 'verificacion-de-vendedor',
    title: 'meta.sellerVerification.title',
    data: { descriptionKey: 'meta.sellerVerification.description' },
    loadComponent: () =>
      import('./features/seller-verification/presentation/verification-page').then(
        (m) => m.VerificationPage,
      ),
  },
  {
    // HU-006. La bandeja del moderador y el detalle de una solicitud.
    //
    // Va detras de `exigirRol`, el primer guard del proyecto (ADR-0021). Ese guard es
    // tambien lo que impide que el titulo de la bandeja viaje en el HTML de cualquiera
    // que pida la direccion: **deniega en el servidor**, asi que lo que se sirve es la
    // pagina de "no existe". No lo hace el modo de renderizado, que es Server como el de
    // todo el sitio.
    //
    // Sin enlace desde ninguna parte, como la de HU-002: quien modera conoce la
    // direccion, y ponerla en la cabecera se la ensenaria a todo el mundo.
    path: 'moderacion/verificaciones',
    title: 'meta.moderationInbox.title',
    data: { descriptionKey: 'meta.moderationInbox.description' },
    canActivate: [exigirRol('MODERATOR')],
    loadComponent: () =>
      import('./features/verification-review/presentation/inbox-page').then((m) => m.InboxPage),
  },
  {
    path: 'moderacion/verificaciones/:id',
    title: 'meta.moderationDetail.title',
    data: { descriptionKey: 'meta.moderationDetail.description' },
    canActivate: [exigirRol('MODERATOR')],
    loadComponent: () =>
      import('./features/verification-review/presentation/review-detail-page').then(
        (m) => m.ReviewDetailPage,
      ),
  },
  {
    // HU-008. La bandeja de moderacion de publicaciones y su detalle.
    //
    // Sin enlace desde ninguna parte, como las de HU-002 y HU-006: quien modera conoce
    // la direccion. Y ademas el backend responde 404 en la cola mientras
    // FEATURE_PUBLISHING este apagada, asi que enlazarla llevaria a una pantalla vacia.
    path: 'moderacion/publicaciones',
    title: 'meta.listingReviewQueue.title',
    data: { descriptionKey: 'meta.listingReviewQueue.description' },
    canActivate: [exigirRol('MODERATOR')],
    loadComponent: () =>
      import('./features/listing-review/presentation/queue-page').then((m) => m.QueuePage),
  },
  {
    path: 'moderacion/publicaciones/:id',
    title: 'meta.listingReviewDetail.title',
    data: { descriptionKey: 'meta.listingReviewDetail.description' },
    canActivate: [exigirRol('MODERATOR')],
    loadComponent: () =>
      import('./features/listing-review/presentation/review-listing-page').then(
        (m) => m.ReviewListingPage,
      ),
  },
  {
    // HU-007. El formulario de publicar y el listado propio.
    //
    // **Sin enlace desde ninguna parte**, igual que las de HU-002 y HU-006: el backend
    // responde 404 en estas rutas mientras FEATURE_PUBLISHING este apagada, y HU-004 y
    // HU-005 prohiben dejar enlaces a algo que no funciona. La entrada en el menu se pone
    // cuando la bandera se encienda.
    //
    // `/publicar` sin identificador es el paso previo: pide la categoria y crea el
    // borrador. No es un capricho de navegacion —una toma se sube contra una publicacion
    // que ya existe— y ademas de la categoria dependen las condiciones admisibles, los
    // sistemas de talla y que medidas se piden.
    path: 'publicar',
    title: 'meta.publish.title',
    data: { descriptionKey: 'meta.publish.description' },
    loadComponent: () =>
      import('./features/listing/presentation/publish-page').then((m) => m.PublishPage),
  },
  {
    path: 'publicar/:id',
    title: 'meta.publish.title',
    data: { descriptionKey: 'meta.publish.description' },
    loadComponent: () =>
      import('./features/listing/presentation/publish-page').then((m) => m.PublishPage),
  },
  {
    // HU-003. El asistente de las ocho tomas, colgado del formulario que ya existe.
    //
    // Ruta propia y no un dialogo sobre `/publicar/:id`: la camara ocupa el alto entero en
    // un telefono, el boton de atras tiene que cerrarla, y el paso en curso sobrevive a que
    // la pantalla rote, que es uno de los casos borde de la historia.
    //
    // Comparte los metadatos de `/publicar` a proposito: es la misma tarea partida en dos
    // pantallas, y no hay nada que un buscador deba encontrar aqui.
    path: 'publicar/:id/capturar',
    title: 'meta.publish.title',
    data: { descriptionKey: 'meta.publish.description' },
    loadComponent: () =>
      import('./features/listing/presentation/capture-wizard').then((m) => m.CaptureWizard),
  },
  {
    path: 'mis-publicaciones',
    title: 'meta.myListings.title',
    data: { descriptionKey: 'meta.myListings.description' },
    loadComponent: () =>
      import('./features/listing/presentation/my-listings-page').then((m) => m.MyListingsPage),
  },
  {
    path: 'verificar-correo',
    title: 'meta.verify.title',
    data: { descriptionKey: 'meta.verify.description' },
    loadComponent: () =>
      import('./features/auth/presentation/verify-email-page').then((m) => m.VerifyEmailPage),
  },
  // Las cuatro paginas informativas de HU-005. Al contrario que las legales, no
  // comparten componente: cada una dice algo distinto y tiene su propia forma.
  // Lo que si comparten es de donde sale su direccion.
  ...paginasDeContenido(),
  // Los tres documentos legales comparten componente y resolutor: lo unico que
  // cambia es cual es y como se llama. El resolutor trae el texto antes de
  // renderizar, asi que viaja dentro del HTML que sirve el servidor.
  ...documentosLegales(),
  {
    path: '**',
    title: 'meta.notFound.title',
    data: { descriptionKey: 'meta.notFound.description' },
    loadComponent: () =>
      import('./features/not-found/presentation/not-found-page').then((m) => m.NotFoundPage),
  },
];

/**
 * Una ruta por pagina informativa.
 *
 * <p>Se generan desde {@link RUTAS_CONTENIDO} por el mismo motivo que las
 * legales: las direcciones las comparten la tabla de rutas, la navegacion de la
 * cabecera y el pie, y saliendo todas de un sitio no puede existir un enlace que
 * apunte a una ruta que no esta (criterio 25).
 *
 * <p>El componente si es propio de cada una, asi que el `loadComponent` se
 * resuelve con un `switch` y no con una plantilla de ruta: son cuatro paginas
 * distintas, no cuatro instancias de la misma.
 */
function paginasDeContenido(): Routes {
  return PAGINAS_DE_CONTENIDO.map((id): Route => ({
    // Sin la barra inicial: RUTAS_CONTENIDO la lleva porque sirven para routerLink.
    path: RUTAS_CONTENIDO[id].slice(1),
    title: `meta.${id}.title`,
    data: { descriptionKey: `meta.${id}.description` },
    loadComponent: () => cargarPaginaDeContenido(id),
  }));
}

/**
 * El `switch` es a proposito: un `import()` con una ruta calculada no lo puede
 * analizar el empaquetador, y las cuatro paginas acabarian en el paquete inicial
 * en vez de en su propio fragmento diferido.
 */
function cargarPaginaDeContenido(id: ContentPageId): Promise<Type<unknown>> {
  switch (id) {
    case 'howItWorks':
      return import('./features/content/presentation/how-it-works-page').then(
        (m) => m.HowItWorksPage,
      );
    case 'about':
      return import('./features/content/presentation/about-page').then((m) => m.AboutPage);
    case 'faq':
      return import('./features/content/presentation/faq-page').then((m) => m.FaqPage);
    case 'contact':
      return import('./features/content/presentation/contact-page').then((m) => m.ContactPage);
  }
}

/**
 * Una ruta por documento legal, todas iguales salvo el nombre.
 *
 * <p>Se generan en vez de escribirse tres veces para que agregar un documento sea
 * agregarlo al tipo y a las traducciones, y no copiar un bloque que se acabaria
 * separando del resto. Las direcciones salen de RUTAS, que es tambien de donde
 * las toman los enlaces del registro y del pie: asi no puede haber una ruta que
 * exista y un enlace que apunte a otra parte.
 */
function documentosLegales(): Routes {
  return DOCUMENTOS_LEGALES.map((id): Route => ({
    // Sin la barra inicial: RUTAS_LEGALES la lleva porque sirven para routerLink.
    path: RUTAS_LEGALES[id].slice(1),
    title: `meta.legal.${id}.title`,
    data: { descriptionKey: `meta.legal.${id}.description`, documento: id },
    resolve: { contenido: legalContentResolver },
    loadComponent: () =>
      import('./features/legal/presentation/legal-page').then((m) => m.LegalPage),
  }));
}
