import type {
  AppConfig,
  BusinessFigures,
  CompanyInfo,
  FeatureFlags,
  LegalVersions,
} from './app-config';

/** Solo la parte del entorno que nos interesa: asi la funcion es pura y se prueba sin Node. */
export type EnvironmentVariables = Readonly<Record<string, string | undefined>>;

const FALLBACK_DEFAULT_LOCALE = 'es';
const FALLBACK_AVAILABLE_LOCALES = 'es,en';

/**
 * El mismo valor por omision que usa el perfil local del backend.
 *
 * No se exige la variable, como si se exige API_BASE_URL, para no romper el
 * arranque en la maquina de quien programa. El riesgo de olvidarla en un
 * despliegue esta cubierto por otro lado: el texto que se sirve con esta version
 * es el borrador, y el borrador dice en su primera linea que no tiene valor
 * legal. Un despliegue que la olvide no falla en silencio, grita.
 */
const VERSION_DE_BORRADOR = 'borrador-local';

/**
 * Los mismos valores por omision que usa el perfil local del backend
 * (`application-local.yaml`). No se exigen por la misma razon que la version de
 * los legales: que a nadie se le caiga el arranque en su maquina por una cifra
 * que ya esta decidida en las reglas de negocio (RN-026, RN-051).
 *
 * <p>Un despliegue que las olvide no falla en silencio: se sirve el valor de las
 * reglas, que es el correcto, y lo unico que se pierde es poder cambiarlo sin
 * desplegar.
 */
const COMISION_POR_OMISION = 0.05;
const VENTANA_DE_RECLAMO_POR_OMISION = 3;

/**
 * Techos de cordura, no reglas de negocio. Existen para que un valor
 * disparatado no llegue a pantalla: la comision de la plataforma es del 5%
 * (RN-026) y la ventana, de 3 dias habiles (RN-051). Cualquier cosa por encima
 * de estos dos numeros es un error de configuracion, no una decision.
 */
const COMISION_MAXIMA = 0.5;
const VENTANA_DE_RECLAMO_MAXIMA = 30;

/**
 * Lee la configuracion del entorno y falla al arrancar si algo obligatorio no
 * esta. Es preferible no levantar el servidor a descubrirlo con un visitante
 * dentro, que es la misma regla que sigue el backend.
 */
export function readAppConfig(env: EnvironmentVariables): AppConfig {
  const apiBaseUrl = trimTrailingSlash(requireValue(env, 'API_BASE_URL'));

  const defaultLocale = (env['DEFAULT_LOCALE'] ?? FALLBACK_DEFAULT_LOCALE).trim();
  const availableLocales = (env['AVAILABLE_LOCALES'] ?? FALLBACK_AVAILABLE_LOCALES)
    .split(',')
    .map((locale) => locale.trim())
    .filter((locale) => locale.length > 0);

  if (availableLocales.length === 0) {
    throw new Error('AVAILABLE_LOCALES no puede quedar vacia.');
  }
  if (!availableLocales.includes(defaultLocale)) {
    throw new Error(
      `DEFAULT_LOCALE es "${defaultLocale}" pero no aparece en AVAILABLE_LOCALES ` +
        `(${availableLocales.join(', ')}). Un idioma por omision que no existe deja el sitio sin texto.`,
    );
  }

  const sentryDsn = env['SENTRY_DSN']?.trim();

  return {
    apiBaseUrl,
    defaultLocale,
    availableLocales,
    enableDevtools: env['ENABLE_DEVTOOLS']?.trim().toLowerCase() === 'true',
    sentryDsn: sentryDsn && sentryDsn.length > 0 ? sentryDsn : null,
    legalVersions: leerVersionesLegales(env),
    company: leerEmpresa(env),
    business: leerCifrasDeNegocio(env),
    features: leerBanderas(env),
  };
}

/**
 * Las banderas de funcionalidad. Los mismos nombres que en el backend, y el mismo
 * valor por omision: apagadas.
 *
 * <p>Solo `true` enciende, como `ENABLE_DEVTOOLS`: una variable ausente, en blanco o
 * con cualquier otra cosa deja la bandera apagada. Aqui una bandera mal escrita no
 * tumba el arranque, a diferencia del backend, porque lo unico que decide es si se
 * pinta un enlace; el 404 lo sigue dando la API.
 */
function leerBanderas(env: EnvironmentVariables): FeatureFlags {
  return {
    catalog: encendida(env, 'FEATURE_CATALOG'),
    checkout: encendida(env, 'FEATURE_CHECKOUT'),
    publishing: encendida(env, 'FEATURE_PUBLISHING'),
    sellerVerification: encendida(env, 'FEATURE_SELLER_VERIFICATION'),
    search: encendida(env, 'FEATURE_SEARCH'),
  };
}

function encendida(env: EnvironmentVariables, name: string): boolean {
  return env[name]?.trim().toLowerCase() === 'true';
}

/**
 * Las cifras que el sitio informativo anuncia.
 *
 * <p>Se validan aqui y no al pintarlas: una comision negativa o una ventana de
 * cero dias no son un problema de maquetacion, son una promesa incorrecta
 * publicada. Vale mas no levantar el servidor que anunciar que se cobra el -5%.
 */
/** Dos dias habiles, lo decidido para HU-002. */
const REVISION_POR_OMISION = 2;

/** Un mes laboral: mas alla de eso ya no es una promesa, es un aviso. */
const REVISION_MAXIMA = 20;

/** Decidido el 26 de agosto de 2026: dos dias habiles para revisar una publicacion. */
const REVISION_DE_PUBLICACION = 2;

function leerCifrasDeNegocio(env: EnvironmentVariables): BusinessFigures {
  const commissionRate = numeroOpcional(env, 'COMMISSION_RATE', COMISION_POR_OMISION);
  const claimWindowDays = numeroOpcional(env, 'CLAIM_WINDOW_DAYS', VENTANA_DE_RECLAMO_POR_OMISION);
  const verificationReviewDays = numeroOpcional(
    env,
    'VERIFICATION_REVIEW_DAYS',
    REVISION_POR_OMISION,
  );
  const listingReviewDays = numeroOpcional(env, 'LISTING_REVIEW_DAYS', REVISION_DE_PUBLICACION);

  /*
   * Los dos bordes estan cerrados a proposito. El cero entraba por abajo, y un
   * COMMISSION_RATE=0 —un dedo, o un despliegue a medias que deja la variable
   * vacia en cero— publica "no cobramos comision" en /como-funciona y en las
   * preguntas. En Colombia lo anunciado es exigible, asi que eso no es un cero
   * de mas: es una tarifa distinta de la de RN-026 anunciada al pais.
   */
  if (commissionRate <= 0 || commissionRate > COMISION_MAXIMA) {
    throw new Error(
      `COMMISSION_RATE es "${commissionRate}" y tiene que ser una fraccion mayor que 0 y ` +
        `hasta ${COMISION_MAXIMA}: 0.05 es el 5%. Un 5 se anunciaria como 500% y un 0, ` +
        'como que no se cobra nada (RN-026).',
    );
  }
  if (
    !Number.isInteger(claimWindowDays) ||
    claimWindowDays < 1 ||
    claimWindowDays > VENTANA_DE_RECLAMO_MAXIMA
  ) {
    throw new Error(
      `CLAIM_WINDOW_DAYS es "${claimWindowDays}" y tiene que ser un entero de dias habiles ` +
        `entre 1 y ${VENTANA_DE_RECLAMO_MAXIMA} (RN-051). Una ventana de meses no es una ` +
        'ventana, y el dinero del comprador queda retenido todo ese tiempo.',
    );
  }

  /*
   * El tope es holgado a proposito: lo que hay que evitar es un cero o un numero
   * absurdo, no acotar una decision de operacion. Un cero prometeria revisar "en cero
   * dias habiles", que no significa nada, y en Colombia lo anunciado es exigible.
   */
  if (
    !Number.isInteger(verificationReviewDays) ||
    verificationReviewDays < 1 ||
    verificationReviewDays > REVISION_MAXIMA
  ) {
    throw new Error(
      `VERIFICATION_REVIEW_DAYS es "${verificationReviewDays}" y tiene que ser un entero de dias ` +
        `habiles entre 1 y ${REVISION_MAXIMA} (criterio 6 de HU-002).`,
    );
  }

  /*
   * Lo mismo para la revision de una publicacion. Es una variable aparte y no la
   * misma de la verificacion porque son dos promesas distintas a dos personas en
   * dos momentos distintos: revisar una cedula y revisar unas fotos no tienen por
   * que tardar lo mismo, y atarlas obligaria a cambiar las dos para mover una.
   */
  if (
    !Number.isInteger(listingReviewDays) ||
    listingReviewDays < 1 ||
    listingReviewDays > REVISION_MAXIMA
  ) {
    throw new Error(
      `LISTING_REVIEW_DAYS es "${listingReviewDays}" y tiene que ser un entero de dias ` +
        `habiles entre 1 y ${REVISION_MAXIMA} (HU-007).`,
    );
  }

  return { commissionRate, claimWindowDays, verificationReviewDays, listingReviewDays };
}

/** Una variable ausente o en blanco toma el valor por omision; una con basura, no. */
function numeroOpcional(env: EnvironmentVariables, name: string, porOmision: number): number {
  const bruto = env[name]?.trim();
  if (bruto === undefined || bruto.length === 0) {
    return porOmision;
  }

  const valor = Number(bruto);
  if (!Number.isFinite(valor)) {
    throw new Error(
      `${name} vale "${bruto}", que no es un numero. Las claves del frontend estan en ` +
        'docs/operacion/configuracion.md y en .env.example.',
    );
  }
  return valor;
}

/**
 * Los datos de la empresa para el pie. Ninguno es obligatorio aqui.
 *
 * <p>Comparten nombre con las variables del backend a proposito: es la misma
 * empresa y no tendria sentido que el pie dijera un NIT y los correos otro.
 */
function leerEmpresa(env: EnvironmentVariables): CompanyInfo {
  return {
    name: opcional(env['COMPANY_NAME']),
    taxId: opcional(env['COMPANY_TAX_ID']),
    address: opcional(env['COMPANY_ADDRESS']),
    supportEmail: opcional(env['SUPPORT_EMAIL']),
  };
}

/** Una variable en blanco es una variable ausente, igual que en el resto del archivo. */
function opcional(valor: string | undefined): string | null {
  const limpio = valor?.trim();
  return limpio && limpio.length > 0 ? limpio : null;
}

/**
 * Lo que falta y no impide arrancar, para que quede dicho en el registro.
 *
 * <p>Ninguno de estos datos tumba el servidor: el pie los omite y el sitio
 * funciona. Pero un despliegue sin ellos es un despliegue a medias y no debe
 * pasar en silencio, que es justo como se llega a produccion sin NIT en el pie.
 *
 * <p>{@code SUPPORT_EMAIL} es el que mas pesa: sin el no hay canal visible para
 * ejercer los derechos del titular de los datos y se incumple la Ley 1581
 * (docs/operacion/datos-personales.md).
 *
 * <p>Devuelve los avisos en vez de escribirlos para que la funcion siga siendo
 * pura y se pueda probar sin capturar la consola. Quien los escribe es
 * src/server.ts, al arrancar.
 */
export function avisosDeConfiguracion(config: AppConfig): string[] {
  const faltantes: string[] = [];
  const { company } = config;

  if (company.name === null) faltantes.push('COMPANY_NAME');
  if (company.taxId === null) faltantes.push('COMPANY_TAX_ID');
  if (company.address === null) faltantes.push('COMPANY_ADDRESS');
  if (company.supportEmail === null) faltantes.push('SUPPORT_EMAIL');

  if (faltantes.length === 0) {
    return [];
  }

  const aviso =
    `Faltan variables de empresa: ${faltantes.join(', ')}. El pie omitira esos datos. ` +
    'Ver docs/operacion/configuracion.md.';

  return company.supportEmail === null
    ? [
        aviso,
        'Sin SUPPORT_EMAIL el pie no ofrece canal de contacto, y ese canal es ' +
          'obligatorio para ejercer los derechos del titular de los datos ' +
          '(Ley 1581 de 2012, docs/operacion/datos-personales.md).',
      ]
    : [aviso];
}

function leerVersionesLegales(env: EnvironmentVariables): LegalVersions {
  return {
    terms: conValorPorOmision(env['LEGAL_TERMS_VERSION']),
    privacy: conValorPorOmision(env['LEGAL_PRIVACY_VERSION']),
    cookies: conValorPorOmision(env['LEGAL_COOKIES_VERSION']),
  };
}

function conValorPorOmision(valor: string | undefined): string {
  const limpio = valor?.trim();
  return limpio && limpio.length > 0 ? limpio : VERSION_DE_BORRADOR;
}

/**
 * Comprobacion aparte porque esta variable no la lee la aplicacion sino
 * @angular/ssr: es la lista de nombres de dominio a los que el servidor acepta
 * responder, y protege contra falsificacion de peticiones del lado del servidor.
 *
 * Se valida al arrancar porque, si falta, Angular no lanza: registra un aviso y
 * entrega la pagina sin renderizar, para que la pinte el navegador. El sitio
 * parece funcionar mientras el buscador y la vista previa de WhatsApp reciben un
 * documento vacio, que es justo lo que ADR-0006 existe para impedir.
 */
export function assertRenderingEnvironment(env: EnvironmentVariables): void {
  requireValue(env, 'NG_ALLOWED_HOSTS');
}

/**
 * Variante que nunca lanza.
 *
 * Al construir, Angular arranca la aplicacion en un entorno vacio para extraer
 * las rutas, y ahi no existe ninguna variable. Fallar en ese momento impediria
 * compilar en una maquina de integracion continua, que es precisamente donde no
 * hay ni debe haber configuracion de ejecucion.
 *
 * La validacion de verdad ocurre al arrancar el servidor, en src/server.ts, que
 * es el momento en el que fallar sirve de algo: antes de atender a nadie. Si
 * pese a todo se llegara a renderizar con esta configuracion de relleno, la
 * primera peticion a la API falla con un mensaje explicito y no en silencio.
 */
export function readAppConfigForBootstrap(env: EnvironmentVariables): AppConfig {
  try {
    return readAppConfig(env);
  } catch {
    return {
      apiBaseUrl: '',
      defaultLocale: FALLBACK_DEFAULT_LOCALE,
      availableLocales: FALLBACK_AVAILABLE_LOCALES.split(','),
      enableDevtools: false,
      sentryDsn: null,
      legalVersions: {
        terms: VERSION_DE_BORRADOR,
        privacy: VERSION_DE_BORRADOR,
        cookies: VERSION_DE_BORRADOR,
      },
      company: { name: null, taxId: null, address: null, supportEmail: null },
      business: {
        commissionRate: COMISION_POR_OMISION,
        claimWindowDays: VENTANA_DE_RECLAMO_POR_OMISION,
        verificationReviewDays: REVISION_POR_OMISION,
        listingReviewDays: REVISION_DE_PUBLICACION,
      },
      features: {
        catalog: false,
        checkout: false,
        publishing: false,
        sellerVerification: false,
        search: false,
      },
    };
  }
}

function requireValue(env: EnvironmentVariables, name: string): string {
  const value = env[name]?.trim();
  if (!value) {
    throw new Error(
      `Falta la variable de entorno ${name}. Las claves del frontend estan en ` +
        'docs/operacion/configuracion.md y en .env.example.',
    );
  }
  return value;
}

/** Evita que una barra final duplique la barra que pone el interceptor de la API. */
function trimTrailingSlash(value: string): string {
  return value.endsWith('/') ? value.slice(0, -1) : value;
}
