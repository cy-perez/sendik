import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Campo de texto del sistema de diseno.
 *
 * <p>El error llega como clave desde arriba, ya decidido, en lugar de deducirlo
 * aqui de los validadores. Con deteccion de cambios sin zonas, leer el estado de
 * un FormControl dentro de un computed no vuelve a evaluarse cuando ese estado
 * cambia; el padre si tiene senales y sabe cuando mostrarlo.
 */
@Component({
  selector: 'sendik-text-field',
  imports: [ReactiveFormsModule, TranslocoPipe],
  templateUrl: './text-field.html',
  styleUrl: './form-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TextField {
  readonly control = input.required<FormControl<string>>();
  readonly labelKey = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly type = input<'text' | 'email' | 'password' | 'date'>('text');
  readonly autocomplete = input<string>('off');
  readonly hintKey = input<string | null>(null);
  readonly errorKey = input<string | null>(null);

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
  protected readonly hintId = computed(() => `${this.fieldId()}-hint`);

  /**
   * El lector de pantalla anuncia primero la pista y luego el error. Si se
   * apuntara solo al error, quien no ve el campo pierde la indicacion de formato
   * en cuanto se equivoca una vez.
   */
  protected readonly describedBy = computed(() => {
    const partes = [this.hintKey() ? this.hintId() : null, this.errorKey() ? this.errorId() : null];
    const unidas = partes.filter((parte) => parte !== null).join(' ');
    return unidas.length > 0 ? unidas : null;
  });
}
