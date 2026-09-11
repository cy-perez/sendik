import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import { CARRITO_VACIO, cuantosLleva, MAXIMO_DE_PRODUCTOS, type Cart } from '../domain/cart';
import { CartApi, type CartItemState } from '../infrastructure/cart.api';
import { LocalCart, type ProductoGuardado } from '../infrastructure/local-cart';
import { portada, type PublicListing } from '../domain/public-listing';
import { queryKeys } from './query-keys';

/** Lo que la pantalla necesita saber del control, sin ver TanStack. */
export interface EstadoDelControl {
  readonly dentro: boolean;
  readonly seOfrece: boolean;
  readonly enCurso: boolean;
}

/**
 * El estado del carrito. HU-015.
 *
 * <p>Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Dos orígenes y una sola forma.</strong> Con sesión, el carrito lo sirve
 * `GET /users/me/cart`; sin ella, `GET /carts?ids=…` con lo que guarda el navegador. Los dos
 * devuelven grupos y subtotales calculados por el servidor, y por eso este almacén no suma
 * nada: la cifra vive en un solo sitio (ADR-0037).
 *
 * <p><strong>Nada de esto sale en el HTML del servidor.</strong> Es la misma propiedad
 * delicada que HU-011: la ficha se renderiza en el servidor y es igual para todo el mundo, y
 * el estado del control se pide aparte, después de hidratar. Sin sesión ni siquiera hay
 * petición: lo sabe el navegador.
 *
 * <p>Ninguna consulta reintenta. Un 404 o un 403 son respuestas normales aquí.
 */
@Injectable({ providedIn: 'root' })
export class CartStore {
  private readonly api = inject(CartApi);
  private readonly local = inject(LocalCart);
  private readonly sesion = inject(SessionStore);
  private readonly consultas = inject(QueryClient);

  /** Cuál publicación está abierta en la ficha. La fija el propio control al montarse. */
  private readonly ficha = signal<string | null>(null);

  /** Si la pantalla del carrito está abierta. Sin esto se pediría al abrir cualquier ficha. */
  private readonly mirandoElCarrito = signal(false);

  /**
   * Lo que hay en el navegador. Señal y no lectura directa para que la pantalla se repinte
   * al agregar o quitar sin sesión: `localStorage` no avisa a nadie cuando cambia.
   */
  private readonly guardadoLocal = signal<readonly ProductoGuardado[]>([]);

  readonly haySesion = computed(() => this.sesion.status() === 'abierta');

  readonly sesionResuelta = computed(() => this.sesion.status() !== 'desconocida');

  /** Si el navegador puede guardar. Falso en incógnito o con el almacenamiento lleno. */
  readonly puedeGuardarLocalmente = computed(() => this.local.disponible());

  constructor() {
    this.releerLoLocal();
    this.fusionDescartada.set(this.local.seDescartoLaFusion());
  }

  // --- El control de la ficha ----------------------------------------------

  /**
   * El estado del control, para quien tiene sesión.
   *
   * <p>Sin sesión no sale: la ruta responde 401 y pedirla sería fabricar un error en cada
   * visita de quien no tiene cuenta, que son casi todas. Sin sesión lo contesta el navegador.
   */
  readonly estado = injectQuery(() => ({
    queryKey: queryKeys.cartItem(this.ficha() ?? 'ninguna'),
    queryFn: () => this.api.estado(this.ficha() ?? ''),
    enabled: this.ficha() !== null && this.haySesion(),
    // Sin frescura: es dato de una persona y cambia porque ella lo cambia, a veces en otra
    // pestaña. Y cuanto menos viva en la caché, mejor (docs/operacion/datos-personales.md).
    staleTime: 0,
    retry: false,
  }));

  /**
   * Lo que la ficha pinta.
   *
   * <p><strong>Sin sesión el control funciona igual y sin pedir permiso</strong> (criterio
   * 8): lo contesta el carrito del navegador. `seOfrece` es cierto porque sin sesión no hay
   * publicación propia posible —no hay contra quién comparar—, y esa comprobación llega en la
   * fusión.
   */
  readonly control = computed<EstadoDelControl>(() => {
    const id = this.ficha();

    if (!this.haySesion()) {
      return {
        dentro: id !== null && this.guardadoLocal().some((p) => p.listingId === id),
        seOfrece: true,
        enCurso: false,
      };
    }

    const respuesta: CartItemState | undefined = this.estado.data();

    return {
      dentro: this.optimista() ?? respuesta?.inCart ?? false,
      seOfrece: respuesta?.eligible ?? true,
      enCurso: this.agregar.isPending() || this.quitar.isPending(),
    };
  });

  /**
   * Lo que el control enseña mientras el servidor confirma.
   *
   * <p>Nulo significa «lo que diga el servidor». Si la petición falla vuelve a nulo, y con eso
   * el control regresa a su estado anterior en vez de quedarse mintiendo.
   */
  private readonly optimista = signal<boolean | null>(null);

  /**
   * Si hay una escritura en vuelo. Se pone **antes** de lanzarla, en el mismo tick.
   *
   * <p>Es lo que corta el doble pulsado, y tiene que ser síncrona: `isPending()` de TanStack
   * se actualiza después, así que dos clics seguidos lo leen en falso los dos.
   */
  private readonly escribiendo = signal(false);

  readonly agregar = injectMutation(() => ({
    mutationFn: (listingId: string) => this.api.agregar(listingId),
    onMutate: () => this.optimista.set(true),
    onError: () => this.optimista.set(null),
    onSuccess: () => this.refrescar(),
    onSettled: () => this.escribiendo.set(false),
  }));

  readonly quitar = injectMutation(() => ({
    mutationFn: (listingId: string) => this.api.quitar(listingId),
    onMutate: () => this.optimista.set(false),
    onError: () => this.optimista.set(null),
    onSuccess: () => this.refrescar(),
    onSettled: () => this.escribiendo.set(false),
  }));

  /**
   * Qué decirle a quien acabó con un error.
   *
   * <p>Los tres códigos tienen texto propio porque no son fallos genéricos: uno dice que la
   * publicación es suya (criterio 5), otro que ya no está disponible (criterio 6) y el tercero
   * que el carrito está lleno (criterio 7). Con un mensaje único, quien pulsa y no ve su
   * producto no sabría por cuál de las tres razones.
   */
  readonly errorDelControl = computed<string | null>(() => {
    const fallo = this.agregar.error() ?? this.quitar.error();

    if (fallo == null) {
      return this.topeLocalAlcanzado() ? 'catalog.cart.errors.full' : null;
    }

    // **Por `code` y no por estado HTTP.** Los dos códigos propios existen justamente para
    // que el cliente distinga —`ErrorCode` y `contrato-api.md` lo justifican con esas
    // palabras— y mapear por 403/404/422 los ignoraba: cualquier segundo 422 que ese
    // endpoint devuelva mañana diría «tu carrito está lleno». Es el patrón que ya usan
    // `listing-review.store.ts` y `review.store.ts`.
    if (fallo instanceof ApiError) {
      if (fallo.code === 'CATALOG_SELF_CART_FORBIDDEN') {
        return 'catalog.cart.errors.own';
      }
      if (fallo.code === 'CATALOG_CART_FULL') {
        return 'catalog.cart.errors.full';
      }
      if (fallo.code === 'COMMON_NOT_FOUND') {
        return 'catalog.cart.errors.unavailable';
      }
    }

    return 'catalog.cart.errors.failed';
  });

  /** El tope alcanzado sin sesión, que no viene de ninguna respuesta: lo decide el navegador. */
  private readonly topeLocalAlcanzado = signal(false);

  /** Cuántos caben, para el mensaje del criterio 7. */
  readonly tope = MAXIMO_DE_PRODUCTOS;

  /**
   * Agregar o quitar, decidiendo por sesión.
   *
   * <p>Es lo único que el control llama: que el carrito viva en la base o en el navegador es
   * decisión de esta capa, no de la pantalla.
   */
  alternar(publicacion: PublicListing, nombreDelVendedor: string | null): void {
    // **El doble pulsado se corta aquí, y hasta ahora no se cortaba en ninguna parte.** La
    // plantilla del control afirmaba «el doble pulsado ya lo bloquea el almacén contando
    // peticiones» y era falso: dos clics seguidos con sesión lanzaban un PUT y, al leer el
    // estado optimista ya en `true`, un DELETE detrás. Dos escrituras en vuelo sobre el mismo
    // par, con resultado dependiente del orden de llegada al servidor.
    //
    // Se corta aquí y no con `[disabled]` en el botón: deshabilitarlo en el mismo tick del
    // clic manda el foco a `body`, que es la regresión que HU-011 tuvo que arreglar.
    //
    // Y con señal propia y no con `isPending()` de la mutación: aquel no cambia dentro del
    // mismo tick, así que dos clics síncronos lo leían en falso los dos y pasaban igual. La
    // prueba lo encontró al primer intento.
    if (this.escribiendo()) {
      return;
    }

    const dentro = this.control().dentro;

    if (this.haySesion()) {
      this.escribiendo.set(true);
      if (dentro) {
        this.quitar.mutate(publicacion.id);
      } else {
        this.agregar.mutate(publicacion.id);
      }
      return;
    }

    this.topeLocalAlcanzado.set(false);

    if (dentro) {
      this.local.quitar(publicacion.id);
      this.releerLoLocal();
      return;
    }

    const cupo = this.local.agregar({
      listingId: publicacion.id,
      title: publicacion.product.title ?? '',
      price: publicacion.product.price?.amount ?? 0,
      imageUrl: portada(publicacion)?.url ?? null,
      sellerName: nombreDelVendedor,
    });

    this.topeLocalAlcanzado.set(!cupo);
    this.releerLoLocal();
  }

  // --- La pantalla del carrito ---------------------------------------------

  /**
   * El carrito, del origen que corresponda.
   *
   * <p>La clave lleva dentro si hay sesión y qué identificadores se piden: son dos carritos
   * distintos y con una clave común, entrar pintaría un instante el del navegador.
   *
   * <p>Sin sesión y sin nada guardado no se pide nada: `GET /carts` sin identificadores es un
   * 400, y pedirlo para que responda que no hay nada sería fabricar un error en cada visita a
   * un carrito vacío.
   */
  readonly carrito = injectQuery(() => ({
    queryKey: queryKeys.cart(this.haySesion(), this.idsLocales()),
    queryFn: () =>
      this.haySesion() ? this.api.carrito() : this.api.carritoAnonimo(this.idsLocales()),
    enabled:
      this.mirandoElCarrito() &&
      this.sesionResuelta() &&
      (this.haySesion() || this.idsLocales().length > 0),
    staleTime: 0,
    retry: false,
  }));

  /**
   * Lo que la pantalla pinta.
   *
   * <p>Un carrito sin sesión y sin nada guardado no llega a pedirse, así que aquí sale el
   * vacío: la pantalla del criterio 19 se pinta igual, y sin haber tocado la red.
   */
  readonly contenido = computed<Cart>(() => this.carrito.data() ?? CARRITO_VACIO);

  readonly cuantos = computed(() => cuantosLleva(this.contenido()));

  /**
   * Lo que el servidor no devolvió y el navegador sí guarda. Criterio 21, sin sesión.
   *
   * <p>Quien no ha entrado pide por identificador y lo que dejó de estar publicado no vuelve
   * (RN-068), así que la única forma de enseñarlo apagado es la copia local. Con sesión no
   * hace falta: allí el servidor sí devuelve lo no disponible.
   */
  readonly noDisponiblesLocales = computed<readonly ProductoGuardado[]>(() => {
    if (this.haySesion()) {
      return [];
    }

    const devueltos = new Set(
      this.contenido().groups.flatMap((grupo) => grupo.lines.map((linea) => linea.listing.id)),
    );

    return this.guardadoLocal().filter((producto) => !devueltos.has(producto.listingId));
  });

  /** La pantalla del carrito dice cuándo se está mirando, y cuándo se dejó de mirar. */
  abrirCarrito(abierta: boolean): void {
    this.mirandoElCarrito.set(abierta);
    if (abierta) {
      this.releerLoLocal();
    }
  }

  /** Quita desde la pantalla del carrito, con sesión o sin ella. */
  quitarDelCarrito(listingId: string): void {
    if (this.haySesion()) {
      if (this.escribiendo()) {
        return;
      }
      this.escribiendo.set(true);
      this.quitar.mutate(listingId);
      return;
    }

    this.local.quitar(listingId);
    this.releerLoLocal();
  }

  reintentar(): void {
    void this.carrito.refetch();
  }

  // --- La fusión -----------------------------------------------------------

  /**
   * Si hay algo del navegador que ofrecer al carrito de la cuenta. Criterios 9 y 12.
   *
   * <p><strong>Se ofrece y no se hace solo</strong>, y esa es la decisión de ADR-0037. El
   * criterio 9 pide unión al entrar y el 12 pide que otra persona no herede el carrito de
   * este navegador; siendo anónimo, nadie puede distinguir «vuelve el mismo» de «llega otro».
   * Preguntar una vez es lo único que cumple los dos.
   */
  readonly hayQueFusionar = computed(
    () => this.haySesion() && this.guardadoLocal().length > 0 && !this.fusionDescartada(),
  );

  /**
   * Si ya se dijo que no a fusionar este carrito.
   *
   * <p><strong>Vive en el navegador y no solo en memoria.</strong> Estaba en memoria, y eso
   * dejaba a quien no armó ese carrito diciendo que no en cada recarga: la pregunta volvía
   * sola. Es el lado incómodo del criterio 12 —quien recibe la oferta puede no ser quien
   * llenó el carrito— y decir que no una vez tiene que bastar.
   */
  private readonly fusionDescartada = signal(false);

  readonly fusionar = injectMutation(() => ({
    mutationFn: (ids: readonly string[]) => this.api.fusionar(ids),
    // **Se vacía solo cuando la fusión salió bien.** Estaba en `onSettled` —«salga bien o
    // mal»— y eso era un camino de pérdida de datos: una fusión que falla por red dejaba a la
    // persona sin el carrito del navegador y sin el de la cuenta, y sin nada que reintentar.
    //
    // El criterio 12 pedía que el carrito local no sobreviva a **su fusión**, no a un intento
    // fallido: si no se fusionó, no hay nada que heredar todavía, y quien vuelva a entrar
    // recibirá otra vez la pregunta, que es el comportamiento correcto.
    onSuccess: () => {
      this.local.vaciar();
      this.releerLoLocal();
      this.refrescar();
    },
  }));

  /** Si la última fusión falló. La pantalla lo dice y ofrece reintentar. */
  readonly falloLaFusion = computed(() => this.fusionar.isError());

  /** Lo que no entró en la última fusión, para decirlo (criterio 10). */
  readonly noEntraron = computed<readonly string[]>(() => this.fusionar.data()?.notMerged ?? []);

  /** Cuántos productos trae el navegador, para la pregunta de la fusión. */
  readonly cuantosGuardadosLocalmente = computed(() => this.guardadoLocal().length);

  aceptarLaFusion(): void {
    this.fusionar.mutate(this.guardadoLocal().map((producto) => producto.listingId));
  }

  /** Descartar no borra el carrito local: quien dijo que no puede seguir sin entrar. */
  descartarLaFusion(): void {
    this.fusionDescartada.set(true);
    this.local.recordarQueSeDescarto();
  }

  // --- Lo que hace la ficha -------------------------------------------------

  /** La ficha fija cuál publicación se está mirando al resolver la ruta. */
  abrirFicha(id: string | null): void {
    this.ficha.set(id);
    this.optimista.set(null);
    this.escribiendo.set(false);
    this.topeLocalAlcanzado.set(false);
    this.agregar.reset();
    this.quitar.reset();
    this.releerLoLocal();
  }

  private idsLocales(): readonly string[] {
    return this.guardadoLocal().map((producto) => producto.listingId);
  }

  private releerLoLocal(): void {
    this.guardadoLocal.set(this.local.todos());
  }

  private refrescar(): void {
    const id = this.ficha();
    if (id !== null) {
      void this.consultas.invalidateQueries({ queryKey: queryKeys.cartItem(id) });
    }
    void this.consultas.invalidateQueries({ queryKey: queryKeys.cartAny });
  }
}
