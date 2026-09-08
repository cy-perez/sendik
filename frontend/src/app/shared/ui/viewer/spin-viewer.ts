import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  input,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { indiceDesde, normalizar } from '../../domain/frame-index';

/** Cuenta las instancias, para dar a cada una un identificador propio. */
let siguienteVisor = 0;

/** Un fotograma de la secuencia: dónde está la imagen y a qué giro corresponde. */
export interface FotogramaDelVisor {
  readonly url: string;
  readonly grados: number;
}

/**
 * Cuántos fotogramas hacen falta para que el visor se active.
 *
 * <p>Es el caso borde de la historia: «Conexión lenta: el visor no se activa hasta tener al
 * menos cuatro fotogramas». Con menos, girar da saltos de noventa grados o más, que se ve
 * peor que el carrusel que ya está debajo.
 */
export const FOTOGRAMAS_MINIMOS = 4;

/**
 * El visor giratorio de la ficha. HU-003, criterios 11 a 19.
 *
 * <p>**Sin ninguna librería** (criterio 19). Lo que hace es cambiar el `src` de una imagen
 * según cuánto se ha arrastrado, y esa cuenta vive en `shared/domain/frame-index.ts`, que
 * se prueba sin navegador. Aquí solo están el gesto, el teclado y la precarga.
 *
 * <p>**El fotograma frontal es una imagen normal y se renderiza en el servidor**
 * (criterio 18). No hay estado que hidratar para verlo: quien llegue sin JavaScript, o
 * antes de que cargue, ve la foto del producto con su `alt`, que es lo que un buscador
 * indexa y de lo que vive el posicionamiento del marketplace.
 *
 * <p><strong>No hay giro automático ni inercia, para nadie.</strong> Eso cubre el criterio
 * 16 por construcción y no con una excepción: `prefers-reduced-motion` no tiene aquí nada
 * que apagar porque el fotograma sigue al dedo y se para cuando el dedo se para. Un giro de
 * cortesía al cargar habría sido bonito y habría obligado a mantener dos comportamientos.
 */
@Component({
  selector: 'sendik-spin-viewer',
  imports: [TranslocoPipe],
  templateUrl: './spin-viewer.html',
  styleUrl: './spin-viewer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SpinViewer {
  readonly fotogramas = input.required<readonly FotogramaDelVisor[]>();

  /** El título del producto. Va en el `alt` del fotograma que ve un buscador. */
  readonly titulo = input.required<string>();

  private readonly marco = viewChild<ElementRef<HTMLElement>>('marco');

  /** Se guarda en el constructor para poder cortar la precarga desde fuera de la inyección. */
  private readonly destruccion = inject(DestroyRef);

  /**
   * El identificador del párrafo de instrucciones.
   *
   * <p>Generado y no fijo: esto es un componente de `shared/ui`, así que dos en la misma
   * página con un `id` literal producirían duplicados y `aria-describedby` apuntaría al
   * primero.
   */
  protected readonly idInstrucciones = `visor-instrucciones-${siguienteVisor++}`;

  protected readonly indice = signal(0);

  /**
   * Cuántos fotogramas están ya descargados.
   *
   * <p>Empieza en uno: el frontal viene en el HTML del servidor y no hay que esperarlo.
   */
  protected readonly cargados = signal(1);

  /** Si el gesto está activo. Mientras lo esté, el marco no suelta el puntero. */
  private arrastre: { x: number; indice: number } | null = null;

  protected readonly total = computed(() => this.fotogramas().length);

  /**
   * Si se puede girar.
   *
   * <p>Dos condiciones y las dos son casos borde de la historia: que la secuencia tenga
   * fotogramas suficientes —una publicación antigua con menos de ocho no ofrece visor— y
   * que hayan llegado al menos cuatro, que es lo de la conexión lenta.
   */
  protected readonly activo = computed(
    () => this.total() >= FOTOGRAMAS_MINIMOS && this.cargados() >= FOTOGRAMAS_MINIMOS,
  );

  /** Criterio 14: mientras no estén todos, se avisa sin robar la atención. */
  protected readonly cargando = computed(() => this.cargados() < this.total());

  protected readonly actual = computed(
    () => this.fotogramas()[normalizar(this.indice(), Math.max(this.total(), 1))] ?? null,
  );

  constructor() {
    /*
     * Precarga el resto en segundo plano. Criterio 13.
     *
     * En `afterNextRender` y no antes: en el servidor no hay `Image`, y en el cliente
     * lanzarlo durante el renderizado competiria con lo que la persona esta esperando ver.
     * Aqui corre cuando la ficha ya esta pintada, que es lo que el criterio pide: "sin
     * bloquear el hilo principal ni retrasar el contenido visible".
     */
    afterNextRender(() => this.precargar());
  }

  /**
   * Empieza el gesto. Criterio 12.
   *
   * <p>Con eventos de puntero y no con los de ratón y los de tacto por separado: es una API
   * y no dos, y cubre también el lápiz. `setPointerCapture` es lo que hace que soltar fuera
   * del marco no deje el visor girando pegado al cursor.
   */
  protected alEmpezar(evento: PointerEvent): void {
    if (!this.activo()) {
      return;
    }

    this.arrastre = { x: evento.clientX, indice: this.indice() };
    (evento.target as Element).setPointerCapture?.(evento.pointerId);
  }

  protected alMover(evento: PointerEvent): void {
    if (this.arrastre === null) {
      return;
    }

    const ancho = this.marco()?.nativeElement.clientWidth ?? 0;

    this.indice.set(
      indiceDesde(evento.clientX - this.arrastre.x, ancho, this.total(), this.arrastre.indice),
    );
  }

  protected alSoltar(evento: PointerEvent): void {
    this.arrastre = null;
    (evento.target as Element).releasePointerCapture?.(evento.pointerId);
  }

  /**
   * Gira con el teclado. Criterio 15.
   *
   * <p>Solo las flechas horizontales. **Las verticales no se tocan a propósito**: son las
   * que desplazan la página, y capturarlas dejaría a quien navega con teclado atrapado
   * dentro del visor, que es el criterio 17 dicho para el otro dispositivo de entrada.
   */
  protected alTeclear(evento: KeyboardEvent): void {
    if (!this.activo()) {
      return;
    }

    const paso = evento.key === 'ArrowRight' ? 1 : evento.key === 'ArrowLeft' ? -1 : 0;

    if (paso === 0) {
      return;
    }

    // Solo se cancela lo que de verdad se atiende: un `preventDefault` incondicional aquí
    // se comería el tabulador y dejaría el foco encerrado.
    evento.preventDefault();
    this.indice.set(normalizar(this.indice() + paso, this.total()));
  }

  /**
   * Descarga el resto de fotogramas, uno a uno.
   *
   * <p>De uno en uno y no todos a la vez: ocho peticiones simultáneas en una conexión lenta
   * se estorban entre sí y ninguna termina, cuando lo que hace falta es que **la cuarta
   * llegue pronto** para poder activar el visor. En orden, el visor se enciende antes.
   */
  private precargar(): void {
    const pendientes = this.fotogramas().slice(1);
    let cancelado = false;

    // Sin esto, salir de la ficha a mitad de la precarga deja ocho descargas en curso
    // compitiendo con las de la pantalla a la que la persona acaba de ir.
    this.destruccion.onDestroy(() => {
      cancelado = true;
    });

    const siguiente = (posicion: number): void => {
      const fotograma = pendientes[posicion];

      if (cancelado || fotograma === undefined) {
        return;
      }

      const imagen = new Image();
      imagen.decoding = 'async';

      const seguir = (): void => {
        if (cancelado) {
          return;
        }
        this.cargados.update((cuantos) => cuantos + 1);
        siguiente(posicion + 1);
      };

      // Se sigue igual si una falla: el criterio 14 dice que el visor funciona con los
      // disponibles, y una toma que no está no puede detener a las que vienen detrás.
      imagen.addEventListener('load', seguir, { once: true });
      imagen.addEventListener('error', seguir, { once: true });
      imagen.src = fotograma.url;
    };

    siguiente(0);
  }
}
