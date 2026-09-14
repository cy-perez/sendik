import { describe, expect, it } from 'vitest';

import {
  assertRenderingEnvironment,
  avisosDeConfiguracion,
  readAppConfig,
  readAppConfigForBootstrap,
} from './read-app-config';

const MINIMUM = { API_BASE_URL: 'https://api.sendik.co/api/v1' };

describe('readAppConfig', () => {
  it('aplica los valores por omision cuando solo esta lo obligatorio', () => {
    const config = readAppConfig(MINIMUM);

    expect(config.apiBaseUrl).toBe('https://api.sendik.co/api/v1');
    expect(config.defaultLocale).toBe('es');
    expect(config.availableLocales).toEqual(['es', 'en']);
    expect(config.enableDevtools).toBe(false);
    expect(config.sentryDsn).toBeNull();
  });

  it('no arranca si falta la direccion de la API', () => {
    expect(() => readAppConfig({})).toThrowError(/API_BASE_URL/);
  });

  it('trata una variable en blanco como ausente', () => {
    expect(() => readAppConfig({ API_BASE_URL: '   ' })).toThrowError(/API_BASE_URL/);
  });

  // Una barra de mas produce //api/v1, que en muchos servidores es un 404 y
  // cuesta una tarde encontrar.
  it('quita la barra final de la direccion de la API', () => {
    const config = readAppConfig({ API_BASE_URL: 'https://api.sendik.co/api/v1/' });

    expect(config.apiBaseUrl).toBe('https://api.sendik.co/api/v1');
  });

  it('recorta los espacios de la lista de idiomas', () => {
    const config = readAppConfig({ ...MINIMUM, AVAILABLE_LOCALES: ' es , en ' });

    expect(config.availableLocales).toEqual(['es', 'en']);
  });

  it('rechaza un idioma por omision que no este entre los disponibles', () => {
    expect(() =>
      readAppConfig({ ...MINIMUM, DEFAULT_LOCALE: 'pt', AVAILABLE_LOCALES: 'es,en' }),
    ).toThrowError(/DEFAULT_LOCALE/);
  });

  it('rechaza una lista de idiomas vacia', () => {
    expect(() => readAppConfig({ ...MINIMUM, AVAILABLE_LOCALES: ' , ' })).toThrowError(
      /AVAILABLE_LOCALES/,
    );
  });

  it('solo activa las herramientas de desarrollo con el valor true', () => {
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: 'TRUE' }).enableDevtools).toBe(true);
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: '1' }).enableDevtools).toBe(false);
    expect(readAppConfig({ ...MINIMUM, ENABLE_DEVTOOLS: 'false' }).enableDevtools).toBe(false);
  });

  /**
   * Sin las variables se cae al borrador, que es la misma version que usa el
   * perfil local del backend. No se exigen para no romper el arranque en la
   * maquina de quien programa: un despliegue que las olvide sirve el texto de
   * borrador, que dice en su primera linea que no tiene valor legal.
   */
  it('cae en la version de borrador si no se declaran las versiones legales', () => {
    const config = readAppConfig(MINIMUM);

    expect(config.legalVersions).toEqual({
      terms: 'borrador-local',
      privacy: 'borrador-local',
      cookies: 'borrador-local',
    });
  });

  /**
   * Las de terminos y tratamiento tienen que valer lo mismo que las del backend,
   * que es quien las guarda como evidencia: por eso salen de las mismas
   * variables de entorno (docs/operacion/datos-personales.md).
   */
  it('toma cada version legal de su variable', () => {
    const config = readAppConfig({
      ...MINIMUM,
      LEGAL_TERMS_VERSION: '2026-08-01',
      LEGAL_PRIVACY_VERSION: '2026-09-15',
      LEGAL_COOKIES_VERSION: '  2026-07-02  ',
    });

    expect(config.legalVersions.terms).toBe('2026-08-01');
    expect(config.legalVersions.privacy).toBe('2026-09-15');
    expect(config.legalVersions.cookies).toBe('2026-07-02');
  });

  it('deja el DSN de Sentry en nulo si viene vacio', () => {
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: '  ' }).sentryDsn).toBeNull();
    expect(readAppConfig({ ...MINIMUM, SENTRY_DSN: 'https://dsn' }).sentryDsn).toBe('https://dsn');
  });

  /**
   * HU-004, criterio 11: el pie los muestra tomados de aqui, nunca escritos en
   * la plantilla.
   */
  it('lee los datos de la empresa para el pie', () => {
    const config = readAppConfig({
      ...MINIMUM,
      COMPANY_NAME: 'Sendik S.A.S.',
      COMPANY_TAX_ID: '000000000-0',
      COMPANY_ADDRESS: '  Medellin, Colombia  ',
      SUPPORT_EMAIL: 'soporte@example.test',
    });

    expect(config.company).toEqual({
      name: 'Sendik S.A.S.',
      taxId: '000000000-0',
      address: 'Medellin, Colombia',
      supportEmail: 'soporte@example.test',
    });
  });

  /**
   * Caso borde de HU-004: el frontend solo los pinta. Quien los exige es el
   * backend, que los necesita para los correos. Tumbar el renderizado por una
   * direccion que falta cambiaria un pie incompleto por un sitio caido.
   */
  it('no exige ningun dato de la empresa y trata el blanco como ausente', () => {
    expect(readAppConfig(MINIMUM).company).toEqual({
      name: null,
      taxId: null,
      address: null,
      supportEmail: null,
    });
    expect(readAppConfig({ ...MINIMUM, COMPANY_NAME: '   ' }).company.name).toBeNull();
  });
});

/**
 * Las cifras que el sitio informativo anuncia (HU-005). Se validan al leerlas y
 * no al pintarlas: una comision negativa o una ventana de cero dias no son un
 * problema de maquetacion, son una promesa incorrecta publicada, y en Colombia
 * lo que se anuncia es exigible.
 */
describe('readAppConfig, cifras de negocio', () => {
  it('cae en los valores de las reglas de negocio cuando no se declaran', () => {
    expect(readAppConfig(MINIMUM).business).toEqual({
      commissionRate: 0.05,
      claimWindowDays: 3,
      verificationReviewDays: 2,
      listingReviewDays: 2,
    });
  });

  it('trata una cifra en blanco como ausente', () => {
    expect(readAppConfig({ ...MINIMUM, COMMISSION_RATE: '   ' }).business.commissionRate).toBe(
      0.05,
    );
  });

  it('toma cada cifra de su variable', () => {
    const config = readAppConfig({
      ...MINIMUM,
      COMMISSION_RATE: '0.08',
      CLAIM_WINDOW_DAYS: '5',
    });

    expect(config.business).toEqual({
      commissionRate: 0.08,
      claimWindowDays: 5,
      verificationReviewDays: 2,
      listingReviewDays: 2,
    });
  });

  // El error que este proyecto tiene delante: la comision se escribe 0.05 en la
  // configuracion y 5% en pantalla. Quien declare 5 esta anunciando un 500%.
  it('rechaza una comision que no sea una fraccion', () => {
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: '5' })).toThrowError(/fraccion/);
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: '-0.1' })).toThrowError(/fraccion/);
  });

  /**
   * Los bordes, que es donde estaba el hueco: el cero entraba y publicaba "no
   * se cobra comision" en las cuatro paginas, y eso en Colombia es exigible.
   * Se prueban los cuatro extremos, no solo el centro del rango.
   */
  it('rechaza una comision de cero y una por encima del techo de cordura', () => {
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: '0' })).toThrowError(/RN-026/);
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: '0.99' })).toThrowError(/RN-026/);
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: '1' })).toThrowError(/RN-026/);
    expect(readAppConfig({ ...MINIMUM, COMMISSION_RATE: '0.5' }).business.commissionRate).toBe(0.5);
  });

  it('rechaza una ventana de reclamo que no sea un entero de dias positivo', () => {
    expect(() => readAppConfig({ ...MINIMUM, CLAIM_WINDOW_DAYS: '0' })).toThrowError(
      /CLAIM_WINDOW_DAYS/,
    );
    expect(() => readAppConfig({ ...MINIMUM, CLAIM_WINDOW_DAYS: '2.5' })).toThrowError(
      /CLAIM_WINDOW_DAYS/,
    );
    expect(() => readAppConfig({ ...MINIMUM, CLAIM_WINDOW_DAYS: '-3' })).toThrowError(
      /CLAIM_WINDOW_DAYS/,
    );
  });

  it('rechaza una ventana de reclamo de meses', () => {
    expect(() => readAppConfig({ ...MINIMUM, CLAIM_WINDOW_DAYS: '31' })).toThrowError(/RN-051/);
    expect(readAppConfig({ ...MINIMUM, CLAIM_WINDOW_DAYS: '1' }).business.claimWindowDays).toBe(1);
  });

  it('rechaza una cifra que no sea un numero en vez de servir NaN', () => {
    expect(() => readAppConfig({ ...MINIMUM, COMMISSION_RATE: 'cinco' })).toThrowError(
      /no es un numero/,
    );
  });

  // La configuracion de relleno solo existe al construir, pero si llegara a
  // pintarse no puede anunciar una comision indefinida.
  it('la configuracion de relleno trae las cifras de las reglas', () => {
    expect(readAppConfigForBootstrap({}).business).toEqual({
      commissionRate: 0.05,
      claimWindowDays: 3,
      verificationReviewDays: 2,
      listingReviewDays: 2,
    });
  });
});

/**
 * Caso borde de HU-004: un despliegue con la configuracion de empresa a medias
 * arranca igual, pero lo dice. Descubrirlo mirando el pie en produccion es tarde.
 */
describe('avisosDeConfiguracion', () => {
  const conEmpresa = (variables: Record<string, string>) =>
    avisosDeConfiguracion(readAppConfig({ ...MINIMUM, ...variables }));

  const COMPLETA = {
    COMPANY_NAME: 'Sendik S.A.S.',
    COMPANY_TAX_ID: '000000000-0',
    COMPANY_ADDRESS: 'Medellin, Colombia',
    SUPPORT_EMAIL: 'soporte@example.test',
  };

  it('no avisa de nada cuando la configuracion esta completa', () => {
    expect(conEmpresa(COMPLETA)).toEqual([]);
  });

  it('nombra cada variable que falta', () => {
    const avisos = conEmpresa({ ...COMPLETA, COMPANY_TAX_ID: '', COMPANY_ADDRESS: '' }).join(' ');

    expect(avisos).toContain('COMPANY_TAX_ID');
    expect(avisos).toContain('COMPANY_ADDRESS');
    expect(avisos).not.toContain('COMPANY_NAME');
  });

  /**
   * Este no es un dato mas: sin el no hay canal visible para ejercer los
   * derechos del titular y se incumple la Ley 1581, asi que se avisa aparte y
   * diciendo por que.
   */
  it('avisa del correo de soporte por separado, citando la obligacion legal', () => {
    const avisos = conEmpresa({ ...COMPLETA, SUPPORT_EMAIL: '' });

    expect(avisos).toHaveLength(2);
    expect(avisos.join(' ')).toContain('Ley 1581');
  });

  it('avisa de las cuatro cuando no hay ninguna', () => {
    const avisos = conEmpresa({}).join(' ');

    for (const variable of Object.keys(COMPLETA)) {
      expect(avisos).toContain(variable);
    }
  });
});

describe('readAppConfig, banderas de funcionalidad', () => {
  it('las deja apagadas cuando no se declaran', () => {
    expect(readAppConfig(MINIMUM).features).toEqual({
      catalog: false,
      checkout: false,
      publishing: false,
      sellerVerification: false,
      search: false,
    });
  });

  it('enciende cada una con su variable y solo con el valor true', () => {
    const config = readAppConfig({
      ...MINIMUM,
      FEATURE_CATALOG: 'true',
      FEATURE_CHECKOUT: ' TRUE ',
      FEATURE_PUBLISHING: 'yes',
      FEATURE_SELLER_VERIFICATION: '',
      FEATURE_SEARCH: 'true',
    });

    expect(config.features).toEqual({
      catalog: true,
      checkout: true,
      publishing: false,
      sellerVerification: false,
      search: true,
    });
  });

  it('la configuracion de relleno las trae apagadas', () => {
    expect(readAppConfigForBootstrap({}).features).toEqual({
      catalog: false,
      checkout: false,
      publishing: false,
      sellerVerification: false,
      search: false,
    });
  });
});

describe('readAppConfigForBootstrap', () => {
  // Angular arranca la aplicacion al construir para extraer las rutas, y ahi no
  // hay entorno. Si esto lanzara, no se podria compilar en integracion continua.
  it('devuelve una configuracion de relleno en vez de lanzar', () => {
    const config = readAppConfigForBootstrap({});

    expect(config.apiBaseUrl).toBe('');
    expect(config.availableLocales).toEqual(['es', 'en']);
  });

  it('usa el entorno real cuando esta completo', () => {
    expect(readAppConfigForBootstrap(MINIMUM).apiBaseUrl).toBe('https://api.sendik.co/api/v1');
  });
});

describe('assertRenderingEnvironment', () => {
  // Sin esta variable Angular no lanza: sirve la pagina sin renderizar. El sitio
  // parece funcionar y el buscador recibe un documento vacio.
  it('exige la lista de dominios permitidos', () => {
    expect(() => assertRenderingEnvironment({})).toThrowError(/NG_ALLOWED_HOSTS/);
  });

  it('pasa cuando esta declarada', () => {
    expect(() => assertRenderingEnvironment({ NG_ALLOWED_HOSTS: 'sendik.co' })).not.toThrow();
  });
});
