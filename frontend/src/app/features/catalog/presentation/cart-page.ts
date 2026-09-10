import { ChangeDetectionStrategy, Component, computed, DestroyRef, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { CartStore } from '../application/cart.store';
import { estaVacio } from '../domain/cart';
import { precioFormateado, type Money } from '../../../shared/domain/listing';

/**
 * La pantalla del carrito. HU-015, criterios 13 a 24.
 *
 * <p><strong>Funciona con sesión y sin ella</strong> (criterio 13), y esa es la diferencia
 * con la lista de favoritos: aquella se le niega a quien no ha entrado porque un favorito es
 * de una cuenta; un carrito no lo es hasta que alguien entra. Sin sesión los grupos y los
 * subtotales los arma igualmente el servidor, con los identificadores que trae el navegador.
 *
 * <p><strong>No se renderiza con datos en el servidor.</strong> Es dato de una persona
 * —o de un navegador— y el HTML servido no puede llevarlo dentro. Quien pida la dirección
 * recibe el esqueleto y el carrito llega al hidratar.
 *
 * <p><strong>El grupo por vendedor es la unidad visual y no la fila</strong>: encabezado con
 * el vendedor, sus productos, y el subtotal cerrando el grupo. Que serán pedidos distintos
 * tiene que verse, no leerse en una nota al pie (criterio 15).
 *
 * <p><strong>En ninguna parte aparece la palabra total</strong> (criterio 16). Se dice
 * subtotal por vendedor y se dice que el envío se calcula al comprar: RN-076 exige tres
 * cifras y aquí solo existe la primera.
 *
 * <p>El estado vacío es una pantalla y no una línea (criterio 19). Es lo primero que ve todo
 * el mundo, porque todo el mundo empieza con el carrito vacío.
 */
@Component({
  selector: 'sendik-cart-page',
  standalone: true,
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './cart-page.html',
  styleUrl: './cart-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CartPage {
  private readonly store = inject(CartStore);
  private readonly idioma = inject(TranslocoService);

  constructor() {
    // La consulta no se habilita por haber sesión sino porque alguien está mirando esta
    // pantalla. Es lo mismo que aprendió la lista de favoritos: sin esto, el almacén —que es
    // de raíz y lo comparte el control de la ficha— pediría el carrito al abrir cualquier
    // producto.
    this.store.abrirCarrito(true);
    inject(DestroyRef).onDestroy(() => this.store.abrirCarrito(false));
  }

  protected readonly carrito = computed(() => this.store.contenido());

  protected readonly grupos = computed(() => this.carrito().groups);

  protected readonly seDividira = computed(() => this.carrito().willSplit);

  /**
   * Mientras la sesión no esté resuelta se enseña el esqueleto y no el carrito vacío.
   *
   * <p>Es la diferencia entre «todavía no sé» y «no llevas nada». Sin ella, quien recarga su
   * carrito ve un instante la pantalla vacía antes de sus propios productos.
   *
   * <p><strong>`isLoading` y no `isPending`</strong>, que es la corrección de un esqueleto
   * eterno. Una consulta deshabilitada —que es lo que pasa sin sesión y sin nada guardado, o
   * sea el carrito vacío de cualquiera que llega por primera vez— se queda en `isPending`
   * para siempre, porque nunca llega a resolverse. `isLoading` es «pendiente **y** pidiendo»,
   * que es lo que de verdad significa estar cargando.
   */
  protected readonly cargando = computed(
    () => !this.store.sesionResuelta() || this.store.carrito.isLoading(),
  );

  protected readonly fallo = computed(() => this.store.carrito.isError());

  /**
   * Vacío de verdad: ni grupos del servidor ni filas apagadas del navegador.
   *
   * <p>Las dos cosas cuentan. Un carrito cuyos dos productos se vendieron no está vacío: está
   * lleno de filas apagadas, y enseñar la pantalla de bienvenida ahí borraría justo lo que el
   * criterio 21 quiere que se vea.
   */
  protected readonly vacio = computed(
    () => estaVacio(this.carrito()) && this.noDisponibles().length === 0,
  );

  /** Lo que el servidor no devolvió y el navegador sí guarda. Criterio 21, sin sesión. */
  protected readonly noDisponibles = computed(() => this.store.noDisponiblesLocales());

  /** Si hay que preguntar por la fusión. Criterios 9 y 12, ADR-0037. */
  protected readonly hayQueFusionar = computed(() => this.store.hayQueFusionar());

  protected readonly fusionando = computed(() => this.store.fusionar.isPending());

  protected readonly noEntraron = computed(() => this.store.noEntraron());

  protected readonly cuantosGuardados = computed(() => this.store.cuantosGuardadosLocalmente());

  /** Si el navegador no puede guardar, se dice en vez de perder el carrito en silencio. */
  protected readonly sinAlmacenamiento = computed(
    () => !this.store.haySesion() && !this.store.puedeGuardarLocalmente(),
  );

  /**
   * El dinero, con la configuración regional activa.
   *
   * <p>Con `Intl` y la misma función que la tarjeta del catálogo, no con un pipe propio: los
   * precios se escriben igual en todo el sitio o no se escriben igual en ninguna parte.
   */
  protected formatear(valor: Money): string {
    return precioFormateado(valor, this.idioma.getActiveLang());
  }

  protected quitar(listingId: string): void {
    this.store.quitarDelCarrito(listingId);
  }

  protected reintentar(): void {
    this.store.reintentar();
  }

  protected aceptarLaFusion(): void {
    this.store.aceptarLaFusion();
  }

  protected descartarLaFusion(): void {
    this.store.descartarLaFusion();
  }
}
