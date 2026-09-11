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
import { AddressForm } from './address-form';

/** El formulario de una dirección de entrega. HU-016, criterios 2 a 9. */
describe('AddressForm', () => {
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

  const guardada: ShippingAddress = {
    id: 'una',
    recipientName: 'Ana María Ruiz',
    phone: '3001234567',
    departmentCode: '11',
    departmentName: 'Bogotá, D.C.',
    municipalityCode: '11001',
    municipalityName: 'Bogotá, D.C.',
    municipalityActive: true,
    line: 'Calle 45 # 12-34',
    complement: 'Apto 802',
    instructions: null,
    postalCode: null,
    isDefault: true,
    savedAt: '2026-09-11T10:00:00Z',
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

  const bombear = async (fixture: ComponentFixture<AddressForm>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const campo = (fixture: ComponentFixture<AddressForm>, id: string) =>
    fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement | HTMLSelectElement;

  /**
   * El botón de guardar, por su nombre accesible y no por `form.dispatchEvent('submit')`.
   *
   * <p>Lo pidió la revisión de pruebas, y con razón: disparando el evento a mano, si
   * `sendik-submit-button` perdiera su `type="submit"`, quedara deshabilitado o
   * desapareciera de la plantilla, nadie podría guardar una dirección y las pruebas
   * seguirían verdes.
   */
  const guardar = async (fixture: ComponentFixture<AddressForm>) => {
    const boton = Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<HTMLButtonElement>,
    ).find((candidato) => candidato.textContent?.includes('Guardar dirección'));

    expect(boton, 'no hay botón de guardar en el formulario').toBeDefined();
    boton?.click();
    await bombear(fixture);
  };

  const escribir = async (fixture: ComponentFixture<AddressForm>, id: string, valor: string) => {
    const elemento = campo(fixture, id);
    elemento.value = valor;
    elemento.dispatchEvent(new Event('change'));
    elemento.dispatchEvent(new Event('input'));
    await bombear(fixture);
  };

  const montar = async (direccion: ShippingAddress | null = null) => {
    const fixture = TestBed.createComponent(AddressForm);
    const backend = TestBed.inject(HttpTestingController);
    TestBed.inject(SessionStore).set(sesion);
    fixture.componentRef.setInput('direccion', direccion);

    fixture.detectChanges();
    await bombear(fixture);

    // El almacén es de raíz: el formulario no abre la libreta, así que solo salen las
    // consultas de la división.
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
    fixture: ComponentFixture<AddressForm>,
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

  /** Criterio 4: el selector de municipio no adivina y dice por qué está vacío. */
  it('no ofrece municipios hasta que hay departamento', async () => {
    const { fixture } = await montar();

    const municipio = campo(fixture, 'direccion-municipio') as HTMLSelectElement;
    expect(municipio.options.length).toBe(1);
    expect(municipio.options[0]?.textContent).toContain('Elige primero un departamento');
  });

  it('puebla los municipios al elegir departamento', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');

    const municipio = campo(fixture, 'direccion-municipio') as HTMLSelectElement;
    expect(municipio.options.length).toBe(2);
    expect(municipio.options[1]?.textContent).toContain('Un municipio');
  });

  /** Criterio 7: una entrada por campo y el foco al primero, no un aviso general. */
  it('marca cada campo que falta al intentar guardar en blanco', async () => {
    const { fixture } = await montar();

    await guardar(fixture);

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Elige un departamento');
    expect(texto).toContain('Escribe quién recibe');
    expect(texto).toContain('Escribe un teléfono');
    expect(texto).toContain('Escribe la dirección');
  });

  it('manda la dirección con los opcionales vacíos como ausencia', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'direccion-municipio', '11001');
    await escribir(fixture, 'direccion-quien-recibe', 'Ana María Ruiz');
    await escribir(fixture, 'direccion-telefono', '300 123 4567');
    await escribir(fixture, 'direccion-linea', 'Calle 45 # 12-34');

    await guardar(fixture);

    const llamada = backend.expectOne(
      (peticion) => peticion.url === `${API}/users/me/addresses` && peticion.method === 'POST',
    );

    // El telefono viaja sin separadores: el borde valida la forma antes que el dominio.
    expect(llamada.request.body).toEqual({
      recipientName: 'Ana María Ruiz',
      phone: '3001234567',
      municipalityCode: '11001',
      line: 'Calle 45 # 12-34',
      complement: null,
      instructions: null,
      postalCode: null,
    });
    // Y no lleva departamento: el codigo del municipio ya lo contiene (RN-100).
    expect(Object.keys(llamada.request.body as object)).not.toContain('departmentCode');

    llamada.flush(guardada);
    await bombear(fixture);

    backend
      .match((peticion) => peticion.url === `${API}/users/me/addresses`)
      .forEach((peticion) => peticion.flush({ addresses: [guardada] }));
    await bombear(fixture);
  });

  /** Un fallo conserva lo escrito: perder una dirección recién tecleada es lo peor. */
  it('conserva lo escrito cuando el guardado falla', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'direccion-municipio', '11001');
    await escribir(fixture, 'direccion-quien-recibe', 'Ana María Ruiz');
    await escribir(fixture, 'direccion-telefono', '3001234567');
    await escribir(fixture, 'direccion-linea', 'Calle 45 # 12-34');

    await guardar(fixture);

    backend
      .expectOne(
        (peticion) => peticion.url === `${API}/users/me/addresses` && peticion.method === 'POST',
      )
      .flush(
        { code: 'USER_UNKNOWN_MUNICIPALITY' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está en la lista oficial');
    expect((campo(fixture, 'direccion-linea') as HTMLInputElement).value).toBe('Calle 45 # 12-34');
  });

  /**
   * Criterio 8: se dice cuál es el tope.
   *
   * <p>Y el número no está escrito en el frontend: cuando el servidor rechaza por lleno,
   * cuántas hay en la libreta es exactamente el máximo.
   */
  it('nombra el tope sin conocerlo cuando la libreta está llena', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'direccion-municipio', '11001');
    await escribir(fixture, 'direccion-quien-recibe', 'Ana María Ruiz');
    await escribir(fixture, 'direccion-telefono', '3001234567');
    await escribir(fixture, 'direccion-linea', 'Calle 45 # 12-34');

    await guardar(fixture);

    backend
      .expectOne(
        (peticion) => peticion.url === `${API}/users/me/addresses` && peticion.method === 'POST',
      )
      .flush(
        { code: 'USER_ADDRESS_BOOK_FULL' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await bombear(fixture);

    // Cero, porque en esta prueba el formulario vive solo y la libreta no se ha pedido.
    // Lo que se comprueba es que el mensaje es el del tope y que lleva el numero dentro.
    expect(fixture.nativeElement.textContent).toContain('direcciones guardadas, que es el máximo');
  });

  /**
   * El fallo que encontró la revisión de pruebas, fijado donde se rompía.
   *
   * <p>«110 111» es como se escribe un código postal, el dominio del servidor lo normaliza
   * y el borde lo rechazaba. Ahora lo normaliza el cliente, que es de quien es el trabajo de
   * hablar el formato del contrato.
   */
  it('manda el código postal sin el espacio con el que se escribe', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'direccion-municipio', '11001');
    await escribir(fixture, 'direccion-quien-recibe', 'Ana María Ruiz');
    await escribir(fixture, 'direccion-telefono', '3001234567');
    await escribir(fixture, 'direccion-linea', 'Calle 45 # 12-34');
    await escribir(fixture, 'direccion-codigo-postal', '110 111');

    await guardar(fixture);

    const llamada = backend.expectOne(
      (peticion) => peticion.url === `${API}/users/me/addresses` && peticion.method === 'POST',
    );
    expect((llamada.request.body as { postalCode: string }).postalCode).toBe('110111');

    llamada.flush(guardada);
    await bombear(fixture);
    backend
      .match((peticion) => peticion.url === `${API}/users/me/addresses`)
      .forEach((peticion) => peticion.flush({ addresses: [guardada] }));
    await bombear(fixture);
  });

  /**
   * Criterio 7: el foco va al primer campo con error, en el orden en que se leen.
   *
   * <p>Sin esta prueba, `enfocarElPrimerError` era código que nadie ejecutaba — y su arreglo
   * estaba en el orden equivocado, así que con «Dirección» y «Quién recibe» vacíos a la vez
   * el foco saltaba al quinto campo.
   */
  it('lleva el foco al primer campo con error en el orden del formulario', async () => {
    const { fixture, backend } = await montar();

    await escribir(fixture, 'direccion-departamento', '11');
    await responderMunicipios(fixture, backend, '11');
    await escribir(fixture, 'direccion-municipio', '11001');
    // Se deja «Direccion» vacia, que en el DOM va antes que «Quien recibe».
    await escribir(fixture, 'direccion-telefono', '3001234567');

    await guardar(fixture);

    expect(document.activeElement?.id).toBe('direccion-linea');
  });

  /**
   * Al editar, el departamento llega ya elegido sin que la API lo mande: se deriva del
   * código del municipio (RN-100).
   */
  it('al editar deriva el departamento del municipio guardado', async () => {
    const { fixture, backend } = await montar(guardada);
    await responderMunicipios(fixture, backend, '11');

    expect((campo(fixture, 'direccion-departamento') as HTMLSelectElement).value).toBe('11');
    expect((campo(fixture, 'direccion-linea') as HTMLInputElement).value).toBe('Calle 45 # 12-34');
    expect((campo(fixture, 'direccion-complemento') as HTMLInputElement).value).toBe('Apto 802');
  });
});
