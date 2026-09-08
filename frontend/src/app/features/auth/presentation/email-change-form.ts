import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import { esCorreoValido } from '../domain/credentials';
import { esElMismoCorreo } from '../domain/profile';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Cambiar el correo de la cuenta. Criterio 21 de HU-001.
 *
 * <p><strong>Pedirlo no lo cambia.</strong> El servidor manda un enlace al correo
 * nuevo y no reemplaza nada hasta que alguien lo abre. Si lo reemplazara antes,
 * quien escribiera mal una letra se quedaria fuera de su cuenta sin forma de
 * volver.
 *
 * <p>El aviso de "revisa tu correo" sale igual este la direccion libre u ocupada,
 * porque el servidor responde igual en los dos casos: cualquier diferencia
 * convertiria este formulario en una forma de averiguar quien tiene cuenta, que
 * es justo lo que el registro evita (criterio 2).
 */
@Component({
  selector: 'sendik-email-change-form',
  imports: [ReactiveFormsModule, TranslocoPipe, TextField, SubmitButton],
  templateUrl: './email-change-form.html',
  styleUrl: './email-change-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EmailChangeForm {
  private readonly store = inject(AuthStore);

  protected readonly perfil = this.store.profile;
  protected readonly peticion = this.store.emailChangeRequest;

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly escrito = toSignal(this.form.controls.email.valueChanges, {
    initialValue: '',
  });

  /** El correo actual, para no mandar a nadie a confirmar el que ya tiene. */
  private readonly actual = computed(() => this.perfil.data()?.email ?? '');

  protected readonly correoError = computed(() => {
    if (!this.intentado()) {
      return null;
    }
    if (!esCorreoValido(this.escrito())) {
      return 'auth.account.email.errors.format';
    }
    if (esElMismoCorreo(this.escrito(), this.actual())) {
      return 'auth.account.email.errors.same';
    }
    return null;
  });

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.peticion.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  /** El correo al que se mando el enlace, para poder nombrarlo en el aviso. */
  protected readonly enviadoA = computed(() => this.peticion.variables() ?? '');

  protected control(): FormControl<string> {
    return this.form.controls.email;
  }

  protected enviar(): void {
    this.intentado.set(true);
    if (this.correoError() !== null) {
      return;
    }

    this.peticion.mutate(this.form.getRawValue().email.trim(), {
      // Lo escrito no se queda en el formulario: el aviso ya dice a donde fue.
      onSuccess: () => {
        this.form.reset();
        this.intentado.set(false);
      },
    });
  }
}
