import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { FavoritesStore } from '../application/favorites.store';
import { ProductCard } from './product-card';

/**
 * La lista propia de favoritos. HU-011, criterios 11 a 16.
 *
 * <p><strong>Reutiliza la tarjeta y la rejilla del catálogo.</strong> No estrena nada: son
 * las mismas publicaciones y la misma forma de mirarlas, y una segunda tarjeta sería un
 * segundo sitio donde arreglar lo que se arregle en la primera.
 *
 * <p><strong>Es la única pantalla del catálogo que depende de la sesión</strong>, y por eso
 * no se renderiza con datos en el servidor: allí no hay cookie de nadie (ADR-0025 no
 * aplica aquí). Quien pida la dirección recibe el esqueleto y la lista llega al hidratar,
 * que es lo correcto para algo privado: el HTML servido no puede llevar dentro lo que a
 * alguien le interesa.
 *
 * <p>Sin sesión no se ve la lista de nadie: se ofrece entrar (criterio 16). Y no se
 * redirige, se explica; una redirección desde una dirección que alguien escribió a
 * propósito hace pensar que se equivocó.
 *
 * <p>El estado vacío es una pantalla y no una línea (criterio 15). Es lo primero que ve
 * todo el mundo, porque todo el mundo empieza sin favoritos.
 */
@Component({
  selector: 'sendik-favorites-page',
  standalone: true,
  imports: [ProductCard, RouterLink, TranslocoPipe],
  templateUrl: './favorites-page.html',
  styleUrl: './favorites-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FavoritesPage {
  private readonly store = inject(FavoritesStore);

  constructor() {
    // La consulta no se habilita por haber sesión sino porque alguien está mirando esta
    // pantalla. Sin esto, el almacén —que es de raíz y lo comparte el control de la
    // ficha— pedía la lista entera al abrir cualquier producto.
    this.store.abrirLista(true);
    inject(DestroyRef).onDestroy(() => this.store.abrirLista(false));
  }

  protected readonly haySesion = computed(() => this.store.haySesion());

  /**
   * Mientras la sesión no esté resuelta se enseña el esqueleto, no la invitación a entrar.
   *
   * <p>Es la diferencia entre «todavía no sé» y «no hay nadie». Sin ella, quien recarga su
   * lista ve un instante «entra para guardar publicaciones» antes de sus propios
   * favoritos, que es exactamente el parpadeo que `SessionStore` documenta.
   */
  protected readonly cargando = computed(
    () => !this.store.sesionResuelta() || (this.haySesion() && this.store.listado.isPending()),
  );

  protected readonly fallo = computed(() => this.store.listado.isError());

  protected readonly publicaciones = computed(() => this.store.publicaciones());

  protected readonly vacio = computed(
    () => !this.cargando() && !this.fallo() && this.publicaciones().length === 0,
  );

  protected readonly hayMas = computed(() => this.store.hayMas());

  protected readonly trayendoMas = computed(() => this.store.trayendoMas());

  /** Los huecos del esqueleto. Ocho, como en el catálogo: es la misma rejilla. */
  protected readonly huecos = [0, 1, 2, 3, 4, 5, 6, 7];

  protected verMas(): void {
    this.store.siguienteTramo();
  }

  protected reintentar(): void {
    this.store.reintentar();
  }
}
