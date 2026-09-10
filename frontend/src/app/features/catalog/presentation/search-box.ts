import {
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  linkedSignal,
  output,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * La caja de búsqueda del catálogo. HU-014.
 *
 * <p><strong>Es un formulario y no un campo suelto</strong>: se envía con Enter sin
 * necesidad de tocar el botón, que es como la gente busca, y el botón sigue ahí para quien
 * navega con el ratón o con lector de pantalla.
 *
 * <p><strong>No busca mientras se escribe.</strong> Cada tecla sería una petición y una
 * entrada en el historial del navegador, y con la dirección llevando los criterios
 * (criterio 15) eso deja el botón de volver inservible. Se busca al enviar.
 *
 * <p>El borrador es un `linkedSignal` y no una señal cualquiera: parte de lo que dice la
 * dirección y se deja sobrescribir mientras se escribe, pero **vuelve a seguirla** cuando
 * cambia por fuera —al pulsar una ficha de filtro, al volver atrás, al entrar por un
 * enlace compartido—. Con una señal normal habría que sincronizarla a mano y la caja se
 * quedaría enseñando lo que ya no se está buscando.
 */
@Component({
  selector: 'sendik-search-box',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './search-box.html',
  styleUrl: './search-box.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SearchBox {
  /** Lo que dice la dirección ahora mismo. */
  readonly texto = input<string | null>(null);

  /** Lo que se busca al enviar. Nulo cuando se vació la caja. */
  readonly buscar = output<string | null>();

  protected readonly borrador = linkedSignal(() => this.texto() ?? '');

  protected readonly hayTexto = computed(() => this.borrador().trim() !== '');

  private readonly entrada = viewChild.required<ElementRef<HTMLInputElement>>('entrada');

  protected escribir(valor: string): void {
    this.borrador.set(valor);
  }

  protected enviar(): void {
    const limpio = this.borrador().trim();
    this.buscar.emit(limpio === '' ? null : limpio);
  }

  /**
   * Vaciar la caja es buscar sin texto, que es el catálogo (criterio 8).
   *
   * <p>Y devuelve el foco a la entrada. El aspa vive dentro de `@if (hayTexto())`, así que
   * al pulsarla se destruye a sí misma en el mismo tic: sin esto el foco cae a `<body>` y
   * quien navega con teclado vuelve al principio del documento (WCAG 2.4.3).
   */
  protected limpiar(): void {
    this.borrador.set('');
    this.entrada().nativeElement.focus();
    this.buscar.emit(null);
  }
}
