import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { RegisterPage } from './register-page';

/**
 * Se consulta por rol y por texto accesible: es como encuentra los controles
 * quien usa el sitio, y no se rompe al maquetar.
 */
describe('RegisterPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const render = async () => {
    const fixture = TestBed.createComponent(RegisterPage);
    await fixture.whenStable();
    return fixture;
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, id: string, valor: string) => {
    const campo = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  const marcar = (fixture: { nativeElement: HTMLElement }, id: string) => {
    const casilla = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    casilla.click();
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();
  };

  /**
   * La mutacion resuelve en una promesa y solo despues actualiza sus senales.
   * whenStable no basta: hay que dejar correr la cola de microtareas primero.
   */
  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();
    await fixture.whenStable();
  };

  const rellenarTodoBien = (fixture: { nativeElement: HTMLElement }) => {
    escribir(fixture, 'correo', 'ana@correo.co');
    escribir(fixture, 'nombre', 'Ana Maria');
    escribir(fixture, 'contrasena', 'una contrasena larga');
    escribir(fixture, 'nacimiento', '1990-03-04');
    marcar(fixture, 'terminos');
    marcar(fixture, 'privacidad');
  };

  beforeEach(() => {
    // Con la cadena real de interceptores, no solo con HttpClient pelado: sin
    // ella la prueba pasaria aunque el prefijo de la API o el mapeo del codigo
    // de error estuvieran rotos, que es justo lo que aqui hay que detectar.
    // Se declaran en este TestBed y no en test-providers.ts porque importarlos
    // desde alli arrastra @angular/common al arranque de toda la suite.
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  it('presenta los campos con su etiqueta asociada', async () => {
    const fixture = await render();
    const html = fixture.nativeElement as HTMLElement;

    for (const id of ['correo', 'nombre', 'contrasena', 'nacimiento']) {
      expect(html.querySelector(`label[for="${id}"]`), `falta la etiqueta de ${id}`).not.toBeNull();
    }
  });

  // Dos casillas separadas: una sola para ambos documentos no es consentimiento
  // valido segun la Ley 1581 (docs/operacion/datos-personales.md).
  it('pide los dos consentimientos por separado', async () => {
    const fixture = await render();
    const casillas = fixture.nativeElement.querySelectorAll('input[type="checkbox"]');

    expect(casillas).toHaveLength(2);
    expect(fixture.nativeElement.querySelector('label[for="terminos"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('label[for="privacidad"]')).not.toBeNull();
  });

  /**
   * Un consentimiento tiene que ser informado para ser valido: sin enlace, la
   * persona acepta un documento que no puede leer, y la evidencia que se guarda
   * de esa aceptacion no vale (docs/operacion/datos-personales.md).
   */
  it('enlaza cada casilla con el documento que acepta', async () => {
    const fixture = await render();
    const enlaces = Array.from(
      fixture.nativeElement.querySelectorAll('a[href]'),
    ) as HTMLAnchorElement[];
    const destinos = enlaces.map((enlace) => enlace.getAttribute('href'));

    expect(destinos).toContain('/terminos');
    expect(destinos).toContain('/tratamiento-de-datos');
  });

  /**
   * En pestana nueva para no perder el formulario a medio llenar, y con rel: sin
   * noopener la pagina abierta puede reescribir esta desde window.opener.
   */
  it('abre los documentos sin perder el formulario ni exponer la pestana', async () => {
    const fixture = await render();
    const enlace = fixture.nativeElement.querySelector('a[href="/terminos"]') as HTMLAnchorElement;

    expect(enlace.getAttribute('target')).toBe('_blank');
    expect(enlace.getAttribute('rel')).toContain('noopener');
    // Que cambia de contexto no puede verse solo: se anuncia.
    expect(enlace.textContent).toContain('pestaña nueva');
  });

  /**
   * El enlace va fuera de la etiqueta. Dentro, pulsarlo marcaria la casilla
   * ademas de abrir el documento: se aceptaria sin haber leido, con un gesto.
   */
  it('no marca la casilla al abrir el documento', async () => {
    const fixture = await render();
    const enlace = fixture.nativeElement.querySelector('a[href="/terminos"]') as HTMLAnchorElement;

    expect(enlace.closest('label')).toBeNull();
  });

  it('no muestra errores antes del primer intento de envio', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelectorAll('[role="alert"]')).toHaveLength(0);
  });

  it('marca cada campo invalido al enviar vacio y no llama a la API', async () => {
    const fixture = await render();

    enviar(fixture);
    await fixture.whenStable();

    const invalidos = fixture.nativeElement.querySelectorAll('[aria-invalid="true"]');
    expect(invalidos.length).toBeGreaterThanOrEqual(4);
    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/register`);
  });

  /**
   * Son seis campos y el foco se queda en el boton, al final. Sin moverlo, la
   * persona pulsa y no ve que haya pasado nada.
   */
  it('lleva el foco al primer campo con error', async () => {
    const fixture = await render();

    enviar(fixture);
    await asentar(fixture);

    expect(document.activeElement?.id).toBe('correo');
  });

  // El orden es el del formulario: se lleva al primero que haya que corregir.
  it('se salta los campos que ya estan bien', async () => {
    const fixture = await render();
    escribir(fixture, 'correo', 'ana@correo.co');
    escribir(fixture, 'nombre', 'Ana Maria');

    enviar(fixture);
    await asentar(fixture);

    expect(document.activeElement?.id).toBe('contrasena');
  });

  it('dice cual de los dos consentimientos falta, no un mensaje generico', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    marcar(fixture, 'privacidad'); // se desmarca
    enviar(fixture);
    await fixture.whenStable();

    const privacidad = fixture.nativeElement.querySelector('#privacidad') as HTMLInputElement;
    const terminos = fixture.nativeElement.querySelector('#terminos') as HTMLInputElement;

    expect(privacidad.getAttribute('aria-invalid')).toBe('true');
    expect(terminos.getAttribute('aria-invalid')).toBeNull();
  });

  it('rechaza a un menor de edad sin llamar a la API RN-008', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    escribir(fixture, 'nacimiento', '2015-01-01');
    enviar(fixture);
    await fixture.whenStable();

    expect(
      (fixture.nativeElement.querySelector('#nacimiento') as HTMLInputElement).getAttribute(
        'aria-invalid',
      ),
    ).toBe('true');
    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/register`);
  });

  it('rechaza una contrasena corta sin llamar a la API RN-005', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    escribir(fixture, 'contrasena', 'corta');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/register`);
  });

  it('envia el registro cuando todo esta bien', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(`${API}/auth/register`);
    expect(peticion.request.body).toMatchObject({
      email: 'ana@correo.co',
      displayName: 'Ana Maria',
      birthDate: '1990-03-04',
      acceptsTerms: true,
      acceptsPrivacy: true,
    });
  });

  // El servidor responde igual exista o no la cuenta, asi que esta pantalla
  // tampoco puede decir nada distinto (criterio 2 de HU-001).
  it('muestra el mismo aviso de revisa tu correo tras un registro correcto', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/register`)
      .flush(null, { status: 202, statusText: 'Accepted' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Revisa tu correo');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('traduce el codigo de error del servidor en vez de mostrar texto crudo', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/register`)
      .flush(
        { code: 'AUTH_PASSWORD_BREACHED', traceId: 'abc', detail: 'texto interno del servidor' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('.error-general') as HTMLElement;
    expect(aviso.textContent).toContain('filtración');
    expect(fixture.nativeElement.textContent).not.toContain('texto interno del servidor');
  });
});
