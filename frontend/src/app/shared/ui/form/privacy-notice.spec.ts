import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, type AppConfig, type CompanyInfo } from '../../../core/config/app-config';
import { PrivacyNotice } from './privacy-notice';

/**
 * El aviso de privacidad no es la politica: es lo que se entrega **donde se pide el
 * dato**. Lo que se prueba aqui es que diga las cuatro cosas que tiene que decir y
 * que la variante sensible diga las dos de mas, porque un aviso al que le falta una
 * de ellas no informa, y un tratamiento informado a medias no esta autorizado.
 */
describe('PrivacyNotice', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /** Sustituye solo los datos de empresa; el resto de la configuracion sigue igual. */
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

  const render = async (variante?: 'cuenta' | 'verificacion') => {
    const fixture = TestBed.createComponent(PrivacyNotice);
    if (variante) {
      fixture.componentRef.setInput('variante', variante);
    }
    await fixture.whenStable();
    return fixture;
  };

  it('dice quien es el responsable, como identificarlo y donde esta', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Sendik S.A.S.');
    expect(fixture.nativeElement.textContent).toContain('000000000-0');
    expect(fixture.nativeElement.textContent).toContain('Medellin, Colombia');
  });

  /**
   * La direccion sale de `COMPANY_ADDRESS` y no del archivo de traduccion, que es
   * donde estaba escrita a mano. Se comprueba con un valor distinto del de la
   * configuracion de prueba: si la plantilla volviera a llevarla dentro, esta
   * prueba seguiria viendo la ciudad vieja y no la nueva.
   */
  it('toma la direccion de la configuracion y no del texto', async () => {
    conEmpresa({
      name: 'Sendik S.A.S.',
      taxId: '000000000-0',
      address: 'Cra. 1 # 2-3. Cali, Colombia',
      supportEmail: 'soporte@example.test',
    });

    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Cra. 1 # 2-3. Cali, Colombia');
    expect(fixture.nativeElement.textContent).not.toContain('Medellin');
  });

  /** Falta el dato, falta la frase: nunca una etiqueta con el hueco vacio detras. */
  it('omite la direccion si no hay ninguna configurada', async () => {
    conEmpresa({
      name: 'Sendik S.A.S.',
      taxId: '000000000-0',
      address: null,
      supportEmail: 'soporte@example.test',
    });

    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Sendik S.A.S.');
    expect(fixture.nativeElement.textContent).not.toContain('Direcci');
  });

  it('dice para que se usan los datos y por donde se ejercen los derechos', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('crear y administrar tu cuenta');
    expect(fixture.nativeElement.textContent).toContain('soporte@example.test');
  });

  it('enlaza la politica completa', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelector('a')?.getAttribute('href')).toBe(
      '/tratamiento-de-datos',
    );
  });

  /**
   * <strong>La que justifica que haya dos variantes.</strong> En la verificacion se
   * piden documento, rostro y cuenta bancaria, y ahi hay que decir expresamente que
   * nadie esta obligado a darlos. Con el aviso de la cuenta esa frase no aparece, y
   * usar el flojo donde toca el otro es el fallo que esta prueba cierra.
   */
  it('avisa de que los datos sensibles no son obligatorios, solo al pedirlos', async () => {
    const cuenta = await render('cuenta');
    expect(cuenta.nativeElement.textContent).not.toContain('No estás obligado');

    const verificacion = await render('verificacion');
    expect(verificacion.nativeElement.textContent).toContain('No estás obligado');
    expect(verificacion.nativeElement.textContent).toContain('confirmar que eres quien dices ser');
  });

  /**
   * Los datos del responsable son configuracion y pueden faltar. Falta el dato,
   * falta la linea; lo que no puede es inventarse un valor ni dejar la plantilla
   * escribiendo "null" dentro de un aviso legal.
   */
  it('omite la linea del responsable si no hay con que escribirla', async () => {
    conEmpresa({ name: null, taxId: null, address: null, supportEmail: null });

    const fixture = await render('cuenta');

    expect(fixture.nativeElement.textContent).not.toContain('null');
    expect(fixture.nativeElement.textContent).toContain('crear y administrar tu cuenta');
  });
});
