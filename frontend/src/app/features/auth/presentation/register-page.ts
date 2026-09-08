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
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import { RUTAS_LEGALES } from '../../../core/routes/legal-routes';
import { esCorreoValido } from '../domain/credentials';
import { cumpleElLargoMinimo, fuerzaDe } from '../domain/password-policy';
import { esMayorDeEdad } from '../domain/registration';
import { CheckboxField } from '../../../shared/ui/field/checkbox-field';
import { PrivacyNotice } from '../../../shared/legal/privacy-notice';
import { SubmitButton } from '../../../shared/ui/field/submit-button';
import { TextField } from '../../../shared/ui/field/text-field';
import { FormError } from '../../../shared/ui/field/form-error';

/**
 * Formulario de registro. Criterios 1 a 6 de HU-001.
 *
 * <p>La validacion del cliente existe para no hacer viajar lo que ya se sabe
 * incorrecto y para responder al instante. La que decide sigue siendo la del
 * servidor: si las dos difieren, manda el servidor.
 *
 * <p>Los errores solo aparecen despues del primer intento de envio. Marcarlos
 * mientras la persona escribe la primera letra de su correo la corrige antes de
 * que haya terminado de equivocarse.
 */
@Component({
  selector: 'sendik-register-page',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TextField,
    CheckboxField,
    SubmitButton,
    PrivacyNotice,
    FormError,
  ],
  templateUrl: './register-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RegisterPage {
  /**
   * Salen de core y no se escriben a mano: son las mismas constantes con las que
   * se declaran las rutas, asi que no puede haber un enlace que apunte a una
   * direccion que no existe.
   */
  protected readonly rutaDeTerminos = RUTAS_LEGALES.terms;
  protected readonly rutaDePrivacidad = RUTAS_LEGALES.privacy;

  private readonly store = inject(AuthStore);
  private readonly transloco = inject(TranslocoService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true }),
    displayName: new FormControl('', { nonNullable: true }),
    birthDate: new FormControl('', { nonNullable: true }),
    acceptsTerms: new FormControl(false, { nonNullable: true }),
    acceptsPrivacy: new FormControl(false, { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly registro = this.store.registration;

  protected readonly emailError = computed(() =>
    this.errorSi(!esCorreoValido(this.valores().email ?? ''), 'auth.register.errors.email'),
  );

  protected readonly passwordError = computed(() =>
    this.errorSi(
      !cumpleElLargoMinimo(this.valores().password ?? ''),
      'auth.register.errors.passwordTooShort',
    ),
  );

  protected readonly displayNameError = computed(() =>
    this.errorSi(
      (this.valores().displayName ?? '').trim().length < 2,
      'auth.register.errors.displayName',
    ),
  );

  protected readonly birthDateError = computed(() => {
    const fecha = this.valores().birthDate ?? '';
    if (fecha === '') {
      return this.errorSi(true, 'auth.register.errors.birthDateRequired');
    }
    return this.errorSi(!esMayorDeEdad(fecha, new Date()), 'auth.register.errors.underage');
  });

  // Dos casillas separadas y dos mensajes separados: decir "acepta las
  // condiciones" cuando falta una de las dos no dice cual.
  protected readonly termsError = computed(() =>
    this.errorSi(!this.valores().acceptsTerms, 'auth.register.errors.termsRequired'),
  );

  protected readonly privacyError = computed(() =>
    this.errorSi(!this.valores().acceptsPrivacy, 'auth.register.errors.privacyRequired'),
  );

  /** Criterio 4: orientativa, nunca bloquea el envio. */
  protected readonly fuerza = computed(() => fuerzaDe(this.valores().password ?? ''));

  protected readonly hayErrores = computed(
    () =>
      this.emailError() !== null ||
      this.passwordError() !== null ||
      this.displayNameError() !== null ||
      this.birthDateError() !== null ||
      this.termsError() !== null ||
      this.privacyError() !== null,
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.registro.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected enviar(): void {
    this.intentado.set(true);
    if (this.hayErrores()) {
      this.enfocarElPrimerError();
      return;
    }

    const valores = this.form.getRawValue();
    this.registro.mutate({
      email: valores.email,
      password: valores.password,
      displayName: valores.displayName,
      birthDate: valores.birthDate,
      locale: this.transloco.getActiveLang(),
      acceptsTerms: valores.acceptsTerms,
      acceptsPrivacy: valores.acceptsPrivacy,
    });
  }

  protected control(
    nombre: 'email' | 'password' | 'displayName' | 'birthDate',
  ): FormControl<string> {
    return this.form.controls[nombre];
  }

  protected casilla(nombre: 'acceptsTerms' | 'acceptsPrivacy'): FormControl<boolean> {
    return this.form.controls[nombre];
  }

  /**
   * Al fallar la validacion el foco esta en el boton, al final de un formulario
   * de seis campos, y los mensajes salen repartidos por encima. Sin moverlo, la
   * persona pulsa y no ve que haya pasado nada.
   *
   * <p>El orden es el del formulario, no el de importancia: se lleva al primero
   * que haya que corregir, que es por donde va a seguir bajando.
   */
  private enfocarElPrimerError(): void {
    const primero = [
      ['correo', this.emailError()],
      ['nombre', this.displayNameError()],
      ['contrasena', this.passwordError()],
      ['nacimiento', this.birthDateError()],
      ['terminos', this.termsError()],
      ['privacidad', this.privacyError()],
    ].find(([, error]) => error !== null);

    if (primero === undefined) {
      return;
    }

    afterNextRender(
      () => {
        this.host.nativeElement.querySelector<HTMLInputElement>(`#${primero[0]}`)?.focus();
      },
      { injector: this.injector },
    );
  }

  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }
}
