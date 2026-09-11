import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  inject,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AddressesStore } from '../application/addresses.store';
import { comoSeLee, type ShippingAddress } from '../domain/shipping-address';
import { AddressForm } from './address-form';

/**
 * La libreta de direcciones de entrega. HU-016, criterios 1 y 8 a 14.
 *
 * <p><strong>Sin guard, como `/mi-cuenta` y `/carrito`.</strong> Quien llega sin sesión ve
 * una explicación y la forma de entrar; no se redirige, porque una redirección desde una
 * dirección que alguien escribió a propósito hace pensar que se equivocó.
 *
 * <p><strong>No se renderiza con datos en el servidor.</strong> Es dato personal: el HTML
 * servido no puede llevar dentro dónde vive alguien. Quien pida la dirección recibe el
 * esqueleto y la libreta llega al hidratar.
 *
 * <p><strong>El acento bronce no aparece en esta pantalla.</strong> No hay insignia de
 * vendedor verificado en una libreta de direcciones, y el acento va una vez por pantalla y
 * siempre en lo mismo. Los botones van en tinta.
 *
 * <p><strong>En ninguna parte se promete que se pueda entregar allí.</strong> RN-103: la
 * cobertura está sin comprobar (RN-080) y la pantalla no puede afirmar lo que nadie ha
 * contrastado.
 */
@Component({
  selector: 'sendik-addresses-page',
  imports: [AddressForm, RouterLink, TranslocoPipe],
  templateUrl: './addresses-page.html',
  styleUrl: './addresses-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AddressesPage {
  private readonly store = inject(AddressesStore);

  constructor() {
    // La consulta se habilita porque alguien está mirando esta pantalla, no por haber
    // sesión: el almacén es de raíz y sin esto pediría la libreta desde cualquier página.
    this.store.abrirLibreta(true);
    inject(DestroyRef).onDestroy(() => this.store.abrirLibreta(false));
  }

  /** Qué se está editando: `null` es nada abierto, `'nueva'` es el formulario en blanco. */
  protected readonly abierto = signal<ShippingAddress | 'nueva' | null>(null);

  /**
   * El aviso de qué pasó, como clave de Transloco.
   *
   * <p>Existe porque el proyecto no tiene diálogos: lo que hubiera dicho una confirmación
   * se dice después, y la región viva de arriba lo anuncia a quien no lo ve.
   */
  protected readonly aviso = signal<string | null>(null);

  protected readonly haySesion = computed(() => this.store.haySesion());

  /**
   * Mientras la sesión no esté resuelta se enseña el esqueleto, no la invitación a entrar.
   *
   * <p>Es la diferencia entre «todavía no sé» y «no hay nadie». Sin ella, quien recarga su
   * libreta ve un instante «entra para guardar direcciones» antes de las suyas.
   */
  protected readonly cargando = computed(
    () => !this.store.sesionResuelta() || (this.haySesion() && this.store.libreta.isPending()),
  );

  protected readonly fallo = computed(() => this.store.libreta.isError());
  protected readonly direcciones = computed(() => this.store.direcciones());
  protected readonly ocupada = computed(() => this.store.ocupada());

  protected readonly vacia = computed(
    () => !this.cargando() && !this.fallo() && this.direcciones().length === 0,
  );

  protected readonly formularioAbierto = computed(() => this.abierto() !== null);

  protected readonly enEdicion = computed(() => {
    const actual = this.abierto();
    return actual === null || actual === 'nueva' ? null : actual;
  });

  protected readonly huecos = [0, 1];

  protected comoSeLee(direccion: ShippingAddress): string {
    return comoSeLee(direccion);
  }

  protected abrirNueva(): void {
    this.aviso.set(null);
    this.abierto.set('nueva');
  }

  protected abrirEdicion(direccion: ShippingAddress): void {
    this.aviso.set(null);
    this.abierto.set(direccion);
  }

  protected cerrarFormulario(): void {
    this.abierto.set(null);
  }

  protected alGuardar(): void {
    this.abierto.set(null);
    this.aviso.set('addresses.notices.saved');
  }

  /**
   * Quita una dirección.
   *
   * <p><strong>Sin diálogo de confirmación</strong>, y no por descuido: el proyecto no tiene
   * uno, lo que se pierde al borrar por error se vuelve a escribir, y estrenar un diálogo
   * para esto sería estrenar un mecanismo. Lo que sí se hace es no ponerlo junto a la acción
   * principal.
   *
   * <p>Después se dice cuál quedó como predeterminada (criterio 11), leyéndolo de la libreta
   * recién refrescada y no adivinándolo aquí: la regla del relevo vive en el servidor.
   */
  protected async quitar(direccion: ShippingAddress): Promise<void> {
    if (this.ocupada()) {
      return;
    }

    const eraLaPredeterminada = direccion.isDefault;
    await this.store.borrado.mutateAsync(direccion.id);

    if (eraLaPredeterminada && this.direcciones().length > 0) {
      this.aviso.set('addresses.notices.defaultChanged');
    } else {
      this.aviso.set('addresses.notices.removed');
    }
  }

  protected async marcarPredeterminada(direccion: ShippingAddress): Promise<void> {
    if (this.ocupada()) {
      return;
    }

    await this.store.marcado.mutateAsync(direccion.id);
    this.aviso.set('addresses.notices.defaultChanged');
  }

  protected reintentar(): void {
    void this.store.libreta.refetch();
  }
}
