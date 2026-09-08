import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiError } from '../../../core/http/api-error';
import { AuthStore } from '../application/auth.store';
import { cumpleElLargoMinimo, fuerzaDe } from '../domain/password-policy';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Pone la contrasena nueva con el enlace del correo. Criterios 18 y 20.
 *
 * <p>Reutiliza la politica del registro entera. Recuperar el acceso no admite una
 * contrasena peor: si la admitiera, bastaria pedir el enlace para saltarse RN-005.
 *
 * <p>Al terminar <strong>no se abre sesion</strong>. El criterio 20 acaba de
 * cerrar todas, y emitir una aqui la dejaria exenta de lo que se acaba de hacer.
 * Se envia a iniciar sesion, que ademas es cuando la contrasena nueva se recuerda.
 *
 * <p>Como en la verificacion de correo, el token llega por parametro de consulta.
 * A diferencia de aquella, aqui <strong>no</strong> se envia nada al cargar: el
 * token se consume al enviar el formulario, no al abrir la pagina, asi que una
 * vista previa de enlace en WhatsApp no puede gastarlo.
 */
@Component({
  selector: 'sendik-reset-password-page',
  imports: [ReactiveFormsModule, TranslocoPipe, RouterLink, TextField, SubmitButton],
  templateUrl: './reset-password-page.html',
  styleUrl: './reset-password-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPasswordPage {
  /** Llega de la ruta por withComponentInputBinding. */
  readonly token = input<string | undefined>(undefined);

  private readonly tokenLimpio = computed(() => (this.token() ?? '').trim());

  private readonly store = inject(AuthStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly form = new FormGroup({
    password: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly cambio = this.store.passwordReset;

  protected readonly sinToken = computed(() => this.tokenLimpio() === '');

  protected readonly passwordError = computed(() =>
    this.errorSi(
      !cumpleElLargoMinimo(this.valores().password ?? ''),
      'auth.reset.errors.passwordTooShort',
    ),
  );

  /** Orientativa, nunca bloquea: la misma del registro. */
  protected readonly fuerza = computed(() => fuerzaDe(this.valores().password ?? ''));

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.cambio.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  /**
   * Solo cuando el enlace ya no sirve se ofrece pedir otro. Si lo que fallo fue la
   * contrasena, el enlace sigue vivo y mandarla a pedir uno nuevo la haria repetir
   * un paso que no hacia falta.
   */
  protected readonly enlaceInservible = computed(() => {
    const fallo = this.cambio.error();
    return (
      fallo instanceof ApiError &&
      (fallo.code === 'AUTH_RESET_TOKEN_EXPIRED' || fallo.code === 'AUTH_RESET_TOKEN_INVALID')
    );
  });

  protected enviar(): void {
    this.intentado.set(true);
    if (this.passwordError() !== null) {
      this.enfocarElError();
      return;
    }

    this.cambio.mutate({
      token: this.tokenLimpio(),
      newPassword: this.form.getRawValue().password,
    });
  }

  protected control(): FormControl<string> {
    return this.form.controls.password;
  }

  private enfocarElError(): void {
    afterNextRender(
      () => {
        this.host.nativeElement.querySelector<HTMLInputElement>('#contrasena')?.focus();
      },
      { injector: this.injector },
    );
  }

  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }
}
