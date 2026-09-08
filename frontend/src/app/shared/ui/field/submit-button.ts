import { ChangeDetectionStrategy, Component, input } from '@angular/core';

import { Button } from '../button/button';

/**
 * Unica llamada a la accion de una pantalla de formulario. Ocupa el ancho
 * completo, que es lo que la distingue de un boton cualquiera.
 *
 * <p>El texto NO cambia al enviar: cambia {@code aria-busy} y aparece un
 * indicador. Sustituir «Crear cuenta» por «Enviando…» mueve el foco de sitio
 * para quien usa lector de pantalla y hace que el boton cambie de tamano bajo el
 * cursor.
 *
 * <p>Nunca se queda cargando para siempre: el estado lo controla quien lo usa, y
 * la pagina de registro lo apaga tambien cuando la peticion falla (caso borde de
 * HU-001).
 *
 * <p>Recibe los textos ya traducidos. La version anterior recibia claves y
 * traducia por dentro, lo que ataba una primitiva al catalogo de traducciones.
 */
@Component({
  selector: 'sendik-submit-button',
  imports: [Button],
  template: `
    <button sendikButton type="submit" class="w-full" [loading]="loading()" [disabled]="loading()">
      {{ label() }}

      @if (loading()) {
        <!-- motion-safe: el indicador que gira sin parar es el caso mas molesto
             para quien pidio menos movimiento, y por eso se corta de forma
             explicita en vez de confiar en que alguien lo recuerde. -->
        <span
          class="size-4 flex-none rounded-full border-2 border-current border-t-transparent motion-safe:animate-spin"
          aria-hidden="true"
        ></span>
        <span class="solo-lectores">{{ loadingLabel() }}</span>
      }
    </button>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SubmitButton {
  readonly label = input.required<string>();
  readonly loading = input(false);
  readonly loadingLabel = input('');
}
