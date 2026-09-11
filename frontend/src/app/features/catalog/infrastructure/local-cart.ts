import { isPlatformBrowser } from '@angular/common';
import { inject, Injectable, PLATFORM_ID } from '@angular/core';

import { MAXIMO_DE_PRODUCTOS } from '../domain/cart';

const CLAVE = 'sendik.carrito';

/** Que ya se dijo que no a fusionar este carrito. Ver `recordarQueSeDescarto`. */
const CLAVE_DESCARTE = 'sendik.carrito.descartado';

/**
 * La forma de un UUID, para no creerse lo que hay guardado.
 *
 * <p>Un identificador que no es un UUID hacía fallar la fusión entera: el borde lo convierte
 * y lanza, y una sola línea mala se llevaba por delante las diecinueve buenas. El caso borde
 * de la historia decía «un identificador inventado se descarta sin error» y solo valía para
 * los inventados **bien escritos**, que no son los que un almacenamiento corrupto produce.
 */
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/**
 * Lo que el navegador guarda de cada producto.
 *
 * <p><strong>Guarda una copia para pintar y no solo el identificador.</strong> Sin ella no
 * se puede pintar la fila del criterio 21 de algo que el servidor ya no devuelve: quien no
 * ha entrado pide `GET /api/v1/carts?ids=…` y lo que dejó de estar publicado no vuelve
 * (RN-068), así que la única forma de enseñarlo apagado es haberlo guardado antes.
 *
 * <p>Es copia y se trata como tal: la verdad es lo que responda el servidor. Cuando las dos
 * discrepan gana la del servidor, y esta solo se usa para las filas que él no devolvió.
 */
export interface ProductoGuardado {
  readonly listingId: string;
  /** Cuándo entró. Ordena la lista y decide qué se conserva si la fusión no cabe entera. */
  readonly addedAt: number;
  readonly title: string;
  readonly price: number;
  readonly imageUrl: string | null;
  readonly sellerName: string | null;
}

/**
 * El carrito de quien no ha entrado. HU-015, criterios 8 a 13.
 *
 * <p><strong>`localStorage` y no `sessionStorage`</strong>, al revés que la intención de
 * favorito de HU-011. Aquella es una intención que debe morir con la pestaña; esto es un
 * carrito que el criterio 8 pide encontrar al día siguiente en ese mismo navegador.
 *
 * <p><strong>Es dato del cliente y no se cree.</strong> Los identificadores se validan
 * contra la base al pintar y al fusionar, y el precio y el título que responda el servidor
 * mandan sobre lo que diga esto. Un identificador inventado se descarta sin error.
 *
 * <p><strong>No es dato personal mientras viva aquí</strong>, y por eso no está en la
 * descarga de datos: no hay nadie identificado a quien asociarlo. Deja de no serlo en el
 * instante de la fusión, que es cuando pasa a ser filas de `cart_items`
 * (docs/operacion/datos-personales.md).
 *
 * <p><strong>Todo acceso es tolerante a fallo.</strong> En una pestaña de incógnito o con
 * el almacenamiento bloqueado o lleno, `localStorage` lanza al tocarlo. El coste de perder
 * el carrito es que la persona vuelva a agregar; el de una excepción sin capturar es que la
 * ficha no se pinte. Lo que no se hace es fallar en silencio: {@link disponible} permite
 * que la pantalla lo diga.
 *
 * <p>En el servidor de renderizado no existe, y por eso cada método comprueba la plataforma
 * antes de tocarlo.
 */
@Injectable({ providedIn: 'root' })
export class LocalCart {
  private readonly enElNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  /**
   * Si se puede guardar algo aquí.
   *
   * <p>Falso en el servidor y en un navegador con el almacenamiento bloqueado. La pantalla
   * lo usa para decirlo en vez de dejar que el carrito se pierda sin explicación.
   */
  disponible(): boolean {
    if (!this.enElNavegador) {
      return false;
    }
    try {
      const prueba = `${CLAVE}.prueba`;
      localStorage.setItem(prueba, '1');
      localStorage.removeItem(prueba);
      return true;
    } catch {
      return false;
    }
  }

  /**
   * Lo que lleva, lo más reciente primero.
   *
   * <p>El orden es el de inserción y no se ordena al leer: `agregar` pone lo nuevo delante,
   * que es el mismo orden del gesto con el que el servidor devuelve el carrito de la cuenta.
   */
  todos(): readonly ProductoGuardado[] {
    return this.leer();
  }

  /** Si ese producto ya está dentro. Es lo que pinta el control de la ficha sin sesión. */
  contiene(listingId: string): boolean {
    return this.leer().some((producto) => producto.listingId === listingId);
  }

  /**
   * Agrega, si cabe. Devuelve si entró.
   *
   * <p><strong>Idempotente, igual que el servidor</strong> (criterio 4): agregar lo que ya
   * está no crea una segunda fila ni mueve su fecha, y responde que sí porque el resultado
   * es el que se pidió. Sin esa condición, volver a pulsar con el carrito lleno respondería
   * que no cabe algo que ya está dentro.
   *
   * <p>El tope lo hace cumplir este archivo o ninguno: aquí no hay servidor (RN-097).
   */
  agregar(producto: Omit<ProductoGuardado, 'addedAt'>): boolean {
    const actuales = this.leer();

    if (actuales.some((guardado) => guardado.listingId === producto.listingId)) {
      return true;
    }
    if (actuales.length >= MAXIMO_DE_PRODUCTOS) {
      return false;
    }

    this.escribir([{ ...producto, addedAt: Date.now() }, ...actuales]);
    return true;
  }

  /** Quita. Idempotente: quitar lo que no está no es un error. */
  quitar(listingId: string): void {
    this.escribir(this.leer().filter((producto) => producto.listingId !== listingId));
  }

  /**
   * Deja anotado que se dijo que no a fusionar, para no volver a preguntar.
   *
   * <p>Va en el navegador y no en memoria: la pregunta la recibe quien entra, que puede no
   * ser quien llenó el carrito, y en memoria volvía en cada recarga.
   */
  recordarQueSeDescarto(): void {
    if (!this.enElNavegador) {
      return;
    }
    try {
      localStorage.setItem(CLAVE_DESCARTE, '1');
    } catch {
      // Sin almacenamiento se volverá a preguntar. Es molesto y no es incorrecto.
    }
  }

  seDescartoLaFusion(): boolean {
    if (!this.enElNavegador) {
      return false;
    }
    try {
      return localStorage.getItem(CLAVE_DESCARTE) !== null;
    } catch {
      return false;
    }
  }

  /**
   * Lo vacía. Lo llama la fusión al terminar.
   *
   * <p>Lo llama la fusión **cuando sale bien**, y no cuando falla. Un carrito local que
   * sobrevive a su fusión se volvería a ofrecer a la siguiente persona que entrara en ese
   * navegador (criterio 12); pero vaciarlo tras un intento fallido deja a quien lo armó sin
   * el carrito del navegador y sin el de la cuenta, que es perder datos por una caída de red.
   */
  vaciar(): void {
    if (!this.enElNavegador) {
      return;
    }
    try {
      localStorage.removeItem(CLAVE);
      // El descarte se va con el carrito: si mañana se arma otro, la pregunta vuelve a
      // tener sentido y no puede quedar silenciada para siempre por un no de hace meses.
      localStorage.removeItem(CLAVE_DESCARTE);
    } catch {
      // Sin almacenamiento no había nada que borrar.
    }
  }

  private leer(): readonly ProductoGuardado[] {
    if (!this.enElNavegador) {
      return [];
    }

    try {
      const guardado = localStorage.getItem(CLAVE);
      if (guardado === null) {
        return [];
      }

      const productos: unknown = JSON.parse(guardado);
      if (!Array.isArray(productos)) {
        this.vaciar();
        return [];
      }

      // Se filtra lo que no tenga la forma esperada en vez de confiar en el `as`. Esto lo
      // escribió una versión anterior de la aplicación, o alguien a mano desde la consola:
      // una fila sin `listingId` reventaría al pintar y el carrito entero se perdería por
      // una línea mala.
      return productos
        .filter((producto): producto is ProductoGuardado => esProductoGuardado(producto))
        .slice(0, MAXIMO_DE_PRODUCTOS);
    } catch {
      // Un valor corrupto no es un carrito. Se quita para no volver a tropezar.
      this.vaciar();
      return [];
    }
  }

  private escribir(productos: readonly ProductoGuardado[]): void {
    if (!this.enElNavegador) {
      return;
    }
    try {
      localStorage.setItem(CLAVE, JSON.stringify(productos));
    } catch {
      // Sin almacenamiento el carrito se pierde y la persona vuelve a agregar. La pantalla
      // ya lo advierte con `disponible()`; aquí lo único que no se puede hacer es romper.
    }
  }
}

function esProductoGuardado(valor: unknown): valor is ProductoGuardado {
  if (typeof valor !== 'object' || valor === null) {
    return false;
  }

  const producto = valor as Record<string, unknown>;

  return (
    typeof producto['listingId'] === 'string' &&
    UUID.test(producto['listingId']) &&
    typeof producto['addedAt'] === 'number' &&
    typeof producto['title'] === 'string' &&
    typeof producto['price'] === 'number'
  );
}
