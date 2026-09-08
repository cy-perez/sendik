import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';

/**
 * Superficie del sistema: fondo, borde y radio. Es la caja de la que salen la
 * tarjeta de producto, las tres tarjetas de confianza de la portada y los
 * bloques del panel del vendedor.
 *
 * <p>No trae relleno propio. El de la tarjeta de producto y el de un bloque de
 * texto no coinciden, y meter aqui uno por omision obliga a anularlo en la mitad
 * de los sitios, que es peor que no ponerlo.
 *
 * <p>{@code interactive} no la vuelve pulsable: solo le da la reaccion visual y
 * el anillo de foco cuando algo de dentro lo recibe. Lo pulsable es el enlace
 * que se estira sobre ella, y tiene que existir de verdad para que funcione con
 * teclado.
 */
@Component({
  selector: 'sendik-card',
  template: '<ng-content />',
  host: {
    '[class]': 'classes()',
  },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Card {
  readonly interactive = input(false);

  protected readonly classes = computed(() =>
    [
      'relative flex flex-col overflow-hidden rounded-lg border border-border bg-surface',
      this.interactive()
        ? 'transition-shadow hover:shadow-md focus-within:outline focus-within:outline-3 focus-within:outline-offset-2 focus-within:outline-focus'
        : '',
    ]
      .filter((clase) => clase !== '')
      .join(' '),
  );
}
