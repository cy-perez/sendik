import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

import type { Category } from '../../../shared/domain/listing';
import type { CatalogPage, PublicListing, SellerProfile } from '../domain/public-listing';
import { aParametros, SIN_CRITERIOS, type SearchCriteria } from '../domain/search-criteria';

/**
 * Adaptador HTTP del catálogo público. HU-009.
 *
 * <p>Las rutas van relativas: el interceptor les antepone la base de la API, que es
 * configuración de ejecución y no algo que esta capa deba conocer.
 *
 * <p><strong>Ninguna llamada de aquí manda token</strong>, y no porque se le quite: es que
 * no hace falta. Son las cuatro rutas públicas de la historia, y el interceptor de sesión
 * añade el token si lo hay sin que cambie la respuesta (RN-068).
 *
 * <p><strong>El cursor viaja opaco y no se interpreta.</strong> Se recibe, se guarda y se
 * devuelve. El día que el orden del catálogo cambie —y cambiará cuando llegue la
 * relevancia de Fase 3— su contenido cambia sin que este archivo se entere.
 */
@Injectable({ providedIn: 'root' })
export class CatalogApi {
  private readonly http = inject(HttpClient);

  /**
   * Un tramo del catálogo, o de lo que casa con lo que se pidió. HU-014.
   *
   * <p><strong>La misma ruta para las dos cosas</strong>, porque buscar es listar este
   * mismo recurso con más condiciones: sin criterios, lo que responde es el catálogo tal
   * cual (criterio 8). Un `/search` aparte habría duplicado aquí la paginación y la forma
   * de la respuesta.
   *
   * @param categoria identificador de una categoría o de una familia; el servidor
   *   resuelve las hojas que cuelgan de ella
   * @param criterios el texto, los filtros y el orden. Los que estén vacíos no viajan
   */
  async listado(opciones: {
    readonly cursor?: string | null;
    readonly categoria?: string | null;
    readonly criterios?: SearchCriteria;
    readonly limite?: number;
  }): Promise<CatalogPage> {
    let parametros = new HttpParams().set('limit', String(opciones.limite ?? 24));

    if (opciones.cursor) {
      parametros = parametros.set('cursor', opciones.cursor);
    }
    if (opciones.categoria) {
      parametros = parametros.set('category', opciones.categoria);
    }

    // `condition` y `color` se repiten en la dirección para pedir varios valores, que es
    // lo que `append` hace y `set` no: con `set`, pedir azul y verde pediría solo verde.
    for (const [nombre, valor] of Object.entries(
      aParametros(opciones.criterios ?? SIN_CRITERIOS),
    )) {
      for (const uno of Array.isArray(valor) ? valor : [valor]) {
        parametros = parametros.append(nombre, uno);
      }
    }

    return firstValueFrom(this.http.get<CatalogPage>('listings', { params: parametros }));
  }

  /** Una publicación publicada. 404 si no lo está o si no existe: son la misma respuesta. */
  async una(id: string): Promise<PublicListing> {
    return firstValueFrom(this.http.get<PublicListing>(`listings/${id}`));
  }

  /** El árbol activo, por familias. El mismo que usa el formulario de publicar. */
  async categorias(): Promise<readonly Category[]> {
    return firstValueFrom(this.http.get<Category[]>('categories'));
  }

  async vendedor(id: string): Promise<SellerProfile> {
    return firstValueFrom(this.http.get<SellerProfile>(`sellers/${id}`));
  }

  async publicacionesDelVendedor(
    id: string,
    cursor?: string | null,
    limite = 24,
  ): Promise<CatalogPage> {
    let parametros = new HttpParams().set('limit', String(limite));
    if (cursor) {
      parametros = parametros.set('cursor', cursor);
    }

    return firstValueFrom(
      this.http.get<CatalogPage>(`sellers/${id}/listings`, { params: parametros }),
    );
  }
}
