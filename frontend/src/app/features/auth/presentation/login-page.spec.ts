import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { SessionStore } from '../../../core/session/session.store';
import { AuthStore } from '../application/auth.store';
import { LoginPage } from './login-page';

/**
 * Se consulta por rol y por texto accesible: es como encuentra los controles
 * quien usa el sitio, y no se rompe al maquetar.
 */
describe('LoginPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION = {
    accessToken: 'token-de-acceso',
    expiresIn: 900,
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana Maria',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  const render = async () => {
    const fixture = TestBed.createComponent(LoginPage);
    await fixture.whenStable();
    return fixture;
  };

  const escribir = (fixture: { nativeElement: HTMLElement }, id: string, valor: string) => {
    const campo = fixture.nativeElement.querySelector(`#${id}`) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
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

  /**
   * Pone el `redirectTo` de la direccion. Se declara antes de crear el componente porque
   * la pantalla lo lee del `snapshot`: es una decision de navegacion que se toma una vez.
   */
  const conRedirectTo = (destino: string) => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: {
        snapshot: { queryParamMap: new Map([['redirectTo', destino]]) },
      },
    });
  };

  const rellenarTodoBien = (fixture: { nativeElement: HTMLElement }) => {
    escribir(fixture, 'correo', 'ana@correo.co');
    escribir(fixture, 'contrasena', 'una contrasena larga');
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
   * Asienta la pantalla respondiendo por el camino las peticiones de la propia
   * cuenta.
   *
   * <p>Al abrirse la sesion, el almacen de raiz pide el perfil y la lista de
   * sesiones: sus consultas se habilitan en cuanto hay sesion (auth.store.ts) y
   * este almacen lo instancia la cabecera en cada carga. No son de lo que esta
   * prueba comprueba, pero hay que contestarlas: una peticion pendiente mantiene
   * la aplicacion inestable, asi que responderlas **despues** de esperar a que se
   * estabilice no funciona —se espera para siempre—. Por eso se contestan dentro
   * del mismo bucle que asienta.
   */
  const asentarRespondiendoLaCuenta = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    const backend = TestBed.inject(HttpTestingController);
    for (let vuelta = 0; vuelta < 8; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      backend
        .match((peticion) => peticion.url.startsWith(`${API}/users/me`))
        .forEach((peticion) => {
          peticion.flush(peticion.request.url.endsWith('/sessions') ? [] : null);
        });
    }
    await fixture.whenStable();
  };

  it('presenta los campos con su etiqueta asociada', async () => {
    const fixture = await render();

    for (const id of ['correo', 'contrasena']) {
      expect(
        fixture.nativeElement.querySelector(`label[for="${id}"]`),
        `falta la etiqueta de ${id}`,
      ).not.toBeNull();
    }
  });

  // Quien tiene el gestor de contrasenas del navegador espera que rellene solo.
  it('declara el autocompletado de una contrasena existente', async () => {
    const fixture = await render();
    const campo = fixture.nativeElement.querySelector('#contrasena') as HTMLInputElement;

    expect(campo.getAttribute('autocomplete')).toBe('current-password');
    expect(campo.type).toBe('password');
  });

  it('no muestra errores antes del primer intento de envio', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelectorAll('[role="alert"]')).toHaveLength(0);
  });

  it('marca los campos vacios al enviar y no llama a la API', async () => {
    const fixture = await render();

    enviar(fixture);
    await fixture.whenStable();

    expect(fixture.nativeElement.querySelectorAll('[aria-invalid="true"]')).toHaveLength(2);
    TestBed.inject(HttpTestingController).expectNone(`${API}/auth/login`);
  });

  /**
   * Sin esto el foco se queda en el boton, al final del formulario, y con el
   * texto al 200% los mensajes salen fuera de la vista: la persona pulsa y no
   * pasa nada visible.
   */
  it('lleva el foco al primer campo con error', async () => {
    const fixture = await render();

    enviar(fixture);
    await asentar(fixture);

    expect(document.activeElement?.id).toBe('correo');
  });

  it('lleva el foco a la contrasena si el correo esta bien y ella no', async () => {
    const fixture = await render();
    escribir(fixture, 'correo', 'ana@correo.co');

    enviar(fixture);
    await asentar(fixture);

    expect(document.activeElement?.id).toBe('contrasena');
  });

  /**
   * Al entrar no se juzga la contrasena, se comprueba. Rechazar aqui una de
   * nueve caracteres le diria a quien la tiene de antes que su cuenta es
   * invalida, cuando lo unico cierto es que el minimo cambio despues.
   */
  it('no aplica la politica de longitud del registro', async () => {
    const fixture = await render();
    escribir(fixture, 'correo', 'ana@correo.co');
    escribir(fixture, 'contrasena', 'corta');
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`);
  });

  it('envia las credenciales cuando el formulario esta completo', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    const peticion = TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`);
    expect(peticion.request.body).toEqual({
      email: 'ana@correo.co',
      password: 'una contrasena larga',
    });
  });

  /**
   * ADR-0029: la vuelta al sitio despues de entrar.
   *
   * <p>El primero que lo necesita es el control de favorito de HU-011, que lleva aqui a
   * quien no tiene sesion y espera volver a la ficha con el favorito ya guardado.
   */
  it('vuelve a la direccion que le pidieron al entrar', async () => {
    conRedirectTo('/producto/una-publicacion');

    const fixture = await render();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`).flush(SESION);
    await asentarRespondiendoLaCuenta(fixture);

    expect(navegar).toHaveBeenCalledWith('/producto/una-publicacion');
  });

  /**
   * <strong>Sin esta comprobacion, este formulario es un redirector abierto.</strong>
   * Manda a alguien recien autenticado a un sitio ajeno con la confianza puesta y con el
   * nombre de Sendik en la barra de la que viene.
   *
   * <p>Las dos formas se prueban porque las dos existen: la URL absoluta es la evidente,
   * y `//otro-sitio` es la que se cuela —empieza por barra, y el navegador la lee como
   * otro dominio—.
   */
  it.each(['https://otro-sitio.example/phishing', '//otro-sitio.example/phishing'])(
    'ignora una vuelta a otro sitio y va a la portada: %s',
    async (destino) => {
      conRedirectTo(destino);

      const fixture = await render();
      const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

      rellenarTodoBien(fixture);
      enviar(fixture);
      await fixture.whenStable();

      TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`).flush(SESION);
      await asentarRespondiendoLaCuenta(fixture);

      expect(navegar).toHaveBeenCalledWith('/');
    },
  );

  it('guarda la sesion y sale de la pantalla al entrar', async () => {
    const fixture = await render();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`).flush(SESION);
    await asentarRespondiendoLaCuenta(fixture);

    const sesion = TestBed.inject(SessionStore);
    expect(sesion.isAuthenticated()).toBe(true);
    expect(sesion.user()?.displayName).toBe('Ana Maria');
    expect(navegar).toHaveBeenCalledWith('/');
  });

  /**
   * El almacen es de raiz y sobrevive a la pantalla: si la mutacion se queda con
   * sus variables, la contrasena en claro sigue ahi hasta cerrar la pestana
   * (docs/operacion/datos-personales.md).
   */
  it('no conserva la contrasena escrita despues de entrar', async () => {
    const fixture = await render();
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);

    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController).expectOne(`${API}/auth/login`).flush(SESION);
    await asentarRespondiendoLaCuenta(fixture);

    const almacen = TestBed.inject(AuthStore);
    expect(almacen.login.variables()).toBeUndefined();
    expect(almacen.login.data()).toBeUndefined();
    // La sesion si queda: lo que se olvida es lo que se escribio, no el ingreso.
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(true);
  });

  /**
   * Criterio 11: el servidor responde lo mismo para un correo que no existe y
   * para una contrasena equivocada, y esta pantalla tampoco puede distinguirlos.
   * Un mensaje por caso convertiria el formulario en un detector de cuentas.
   */
  it('muestra un unico mensaje generico ante credenciales incorrectas', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/login`)
      .flush(
        { code: 'AUTH_INVALID_CREDENTIALS', detail: 'user not found' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await asentar(fixture);

    const aviso = fixture.nativeElement.querySelector('.error-general') as HTMLElement;
    expect(aviso.textContent).toContain('El correo o la contraseña no coinciden');
    // Ni el texto interno del servidor ni ninguna pista de cual de los dos fallo.
    expect(fixture.nativeElement.textContent).not.toContain('user not found');
    expect(fixture.nativeElement.querySelectorAll('[aria-invalid="true"]')).toHaveLength(0);
  });

  it('explica el bloqueo por intentos sin dejar el boton cargando RN-006', async () => {
    const fixture = await render();
    rellenarTodoBien(fixture);
    enviar(fixture);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne(`${API}/auth/login`)
      .flush({ code: 'AUTH_ACCOUNT_LOCKED' }, { status: 429, statusText: 'Too Many Requests' });
    await asentar(fixture);

    expect(fixture.nativeElement.textContent).toContain('Demasiados intentos');
    const boton = fixture.nativeElement.querySelector('button[type="submit"]') as HTMLButtonElement;
    expect(boton.getAttribute('aria-busy')).not.toBe('true');
  });
});
