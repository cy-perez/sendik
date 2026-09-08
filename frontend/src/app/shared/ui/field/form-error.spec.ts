import { ChangeDetectionStrategy, Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { FormError } from './form-error';

@Component({
  imports: [FormError],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<sendik-form-error>No pudimos crear la cuenta</sendik-form-error>',
})
class Anfitrion {}

describe('FormError', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const banda = () => fixture.nativeElement.querySelector('sendik-form-error') as HTMLElement;

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [Anfitrion] });
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  /**
   * role="alert" lo pone el componente y no quien lo usa. Es la razon de que
   * exista: estaba copiado en cinco pantallas y es justo lo que se olvida al
   * copiar. Sin el, el error se ve pero no se anuncia, y quien no mira la
   * pantalla no se entera de que el envio fallo.
   */
  it('se anuncia como alerta sin que quien lo usa tenga que acordarse', () => {
    expect(banda().getAttribute('role')).toBe('alert');
  });

  it('muestra el mensaje que recibe', () => {
    expect(banda().textContent?.trim()).toBe('No pudimos crear la cuenta');
  });

  /**
   * El borde grueso de la izquierda es la senal que NO depende del color: quien
   * no percibe el rojo sigue viendo que ese bloque esta marcado.
   */
  it('no depende solo del color para marcarse', () => {
    expect(banda().getAttribute('class')).toContain('border-l-4');
  });
});
