import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiError } from '../../../core/http/api-error';
import { SelectField } from '../../../shared/ui/form/select-field';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';
import { AddressesStore } from '../application/addresses.store';
import { OriginAddressStore } from '../application/origin-address.store';
import {
  elRemitenteEstaCompleto,
  type OriginAddress,
  type OriginDraft,
  type Sender,
} from '../domain/origin-address';
import {
  comoDatoOpcional,
  comoViajaElCodigoPostal,
  departamentoDe,
  elCodigoPostalEsValido,
  elComplementoEsValido,
  elMunicipioEsValido,
  laLineaEsValida,
  lasIndicacionesSonValidas,
} from '../domain/shipping-address';

/**
 * El formulario de la dirección de origen. HU-017, criterios 2 a 8.
 *
 * <p>Es el de la libreta sin quien recibe ni teléfono: esos dos son del perfil y aquí se
 * muestran de solo lectura, con el enlace para cambiarlos (criterio 4). Los dos selectores
 * de la división los presta `AddressesStore`, que es quien los tiene; el resto de reglas de
 * campo son las mismas funciones del dominio de la libreta.
 *
 * <p><strong>Sin teléfono en el perfil no se guarda</strong> (criterio 5), y la pantalla lo
 * dice antes de que nadie pulse: el bloque del remitente lo anuncia y enlaza al perfil. El
 * botón no se deshabilita —un botón deshabilitado no dice por qué lo está, y es la regla de
 * `account-page`—; al enviar, el aviso recibe el foco y no se llama al servidor.
 *
 * <p><strong>Un fallo conserva lo escrito.</strong>
 */
@Component({
  selector: 'sendik-origin-address-form',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe, TextField, SelectField, SubmitButton],
  templateUrl: './origin-address-form.html',
  styleUrl: './origin-address-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OriginAddressForm {
  /** El origen que se edita, o nulo para el primero. */
  readonly origen = input<OriginAddress | null>(null);

  /** El remitente tal como está en el perfil ahora. Nulo mientras no ha llegado. */
  readonly remitente = input<Sender | null>(null);

  readonly guardada = output<void>();
  readonly cancelada = output<void>();

  private readonly division = inject(AddressesStore);
  private readonly store = inject(OriginAddressStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');
  private readonly avisoDelTelefono = viewChild<ElementRef<HTMLElement>>('avisoDelTelefono');

  protected readonly form = new FormGroup({
    departmentCode: new FormControl('', { nonNullable: true }),
    municipalityCode: new FormControl('', { nonNullable: true }),
    line: new FormControl('', { nonNullable: true }),
    complement: new FormControl('', { nonNullable: true }),
    instructions: new FormControl('', { nonNullable: true }),
    postalCode: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);
  protected readonly fallo = signal<string | null>(null);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly departamentos = computed(() => this.division.departamentos.data() ?? []);
  protected readonly municipios = computed(() => this.division.municipios.data() ?? []);

  protected readonly editando = computed(() => this.origen() !== null);
  protected readonly guardando = computed(() => this.store.guardando());

  /** Criterio 5: si falta el teléfono se dice antes de que nadie escriba. */
  protected readonly faltaElTelefono = computed(() => {
    const quien = this.remitente();
    return quien !== null && !elRemitenteEstaCompleto(quien);
  });

  protected readonly municipioDisponible = computed(
    () =>
      (this.valores().departmentCode ?? '') !== '' &&
      !this.division.municipios.isPending() &&
      !this.division.municipios.isError(),
  );

  protected readonly divisionFallo = computed(
    () => this.division.departamentos.isError() || this.division.municipios.isError(),
  );

  protected readonly anuncioDeMunicipios = computed(() => {
    if ((this.valores().departmentCode ?? '') === '') {
      return null;
    }
    if (this.division.municipios.isPending()) {
      return 'addresses.form.municipality.loading';
    }
    return this.division.municipios.isError()
      ? 'addresses.form.municipality.failed'
      : 'addresses.form.municipality.updated';
  });

  protected readonly municipioPlaceholder = computed(() => {
    if ((this.valores().departmentCode ?? '') === '') {
      return 'addresses.form.municipality.chooseDepartmentFirst';
    }
    if (this.division.municipios.isError()) {
      return 'addresses.form.municipality.failed';
    }
    return this.division.municipios.isPending()
      ? 'addresses.form.municipality.loading'
      : 'addresses.form.municipality.choose';
  });

  constructor() {
    // Los departamentos los pide el formulario, a traves del store de la libreta.
    this.division.abrirFormulario(true);
    inject(DestroyRef).onDestroy(() => this.division.abrirFormulario(false));

    effect(() => {
      this.origen();
      afterNextRender(() => this.titulo()?.nativeElement.focus(), { injector: this.injector });
    });

    // El municipio se deshabilita deshabilitando **el control**, no con `[disabled]`.
    effect(() => {
      const control = this.form.controls.municipalityCode;
      if (this.municipioDisponible()) {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    });

    // Rellena con el origen que se edita, y solo mientras nadie lo haya tocado.
    effect(() => {
      const actual = this.origen();
      if (actual === null || this.form.dirty) {
        return;
      }

      this.form.setValue({
        departmentCode: actual.departmentCode,
        municipalityCode: actual.municipalityCode,
        line: actual.line,
        complement: actual.complement ?? '',
        instructions: actual.instructions ?? '',
        postalCode: actual.postalCode ?? '',
      });

      this.division.elegirDepartamento(actual.departmentCode);
    });

    // Cambiar de departamento vacia el municipio: el que estaba elegido es de otro sitio.
    effect(() => {
      const elegido = this.valores().departmentCode ?? '';
      this.division.elegirDepartamento(elegido === '' ? null : elegido);

      const municipio = this.form.controls.municipalityCode.value;
      if (municipio !== '' && departamentoDe(municipio) !== elegido) {
        this.form.controls.municipalityCode.setValue('');
      }
    });
  }

  protected readonly departmentError = computed(() =>
    this.errorSi((this.valores().departmentCode ?? '') === '', 'addresses.form.department.error'),
  );

  protected readonly municipalityError = computed(() =>
    this.errorSi(
      !elMunicipioEsValido(this.valores().municipalityCode ?? ''),
      'addresses.form.municipality.error',
    ),
  );

  protected readonly lineError = computed(() =>
    this.errorSi(!laLineaEsValida(this.valores().line ?? ''), 'originAddress.form.line.error'),
  );

  protected readonly complementError = computed(() =>
    this.errorSi(
      !elComplementoEsValido(this.valores().complement ?? ''),
      'originAddress.form.complement.error',
    ),
  );

  protected readonly instructionsError = computed(() =>
    this.errorSi(
      !lasIndicacionesSonValidas(this.valores().instructions ?? ''),
      'originAddress.form.instructions.error',
    ),
  );

  protected readonly postalCodeError = computed(() =>
    this.errorSi(
      !elCodigoPostalEsValido(this.valores().postalCode ?? ''),
      'originAddress.form.postalCode.error',
    ),
  );

  private readonly hayErrores = computed(
    () =>
      this.departmentError() !== null ||
      this.municipalityError() !== null ||
      this.lineError() !== null ||
      this.complementError() !== null ||
      this.instructionsError() !== null ||
      this.postalCodeError() !== null,
  );

  protected async guardar(): Promise<void> {
    this.intentado.set(true);
    this.fallo.set(null);

    // Criterio 5, del lado del cliente: sin telefono no se manda nada, y el foco va al
    // aviso que ya lo decia. El servidor lo rechaza igual con USER_PHONE_REQUIRED.
    if (this.faltaElTelefono()) {
      afterNextRender(() => this.avisoDelTelefono()?.nativeElement.focus(), {
        injector: this.injector,
      });
      return;
    }
    if (this.hayErrores()) {
      this.enfocarElPrimerError();
      return;
    }
    if (this.guardando()) {
      return;
    }

    try {
      await this.store.guardado.mutateAsync(this.comoBorrador());
      this.guardada.emit();
    } catch (error) {
      // Lo escrito se queda. El mensaje sale del codigo del error.
      this.fallo.set(claveDelError(error));
    }
  }

  protected cancelar(): void {
    this.cancelada.emit();
  }

  private comoBorrador(): OriginDraft {
    const valores = this.form.getRawValue();

    return {
      municipalityCode: valores.municipalityCode,
      line: valores.line.trim(),
      complement: comoDatoOpcional(valores.complement),
      instructions: comoDatoOpcional(valores.instructions),
      // Viaja sin separadores: el borde valida la forma antes que el dominio.
      postalCode: comoViajaElCodigoPostal(valores.postalCode),
    };
  }

  /** Lleva el foco al primer campo con error, en el orden del DOM. */
  private enfocarElPrimerError(): void {
    const primero = [
      ['origen-departamento', this.departmentError()],
      ['origen-municipio', this.municipalityError()],
      ['origen-linea', this.lineError()],
      ['origen-complemento', this.complementError()],
      ['origen-indicaciones', this.instructionsError()],
      ['origen-codigo-postal', this.postalCodeError()],
    ].find(([, error]) => error !== null);

    if (primero === undefined) {
      return;
    }

    afterNextRender(
      () => {
        this.host.nativeElement.querySelector<HTMLElement>(`#${primero[0]}`)?.focus();
      },
      { injector: this.injector },
    );
  }

  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }
}

function claveDelError(error: unknown): string {
  return error instanceof ApiError ? error.translationKey : 'errors.fallback';
}
