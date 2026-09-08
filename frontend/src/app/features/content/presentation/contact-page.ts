import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import { RUTAS_LEGALES } from '../../../core/routes/legal-routes';
import { SessionStore } from '../../../core/session/session.store';

/**
 * Contacto. HU-005, criterios 19 a 22.
 *
 * <p>El correo de soporte sale de la configuracion. Si no esta, la seccion no se
 * pinta vacia: sin ese canal no hay forma de ejercer los derechos del titular de
 * los datos y se incumple la Ley 1581 (docs/operacion/datos-personales.md), asi
 * que el servidor lo registra como aviso al arrancar.
 *
 * <p>{@code SessionStore} vive en `core`, no en `features/auth`: esta pagina no
 * importa de otra funcionalidad (frontend/CLAUDE.md).
 */
@Component({
  selector: 'sendik-contact-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './contact-page.html',
  styleUrl: './content-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ContactPage {
  private readonly sesion = inject(SessionStore);

  protected readonly correoDeSoporte = inject(APP_CONFIG).company.supportEmail;

  /**
   * Criterio 21. A quien ya entro se le dice que sus datos los cambia el mismo,
   * en vez de hacerle escribir un correo para algo que resuelve en dos clics.
   */
  protected readonly tieneSesion = this.sesion.isAuthenticated;

  /**
   * La direccion sale de RUTAS_LEGALES y no escrita a mano: si la ruta del
   * documento cambia, este enlace cambia con ella. Es la unica forma de que no
   * exista un enlace roto justo en la pagina que la ley obliga a ofrecer.
   */
  protected readonly rutaDeTratamiento = RUTAS_LEGALES.privacy;
}
