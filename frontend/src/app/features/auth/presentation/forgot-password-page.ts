import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import { esCorreoValido } from '../domain/credentials';
import { MINUTOS_DE_VIGENCIA } from '../domain/password-reset';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Pide el enlace para poner una contrasena nueva. Criterio 19.
 *
 * <p><strong>Esta pantalla no puede decir si el correo existe.</strong> El
 * servidor responde igual en los dos casos, y aqui se muestra el mismo aviso
 * pase lo que pase: "si ese correo tiene cuenta, le llega un enlace". Cualquier
 * otra cosa, incluido un error distinto, convertiria el formulario en una forma
 * de averiguar quien esta registrado en Sendik.
 *
 * <p>Por eso el aviso de exito habla en condicional. Es incomodo de redactar y es
 * lo correcto: la persona que si tiene cuenta entiende igual que revise su buzon.
 */
@Component({
  selector: 'sendik-forgot-password-page',
  imports: [ReactiveFormsModule, TranslocoPipe, RouterLink, TextField, SubmitButton],
  templateUrl: './forgot-password-page.html',
  styleUrl: './forgot-password-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ForgotPasswordPage {
  protected readonly minutos = MINUTOS_DE_VIGENCIA;

  private readonly store = inject(AuthStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly peticion = this.store.passwordResetRequest;

  protected readonly emailError = computed(() =>
    this.errorSi(!esCorreoValido(this.valores().email ?? ''), 'auth.forgot.errors.email'),
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.peticion.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected enviar(): void {
    this.intentado.set(true);
    if (this.emailError() !== null) {
      this.enfocarElError();
      return;
    }

    this.peticion.mutate({ email: this.form.getRawValue().email });
  }

  protected control(): FormControl<string> {
    return this.form.controls.email;
  }

  private enfocarElError(): void {
    afterNextRender(
      () => {
        this.host.nativeElement.querySelector<HTMLInputElement>('#correo')?.focus();
      },
      { injector: this.injector },
    );
  }

  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }
}
