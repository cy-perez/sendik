import { describe, expect, it } from 'vitest';

import { buildThemeCookie, resolveTheme } from './theme';

describe('resolveTheme', () => {
  it('sirve el modo claro cuando no hay ninguna pista', () => {
    expect(resolveTheme({})).toBe('light');
  });

  it('respeta la preferencia guardada', () => {
    expect(resolveTheme({ cookieHeader: 'sendik_theme=dark' })).toBe('dark');
    expect(resolveTheme({ cookieHeader: 'sendik_theme=light' })).toBe('light');
  });

  // En la primera visita el navegador todavia no manda la pista, porque el
  // sitio la pide con Accept-CH en esa misma respuesta.
  it('usa la preferencia del sistema cuando llega la pista del navegador', () => {
    expect(resolveTheme({ colorSchemeHint: 'dark' })).toBe('dark');
    expect(resolveTheme({ colorSchemeHint: 'light' })).toBe('light');
  });

  it('la eleccion de la persona gana sobre la del sistema', () => {
    expect(resolveTheme({ cookieHeader: 'sendik_theme=light', colorSchemeHint: 'dark' })).toBe(
      'light',
    );
  });

  it('ignora una cookie con un valor que no existe', () => {
    expect(resolveTheme({ cookieHeader: 'sendik_theme=neon', colorSchemeHint: 'dark' })).toBe(
      'dark',
    );
  });
});

describe('buildThemeCookie', () => {
  it('limita la cookie al sitio y le da un ano de vida', () => {
    const cookie = buildThemeCookie('dark');

    expect(cookie).toContain('sendik_theme=dark');
    expect(cookie).toContain('Path=/');
    expect(cookie).toContain('SameSite=Lax');
    expect(cookie).toContain('Max-Age=31536000');
  });
});
