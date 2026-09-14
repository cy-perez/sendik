import type { EnvironmentProviders, Provider } from '@angular/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

import { APP_CONFIG, type AppConfig } from './app/core/config/app-config';
import { provideActiveLocaleFromDocument, provideI18n } from './app/core/i18n/i18n.providers';
import { provideQuery } from './app/core/query/query.providers';
import { BundledTranslationLoader } from './app/core/i18n/bundled-translation-loader';

/**
 * Entorno comun de las pruebas de componente. Se usan las traducciones reales,
 * no unas de mentira: una prueba que pasa con "clave.de.texto" en pantalla no
 * demuestra que la clave exista.
 *
 * Ninguna prueba sale a la red: el cliente HTTP es siempre el de pruebas.
 */
const testConfig: AppConfig = {
  apiBaseUrl: 'https://api.pruebas.sendik.co/api/v1',
  defaultLocale: 'es',
  availableLocales: ['es', 'en'],
  enableDevtools: false,
  sentryDsn: null,
  // La misma version de borrador que trae el perfil local, para que las pruebas
  // pidan los archivos que de verdad estan en public/legal.
  legalVersions: { terms: 'borrador-local', privacy: 'borrador-local', cookies: 'borrador-local' },
  // Datos de empresa completos por omision, que es el caso normal. La prueba que
  // comprueba el pie incompleto sobrescribe APP_CONFIG en su propio TestBed.
  company: {
    name: 'Sendik S.A.S.',
    taxId: '000000000-0',
    address: 'Medellin, Colombia',
    supportEmail: 'soporte@example.test',
  },
  // Las mismas cifras que las reglas de negocio (RN-026, RN-051) y no unas
  // inventadas: una prueba que pase con el 8% no demuestra que la pagina diga lo
  // que el negocio cobra. La que compruebe que la cifra sale de configuracion y
  // no de la plantilla sobrescribe APP_CONFIG en su propio TestBed.
  business: {
    commissionRate: 0.05,
    claimWindowDays: 3,
    verificationReviewDays: 2,
    listingReviewDays: 2,
  },
  // Todas encendidas, que es el estado de `dev` y el que deja ver la navegacion
  // completa. La prueba que compruebe que un enlace desaparece con la bandera
  // apagada sobrescribe APP_CONFIG en su propio TestBed.
  features: {
    catalog: true,
    checkout: true,
    publishing: true,
    sellerVerification: true,
    search: true,
  },
};

const providers: (Provider | EnvironmentProviders)[] = [
  { provide: APP_CONFIG, useValue: testConfig },
  // Sin interceptores: cada prueba que los necesite los declara en su propio
  // TestBed. Importarlos desde este archivo arrastra @angular/common al paquete
  // de arranque de las pruebas y rompe la compilacion de toda la suite.
  provideHttpClient(),
  provideHttpClientTesting(),
  provideActiveLocaleFromDocument(),
  provideI18n(BundledTranslationLoader),
  // Las pantallas que llaman a la API usan mutaciones de TanStack Query, que
  // necesitan su QueryClient en el inyector.
  provideQuery(),
];

export default providers;
