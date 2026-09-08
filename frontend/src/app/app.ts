import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { SessionMenu } from './features/auth/presentation/session-menu';
import { VerificationNotice } from './features/auth/presentation/verification-notice';
import { SiteHeader } from './layout/site-header';
import { SiteFooter } from './layout/site-footer';

/**
 * La raiz es el unico sitio que puede juntar `shared` con una funcionalidad: la
 * cabecera ofrece el hueco con ng-content y aqui se decide que va dentro.
 */
@Component({
  selector: 'sendik-root',
  imports: [RouterOutlet, SiteHeader, SiteFooter, SessionMenu, VerificationNotice],
  templateUrl: './app.html',
  host: { class: 'flex min-h-screen flex-col' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {}
