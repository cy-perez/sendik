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
import { SessionStore } from '../../../core/session/session.store';
import { ResetPasswordPage } from './reset-password-page';

/** Criterios 18 y 20 de HU-001. */
describe('ResetPasswordPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const render = async (token?: string) => {
    const fixture = TestBed.createComponent(ResetPasswordPage);
    if (token !== undefined) {
      fixture.componentRef.setInput('token', token);
    }
    await fixture.whenStable();
    return fixture;
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, valor: string) => {
    const campo = fixture.nativeElement.querySelector('#contrasena') as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  const enviar = (fixture: { nativeElement: HTMLElement }) => {
    (fixture.nativeElement.querySelector('form') as HTMLFormElement).requestSubmit();
  };

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();
    await fixture.whenStable();
  };

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
  });

  /**
   * A diferencia de la verificacion de correo, aqui no se envia nada al cargar: el
   * enlace se consume al enviar el formulario. Asi la vista previa de un enlace en
   * WhatsApp no puede gastarlo.
   */
  it('no consume el enlace al abrir la pagina', async () => {
    await render('el-del-correo');

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/reset-password`);
  });

  it('explica que falta el enlace si no viene token', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.textContent).toContain('Falta el enlace');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  // RN-005: recuperar el acceso no admite una contrasena peor.
  it('no llama a la API con una contrasena corta RN_005', async () => {
    const fixture = await render('el-del-correo');
    escribir(fixture, 'corta');
    enviar(fixture);
    await asentar(fixture);

    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/reset-password`);
    expect(document.activeElement?.id).toBe('contrasena');
  });

  it('envia el token del enlace junto a la contrasena nueva', async () => {
    const fixture = await render('el-del-correo');
    escribir(fixture, 'una-contrasena-larga');
    enviar(fixture);
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(`${API}/auth/reset-password`);
    expect(peticion.request.body).toEqual({
      token: 'el-del-correo',
      newPassword: 'una-contrasena-larga',
    });
  });

  /**
   * Criterio 20: no se abre sesion. Emitirla aqui dejaria exenta del cierre a la
   * sesion recien creada, que es justo lo que el criterio acaba de hacer.
   */
  it('no abre sesion al terminar y manda a entrar criterio_20', async () => {
    const fixture = await render('el-del-correo');
    escribir(fixture, 'una-contrasena-larga');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/reset-password`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);

    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(fixture.nativeElement.textContent).toContain('Cerramos todas las sesiones');
    expect(fixture.nativeElement.querySelector('a[href="/ingresar"]')).not.toBeNull();
  });

  /**
   * Criterio 18: el mensaje dice 30 minutos, no 24 horas. Es la razon de que este
   * enlace tenga codigo de error propio y no reutilice el de la verificacion.
   */
  it('dice la duracion correcta cuando el enlace vencio criterio_18', async () => {
    const fixture = await render('el-vencido');
    escribir(fixture, 'una-contrasena-larga');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/reset-password`)
      .flush(
        { code: 'AUTH_RESET_TOKEN_EXPIRED' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('.error-general') as HTMLElement;
    expect(aviso.textContent).toContain('30 minutos');
    // Y se ofrece pedir otro, porque el enlace ya no sirve.
    expect(aviso.querySelector('a[href="/recuperar-contrasena"]')).not.toBeNull();
  });

  /**
   * Si lo que fallo fue la contrasena, el enlace sigue vivo: mandarla a pedir otro
   * la haria repetir un paso que no hacia falta.
   */
  it('no ofrece pedir otro enlace cuando el que fallo fue la contrasena', async () => {
    const fixture = await render('el-del-correo');
    escribir(fixture, 'una-contrasena-larga');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/reset-password`)
      .flush(
        { code: 'AUTH_PASSWORD_BREACHED' },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('.error-general') as HTMLElement;
    expect(aviso.textContent).toContain('filtración');
    expect(aviso.querySelector('a')).toBeNull();
  });
});
