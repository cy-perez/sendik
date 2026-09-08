import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { precioFormateado, type Money } from '../../../shared/domain/listing';
import { ListingReviewStore } from '../application/listing-review.store';

/**
 * La bandeja de moderación de publicaciones: lo que espera revisión, lo más viejo
 * primero. HU-008.
 *
 * <p>Pantalla interna, no pública. No lleva el acento bronce, que está reservado a lo
 * verificado y a la acción principal de las pantallas de cara al comprador.
 *
 * <p>No decide nada: desde aquí se entra al detalle, y es allí donde se aprueba o se
 * rechaza. Poner los botones en la lista invitaría a decidir sin haber mirado las ocho
 * tomas, que es exactamente lo que esta pantalla existe para que no pase.
 */
@Component({
  selector: 'sendik-queue-page',
  imports: [TranslocoPipe, RouterLink, NgOptimizedImage],
  templateUrl: './queue-page.html',
  styleUrl: './queue-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class QueuePage {
  private readonly store = inject(ListingReviewStore);
  private readonly idioma = inject(TranslocoService);

  protected readonly consulta = this.store.queue;
  protected readonly pendientes = this.store.pendientes;

  protected readonly pagina = this.store.pagina;
  protected readonly hayMas = this.store.hayMas;
  protected readonly hayAnterior = this.store.hayAnterior;
  protected readonly actualizando = this.store.actualizando;

  /** Constante y no un literal en la plantilla, que se recrearía en cada ciclo. */
  protected readonly filasDelEsqueleto = [1, 2, 3];

  /**
   * Criterio 8: lo que acaba de hacerse, dicho al volver.
   *
   * <p>Viaja en el estado de la navegación y no en la dirección: es un mensaje de una
   * sola vez, y en la dirección sobreviviría a un refresco y a que alguien la comparta.
   *
   * <p>Se lee en el constructor porque `getCurrentNavigation()` solo existe durante la
   * navegación; un rato después ya devuelve `null`.
   */
  protected readonly confirmacion = signal<string | null>(null);

  constructor() {
    const estado = inject(Router).getCurrentNavigation()?.extras.state;
    const decision = estado?.['decision'];

    if (decision === 'approved' || decision === 'rejected') {
      this.confirmacion.set(`listingReview.decision.${decision}`);
    }
  }

  /** Con la configuración regional activa, no con el `date` de Angular, que cae en inglés. */
  protected desdeCuando(iso: string): string {
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  }

  protected precio(valor: Money): string {
    return precioFormateado(valor, this.idioma.getActiveLang());
  }

  /**
   * Las dos siguen siendo llamables en el borde, y no es un descuido: los botones se
   * marcan con `aria-disabled` y no con `disabled`, así que en la última página
   * «Siguiente» todavía recibe el clic. Quien acota el movimiento es el estado.
   */
  protected siguiente(): void {
    this.store.paginaSiguiente();
  }

  protected anterior(): void {
    this.store.paginaAnterior();
  }

  protected reintentar(): void {
    void this.consulta.refetch();
  }
}
