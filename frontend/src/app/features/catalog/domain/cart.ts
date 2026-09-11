import type { Money } from '../../../shared/domain/listing';
import type { PublicListing } from './public-listing';

/**
 * RN-097: cuántos productos caben en el carrito.
 *
 * <p><strong>El número está aquí además de en el dominio del backend, y no es una copia
 * por descuido.</strong> El carrito sin sesión vive en el navegador y ahí no hay servidor
 * que haga cumplir nada: quien no ha entrado tiene que ser frenado por este archivo o por
 * ninguno. Lo mismo con el mensaje del criterio 7, que nombra el tope: el cuerpo de error
 * del backend lleva `code` y `traceId` y nada más, porque el texto que ve la persona lo
 * escribe Transloco.
 *
 * <p>Si el tope cambia, cambia en dos sitios. Lo alternativo —pedirlo al servidor al
 * arrancar— sería una petición más en cada visita para leer un número que no cambia, y aun
 * así habría que tener un valor por omisión mientras no llegue.
 */
export const MAXIMO_DE_PRODUCTOS = 20;

/**
 * Un producto dentro del carrito. HU-015.
 *
 * <p><strong>`available` es un booleano y no un motivo.</strong> Vendido, pausado o bajado
 * por un moderador dan lo mismo aquí y dan lo mismo en la pantalla (criterio 22, RN-068):
 * la respuesta no trae por qué, así que no hay forma de filtrarlo por descuido.
 */
export interface CartLine {
  readonly listing: PublicListing;
  readonly addedAt: string;
  readonly available: boolean;
  readonly priceChanged: boolean;
}

/**
 * Los productos de un mismo vendedor, con su subtotal. RN-090.
 *
 * <p><strong>`subtotal` llega calculado y no se recalcula aquí.</strong> Es la razón de
 * que `GET /api/v1/carts` exista: con la suma escrita también en TypeScript habría dos
 * implementaciones de la misma cifra —una para quien entró y otra para quien no— y
 * divergir sería cuestión de tiempo (ADR-0037).
 *
 * <p>`sellerName` puede faltar. Un perfil que no responde no tumba el carrito: el grupo se
 * pinta con lo que hay.
 */
export interface CartGroup {
  readonly sellerId: string;
  readonly sellerName: string | null;
  readonly sellerVerified: boolean;
  readonly lines: readonly CartLine[];
  readonly subtotal: Money;
  readonly allUnavailable: boolean;
}

/**
 * El carrito entero.
 *
 * <p><strong>No hay `total`, y su ausencia es la regla.</strong> RN-076 obliga a enseñar
 * tres cifras —precio base, costo de envío y total— y aquí solo existe la primera. Un
 * total con la suma de los subtotales mentiría sobre lo que se va a pagar (RN-096).
 */
export interface Cart {
  readonly groups: readonly CartGroup[];
  readonly willSplit: boolean;
}

/** Lo que devuelve la fusión: cómo quedó y qué no entró (criterios 9 y 10). */
export interface MergeResult {
  readonly cart: Cart;
  readonly notMerged: readonly string[];
}

/** Cuántos productos lleva, disponibles o no. Es lo que cuenta contra RN-097. */
export function cuantosLleva(carrito: Cart): number {
  return carrito.groups.reduce((suma, grupo) => suma + grupo.lines.length, 0);
}

/** Un carrito vacío es cero grupos, no una ausencia. La pantalla vacía se pinta de aquí. */
export function estaVacio(carrito: Cart): boolean {
  return carrito.groups.length === 0;
}

/** El carrito que se pinta cuando todavía no hay nada que pedir ni nada que pedirle. */
export const CARRITO_VACIO: Cart = { groups: [], willSplit: false };
