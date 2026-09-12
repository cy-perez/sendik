import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiError } from '../../../core/http/api-error';
import { AddressesStore } from '../application/addresses.store';
import { comoSeLee, laPredeterminada, type ShippingAddress } from '../domain/shipping-address';
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
  private readonly injector = inject(Injector);

  /**
   * El encabezado, que recoge el foco cuando se destruye lo que se acaba de pulsar.
   *
   * <p>Quitar una dirección destruye su propio botón; marcar otra como predeterminada
   * destruye el botón «Usar como predeterminada» de esa tarjeta, porque está bajo un `@if`;
   * cerrar el formulario destruye el formulario con el foco dentro. En los tres casos el
   * navegador manda el foco a `<body>` y quien navega con teclado vuelve al principio del
   * documento. Es el patrón que `cart-page` ya usaba, y lo cazó la revisión de accesibilidad.
   */
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

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

  /** Con qué quedó la predeterminada tras el relevo, para poder decir cuál (criterio 11). */
  protected readonly avisoNombre = signal('');

  /**
   * Lo que falló al quitar o al marcar.
   *
   * <p>Las dos mutaciones no tenían ningún estado de error: si la petición fallaba —otra
   * pestaña ya borró la dirección, la sesión venció, la bandera está apagada— la promesa
   * quedaba rechazada sin capturar, la tarjeta seguía en pantalla y no se decía nada. La
   * pantalla tenía sus tres estados para la lectura y ninguno para las escrituras.
   */
  protected readonly falloDeAccion = signal<string | null>(null);

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
    this.limpiarAvisos();
    this.abierto.set('nueva');
  }

  protected abrirEdicion(direccion: ShippingAddress): void {
    this.limpiarAvisos();
    this.abierto.set(direccion);
  }

  /** Al cerrar, el foco vuelve arriba: el formulario que lo tenía deja de existir. */
  protected cerrarFormulario(): void {
    this.abierto.set(null);
    this.devolverElFoco();
  }

  protected alGuardar(): void {
    this.abierto.set(null);
    this.anunciar('addresses.notices.saved');
    this.devolverElFoco();
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
    this.limpiarAvisos();

    const eraLaPredeterminada = direccion.isDefault;
    let libreta: readonly ShippingAddress[] | null;

    try {
      await this.store.borrado.mutateAsync(direccion.id);
      libreta = await this.store.refrescarLibreta();
    } catch (error) {
      // Sin mover el foco: el `role="alert"` es asertivo y moverlo lo pisaria.
      this.falloDeAccion.set(claveDelError(error));
      return;
    }

    // **Si el refresco falló, no se deduce nada.** `refetch()` de TanStack no rechaza:
    // ante un fallo resuelve sin datos, y con una lista vacía se anunciaría «quitada» aunque
    // el servidor sí hubiera relevado otra. Lo cazó la segunda revisión de accesibilidad.
    if (libreta === null) {
      this.falloDeAccion.set('addresses.errors.stale');
      return;
    }

    // Cuál quedó se lee de la libreta recién refrescada y no se adivina aquí: la regla del
    // relevo vive en el servidor (RN-099).
    const releva = eraLaPredeterminada ? laPredeterminada(libreta) : null;
    if (releva !== null) {
      this.anunciar('addresses.notices.defaultChanged', releva.recipientName);
    } else {
      this.anunciar('addresses.notices.removed');
    }
    this.devolverElFoco();
  }

  protected async marcarPredeterminada(direccion: ShippingAddress): Promise<void> {
    if (this.ocupada()) {
      return;
    }
    this.limpiarAvisos();

    try {
      await this.store.marcado.mutateAsync(direccion.id);
      await this.store.refrescarLibreta();
    } catch (error) {
      this.falloDeAccion.set(claveDelError(error));
      return;
    }

    this.anunciar('addresses.notices.defaultChanged', direccion.recipientName);
    this.devolverElFoco();
  }

  protected reintentar(): void {
    this.limpiarAvisos();
    void this.store.libreta.refetch();
  }

  private anunciar(clave: string, nombre = ''): void {
    this.avisoNombre.set(nombre);
    this.aviso.set(clave);
  }

  private limpiarAvisos(): void {
    this.aviso.set(null);
    this.falloDeAccion.set(null);
  }

  /**
   * El foco vuelve al encabezado, que es lo más cerca que hay de «donde estabas».
   *
   * <p>`afterNextRender` porque el elemento al que se devuelve tiene que existir ya: la
   * libreta se acaba de repintar.
   */
  private devolverElFoco(): void {
    afterNextRender(() => this.titulo()?.nativeElement.focus(), { injector: this.injector });
  }
}

/**
 * El código del error, traducido a clave de Transloco.
 *
 * <p>La misma función que el formulario, y por lo mismo: `ApiError` ya sabe convertirse en su
 * clave y los dos códigos de esta historia viven en `errors.byCode` con todos los demás.
 */
function claveDelError(error: unknown): string {
  return error instanceof ApiError ? error.translationKey : 'errors.fallback';
}
