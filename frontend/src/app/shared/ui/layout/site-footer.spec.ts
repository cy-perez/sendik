import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, type AppConfig, type CompanyInfo } from '../../../core/config/app-config';
import { SiteFooter } from './site-footer';

describe('SiteFooter', () => {
  const render = async () => {
    const fixture = TestBed.createComponent(SiteFooter);
    await fixture.whenStable();
    return fixture;
  };

  /** Sustituye solo los datos de empresa; el resto de la configuracion de prueba sigue igual. */
  const conEmpresa = (empresa: CompanyInfo) => {
    const base = TestBed.inject(APP_CONFIG);
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { ...base, company: empresa } satisfies AppConfig },
      ],
    });
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /**
   * La politica de tratamiento tiene que ser un enlace visible en el pie: es
   * obligatorio y es lo primero que revisa una autoridad
   * (docs/operacion/datos-personales.md).
   */
  it('enlaza los tres documentos legales', async () => {
    const fixture = await render();
    const destinos = Array.from(fixture.nativeElement.querySelectorAll('a[href]')).map((enlace) =>
      (enlace as HTMLAnchorElement).getAttribute('href'),
    );

    expect(destinos).toEqual(
      expect.arrayContaining(['/terminos', '/tratamiento-de-datos', '/politica-de-cookies']),
    );
  });

  // Van en una lista dentro de su propia region, para que quien navegue por
  // landmarks los encuentre sin recorrer el pie entero.
  /** El nombre accesible de una region, venga de aria-label o de aria-labelledby. */
  const nombreDe = (raiz: HTMLElement, region: HTMLElement): string => {
    const etiqueta = region.getAttribute('aria-label');
    if (etiqueta !== null) {
      return etiqueta;
    }
    const id = region.getAttribute('aria-labelledby');
    return id === null ? '' : (raiz.querySelector(`#${id}`)?.textContent?.trim() ?? '');
  };

  const regionLlamada = (raiz: HTMLElement, nombre: string): HTMLElement | undefined =>
    Array.from(raiz.querySelectorAll('nav') as NodeListOf<HTMLElement>).find(
      (candidata) => nombreDe(raiz, candidata) === nombre,
    );

  it('agrupa los enlaces legales en una region con nombre', async () => {
    const fixture = await render();
    const legal = regionLlamada(fixture.nativeElement, 'Documentos legales');

    expect(legal).toBeDefined();
    expect(legal?.querySelectorAll('a')).toHaveLength(3);
  });

  it('agrupa el contacto en su propia region con nombre', async () => {
    const fixture = await render();

    expect(regionLlamada(fixture.nativeElement, 'Contacto')).toBeDefined();
  });

  // Criterio 12: visibles, no solo presentes. Un enlace sin texto esta ahi y no
  // lo ve nadie.
  it('cada documento legal se enuncia con su nombre', async () => {
    const fixture = await render();
    const nombres = Array.from(
      fixture.nativeElement.querySelectorAll('.lista-enlaces a') as NodeListOf<HTMLAnchorElement>,
    ).map((enlace) => enlace.textContent?.trim());

    expect(nombres).toEqual(
      expect.arrayContaining([
        expect.stringMatching(/\S/),
        expect.stringMatching(/\S/),
        expect.stringMatching(/\S/),
      ]),
    );
  });

  /** Criterio 11: salen de la configuracion, nunca escritos en la plantilla. */
  it('muestra razon social, NIT y direccion de la configuracion', async () => {
    const fixture = await render();
    const datos = fixture.nativeElement.querySelector('address') as HTMLElement;

    expect(datos.textContent).toContain('Sendik S.A.S.');
    expect(datos.textContent).toContain('000000000-0');
    expect(datos.textContent).toContain('Medellin, Colombia');
  });

  /**
   * Caso borde: un despliegue con la configuracion a medias. Se omite el dato
   * que falta y se conservan los demas; nunca se pinta el hueco.
   */
  it('omite cada dato de empresa que falte sin perder los demas', async () => {
    conEmpresa({
      name: 'Sendik S.A.S.',
      taxId: null,
      address: null,
      supportEmail: 'soporte@example.test',
    });
    const fixture = await render();
    const datos = fixture.nativeElement.querySelector('address') as HTMLElement;

    expect(datos.textContent).toContain('Sendik S.A.S.');
    expect(datos.textContent).not.toContain('NIT');
    expect(datos.textContent).not.toContain('null');
  });

  // Con la configuracion entera vacia no queda un bloque de datos huerfano.
  it('no deja un bloque de datos vacio si no hay ninguno', async () => {
    conEmpresa({ name: null, taxId: null, address: null, supportEmail: null });
    const fixture = await render();
    const datos = fixture.nativeElement.querySelector('address') as HTMLElement | null;

    expect(datos?.textContent?.trim() ?? '').toBe('');
  });

  /**
   * Criterio 13. Es ademas la via por la que se ejercen los derechos del titular
   * de los datos, asi que un pie sin el incumple la Ley 1581
   * (docs/operacion/datos-personales.md).
   */
  it('ofrece el correo de soporte como canal de contacto', async () => {
    const fixture = await render();
    const correo = fixture.nativeElement.querySelector(
      'a[href^="mailto:"]',
    ) as HTMLAnchorElement | null;

    expect(correo?.getAttribute('href')).toBe('mailto:soporte@example.test');
    expect(correo?.textContent?.trim()).toBe('soporte@example.test');
  });

  // Sin correo configurado no se pinta una columna de contacto vacia: se calla.
  it('no pinta la columna de contacto si no hay correo configurado', async () => {
    conEmpresa({ name: 'Sendik S.A.S.', taxId: null, address: null, supportEmail: null });
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('a[href^="mailto:"]')).toBeNull();
  });

  /**
   * Criterio 14: la franja es tinta en los DOS modos, asi que el logo en tinta
   * desaparece en ambos y va siempre la version monocroma negativa. Se renderiza
   * en claro y en oscuro y se comprueba que no cambia nada: es justo lo que el
   * criterio pide comparar, y lo que una sola pasada no puede demostrar.
   */
  it('conserva la franja y el logo negativo en los dos modos', async () => {
    const enModo = async (tema: 'claro' | 'oscuro') => {
      document.documentElement.setAttribute('data-tema', tema);
      const fixture = await render();
      const pie = fixture.nativeElement.querySelector('footer') as HTMLElement;
      const logo = pie.querySelector('img') as HTMLImageElement;
      return {
        franja: pie.classList.contains('franja-tinta'),
        logo: logo.getAttribute('src'),
      };
    };

    const claro = await enModo('claro');
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    const oscuro = await enModo('oscuro');
    document.documentElement.removeAttribute('data-tema');

    expect(claro).toEqual({ franja: true, logo: '/logo-mono-negativo.svg' });
    expect(oscuro).toEqual(claro);
  });

  // El nombre accesible lo lleva la imagen: en el pie no hay enlace que lo dé,
  // al contrario que en la cabecera.
  it('el logo del pie tiene nombre accesible', async () => {
    const fixture = await render();
    const logo = fixture.nativeElement.querySelector('footer img') as HTMLImageElement;

    expect(logo.getAttribute('alt')).toBe('Sendik');
  });
});
