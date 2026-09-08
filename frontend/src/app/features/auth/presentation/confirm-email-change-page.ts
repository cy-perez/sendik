import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';

/**
 * Confirma el correo nuevo con el enlace que llego a ese buzon. Criterio 21.
 *
 * <p>El enlace <strong>no se consume al abrir la pagina</strong>, sino al pulsar
 * el boton. Es la misma decision del restablecimiento de contrasena y por el
 * mismo motivo: una vista previa de enlace en WhatsApp o un antivirus de correo
 * abren la direccion sin que nadie la haya visto, y consumirian el enlace antes
 * de que la persona llegara a el.
 *
 * <p>No exige sesion: se llega desde un correo, y ese correo se abre la mitad de
 * las veces en otro dispositivo. La credencial es el token del enlace.
 */
@Component({
  selector: 'sendik-confirm-email-change-page',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './confirm-email-change-page.html',
  styleUrl: './confirm-email-change-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmEmailChangePage {
  /** Llega de la ruta por withComponentInputBinding. */
  readonly token = input<string | undefined>(undefined);

  private readonly store = inject(AuthStore);

  protected readonly confirmacion = this.store.emailChangeConfirmation;

  private readonly tokenLimpio = computed(() => (this.token() ?? '').trim());

  protected readonly sinToken = computed(() => this.tokenLimpio() === '');

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.confirmacion.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected confirmar(): void {
    this.confirmacion.mutate(this.tokenLimpio());
  }
}
