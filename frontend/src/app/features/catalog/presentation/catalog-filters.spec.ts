import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';

import { SIN_CRITERIOS, type SearchCriteria } from '../domain/search-criteria';
import { CatalogFilters } from './catalog-filters';

/**
 * El panel de filtros. HU-014, criterios 10 a 14.
 *
 * <p><strong>Por la pantalla y no por la dirección.</strong> La suite de extremo a extremo
 * pone los filtros escribiéndolos en la URL, así que nada tocaba estos controles: se podía
 * borrar la guarda del rango del revés, o hacer que marcar un color sustituyera al anterior
 * en vez de sumarse, y toda la suite seguía verde.
 */
describe('CatalogFilters', () => {
  const render = async (criterios: SearchCriteria = SIN_CRITERIOS) => {
    const fixture = TestBed.createComponent(CatalogFilters);
    fixture.componentRef.setInput('criterios', criterios);
    await fixture.whenStable();
    fixture.detectChanges();

    const emitidos: SearchCriteria[] = [];
    fixture.componentInstance.cambiar.subscribe((criterio) => emitidos.push(criterio));

    // `nativeElement` es `any`, y sobre `any` no se pueden usar argumentos de tipo.
    const raiz: HTMLElement = fixture.nativeElement;

    return { fixture, raiz, emitidos };
  };

  const exigir = <T>(valor: T | null | undefined, que: string): T => {
    if (valor === null || valor === undefined) {
      throw new Error(`No hay ${que} en el panel`);
    }
    return valor;
  };

  /** Los grupos van en orden: condición, color, talla y precio. */
  const casillasDe = (raiz: HTMLElement, grupo: number) =>
    Array.from(
      exigir(
        raiz.querySelectorAll('fieldset')[grupo],
        `el grupo de filtros ${grupo}`,
      ).querySelectorAll<HTMLInputElement>('input[type="checkbox"]'),
    );

  const camposDePrecio = (raiz: HTMLElement) => {
    const campos = raiz.querySelectorAll<HTMLInputElement>('input[type="number"]');
    return {
      desde: exigir(campos[0], 'el campo «desde»'),
      hasta: exigir(campos[1], 'el campo «hasta»'),
    };
  };

  const ultimo = (emitidos: SearchCriteria[]): SearchCriteria =>
    exigir(emitidos.at(-1), 'ningún criterio emitido');

  const marcar = (casilla: HTMLInputElement) => {
    casilla.checked = true;
    casilla.dispatchEvent(new Event('change'));
  };

  it('suma valores dentro de un mismo filtro en vez de sustituirlos, criterio 14', async () => {
    const { fixture, raiz, emitidos } = await render();

    marcar(exigir(casillasDe(raiz, 1)[0], 'la primera casilla de color'));
    expect(ultimo(emitidos).colors).toHaveLength(1);
    const primero = exigir(ultimo(emitidos).colors[0], 'el color marcado');

    // El segundo se marca sobre unos criterios que ya llevan el primero, que es como llega
    // de vuelta por la dirección.
    fixture.componentRef.setInput('criterios', { ...SIN_CRITERIOS, colors: [primero] });
    await fixture.whenStable();
    fixture.detectChanges();

    marcar(exigir(casillasDe(raiz, 1)[1], 'la segunda casilla de color'));

    expect(ultimo(emitidos).colors).toHaveLength(2);
    expect(ultimo(emitidos).colors).toContain(primero);
  });

  it('desmarcar quita solo ese valor y deja los demás', async () => {
    const { raiz, emitidos } = await render({
      ...SIN_CRITERIOS,
      conditions: ['NEW', 'GOOD'],
    });

    const casilla = exigir(
      casillasDe(raiz, 0).find((opcion) => opcion.checked),
      'una casilla de condición marcada',
    );
    casilla.checked = false;
    casilla.dispatchEvent(new Event('change'));

    expect(ultimo(emitidos).conditions).toHaveLength(1);
  });

  it('soltar el sistema de talla se lleva también la talla, RN-087', async () => {
    const { raiz, emitidos } = await render({
      ...SIN_CRITERIOS,
      sizeSystem: 'ALPHA',
      size: 'M',
    });

    const sistema = exigir(raiz.querySelector<HTMLSelectElement>('select'), 'el sistema de talla');
    sistema.value = '';
    sistema.dispatchEvent(new Event('change'));

    expect(ultimo(emitidos).sizeSystem).toBeNull();
    expect(ultimo(emitidos).size).toBeNull();
  });

  it('con el rango del revés no pide nada y lo dice en el panel', async () => {
    const { fixture, raiz, emitidos } = await render();
    const { desde, hasta } = camposDePrecio(raiz);

    desde.value = '100000';
    desde.dispatchEvent(new Event('input'));
    hasta.value = '50000';
    hasta.dispatchEvent(new Event('input'));
    hasta.dispatchEvent(new Event('blur'));
    fixture.detectChanges();

    // Ni una petición: un 400 del servidor llegaría a la pantalla como error de red, que le
    // diría a quien busca que Sendik está caído.
    expect(emitidos).toHaveLength(0);
    expect(raiz.querySelector('#error-de-precio')).not.toBeNull();
    expect(desde.getAttribute('aria-invalid')).toBe('true');
    expect(hasta.getAttribute('aria-invalid')).toBe('true');
  });

  it('aplica el precio al salir del campo y no en cada tecla', async () => {
    const { raiz, emitidos } = await render();
    const { desde } = camposDePrecio(raiz);

    desde.value = '5';
    desde.dispatchEvent(new Event('input'));
    desde.value = '50000';
    desde.dispatchEvent(new Event('input'));

    expect(emitidos).toHaveLength(0);

    desde.dispatchEvent(new Event('blur'));

    expect(emitidos).toHaveLength(1);
    expect(ultimo(emitidos).minPrice).toBe(50000);
  });
});
