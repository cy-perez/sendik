import {
  type ApplicationConfig,
  DOCUMENT,
  inject,
  mergeApplicationConfig,
  provideEnvironmentInitializer,
  REQUEST,
} from '@angular/core';
import { provideServerRendering, withRoutes } from '@angular/ssr';
import { appConfig } from './app.config';
import { serverRoutes } from './app.routes.server';
import { provideAppConfig } from './core/config/app-config.providers';
import { readAppConfigForBootstrap } from './core/config/read-app-config';
import { ACTIVE_LOCALE, provideI18n } from './core/i18n/i18n.providers';
import { resolveLocale } from './core/i18n/locale';
import { BundledTranslationLoader } from './core/i18n/bundled-translation-loader';
import { resolveTheme, themeAttribute } from './core/theme/theme';

/**
 * Se lee una sola vez, al cargar el modulo. La validacion estricta vive en
 * src/server.ts, que es el arranque real del servidor: aqui no puede lanzar
 * porque este mismo modulo se ejecuta al construir, para extraer las rutas.
 */
const runtimeConfig = readAppConfigForBootstrap(process.env);

const serverConfig: ApplicationConfig = {
  providers: [
    provideServerRendering(withRoutes(serverRoutes)),
    provideAppConfig(runtimeConfig),
    provideI18n(BundledTranslationLoader),

    {
      provide: ACTIVE_LOCALE,
      useFactory: () => {
        const headers = inject(REQUEST)?.headers;
        return resolveLocale({
          cookieHeader: headers?.get('cookie'),
          acceptLanguage: headers?.get('accept-language'),
          availableLocales: runtimeConfig.availableLocales,
          defaultLocale: runtimeConfig.defaultLocale,
        });
      },
    },

    // El idioma y el tema se escriben en el elemento raiz antes de renderizar.
    // Por eso el HTML sale ya traducido y con el modo correcto: el navegador no
    // tiene nada que corregir al hidratar, y no hay parpadeo.
    //
    // Se usa setAttribute y no la propiedad lang: el DOM del servidor no la
    // refleja al atributo, y el HTML saldria con el idioma de index.html.
    provideEnvironmentInitializer(() => {
      const documentElement = inject(DOCUMENT).documentElement;
      const headers = inject(REQUEST)?.headers;

      documentElement.setAttribute('lang', inject(ACTIVE_LOCALE));
      documentElement.setAttribute(
        'data-tema',
        themeAttribute(
          resolveTheme({
            cookieHeader: headers?.get('cookie'),
            colorSchemeHint: headers?.get('sec-ch-prefers-color-scheme'),
          }),
        ),
      );
    }),
  ],
};

export const config = mergeApplicationConfig(appConfig, serverConfig);
