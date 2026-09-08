import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Casilla de verificacion con su etiqueta y, si hace falta, un enlace al
 * documento que se esta aceptando.
 *
 * <p>Existe como componente propio porque el consentimiento de la Ley 1581 son
 * dos casillas separadas y obligatorias, y conviene que las dos se comporten
 * exactamente igual: mismo destino tactil, mismo anuncio de error, misma
 * asociacion entre etiqueta y control.
 *
 * <p>El enlace es parte de eso mismo. Un consentimiento valido tiene que ser
 * informado, y no lo es si la persona no puede leer lo que acepta
 * (docs/operacion/datos-personales.md).
 */
@Component({
  selector: 'sendik-checkbox-field',
  imports: [ReactiveFormsModule, RouterLink, TranslocoPipe],
  templateUrl: './checkbox-field.html',
  styleUrl: './form-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckboxField {
  readonly control = input.required<FormControl<boolean>>();
  readonly labelKey = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly errorKey = input<string | null>(null);

  /** Documento que se esta aceptando. Sin el, la casilla no muestra enlace. */
  readonly linkTo = input<string | null>(null);

  readonly linkLabelKey = input('form.readDocument');

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
}
