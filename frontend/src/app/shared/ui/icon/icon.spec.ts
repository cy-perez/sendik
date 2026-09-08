import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { LucideSearch } from '@lucide/angular';
import { beforeEach, describe, expect, it } from 'vitest';

import { Icon } from './icon';

@Component({
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <sendik-icon [icon]="buscar" [label]="etiqueta()" [large]="grande()" [filled]="relleno()" />
  `,
})
class Anfitrion {
  readonly buscar = LucideSearch.icon;
  readonly etiqueta = signal<string | null>(null);
  readonly grande = signal(false);
  readonly relleno = signal(false);
}

describe('Icon', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const svg = () => fixture.nativeElement.querySelector('svg') as SVGElement;

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
   * La razon de que este envoltorio exista. Lucide dibuja con terminaciones
   * redondeadas; el sistema las quiere rectas. Si esta clase deja de aplicarse,
   * los iconos siguen viendose —solo que redondeados— y ninguna otra prueba lo
   * nota.
   */
  it('impone la geometria del sistema y no ofrece forma de quitarla', () => {
    expect(svg().getAttribute('class')).toContain('icon-base');
  });

  it('es decorativo mientras no tenga nombre accesible', () => {
    expect(svg().getAttribute('aria-hidden')).toBe('true');
    expect(svg().getAttribute('role')).toBeNull();
    expect(svg().getAttribute('aria-label')).toBeNull();
  });

  /**
   * Un icono con nombre deja de estar oculto Y pasa a ser una imagen. Las dos
   * cosas o ninguna: con aria-label pero aria-hidden puesto, el nombre no se
   * anuncia y el icono queda mudo, que es el fallo silencioso de siempre.
   */
  it('con nombre accesible se anuncia como imagen y deja de estar oculto', async () => {
    fixture.componentInstance.etiqueta.set('Buscar');
    await render();

    expect(svg().getAttribute('aria-hidden')).toBeNull();
    expect(svg().getAttribute('role')).toBe('img');
    expect(svg().getAttribute('aria-label')).toBe('Buscar');
  });

  it('aplica las dos unicas variantes que existen', async () => {
    fixture.componentInstance.grande.set(true);
    fixture.componentInstance.relleno.set(true);
    await render();

    const clases = svg().getAttribute('class') ?? '';
    expect(clases).toContain('icon-lg');
    expect(clases).toContain('icon-filled');
  });
});
