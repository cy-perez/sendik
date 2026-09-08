import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  signal,
  untracked,
} from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { FavoritesStore } from '../application/favorites.store';

/**
 * El control de favorito de la ficha. HU-011, criterios 1 a 10 y 17.
 *
 * <p><strong>Se ofrece también a quien no tiene sesión</strong> (criterio 7). Pulsarlo
 * lleva a entrar y, al volver, el favorito ya está guardado sin tener que volver a pulsar:
 * la intención queda anotada en el navegador y el destino viaja en la dirección del ingreso
 * (ADR-0029).
 *
 * <p><strong>No se ofrece sobre la publicación propia</strong> (criterio 5), y eso lo dice
 * el servidor: la sesión que guarda el navegador no lleva el identificador de la cuenta,
 * así que la pantalla no puede compararlo con el vendedor. Esconder el control no es la
 * regla —RN-072 se comprueba al marcar— sino evitar que alguien pulse para enterarse.
 *
 * <p><strong>El estado marcado no se comunica solo por color</strong> (criterio 17): el
 * icono cambia de contorno a relleno y el texto del botón cambia con él. Son dos señales
 * reales; hubo una tercera —el borde— y era inerte, porque en modo claro `--color-texto` y
 * `--color-primario` son el mismo valor.
 *
 * <p><strong>Sin `aria-pressed`.</strong> El nombre accesible cambia con el estado, y la
 * APG de ARIA dice que entonces no se pone: con los dos, un lector lee «Quitar de
 * favoritos, botón de alternancia, pulsado», que se entiende como «quitar está activado».
 *
 * <p><strong>Va en tinta y nunca en bronce.</strong> El acento aparece una vez por pantalla
 * y en la ficha ya lo tiene la insignia de vendedor verificado.
 */
@Component({
  selector: 'sendik-favorite-toggle',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './favorite-toggle.html',
  styleUrl: './favorite-toggle.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FavoriteToggle {
  private readonly store = inject(FavoritesStore);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);

  readonly publicacion = input.required<string>();

  /**
   * Lo que se le anuncia a un lector de pantalla, y solo tras una acción.
   *
   * <p>Nulo hasta que alguien pulsa. Una región viva que nace con texto dentro se comporta
   * distinto según el lector, y en los que la locutan suelta un anuncio que nadie pidió en
   * cada carga de ficha. El estado inicial ya lo dice el nombre del botón.
   */
  private readonly anunciable = signal(false);

  protected readonly anuncio = computed(() => {
    if (!this.anunciable()) {
      return null;
    }
    return this.marcado() ? 'catalog.favorite.saved' : 'catalog.favorite.notSaved';
  });

  constructor() {
    // Se lo dice al almacen el mismo, no la pantalla que lo contiene. El
    // control recibe la publicación por entrada, así que ya la conoce; dejar que la ficha
    // llamara al almacén repartiría la misma responsabilidad en dos sitios y ataría el
    // control a que la pantalla se acordara. Así funciona donde se ponga.
    effect(() => {
      const id = this.publicacion();
      untracked(() => this.store.abrirFicha(id));
    });

    // Y le dice que se dejó de mirar. Sin esto, el almacén se queda con la última ficha
    // abierta: quien sale de un producto, entra desde la cabecera por otra razón y vuelve,
    // dejaría al almacén creyendo que sigue en aquella.
    inject(DestroyRef).onDestroy(() => this.store.abrirFicha(null));

    // La intención que quedó del ingreso (criterios 8, 9 y 10). Depende de que la sesión
    // esté resuelta a propósito: leerla mientras es «desconocida» la descartaría en cada
    // recarga, que es justo el caso que existe para cubrir. El almacén la consume una sola
    // vez y la borra siempre.
    effect(() => {
      const id = this.publicacion();
      const resuelta = this.store.sesionResuelta();

      if (!resuelta) {
        return;
      }
      // El pase viene en la direccion de vuelta. Sin el, la intencion no se consume: es lo
      // que impide que se la lleve quien entre despues en esta misma pestana.
      const pase = this.ruta.snapshot.queryParamMap.get('fav');
      untracked(() => this.store.retomarIntencion(id, pase));
    });
  }

  protected readonly marcado = computed(() => this.store.control().marcado);

  protected readonly seOfrece = computed(() => this.store.control().seOfrece);

  protected readonly enCurso = computed(() => this.store.control().enCurso);

  protected readonly error = computed(() => this.store.errorDelControl());

  /**
   * El texto del botón, que es también su nombre accesible.
   *
   * <p>Cambia con el estado y no describe el estado sino la acción: quien lo lee sabe qué
   * pasa si lo pulsa. Que ahora mismo esté marcado lo dice `aria-pressed`.
   */
  protected readonly etiqueta = computed(() =>
    this.marcado() ? 'catalog.favorite.remove' : 'catalog.favorite.add',
  );

  protected pulsar(): void {
    this.anunciable.set(true);

    const resultado = this.store.alternar(this.publicacion());
    if (resultado.que !== 'hay-que-entrar') {
      return;
    }

    // La dirección a la que volver viaja en la URL; la intención, no (ADR-0029). Lo que sí
    // viaja es el pase, que no es la acción ni sirve por sí solo: sin la intención guardada
    // en esta pestaña no marca nada.
    void this.router.navigate(['/ingresar'], {
      queryParams: {
        redirectTo: `/producto/${this.publicacion()}?fav=${resultado.pase}`,
      },
    });
  }
}
