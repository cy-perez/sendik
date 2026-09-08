import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ReviewStore } from '../application/review.store';
import { hayDiscrepanciaDeTitular } from '../domain/pending-verification';

/**
 * La bandeja del moderador: lo que espera revisión, lo más viejo primero. HU-006.
 *
 * <p>Pantalla interna, no pública. No lleva el acento bronce, que está reservado
 * a lo verificado, ni compite con la acción principal de las pantallas de cara
 * al comprador.
 *
 * <p>No decide nada: desde aquí se entra al detalle, y es allí donde se aprueba o se
 * rechaza. Poner los botones en la lista invitaría a decidir sin haber mirado las
 * imágenes, que es exactamente lo que esta pantalla existe para que no pase.
 */
@Component({
  selector: 'sendik-inbox-page',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './inbox-page.html',
  styleUrl: './review.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class InboxPage {
  private readonly store = inject(ReviewStore);
  private readonly idioma = inject(TranslocoService);

  private readonly titulo = viewChild.required<ElementRef<HTMLElement>>('titulo');

  protected readonly consulta = this.store.inbox;
  protected readonly pendientes = this.store.pendientes;

  protected readonly pagina = this.store.pagina;
  protected readonly hayMas = this.store.hayMas;
  protected readonly hayAnterior = this.store.hayAnterior;
  protected readonly actualizando = this.store.actualizando;

  /** Constante y no un literal en la plantilla, que se recrearía en cada ciclo. */
  protected readonly filasDelEsqueleto = [1, 2, 3];

  /** Criterio 7: se señala también en la lista, para saber qué mirar antes de abrir. */
  protected readonly discrepa = hayDiscrepanciaDeTitular;

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
      this.confirmacion.set(`verificationReview.decision.${decision}`);
    }
  }

  /** Con la configuración regional activa, no con el `date` de Angular, que cae en inglés. */
  protected desdeCuando(iso: string): string {
    return new Intl.DateTimeFormat(this.idioma.getActiveLang(), {
      dateStyle: 'medium',
      timeStyle: 'short',
    }).format(new Date(iso));
  }

  /**
   * Reintenta la carga, y se lleva el foco antes de que el boton desaparezca.
   *
   * <p><strong>El boton se destruye al pulsarlo</strong>: el bloque de error deja paso al
   * esqueleto mientras vuelve a pedirse. Sin mover el foco se queda en `body`, y quien
   * navega con teclado tiene que tabular desde el principio del documento. Es la misma
   * leccion que ya pagaron los botones de paginacion, por el otro camino.
   *
   * <p>Se mueve <strong>aqui, en el clic</strong>, y no cuando termina el reintento. El
   * encabezado existe en los cuatro estados de esta pantalla, asi que ya esta en el DOM y
   * el foco no pasa por `body` en ningun momento. Esperar al desenlace obligaba a
   * adivinar cuando las señales de la consulta se ponen de acuerdo entre ellas, y ese
   * hueco -promesa resuelta, señal todavia sin actualizar- reabria el mismo defecto por
   * otra puerta.
   *
   * <p>Queda donde queda pase lo que pase: si sale bien, arriba de la lista que se pidio;
   * si vuelve a fallar, arriba del aviso, a un tabulador del boton. Lo que no queda es en
   * `body`.
   */
  protected reintentar(): void {
    this.titulo().nativeElement.focus();

    void this.consulta.refetch();
  }

  /**
   * Las dos siguen siendo llamables en el borde, y no es un descuido.
   *
   * <p>Los botones se marcan con `aria-disabled` y no con `disabled`, así que en la última
   * página «Siguiente» todavía recibe el clic. Quien acota el movimiento es el estado:
   * pulsar ahí no hace nada. Comprobarlo también aquí sería repetir la misma regla en dos
   * sitios que pueden dejar de estar de acuerdo.
   */
  protected siguiente(): void {
    this.store.paginaSiguiente();
  }

  protected anterior(): void {
    this.store.paginaAnterior();
  }
}
