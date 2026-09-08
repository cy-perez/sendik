import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { Card } from './card';

@Component({
  imports: [Card],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sendik-card [interactive]="interactiva()">
      <p>Chaqueta de mezclilla</p>
    </sendik-card>
  `,
})
class Anfitrion {
  readonly interactiva = signal(false);
}

describe('Card', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const tarjeta = () => fixture.nativeElement.querySelector('sendik-card') as HTMLElement;

  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [Anfitrion] });
    fixture = TestBed.createComponent(Anfitrion);
    await render();
  });

  it('proyecta su contenido sobre la superficie del sistema', () => {
    expect(tarjeta().textContent).toContain('Chaqueta de mezclilla');
    expect(tarjeta().getAttribute('class')).toContain('bg-surface');
  });

  /**
   * Una tarjeta que no es interactiva no debe mostrar anillo de foco: seria un
   * anillo que aparece sin que nada se pueda accionar.
   */
  it('en reposo no reacciona al foco', () => {
    expect(tarjeta().getAttribute('class')).not.toContain('focus-within');
  });

  /**
   * Cuando si lo es, el anillo lo da focus-within y no :focus. Lo que recibe el
   * foco es el enlace de dentro, no la tarjeta; con :focus el borde no
   * aparecería nunca al navegar con teclado.
   */
  it('interactiva muestra el anillo cuando algo de dentro recibe el foco', async () => {
    fixture.componentInstance.interactiva.set(true);
    await render();

    expect(tarjeta().getAttribute('class')).toContain('focus-within:outline');
  });
});
