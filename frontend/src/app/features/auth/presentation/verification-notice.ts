import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { SessionStore } from '../../../core/session/session.store';
import { AuthStore } from '../application/auth.store';

/**
 * Aviso de correo sin verificar, con su boton de reenvio. Criterio 13 de HU-001.
 *
 * <p>Quien entra sin haber verificado su correo tiene sesion, pero no deberia
 * poder hacer nada mas que esto. La mitad que impide hacerlo es del servidor,
 * que exige la cuenta verificada donde toca; esta es la mitad que lo explica, y
 * sin ella la persona solo veria fallar cosas sin saber por que.
 *
 * <p>No se muestra a quien no tiene sesion: sin sesion, la ausencia de
 * verificacion no significa nada.
 */
@Component({
  selector: 'sendik-verification-notice',
  imports: [TranslocoPipe],
  templateUrl: './verification-notice.html',
  styleUrl: './verification-notice.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificationNotice {
  private readonly store = inject(AuthStore);
  private readonly sesion = inject(SessionStore);

  protected readonly reenvio = this.store.emailVerificationRequest;

  protected readonly visible = computed(
    () => this.sesion.isAuthenticated() && !this.sesion.emailVerified(),
  );

  protected readonly errorDelReenvio = computed(() => {
    const fallo = this.reenvio.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  /**
   * El corte al doble envio vive aqui y no en un {@code disabled}: deshabilitar
   * el boton que la persona acaba de pulsar le quita el foco y lo manda al
   * principio del documento.
   */
  protected reenviar(): void {
    if (this.reenvio.isPending()) {
      return;
    }
    this.reenvio.mutate();
  }
}
