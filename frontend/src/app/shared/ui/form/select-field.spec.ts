import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { SelectField, type SelectOption } from './select-field';

/**
 * El selector del sistema de diseño. HU-016.
 *
 * <p>Entró sin spec y eso costó un arreglo que no arreglaba nada: se le puso una entrada
 * `disabled` que ponía `[disabled]` sobre un `<select [formControl]>`, que es un no-op —la
 * directiva declara ese input y su setter solo advierte—. Lo que se prueba aquí es lo
 * contrario: que deshabilitar **el control** sí deshabilita el elemento.
 */
@Component({
  imports: [ReactiveFormsModule, SelectField],
  template: `
    <sendik-select-field
      fieldId="prueba"
      labelKey="Departamento"
      placeholderKey="Elige uno"
      [control]="control"
      [options]="opciones()"
      [errorKey]="error()"
    />
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class Anfitrion {
  readonly control = new FormControl('', { nonNullable: true });
  readonly opciones = signal<readonly SelectOption[]>([
    { code: '05', name: 'Antioquia' },
    { code: '11', name: 'Bogotá, D.C.' },
  ]);
  readonly error = signal<string | null>(null);
}

describe('SelectField', () => {
  let fixture: ComponentFixture<Anfitrion>;

  const select = () => fixture.nativeElement.querySelector('#prueba') as HTMLSelectElement;

  beforeEach(() => {
    TestBed.configureTestingModule({ imports: [Anfitrion] });
    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
  });

  it('ofrece la opción vacía primero y luego las que le den', () => {
    expect(select().options.length).toBe(3);
    expect(select().options[0]?.value).toBe('');
    expect(select().options[1]?.textContent).toContain('Antioquia');
  });

  /** Criterio 4: el control deshabilitado deshabilita el elemento de verdad. */
  it('se deshabilita cuando se deshabilita su control', () => {
    expect(select().disabled).toBe(false);

    fixture.componentInstance.control.disable();
    fixture.detectChanges();

    expect(select().disabled).toBe(true);
  });

  /** La etiqueta apunta al campo: es lo que hace que consultarlo por etiqueta funcione. */
  it('asocia la etiqueta con el campo', () => {
    const etiqueta = fixture.nativeElement.querySelector('label') as HTMLLabelElement;

    expect(etiqueta.getAttribute('for')).toBe('prueba');
    expect(select().id).toBe('prueba');
  });

  /** El error se anuncia y se asocia; sin error no ensucia el árbol de accesibilidad. */
  it('marca el campo y describe el error cuando lo hay', () => {
    expect(select().getAttribute('aria-invalid')).toBeNull();
    expect(select().getAttribute('aria-describedby')).toBeNull();

    fixture.componentInstance.error.set('Elige un departamento.');
    fixture.detectChanges();

    expect(select().getAttribute('aria-invalid')).toBe('true');
    expect(select().getAttribute('aria-describedby')).toBe('prueba-error');

    const aviso = fixture.nativeElement.querySelector('#prueba-error') as HTMLElement;
    expect(aviso.getAttribute('role')).toBe('alert');
    expect(aviso.textContent).toContain('Elige un departamento.');
  });
});
