import { NgOptimizedImage } from '@angular/common';
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

import {
  categoriaPorId,
  MOTIVOS_DE_RECHAZO_DE_PUBLICACION,
  posicionesAPintar,
  precioFormateado,
  tomaEn,
  type Listing,
  type ListingRejectionReason,
} from '../../../shared/domain/listing';
import { ListingReviewStore } from '../application/listing-review.store';

/** Lo que hay pendiente de confirmar. `null` cuando no se ha pulsado nada. */
type Decision = 'aprobar' | 'rechazar' | null;

/**
 * El detalle de una publicación, donde se decide. HU-008.
 *
 * <p>Pide la publicación completa a `GET /listings/{id}`, que a un moderador le responde
 * la forma con las ocho tomas, las medidas y las marcas de atención. Es la diferencia con
 * HU-006, donde el detalle salía de la bandeja ya cargada porque no había endpoint por
 * solicitud: aquí recargar la dirección directa funciona sin pedir la cola.
 *
 * <p><strong>Las tomas se cargan con la pantalla</strong>, al revés que en HU-006. Allí
 * cada imagen era un acceso a un dato personal que dejaba fila en la bitácora; aquí son
 * fotos de una prenda en el almacén público y mirarlas es justo el trabajo.
 */
@Component({
  selector: 'sendik-review-listing-page',
  imports: [TranslocoPipe, RouterLink, NgOptimizedImage],
  templateUrl: './review-listing-page.html',
  styleUrl: './review-listing-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ReviewListingPage {
  /** Lo entrega el router con withComponentInputBinding. */
  readonly id = input.required<string>();

  private readonly store = inject(ListingReviewStore);
  private readonly router = inject(Router);
  private readonly idioma = inject(TranslocoService);
  private readonly inyector = inject(Injector);

  protected readonly consulta = this.store.listing;
  protected readonly aprobacion = this.store.approval;
  protected readonly rechazo = this.store.rejection;

  protected readonly motivos = MOTIVOS_DE_RECHAZO_DE_PUBLICACION;

  /** El mismo tope que valida el backend. Un número suelto en la plantilla se separa. */
  protected readonly maximoDeNota = 500;

  protected readonly publicacion = computed<Listing | undefined>(() => this.consulta.data());

  /** Las posiciones que toca pintar: ocho, o cuatro si es tecnología sellada (RN-065). */
  protected readonly posiciones = computed<readonly number[]>(() => {
    const actual = this.publicacion();
    return actual === undefined ? [] : posicionesAPintar(actual);
  });

  protected readonly precio = computed(() => {
    const valor = this.publicacion()?.product.price;
    return valor === undefined || valor === null
      ? ''
      : precioFormateado(valor, this.idioma.getActiveLang());
  });

  /**
   * El nombre de la categoría, que el criterio 7 pide.
   *
   * <p>El producto solo trae el identificador, así que se resuelve contra el árbol. Si
   * todavía no llegó se muestra el identificador: es feo y es cierto, y taparlo con un
   * guion escondería que la categoría no se pudo resolver.
   */
  protected readonly categoria = computed(() => {
    const id = this.publicacion()?.product.categoryId;
    if (id === undefined) {
      return '';
    }
    const categoria = categoriaPorId(this.store.arbol(), id);
    if (categoria === null) {
      return id;
    }
    // Por idioma activo. `publish-page.html` usa `nameEs` fijo, que es un hueco de
    // internacionalizacion de HU-007: aqui no se repite.
    return this.idioma.getActiveLang() === 'en' ? categoria.nameEn : categoria.nameEs;
  });

  /** Criterio 6: aquí sí se dice por qué, al contrario que en la lista del vendedor. */
  protected readonly marcasDeAtencion = computed<readonly string[]>(
    () => this.publicacion()?.attentionReasons ?? [],
  );

  /**
   * Las medidas como pares, ya con su clave de traducción.
   *
   * <p>Se pintan en el orden en que llegan: el backend las manda en el orden del grupo de
   * medida, que es el mismo en que el formulario las pidió.
   */
  protected readonly medidas = computed(() => {
    const valores = this.publicacion()?.product.measurements ?? {};
    return Object.entries(valores).map(([clave, valor]) => ({
      clave: `listing.measurement.${clave}`,
      valor,
    }));
  });

  /**
   * Desde cuándo espera, con la configuración regional activa.
   *
   * <p>Sale de la fila de la cola y no de la publicación: `submitted_at` no viaja en la
   * respuesta completa, que es la que ve también el vendedor y a la que ese dato no le
   * hace falta. Si la cola no está cargada —se entró por la dirección directa— no se
   * muestra, y no pasa nada: es un dato de ordenación, no de decisión.
   */
  protected readonly espera = computed(() => {
    const cuando = this.store.fila(this.id())?.waitingSince;
    if (cuando === undefined) {
      return null;
    }
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(cuando));
  });

  /**
   * Criterio 12 y RN-063: sobre lo propio no se decide, y se dice antes de intentarlo.
   *
   * <p>Sale de `own`, que lo calcula el servidor con `laPublico` y viaja **en la propia
   * publicación**, no en la fila de la cola. Con la fila, entrar por la dirección directa
   * —o que la publicación esté más allá de la primera página— dejaba `esPropia` en falso y
   * la pantalla ofrecía decidir sobre lo propio. Lo encontraron tres revisiones distintas.
   *
   * <p>El servidor lo rechaza igual —esconder el botón no es la regla— pero enterarse
   * después de pulsar, con un correo ya prometido, no hace falta.
   */
  protected readonly esPropia = computed(() => this.publicacion()?.own === true);

  /** Criterio 10: aprobar y rechazar se confirman una vez. No se deshacen. */
  protected readonly porConfirmar = signal<Decision>(null);

  protected readonly motivoElegido = signal<ListingRejectionReason | ''>('');
  protected readonly nota = signal('');

  /** Criterio 9: se intentó rechazar sin elegir motivo. */
  protected readonly faltaMotivo = signal(false);

  protected readonly enCurso = computed(
    () => this.aprobacion.isPending() || this.rechazo.isPending(),
  );

  /** Criterios 11 y 13: se dice qué pasó, no «error inesperado». */
  protected readonly yaNoEstaPendiente = computed(
    () =>
      ListingReviewStore.yaNoEstaPendiente(this.aprobacion.error()) ||
      ListingReviewStore.yaNoEstaPendiente(this.rechazo.error()),
  );

  protected readonly claveDeError = computed(() => {
    const fallo = this.aprobacion.error() ?? this.rechazo.error();
    return fallo === null ? null : ListingReviewStore.claveDeError(fallo);
  });

  private readonly cajaDeConfirmacion = viewChild<ElementRef<HTMLElement>>('confirmacion');
  private readonly selectorDeMotivo = viewChild<ElementRef<HTMLSelectElement>>('motivo');
  private readonly botonAprobar = viewChild<ElementRef<HTMLButtonElement>>('aprobar');
  private readonly botonRechazar = viewChild<ElementRef<HTMLButtonElement>>('rechazar');

  /**
   * Qué botón abrió la confirmación, para devolverle el foco al cancelar.
   *
   * <p>Se guarda cuál fue, y no el elemento que tenía el foco: preguntarle al documento
   * global quién estaba enfocado obliga a tocar una API que en el renderizado en servidor
   * no existe, y aquí las dos únicas puertas de entrada son estos dos botones.
   */
  private disparador: Exclude<Decision, null> | null = null;

  constructor() {
    // Le dice al almacén qué publicación mirar. En un efecto y no en el constructor
    // porque `id()` es una entrada del router y todavía no tiene valor al construir.
    effect(() => this.store.abrir(this.id()));

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

  /** La toma de esa posición, o `null` si el archivo no está (caso borde de la historia). */
  protected tomaDe(posicion: number): string | null {
    const actual = this.publicacion();
    if (actual === undefined) {
      return null;
    }
    return tomaEn(actual, posicion)?.url ?? null;
  }

  protected reintentar(): void {
    void this.consulta.refetch();
  }

  protected pedirConfirmacion(cual: Exclude<Decision, null>): void {
    this.disparador = cual;
    this.porConfirmar.set(cual);
  }

  /**
   * El envío nativo del formulario no decide nada.
   *
   * <p>Antes disparaba rechazar, y eso hacía que **con teclado la acción por omisión fuera
   * la destructiva** —Enter sobre el desplegable de motivo— mientras con ratón la acción
   * destacada es aprobar. Dos comportamientos distintos para el mismo formulario, sobre un
   * acto que manda un correo al vendedor.
   *
   * <p>Ahora los dos botones son `type="button"` con su `(click)`, y esto se queda solo
   * como red de seguridad para que Enter no recargue la página.
   *
   * <p>Evento `submit` y no `ngSubmit`: este formulario no tiene modelo, así que no entra
   * `FormsModule`, y sin él `ngSubmit` no existe. Lo aprendió HU-006.
   */
  protected alEnviar(evento: Event): void {
    evento.preventDefault();
  }

  /** Rechazar exige motivo. Si falta, se señala el campo en vez de apagar el botón. */
  protected pedirRechazo(): void {
    if (this.motivoElegido() === '') {
      this.faltaMotivo.set(true);
      // Tras pintar: `focus()` es sincrono y `aria-invalid` con su descripcion se ponen
      // en el ciclo siguiente. Enfocar antes deja el campo sin marcar como invalido en el
      // arbol de accesibilidad justo cuando el lector lo anuncia.
      afterNextRender(() => this.selectorDeMotivo()?.nativeElement.focus(), {
        injector: this.inyector,
      });
      return;
    }

    this.faltaMotivo.set(false);
    this.pedirConfirmacion('rechazar');
  }

  protected cancelar(): void {
    this.cerrarConfirmacion();
  }

  /**
   * Cierra la confirmación y devuelve el foco al botón que la abrió.
   *
   * <p>Lo usan cancelar y el camino de error, que es el que se olvidaba: los dos destruyen
   * el bloque donde estaba el foco.
   */
  private cerrarConfirmacion(): void {
    const volverA = this.disparador;
    this.porConfirmar.set(null);
    this.disparador = null;

    // Tras pintar: el botón al que se vuelve acaba de reaparecer, y enfocarlo antes de
    // que exista no hace nada y deja el foco en el cuerpo del documento.
    afterNextRender(
      () => {
        const boton = volverA === 'aprobar' ? this.botonAprobar() : this.botonRechazar();
        boton?.nativeElement.focus();
      },
      { injector: this.inyector },
    );
  }

  protected elegirMotivo(valor: string): void {
    this.motivoElegido.set(valor as ListingRejectionReason | '');
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
          motivo: this.motivoElegido() as ListingRejectionReason,
          nota: this.nota().trim() === '' ? null : this.nota().trim(),
        });
      }
    } catch {
      // El fallo ya esta en la senal de la mutacion y la pantalla lo dice. Se recoge para
      // no dejar una promesa rechazada suelta y, sobre todo, para NO navegar: si la
      // publicacion ya no esta pendiente, quien revisa tiene que leerlo aqui.
      //
      // Y se devuelve el foco, que si no cae al cuerpo del documento: cerrar la
      // confirmacion destruye el boton que acaba de pulsarse. Con teclado eso significa
      // volver al principio de la pagina justo cuando hay un mensaje que leer.
      this.cerrarConfirmacion();
      return;
    }

    this.porConfirmar.set(null);

    // Criterio 8: vuelve a la lista **con la confirmación de lo que hizo**. El resultado
    // viaja en el estado de la navegación y lo anuncia la bandeja: sin eso, quien usa un
    // lector de pantalla aprueba y no recibe ninguna señal de que pasó algo.
    await this.router.navigate(['/moderacion/publicaciones'], {
      state: { decision: decision === 'aprobar' ? 'approved' : 'rejected' },
    });
  }
}
