import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl } from '@angular/forms';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { TextField } from './text-field';

@Component({
  imports: [TextField],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sendik-text-field
      [control]="correo"
      fieldId="correo"
      label="Correo electronico"
      type="email"
      [hint]="pista()"
      [error]="error()"
    />
  `,
})
class Anfitrion {
  readonly correo = new FormControl('', { nonNullable: true });
  readonly pista = signal<string | null>(null);
  readonly error = signal<string | null>(null);
}

describe('TextField', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const campo = () => fixture.nativeElement.querySelector('input') as HTMLInputElement;

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

  /**
   * La etiqueta tiene que estar asociada de verdad, no solo puesta al lado: es
   * lo que hace que pulsar sobre el texto enfoque el campo y que el lector de
   * pantalla lo anuncie por su nombre.
   */
  it('asocia la etiqueta con el campo', () => {
    const etiqueta = fixture.nativeElement.querySelector('label') as HTMLLabelElement;

    expect(etiqueta.textContent).toContain('Correo electronico');
    expect(etiqueta.getAttribute('for')).toBe(campo().id);
  });

  /**
   * El error se anuncia al aparecer y no cuando el foco llega al campo. Sin
   * role="alert" el mensaje se ve pero no se locuta, y quien no mira la pantalla
   * no se entera de que el envio fallo.
   */
  it('anuncia el error en cuanto aparece', async () => {
    fixture.componentInstance.error.set('Escribe un correo valido');
    await render();

    const id = campo().getAttribute('aria-describedby');
    const mensaje = fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;
    expect(mensaje.getAttribute('role')).toBe('alert');
  });

  it('en reposo no se declara invalido ni describe nada', () => {
    expect(campo().getAttribute('aria-invalid')).toBeNull();
    expect(campo().getAttribute('aria-describedby')).toBeNull();
  });

  it('con error se marca invalido y apunta al mensaje', async () => {
    fixture.componentInstance.error.set('Escribe un correo valido');
    await render();

    expect(campo().getAttribute('aria-invalid')).toBe('true');

    const id = campo().getAttribute('aria-describedby');
    const mensaje = fixture.nativeElement.querySelector(`#${id}`) as HTMLElement;
    expect(mensaje.textContent?.trim()).toBe('Escribe un correo valido');
  });

  /**
   * Los dos, y en ese orden. Si al fallar el campo se apuntara solo al error,
   * quien no ve la pantalla pierde la indicacion de formato justo cuando mas la
   * necesita: cuando se acaba de equivocar.
   */
  it('con pista y error describe primero la pista y luego el error', async () => {
    fixture.componentInstance.pista.set('Usaremos este correo para avisarte');
    fixture.componentInstance.error.set('Escribe un correo valido');
    await render();

    expect(campo().getAttribute('aria-describedby')).toBe('correo-hint correo-error');
  });

  it('escribir en el campo actualiza el control', async () => {
    campo().value = 'ana@sendik.co';
    campo().dispatchEvent(new Event('input'));
    await render();

    expect(fixture.componentInstance.correo.value).toBe('ana@sendik.co');
  });
});
