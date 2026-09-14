import { InjectionToken, makeStateKey } from '@angular/core';

/**
 * Configuracion que el servidor entrega en cada peticion. No se compila dentro
 * del paquete: el mismo artefacto sirve para dev y para prod, y cambiar la URL
 * de la API no exige volver a construir. Ver docs/operacion/configuracion.md.
 */
/**
 * Version vigente de cada documento legal.
 *
 * <p>Las de terminos y tratamiento tienen que valer <strong>lo mismo</strong> que
 * las del backend, que es quien las guarda como evidencia del consentimiento.
 * Salen de las mismas variables de entorno por eso: si el texto que se muestra y
 * el que quedo escrito no son el mismo, la prueba no vale
 * (docs/operacion/datos-personales.md).
 *
 * <p>La de cookies no la conoce el backend porque nadie consiente cookies en un
 * formulario: existe solo para versionar el archivo del texto.
 */
export interface LegalVersions {
  readonly terms: string;
  readonly privacy: string;
  readonly cookies: string;
}

/**
 * Quien responde por el sitio: razon social, NIT, direccion y canal de contacto.
 *
 * <p>En Colombia estos datos en el pie son una senal de que la empresa existe, y
 * el canal de contacto es ademas la via por la que se ejercen los derechos del
 * titular de los datos (docs/operacion/datos-personales.md). Son datos publicos
 * de la empresa: aqui no viaja nada personal de nadie.
 *
 * <p><strong>Todos pueden faltar.</strong> El backend si los exige, porque los
 * necesita para los correos y para la evidencia del consentimiento; el frontend
 * solo los pinta. Tumbar el renderizado entero porque falta una direccion seria
 * cambiar un pie incompleto por un sitio caido.
 */
export interface CompanyInfo {
  readonly name: string | null;
  readonly taxId: string | null;
  readonly address: string | null;
  readonly supportEmail: string | null;
}

/**
 * Cifras de negocio que el sitio informativo <strong>anuncia</strong>.
 *
 * <p>Viajan al navegador porque las paginas de HU-005 las dicen en voz alta: la
 * comision en el recorrido del vendedor (RN-026) y la ventana de reclamo en el
 * del comprador (RN-051). No son secretas: cualquiera que entre las lee.
 *
 * <p><strong>Nunca se escriben en una plantilla ni en un archivo de traduccion.</strong>
 * En Colombia lo que se anuncia es exigible, asi que una cifra que viva en dos
 * sitios es una promesa que puede contradecirse a si misma. El texto lleva el
 * marcador y el valor lo interpola la aplicacion
 * (docs/operacion/configuracion.md).
 */
export interface BusinessFigures {
  /** Fraccion, no porcentaje: 0.05 es el 5%. La plantilla lo formatea con Intl. */
  readonly commissionRate: number;
  /** Dias habiles desde la entrega para reportar un producto no conforme. */
  readonly claimWindowDays: number;
  /**
   * Dias habiles que se promete tardar en revisar una verificacion de vendedor.
   *
   * Es una promesa que la pantalla dice en voz alta (criterio 6 de HU-002), asi que va
   * por configuracion: cambiarla no puede exigir un despliegue de codigo. Nadie la hace
   * cumplir: una solicitud que tarda mas no cambia de estado sola.
   */
  readonly verificationReviewDays: number;
  /**
   * Dias habiles que se promete tardar en revisar una publicacion (HU-007).
   *
   * Decidido el 26 de agosto de 2026: dos. Hasta entonces la interfaz de publicar no
   * prometia nada, porque no habia plazo que prometer.
   *
   * Es una cifra aparte de la de la verificacion y no la misma reutilizada: son dos
   * promesas distintas, a dos personas y en dos momentos distintos. Revisar una cedula y
   * revisar unas fotos no tienen por que tardar lo mismo, y atarlas obligaria a mover las
   * dos para cambiar una.
   */
  readonly listingReviewDays: number;
}

/**
 * Las banderas de funcionalidad, tal como las ve el sitio.
 *
 * <p>Son las mismas que lee el backend y con los mismos nombres de variable
 * (`docs/operacion/configuracion.md`). El frontend no las usa para esconder rutas
 * —las rutas existen siempre y la API responde 404 cuando algo esta apagado— sino
 * para no **enlazar** lo que no funciona: HU-004 y HU-005 prohiben un enlace que
 * lleve a un 404, y hasta que estas banderas llegaron aqui la unica forma de
 * cumplirlo era no enlazar nada (ADR-0041).
 */
export interface FeatureFlags {
  readonly catalog: boolean;
  readonly checkout: boolean;
  readonly publishing: boolean;
  readonly sellerVerification: boolean;
  /**
   * La unica que no decide un enlace sino un control: con ella apagada el catalogo
   * no pinta la caja de busqueda ni los filtros, porque el backend responde 404 a
   * cualquier parametro de busqueda (HU-014, criterio 26).
   */
  readonly search: boolean;
}

export interface AppConfig {
  /** Base de la API, incluida la version. Ejemplo: https://api.sendik.co/api/v1 */
  readonly apiBaseUrl: string;
  readonly defaultLocale: string;
  readonly availableLocales: readonly string[];
  readonly enableDevtools: boolean;
  readonly sentryDsn: string | null;
  readonly legalVersions: LegalVersions;
  readonly company: CompanyInfo;
  readonly business: BusinessFigures;
  readonly features: FeatureFlags;
}

export const APP_CONFIG = new InjectionToken<AppConfig>('sendik.app-config');

/**
 * Clave con la que la configuracion viaja del servidor al navegador. Solo lleva
 * valores publicos: nada de aqui es secreto ni personal.
 */
export const APP_CONFIG_STATE_KEY = makeStateKey<AppConfig>('sendik.app-config');
