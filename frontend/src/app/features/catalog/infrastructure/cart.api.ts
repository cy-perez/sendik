import { HttpClient, HttpParams } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { firstValueFrom } from 'rxjs';

import type { Cart, MergeResult } from '../domain/cart';

/**
 * El estado del control del carrito para una publicación. HU-015, criterios 1 y 5.
 *
 * <p>`eligible` es lo que permite no ofrecer el control sobre la publicación propia sin que
 * el navegador sepa quién es el dueño: la sesión que guarda no lleva el identificador de la
 * cuenta. La regla vive en el servidor (RN-092) y esto es lo que responde.
 */
export interface CartItemState {
  readonly inCart: boolean;
  readonly eligible: boolean;
}

/**
 * Adaptador HTTP del carrito. HU-015.
 *
 * <p><strong>Dos rutas y no una, y la diferencia es quién pregunta.</strong> Las cinco de
 * `users/me/cart` necesitan token y son de quien lo tiene; `carts` es pública y sirve para
 * armar el carrito de quien no ha entrado con los identificadores que guarda su navegador.
 * Las dos devuelven la misma forma, que es la razón de que la segunda exista: sin ella la
 * suma habría que escribirla también aquí (ADR-0037).
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p><strong>El identificador se codifica al meterlo en la ruta</strong>, por lo mismo que en
 * `FavoritesApi`: el día que llegue de la barra de direcciones —que el enrutador entrega ya
 * descodificado, así que un `%2F` llega como barra— estos métodos serían la forma de emitir
 * escrituras con el token de quien mira contra rutas que nadie eligió.
 */
@Injectable({ providedIn: 'root' })
export class CartApi {
  private readonly http = inject(HttpClient);

  /** El carrito de quien tiene sesión, ya agrupado y con sus subtotales. */
  async carrito(): Promise<Cart> {
    return firstValueFrom(this.http.get<Cart>('users/me/cart'));
  }

  /**
   * El carrito de quien no ha entrado, armado por el servidor con estos identificadores.
   *
   * <p>Repetible y no separado por comas —`?ids=a&ids=b`—, que es como el contrato ya pasa
   * `condition` y `color`. El orden se respeta: es el único que existe sin filas en la base.
   */
  async carritoAnonimo(ids: readonly string[]): Promise<Cart> {
    let parametros = new HttpParams();
    for (const id of ids) {
      parametros = parametros.append('ids', id);
    }

    return firstValueFrom(this.http.get<Cart>('carts', { params: parametros }));
  }

  /**
   * Agrega. Idempotente: repetirlo no crea una segunda línea ni falla (criterio 4).
   *
   * <p>`PUT` y no `POST`, que es lo que el servidor expone y lo que corresponde a una
   * operación que se puede repetir sin cambiar el resultado.
   */
  async agregar(listingId: string): Promise<void> {
    await firstValueFrom(
      this.http.put<void>(`users/me/cart/items/${encodeURIComponent(listingId)}`, {}),
    );
  }

  /** Quita. Idempotente también: quitar lo que no está responde 204. */
  async quitar(listingId: string): Promise<void> {
    await firstValueFrom(
      this.http.delete<void>(`users/me/cart/items/${encodeURIComponent(listingId)}`),
    );
  }

  /** Si ese producto está en el carrito, y si se puede agregar. */
  async estado(listingId: string): Promise<CartItemState> {
    return firstValueFrom(
      this.http.get<CartItemState>(`users/me/cart/items/${encodeURIComponent(listingId)}`),
    );
  }

  /**
   * Une al carrito de la cuenta lo que traía el navegador. Criterios 9 y 10.
   *
   * <p>Devuelve el carrito ya fusionado —para no pedirlo otra vez— y lo que no entró, que es
   * lo que el navegador usa para limpiar su copia local.
   */
  async fusionar(listingIds: readonly string[]): Promise<MergeResult> {
    return firstValueFrom(this.http.post<MergeResult>('users/me/cart', { listingIds }));
  }
}
