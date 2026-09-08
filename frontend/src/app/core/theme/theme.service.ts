import { DOCUMENT, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { isPlatformBrowser } from '@angular/common';

import { buildThemeCookie, themeAttribute, themeFromAttribute, type Theme } from './theme';

/**
 * El tema ya viene resuelto en el atributo data-tema del documento, escrito por
 * el servidor. Este servicio solo lo cambia: no lo decide en el arranque, que es
 * justo lo que produciria el parpadeo que se quiere evitar.
 */
@Injectable({ providedIn: 'root' })
export class ThemeService {
  private readonly document = inject(DOCUMENT);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly active = signal<Theme>(
    themeFromAttribute(this.document.documentElement.getAttribute('data-tema')),
  );

  readonly current = this.active.asReadonly();

  toggle(): void {
    this.set(this.active() === 'dark' ? 'light' : 'dark');
  }

  set(theme: Theme): void {
    if (theme === this.active()) {
      return;
    }

    this.document.documentElement.setAttribute('data-tema', themeAttribute(theme));
    this.active.set(theme);

    if (this.isBrowser) {
      this.document.cookie = buildThemeCookie(theme);
    }
  }
}
