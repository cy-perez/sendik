import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  ElementRef,
  inject,
  Injector,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { MOTIVOS_DE_RECHAZO, type RejectionReason } from '../../../shared/domain/rejection-reason';
import { ReviewStore } from '../application/review.store';
import { hayDiscrepanciaDeTitular, IMAGENES } from '../domain/pending-verification';
import { VerificationImage } from './verification-image';

/** Lo que hay pendiente de confirmar. `null` cuando no se ha pulsado nada. */
type Decision = 'aprobar' | 'rechazar' | null;

/** El nombre y la descripción de cada imagen, que son textos distintos a propósito. */
const TEXTOS_DE_IMAGEN = {
  'document-front': {
    etiqueta: 'verificationReview.images.front',
    descripcion: 'verificationReview.images.alt.front',
  },
  'document-back': {
    etiqueta: 'verificationReview.images.back',
    descripcion: 'verificationReview.images.alt.back',
  },
  selfie: {
    etiqueta: 'verificationReview.images.selfie',
    descripcion: 'verificationReview.images.alt.selfie',
  },
} as const;

/**
 * El detalle de una solicitud, donde se decide. HU-006.
 *
 * <p>Los datos salen de la bandeja que ya está cargada, no de una consulta propia: el
 * servidor no ofrece un endpoint por solicitud, y pedir la bandeja entera para quedarse
 * con una fila es lo que hay. Al recargar con la dirección directa, la bandeja se carga
 * igual y la fila aparece cuando llega.
 *
 * <p><strong>Las imágenes no se piden aquí.</strong> Cada una es un acceso registrado, y
 * las pide su propio componente cuando alguien la abre.
 */
@Component({
  selector: 'sendik-review-detail-page',
  imports: [TranslocoPipe, RouterLink, VerificationImage],
  templateUrl: './review-detail-page.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewDetailPage {
  /** Lo entrega el router con withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly store = inject(ReviewStore);
  private readonly router = inject(Router);
  private readonly idioma = inject(TranslocoService);
  private readonly inyector = inject(Injector);

  protected readonly consulta = this.store.inbox;
  protected readonly aprobacion = this.store.approval;
  protected readonly rechazo = this.store.rejection;

  protected readonly motivos = MOTIVOS_DE_RECHAZO;

  /** El mismo tope que valida el backend. Un número suelto en la plantilla se separa. */
  protected readonly maximoDeNota = 500;

  protected readonly solicitud = computed(() => this.store.solicitud(this.id()));

  protected readonly imagenes = computed(() =>
    IMAGENES.map((cual) => ({ cual, ...TEXTOS_DE_IMAGEN[cual] })),
  );

  protected readonly discrepa = computed(() => {
    const actual = this.solicitud();
    return actual !== undefined && hayDiscrepanciaDeTitular(actual);
  });

  /**
   * Los enum del backend, traducidos. Llegan como `CC` y `SAVINGS`, y eso no es texto
   * que se le enseñe a nadie: ningún texto visible se escribe fuera de Transloco.
   */
  protected readonly tipoDeDocumento = computed(() => {
    const tipo = this.solicitud()?.documentType;
    return tipo === null || tipo === undefined
      ? 'verificationReview.detail.missingValue'
      : `verificationReview.detail.documentTypes.${tipo}`;
  });

  protected readonly tipoDeCuenta = computed(() => {
    const tipo = this.solicitud()?.bankAccountType;
    return tipo === null || tipo === undefined
      ? 'verificationReview.detail.missingValue'
      : `verificationReview.detail.accountTypes.${tipo}`;
  });

  /**
   * Desde cuándo espera, con la configuración regional activa.
   *
   * <p>Con el `date` de Angular saldría en inglés: el proyecto no provee `LOCALE_ID`, así
   * que ese tubo cae en `en-US` y pintaba «Aug 22, 2026» dentro de una interfaz en
   * español. `Intl` con el idioma de Transloco es lo que pide frontend/CLAUDE.md.
   */
  protected readonly espera = computed(() => {
    const cuando = this.solicitud()?.waitingSince;
    if (cuando === undefined) {
      return '';
    }
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(cuando));
  });

  /**
   * Criterio 12 y RN-060: sobre lo propio no se decide, y se dice antes de intentarlo.
   *
   * <p>El servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse
   * después de pulsar, con un correo ya prometido, es peor experiencia y no hace falta.
   */
  protected readonly esPropia = computed(() => this.solicitud()?.own === true);

  /** Criterio 10: aprobar y rechazar se confirman una vez. No se deshacen. */
  protected readonly porConfirmar = signal<Decision>(null);

  protected readonly motivoElegido = signal<RejectionReason | ''>('');
  protected readonly nota = signal('');

  /** Criterio 9: se intentó rechazar sin elegir motivo. */
  protected readonly faltaMotivo = signal(false);

  protected readonly enCurso = computed(
    () => this.aprobacion.isPending() || this.rechazo.isPending(),
  );

  /** Criterio 11: se dice qué pasó, no «error inesperado». */
  protected readonly yaResuelta = computed(
    () =>
      ReviewStore.yaResuelta(this.aprobacion.error()) ||
      ReviewStore.yaResuelta(this.rechazo.error()),
  );

  protected readonly claveDeError = computed(() => {
    const fallo = this.aprobacion.error() ?? this.rechazo.error();
    return fallo === null ? null : ReviewStore.claveDeError(fallo);
  });

  private readonly cajaDeConfirmacion = viewChild<ElementRef<HTMLElement>>('confirmacion');

  /** Dónde estaba el foco antes de pedir confirmación, para devolverlo al cancelar. */
  private disparador: HTMLElement | null = null;

  constructor() {
    // El bloque de confirmación aparece donde antes no había nada, así que sin esto el
    // foco se queda en un botón que acaba de dejar de existir y cae al cuerpo del
    // documento. Con teclado eso significa recorrer la página entera para llegar a
    // «Confirmar».
    effect(() => {
      const caja = this.cajaDeConfirmacion();
      if (caja !== undefined) {
        afterNextRender(() => caja.nativeElement.focus(), { injector: this.inyector });
      }
    });
  }

  protected pedirConfirmacion(cual: Exclude<Decision, null>): void {
    this.disparador = document.activeElement as HTMLElement | null;
    this.porConfirmar.set(cual);
  }

  /**
   * El envío nativo del formulario, que es rechazar.
   *
   * <p>Evento `submit` y no `ngSubmit`: este formulario no tiene modelo, así que no entra
   * `FormsModule`, y sin él `ngSubmit` no existe —quedaría como un evento del DOM con ese
   * nombre, que no dispara nunca—. Se descubrió porque la prueba del criterio 9 no veía
   * el mensaje.
   */
  protected alEnviar(evento: Event): void {
    evento.preventDefault();
    this.pedirRechazo();
  }

  /** Rechazar exige motivo. Si falta, se señala el campo en vez de apagar el botón. */
  protected pedirRechazo(): void {
    if (this.motivoElegido() === '') {
      this.faltaMotivo.set(true);
      document.getElementById('motivo')?.focus();
      return;
    }

    this.faltaMotivo.set(false);
    this.pedirConfirmacion('rechazar');
  }

  protected cancelar(): void {
    this.porConfirmar.set(null);
    this.disparador?.focus();
  }

  protected elegirMotivo(valor: string): void {
    this.motivoElegido.set(valor as RejectionReason | '');
    if (valor !== '') {
      this.faltaMotivo.set(false);
    }
  }

  protected async confirmar(): Promise<void> {
    const decision = this.porConfirmar();

    if (decision === null) {
      return;
    }

    try {
      if (decision === 'aprobar') {
        await this.aprobacion.mutateAsync(this.id());
      } else {
        await this.rechazo.mutateAsync({
          id: this.id(),
          motivo: this.motivoElegido() as RejectionReason,
          nota: this.nota().trim() === '' ? null : this.nota().trim(),
        });
      }
    } catch {
      // El fallo ya esta en la senal de la mutacion y la pantalla lo dice. Se recoge para
      // no dejar una promesa rechazada suelta y, sobre todo, para NO navegar: si la
      // solicitud ya la resolvio otra persona, quien revisa tiene que leerlo aqui.
      this.porConfirmar.set(null);
      return;
    }

    this.porConfirmar.set(null);

    // Criterio 8: vuelve a la lista **con la confirmación de lo que hizo**. El resultado
    // viaja en el estado de la navegación y lo anuncia la bandeja: sin eso, quien usa un
    // lector de pantalla aprueba y no recibe ninguna señal de que pasó algo.
    await this.router.navigate(['/moderacion/verificaciones'], {
      state: { decision: decision === 'aprobar' ? 'approved' : 'rejected' },
    });
  }
}
