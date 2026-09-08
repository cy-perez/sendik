import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { LucideDynamicIcon, type LucideIconData } from '@lucide/angular';

/**
 * Icono de interfaz.
 *
 * <p>Envuelve a Lucide en vez de usarlo directo por una razon concreta: Lucide
 * dibuja con terminaciones REDONDEADAS y el sistema de Sendik las quiere RECTAS,
 * que es la misma decision que los cortes rectos del isotipo. Usar
 * {@code <svg lucideIcon>} suelto por las plantillas significa confiar en que
 * nadie olvide la clase, y basta un olvido para que el set se vea mezclado, que
 * es lo que hace que una iconografia parezca amateur. Aqui la geometria no es
 * opcional: la pone el componente y no hay entrada para quitarla.
 *
 * <p>El icono llega como dato, no como nombre en una cadena. Asi el compilador
 * avisa de un icono que no existe, y el empaquetador solo incluye los que de
 * verdad se usan; con nombres sueltos entrarian los mil y pico del paquete.
 *
 * <p>Es decorativo salvo que se le de {@code label}. Un icono junto a un texto
 * que ya dice lo mismo se anuncia dos veces si no se oculta, y un icono solo,
 * sin nombre accesible, es un boton mudo. Por eso son dos entradas y no una.
 */
@Component({
  selector: 'sendik-icon',
  imports: [LucideDynamicIcon],
  template: `
    <svg
      [lucideIcon]="icon()"
      [class]="classes()"
      [attr.role]="label() ? 'img' : null"
      [attr.aria-label]="label()"
      [attr.aria-hidden]="label() ? null : 'true'"
    ></svg>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Icon {
  readonly icon = input.required<LucideIconData>();

  /**
   * Nombre accesible. Si no se da, el icono se oculta al lector de pantalla,
   * que es lo correcto cuando va acompanado de texto visible.
   */
  readonly label = input<string | null>(null);

  /** Sube el area viva de 20 a 24. Es la unica variante de tamano que hay. */
  readonly large = input(false);

  /**
   * Rellena el trazo. Solo para el icono que tiene que distinguir «puesto» de
   * «no puesto» sin depender del color.
   */
  readonly filled = input(false);

  protected readonly classes = computed(() =>
    ['icon-base', this.large() ? 'icon-lg' : '', this.filled() ? 'icon-filled' : '']
      .filter((clase) => clase !== '')
      .join(' '),
  );
}
