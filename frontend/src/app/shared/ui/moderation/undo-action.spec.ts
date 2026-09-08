import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { UndoAction, type DecisionDeshecha } from './undo-action';

/**
 * El panel de deshacer. HU-010, criterios 4, 12, 13 y 19.
 *
 * <p>Se prueba a través de un anfitrión y no montando el componente suelto, porque lo que
 * importa es lo que sale por `decidido`: quién lo recibe y con qué. Montarlo solo obligaría
 * a leer señales protegidas, que es probar la implementación.
 */
@Component({
  imports: [UndoAction],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sendik-undo-action
      accion="moderation.undo.seal.action"
      confirmacion="moderation.undo.seal.confirm"
      [aviso]="aviso()"
      prefijoDeMotivos="verificationReview.revocationReasons."
      [motivos]="motivos"
      (decidido)="recibido.set($event)"
    />
  `,
})
class Anfitrion {
  readonly motivos = ['HOLDER_REQUEST', 'BANK_ACCOUNT_NOT_HOLDER'];
  readonly aviso = signal<string | null>(null);
  readonly recibido = signal<DecisionDeshecha | null>(null);
}

describe('UndoAction', () => {
  let fixture: ComponentFixture<Anfitrion>;

  beforeEach(async () => {
    // Sin proveedores propios: Transloco y el resto del entorno los pone
    // `src/test-providers.ts`, que es global a la suite. Las traducciones son las de
    // verdad, así que estas pruebas también comprueban que las claves existan.
    TestBed.configureTestingModule({ imports: [Anfitrion] });

    fixture = TestBed.createComponent(Anfitrion);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  /**
   * El documento del propio fixture, no el global.
   *
   * <p>Es lo correcto en un TestBed —el elemento sabe en qué documento vive— y de paso
   * evita el `document` global, que el hook de convenciones prohíbe en el frontend por el
   * renderizado en servidor.
   */
  const enfocado = () => fixture.nativeElement.ownerDocument.activeElement;

  const boton = (nombre: string): HTMLButtonElement | null =>
    Array.from(fixture.nativeElement.querySelectorAll('button')).find((elemento) =>
      (elemento as HTMLButtonElement).textContent?.includes(nombre),
    ) as HTMLButtonElement | null;

  const abrir = () => {
    boton('Revocar el sello')?.click();
    fixture.detectChanges();
  };

  const elegir = (valor: string) => {
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    select.value = valor;
    select.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  };

  it('no pinta nada del formulario hasta que se pulsa la accion', () => {
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
  });

  /** Criterio 4 y 12: sin motivo no se confirma, y se dice por qué. */
  it('no emite nada sin motivo, y lo explica junto al campo', () => {
    abrir();
    boton('Confirmar')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.recibido()).toBeNull();

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');
    expect(aviso).not.toBeNull();
    expect(fixture.nativeElement.querySelector('select')?.getAttribute('aria-invalid')).toBe(
      'true',
    );
  });

  it('emite el motivo elegido y la nota escrita', () => {
    abrir();
    elegir('HOLDER_REQUEST');

    const nota = fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    nota.value = '  Lo pidio por correo  ';
    nota.dispatchEvent(new Event('input'));
    fixture.detectChanges();

    boton('Confirmar')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.recibido()).toEqual({
      motivo: 'HOLDER_REQUEST',
      nota: 'Lo pidio por correo',
    });
  });

  /** Una nota en blanco es no haber escrito nota, no una nota de espacios. */
  it('manda la nota en nulo cuando no se escribio nada', () => {
    abrir();
    elegir('HOLDER_REQUEST');
    boton('Confirmar')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.recibido()?.nota).toBeNull();
  });

  /** Criterio 19: el foco entra al panel al abrirlo. */
  it('lleva el foco al panel al abrirlo', () => {
    abrir();

    const panel = fixture.nativeElement.querySelector('[role="group"]');
    expect(enfocado()).toBe(panel);
  });

  /** Criterio 19: Escape cierra sin ejecutar nada y devuelve el foco. */
  it('se cierra con Escape sin emitir, y devuelve el foco al disparador', () => {
    abrir();
    elegir('HOLDER_REQUEST');

    const panel = fixture.nativeElement.querySelector('[role="group"]') as HTMLElement;
    panel.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    expect(fixture.componentInstance.recibido()).toBeNull();
    expect(fixture.nativeElement.querySelector('select')).toBeNull();
    expect(enfocado()).toBe(boton('Revocar el sello'));
  });

  it('cancelar tampoco emite, y vuelve a ofrecer la accion', () => {
    abrir();
    boton('Cancelar')?.click();
    fixture.detectChanges();

    expect(fixture.componentInstance.recibido()).toBeNull();
    expect(boton('Revocar el sello')).not.toBeNull();
  });

  /**
   * Criterio 13: el aviso se lee antes de confirmar.
   *
   * <p>Se comprueba que esté dentro del panel y no en cualquier parte de la pantalla: un
   * aviso que aparece debajo del botón de confirmar llega tarde.
   */
  it('pinta el aviso dentro del panel cuando se le da uno', () => {
    fixture.componentInstance.aviso.set('moderation.undo.seal.listingsStayVisible');
    fixture.detectChanges();
    abrir();

    const panel = fixture.nativeElement.querySelector('[role="group"]') as HTMLElement;
    expect(panel.textContent).toContain('sigue visible');
  });

  it('no pinta aviso cuando no se le da ninguno', () => {
    abrir();

    const panel = fixture.nativeElement.querySelector('[role="group"]') as HTMLElement;
    expect(panel.querySelector('.deshacer__aviso')).toBeNull();
  });
});
