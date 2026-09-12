import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type { AddressDraft, Place, ShippingAddress } from '../domain/shipping-address';

interface AddressBookResponse {
  readonly addresses: readonly ShippingAddress[];
}

interface DepartmentsResponse {
  readonly departments: readonly Place[];
}

interface MunicipalitiesResponse {
  readonly municipalities: readonly Place[];
}

/**
 * Adaptador HTTP de la libreta de direcciones. HU-016.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p><strong>Todas necesitan token</strong>, incluidas las dos de la división
 * político-administrativa. El identificador de quien pide sale del token y no hay forma de
 * nombrar la libreta de otra persona: no existe donde escribir un identificador ajeno.
 *
 * <p><strong>El identificador se codifica al meterlo en la ruta</strong>, por lo mismo que
 * en los favoritos: hoy llega siempre de la respuesta del servidor, y el día que llegue de
 * la barra de direcciones —que el enrutador entrega ya descodificado— estos métodos serían
 * la forma de emitir escrituras contra rutas que nadie eligió.
 */
@Injectable({ providedIn: 'root' })
export class AddressesApi {
  private readonly http = inject(HttpClient);

  /** La libreta entera, de la más reciente a la más antigua. Sin paginar. */
  async libreta(): Promise<readonly ShippingAddress[]> {
    const respuesta = await firstValueFrom(
      this.http.get<AddressBookResponse>('users/me/addresses'),
    );
    return respuesta.addresses;
  }

  /** Guarda una nueva. Devuelve la creada, ya con los nombres del municipio y el departamento. */
  async agregar(datos: AddressDraft): Promise<ShippingAddress> {
    return firstValueFrom(this.http.post<ShippingAddress>('users/me/addresses', datos));
  }

  /** Cambia sus datos. La misma fila: no crea una segunda. */
  async editar(id: string, datos: AddressDraft): Promise<ShippingAddress> {
    return firstValueFrom(
      this.http.put<ShippingAddress>(`users/me/addresses/${encodeURIComponent(id)}`, datos),
    );
  }

  /** La quita. Idempotente: quitar lo que no está responde 204 igual. */
  async quitar(id: string): Promise<void> {
    await firstValueFrom(this.http.delete<void>(`users/me/addresses/${encodeURIComponent(id)}`));
  }

  /**
   * Elige cuál es la predeterminada.
   *
   * <p>`PUT` sobre un recurso singular de la persona y no un verbo en la ruta: marcar es
   * reemplazar el valor de ese recurso.
   */
  async marcarPredeterminada(id: string): Promise<void> {
    await firstValueFrom(this.http.put<void>('users/me/default-address', { addressId: id }));
  }

  /** Los treinta y tres departamentos, ordenados por nombre. */
  async departamentos(): Promise<readonly Place[]> {
    const respuesta = await firstValueFrom(
      this.http.get<DepartmentsResponse>('locations/departments'),
    );
    return respuesta.departments;
  }

  /**
   * Los municipios activos de un departamento.
   *
   * <p>Dos peticiones y no una: los departamentos son treinta y tres y los municipios más
   * de mil, así que traer el árbol entero para pintar el primer selector sería traer
   * cuarenta veces lo que se necesita.
   */
  async municipios(departamento: string): Promise<readonly Place[]> {
    const respuesta = await firstValueFrom(
      this.http.get<MunicipalitiesResponse>(
        `locations/departments/${encodeURIComponent(departamento)}/municipalities`,
      ),
    );
    return respuesta.municipalities;
  }
}
