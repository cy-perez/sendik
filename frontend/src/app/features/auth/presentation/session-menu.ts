import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { SessionStore } from '../../../core/session/session.store';
import { AuthStore } from '../application/auth.store';

/**
 * Entrar o salir, segun haya sesion. Criterio 16 de HU-001.
 *
 * <p>Vive en la funcionalidad de cuentas y no en la cabecera: la cabecera es de
 * `shared`, que no puede depender de una funcionalidad. Se proyecta dentro de
 * ella desde app.html, que es el unico sitio con permiso para juntar las dos
 * cosas.
 *
 * <p>Sin llamada a la accion rellena: hay una sola por pantalla y le
 * corresponde a la accion principal del contenido, no a un control de la
 * cabecera que sale en todas. Tampoco lleva el bronce, que en Sendik es solo de
 * lo verificado.
 */
@Component({
  selector: 'sendik-session-menu',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './session-menu.html',
  styleUrl: './session-menu.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SessionMenu {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);

  protected readonly sesion = inject(SessionStore);
  protected readonly cierre = this.store.logout;

  protected salir(): void {
    this.cierre.mutate(undefined, {
      // Se vuelve al inicio pase lo que pase: el almacen ya limpio la sesion en
      // onSettled, asi que quedarse en una ruta de cuenta mostraria una pantalla
      // sin datos.
      onSettled: () => {
        void this.router.navigateByUrl('/');
      },
    });
  }
}
