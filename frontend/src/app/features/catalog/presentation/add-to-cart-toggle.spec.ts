import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
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
import { MAXIMO_DE_PRODUCTOS } from '../domain/cart';
import type { PublicListing } from '../domain/public-listing';
import { LocalCart } from '../infrastructure/local-cart';
import { AddToCartToggle } from './add-to-cart-toggle';

/**
 * El control del carrito. HU-015, criterios 1 a 8 y 25.
 *
 * <p><strong>La sesión se abre después de crear el componente, nunca antes.</strong> En una
 * carga de página el componente nace primero y la sesión llega luego, por la cookie de
 * refresco; una prueba que la ponga antes no prueba la carga real. Es la regresión que
 * `account-page.spec.ts` fijó.
 *
 * <p>Lo que se comprueba aquí y no en ningún otro sitio: que sin sesión el control funciona
 * <strong>sin tocar la red</strong> (criterio 8), que el estado se distingue por algo que no
 * es color (criterio 25), y que el mensaje del tope nombra el número.
 */
describe('AddToCartToggle', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';

  const publicacion = (id = ID): PublicListing =>
    ({
      id,
      sellerId: '01a04385-47b7-79c7-b3f2-62c03a8d4b00',
      product: {
        title: 'Camisa de lino color hueso',
        price: { amount: 185_000, currency: 'COP' },
      },
      images: [],
      publishedAt: '2026-09-10T15:00:00Z',
    }) as unknown as PublicListing;

  @Component({
    standalone: true,
    imports: [AddToCartToggle],
    template: '<sendik-add-to-cart-toggle [publicacion]="p()" [nombreDelVendedor]="\'Ana\'" />',
    changeDetection: ChangeDetectionStrategy.OnPush,
  })
  class Anfitrion {
    readonly p = signal(publicacion());
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

  beforeEach(() => {
    localStorage.clear();

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

  /**
   * Ninguna petición de más.
   *
   * <p>Es el guardián que HU-011 tuvo que añadir cuando descubrió que el almacén, siendo de
   * raíz, pedía la lista entera al abrir cualquier producto. Aquí protege lo mismo y una cosa
   * más: que sin sesión no salga ni una petición.
   */
  afterEach(() => {
    const backend = TestBed.inject(HttpTestingController);

    // Toda mutación invalida el estado, así que al final queda una relectura pendiente. Es
    // comportamiento correcto y se responde aquí para que `verify` siga siendo estricto.
    backend
      .match((llamada) => llamada.method === 'GET' && llamada.url.includes('/users/me/cart/items/'))
      .forEach((llamada) => llamada.flush({ inCart: false, eligible: true }));

    backend.verify();
    localStorage.clear();
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
    cuerpo: { inCart: boolean; eligible: boolean },
  ) => {
    backend.expectOne(`${API}/users/me/cart/items/${ID}`).flush(cuerpo);
    await bombear(fixture);
  };

  const boton = (fixture: ComponentFixture<Anfitrion>) =>
    fixture.nativeElement.querySelector('button') as HTMLButtonElement | null;

  const texto = (fixture: ComponentFixture<Anfitrion>) =>
    (fixture.nativeElement as HTMLElement).textContent ?? '';

  // --- Sin sesión ----------------------------------------------------------

  /**
   * Criterio 8, y es la diferencia con el control de favorito: aquel lleva a entrar.
   *
   * <p>Se afirma además que no hubo <strong>ninguna</strong> petición, que es lo que de verdad
   * separa este control del de HU-011: sin sesión el carrito lo contesta el navegador.
   */
  it('agrega sin sesión y sin tocar la red', async () => {
    const { fixture, backend } = await montar(false);

    boton(fixture)!.click();
    await bombear(fixture);

    expect(TestBed.inject(LocalCart).contiene(ID)).toBe(true);
    backend.verify();
  });

  it('quita sin sesión, y el control vuelve a ofrecer agregar', async () => {
    const { fixture } = await montar(false);

    boton(fixture)!.click();
    await bombear(fixture);
    boton(fixture)!.click();
    await bombear(fixture);

    expect(TestBed.inject(LocalCart).contiene(ID)).toBe(false);
  });

  /** Criterio 7: se rechaza y se dice cuál es el tope, con el número dentro del mensaje. */
  it('dice el tope cuando el carrito local está lleno', async () => {
    const local = TestBed.inject(LocalCart);
    for (let i = 0; i < MAXIMO_DE_PRODUCTOS; i++) {
      local.agregar({
        listingId: `otro-${i}`,
        title: 'Otra cosa',
        price: 1000,
        imageUrl: null,
        sellerName: null,
      });
    }

    const { fixture } = await montar(false);
    boton(fixture)!.click();
    await bombear(fixture);

    expect(texto(fixture)).toContain(String(MAXIMO_DE_PRODUCTOS));
  });

  // --- Con sesión ----------------------------------------------------------

  it('refleja el estado que llega y no el que se supone', async () => {
    const { fixture, backend } = await montar(true);

    await responderEstado(fixture, backend, { inCart: true, eligible: true });

    expect(texto(fixture)).toContain('Quitar del carrito');
  });

  /** Criterio 5: sobre la publicación propia el control no se ofrece. */
  it('no ofrece el control cuando el servidor dice que no es elegible', async () => {
    const { fixture, backend } = await montar(true);

    await responderEstado(fixture, backend, { inCart: false, eligible: false });

    expect(boton(fixture)).toBeNull();
  });

  /**
   * El error se ve, y **cuelga del contenedor y no del botón**.
   *
   * <p>Es el guardián de la regresión que la revisión de HU-011 encontró en su criterio 10: el
   * bloque del error vivía dentro del `@if` del control, así que en cuanto el servidor decía
   * que la publicación no era elegible el control dejaba de pintarse y se llevaba por delante
   * el mensaje que explicaba por qué.
   *
   * <p>Aquí ese camino exacto no se puede recorrer —un error no invalida el estado, así que
   * el control no desaparece por haber fallado— y por eso se comprueba la propiedad
   * estructural en vez de simularlo: el aviso es hermano del botón, no su descendiente. Si
   * alguien lo vuelve a meter dentro del `@if`, esta prueba cae.
   */
  it('enseña el error como hermano del botón y no dentro de él', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { inCart: false, eligible: true });

    boton(fixture)!.click();
    await bombear(fixture);
    backend
      .expectOne(`${API}/users/me/cart/items/${ID}`)
      .flush({ code: 'CATALOG_SELF_CART_FORBIDDEN' }, { status: 403, statusText: 'Forbidden' });
    await bombear(fixture);

    const aviso = fixture.nativeElement.querySelector('[role="alert"]') as HTMLElement | null;

    expect(aviso).not.toBeNull();
    expect(aviso!.parentElement!.classList.contains('carrito-control')).toBe(true);
    expect(boton(fixture)!.contains(aviso)).toBe(false);
  });

  /**
   * Criterio 25: el estado no se comunica solo por color.
   *
   * <p>Dos señales reales: el icono pasa de contorno a relleno y el texto del botón cambia.
   * Se comprueban las dos, porque una sola dejaría pasar la regresión que HU-011 tuvo —dos
   * señales que parecían dos y eran una—.
   */
  it('distingue el estado por el relleno del icono y por el texto', async () => {
    const { fixture, backend } = await montar(true);

    await responderEstado(fixture, backend, { inCart: false, eligible: true });

    expect(fixture.nativeElement.querySelector('.icono-relleno')).toBeNull();
    expect(texto(fixture)).toContain('Agregar al carrito');
  });

  /** Y el otro lado del par: dentro del carrito, relleno y con el otro texto. */
  it('rellena el icono y cambia el texto cuando está dentro', async () => {
    const { fixture, backend } = await montar(true);

    await responderEstado(fixture, backend, { inCart: true, eligible: true });

    expect(fixture.nativeElement.querySelector('.icono-relleno')).not.toBeNull();
    expect(texto(fixture)).toContain('Quitar del carrito');
  });

  /** El botón no se deshabilita mientras la petición viaja: mataría el foco (HU-011). */
  it('no deshabilita el botón mientras la petición viaja', async () => {
    const { fixture, backend } = await montar(true);
    await responderEstado(fixture, backend, { inCart: false, eligible: true });

    boton(fixture)!.click();
    await bombear(fixture);

    expect(boton(fixture)!.disabled).toBe(false);
    expect(boton(fixture)!.getAttribute('aria-busy')).toBe('true');

    backend
      .expectOne(`${API}/users/me/cart/items/${ID}`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await bombear(fixture);
  });

  /** Sin `aria-pressed`: el nombre accesible ya cambia con el estado. */
  it('no lleva aria-pressed', async () => {
    const { fixture, backend } = await montar(true);

    await responderEstado(fixture, backend, { inCart: true, eligible: true });

    expect(boton(fixture)!.hasAttribute('aria-pressed')).toBe(false);
  });
});
