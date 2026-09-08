import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/**
 * Boton de envio con estado de carga.
 *
 * <p>El texto no cambia al enviar: cambia {@code aria-busy} y aparece un
 * indicador. Sustituir "Crear cuenta" por "Enviando..." mueve el foco de sitio
 * para quien usa lector de pantalla y hace que el boton cambie de tamano.
 *
 * <p>Nunca se queda cargando para siempre: el estado lo controla quien lo usa y
 * la pagina de registro lo apaga tambien cuando la peticion falla (caso borde de
 * HU-001).
 */
@Component({
  selector: 'sendik-submit-button',
  imports: [TranslocoPipe],
  templateUrl: './submit-button.html',
  styleUrl: './submit-button.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitButton {
  readonly labelKey = input.required<string>();
  readonly loading = input(false);
  readonly loadingLabelKey = input('form.sending');
}
