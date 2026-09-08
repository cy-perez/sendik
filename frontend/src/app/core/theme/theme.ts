import { parseCookies } from '../i18n/locale';

/**
 * Resolucion del tema. TypeScript puro: corre en el servidor antes de pintar y
 * se prueba sin TestBed.
 */
export type Theme = 'light' | 'dark';

export const THEME_COOKIE = 'sendik_theme';
export const THEME_COOKIE_MAX_AGE_SECONDS = 60 * 60 * 24 * 365;

/*
 * Aqui vivian themeAttribute y themeFromAttribute, que traducian el tema al
 * atributo data-tema del <html>. Ese atributo existia por una sola razon: era lo
 * que leia tokens.css, que estaba generado y usaba valores en espanol. Retirada
 * esa hoja (ADR-0032) el atributo no lo leia ya nadie, asi que se fue con ella y
 * las dos funciones con el. La marca es la clase, y solo la clase.
 */

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

/**
 * La clase que lee la variante `dark` de Tailwind, declarada en
 * styles/tema.css. Convive con el atributo data-tema mientras dure la
 * migracion: el atributo es lo que sigue leyendo tokens.css, que es generado y
 * no se puede editar, y la clase es lo que leen las utilidades nuevas. Los dos
 * los escribe el servidor en la misma pasada, antes de pintar, que es lo que
 * evita el parpadeo. Cuando la Fase 3 retire tokens.css, se va el atributo y
 * queda la clase.
 */
export const THEME_DARK_CLASS = 'dark';

export function isDark(theme: Theme): boolean {
  return theme === 'dark';
}
