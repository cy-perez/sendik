import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import type { Category } from '../../../shared/domain/listing';
import { CatalogStore } from '../application/catalog.store';
import { categoriaPorSlugs, nombreDeCategoria } from '../domain/public-listing';
import {
  aParametros,
  desdeParametros,
  esBusqueda,
  filtrosPuestos,
  sinFiltro,
  sinFiltros,
  type FiltroPuesto,
  type SearchCriteria,
} from '../domain/search-criteria';
import { CatalogFilters } from './catalog-filters';
import { ProductCard } from './product-card';
import { SearchBox } from './search-box';

/**
 * El catálogo público y la búsqueda. HU-009, criterios 1 a 10; HU-014 entera.
 *
 * <p>Una sola pantalla para las tres rutas —todo, una familia y una categoría— y también
 * para la búsqueda, porque son el mismo listado con distinto filtro. El criterio 8 lo dice
 * al revés y con las mismas consecuencias: buscar nada no es una pantalla distinta.
 *
 * <p><strong>Es la primera pantalla del proyecto que sirve a alguien sin cuenta.</strong>
 * No hay guardas, no hay sesión y no se pide token: lo que se ve es lo mismo con sesión y
 * sin ella (RN-068, RN-081).
 *
 * <p><strong>La dirección manda.</strong> La categoría viaja en la ruta y los criterios en
 * la cadena de consulta, y de ahí sale lo que se pide: un resultado se puede compartir,
 * recargar y abrir en otra pestaña con el mismo contenido (criterio 15). Nada de esto vive
 * solo en memoria.
 *
 * <p>Con `FEATURE_CATALOG` o `FEATURE_SEARCH` apagadas la ruta sigue existiendo y la API
 * responde 404, así que la pantalla muestra su estado de error. Es lo mismo que hace
 * `/publicar` y lo que permite que `rutas.spec.ts` recorra todas las rutas sin saber qué
 * bandera está encendida.
 */
@Component({
  selector: 'sendik-catalog-page',
  standalone: true,
  imports: [CatalogFilters, ProductCard, RouterLink, SearchBox, TranslocoPipe],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  private readonly store = inject(CatalogStore);
  private readonly idioma = inject(TranslocoService);
  private readonly ruta = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly meta = inject(Meta);

  private readonly parametros = toSignal(this.ruta.paramMap);
  private readonly consulta = toSignal(this.ruta.queryParamMap);

  protected readonly familiaSlug = computed(() => this.parametros()?.get('familia') ?? null);
  protected readonly categoriaSlug = computed(() => this.parametros()?.get('categoria') ?? null);

  /** Lo que se está buscando, tal como lo dice la dirección. */
  protected readonly criterios = computed<SearchCriteria>(() => {
    const consulta = this.consulta();
    return consulta === undefined
      ? desdeParametros({ get: () => null, getAll: () => [] })
      : desdeParametros(consulta);
  });

  protected readonly fichas = computed(() => filtrosPuestos(this.criterios()));

  protected readonly hayFiltros = computed(() => this.fichas().length > 0);

  protected readonly arbol = computed<readonly Category[]>(() => this.store.arbol());

  protected readonly cargandoArbol = computed(() => this.store.categories.isPending());
  protected readonly falloElArbol = computed(() => this.store.categories.isError());

  /**
   * La categoría abierta, resuelta contra el árbol.
   *
   * <p>Nula cuando no hay ninguna en la dirección. **Indefinida** cuando la hay pero no
   * está en el árbol, que es distinto: eso es el criterio 9 y se pinta como no encontrada,
   * no como listado vacío.
   */
  protected readonly abierta = computed<Category | null | undefined>(() => {
    const familia = this.familiaSlug();
    if (familia === null || this.arbol().length === 0) {
      return null;
    }
    return categoriaPorSlugs(this.arbol(), familia, this.categoriaSlug()) ?? undefined;
  });

  /**
   * Si la dirección trae una categoría y el árbol todavía no ha llegado.
   *
   * <p>Mientras dure, no se pide el listado: pedirlo sin filtro traería el catálogo
   * entero y lo pintaría un instante antes de sustituirlo por el de la categoría.
   */
  protected readonly resolviendo = computed(
    () => this.familiaSlug() !== null && this.arbol().length === 0 && !this.falloElArbol(),
  );

  protected readonly noExiste = computed(() => this.abierta() === undefined);

  protected readonly titulo = computed(() => {
    const categoria = this.abierta();
    return categoria === null || categoria === undefined
      ? null
      : nombreDeCategoria(categoria, this.idioma.getActiveLang());
  });

  protected readonly publicaciones = this.store.publicaciones;
  protected readonly hayMas = this.store.hayMas;
  protected readonly trayendoMas = this.store.trayendoMas;

  protected readonly cargando = computed(() => this.store.listado.isPending());
  protected readonly fallo = computed(() => this.store.listado.isError());
  protected readonly vacio = computed(
    () => !this.cargando() && !this.fallo() && this.publicaciones().length === 0,
  );

  constructor() {
    // Sincroniza el filtro del listado con la dirección. Es un efecto porque sincroniza
    // con algo externo al marco —el estado de la consulta—, que es el único uso que
    // frontend/CLAUDE.md admite.
    effect(() => {
      const criterios = this.criterios();

      // Todavía no se sabe qué filtro va: el árbol viene en camino.
      if (this.resolviendo()) {
        untracked(() => this.store.esperar());
        return;
      }

      const categoria = this.abierta();

      // Una categoría que no está en el árbol no pide nada: se pinta el criterio 9.
      if (categoria === undefined) {
        untracked(() => this.store.esperar());
        return;
      }

      untracked(() => {
        this.store.buscar(criterios);
        this.store.abrir(categoria === null ? null : categoria.id);
      });
    });

    // Criterio 24: una búsqueda no se indexa. Se resuelve en el servidor, así que la
    // etiqueta ya viaja en el HTML que llega y ningún rastreador ve la página sin ella.
    effect(() => {
      if (esBusqueda(this.criterios())) {
        this.meta.updateTag({ name: 'robots', content: 'noindex' });
      } else {
        // Las categorías y las fichas siguen indexadas: de ahí vienen las visitas.
        this.meta.removeTag("name='robots'");
      }
    });
  }

  /** Las familias del árbol, que son el primer nivel de la navegación. */
  protected familias(): readonly Category[] {
    return this.arbol();
  }

  /** Las hijas de la familia abierta, o vacío si no hay ninguna abierta. */
  protected hijas(): readonly Category[] {
    const familia = this.familiaSlug();
    if (familia === null) {
      return [];
    }
    return this.arbol().find((candidata) => candidata.slug === familia)?.children ?? [];
  }

  protected nombre(categoria: Category): string {
    return nombreDeCategoria(categoria, this.idioma.getActiveLang());
  }

  /**
   * Buscar es ir al catálogo entero. RN-083.
   *
   * <p>Escribir en la caja **suelta la categoría** que se estaba navegando, y no es un
   * detalle de implementación: quien busca «tenis» sin darse cuenta de que estaba dentro de
   * Accesorios leería «no hay» donde sí hay. La categoría no desaparece como idea —vuelve a
   * ponerse desde la navegación, que conserva lo buscado—, cambia de sitio.
   */
  protected buscar(texto: string | null): void {
    void this.router.navigate(['/catalogo'], {
      queryParams: aParametros({ ...this.criterios(), q: texto }),
    });
  }

  /** Los filtros se quedan donde se está: acotan lo que ya se está viendo. */
  protected filtrar(criterios: SearchCriteria): void {
    void this.router.navigate([], {
      relativeTo: this.ruta,
      queryParams: aParametros(criterios),
    });
  }

  protected quitar(ficha: FiltroPuesto): void {
    this.filtrar(sinFiltro(this.criterios(), ficha));
  }

  /** La salida del vacío honesto: se van los filtros, no lo que se estaba buscando. */
  protected quitarTodos(): void {
    this.filtrar(sinFiltros(this.criterios()));
  }

  protected verMas(): void {
    this.store.siguienteTramo();
  }

  protected reintentar(): void {
    this.store.reintentar();
  }
}
