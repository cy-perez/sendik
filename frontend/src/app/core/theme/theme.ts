import { parseCookies } from '../i18n/locale';

/**
 * Resolucion del tema. TypeScript puro: corre en el servidor antes de pintar y
 * se prueba sin TestBed.
 */
export type Theme = 'light' | 'dark';

export const THEME_COOKIE = 'sendik_theme';
export const THEME_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

/**
 * El atributo que lee tokens.css. Los valores son los del sistema de diseno y
 * estan en espanol: aqui se traduce una sola vez, en la frontera, en vez de
 * arrastrar la cadena por toda la aplicacion.
 */
export function themeAttribute(theme: Theme): 'claro' | 'oscuro' {
  return theme === 'dark' ? 'oscuro' : 'claro';
}

export function themeFromAttribute(value: string | null | undefined): Theme {
  return value === 'oscuro' ? 'dark' : 'light';
}

export interface ThemeResolution {
  readonly cookieHeader?: string | null;
  /**
   * Cabecera Sec-CH-Prefers-Color-Scheme. El navegador solo la manda si el
   * sitio la pidio antes con Accept-CH, asi que en la primera visita no llega y
   * se cae al modo claro. A partir de la segunda, la preferencia del sistema se
   * respeta ya en el HTML servido, sin parpadeo.
   */
  readonly colorSchemeHint?: string | null;
}

export function resolveTheme(resolution: ThemeResolution): Theme {
  const fromCookie = parseCookies(resolution.cookieHeader)[THEME_COOKIE];
  if (fromCookie === 'dark' || fromCookie === 'light') {
    return fromCookie;
  }
  return resolution.colorSchemeHint?.trim() === 'dark' ? 'dark' : 'light';
}

/** Cookie de primera parte, sin datos personales: es una preferencia de interfaz. */
export function buildThemeCookie(theme: Theme): string {
  return `${THEME_COOKIE}=${theme}; Path=/; Max-Age=${THEME_COOKIE_MAX_AGE_SECONDS}; SameSite=Lax`;
}
