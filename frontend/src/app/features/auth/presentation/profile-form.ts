import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import {
  comoDatoOpcional,
  elNombreEsValido,
  elTelefonoEsValido,
  laCiudadEsValida,
} from '../domain/profile';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Los datos del perfil que se editan de una vez. Criterio 21 de HU-001.
 *
 * <p>El correo no esta aqui y no es un olvido: cambiarlo exige verificar el
 * nuevo antes de reemplazar el anterior, asi que es otro formulario con otro
 * ritmo y otro resultado.
 *
 * <p>Es un componente aparte de la pantalla de cuenta porque tiene su propio
 * formulario, su propia validacion y su propio estado de envio. Tenerlo todo en
 * un componente obligaria a distinguir con prefijos cual error es de cual campo.
 */
@Component({
  selector: 'sendik-profile-form',
  imports: [ReactiveFormsModule, TranslocoPipe, TextField, SubmitButton],
  templateUrl: './profile-form.html',
  styleUrl: './profile-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfileForm {
  private readonly store = inject(AuthStore);

  protected readonly perfil = this.store.profile;
  protected readonly guardado = this.store.profileUpdate;

  protected readonly form = new FormGroup({
    displayName: new FormControl('', { nonNullable: true }),
    city: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  /**
   * Rellena el formulario en cuanto llega el perfil.
   *
   * <p>Es de los pocos usos legitimos de {@code effect}: sincroniza una senal con
   * algo externo al marco de senales, que aqui es el estado imperativo de un
   * FormGroup (frontend/CLAUDE.md). No deriva ningun valor.
   *
   * <p>Solo mientras nadie lo haya tocado. Sin esa condicion, una respuesta que
   * llegue tarde borraria lo que la persona esta escribiendo, que es la peor
   * forma de perder un formulario.
   */
  constructor() {
    effect(() => {
      const actual = this.perfil.data();
      if (actual === undefined || this.form.dirty) {
        return;
      }

      this.form.setValue({
        displayName: actual.displayName,
        city: actual.city ?? '',
        phone: actual.phone ?? '',
      });
    });
  }

  protected readonly nombreError = computed(() =>
    this.errorSi(
      !elNombreEsValido(this.valores().displayName ?? ''),
      'auth.account.profile.errors.displayName',
    ),
  );

  protected readonly ciudadError = computed(() =>
    this.errorSi(!laCiudadEsValida(this.valores().city ?? ''), 'auth.account.profile.errors.city'),
  );

  protected readonly telefonoError = computed(() =>
    this.errorSi(
      !elTelefonoEsValido(this.valores().phone ?? ''),
      'auth.account.profile.errors.phone',
    ),
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.guardado.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected readonly errorDeCarga = computed(() => {
    const fallo = this.perfil.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected control(nombre: 'displayName' | 'city' | 'phone'): FormControl<string> {
    return this.form.controls[nombre];
  }

  /**
   * Nada se senala como error hasta que se intenta enviar. Marcar en rojo un
   * campo que todavia se esta escribiendo regana a quien va por la mitad.
   */
  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }

  protected enviar(): void {
    this.intentado.set(true);
    if (
      this.nombreError() !== null ||
      this.ciudadError() !== null ||
      this.telefonoError() !== null
    ) {
      return;
    }

    const valores = this.form.getRawValue();
    this.guardado.mutate(
      {
        displayName: valores.displayName.trim(),
        // Vaciar un campo es quitar el dato, no dejarlo en blanco.
        city: comoDatoOpcional(valores.city),
        phone: comoDatoOpcional(valores.phone),
      },
      {
        // Vuelve a quedar limpio para que la respuesta del servidor, ya
        // normalizada, se pinte en los campos.
        onSuccess: () => {
          this.form.markAsPristine();
          this.intentado.set(false);
        },
      },
    );
  }
}
