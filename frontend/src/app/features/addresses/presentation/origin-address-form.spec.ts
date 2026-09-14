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
import type { OriginAddress, Sender } from '../domain/origin-address';
import { OriginAddressForm } from './origin-address-form';

/** El formulario de la dirección de origen. HU-017, criterios 2 a 8. */
describe('OriginAddressForm', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const sesion: Session = {
    accessToken: 'un-token',
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana María',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  const conTelefono: Sender = { name: 'Ana María', phone: '3001234567' };
  const sinTelefono: Sender = { name: 'Ana María', phone: null };

  const guardado: OriginAddress = {
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

  const bombear = async (fixture: ComponentFixture<OriginAddressForm>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const campo = (fixture: ComponentFixture<OriginAddressForm>, id: string) =>
    fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement | HTMLSelectElement;

  const guardar = async (fixture: ComponentFixture<OriginAddressForm>) => {
    const boton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidato) => candidato.textContent?.includes('Guardar dirección de origen'));

    expect(boton, 'no hay botón de guardar en el formulario').toBeDefined();
    boton?.click();
    await bombear(fixture);
  };

  const escribir = async (
    fixture: ComponentFixture<OriginAddressForm>,
    id: string,
    valor: string,
  ) => {
    const elemento = campo(fixture, id);
    elemento.value = valor;
    elemento.dispatchEvent(new Event('change'));
    elemento.dispatchEvent(new Event('input'));
    await bombear(fixture);
  };

  const montar = async (remitente: Sender, origen: OriginAddress | null = null) => {
    const fixture = TestBed.createComponent(OriginAddressForm);
    const backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionStore).set(sesion);
    fixture.componentRef.setInput('origen', origen);
    fixture.componentRef.setInput('remitente', remitente);

    fixture.detectChanges();
    await bombear(fixture);

    backend
      .match((llamada) => llamada.url === `${API}/locations/departments`)
      .forEach((llamada) =>
        llamada.flush({
          departments: [
            { code: '05', name: 'Antioquia' },
            { code: '11', name: 'Bogotá, D.C.' },
          ],
        }),
      );
    await bombear(fixture);

    return { fixture, backend };
  };

  const responderMunicipios = async (
    fixture: ComponentFixture<OriginAddressForm>,
    backend: HttpTestingController,
    departamento: string,
  ) => {
    backend
      .match(
        (llamada) => llamada.url === `${API}/locations/departments/${departamento}/municipalities`,
      )
      .forEach((llamada) =>
        llamada.flush({ municipalities: [{ code: `${departamento}001`, name: 'Un municipio' }] }),
      );
    await bombear(fixture);
  };

  /** Criterio 4: el remitente se lee del perfil y se cambia allí. */
  it('enseña el remitente de solo lectura con el enlace al perfil', async () => {
    const { fixture } = await montar(conTelefono);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Ana María');
    expect(texto).toContain('3001234567');
    expect(fixture.nativeElement.querySelector('a[href="/mi-cuenta"]')).not.toBeNull();
    // No hay campo para escribirlos.
    expect(fixture.nativeElement.querySelector('#origen-telefono')).toBeNull();
  });

  /** Criterio 5: sin teléfono se dice antes de pulsar, y al pulsar no se manda nada. */
  it('sin teléfono en el perfil lo dice antes y no manda nada al guardar', async () => {
    const { fixture, backend } = await montar(sinTelefono);

    expect(fixture.nativeElement.textContent).toContain('Tu perfil no tiene teléfono');

    await escribir(fixture, 'origen-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'origen-municipio', '11001');
    await escribir(fixture, 'origen-linea', 'Carrera 15 # 93-47');
    await guardar(fixture);

    backend.expectNone((llamada) => llamada.method === 'PUT');
    // El foco va al aviso, que es lo que distingue «no se mando» de «no paso nada».
    expect(document.activeElement?.getAttribute('role')).toBe('alert');
    expect(document.activeElement?.textContent).toContain('no tiene teléfono');
  });

  /** El foco entra al formulario: el botón que lo abrió deja de existir. */
  it('recoge el foco en su encabezado al abrirse', async () => {
    const { fixture } = await montar(conTelefono);
    await bombear(fixture);

    expect(document.activeElement?.tagName).toBe('H2');
  });

  /** Criterio 6: se puebla al elegir departamento y se vacía al cambiarlo. */
  it('puebla los municipios al elegir departamento y vacía el elegido al cambiarlo', async () => {
    const { fixture, backend } = await montar(conTelefono);

    await escribir(fixture, 'origen-departamento', '11');
    await responderMunicipios(fixture, backend, '11');

    const municipio = campo(fixture, 'origen-municipio') as HTMLSelectElement;
    expect(municipio.options.length).toBe(2);
    expect(municipio.options[1]?.textContent).toContain('Un municipio');

    await escribir(fixture, 'origen-municipio', '11001');
    await escribir(fixture, 'origen-departamento', '05');
    await responderMunicipios(fixture, backend, '05');

    expect((campo(fixture, 'origen-municipio') as HTMLSelectElement).value).toBe('');
  });

  /** Criterio 8: el foco va al primer campo con error, en el orden en que se leen. */
  it('lleva el foco al primer campo con error', async () => {
    const { fixture, backend } = await montar(conTelefono);

    await guardar(fixture);
    expect(document.activeElement?.id).toBe('origen-departamento');

    await escribir(fixture, 'origen-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'origen-municipio', '11001');
    await guardar(fixture);

    expect(document.activeElement?.id).toBe('origen-linea');
  });

  /** Criterio 6: el selector de municipio no adivina. */
  it('no ofrece municipios hasta que hay departamento', async () => {
    const { fixture } = await montar(conTelefono);

    const municipio = campo(fixture, 'origen-municipio') as HTMLSelectElement;
    expect(municipio.options.length).toBe(1);
    expect(municipio.options[0]?.textContent).toContain('Elige primero un departamento');
  });

  /** Criterio 8: una entrada por campo y no un aviso general. */
  it('marca cada campo que falta al intentar guardar en blanco', async () => {
    const { fixture, backend } = await montar(conTelefono);

    await guardar(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Elige un departamento');
    expect(texto).toContain('Escribe la dirección');
    backend.expectNone((llamada) => llamada.method === 'PUT');
  });

  it('manda el origen con los opcionales vacíos como ausencia y sin departamento', async () => {
    const { fixture, backend } = await montar(conTelefono);

    await escribir(fixture, 'origen-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'origen-municipio', '11001');
    await escribir(fixture, 'origen-linea', 'Carrera 15 # 93-47');
    await escribir(fixture, 'origen-codigo-postal', '110 221');

    await guardar(fixture);

    const llamada = backend.expectOne(
      (peticion) => peticion.url === `${API}/users/me/origin-address` && peticion.method === 'PUT',
    );
    expect(llamada.request.body).toEqual({
      municipalityCode: '11001',
      line: 'Carrera 15 # 93-47',
      complement: null,
      instructions: null,
      postalCode: '110221',
    });
    llamada.flush(guardado);
  });

  /** Criterio 3: al editar, el formulario llega relleno y el departamento ya elegido. */
  it('llega relleno al editar, con el departamento derivado del municipio', async () => {
    const { fixture, backend } = await montar(conTelefono, guardado);
    await responderMunicipios(fixture, backend, '11');

    expect(campo(fixture, 'origen-departamento').value).toBe('11');
    expect(campo(fixture, 'origen-linea').value).toBe('Carrera 15 # 93-47');
    expect(campo(fixture, 'origen-complemento').value).toBe('Local 3');
  });

  /** Un fallo del servidor se dice y lo escrito se queda. */
  it('conserva lo escrito cuando el servidor rechaza', async () => {
    const { fixture, backend } = await montar(conTelefono);

    await escribir(fixture, 'origen-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'origen-municipio', '11001');
    await escribir(fixture, 'origen-linea', 'Carrera 15 # 93-47');
    await guardar(fixture);

    backend
      .expectOne((peticion) => peticion.method === 'PUT')
      .flush({ code: 'USER_UNKNOWN_MUNICIPALITY' }, { status: 422, statusText: 'Unprocessable' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está en la lista oficial');
    expect(campo(fixture, 'origen-linea').value).toBe('Carrera 15 # 93-47');
  });
});
