import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

/** Una opción del selector: lo que se manda y lo que se lee. */
export interface SelectOption {
  readonly code: string;
  readonly name: string;
}

/**
 * Selector del sistema de diseño.
 *
 * <p>Gemelo de `TextField` y con las mismas decisiones: el error llega como clave desde
 * arriba, ya decidido, en lugar de deducirlo aquí de los validadores; con detección de
 * cambios sin zonas, leer el estado de un `FormControl` dentro de un `computed` no vuelve a
 * evaluarse cuando ese estado cambia.
 *
 * <p><strong>Comparte `form-field.css` con el campo de texto</strong>, y eso no es ahorro:
 * un selector que se viera distinto de un campo de texto en el mismo formulario sería dos
 * sistemas de diseño en una pantalla.
 *
 * <p><strong>Las opciones llegan con su texto ya resuelto y no como clave de Transloco.</strong>
 * Es lo contrario de la etiqueta y de la pista. El motivo está en el criterio 26 de HU-016:
 * los nombres de departamento y municipio son nombres propios y no se traducen, así que
 * pasan tal cual y no hay clave que buscar.
 *
 * <p>Un `<select>` nativo y no una lista propia: se recorre con el teclado, lo anuncia
 * cualquier lector de pantalla, y en móvil el navegador da su propio selector, que es mejor
 * que cualquiera que pudiéramos escribir (frontend/CLAUDE.md: HTML semántico antes que ARIA).
 */
@Component({
  selector: 'sendik-select-field',
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './select-field.html',
  styleUrl: './form-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SelectField {
  readonly control = input.required<FormControl<string>>();
  readonly labelKey = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly options = input.required<readonly SelectOption[]>();
  /** El texto de la opción vacía. Dice qué hay que elegir, o por qué todavía no se puede. */
  readonly placeholderKey = input.required<string>();
  readonly hintKey = input<string | null>(null);
  readonly errorKey = input<string | null>(null);

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
  protected readonly hintId = computed(() => `${this.fieldId()}-hint`);

  /**
   * El lector de pantalla anuncia primero la pista y luego el error. Si se apuntara solo al
   * error, quien no ve el campo pierde la indicación en cuanto se equivoca una vez.
   */
  protected readonly describedBy = computed(() => {
    const partes = [this.hintKey() ? this.hintId() : null, this.errorKey() ? this.errorId() : null];
    const unidas = partes.filter((parte) => parte !== null).join(' ');
    return unidas.length > 0 ? unidas : null;
  });
}
