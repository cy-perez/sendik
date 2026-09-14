import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { CameraService } from '../../../shared/infrastructure/camera.service';
import { SharpnessService } from '../infrastructure/sharpness.service';
import { DocumentCaptureForm } from './document-capture-form';
import { SelfieCaptureForm } from './selfie-capture-form';

/** Los dos pasos de imagen de HU-002: documento por ambas caras y selfie. */
describe('formularios de captura', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  class CamaraFalsa {
    apagadas = 0;

    soportada(): boolean {
      return true;
    }
    async abrir(): Promise<MediaStream> {
      return { getTracks: () => [{ stop: vi.fn() }] } as unknown as MediaStream;
    }
    cerrar(): void {
      // Nada que apagar en un doble: lo que se comprueba aqui es el envio, y que la
      // camara se apague lo prueba capture-field.spec.ts.
      this.apagadas++;
    }
    async capturar(): Promise<Blob> {
      return new Blob(['unos bytes'], { type: 'image/jpeg' });
    }
  }

  /** Aqui se comprueba el envio, no el umbral: lo borroso lo prueba capture-field.spec.ts. */
  class NitidezFalsa {
    async estaNitida(): Promise<boolean> {
      return true;
    }
  }

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

  /**
   * Toma una foto en el primer campo que todavía no la tenga.
   *
   * <p>Siempre el primero disponible y no un índice fijo: en cuanto un campo tiene su
   * foto deja de ofrecer «Abrir la cámara», así que los índices se desplazan. Con un
   * índice fijo, la segunda llamada no encontraba botón y el formulario se quedaba con
   * una sola cara sin que la prueba lo dijera.
   */
  const capturar = async (fixture: {
    nativeElement: HTMLElement;
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    const abrir = [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes('Abrir la cámara'),
    );
    expect(abrir, 'no queda ningún campo sin foto').toBeDefined();
    abrir?.click();
    await asentar(fixture);

    const tomar = [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes('Tomar la foto'),
    );
    tomar?.click();
    await asentar(fixture);
  };

  const escribir = async (
    fixture: { nativeElement: HTMLElement; whenStable: () => Promise<unknown> },
    id: string,
    valor: string,
  ) => {
    const campo = fixture.nativeElement.querySelector(`#${id}`) as
      HTMLInputElement | HTMLSelectElement;
    campo.value = valor;
    campo.dispatchEvent(new Event(campo.tagName === 'SELECT' ? 'change' : 'input'));
    await fixture.whenStable();
  };

  const enviarFormulario = (fixture: { nativeElement: HTMLElement }) => {
    const formulario = fixture.nativeElement.querySelector('form') as HTMLFormElement;
    formulario.dispatchEvent(new Event('submit'));
  };

  beforeEach(() => {
    URL.createObjectURL = vi.fn(() => 'blob:una-vista-previa');
    URL.revokeObjectURL = vi.fn();

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
        { provide: CameraService, useValue: new CamaraFalsa() },
        { provide: SharpnessService, useValue: new NitidezFalsa() },
      ],
    });
    TestBed.inject(SessionStore).set(SESION);
  });

  // --- Documento. Criterio 2 ------------------------------------------------

  describe('DocumentCaptureForm', () => {
    const montar = async () => {
      const fixture = TestBed.createComponent(DocumentCaptureForm);
      await asentar(fixture);
      return { fixture, backend: TestBed.inject(HttpTestingController) };
    };

    /**
     * Las dos caras son obligatorias las dos: el número está en una y la fecha de
     * vencimiento suele estar en la otra, y sin ella `EXPIRED_DOCUMENT` no se puede
     * comprobar.
     */
    it('no envía con una sola cara y lo dice', async () => {
      const { fixture, backend } = await montar();

      await escribir(fixture, 'tipo-de-documento', 'CC');
      await escribir(fixture, 'numero-de-documento', '1053812947');
      await escribir(fixture, 'titular-del-documento', 'Ana Maria Garcia');
      await capturar(fixture);

      enviarFormulario(fixture);
      await asentar(fixture);

      backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/document`);
      expect(fixture.nativeElement.textContent).toContain('Faltan las dos fotos');
    });

    it('envía las dos caras y los tres datos como multipart', async () => {
      const { fixture, backend } = await montar();

      await escribir(fixture, 'tipo-de-documento', 'PPT');
      await escribir(fixture, 'numero-de-documento', '1053812947');
      await escribir(fixture, 'titular-del-documento', 'Ana Maria Garcia');
      await capturar(fixture);
      await capturar(fixture);

      enviarFormulario(fixture);
      await fixture.whenStable();

      const peticion = backend.expectOne(
        (enviada) =>
          enviada.method === 'PUT' && enviada.url === `${API}/users/me/verification/document`,
      );
      const cuerpo = peticion.request.body as FormData;

      expect(cuerpo.get('tipo')).toBe('PPT');
      expect(cuerpo.get('numero')).toBe('1053812947');
      expect(cuerpo.get('titular')).toBe('Ana Maria Garcia');
      expect(cuerpo.get('frente')).toBeInstanceOf(Blob);
      expect(cuerpo.get('reverso')).toBeInstanceOf(Blob);
    });

    it('no envía un número con letras', async () => {
      const { fixture, backend } = await montar();

      await escribir(fixture, 'tipo-de-documento', 'CC');
      await escribir(fixture, 'numero-de-documento', '105ABC947');
      await escribir(fixture, 'titular-del-documento', 'Ana Maria Garcia');
      await capturar(fixture);
      await capturar(fixture);

      enviarFormulario(fixture);
      await asentar(fixture);

      backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/document`);
      expect(fixture.nativeElement.textContent).toContain('solo dígitos');
    });

    it('no envía sin tipo de documento', async () => {
      const { fixture, backend } = await montar();

      await escribir(fixture, 'numero-de-documento', '1053812947');
      await escribir(fixture, 'titular-del-documento', 'Ana Maria Garcia');
      await capturar(fixture);
      await capturar(fixture);

      enviarFormulario(fixture);
      await asentar(fixture);

      backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/document`);
      expect(fixture.nativeElement.textContent).toContain('Elige el tipo de documento');
    });

    /** Criterio 5, traducido en el borde: el documento ya está verificado en otra cuenta. */
    it('traduce el rechazo del servidor', async () => {
      const { fixture, backend } = await montar();

      await escribir(fixture, 'tipo-de-documento', 'CC');
      await escribir(fixture, 'numero-de-documento', '1053812947');
      await escribir(fixture, 'titular-del-documento', 'Ana Maria Garcia');
      await capturar(fixture);
      await capturar(fixture);

      enviarFormulario(fixture);
      await fixture.whenStable();

      backend
        .expectOne(
          (enviada) =>
            enviada.method === 'PUT' && enviada.url === `${API}/users/me/verification/document`,
        )
        .flush(
          { code: 'SELLER_DOCUMENT_ALREADY_VERIFIED' },
          { status: 409, statusText: 'Conflict' },
        );
      await asentar(fixture);

      expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
        'ya está verificado en otra cuenta',
      );
    });
  });

  // --- Selfie. Criterio 3 ---------------------------------------------------

  describe('SelfieCaptureForm', () => {
    const montar = async () => {
      const fixture = TestBed.createComponent(SelfieCaptureForm);
      await asentar(fixture);
      return { fixture, backend: TestBed.inject(HttpTestingController) };
    };

    it('no envía sin foto y lo dice', async () => {
      const { fixture, backend } = await montar();

      enviarFormulario(fixture);
      await asentar(fixture);

      backend.expectNone((enviada) => enviada.url === `${API}/users/me/verification/selfie`);
      expect(fixture.nativeElement.textContent).toContain('Falta tu foto');
    });

    it('envía la foto como multipart', async () => {
      const { fixture, backend } = await montar();

      await capturar(fixture);
      enviarFormulario(fixture);
      await fixture.whenStable();

      const peticion = backend.expectOne(
        (enviada) =>
          enviada.method === 'PUT' && enviada.url === `${API}/users/me/verification/selfie`,
      );

      expect((peticion.request.body as FormData).get('archivo')).toBeInstanceOf(Blob);
    });

    /** El criterio 3 en el paso que le corresponde: aquí tampoco hay selector de archivos. */
    it('no ofrece subir desde la galería', async () => {
      const { fixture } = await montar();

      expect(fixture.nativeElement.querySelector('input[type="file"]')).toBeNull();
    });
  });
});
