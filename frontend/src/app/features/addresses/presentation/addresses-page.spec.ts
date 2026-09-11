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
import type { ShippingAddress } from '../domain/shipping-address';
import { AddressesPage } from './addresses-page';

/**
 * La libreta de direcciones. HU-016.
 *
 * <p>La sesión se abre <strong>después</strong> de crear el componente, nunca antes: en una
 * carga de página el componente nace primero y la sesión llega luego, por la cookie de
 * refresco. Es la diferencia que dejó el perfil de `/mi-cuenta` sin cargarse nunca
 * (frontend/CLAUDE.md).
 */
describe('AddressesPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const direccion = (campos: Partial<ShippingAddress> = {}): ShippingAddress => ({
    id: 'una',
    recipientName: 'Ana María Ruiz',
    phone: '3001234567',
    departmentCode: '11',
    departmentName: 'Bogotá, D.C.',
    municipalityCode: '11001',
    municipalityName: 'Bogotá, D.C.',
    municipalityActive: true,
    line: 'Calle 45 # 12-34',
    complement: null,
    instructions: null,
    postalCode: null,
    isDefault: true,
    savedAt: '2026-09-11T10:00:00Z',
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

  /** Ninguna petición de más: el almacén es de raíz y sin esto pediría desde cualquier página. */
  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
  });

  const bombear = async (fixture: ComponentFixture<AddressesPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /** Por nombre accesible y no por clase de CSS: es lo que ve quien usa la pantalla. */
  const botonLlamado = (fixture: ComponentFixture<AddressesPage>, nombre: string) =>
    Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((boton) => boton.textContent?.trim() === nombre) ?? null;

  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(AddressesPage);
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

  const responder = async (
    fixture: ComponentFixture<AddressesPage>,
    backend: HttpTestingController,
    direcciones: readonly ShippingAddress[],
  ) => {
    // Solo la libreta: los departamentos los pide el formulario, que aqui no esta abierto.
    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/addresses`)
      .flush({ addresses: direcciones });
    await bombear(fixture);
  };

  it('enseña las direcciones que llegan, con el municipio y su departamento', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [
      direccion({ municipalityName: 'Santa María', departmentName: 'Huila' }),
    ]);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Ana María Ruiz');
    expect(texto).toContain('Calle 45 # 12-34');
    // Criterio 24: el municipio nunca se lee solo.
    expect(texto).toContain('Santa María, Huila');
  });

  /** La etiqueta es texto y no un color: el estado no se comunica solo por color. */
  it('marca la predeterminada con una etiqueta de texto', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ isDefault: true })]);

    expect(fixture.nativeElement.textContent).toContain('Predeterminada');
  });

  /**
   * Y no ofrece marcar como predeterminada la que ya lo es.
   *
   * <p>Son dos casos y no uno con dos montajes: el almacén es de raíz, así que un segundo
   * componente en la misma prueba encuentra la libreta ya en caché y no vuelve a pedirla.
   */
  it('no ofrece marcar la que ya es predeterminada', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ isDefault: true })]);

    expect(botonLlamado(fixture, 'Usar como predeterminada')).toBeNull();
  });

  it('sí lo ofrece sobre una que no lo es', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ isDefault: false })]);

    expect(botonLlamado(fixture, 'Usar como predeterminada')).not.toBeNull();
  });

  /** Criterio 1: el vacío es una pantalla y explica para qué sirve. */
  it('enseña el estado vacío cuando no hay ninguna', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, []);

    expect(fixture.nativeElement.textContent).toContain('Todavía no tienes direcciones');
    expect(botonLlamado(fixture, 'Agregar una dirección')).not.toBeNull();
  });

  /** Criterio 17: se explica y se ofrece entrar; no se redirige. */
  it('sin sesión ofrece entrar y no pide nada', async () => {
    const { fixture } = await montar(false);

    expect(fixture.nativeElement.textContent).toContain('Entra a tu cuenta');
    // El `verify()` de afterEach es lo que comprueba que no salió ninguna petición.
  });

  /**
   * Criterio 23: el DANE suprimió ese municipio. La dirección se lee igual y se avisa de
   * que al editarla habrá que elegir otro, antes de que el servidor lo rechace.
   */
  it('avisa cuando el municipio ya no está en la lista oficial', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ municipalityActive: false })]);

    expect(fixture.nativeElement.textContent).toContain('ya no está en la lista oficial');
    // Y la dirección sigue viéndose entera.
    expect(fixture.nativeElement.textContent).toContain('Calle 45 # 12-34');
  });

  it('ofrece reintentar cuando la libreta falla', async () => {
    const fixture = TestBed.createComponent(AddressesPage);
    const backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionStore).set(sesion);
    fixture.detectChanges();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/addresses`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(botonLlamado(fixture, 'Reintentar')).not.toBeNull();
  });

  /**
   * El borrado no estrena un diálogo de confirmación, y por eso el aviso de después es lo
   * que cuenta lo que pasó: quien no lo ve lo oye por la región viva.
   */
  it('quita una dirección y lo dice', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ isDefault: false })]);

    botonLlamado(fixture, 'Quitar')?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) => llamada.url === `${API}/users/me/addresses/una` && llamada.method === 'DELETE',
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/addresses`)
      .flush({ addresses: [] });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Dirección quitada');
  });

  /** Criterio 13: marcar otra manda la petición y vuelve a leer la libreta. */
  it('marca otra como predeterminada', async () => {
    const { fixture, backend } = await montar(true);
    await responder(fixture, backend, [direccion({ isDefault: false })]);

    botonLlamado(fixture, 'Usar como predeterminada')?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) => llamada.url === `${API}/users/me/default-address` && llamada.method === 'PUT',
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.url === `${API}/users/me/addresses`)
      .flush({ addresses: [direccion({ isDefault: true })] });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain(
      'Cambió cuál es tu dirección predeterminada',
    );
  });
});
