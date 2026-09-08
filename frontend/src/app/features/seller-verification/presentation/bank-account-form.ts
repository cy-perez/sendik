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

import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';
import { InstitutionsStore } from '../application/institutions.store';
import { VerificationStore } from '../application/verification.store';
import {
  tiposDeCuentaDe,
  type BankAccountType,
  type FinancialInstitution,
} from '../domain/verification';

/**
 * El formulario de la cuenta donde el vendedor recibe. Criterio 4 de HU-002.
 *
 * <p>Los cuatro datos que pide el criterio: entidad, tipo, número y titular. Formulario
 * reactivo y campos del sistema de diseño, como el resto del proyecto: es lo que trae
 * resueltas la etiqueta visible, el `aria-describedby` y el `aria-invalid`.
 *
 * <p><strong>Los tipos de cuenta dependen de la entidad.</strong> Una billetera solo
 * recibe en depósito electrónico, así que ofrecer «ahorros» en Nequi sería ofrecer algo
 * que no existe. Es una ayuda de presentación y no una validación: el servidor no impone
 * esa regla, porque la clasificación entre banco y billetera está por confirmar (HU-002).
 * Si una entidad estuviera mal clasificada, lo peor que pasa es que falte una opción.
 *
 * <p>La coincidencia del titular con el documento (RN-012) no se comprueba aquí: la
 * comprueba el servidor, que normaliza acentos y espacios, y hacerlo también en el
 * cliente sería la misma regla con dos criterios. Lo que sí se hace es decir de dónde
 * tiene que salir ese nombre.
 */
@Component({
  selector: 'sendik-bank-account-form',
  imports: [ReactiveFormsModule, TranslocoPipe, TextField, SubmitButton],
  templateUrl: './bank-account-form.html',
  styleUrl: './bank-account-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BankAccountForm {
  private readonly store = inject(VerificationStore);

  protected readonly entidades = inject(InstitutionsStore).institutions;
  protected readonly envio = this.store.bankAccountSubmission;

  protected readonly form = new FormGroup({
    entidad: new FormControl('', { nonNullable: true }),
    tipo: new FormControl('', { nonNullable: true }),
    numero: new FormControl('', { nonNullable: true }),
    titular: new FormControl('', { nonNullable: true }),
  });

  /** Los errores se muestran al intentar enviar, no mientras alguien escribe. */
  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly entidad = computed<FinancialInstitution | null>(() => {
    const codigo = this.valores().entidad ?? '';
    return (this.entidades.data() ?? []).find((una) => una.code === codigo) ?? null;
  });

  protected readonly tiposDisponibles = computed<readonly BankAccountType[]>(() => {
    const elegida = this.entidad();
    return elegida === null ? [] : tiposDeCuentaDe(elegida);
  });

  protected readonly esBilletera = computed(() => this.entidad()?.wallet === true);

  /**
   * Solo dígitos y separadores de adorno, entre 6 y 25. Es la misma forma que valida el
   * borde del servidor, y aquí está para poder decir qué campo está mal: el dominio la
   * valida otra vez y es el que manda.
   */
  private readonly numeroValido = computed(() =>
    /^[0-9 .-]{6,25}$/.test((this.valores().numero ?? '').trim()),
  );

  private readonly titularValido = computed(
    () => (this.valores().titular ?? '').trim().length >= 2,
  );

  protected readonly errorDeEntidad = computed(() =>
    this.intentado() && this.entidad() === null
      ? 'sellerVerification.bankForm.errors.entity'
      : null,
  );

  protected readonly errorDeNumero = computed(() =>
    this.intentado() && !this.numeroValido()
      ? 'sellerVerification.bankForm.errors.accountNumber'
      : null,
  );

  protected readonly errorDeTitular = computed(() =>
    this.intentado() && !this.titularValido()
      ? 'sellerVerification.bankForm.errors.holderName'
      : null,
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.envio.error();
    return fallo === null ? null : VerificationStore.claveDeError(fallo);
  });

  /**
   * Al cambiar de entidad se limpia el tipo elegido.
   *
   * <p>Es uno de los usos legítimos de `effect`: sincroniza una señal con el estado
   * imperativo de un `FormGroup`, que es externo al marco de señales
   * (frontend/CLAUDE.md). No deriva ningún valor.
   *
   * <p>Sin esto, quien elige «corriente» en un banco y luego cambia a Nequi se queda con
   * un tipo que esa entidad no admite y que ya no aparece en la lista: el formulario se
   * vería bien y mandaría algo imposible.
   */
  constructor() {
    effect(() => {
      const disponibles = this.tiposDisponibles();
      const actual = this.valores().tipo ?? '';

      const [unico] = disponibles;

      if (disponibles.length === 1 && unico !== undefined) {
        // Una sola opción no es una elección: se pone y se muestra, no se pregunta.
        if (actual !== unico) {
          this.form.controls.tipo.setValue(unico);
        }
        return;
      }
      if (actual !== '' && !disponibles.includes(actual as BankAccountType)) {
        this.form.controls.tipo.setValue('');
      }
    });
  }

  protected control(nombre: 'numero' | 'titular'): FormControl<string> {
    return this.form.controls[nombre];
  }

  protected guardar(): void {
    this.intentado.set(true);

    const elegida = this.entidad();
    const tipo = this.valores().tipo ?? '';

    if (elegida === null || tipo === '' || !this.numeroValido() || !this.titularValido()) {
      return;
    }

    this.envio.mutate({
      entidad: elegida.code,
      tipo: tipo as BankAccountType,
      numero: (this.valores().numero ?? '').trim(),
      titular: (this.valores().titular ?? '').trim(),
    });
  }
}
