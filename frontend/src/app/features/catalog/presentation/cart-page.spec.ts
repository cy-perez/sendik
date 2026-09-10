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
import { LocalCart } from '../infrastructure/local-cart';
import { CartPage } from './cart-page';

/**
 * La pantalla del carrito. HU-015, criterios 13 a 24.
 *
 * <p>Lo que se comprueba aquí y no en ningún otro sitio: que la palabra «total» no aparece
 * (criterio 16), que lo no disponible sigue a la vista y fuera del subtotal (criterio 21),
 * que con dos vendedores se avisa de la división (criterio 15), y que la fusión se
 * <strong>pregunta</strong> en vez de hacerse sola (criterios 9 y 12).
 */
describe('CartPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const UNA = '01a04385-47b7-79c7-b3f2-62c03a8d4a01';
  const OTRA = '01a04385-47b7-79c7-b3f2-62c03a8d4a02';

  const linea = (id: string, precio: number, disponible = true, cambio = false) => ({
    listing: {
      id,
      sellerId: 'v-1',
      product: {
        title: `Producto ${id.slice(-2)}`,
        price: { amount: precio, currency: 'COP' },
      },
      images: [],
      publishedAt: '2026-09-10T15:00:00Z',
    },
    addedAt: '2026-09-10T15:00:00Z',
    available: disponible,
    priceChanged: cambio,
  });

  const grupo = (vendedor: string, nombre: string | null, lineas: unknown[], subtotal: number) => ({
    sellerId: vendedor,
    sellerName: nombre,
    sellerVerified: true,
    lines: lineas,
    subtotal: { amount: subtotal, currency: 'COP' },
    allUnavailable: subtotal === 0,
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

  afterEach(() => {
    TestBed.inject(HttpTestingController).verify();
    localStorage.clear();
  });

  const bombear = async (fixture: ComponentFixture<CartPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /** La pantalla nace primero; la sesión, si la hay, llega después. */
  const montar = async (conSesion: boolean) => {
    const fixture = TestBed.createComponent(CartPage);
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

  const texto = (fixture: ComponentFixture<CartPage>) =>
    (fixture.nativeElement as HTMLElement).textContent ?? '';

  // --- La forma de la pantalla --------------------------------------------

  it('pinta un grupo por vendedor con su subtotal', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [
        grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000),
        grupo('v-2', 'Carlos', [linea(OTRA, 50_000)], 50_000),
      ],
      willSplit: true,
    });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelectorAll('.carrito__grupo')).toHaveLength(2);
    expect(texto(fixture)).toContain('Ana Maria');
    expect(texto(fixture)).toContain('Carlos');
  });

  /** Criterio 15: que serán dos pedidos se dice antes de que sorprenda. */
  it('avisa de la división cuando hay más de un vendedor', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [
        grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000),
        grupo('v-2', 'Carlos', [linea(OTRA, 50_000)], 50_000),
      ],
      willSplit: true,
    });
    await bombear(fixture);

    expect(texto(fixture)).toContain('Cada vendedor es un pedido');
  });

  it('no avisa de la división con un solo vendedor', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000)],
      willSplit: false,
    });
    await bombear(fixture);

    expect(texto(fixture)).not.toContain('Cada vendedor es un pedido');
  });

  /**
   * Criterio 16, comprobado en la pantalla y no solo en el contrato.
   *
   * <p>Se busca la palabra suelta y no la subcadena, porque «subtotal» contiene «total»: es el
   * mismo cuidado que hizo falta en la prueba del borde. Lo que no puede aparecer es la
   * palabra por su cuenta, que sería la promesa que RN-076 no deja hacer todavía.
   */
  it('no dice «total» en ninguna parte, y sí dice que el envío se calcula al comprar', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000)],
      willSplit: false,
    });
    await bombear(fixture);

    expect(texto(fixture)).not.toMatch(/\bTotal\b/i);
    expect(texto(fixture)).toContain('Subtotal');
    expect(texto(fixture)).toContain('El costo de envío se calcula al comprar');
  });

  // --- Lo que dejó de estar disponible ------------------------------------

  /** Criterio 21: sigue a la vista, apagado, y con la opción de quitarlo. */
  it('conserva a la vista lo que dejó de estar disponible', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [
        grupo('v-1', 'Ana Maria', [linea(UNA, 100_000), linea(OTRA, 900_000, false)], 100_000),
      ],
      willSplit: false,
    });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelectorAll('.carrito__linea')).toHaveLength(2);
    expect(fixture.nativeElement.querySelectorAll('.carrito__linea--apagada')).toHaveLength(1);
    expect(texto(fixture)).toContain('Ya no está disponible');
  });

  /** Criterio 23: un grupo entero apagado se ve con subtotal en cero y no se oculta. */
  it('no oculta el grupo cuyos productos dejaron de estar disponibles', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000, false)], 0)],
      willSplit: false,
    });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelectorAll('.carrito__grupo')).toHaveLength(1);
    expect(texto(fixture)).not.toContain('Tu carrito está vacío');
  });

  /** Criterio 18: se avisa del cambio de precio, y solo sobre lo que sigue disponible. */
  it('avisa de que el precio cambió', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({
      groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 120_000, true, true)], 120_000)],
      willSplit: false,
    });
    await bombear(fixture);

    expect(texto(fixture)).toContain('El precio cambió');
  });

  // --- El vacío ------------------------------------------------------------

  /** Criterio 19: el vacío es una pantalla y dice además que agregar no aparta nada. */
  it('pinta la pantalla vacía cuando no hay nada', async () => {
    const { fixture, backend } = await montar(true);

    backend.expectOne(`${API}/users/me/cart`).flush({ groups: [], willSplit: false });
    await bombear(fixture);

    expect(texto(fixture)).toContain('Tu carrito está vacío');
    expect(texto(fixture)).toContain('quien pague primero se lo lleva');
  });

  // --- Sin sesión ----------------------------------------------------------

  /**
   * Criterio 13: sin sesión se ve el carrito, con los mismos grupos y subtotales.
   *
   * <p>Y por la ruta pública, que es la razón de que ese recurso exista: sin él el navegador
   * tendría que agrupar y sumar por su cuenta (ADR-0037).
   */
  it('pide el carrito anónimo con lo que guarda el navegador', async () => {
    TestBed.inject(LocalCart).agregar({
      listingId: UNA,
      title: 'Camisa de lino',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(false);

    const llamada = backend.expectOne((peticion) => peticion.url === `${API}/carts`);
    expect(llamada.request.params.getAll('ids')).toEqual([UNA]);
    llamada.flush({
      groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000)],
      willSplit: false,
    });
    await bombear(fixture);

    expect(texto(fixture)).toContain('Subtotal');
  });

  /**
   * Sin sesión y sin nada guardado no se pide nada.
   *
   * <p>`GET /carts` sin identificadores es un 400, y pedirlo para que responda que no hay nada
   * sería fabricar un error en cada visita a un carrito vacío.
   */
  it('no toca la red cuando no hay nada guardado', async () => {
    const { fixture, backend } = await montar(false);

    expect(texto(fixture)).toContain('Tu carrito está vacío');
    backend.verify();
  });

  /**
   * Criterio 21 sin sesión: lo que el servidor no devuelve se pinta con la copia local.
   *
   * <p>Quien no ha entrado pide por identificador y lo que dejó de estar publicado no vuelve
   * (RN-068), así que la copia del navegador es lo único que permite enseñarlo apagado.
   */
  it('pinta apagado lo que el servidor ya no devuelve', async () => {
    const local = TestBed.inject(LocalCart);
    local.agregar({
      listingId: UNA,
      title: 'La que sigue',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });
    local.agregar({
      listingId: OTRA,
      title: 'La que se vendio',
      price: 900_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(false);

    backend
      .expectOne((peticion) => peticion.url === `${API}/carts`)
      .flush({
        groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000)],
        willSplit: false,
      });
    await bombear(fixture);

    expect(texto(fixture)).toContain('La que se vendio');
    expect(texto(fixture)).toContain('Ya no está disponible');
  });

  // --- La fusión -----------------------------------------------------------

  /**
   * Criterios 9 y 12: se pregunta, no se hace sola.
   *
   * <p>Es la decisión de ADR-0037, y aquí es donde se comprueba: al entrar con carrito en el
   * navegador aparece la pregunta y <strong>no sale ninguna petición de fusión</strong> hasta
   * que alguien la acepta. Sin esto, otra persona que entrara en este navegador heredaría un
   * carrito que nunca pidió.
   */
  it('pregunta antes de fusionar y no fusiona sola', async () => {
    TestBed.inject(LocalCart).agregar({
      listingId: UNA,
      title: 'Camisa de lino',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(true);
    backend.expectOne(`${API}/users/me/cart`).flush({ groups: [], willSplit: false });
    await bombear(fixture);

    expect(texto(fixture)).toContain('Encontramos');
    backend.verify();
  });

  it('fusiona al aceptar, y vacía el carrito del navegador', async () => {
    const local = TestBed.inject(LocalCart);
    local.agregar({
      listingId: UNA,
      title: 'Camisa de lino',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(true);
    backend.expectOne(`${API}/users/me/cart`).flush({ groups: [], willSplit: false });
    await bombear(fixture);

    const aceptar = fixture.nativeElement.querySelector(
      '.carrito__fusion .btn-primario',
    ) as HTMLButtonElement;
    aceptar.click();
    await bombear(fixture);

    const llamada = backend.expectOne(`${API}/users/me/cart`);
    expect(llamada.request.method).toBe('POST');
    expect(llamada.request.body).toEqual({ listingIds: [UNA] });
    llamada.flush({
      cart: {
        groups: [grupo('v-1', 'Ana Maria', [linea(UNA, 100_000)], 100_000)],
        willSplit: false,
      },
      notMerged: [],
    });
    await bombear(fixture);

    expect(local.todos()).toEqual([]);

    // La fusión invalida el carrito, así que queda una relectura pendiente.
    backend
      .match((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me/cart`)
      .forEach((peticion) => peticion.flush({ groups: [], willSplit: false }));
  });

  /** Descartar no borra el carrito local: quien dijo que no puede seguir sin entrar. */
  it('al descartar deja de preguntar y no borra lo guardado', async () => {
    const local = TestBed.inject(LocalCart);
    local.agregar({
      listingId: UNA,
      title: 'Camisa de lino',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(true);
    backend.expectOne(`${API}/users/me/cart`).flush({ groups: [], willSplit: false });
    await bombear(fixture);

    const descartar = fixture.nativeElement.querySelector(
      '.carrito__fusion .btn-secundario',
    ) as HTMLButtonElement;
    descartar.click();
    await bombear(fixture);

    expect(fixture.nativeElement.querySelector('.carrito__fusion')).toBeNull();
    expect(local.contiene(UNA)).toBe(true);
  });

  /** Criterio 10: lo que no entró se dice, y se dice cuántos y no por qué. */
  it('dice cuántos no entraron en la fusión', async () => {
    const local = TestBed.inject(LocalCart);
    local.agregar({
      listingId: UNA,
      title: 'Camisa de lino',
      price: 100_000,
      imageUrl: null,
      sellerName: 'Ana Maria',
    });

    const { fixture, backend } = await montar(true);
    backend.expectOne(`${API}/users/me/cart`).flush({ groups: [], willSplit: false });
    await bombear(fixture);

    (
      fixture.nativeElement.querySelector('.carrito__fusion .btn-primario') as HTMLButtonElement
    ).click();
    await bombear(fixture);

    backend.expectOne(`${API}/users/me/cart`).flush({
      cart: { groups: [], willSplit: false },
      notMerged: [UNA],
    });
    await bombear(fixture);

    expect(texto(fixture)).toContain('No pudimos agregar 1');

    backend
      .match((peticion) => peticion.method === 'GET' && peticion.url === `${API}/users/me/cart`)
      .forEach((peticion) => peticion.flush({ groups: [], willSplit: false }));
  });

  // --- El error ------------------------------------------------------------

  it('ofrece reintentar cuando la carga falla', async () => {
    const { fixture, backend } = await montar(true);

    backend
      .expectOne(`${API}/users/me/cart`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(texto(fixture)).toContain('Reintentar');
  });
});
