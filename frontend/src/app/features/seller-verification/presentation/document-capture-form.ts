import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';

import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';
import { VerificationStore } from '../application/verification.store';
import type { IdentityDocumentType } from '../domain/verification';
import { CaptureField } from './capture-field';

/**
 * El documento de identidad: tipo, número, titular y las dos caras. Criterio 2 de HU-002.
 *
 * <p>Las dos fotos son obligatorias las dos. Una sola cara no sirve: el número está en
 * una y la fecha de vencimiento suele estar en la otra, y sin ella el motivo de rechazo
 * `EXPIRED_DOCUMENT` no se puede comprobar. El dominio del servidor lo exige también.
 *
 * <p>Los tres tipos son los del glosario y no hay pasaporte, porque no se pidió.
 */
@Component({
  selector: 'sendik-document-capture-form',
  imports: [ReactiveFormsModule, TranslocoPipe, TextField, SubmitButton, CaptureField],
  templateUrl: './document-capture-form.html',
  styleUrl: './bank-account-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class DocumentCaptureForm {
  private readonly store = inject(VerificationStore);

  protected readonly envio = this.store.documentSubmission;

  protected readonly tipos: readonly IdentityDocumentType[] = ['CC', 'CE', 'PPT'];

  protected readonly form = new FormGroup({
    tipo: new FormControl('', { nonNullable: true }),
    numero: new FormControl('', { nonNullable: true }),
    titular: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  /** Las dos caras, cada una cuando la cámara la entrega nítida. */
  protected readonly frente = signal<Blob | null>(null);
  protected readonly reverso = signal<Blob | null>(null);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  private readonly tipoValido = computed(() => (this.valores().tipo ?? '') !== '');

  private readonly numeroValido = computed(() =>
    /^[0-9 .-]{5,20}$/.test((this.valores().numero ?? '').trim()),
  );

  private readonly titularValido = computed(
    () => (this.valores().titular ?? '').trim().length >= 2,
  );

  private readonly fotosListas = computed(() => this.frente() !== null && this.reverso() !== null);

  protected readonly errorDeTipo = computed(() =>
    this.intentado() && !this.tipoValido() ? 'sellerVerification.documentForm.errors.type' : null,
  );

  protected readonly errorDeNumero = computed(() =>
    this.intentado() && !this.numeroValido()
      ? 'sellerVerification.documentForm.errors.number'
      : null,
  );

  protected readonly errorDeTitular = computed(() =>
    this.intentado() && !this.titularValido()
      ? 'sellerVerification.documentForm.errors.holder'
      : null,
  );

  protected readonly errorDeFotos = computed(() =>
    this.intentado() && !this.fotosListas()
      ? 'sellerVerification.documentForm.errors.photos'
      : null,
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.envio.error();
    return fallo === null ? null : VerificationStore.claveDeError(fallo);
  });

  protected control(nombre: 'numero' | 'titular'): FormControl<string> {
    return this.form.controls[nombre];
  }

  protected guardar(): void {
    this.intentado.set(true);

    const frente = this.frente();
    const reverso = this.reverso();

    if (!this.tipoValido() || !this.numeroValido() || !this.titularValido()) {
      return;
    }
    if (frente === null || reverso === null) {
      return;
    }

    this.envio.mutate({
      datos: {
        tipo: (this.valores().tipo ?? '') as IdentityDocumentType,
        numero: (this.valores().numero ?? '').trim(),
        titular: (this.valores().titular ?? '').trim(),
      },
      frente,
      reverso,
    });
  }
}
