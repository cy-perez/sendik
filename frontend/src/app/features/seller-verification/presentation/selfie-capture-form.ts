import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { VerificationStore } from '../application/verification.store';
import { CaptureField } from './capture-field';

/**
 * La selfie. Criterio 3 de HU-002.
 *
 * <p>Un solo campo y un botón: no hay nada que escribir. Lo que hace este componente que
 * el campo de captura no hace es subirla, y separar las dos cosas es lo que permite que
 * el mismo campo sirva para el documento y para la cara.
 *
 * <p>Pide la cámara frontal, que es la que enfoca una cara sin girar el teléfono.
 */
@Component({
  selector: 'sendik-selfie-capture-form',
  imports: [TranslocoPipe, CaptureField],
  templateUrl: './selfie-capture-form.html',
  styleUrl: './bank-account-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelfieCaptureForm {
  private readonly store = inject(VerificationStore);

  protected readonly envio = this.store.selfieSubmission;

  protected readonly foto = signal<Blob | null>(null);
  protected readonly intentado = signal(false);

  protected readonly errorDeFoto = computed(() =>
    this.intentado() && this.foto() === null ? 'sellerVerification.selfieForm.errors.photo' : null,
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.envio.error();
    return fallo === null ? null : VerificationStore.claveDeError(fallo);
  });

  protected guardar(): void {
    this.intentado.set(true);

    const imagen = this.foto();
    if (imagen === null) {
      return;
    }

    this.envio.mutate(imagen);
  }
}
