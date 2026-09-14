import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import type { OriginAddress } from '../domain/origin-address';
import { OriginAddressPage } from './origin-address-page';

/**
 * La dirección de origen. HU-017.
 *
 * <p>La sesión se abre <strong>después</strong> de crear el componente, como en la libreta:
 * en una carga de página el componente nace primero y la sesión llega luego.
 */
describe('OriginAddressPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const origen = (campos: Partial<OriginAddress> = {}): OriginAddress => ({
    departmentCode: '11',
    departmentName: 'Bogotá, D.C.',
    municipalityCode: '11001',
    municipalityName: 'Bogotá, D.C.',
    municipalityActive: true,
    line: 'Carrera 15 # 93-47',
    complement: 'Local 3',
    instructions: null,
    postalCode: null,
    senderName: 'Ana María',
    senderPhone: '3001234567',
    savedAt: '2026-09-14T10:00:00Z',
    ...campos,
  });

  const sesion: Session = {
    accessToken: 'un-token',
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana María',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  const bombear = async (fixture: ComponentFixture<OriginAddressPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const botonLlamado = (fixture: ComponentFixture<OriginAddressPage>, nombre: string) =>
    Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((boton) => (boton.getAttribute('aria-label') ?? boton.textContent?.trim()) === nombre) ??
    null;

  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(OriginAddressPage);
    const backend = TestBed.inject(HttpTestingController);
    const almacen = TestBed.inject(SessionStore);

    fixture.detectChanges();
    await bombear(fixture);

    if (conSesion) {
      almacen.set(sesion);
    } else {
      almacen.clear();
    }
    await bombear(fixture);

    return { fixture, backend };
  };

  /** Dos lecturas: el origen y el remitente. Sin origen, el servidor responde 204. */
  const responder = async (
    fixture: ComponentFixture<OriginAddressPage>,
    backend: HttpTestingController,
    actual: OriginAddress | null,
    telefono: string | null = '3001234567',
  ) => {
    const lectura = backend.expectOne(
      (llamada) => llamada.url === `${API}/users/me/origin-address`,
    );
    if (actual === null) {
      lectura.flush(null, { status: 204, statusText: 'No Content' });
    } else {
      lectura.flush(actual);
    }
    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me`)
      .flush({ displayName: 'Ana María', phone: telefono });
    await bombear(fixture);
  };

  it('enseña el origen con el municipio, su departamento y el remitente', async () => {
    const { fixture, backend } = await montar(true);
    await responder(
      fixture,
      backend,
      origen({ municipalityName: 'Santa María', departmentName: 'Huila' }),
    );

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Carrera 15 # 93-47');
    expect(texto).toContain('Local 3');
    expect(texto).toContain('Santa María, Huila');
    expect(texto).toContain('Ana María');
    expect(texto).toContain('3001234567');
  });

  /** Criterio 1: sin origen el servidor responde 204, y el vacío es una pantalla. */
  it('enseña el estado vacío cuando el servidor responde 204', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, null);

    expect(fixture.nativeElement.textContent).toContain('Todavía no tienes dirección de origen');
    expect(botonLlamado(fixture, 'Agregar mi dirección de origen')).not.toBeNull();
  });

  /** Criterio 24: el vacío no afirma que el origen ya sirva para nada. */
  it('habla en futuro de lo que el origen servirá', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, null);

    expect(fixture.nativeElement.textContent).toContain('servirá');
  });

  /** Criterio 14: se explica y se ofrece entrar; no se redirige. */
  it('sin sesión ofrece entrar y no pide nada', async () => {
    const { fixture } = await montar(false);

    expect(fixture.nativeElement.textContent).toContain('Entra a tu cuenta');
  });

  /** Criterio 13: el municipio se suprimió; el origen se lee igual y se avisa. */
  it('avisa cuando el municipio ya no está en la lista oficial', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, origen({ municipalityActive: false }));

    expect(fixture.nativeElement.textContent).toContain('ya no está en la lista oficial');
    expect(fixture.nativeElement.textContent).toContain('Carrera 15 # 93-47');
  });

  it('ofrece reintentar cuando la lectura falla', async () => {
    const { fixture, backend } = await montar(true);

    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/origin-address`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me`)
      .flush({ displayName: 'Ana María', phone: '3001234567' });
    await bombear(fixture);

    expect(botonLlamado(fixture, 'Reintentar')).not.toBeNull();
  });

  /** Criterio 9: quitar manda el DELETE, deja el vacío y lo dice. */
  it('quita el origen y lo dice', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, origen());

    botonLlamado(fixture, 'Quitar')?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) =>
          llamada.url === `${API}/users/me/origin-address` && llamada.method === 'DELETE',
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Dirección de origen quitada');
    expect(fixture.nativeElement.textContent).toContain('Todavía no tienes dirección de origen');
    // El boton pulsado ya no existe: el foco vuelve al encabezado.
    expect(document.activeElement?.tagName).toBe('H1');
  });

  /** Abrir y cerrar el formulario mueven el foco: al h2 al abrir, al h1 al cancelar. */
  it('devuelve el foco al encabezado al cancelar el formulario', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, null);

    botonLlamado(fixture, 'Agregar mi dirección de origen')?.click();
    await bombear(fixture);
    backend
      .match((llamada) => llamada.url === `${API}/locations/departments`)
      .forEach((llamada) => llamada.flush({ departments: [] }));
    await bombear(fixture);
    expect(document.activeElement?.tagName).toBe('H2');

    botonLlamado(fixture, 'Cancelar')?.click();
    await bombear(fixture);

    expect(document.activeElement?.tagName).toBe('H1');
  });

  /** Un fallo al quitar se dice y la tarjeta se queda. */
  it('dice que no pudo quitar y conserva la tarjeta', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, origen());

    botonLlamado(fixture, 'Quitar')?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'DELETE')
      .flush({ code: 'AUTH_SESSION_INVALID' }, { status: 401, statusText: 'Unauthorized' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Tu sesión terminó');
    expect(fixture.nativeElement.textContent).toContain('Carrera 15 # 93-47');
  });
});
