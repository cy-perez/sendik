import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { MAXIMO_DE_PRODUCTOS } from '../domain/cart';
import { LocalCart } from './local-cart';

/**
 * El carrito del navegador. HU-015, criterios 8, 10, 11 y 12.
 *
 * <p>Es la mitad del carrito que no tiene servidor detrás: el tope de RN-097 lo hace cumplir
 * este archivo o no lo hace nadie, y una copia corrupta que reviente al leer se lleva por
 * delante el carrito entero de alguien.
 */
describe('LocalCart', () => {
  /** La misma clave que usa el almacén. Se toca a mano para simular lo que otro escribió. */
  const CLAVE = 'sendik.carrito';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';
  const OTRA = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  let carrito: LocalCart;

  const producto = (listingId: string) => ({
    listingId,
    title: 'Camisa de lino color hueso',
    price: 185_000,
    imageUrl: null,
    sellerName: 'Ana Maria',
  });

  /**
   * Una fila tal como queda guardada, con su fecha.
   *
   * <p>Distinta de {@link producto} a propósito: aquella es lo que se le pasa a `agregar`, que
   * pone la fecha; esta es lo que hay en el almacenamiento. Escribir la primera a mano produce
   * filas sin `addedAt`, que el almacén descarta con razón —y descartarlas es justo lo que
   * comprueba la prueba de más abajo—.
   */
  const guardado = (listingId: string, cuando = 1_757_500_000_000) => ({
    ...producto(listingId),
    addedAt: cuando,
  });

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    carrito = TestBed.inject(LocalCart);
  });

  afterEach(() => {
    vi.restoreAllMocks();
    localStorage.clear();
  });

  it('guarda un producto y lo encuentra', () => {
    expect(carrito.agregar(producto(ID))).toBe(true);

    expect(carrito.contiene(ID)).toBe(true);
    expect(carrito.todos()).toHaveLength(1);
  });

  /** Criterio 8: sigue ahí al recargar, porque vive en localStorage y no en memoria. */
  it('sobrevive a que se vuelva a construir el almacén', () => {
    carrito.agregar(producto(ID));

    const otro = TestBed.inject(LocalCart);

    expect(otro.contiene(ID)).toBe(true);
  });

  it('quita, y quitar lo que no está no es un error', () => {
    carrito.agregar(producto(ID));

    carrito.quitar(ID);
    carrito.quitar(ID);

    expect(carrito.contiene(ID)).toBe(false);
  });

  /**
   * RN-091 y criterio 4: un producto entra una sola vez.
   *
   * <p>Y responde que sí, porque el resultado es el que se pidió. Sin esa condición, volver a
   * pulsar con el carrito lleno respondería que no cabe algo que ya está dentro.
   */
  it('es idempotente y no mueve la fecha del que ya estaba', () => {
    carrito.agregar(producto(ID));
    const cuando = carrito.todos()[0]!.addedAt;

    expect(carrito.agregar(producto(ID))).toBe(true);

    expect(carrito.todos()).toHaveLength(1);
    expect(carrito.todos()[0]!.addedAt).toBe(cuando);
  });

  /** El orden es el del gesto: lo último agregado, primero. */
  it('pone lo más reciente delante', () => {
    carrito.agregar(producto(ID));
    carrito.agregar(producto(OTRA));

    expect(carrito.todos().map((p) => p.listingId)).toEqual([OTRA, ID]);
  });

  // --- RN-097: el tope, que aquí no lo hace cumplir nadie más --------------

  it('rechaza el producto veintiuno', () => {
    for (let i = 0; i < MAXIMO_DE_PRODUCTOS; i++) {
      expect(carrito.agregar(producto(`${ID}-${i}`))).toBe(true);
    }

    expect(carrito.agregar(producto(OTRA))).toBe(false);
    expect(carrito.todos()).toHaveLength(MAXIMO_DE_PRODUCTOS);
  });

  /** El borde por los dos lados: veinte vale y veintiuno no. */
  it('admite el producto veinte', () => {
    for (let i = 0; i < MAXIMO_DE_PRODUCTOS - 1; i++) {
      carrito.agregar(producto(`${ID}-${i}`));
    }

    expect(carrito.agregar(producto(OTRA))).toBe(true);
  });

  /** Y el carrito lleno sigue aceptando lo que ya tiene dentro. */
  it('no rechaza el reintento de algo que ya está dentro con el carrito lleno', () => {
    for (let i = 0; i < MAXIMO_DE_PRODUCTOS; i++) {
      carrito.agregar(producto(`${ID}-${i}`));
    }

    expect(carrito.agregar(producto(`${ID}-0`))).toBe(true);
  });

  // --- Lo que otro escribió ------------------------------------------------

  /**
   * Es dato del cliente y no se cree.
   *
   * <p>Una fila sin `listingId` reventaría al pintar y el carrito entero se perdería por una
   * línea mala. Se descarta la fila, no el carrito.
   */
  it('descarta las filas que no tienen la forma esperada y conserva las demás', () => {
    localStorage.setItem(
      CLAVE,
      JSON.stringify([guardado(ID), { title: 'sin identificador' }, null, 'texto suelto']),
    );

    expect(carrito.todos()).toHaveLength(1);
    expect(carrito.contiene(ID)).toBe(true);
  });

  it('descarta un valor corrupto entero y no revienta', () => {
    localStorage.setItem(CLAVE, 'esto no es JSON');

    expect(carrito.todos()).toEqual([]);
    expect(localStorage.getItem(CLAVE)).toBeNull();
  });

  it('descarta lo que no es una lista', () => {
    localStorage.setItem(CLAVE, JSON.stringify({ listingId: ID }));

    expect(carrito.todos()).toEqual([]);
  });

  /** Ni siquiera un archivo escrito a mano puede saltarse el tope. */
  it('no devuelve más del tope aunque el almacenamiento traiga más', () => {
    const demasiados = Array.from({ length: MAXIMO_DE_PRODUCTOS + 5 }, (_, i) =>
      guardado(`${ID}-${i}`),
    );
    localStorage.setItem(CLAVE, JSON.stringify(demasiados));

    expect(carrito.todos()).toHaveLength(MAXIMO_DE_PRODUCTOS);
  });

  // --- Sin almacenamiento --------------------------------------------------

  /**
   * En incógnito o con el almacenamiento lleno, `localStorage` lanza al tocarlo.
   *
   * <p>El coste de perder el carrito es que la persona vuelva a agregar; el de una excepción
   * sin capturar es que la ficha no se pinte. Lo que no se hace es fallar en silencio:
   * `disponible()` es lo que permite a la pantalla decirlo.
   */
  it('no revienta cuando el almacenamiento lanza, y lo dice', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('QuotaExceededError');
    });

    expect(carrito.disponible()).toBe(false);
    expect(() => carrito.agregar(producto(ID))).not.toThrow();
  });

  it('no revienta cuando la lectura lanza', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('SecurityError');
    });

    expect(carrito.todos()).toEqual([]);
  });

  // --- La fusión -----------------------------------------------------------

  /**
   * Criterio 12: se vacía al fusionar, siempre.
   *
   * <p>Un carrito local que sobrevive a su fusión se le vuelve a ofrecer a la siguiente
   * persona que entre en este navegador, que es exactamente lo que el criterio prohíbe.
   */
  it('se vacía del todo', () => {
    carrito.agregar(producto(ID));
    carrito.agregar(producto(OTRA));

    carrito.vaciar();

    expect(carrito.todos()).toEqual([]);
    expect(localStorage.getItem(CLAVE)).toBeNull();
  });
});
