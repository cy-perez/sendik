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
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ListingStore } from '../application/listing.store';
import { esAccionDeModeracionConocida, type ModerationEvent } from '../../../shared/domain/listing';

/** Un paso del rastro, ya listo para pintar. */
interface PasoDelRastro {
  readonly clave: string;
  readonly etiqueta: string;
  readonly motivo: string | null;
  readonly iso: string;
  readonly cuando: string;
}

/**
 * El rastro de moderación de una publicación propia. HU-013.
 *
 * <p>Va **dentro de la publicación y no como pantalla aparte**: se llega desde
 * `/mis-publicaciones` y desde el borrador rechazado, que es donde hoy ya se ve el motivo
 * del último rechazo. Por eso es un componente y no una ruta.
 *
 * <p><strong>Se abre plegado y solo uno a la vez.</strong> `/mis-publicaciones` pinta hasta
 * veinte filas, y un rastro desplegado por fila serían veinte peticiones que nadie pidió.
 * Cuál está abierto lo guarda el store, así que abrir uno cierra el otro sin que ninguno
 * de los dos componentes sepa que el otro existe.
 *
 * <p><strong>Con texto y no solo con color</strong> (WCAG 1.4.1): un rechazo no puede
 * distinguirse de una aprobación por el tono. Es lo mismo que ya hacen la bandeja y el
 * detalle del moderador, y no hay ninguna regla de negocio detrás: es accesibilidad.
 *
 * <p>Sus cuatro estados —cargando, vacío, error y listo— quedan acotados a este bloque. Un
 * rastro que no carga no puede tumbar la publicación de la que cuelga, que es lo mismo que
 * decidió HU-012 para las cifras y HU-008 para la bandeja.
 */
@Component({
  selector: 'sendik-moderation-trail',
  imports: [TranslocoPipe],
  templateUrl: './moderation-trail.html',
  styleUrl: './moderation-trail.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ModerationTrail {
  private readonly store = inject(ListingStore);
  private readonly idioma = inject(TranslocoService);
  private readonly inyector = inject(Injector);

  /** De qué publicación es el rastro. */
  readonly publicacion = input.required<string>();

  /**
   * Qué elemento describe este rastro, si lo hay.
   *
   * <p>Existe por `/mis-publicaciones`, que pinta hasta veinte filas y por tanto hasta
   * veinte botones con el mismo nombre accesible. Quien navega por lista de botones o por
   * voz obtiene veinte entradas idénticas. Apuntando al título de la fila, cada botón se
   * distingue por lo que describe.
   *
   * <p>En `/publicar/:id` no hace falta y llega nulo: allí solo hay un rastro.
   */
  readonly etiquetadoPor = input<string | null>(null);

  protected readonly consulta = this.store.history;

  /**
   * Si este rastro es el que está abierto.
   *
   * <p>Derivado del store y no de una señal local: con una local, abrir el de otra fila
   * dejaría a esta con el botón desplegado enseñando eventos que no son suyos, porque la
   * consulta es una sola.
   */
  protected readonly abierto = computed(() => this.store.rastroAbierto() === this.publicacion());

  /**
   * El identificador de la región que el botón despliega.
   *
   * <p>Sale del identificador de la publicación y no de un contador ni de
   * `crypto.randomUUID`: tiene que ser el mismo en el servidor y en el cliente o la
   * hidratación encuentra dos documentos distintos. Es único porque en una pantalla no hay
   * dos veces la misma publicación.
   */
  protected readonly idDeLaRegion = computed(() => `rastro-${this.publicacion()}`);

  /**
   * Lo que se anuncia, con los cuatro desenlaces. WCAG 4.1.3.
   *
   * <p>Calcado de la fila de cifras de HU-012, incluida la razón de mirar `isFetching()` y
   * no `isPending()`: sin sesión la consulta nace deshabilitada y se queda pendiente para
   * siempre, y con `isPending()` se anunciaría «cargando» indefinidamente a quien no ha
   * entrado.
   *
   * <p>Callado mientras está plegado, que es cuando no hay nada que contar.
   */
  protected readonly anuncio = computed<string | null>(() => {
    if (!this.abierto()) {
      return null;
    }
    if (this.consulta.isFetching()) {
      return 'listing.trail.loading';
    }
    if (this.consulta.isError()) {
      return ListingStore.claveDeError(this.consulta.error());
    }
    if (this.consulta.isSuccess()) {
      return this.pasos().length === 0 ? 'listing.trail.empty' : 'listing.trail.ready';
    }
    return null;
  });

  /** El bloque entero, que existe en los cuatro estados. Adonde vuelve el foco. */
  private readonly zona = viewChild<ElementRef<HTMLElement>>('zonaDelRastro');

  private readonly focoHuerfano = signal(false);

  constructor() {
    // Si esta fila desaparece con su rastro abierto —al archivar, al cambiar de página—,
    // el store se quedaria creyendo que sigue a la vista y pidiendolo. Solo se limpia si
    // el abierto era el propio: cerrar el de otro al destruirse este seria peor.
    inject(DestroyRef).onDestroy(() => {
      if (untracked(this.abierto)) {
        this.store.mirarElRastro(null);
      }
    });

    // Sincroniza el foco -que es del navegador, no del marco- con el final del reintento.
    // Es el unico uso de `effect` que frontend/CLAUDE.md admite.
    effect(() => {
      if (!this.focoHuerfano() || this.consulta.isFetching()) {
        return;
      }
      untracked(() => {
        this.focoHuerfano.set(false);
        afterNextRender(() => this.zona()?.nativeElement.focus(), { injector: this.inyector });
      });
    });
  }

  protected alternar(): void {
    this.store.mirarElRastro(this.abierto() ? null : this.publicacion());
  }

  /**
   * Vuelve a pedir el rastro.
   *
   * <p><strong>Recoge el foco</strong>, que es la mitad que faltaba. Al reintentar, la
   * consulta vuelve a `pending`, esta rama se destruye y con ella el botón que se acaba de
   * pulsar: el foco cae al `body`, y en `/mis-publicaciones` eso significa volver al
   * principio del documento y reatravesar la cabecera, las cifras y hasta veinte filas.
   * Cuando la petición termina se lleva a la zona del rastro, que existe en los cuatro
   * estados. Es lo mismo que hace la fila de cifras de HU-012.
   */
  protected reintentar(): void {
    this.focoHuerfano.set(true);
    void this.consulta.refetch();
  }

  /**
   * Los pasos, ya traducidos y fechados.
   *
   * <p><strong>Una acción que esta versión no conoce se pinta igual</strong>, con su fecha
   * y una descripción genérica. Es el caso borde de la historia y es lo contrario de lo que
   * hacen las cifras del panel, que descartan un estado desconocido: allí una cifra sin
   * nombre no se puede explicar, aquí omitir la fila escondería que algo pasó, que es lo
   * único que este rastro existe para no hacer.
   */
  protected readonly pasos = computed<readonly PasoDelRastro[]>(() =>
    (this.consulta.data() ?? []).map((evento, posicion) => ({
      // La posición entra en la clave porque tiene que ser única y nada más lo garantiza:
      // dos eventos pueden compartir instante y acción, y una clave repetida en un `@for`
      // es un error en tiempo de ejecución. El coste es repintar la lista entera cuando
      // llega un evento nuevo, que en una lista de unas pocas filas no se nota.
      clave: `${posicion}#${evento.occurredAt}#${evento.action}`,
      etiqueta: esAccionDeModeracionConocida(evento.action)
        ? `listing.trail.action.${evento.action}`
        : 'listing.trail.action.unknown',
      motivo: evento.reason === null ? null : `listing.rejectionReason.${evento.reason}`,
      iso: evento.occurredAt,
      cuando: this.fechaDe(evento),
    })),
  );

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }

  /**
   * La fecha, en la zona y el formato de la configuración regional activa. Criterio 9.
   *
   * <p>Con `Intl` y la zona del navegador, no en UTC crudo: «2026-09-04T22:10:00Z» no le
   * dice a nadie en Colombia qué día pasó eso.
   *
   * <p><strong>La zona es la del navegador y no una configurada</strong>, que es lo que el
   * criterio 9 pide al pie de la letra. El frontend no tiene ninguna zona configurada -la de
   * `AppProperties` es del backend- y las cuatro pantallas que ya formatean fechas hacen
   * exactamente esto. Inventar aquí una configuración divergente sería peor que la
   * divergencia con el texto del criterio.
   *
   * <p>No hay riesgo de hidratación aunque las dos zonas difieran: este bloque solo se pinta
   * al desplegarlo, así que en el servidor no se renderiza nunca.
   */
  private fechaDe(evento: ModerationEvent): string {
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(evento.occurredAt));
  }
}
