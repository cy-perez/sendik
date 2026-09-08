import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import type { Category } from '../../../shared/domain/listing';
import { CatalogStore } from '../application/catalog.store';
import { categoriaPorSlugs, nombreDeCategoria } from '../domain/public-listing';
import { ProductCard } from './product-card';

/**
 * El catálogo público. HU-009, criterios 1 a 10.
 *
 * <p>Una sola pantalla para las tres rutas —todo, una familia y una categoría—, porque son
 * el mismo listado con distinto filtro. Separarlas en tres componentes duplicaría los tres
 * estados de carga y la rejilla.
 *
 * <p><strong>Es la primera pantalla del proyecto que sirve a alguien sin cuenta.</strong>
 * No hay guardas, no hay sesión y no se pide token: lo que se ve es lo mismo con sesión y
 * sin ella (RN-068).
 *
 * <p>Con `FEATURE_CATALOG` apagada la ruta sigue existiendo y la API responde 404, así que
 * la pantalla muestra su estado de error. Es lo mismo que hace `/publicar` y lo que
 * permite que `rutas.spec.ts` recorra todas las rutas sin saber qué bandera está
 * encendida.
 */
@Component({
  selector: 'sendik-catalog-page',
  standalone: true,
  imports: [ProductCard, RouterLink, TranslocoPipe],
  templateUrl: './catalog-page.html',
  styleUrl: './catalog-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogPage {
  private readonly store = inject(CatalogStore);
  private readonly idioma = inject(TranslocoService);

  private readonly parametros = toSignal(inject(ActivatedRoute).paramMap);

  protected readonly familiaSlug = computed(() => this.parametros()?.get('familia') ?? null);
  protected readonly categoriaSlug = computed(() => this.parametros()?.get('categoria') ?? null);

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

      untracked(() => this.store.abrir(categoria === null ? null : categoria.id));
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

  protected verMas(): void {
    this.store.siguienteTramo();
  }

  protected reintentar(): void {
    this.store.reintentar();
  }
}
