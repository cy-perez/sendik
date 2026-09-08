import { ChangeDetectionStrategy, Component } from '@angular/core';

/**
 * La banda de error que encabeza un formulario cuando falla el envio.
 *
 * <p>Existe como primitiva porque estaba repetida, identica, en cinco hojas de
 * `auth`, y una regla copiada cinco veces se corrige en cuatro sitios y se
 * olvida en el quinto.
 *
 * <p>Lleva {@code role="alert"} puesto por el componente y no por quien lo usa:
 * es lo unico que hace que el mensaje se anuncie sin mover el foco, y es
 * justamente lo que se olvida al copiar y pegar. Se pinta solo cuando hay algo
 * que decir, asi que no hay region viva vacia esperando.
 *
 * <p>El borde izquierdo grueso es la unica senal ademas del color: quien no
 * percibe el rojo sigue viendo que ese bloque esta marcado.
 */
@Component({
  selector: 'sendik-form-error',
  template: '<ng-content />',
  host: {
    role: 'alert',
    class:
      'tipo-cuerpo mb-6 block rounded-md border border-l-4 border-error bg-surface px-4 py-3 text-error',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FormError {}
