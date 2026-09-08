import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

/**
 * Casilla de verificacion con su etiqueta y, si hace falta, un enlace al
 * documento que se esta aceptando.
 *
 * <p>Existe como primitiva propia porque el consentimiento de la Ley 1581 son
 * dos casillas separadas y obligatorias, y conviene que las dos se comporten
 * exactamente igual: mismo destino tactil, mismo anuncio de error, misma
 * asociacion entre etiqueta y control.
 *
 * <p>El enlace es parte de eso mismo. Un consentimiento valido tiene que ser
 * informado, y no lo es si la persona no puede leer lo que acepta
 * (docs/operacion/datos-personales.md).
 *
 * <p>Recibe los textos ya traducidos, no claves.
 */
@Component({
  selector: 'sendik-checkbox-field',
  imports: [ReactiveFormsModule, RouterLink],
  template: `
    <!-- La etiqueta envuelve el control, asi que tocar el texto tambien marca la
         casilla: el destino tactil deja de ser el cuadrito del navegador. -->
    <label class="mb-4 flex min-h-touch cursor-pointer items-start gap-3 py-2" [for]="fieldId()">
      <input
        type="checkbox"
        class="m-0 size-5 flex-none"
        [id]="fieldId()"
        [formControl]="control()"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="error() ? errorId() : null"
      />
      <span class="tipo-cuerpo">{{ label() }}</span>
    </label>

    <!-- El enlace va FUERA de la etiqueta y no dentro. Dentro, pulsarlo marcaria
         la casilla ademas de abrir el documento, porque la etiqueta reenvia el
         clic al control: la persona aceptaria sin haber leido, con un solo
         gesto. -->
    @if (linkTo(); as destino) {
      <p class="tipo-secundario -mt-2 mb-4">
        <!-- En pestana nueva para no perder el formulario a medio llenar. rel es
             obligatorio con target: sin noopener, la pagina abierta puede
             reescribir esta desde window.opener. -->
        <a
          class="inline-flex min-h-touch items-center text-primary"
          [routerLink]="destino"
          target="_blank"
          rel="noopener noreferrer"
        >
          {{ linkLabel() }}
          <!-- Que se abre otra pestana no puede verse solo: quien usa lector de
               pantalla o lupa esta a mitad de un formulario de seis campos y
               tiene que saber que cambio de contexto. -->
          <span class="solo-lectores">{{ newTabLabel() }}</span>
        </a>
      </p>
    }

    @if (error(); as texto) {
      <p class="tipo-leyenda mt-1 text-error" [id]="errorId()" role="alert">{{ texto }}</p>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CheckboxField {
  readonly control = input.required<FormControl<boolean>>();
  readonly label = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly error = input<string | null>(null);

  /** Documento que se esta aceptando. Sin el, la casilla no muestra enlace. */
  readonly linkTo = input<string | null>(null);
  readonly linkLabel = input('');

  /** Aviso de que el enlace abre otra pestana. Solo para lector de pantalla. */
  readonly newTabLabel = input('');

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
}
