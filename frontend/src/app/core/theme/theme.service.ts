import { DOCUMENT, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

import { buildThemeCookie, isDark, THEME_DARK_CLASS, type Theme } from './theme';

/**
 * El tema ya viene resuelto en el documento, escrito por el servidor. Este
 * servicio solo lo cambia: no lo decide en el arranque, que es justo lo que
 * produciria el parpadeo que se quiere evitar.
 *
 * <p>La marca es la clase que lee la variante dark de Tailwind, y solo esa.
 * Hasta la ADR-0032 se escribia ademas un atributo data-tema, porque era lo que
 * leia tokens.css; retirada esa hoja, el atributo no lo leia ya nadie y se fue
 * con ella.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly active = signal<Theme>(
    this.document.documentElement.classList.contains(THEME_DARK_CLASS) ? 'dark' : 'light',
  );

  readonly current = this.active.asReadonly();

  toggle(): void {
    this.set(this.active() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    if (theme === this.active()) {
      return;
    }

    this.document.documentElement.classList.toggle(THEME_DARK_CLASS, isDark(theme));

    this.active.set(theme);

    if (this.isBrowser) {
      this.document.cookie = buildThemeCookie(theme);
    }
  }
}
