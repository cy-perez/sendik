import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { AccountPage } from './account-page';

/** Criterios 17, 21, 22 y 23 de HU-001. */
describe('AccountPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  const LISTA = [
    {
      id: 'la-de-ahora',
      userAgent: 'Chrome',
      startedAt: '2026-08-17T15:00:00Z',
      expiresAt: '2026-09-16T15:00:00Z',
      current: true,
    },
    {
      id: 'la-del-movil',
      userAgent: 'Firefox',
      startedAt: '2026-08-10T15:00:00Z',
      expiresAt: '2026-09-09T15:00:00Z',
      current: false,
    },
  ];

  const PERFIL = {
    email: 'ana@correo.co',
    emailVerified: true,
    displayName: 'Ana Maria',
    city: 'Medellin',
    phone: '3001234567',
  };

  const render = async () => {
    const fixture = TestBed.createComponent(AccountPage);
    await fixture.whenStable();
    return fixture;
  };

  /**
   * La pantalla pide dos cosas al abrirse: el perfil (criterio 21) y las sesiones
   * (criterio 17). Las dos se responden aqui para que cada prueba hable solo de
   * lo suyo.
   */
  const responderLaCarga = (sesiones: object[] = []) => {
    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me`)
      .flush(PERFIL);
    backend.expectOne(`${API}/users/me/sessions`).flush(sesiones);
    return backend;
  };

  /**
   * Una consulta de TanStack necesita mas vueltas que una mutacion: la respuesta
   * pasa por su observador antes de llegar a las senales, y con una sola vuelta
   * la pantalla se queda en "cargando".
   */
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

  const escribir = (fixture: { nativeElement: HTMLElement }, valor: string) => {
    const campo = fixture.nativeElement.querySelector('#confirmacion') as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  /**
   * Aparte del `beforeEach` porque una prueba necesita montar la pantalla con la
   * sesion todavia sin resolver, y para eso hace falta configurar sin ponerla.
   * `SessionStore` no ofrece forma de volver a 'desconocida' a proposito: es el
   * estado inicial y nada del producto tiene por que devolverlo ahi.
   */
  const configurar = () => {
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
  };

  beforeEach(() => {
    configurar();
    TestBed.inject(SessionStore).set(SESION);
  });

  /**
   * Los atajos a lo de la persona (ADR-0041). La configuracion de pruebas trae las
   * cuatro banderas encendidas; las bandejas solo aparecen a quien modera.
   */
  it('ofrece atajos a lo que las banderas encienden, y las bandejas solo a quien modera', async () => {
    const fixture = await render();
    responderLaCarga(LISTA);
    await asentar(fixture);

    const destinos = () =>
      Array.from(
        fixture.nativeElement.querySelectorAll('.atajo') as NodeListOf<HTMLAnchorElement>,
      ).map((a) => a.getAttribute('href'));

    expect(destinos()).toEqual([
      '/mis-favoritos',
      '/carrito',
      '/mis-direcciones',
      '/mis-publicaciones',
      '/publicar',
      '/verificacion-de-vendedor',
    ]);

    TestBed.inject(SessionStore).set({
      ...SESION,
      user: { ...SESION.user, roles: ['MODERATOR'] },
    });
    await asentar(fixture);

    expect(destinos()).toContain('/moderacion/verificaciones');
    expect(destinos()).toContain('/moderacion/publicaciones');
  });

  // Criterio 17: la lista dice cual es la sesion desde la que se mira.
  it('lista las sesiones y senala la actual criterio_17', async () => {
    const fixture = await render();

    responderLaCarga(LISTA);
    await asentar(fixture);

    const filas = fixture.nativeElement.querySelectorAll('.sesion');
    expect(filas).toHaveLength(2);
    expect(filas[0].textContent).toContain('Chrome');
    expect(filas[0].textContent).toContain('esta sesión');
    expect(filas[1].textContent).not.toContain('esta sesión');
  });

  // Cerrar la propia dice lo que va a pasar: no es lo mismo que cerrar otra.
  it('distingue cerrar la propia de cerrar otra criterio_17', async () => {
    const fixture = await render();

    responderLaCarga(LISTA);
    await asentar(fixture);

    const botones = fixture.nativeElement.querySelectorAll('.sesion button');
    expect(botones[0].textContent?.trim()).toBe('Cerrar y salir');
    expect(botones[1].textContent?.trim()).toBe('Cerrar');
  });

  it('cierra la sesion elegida y recarga la lista criterio_17', async () => {
    const fixture = await render();
    const backend = responderLaCarga(LISTA);
    await asentar(fixture);

    (fixture.nativeElement.querySelectorAll('.sesion button')[1] as HTMLButtonElement).click();
    await new Promise((listo) => setTimeout(listo, 0));

    backend
      .expectOne(`${API}/users/me/sessions/la-del-movil`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await new Promise((listo) => setTimeout(listo, 0));

    // Se vuelve a consultar: la lista tiene que reflejar lo que acaba de pasar.
    backend.expectOne(`${API}/users/me/sessions`).flush([LISTA[0]]);
  });

  it('pide el archivo de datos al descargarlo criterio_22', async () => {
    const fixture = await render();
    const backend = responderLaCarga();
    await asentar(fixture);

    const boton = Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
      (b as HTMLButtonElement).textContent?.includes('Descargar'),
    ) as HTMLButtonElement;
    boton.click();
    await fixture.whenStable();

    backend.expectOne(`${API}/users/me/export`).flush('{"cuenta":{}}');
  });

  /**
   * Criterio 23: cerrar no es una accion de paso. El formulario no esta a la
   * vista hasta que la persona lo pide.
   */
  it('no muestra el formulario de cierre hasta que se pide criterio_23', async () => {
    const fixture = await render();
    responderLaCarga();
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('#confirmacion')).toBeNull();
  });

  it('no cierra la cuenta si lo escrito no es el propio correo criterio_23', async () => {
    const fixture = await render();
    const backend = responderLaCarga();
    await asentar(fixture);

    (
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
        (b as HTMLButtonElement).textContent?.includes('Quiero cerrar'),
      ) as HTMLButtonElement
    ).click();
    await asentar(fixture);

    escribir(fixture, 'otra@correo.co');
    (fixture.nativeElement.querySelector('.peligro form') as HTMLFormElement).requestSubmit();
    await asentar(fixture);

    backend.expectNone((peticion) => peticion.method === 'DELETE');
    expect(fixture.nativeElement.querySelector('.peligro [aria-invalid="true"]')).not.toBeNull();
  });

  it('cierra la cuenta con la confirmacion correcta criterio_23', async () => {
    const fixture = await render();
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
    const backend = responderLaCarga();
    await asentar(fixture);

    (
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((b) =>
        (b as HTMLButtonElement).textContent?.includes('Quiero cerrar'),
      ) as HTMLButtonElement
    ).click();
    await asentar(fixture);

    escribir(fixture, 'ana@correo.co');
    (fixture.nativeElement.querySelector('.peligro form') as HTMLFormElement).requestSubmit();
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (enviada) => enviada.method === 'DELETE' && enviada.url === `${API}/users/me`,
    );
    expect(peticion.request.body).toEqual({ confirmation: 'ana@correo.co' });

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await asentar(fixture);

    // La cuenta ya no existe: la sesion local no puede sobrevivirla.
    expect(TestBed.inject(SessionStore).isAuthenticated()).toBe(false);
    expect(navegar).toHaveBeenCalledWith('/');
  });
  /**
   * La regresion que encontraron las pruebas de extremo a extremo completas.
   *
   * <p>Todas las demas pruebas de este archivo ponen la sesion **antes** de crear
   * el componente. Al recargar /mi-cuenta con sesion abierta ocurre lo contrario:
   * la ruta no tiene guarda, el enrutador crea la pantalla sin sesion y la
   * recuperacion por la cookie de refresco termina despues.
   *
   * <p>Las consultas del perfil y de las sesiones evaluan su `enabled` al crear su
   * observador, asi que montandolas antes nacian deshabilitadas y no se
   * reactivaban nunca: la pantalla se quedaba en "Cargando tus datos" para
   * siempre. Una consulta deshabilitada esta en estado pendiente, y por eso el
   * sintoma era una carga eterna y no un error visible.
   *
   * <p>Y no era solo al recargar: `SessionMenu` vive en app.html e inyecta el
   * mismo almacen de raiz, asi que el observador nacia sin sesion en **cada**
   * carga y el perfil no se cargaba nunca.
   *
   * <p>Lo que se comprueba aqui es la secuencia entera: sin sesion no se pregunta
   * nada, y en cuanto la sesion llega, se pregunta.
   */
  it('pide sus datos cuando la sesion llega despues de montarse la pantalla', async () => {
    // Un TestBed limpio: `SessionStore` recien creado esta en 'desconocida', que
    // es el estado con el que el enrutador crea esta pantalla en cada carga.
    TestBed.resetTestingModule();
    configurar();

    const almacen = TestBed.inject(SessionStore);
    expect(almacen.status()).toBe('desconocida');

    const fixture = await render();
    const backend = TestBed.inject(HttpTestingController);

    // Sin sesion no se pregunta: seria un 401 seguro.
    backend.expectNone((peticion) => peticion.url.startsWith(`${API}/users/me`));

    almacen.set(SESION);
    // No se espera a que se estabilice antes de responder: una peticion pendiente
    // mantiene la aplicacion inestable, asi que habria que esperar para siempre.
    // Se contesta dentro del bucle que asienta.
    for (let vuelta = 0; vuelta < 8; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      backend
        .match((peticion) => peticion.url.startsWith(`${API}/users/me`))
        .forEach((peticion) => {
          peticion.flush(peticion.request.url.endsWith('/sessions') ? LISTA : PERFIL);
        });
    }
    await asentar(fixture);

    const texto = fixture.nativeElement.textContent as string;
    expect(texto).not.toContain('Cargando tus datos');
    expect(texto).toContain('Chrome');
  });
});
