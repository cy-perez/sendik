import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  ElementRef,
  inject,
  Injector,
  signal,
  untracked,
  viewChild,
  viewChildren,
} from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { ListingStore } from '../application/listing.store';
import { ModerationTrail } from './moderation-trail';
import {
  precioFormateado,
  tomasDelVendedor,
  type Listing,
  type StatusCount,
} from '../../../shared/domain/listing';

/**
 * Las publicaciones propias del vendedor. HU-007.
 *
 * <p>Es la pantalla desde la que se llega a los borradores, y por eso pinta el estado de
 * cada una con su explicación: quien vuelve al cabo de unos días no se acuerda de si la
 * envió o la dejó a medias.
 *
 * <p>Las acciones que no exigen abrir la publicación —pausar, reactivar y archivar— se
 * ofrecen desde aquí. <strong>Archivar pide confirmación</strong>, y es la única de toda
 * la historia que lo hace: es la única acción del vendedor que no se puede deshacer.
 */
@Component({
  selector: 'sendik-my-listings-page',
  imports: [ModerationTrail, NgOptimizedImage, RouterLink, TranslocoPipe],
  templateUrl: './my-listings-page.html',
  styleUrl: './my-listings-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MyListingsPage {
  private readonly store = inject(ListingStore);
  private readonly inyector = inject(Injector);
  private readonly destruccion = inject(DestroyRef);

  protected readonly consulta = this.store.mine;
  protected readonly cifras = this.store.summary;
  protected readonly pausa = this.store.pause;
  protected readonly reanudacion = this.store.resume;
  protected readonly archivo = this.store.archive;

  /** Cuál está esperando confirmación de archivo. Nunca hay dos a la vez. */
  protected readonly porArchivar = signal<string | null>(null);

  /**
   * Los botones de archivar y el de confirmar, en el orden de las filas.
   *
   * <p>Por {@code viewChildren} y no buscando en el documento: así el foco no puede
   * acabar en un elemento de otra parte de la página, y no hace falta tocar
   * {@code document}, que en el servidor no existe.
   *
   * <p>Del de confirmar solo hay uno a la vez —{@code porArchivar} guarda una sola
   * publicación—, así que el primero es el que hay. Los de archivar salen en el mismo
   * orden que {@code publicaciones()}, y por eso se puede volver al de la fila correcta
   * sin preguntarle nada al DOM.
   */
  private readonly botonesDeArchivar = viewChildren<ElementRef<HTMLButtonElement>>('archivarBoton');

  private readonly botonDeConfirmar = viewChildren<ElementRef<HTMLButtonElement>>('confirmarBoton');

  /**
   * La zona de las cifras, que es adonde vuelve el foco tras un reintento.
   *
   * <p>Es el contenedor de los cuatro estados y no el {@code <dl>}: **existe siempre**. Un
   * destino que cambia de rama con el estado a veces todavía no está en el DOM cuando se le
   * pide el foco, y entonces el foco no va a ninguna parte.
   */
  private readonly zonaDeCifras = viewChild<ElementRef<HTMLElement>>('zonaDeCifras');

  /**
   * Se pulsó «Reintentar» y el foco quedó sin dueño.
   *
   * <p>Lo recoge el efecto del constructor cuando la petición termina. Por señal y efecto y
   * no encadenando al {@code refetch()}: la promesa se resuelve fuera del ciclo de
   * detección, y un {@code afterNextRender} pedido desde ahí no llega a ejecutarse. El
   * efecto corre dentro del ciclo, que es donde esta pantalla ya pide el foco para la
   * confirmación de archivar.
   */
  private readonly focoHuerfano = signal(false);

  protected readonly publicaciones = computed<readonly Listing[]>(() => this.consulta.data() ?? []);

  protected readonly vacio = computed(
    () => this.consulta.isSuccess() && this.publicaciones().length === 0,
  );

  /**
   * Todavía no se sabe si hay sesión. Criterio 7.
   *
   * <p>Mientras dure se pinta el esqueleto y **no** se ofrece entrar: el token de acceso se
   * pierde al recargar y la sesión llega después por la cookie de refresco, así que tratar
   * este momento como «no hay sesión» le enseñaría «entra» a quien ya entró.
   */
  protected readonly resolviendoSesion = computed(() => !this.store.sesionResuelta());

  /**
   * Se sabe que no hay sesión. Criterio 7.
   *
   * <p><strong>Se explica y se ofrece entrar; no se redirige.</strong> Es la decisión que
   * tomó HU-011 para su lista, y por el mismo motivo: una redirección desde una dirección
   * que alguien escribió a propósito le hace pensar que se equivocó.
   */
  protected readonly sinSesion = computed(
    () => this.store.sesionResuelta() && !this.store.haySesion(),
  );

  private readonly idiomas = inject(TranslocoService);

  /**
   * Las cifras por estado, en el orden que manda el servidor.
   *
   * <p>Vienen los siete de RN-061 y el cero viene dicho, no omitido: la pantalla no
   * completa nada ni esconde lo que vale cero. Si algún día llegaran menos, se pintan los
   * que lleguen; inventar aquí los que faltan taparía que la respuesta viene incompleta.
   */
  protected readonly porEstado = computed<readonly StatusCount[]>(() => this.cifras.data() ?? []);

  /**
   * Lo que se anuncia sobre las cifras. Criterio 5.
   *
   * <p>Por una región viva permanente y no por un {@code role="status"} que aparece con el
   * texto ya dentro: esa forma no se anuncia de manera fiable, porque la región tiene que
   * existir antes de que su contenido cambie. Es la misma lección de la pantalla de
   * publicar.
   *
   * <p><strong>Cubre los tres desenlaces y no solo la carga inicial.</strong> Con solo
   * {@code isPending()}, pulsar «Reintentar» no producía una sola palabra: tras un error el
   * estado sigue siendo `error` mientras se reintenta, terminar bien vaciaba la región -y
   * vaciar no anuncia- y volver a fallar reusaba el mismo nodo con el mismo texto, que
   * tampoco se re-anuncia. El error viaja por aquí y no por un {@code role="alert"} aparte
   * para no decirlo dos veces.
   *
   * <p>Se mira {@code isFetching()} y no {@code isPending()}, y esa diferencia importa:
   * sin sesión la consulta se queda pendiente para siempre -nace deshabilitada- y con
   * {@code isPending()} la región anunciaba «Cargando las cifras» indefinidamente a quien
   * no ha entrado. Un dato falso permanente es justo lo que el criterio 5 quiere evitar.
   */
  protected readonly anuncio = computed<string | null>(() => {
    if (this.cifras.isFetching()) {
      return 'listing.mine.summary.loading';
    }
    if (this.cifras.isError()) {
      return ListingStore.claveDeError(this.cifras.error());
    }
    if (this.cifras.isSuccess()) {
      return 'listing.mine.summary.ready';
    }
    return null;
  });

  /**
   * La cifra con el separador de miles del idioma activo.
   *
   * <p>Por {@code Intl} y no concatenando: «1.240» y «1,240» no son la misma cifra en los
   * dos idiomas que sirve el sitio.
   */
  protected cifraFormateada(cuantas: number): string {
    return new Intl.NumberFormat(this.idiomas.getActiveLang()).format(cuantas);
  }

  /**
   * Vuelve a pedir las cifras.
   *
   * <p><strong>No lleva guardia contra la doble pulsación, y conviene decir por qué.</strong>
   * La consulta falló sin llegar a tener datos, así que al reintentar TanStack la devuelve a
   * {@code pending} —limpia el error— y la pantalla pinta el esqueleto: **el botón deja de
   * existir en cuanto se pulsa** y no hay segunda pulsación que evitar. Deshabilitarlo sería
   * describir en el marcado algo que el marcado ya resuelve quitándolo de en medio, y
   * {@code disabled} sobre el elemento enfocado tiene además su propio problema.
   *
   * <p>Lo que sí hace falta es <strong>recoger el foco</strong>. Al destruirse el botón, el
   * foco cae al {@code body} y quien navega con teclado se queda al principio del documento.
   * Cuando la petición termina se lleva a lo que haya quedado: las cifras si llegaron, y el
   * botón otra vez si volvió a fallar. Mientras tanto la región viva dice que está cargando.
   */
  protected reintentarCifras(): void {
    this.focoHuerfano.set(true);
    void this.cifras.refetch();
  }

  constructor() {
    // Las cifras solo se piden mientras esta pantalla esta a la vista. El store es de raíz,
    // así que sin esto la consulta vivía en todas: `/publicar` pedía un resumen que no usa,
    // y cada guardado suyo lo volvía a pedir por la invalidación de prefijo.
    this.store.mirarLasCifras(true);
    this.destruccion.onDestroy(() => this.store.mirarLasCifras(false));

    // Sincroniza el foco -que es del navegador, no del marco- con el final del reintento.
    // Es el único uso de `effect` que frontend/CLAUDE.md admite.
    effect(() => {
      if (!this.focoHuerfano() || this.cifras.isFetching()) {
        return;
      }
      untracked(() => {
        this.focoHuerfano.set(false);
        this.enfocar(() => this.zonaDeCifras());
      });
    });
  }

  /** El precio ya formateado, en la configuración regional activa. */
  protected precioDe(publicacion: Listing): string | null {
    const precio = publicacion.product.price;
    return precio === null ? null : precioFormateado(precio, this.idiomas.getActiveLang());
  }

  protected portadaDe(publicacion: Listing): string | null {
    return tomasDelVendedor(publicacion)[0]?.url ?? null;
  }

  /** Pausar y reanudar solo tienen sentido sobre algo que se ve o se veia. */
  protected admitePausa(publicacion: Listing): boolean {
    return publicacion.status === 'PUBLISHED' || publicacion.status === 'PAUSED';
  }

  protected alternarPausa(publicacion: Listing): void {
    if (publicacion.status === 'PAUSED') {
      this.reanudacion.mutate(publicacion.id);
    } else {
      this.pausa.mutate(publicacion.id);
    }
  }

  /**
   * Abre la confirmación y lleva el foco a ella.
   *
   * <p>Es lo más parecido a un diálogo de toda la historia: el botón que la abre se
   * destruye al abrirla, así que sin mover el foco se quedaría en el body y quien navega
   * con teclado no sabría que ha pasado nada.
   */
  protected pedirConfirmacion(id: string): void {
    this.porArchivar.set(id);
    this.enfocar(() => this.botonDeConfirmar()[0]);
  }

  /** Al cancelar, el foco vuelve a donde estaba: al botón de archivar de esa fila. */
  protected cancelarArchivo(): void {
    const cancelada = this.porArchivar();
    this.porArchivar.set(null);

    const fila = this.publicaciones().findIndex((publicacion) => publicacion.id === cancelada);
    this.enfocar(() => (fila === -1 ? undefined : this.botonesDeArchivar()[fila]));
  }

  protected archivar(id: string): void {
    this.archivo.mutate(id, { onSettled: () => this.porArchivar.set(null) });
  }

  /**
   * Lleva el foco a donde diga la función, después de pintar.
   *
   * <p>Se resuelve tras pintar porque antes el elemento no existe: los dos casos cambian
   * de rama del {@code @if} justo al pulsarlos.
   */
  private enfocar(donde: () => ElementRef<HTMLElement> | undefined): void {
    afterNextRender(() => donde()?.nativeElement.focus(), { injector: this.inyector });
  }

  protected claveDeError(fallo: unknown): string {
    return ListingStore.claveDeError(fallo);
  }
}
