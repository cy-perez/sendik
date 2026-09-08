import {
  ChangeDetectionStrategy,
  Component,
  effect,
  input,
  output,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Lo que sale cuando alguien confirma. La nota es opcional de verdad, no cadena vacía. */
export interface DecisionDeshecha {
  readonly motivo: string;
  readonly nota: string | null;
}

/**
 * Deshacer una decisión de moderación, con motivo obligatorio y confirmación. HU-010.
 *
 * <p>Vive en `shared/ui` porque lo usan las dos pantallas de la historia —la ficha para
 * bajar una publicación y el perfil para revocar un sello— y son la misma interacción con
 * distinta lista de motivos. Duplicarlo significaba duplicar el manejo del foco y el de
 * `Escape`, que es justo la parte que se rompe cuando hay dos copias.
 *
 * <p><strong>No conoce ninguna de las dos acciones.</strong> Recibe las claves de texto y
 * la lista de motivos, y devuelve lo elegido. Así no tiene que importar nada de `catalog`
 * ni de `identity`, que es lo que le permite estar aquí.
 *
 * <p>El motivo se pide **dentro** de la confirmación y no antes: la acción es destructiva
 * y de un solo paso, y separar «elige motivo» de «¿seguro?» en dos pantallas hace que la
 * segunda pregunte por algo que ya no se está viendo.
 */
@Component({
  selector: 'sendik-undo-action',
  imports: [TranslocoPipe],
  templateUrl: './undo-action.html',
  styleUrl: './undo-action.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UndoAction {
  /** Clave del texto del botón que abre. */
  readonly accion = input.required<string>();

  /** Clave de la pregunta de confirmación. */
  readonly confirmacion = input.required<string>();

  /**
   * Clave de un aviso que se lee antes de confirmar, si lo hay.
   *
   * <p>Lo usa la revocación para el criterio 13: que las publicaciones de esa persona
   * siguen visibles (RN-013). Sin esa frase, quien revoca cree que ya retiró lo que no
   * retiró.
   */
  readonly aviso = input<string | null>(null);

  readonly motivos = input.required<readonly string[]>();

  /** Prefijo de la clave de cada motivo. La clave completa es prefijo + valor. */
  readonly prefijoDeMotivos = input.required<string>();

  readonly enCurso = input(false);

  /** Clave del error que devolvió el servidor, si devolvió alguno. */
  readonly error = input<string | null>(null);

  readonly decidido = output<DecisionDeshecha>();

  protected readonly abierto = signal(false);
  protected readonly motivo = signal('');
  protected readonly nota = signal('');
  protected readonly faltaMotivo = signal(false);

  /** Lo mismo que en el rechazo de HU-008: la nota cabe, y quien la escribe la acota. */
  protected readonly maximoDeNota = 500;

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly disparador = viewChild<ElementRef<HTMLButtonElement>>('disparador');

  /** Para no llevar el foco otra vez en cada revisión de la vista. */
  private enfocado = false;

  /**
   * Que al cerrar hay que devolver el foco al botón.
   *
   * <p>Hace falta el aplazamiento y no vale llamar a `focus()` dentro de `cancelar()`: en
   * ese momento el botón **todavía no existe**. Vive dentro del `@else` del panel, así que
   * aparece cuando la vista se revisa, después de que la señal cambie. Llamándolo antes se
   * enfoca sobre `undefined` y el foco se queda en el `body`, que es exactamente lo que
   * este componente existe para evitar.
   *
   * <p>Campo normal y no señal: solo lo lee el efecto que ya se despierta cuando la
   * consulta de vista cambia, y hacerlo señal metería una escritura dentro de un efecto
   * para no ganar nada.
   */
  private devolviendoElFoco = false;

  constructor() {
    effect(() => {
      const panel = this.panel()?.nativeElement;
      const disparador = this.disparador()?.nativeElement;

      if (panel !== undefined) {
        if (!this.enfocado) {
          this.enfocado = true;
          panel.focus();
        }
        return;
      }

      this.enfocado = false;

      if (this.devolviendoElFoco && disparador !== undefined) {
        this.devolviendoElFoco = false;
        disparador.focus();
      }
    });
  }

  protected abrir(): void {
    this.motivo.set('');
    this.nota.set('');
    this.faltaMotivo.set(false);
    this.abierto.set(true);
  }

  /**
   * Cierra sin hacer nada y devuelve el foco a donde estaba.
   *
   * <p>Devolverlo no es un detalle: el panel se pinta donde estaba el botón, así que al
   * cerrarlo el foco se iría al `body` y quien navega con teclado tendría que retabular
   * desde la cabecera del sitio.
   */
  protected cancelar(): void {
    this.devolviendoElFoco = true;
    this.abierto.set(false);
  }

  protected elegir(valor: string): void {
    this.motivo.set(valor);
    if (valor !== '') {
      this.faltaMotivo.set(false);
    }
  }

  protected confirmar(): void {
    if (this.enCurso()) {
      return;
    }
    if (this.motivo() === '') {
      this.faltaMotivo.set(true);
      return;
    }

    const escrita = this.nota().trim();
    this.decidido.emit({ motivo: this.motivo(), nota: escrita === '' ? null : escrita });
  }

  /** Se cierra sin ejecutar nada. Criterio 19. */
  protected alTeclear(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.stopPropagation();
      this.cancelar();
    }
  }
}
