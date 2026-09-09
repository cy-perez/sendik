import { computed, inject, Injectable, signal } from '@angular/core';
import { injectInfiniteQuery, injectQuery } from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import type { Category } from '../../../shared/domain/listing';
import type { CatalogPage, PublicListing } from '../domain/public-listing';
import { aParametros, SIN_CRITERIOS, type SearchCriteria } from '../domain/search-criteria';
import { CatalogApi } from '../infrastructure/catalog.api';
import { queryKeys } from './query-keys';

/**
 * El estado del catálogo público. HU-009.
 *
 * <p>Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>El listado es una consulta infinita y no una paginada</strong>, porque el
 * contrato lo pagina por cursor: no hay número de página que pedir, hay un «por dónde
 * seguir» que sale de la respuesta anterior. TanStack lo encadena y esta clase expone lo
 * único que a una pantalla le importa —los elementos acumulados y si queda más—, no la
 * lista de tramos.
 *
 * <p>Ninguna consulta reintenta. Con los tres reintentos de por omisión, quien se queda
 * sin catálogo mira un esqueleto siete segundos antes de que la pantalla le diga que algo
 * falló.
 */
@Injectable({ providedIn: 'root' })
export class CatalogStore {
  private readonly api = inject(CatalogApi);
  private readonly sesion = inject(SessionStore);

  /**
   * Si quien pregunta modera, que decide qué forma devuelve el servidor.
   *
   * <p>Entra en la clave de la ficha y en nada más: el listado y el perfil responden
   * igual para todo el mundo (RN-068).
   */
  private readonly comoModerador = computed(
    () => this.sesion.user()?.roles.includes('MODERATOR') === true,
  );

  /**
   * Qué categoría se está viendo.
   *
   * <p>Tres valores y no dos, y el tercero es el que importa: `undefined` significa
   * **todavía no se sabe**, y con él la consulta no sale. En una dirección de categoría el
   * identificador no se conoce hasta que llega el árbol, así que sin este estado la
   * pantalla pedía el catálogo entero primero y la categoría después: dos viajes, y el
   * primero pintaba un instante lo que no era.
   *
   * <p>`null` es «todo el catálogo» y sí pide.
   *
   * <p>Es una señal y no un parámetro: la página la fija al resolver la ruta y TanStack
   * vuelve a pedir sola cuando cambia, que es lo que hace falta al navegar entre
   * categorías sin recargar.
   */
  private readonly categoria = signal<string | null | undefined>(undefined);

  /**
   * Qué se está buscando y con qué filtros. HU-014.
   *
   * <p>Señal aparte de la categoría, y no un solo objeto con las dos cosas, porque cambian
   * por caminos distintos: la categoría la fija la ruta y los criterios la cadena de
   * consulta. Juntarlas obligaría a que resolver la categoría reescribiera los filtros.
   *
   * <p>Sin nada puesto vale {@link SIN_CRITERIOS}, y entonces lo que se pide es el catálogo
   * tal cual: buscar nada no es una pantalla distinta (criterio 8).
   */
  private readonly criterios = signal<SearchCriteria>(SIN_CRITERIOS);

  /** El árbol activo. Con frescura larga: lo cambia una migración, no el uso. */
  readonly categories = injectQuery(() => ({
    queryKey: queryKeys.categories,
    queryFn: () => this.api.categorias(),
    staleTime: 60 * 60 * 1000,
    retry: false,
  }));

  readonly listado = injectInfiniteQuery(() => ({
    queryKey: queryKeys.list(this.categoria() ?? null, this.criteriosEnClave()),
    queryFn: ({ pageParam }: { pageParam: string | null }) =>
      this.api.listado({
        cursor: pageParam,
        categoria: this.categoria() ?? null,
        criterios: this.criterios(),
      }),
    // Sin esto sale un viaje de más en cada dirección de categoría: ver `categoria`.
    enabled: this.categoria() !== undefined,
    initialPageParam: null as string | null,
    // Nulo significa «no hay más», que es como TanStack sabe que llegó al final. Es
    // exactamente lo que el servidor manda en `nextCursor` del último tramo.
    getNextPageParam: (ultimo: CatalogPage) => ultimo.nextCursor,
    retry: false,
  }));

  /**
   * Todo lo traído hasta ahora, en un solo arreglo.
   *
   * <p>La pantalla pinta una rejilla de publicaciones, no una lista de tramos: que la
   * respuesta venga por tramos es del transporte y no tiene por qué llegar a la plantilla.
   */
  readonly publicaciones = computed<readonly PublicListing[]>(
    () => this.listado.data()?.pages.flatMap((tramo) => tramo.items) ?? [],
  );

  readonly hayMas = computed(() => this.listado.hasNextPage());

  readonly trayendoMas = computed(() => this.listado.isFetchingNextPage());

  /**
   * Cuál publicación está abierta en la ficha.
   *
   * <p>Nula mientras no haya ninguna, y con ella la consulta no sale: es el mismo criterio
   * que el listado y evita una petición a `listings/null` al montar la pantalla.
   */
  private readonly ficha = signal<string | null>(null);

  /** Cuál vendedor está abierto en su perfil. */
  private readonly perfil = signal<string | null>(null);

  /**
   * La publicación de la ficha.
   *
   * <p>Sin reintentos: el 404 es una respuesta normal aquí. RN-068 hace que «no existe» y
   * «ya no está publicada» respondan igual, así que reintentar tres veces un 404 solo
   * retrasa el mensaje que la pantalla ya sabe dar.
   */
  readonly publicacion = injectQuery(() => ({
    queryKey: queryKeys.one(this.ficha() ?? 'ninguna', this.comoModerador()),
    queryFn: () => this.api.una(this.ficha() ?? ''),
    enabled: this.ficha() !== null,
    retry: false,
  }));

  readonly vendedor = injectQuery(() => ({
    queryKey: queryKeys.seller(this.perfil() ?? 'ninguno'),
    queryFn: () => this.api.vendedor(this.perfil() ?? ''),
    enabled: this.perfil() !== null,
    retry: false,
  }));

  readonly deVendedor = injectInfiniteQuery(() => ({
    queryKey: queryKeys.sellerListings(this.perfil() ?? 'ninguno'),
    queryFn: ({ pageParam }: { pageParam: string | null }) =>
      this.api.publicacionesDelVendedor(this.perfil() ?? '', pageParam),
    enabled: this.perfil() !== null,
    initialPageParam: null as string | null,
    getNextPageParam: (ultimo: CatalogPage) => ultimo.nextCursor,
    retry: false,
  }));

  readonly publicacionesDelVendedor = computed<readonly PublicListing[]>(
    () => this.deVendedor.data()?.pages.flatMap((tramo) => tramo.items) ?? [],
  );

  /** La ficha fija cuál publicación se está viendo al resolver la ruta. */
  abrirFicha(id: string | null): void {
    this.ficha.set(id);
  }

  /** El perfil fija cuál vendedor se está viendo al resolver la ruta. */
  abrirPerfil(id: string | null): void {
    this.perfil.set(id);
  }

  siguienteTramoDelVendedor(): void {
    if (this.deVendedor.hasNextPage() && !this.deVendedor.isFetchingNextPage()) {
      void this.deVendedor.fetchNextPage();
    }
  }

  /** La página fija cuál categoría se está viendo al resolver la ruta. */
  abrir(categoria: string | null): void {
    this.categoria.set(categoria);
  }

  /**
   * La página fija qué se está buscando al leer la dirección.
   *
   * <p>No dispara la petición por su cuenta: cambia la clave de la consulta y TanStack pide
   * sola, que es lo mismo que ya hace al navegar entre categorías.
   */
  buscar(criterios: SearchCriteria): void {
    this.criterios.set(criterios);
  }

  /** Lo pedido ahora mismo, para que la pantalla pinte las fichas de lo que está puesto. */
  loPedido(): SearchCriteria {
    return this.criterios();
  }

  /**
   * Los criterios como cadena estable, que es lo que entra en la clave de consulta.
   *
   * <p>Ordenada por nombre de parámetro a propósito: dos peticiones equivalentes escritas
   * en distinto orden tienen que compartir entrada de caché, o pedir azul y verde daría una
   * consulta distinta que pedir verde y azul.
   */
  private criteriosEnClave(): string {
    const parametros = aParametros(this.criterios());

    return Object.keys(parametros)
      .sort()
      .map((nombre) => `${nombre}=${[parametros[nombre]].flat().join(',')}`)
      .join('&');
  }

  /**
   * Mientras no se sepa qué filtro va, no se pide nada.
   *
   * <p>Lo usa la pantalla al salir de una categoría hacia otra que todavía no ha
   * resuelto: sin esto se quedaría pintando el listado de la anterior.
   */
  esperar(): void {
    this.categoria.set(undefined);
  }

  siguienteTramo(): void {
    if (this.listado.hasNextPage() && !this.listado.isFetchingNextPage()) {
      void this.listado.fetchNextPage();
    }
  }

  reintentar(): void {
    void this.listado.refetch();
  }

  /** El árbol tal como llegó, o vacío mientras carga o si falló. */
  arbol(): readonly Category[] {
    return this.categories.data() ?? [];
  }
}
