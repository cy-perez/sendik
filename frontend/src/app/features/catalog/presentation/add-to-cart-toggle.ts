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
import { TranslocoPipe } from '@jsverse/transloco';

import { CartStore } from '../application/cart.store';
import type { PublicListing } from '../domain/public-listing';

/**
 * El control del carrito en la ficha. HU-015, criterios 1 a 8 y 25.
 *
 * <p><strong>Funciona sin sesión y sin pedirla</strong> (criterio 8), y ahí está la
 * diferencia con el control de favorito de HU-011: aquel lleva a entrar porque un favorito
 * es de una cuenta; un carrito no lo es hasta que alguien entra. Lo que se pulsa sin sesión
 * se guarda en el navegador y sigue ahí al día siguiente.
 *
 * <p><strong>No se ofrece sobre la publicación propia</strong> (criterio 5), y eso lo dice
 * el servidor: la sesión que guarda el navegador no lleva el identificador de la cuenta.
 * Esconder el control no es la regla —RN-092 se comprueba al agregar— sino evitar que
 * alguien pulse para enterarse. Sin sesión no hay publicación propia posible, así que el
 * control se ofrece siempre y la comprobación llega en la fusión.
 *
 * <p><strong>El estado no se comunica solo por color</strong> (criterio 25): el icono cambia
 * de contorno a relleno y el texto del botón cambia con él. Son dos señales reales.
 *
 * <p><strong>Sin `aria-pressed`.</strong> El nombre accesible cambia con el estado, y la APG
 * de ARIA dice que entonces no se pone: con los dos, un lector lee «Quitar del carrito,
 * botón de alternancia, pulsado», que se entiende al revés.
 *
 * <p><strong>Va en tinta y nunca en bronce.</strong> El acento aparece una vez por pantalla y
 * en la ficha ya lo tiene la insignia de vendedor verificado.
 */
@Component({
  selector: 'sendik-add-to-cart-toggle',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './add-to-cart-toggle.html',
  styleUrl: './add-to-cart-toggle.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddToCartToggle {
  private readonly store = inject(CartStore);

  readonly publicacion = input.required<PublicListing>();

  /** El nombre del vendedor, para guardarlo en la copia local del criterio 21. */
  readonly nombreDelVendedor = input<string | null>(null);

  /**
   * Lo que se le anuncia a un lector de pantalla, y solo tras una acción.
   *
   * <p>Nulo hasta que alguien pulsa, por lo mismo que en el control de favorito: una región
   * viva que nace con texto dentro suelta en algunos lectores un anuncio que nadie pidió en
   * cada carga de ficha. El estado inicial ya lo dice el nombre del botón.
   */
  private readonly anunciable = signal(false);

  /**
   * Lo que se anuncia es el <strong>resultado</strong>, no la acción siguiente.
   *
   * <p>Decía `toggle.add` en la rama falsa, que es la etiqueta del botón: al quitar, un lector
   * locutaba «Agregar al carrito», o sea una orden donde tenía que haber una confirmación. El
   * control de favorito ya lo había resuelto con cadenas de resultado separadas de las de
   * acción, y aquí se copió mal.
   */
  protected readonly anuncio = computed(() => {
    if (!this.anunciable()) {
      return null;
    }
    return this.dentro() ? 'catalog.cart.toggle.added' : 'catalog.cart.toggle.notInCart';
  });

  constructor() {
    // Se lo dice al almacén el propio control y no la pantalla que lo contiene: recibe la
    // publicación por entrada, así que ya la conoce. Es el mismo criterio que el control de
    // favorito, y lo que hace que funcione donde se ponga.
    effect(() => {
      const publicacion = this.publicacion();
      untracked(() => this.store.abrirFicha(publicacion.id));
    });

    // Y le dice que se dejó de mirar. Sin esto el almacén se queda con la última ficha
    // abierta y pediría su estado desde cualquier otra pantalla.
    inject(DestroyRef).onDestroy(() => this.store.abrirFicha(null));
  }

  protected readonly dentro = computed(() => this.store.control().dentro);

  protected readonly seOfrece = computed(() => this.store.control().seOfrece);

  protected readonly enCurso = computed(() => this.store.control().enCurso);

  protected readonly error = computed(() => this.store.errorDelControl());

  /** El tope, para el mensaje que lo nombra (criterio 7). */
  protected readonly tope = this.store.tope;

  /**
   * El texto del botón, que es también su nombre accesible.
   *
   * <p>Cambia con el estado y describe la acción, no el estado: quien lo lee sabe qué pasa si
   * lo pulsa.
   */
  protected readonly etiqueta = computed(() =>
    this.dentro() ? 'catalog.cart.toggle.remove' : 'catalog.cart.toggle.add',
  );

  protected pulsar(): void {
    this.anunciable.set(true);
    this.store.alternar(this.publicacion(), this.nombreDelVendedor());
  }
}
