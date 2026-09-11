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
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiError } from '../../../core/http/api-error';

import { AddressesStore } from '../application/addresses.store';
import {
  comoDatoOpcional,
  comoViajaElCodigoPostal,
  comoViajaElTelefono,
  cuantasHay,
  departamentoDe,
  elCodigoPostalEsValido,
  elComplementoEsValido,
  elMunicipioEsValido,
  elNombreDeQuienRecibeEsValido,
  elTelefonoEsValido,
  laLineaEsValida,
  lasIndicacionesSonValidas,
  type AddressDraft,
  type ShippingAddress,
} from '../domain/shipping-address';
import { PrivacyNotice } from '../../../shared/ui/form/privacy-notice';
import { SelectField } from '../../../shared/ui/form/select-field';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * El formulario de una dirección de entrega. HU-016, criterios 2 a 9.
 *
 * <p>El mismo para crear y para editar: los campos son los mismos y lo único que cambia es
 * si llega una dirección dentro. Dos formularios serían dos sitios donde arreglar la misma
 * validación.
 *
 * <p><strong>Los dos selectores son dependientes y el de municipio no adivina.</strong>
 * Mientras no haya departamento elegido está deshabilitado y dice por qué (criterio 4); al
 * cambiar de departamento el municipio se vacía, porque el que estaba elegido pertenece a
 * otro sitio.
 *
 * <p><strong>Al editar, el departamento llega ya elegido</strong> sin que la API lo mande en
 * el cuerpo: se deriva del código del municipio (RN-100). Es la misma propiedad que hace
 * imposible un par incoherente, usada aquí para lo contrario.
 *
 * <p><strong>Un fallo conserva lo escrito.</strong> Perder una dirección recién tecleada por
 * una sesión vencida es el peor momento para volver a pedirla.
 */
@Component({
  selector: 'sendik-address-form',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    TextField,
    SelectField,
    SubmitButton,
    PrivacyNotice,
  ],
  templateUrl: './address-form.html',
  styleUrl: './address-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressForm {
  /** La dirección que se edita, o nula para una nueva. */
  readonly direccion = input<ShippingAddress | null>(null);

  readonly guardada = output<void>();
  readonly cancelada = output<void>();

  private readonly store = inject(AddressesStore);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  /**
   * El encabezado del formulario, que recoge el foco al abrirlo.
   *
   * <p>Abrir el formulario destruye el botón que lo abrió —está en la rama `@else` de la
   * plantilla— así que sin esto el foco cae a `<body>` y quien navega con teclado vuelve al
   * principio del documento. Es el patrón de `cart-page`.
   */
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

  protected readonly form = new FormGroup({
    departmentCode: new FormControl('', { nonNullable: true }),
    municipalityCode: new FormControl('', { nonNullable: true }),
    recipientName: new FormControl('', { nonNullable: true }),
    phone: new FormControl('', { nonNullable: true }),
    line: new FormControl('', { nonNullable: true }),
    complement: new FormControl('', { nonNullable: true }),
    instructions: new FormControl('', { nonNullable: true }),
    postalCode: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);
  protected readonly fallo = signal<string | null>(null);

  /**
   * Cuántas direcciones había cuando el servidor rechazó por tope. Criterio 8.
   *
   * <p><strong>Es el tope, y no hace falta escribirlo aquí para decirlo.</strong> El
   * servidor solo rechaza cuando ya no cabe ninguna más, así que en ese instante cuántas
   * hay es exactamente el máximo. Es lo que permite que el mensaje lo nombre sin que el
   * número viva en dos sitios —la deuda que HU-015 dejó anotada con el veinte del carrito—.
   */
  protected readonly tope = signal(0);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly departamentos = computed(() => this.store.departamentos.data() ?? []);
  protected readonly municipios = computed(() => this.store.municipios.data() ?? []);

  protected readonly editando = computed(() => this.direccion() !== null);
  protected readonly guardando = computed(() => this.store.guardando());

  /**
   * Si el selector de municipio se puede usar.
   *
   * <p>Deshabilitado hasta que haya departamento, y también mientras sus municipios están en
   * camino: ofrecer una lista vacía que se llena sola es peor que decir que está cargando.
   */
  protected readonly municipioDisponible = computed(
    () =>
      (this.valores().departmentCode ?? '') !== '' &&
      !this.store.municipios.isPending() &&
      !this.store.municipios.isError(),
  );

  /**
   * Si la división no se pudo cargar, el formulario no sirve y hay que decirlo.
   *
   * <p>Las dos consultas van con `retry: false`, así que un fallo deja `isPending()` en falso
   * y `data()` sin definir: el selector quedaba habilitado, vacío y sin mensaje. Lo cazó la
   * revisión de accesibilidad.
   */
  protected readonly divisionFallo = computed(
    () => this.store.departamentos.isError() || this.store.municipios.isError(),
  );

  /** Lo que se anuncia al cambiar de departamento. Criterio 27. */
  protected readonly anuncioDeMunicipios = computed(() => {
    if ((this.valores().departmentCode ?? '') === '') {
      return null;
    }
    if (this.store.municipios.isPending()) {
      return 'addresses.form.municipality.loading';
    }
    return this.store.municipios.isError()
      ? 'addresses.form.municipality.failed'
      : 'addresses.form.municipality.updated';
  });

  protected readonly municipioPlaceholder = computed(() => {
    if ((this.valores().departmentCode ?? '') === '') {
      return 'addresses.form.municipality.chooseDepartmentFirst';
    }
    if (this.store.municipios.isError()) {
      return 'addresses.form.municipality.failed';
    }
    return this.store.municipios.isPending()
      ? 'addresses.form.municipality.loading'
      : 'addresses.form.municipality.choose';
  });

  constructor() {
    // Los departamentos los pide el formulario y no la pantalla: quien solo mira su libreta
    // no necesita treinta y tres opciones que no va a usar.
    this.store.abrirFormulario(true);
    inject(DestroyRef).onDestroy(() => this.store.abrirFormulario(false));

    // **El foco se recoloca en cada apertura y en cada cambio de direccion editada**, no
    // solo al construirse. La lista de tarjetas sigue en pantalla con el formulario abierto,
    // asi que pulsar «Editar» en otra tarjeta no destruye este componente: cambia su
    // contenido entero -incluido este titulo- sin que el constructor vuelva a correr.
    effect(() => {
      this.direccion();
      afterNextRender(() => this.titulo()?.nativeElement.focus(), { injector: this.injector });
    });

    // El municipio se deshabilita deshabilitando **el control**. Un `[disabled]` en la
    // plantilla sobre un `<select>` con `[formControl]` es un no-op: la directiva declara ese
    // input y su setter solo advierte.
    effect(() => {
      const control = this.form.controls.municipalityCode;
      if (this.municipioDisponible()) {
        control.enable({ emitEvent: false });
      } else {
        control.disable({ emitEvent: false });
      }
    });

    // Rellena el formulario cuando llega la direccion que se va a editar, y solo mientras
    // nadie lo haya tocado: una respuesta que llegue tarde no puede borrar lo que la
    // persona esta escribiendo. Es uno de los usos legitimos de `effect` -sincronizar una
    // senal con el estado imperativo de un FormGroup- y no deriva ningun valor.
    effect(() => {
      const actual = this.direccion();
      if (actual === null || this.form.dirty) {
        return;
      }

      this.form.setValue({
        departmentCode: actual.departmentCode,
        municipalityCode: actual.municipalityCode,
        recipientName: actual.recipientName,
        phone: actual.phone,
        line: actual.line,
        complement: actual.complement ?? '',
        instructions: actual.instructions ?? '',
        postalCode: actual.postalCode ?? '',
      });

      this.store.elegirDepartamento(actual.departmentCode);
    });

    // Cambiar de departamento vacia el municipio: el que estaba elegido es de otro sitio.
    // Va aqui y no en el manejador del evento porque el valor tambien cambia al rellenar
    // el formulario, y ahi no hay que vaciar nada -de eso se encarga la guarda de arriba,
    // que compara con lo que ya hay-.
    effect(() => {
      const elegido = this.valores().departmentCode ?? '';
      this.store.elegirDepartamento(elegido === '' ? null : elegido);

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

  protected readonly recipientNameError = computed(() =>
    this.errorSi(
      !elNombreDeQuienRecibeEsValido(this.valores().recipientName ?? ''),
      'addresses.form.recipientName.error',
    ),
  );

  protected readonly phoneError = computed(() =>
    this.errorSi(!elTelefonoEsValido(this.valores().phone ?? ''), 'addresses.form.phone.error'),
  );

  protected readonly lineError = computed(() =>
    this.errorSi(!laLineaEsValida(this.valores().line ?? ''), 'addresses.form.line.error'),
  );

  protected readonly complementError = computed(() =>
    this.errorSi(
      !elComplementoEsValido(this.valores().complement ?? ''),
      'addresses.form.complement.error',
    ),
  );

  protected readonly instructionsError = computed(() =>
    this.errorSi(
      !lasIndicacionesSonValidas(this.valores().instructions ?? ''),
      'addresses.form.instructions.error',
    ),
  );

  protected readonly postalCodeError = computed(() =>
    this.errorSi(
      !elCodigoPostalEsValido(this.valores().postalCode ?? ''),
      'addresses.form.postalCode.error',
    ),
  );

  private readonly hayErrores = computed(
    () =>
      this.departmentError() !== null ||
      this.municipalityError() !== null ||
      this.recipientNameError() !== null ||
      this.phoneError() !== null ||
      this.lineError() !== null ||
      this.complementError() !== null ||
      this.instructionsError() !== null ||
      this.postalCodeError() !== null,
  );

  protected async guardar(): Promise<void> {
    this.intentado.set(true);
    this.fallo.set(null);

    if (this.hayErrores()) {
      this.enfocarElPrimerError();
      return;
    }
    if (this.guardando()) {
      return;
    }

    const datos = this.comoBorrador();
    const actual = this.direccion();

    try {
      if (actual === null) {
        await this.store.agregado.mutateAsync(datos);
      } else {
        await this.store.edicion.mutateAsync({ id: actual.id, datos });
      }
      this.guardada.emit();
    } catch (error) {
      // Lo escrito se queda. El mensaje sale del codigo del error, que es lo unico que el
      // cuerpo de un ProblemDetail trae ademas del traceId.
      if (error instanceof ApiError && error.code === 'USER_ADDRESS_BOOK_FULL') {
        this.tope.set(cuantasHay(this.store.direcciones()));
        this.fallo.set('addresses.errors.full');
      } else {
        this.fallo.set(claveDelError(error));
      }
    }
  }

  protected cancelar(): void {
    this.cancelada.emit();
  }

  private comoBorrador(): AddressDraft {
    const valores = this.form.getRawValue();

    return {
      recipientName: valores.recipientName.trim(),
      // Los dos viajan normalizados: el borde valida la forma antes que el dominio, y la
      // gente escribe «300 123 4567» y «110 111».
      phone: comoViajaElTelefono(valores.phone),
      municipalityCode: valores.municipalityCode,
      line: valores.line.trim(),
      complement: comoDatoOpcional(valores.complement),
      instructions: comoDatoOpcional(valores.instructions),
      postalCode: comoViajaElCodigoPostal(valores.postalCode),
    };
  }

  /**
   * Lleva el foco al primer campo con error, en el orden en que se leen.
   *
   * <p>El primero de arriba abajo y no el primero que falle en cualquier orden: es por donde
   * la persona va a seguir bajando.
   */
  private enfocarElPrimerError(): void {
    // En el orden del DOM, que es lo que el comentario de arriba promete y lo que este
    // arreglo NO hacia: estaba en el orden en que se declararon los campos, asi que con
    // «Direccion» y «Quien recibe» vacios a la vez el foco saltaba al quinto campo y dejaba
    // dos errores por encima sin visitar. Lo cazo la revision de accesibilidad.
    const primero = [
      ['direccion-departamento', this.departmentError()],
      ['direccion-municipio', this.municipalityError()],
      ['direccion-linea', this.lineError()],
      ['direccion-complemento', this.complementError()],
      ['direccion-quien-recibe', this.recipientNameError()],
      ['direccion-telefono', this.phoneError()],
      ['direccion-indicaciones', this.instructionsError()],
      ['direccion-codigo-postal', this.postalCodeError()],
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

/**
 * El código del error, traducido a clave de Transloco.
 *
 * <p><strong>No inventa un mecanismo nuevo.</strong> `ApiError` ya sabe convertirse en su
 * clave —`errors.byCode.<CODIGO>`, o `errors.network` cuando la petición no salió— y los dos
 * códigos de esta historia viven allí con todos los demás. Escribir aquí un `switch` propio
 * habría duplicado esos textos en un segundo sitio.
 */
function claveDelError(error: unknown): string {
  return error instanceof ApiError ? error.translationKey : 'errors.fallback';
}
