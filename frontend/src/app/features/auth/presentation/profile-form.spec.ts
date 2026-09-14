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
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { ProfileForm } from './profile-form';

/** Criterio 21 de HU-001: el perfil que se edita de una vez. */
describe('ProfileForm', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const PERFIL = {
    email: 'ana@correo.co',
    emailVerified: true,
    displayName: 'Ana Maria',
    city: 'Medellin',
    phone: '3001234567',
  };

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  const montar = async (perfil: object = PERFIL) => {
    const fixture = TestBed.createComponent(ProfileForm);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me`)
      .flush(perfil);
    await asentar(fixture);

    return { fixture, backend };
  };

  const campo = (fixture: { nativeElement: HTMLElement }, id: string) =>
    fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;

  const escribir = (fixture: { nativeElement: HTMLElement }, id: string, valor: string) => {
    const control = campo(fixture, id);
    control.value = valor;
    control.dispatchEvent(new Event('input'));
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) =>
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();

  beforeEach(() => {
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
    TestBed.inject(SessionStore).set(SESION);
  });

  it('llega con los datos que ya estaban puestos criterio_21', async () => {
    const { fixture } = await montar();

    expect(campo(fixture, 'nombre-visible').value).toBe('Ana Maria');
    expect(campo(fixture, 'ciudad').value).toBe('Medellin');
    expect(campo(fixture, 'telefono').value).toBe('3001234567');
  });

  // Nulo y vacio son lo mismo para quien mira el formulario: no hay dato.
  it('deja vacios los campos que no tienen dato criterio_21', async () => {
    const { fixture } = await montar({ ...PERFIL, city: null, phone: null });

    expect(campo(fixture, 'ciudad').value).toBe('');
    expect(campo(fixture, 'telefono').value).toBe('');
  });

  /**
   * HU-017, criterio 11: con direccion de origen la ciudad viene del municipio, se pinta
   * como texto con enlace al origen y no como campo, y al guardar viaja nula.
   */
  it('pinta la ciudad derivada del origen como texto con enlace y la manda nula', async () => {
    const { fixture, backend } = await montar({
      ...PERFIL,
      city: 'Medellín, Antioquia',
      cityEditable: false,
    });

    expect(campo(fixture, 'ciudad')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Medellín, Antioquia');
    expect(fixture.nativeElement.textContent).toContain('Viene de tu dirección de origen');
    expect(fixture.nativeElement.querySelector('a[href="/mi-direccion-de-origen"]')).not.toBeNull();

    escribir(fixture, 'nombre-visible', 'Ana');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`,
    );
    expect(peticion.request.body).toEqual({
      displayName: 'Ana',
      city: null,
      phone: '3001234567',
    });
  });

  it('guarda lo escrito criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'nombre-visible', 'Ana');
    escribir(fixture, 'ciudad', 'Cali');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`,
    );
    expect(peticion.request.body).toEqual({
      displayName: 'Ana',
      city: 'Cali',
      phone: '3001234567',
    });
  });

  /**
   * Vaciar un campo es quitar el dato. Si viajara como cadena vacia, el servidor
   * guardaria una ciudad en blanco y no habria forma de borrarla.
   */
  it('manda ausencia cuando un campo opcional se vacia criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'ciudad', '   ');
    escribir(fixture, 'telefono', '');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`,
    );
    expect(peticion.request.body).toEqual({
      displayName: 'Ana Maria',
      city: null,
      phone: null,
    });
  });

  it('no envia nada si el telefono no lo parece criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'telefono', 'no-es-numero');
    enviar(fixture);
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.method === 'PUT');
    expect(campo(fixture, 'telefono').getAttribute('aria-invalid')).toBe('true');
  });

  it('no envia nada si el nombre queda vacio criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'nombre-visible', ' ');
    enviar(fixture);
    await asentar(fixture);

    backend.expectNone((enviada) => enviada.method === 'PUT');
    expect(campo(fixture, 'nombre-visible').getAttribute('aria-invalid')).toBe('true');
  });

  /**
   * Se pinta lo que devolvio el servidor, no lo que se escribio: el telefono
   * entra con separadores y sale normalizado, y el formulario tiene que mostrar
   * lo que de verdad quedo guardado.
   */
  it('muestra el telefono ya normalizado por el servidor criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'telefono', '+57 300 123 4567');
    enviar(fixture);
    await fixture.whenStable();

    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`)
      .flush({ ...PERFIL, phone: '+573001234567' });
    await asentar(fixture);

    expect(campo(fixture, 'telefono').value).toBe('+573001234567');
  });

  // Lo que se ve en la cabecera sale de la sesion en memoria: dejarlo con el
  // nombre anterior haria dudar de si se guardo.
  it('actualiza el nombre de la sesion al guardar criterio_21', async () => {
    const { fixture, backend } = await montar();

    escribir(fixture, 'nombre-visible', 'Ana');
    enviar(fixture);
    await fixture.whenStable();

    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`)
      .flush({ ...PERFIL, displayName: 'Ana' });
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).user()?.displayName).toBe('Ana');
  });

  // El servidor manda un codigo, nunca texto para mostrar.
  it('traduce el codigo de error del servidor', async () => {
    const { fixture, backend } = await montar();

    enviar(fixture);
    await fixture.whenStable();

    backend
      .expectOne((enviada) => enviada.method === 'PUT' && enviada.url === `${API}/users/me`)
      .flush(
        { code: 'COMMON_VALIDATION_FAILED', title: 'da igual', traceId: 'x' },
        { status: 400, statusText: 'Bad Request' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]');
    expect(aviso?.textContent).toContain('Revisa los datos del formulario');
  });
});
