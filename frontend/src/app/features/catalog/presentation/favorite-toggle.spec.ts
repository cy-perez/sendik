import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { SessionStore } from '../../../core/session/session.store';
import { FavoriteIntent } from '../infrastructure/favorite-intent';
import { FavoriteToggle } from './favorite-toggle';

/**
 * El control de favorito. HU-011, criterios 1 a 10 y 17.
 *
 * <p><strong>La sesión se abre después de crear el componente, nunca antes.</strong> En una
 * carga de página el componente nace primero y la sesión llega luego, por la cookie de
 * refresco; una prueba que la ponga antes no prueba la carga real. Es la regresión que
 * `account-page.spec.ts` fijó y que aquí importa el doble, porque de que la sesión esté
 * resuelta dependen la consulta del estado y la intención pendiente.
 */
describe('FavoriteToggle', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';

  @Component({
    standalone: true,
    imports: [FavoriteToggle],
    template: '<sendik-favorite-toggle [publicacion]="id()" />',
    changeDetection: ChangeDetectionStrategy.OnPush,
  })
  class Anfitrion {
    readonly id = signal(ID);
  }

  const sesion: Session = {
    accessToken: 'un-token',
    user: {
      email: 'ana@correo.co',
      displayName: 'Ana María',
      emailVerified: true,
      roles: ['BUYER'],
    },
  };

  /** El pase que trae la direccion de vuelta, o ninguno. Ver `FavoriteIntent`. */
  let paseEnLaDireccion: string | null = null;

  beforeEach(() => {
    sessionStorage.clear();
    paseEnLaDireccion = null;

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: {
                get: (nombre: string) => (nombre === 'fav' ? paseEnLaDireccion : null),
              },
            },
          },
        },
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  /**
   * Ninguna peticion de mas.
   *
   * <p>Sin esto, una llamada que nadie esperaba —la lista pedida desde la ficha, un
   * segundo estado— pasaba desapercibida en toda la suite. Es justo el defecto que hubo:
   * el almacen es de raiz y con sesion abierta pedia la lista al abrir cualquier producto.
   */
  afterEach(() => {
    const backend = TestBed.inject(HttpTestingController);

    // Toda mutación invalida el estado, así que al final queda una relectura pendiente.
    // Es comportamiento correcto y esperado —es lo que hace que el control acabe
    // mostrando lo que el servidor dice— y se responde aquí para que `verify` pueda
    // seguir siendo estricto con todo lo demás.
    backend
      .match((llamada) => llamada.method === 'GET' && llamada.url.includes('/users/me/favorites/'))
      .forEach((llamada) => llamada.flush({ favorite: false, eligible: true }));

    backend.verify();
  });

  const bombear = async (fixture: ComponentFixture<Anfitrion>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /** El componente nace primero; la sesión, si la hay, llega después. */
  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(Anfitrion);
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

  const responderEstado = async (
    fixture: ComponentFixture<Anfitrion>,
    backend: HttpTestingController,
    cuerpo: { favorite: boolean; eligible: boolean },
  ) => {
    // Por metodo ademas de por URL: el PUT que marca comparte direccion con esta lectura,
    // asi que sin el metodo la intencion pendiente hace que casen dos peticiones.
    backend
      .expectOne(
        (llamada) => llamada.method === 'GET' && llamada.url === `${API}/users/me/favorites/${ID}`,
      )
      .flush(cuerpo);
    await bombear(fixture);
  };

  const boton = (fixture: ComponentFixture<Anfitrion>) =>
    fixture.nativeElement.querySelector('button') as HTMLButtonElement | null;

  /** El nombre accesible del control, que es lo que un lector de pantalla lee. */
  const nombreDelBoton = (fixture: ComponentFixture<Anfitrion>) =>
    boton(fixture)?.textContent?.trim();

  /** Criterio 1: el control refleja el estado que llega, no el que se supone. */
  it('se pinta marcado cuando el servidor dice que ya lo estaba', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    expect(nombreDelBoton(fixture)).toBe('Quitar de favoritos');
  });

  it('se pinta sin marcar cuando el servidor dice que no lo estaba', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    expect(nombreDelBoton(fixture)).toBe('Guardar');
  });

  /** Criterio 7: sin sesión el control se ofrece igual, y no se pide nada al servidor. */
  it('se ofrece a quien no tiene sesión y no consulta el estado', async () => {
    const { fixture, backend } = await montar(false);

    expect(boton(fixture)).not.toBeNull();
    expect(nombreDelBoton(fixture)).toBe('Guardar');
    backend.expectNone((llamada) => llamada.url.includes('/users/me/favorites'));
  });

  /** Criterio 5: sobre la publicación propia no se ofrece. Lo decide el servidor. */
  it('no se ofrece cuando el servidor dice que no es elegible, criterio 5', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: false });

    expect(boton(fixture)).toBeNull();
  });

  /** Criterio 2: se marca, y el control se adelanta a la respuesta. */
  it('marca al pulsar', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(nombreDelBoton(fixture)).toBe('Quitar de favoritos');

    peticion.flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);
  });

  /** Criterio 3: y se quita. */
  it('quita al pulsar cuando ya estaba marcado', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) =>
          llamada.method === 'DELETE' && llamada.url === `${API}/users/me/favorites/${ID}`,
      )
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);

    // Y sigue sin marcar mientras la respuesta fresca viaja de vuelta. Antes de arreglarlo
    // volvia a pintarse marcado en ese hueco y se desmarcaba al llegar: un parpadeo que
    // decia lo contrario de lo que acababa de pasar.
    expect(nombreDelBoton(fixture)).toBe('Guardar');

    backend
      .match((llamada) => llamada.method === 'GET')
      .forEach((llamada) => llamada.flush({ favorite: false, eligible: true }));
    await bombear(fixture);

    expect(nombreDelBoton(fixture)).toBe('Guardar');
  });

  /**
   * Caso borde: el doble pulsado no manda dos peticiones ni deja el estado invertido.
   *
   * <p>Se comprueba contando las peticiones y no mirando el botón: deshabilitarlo mientras
   * la petición está en curso es una de las formas de conseguirlo, pero no la única, y la
   * prueba tiene que valer si mañana se resuelve de otra manera.
   */
  it('no manda dos peticiones al pulsar dos veces seguidas', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);
    boton(fixture)?.click();
    await bombear(fixture);

    const peticiones = backend.match(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(peticiones).toHaveLength(1);
    peticiones.forEach((peticion) =>
      peticion.flush(null, { status: 204, statusText: 'No Content' }),
    );
    await bombear(fixture);
  });

  /**
   * Un fallo devuelve el control a su estado anterior y lo dice. Lo peor que puede hacer
   * un control optimista es quedarse mintiendo.
   */
  it('vuelve al estado anterior cuando la petición falla', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(nombreDelBoton(fixture)).toBe('Guardar');
    expect(fixture.nativeElement.textContent).toContain('No pudimos guardar el cambio');
  });

  /** Criterio 10: si resulta ser suya, se explica por qué no se guardó. */
  it('explica que la publicación es propia cuando el servidor responde 403', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'CATALOG_SELF_FAVORITE_FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Esta publicación es tuya');
  });

  /** Criterio 6: la publicación se vendió mientras estaba en pantalla. */
  it('explica que ya no está disponible cuando el servidor responde 404', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    backend
      .expectOne((llamada) => llamada.method === 'PUT')
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está disponible');
  });

  /**
   * Criterio 8, primera mitad: sin sesión se anota la intención y se va a entrar, con la
   * dirección de vuelta puesta.
   */
  it('sin sesión anota la intención y lleva a entrar con la vuelta puesta', async () => {
    const { fixture, backend } = await montar(false);
    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    boton(fixture)?.click();
    await bombear(fixture);

    expect(navegar).toHaveBeenCalledTimes(1);
    const [ruta, opciones] = navegar.mock.calls[0] as [
      string[],
      { queryParams: { redirectTo: string } },
    ];
    expect(ruta).toEqual(['/ingresar']);

    // La vuelta lleva la ficha y el pase. El pase no es la accion: sin la intencion
    // guardada en esta pestana no marca nada, y por eso puede viajar en la direccion.
    expect(opciones.queryParams.redirectTo.startsWith(`/producto/${ID}?fav=`)).toBe(true);
    expect(opciones.queryParams.redirectTo.length).toBeGreaterThan(`/producto/${ID}?fav=`.length);

    backend.expectNone((llamada) => llamada.method === 'PUT');
  });

  /**
   * Criterio 10, y es el camino que el mensaje de error tiene que sobrevivir.
   *
   * <p>Vuelvo del ingreso con una intención pendiente sobre **mi propia** publicación: la
   * lectura puntual responde `eligible: false` y el PUT falla con 403. El control deja de
   * ofrecerse, y el mensaje **tiene que seguir viéndose**: el criterio pide que no se
   * guarde nada y que se explique por qué.
   *
   * <p>Estaba mal. El bloque del error vivía dentro del `@if` del control, así que
   * desaparecía con él y nadie llegaba a leerlo nunca. La prueba del 403 de más abajo no
   * lo veía porque monta con `eligible: true`, que es justo el caso en el que el estado no
   * contradice al error.
   */
  it('explica por que no se guardo aunque el control deje de ofrecerse, criterio 10', async () => {
    paseEnLaDireccion = TestBed.inject(FavoriteIntent).recordar(ID);

    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: false });

    backend
      .match((llamada) => llamada.method === 'PUT')
      .forEach((llamada) =>
        llamada.flush(
          { code: 'CATALOG_SELF_FAVORITE_FORBIDDEN' },
          { status: 403, statusText: 'Forbidden' },
        ),
      );
    await bombear(fixture);

    expect(boton(fixture)).toBeNull();
    expect(fixture.nativeElement.querySelector('[role="alert"]')?.textContent).toContain(
      'Esta publicación es tuya',
    );
  });

  /**
   * Criterio 9: abandonar el ingreso no deja nada guardado.
   *
   * <p>Volver atrás desde el formulario es exactamente esto: la ficha se monta otra vez y
   * la sesión resuelve **anónima**. Si la intención sobreviviera a eso, el favorito
   * aparecería la próxima vez que alguien entrara desde ese navegador, sobre algo que ya
   * no recuerda haber pulsado.
   */
  it('descarta la intención pendiente cuando la sesión resuelve anónima, criterio 9', async () => {
    const pase = TestBed.inject(FavoriteIntent).recordar(ID);

    const { fixture, backend } = await montar(false);
    await bombear(fixture);

    expect(TestBed.inject(FavoriteIntent).consumir(ID, pase)).toBe(false);
    backend.expectNone((llamada) => llamada.method === 'PUT');
  });

  /**
   * Criterio 8, segunda mitad: al volver con sesión, la intención se dispara sola y una
   * sola vez.
   *
   * <p>Es el caso que pasa por la recarga: el componente nace sin sesión, la cookie de
   * refresco la trae después, y solo entonces se puede guardar nada.
   */
  it('retoma la intención al resolverse la sesión, y no la repite', async () => {
    const pase = TestBed.inject(FavoriteIntent).recordar(ID);
    paseEnLaDireccion = pase;

    const { fixture, backend } = await montar(true);
    await bombear(fixture);

    const marcados = backend.match(
      (llamada) => llamada.method === 'PUT' && llamada.url === `${API}/users/me/favorites/${ID}`,
    );
    expect(marcados).toHaveLength(1);
    marcados.forEach((llamada) => llamada.flush(null, { status: 204, statusText: 'No Content' }));
    await bombear(fixture);

    expect(TestBed.inject(FavoriteIntent).consumir(ID, pase)).toBe(false);
  });

  /**
   * Criterio 17: el estado se distingue sin percibir color. Dos señales, y ninguna lo es:
   * el nombre del botón y el relleno del icono.
   */
  it('distingue el estado por el nombre del boton y por el relleno del icono', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    expect(nombreDelBoton(fixture)).toBe('Quitar de favoritos');
    expect(fixture.nativeElement.querySelector('svg')?.classList).toContain('icono-relleno');
  });

  /**
   * La región viva nace vacía y solo se llena tras una acción.
   *
   * <p>Una región que aparece con texto dentro se comporta distinto según el lector, y en
   * los que la locutan suelta un anuncio que nadie pidió en cada carga de ficha.
   */
  it('no anuncia nada hasta que alguien pulsa', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: true, eligible: true });

    const region = fixture.nativeElement.querySelector('[role="status"]') as HTMLElement;
    expect(region).not.toBeNull();
    expect(region.textContent?.trim()).toBe('');

    boton(fixture)?.click();
    await bombear(fixture);
    backend
      .match((llamada) => llamada.method === 'DELETE')
      .forEach((llamada) => llamada.flush(null, { status: 204, statusText: 'No Content' }));
    await bombear(fixture);

    expect(region.textContent).toContain('No está en tus favoritos');
  });

  /**
   * El botón no se deshabilita mientras la petición viaja.
   *
   * <p>Deshabilitarlo en el mismo tick del clic, con el foco dentro, hace que el navegador
   * mande el foco a `body`: quien pulsa con teclado tendría que tabular desde el principio
   * del documento. El doble pulsado ya lo bloquea el almacén contando peticiones.
   */
  it('no saca el boton del orden de foco mientras la peticion viaja', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { favorite: false, eligible: true });

    boton(fixture)?.click();
    await bombear(fixture);

    expect(boton(fixture)?.disabled).toBe(false);
    expect(boton(fixture)?.getAttribute('aria-busy')).toBe('true');

    backend
      .match((llamada) => llamada.method === 'PUT')
      .forEach((llamada) => llamada.flush(null, { status: 204, statusText: 'No Content' }));
    await bombear(fixture);
  });
});
