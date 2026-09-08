import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { toSignal } from '@angular/core/rxjs-interop';
import { map } from 'rxjs';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { TOMAS_DE_LA_SECUENCIA, type Listing } from '../../../shared/domain/listing';
import { estaNivelado } from '../../../shared/domain/tilt';
import { CameraService } from '../../../shared/infrastructure/camera.service';
import { OrientationService } from '../../../shared/infrastructure/orientation.service';
import { ImagenNoNormalizable } from '../../../shared/infrastructure/photo-normalizer';
import { ListingStore } from '../application/listing.store';
import { admiteAsistente, pasosDeCaptura, primerPasoPendiente } from '../domain/capture-steps';

/** En qué punto está el permiso de sensores. Solo iOS llega a `pendiente`. */
type EstadoDelPermiso = 'pendiente' | 'concedido' | 'negado';

/**
 * El asistente de captura de las ocho tomas. HU-003, criterios 1 a 10.
 *
 * <p>Pantalla propia y no un diálogo sobre el formulario: la cámara ocupa el alto entero en
 * un teléfono, el botón de atrás tiene que cerrarla, y el paso en curso sobrevive a que la
 * pantalla rote, que es uno de los casos borde de la historia.
 *
 * <p>**Nada de lo que hay aquí decide.** Si una lectura del sensor basta para disparar lo
 * dice `shared/domain/tilt.ts`; qué paso toca, `domain/capture-steps.ts`; si la foto sirve,
 * el normalizador y, por encima de él, el servidor sobre los bytes que recibe (ADR-0018).
 * Este componente abre la cámara, pinta y encadena.
 */
@Component({
  selector: 'sendik-capture-wizard',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './capture-wizard.html',
  styleUrl: './capture-wizard.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptureWizard {
  private readonly camara = inject(CameraService);
  private readonly sensores = inject(OrientationService);
  private readonly publicaciones = inject(ListingStore);
  private readonly router = inject(Router);
  private readonly idioma = inject(TranslocoService);

  private readonly id = toSignal(
    inject(ActivatedRoute).paramMap.pipe(map((parametros) => parametros.get('id'))),
    { initialValue: null },
  );

  protected readonly publicacion = computed(() => this.publicaciones.current.data() ?? null);

  /** Los tres estados que toda pantalla que carga datos define (frontend/CLAUDE.md). */
  protected readonly cargando = computed(() => this.publicaciones.current.isPending());

  /**
   * No hay publicación que capturar.
   *
   * <p>La consulta no reintenta, así que un 404 —no existe, o no es de quien pregunta— es
   * definitivo. Sin esta rama la pantalla se quedaba en blanco para siempre, sin mensaje
   * y sin salida.
   */
  protected readonly noDisponible = computed(() => this.publicaciones.current.isError());

  /** El paso en curso. Lo fija el primer hueco al abrir y avanza al subir. */
  private readonly paso = signal<number | null>(null);

  private readonly flujo = signal<MediaStream | null>(null);
  private readonly inclinacion = signal<{ beta: number; gamma: number } | null>(null);

  /** La baja del sensor, guardada para poder soltarlo al salir. */
  private bajaDelSensor: (() => void) | null = null;

  /** El foco se lleva al título una sola vez, no en cada revisión de la vista. */
  private enfocado = false;

  protected readonly abierta = signal(false);
  protected readonly ocupada = signal(false);
  protected readonly error = signal<string | null>(null);
  /**
   * En qué punto está el permiso de sensores.
   *
   * <p>Nace en `pendiente` solo donde de verdad hay algo que pedir, que hoy es iOS. En el
   * resto nace concedido: pintar «vamos a pedirte permiso» en un navegador que no lo pide
   * anunciaría un diálogo que nunca aparece.
   */
  /**
   * Cuántas tomas se recuperaron del borrador al abrir. HU-003 criterio 7.
   *
   * <p>Se pintan como aviso —«sigue donde ibas»— y se suben solas: ya estaban congeladas,
   * y volver a pedirlas sería justo lo que el criterio dice que no hay que hacer.
   */
  protected readonly recuperadas = signal(0);

  /**
   * Lo que se le dice a un lector de pantalla cuando algo cambia.
   *
   * <p>Una sola región, que vive fuera de todos los `@if` y nace vacía: una región que se
   * inserta ya con su texto dentro no se anuncia de forma fiable. Por aquí pasan el
   * cambio de nivel y el fin de cada subida, que son los dos momentos en los que alguien
   * que no ve la pantalla necesita saber qué pasó.
   */
  protected readonly anuncio = signal('');

  protected readonly permiso = signal<EstadoDelPermiso>(
    this.sensores.necesitaPermiso() ? 'pendiente' : 'concedido',
  );

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

  protected readonly total = TOMAS_DE_LA_SECUENCIA;
  protected readonly soportada = computed(() => this.camara.soportada());

  protected readonly pasos = computed(() => {
    const actual = this.publicacion();
    return actual === null ? [] : pasosDeCaptura(actual);
  });

  protected readonly hechas = computed(() => this.pasos().filter((paso) => paso.hecha).length);

  protected readonly pasoActual = computed(() => {
    const posicion = this.paso();
    return posicion === null ? null : (this.pasos()[posicion] ?? null);
  });

  /**
   * Si el obturador está habilitado. Criterio 3.
   *
   * <p>Sin lectura del sensor responde que sí, y eso es el criterio 4: el asistente sigue
   * sin nivel y **nunca se bloquea la publicación** por un permiso denegado.
   */
  protected readonly nivelado = computed(() => {
    const lectura = this.inclinacion();
    return estaNivelado(lectura?.beta ?? null, lectura?.gamma ?? null);
  });

  /** Se muestra el nivel solo cuando hay algo que mostrar. */
  protected readonly hayNivel = computed(() => this.inclinacion() !== null);

  protected readonly avance = computed(() => this.publicaciones.uploadProgress());

  protected readonly porcentaje = computed(() => {
    const fraccion = this.avance()?.fraccion;
    return fraccion === null || fraccion === undefined ? null : Math.round(fraccion * 100);
  });

  constructor() {
    // La página fija cuál publicación se está viendo; el store pide la que toque.
    effect(() => this.publicaciones.abrir(this.id()));

    /*
     * Coloca el asistente en el primer hueco, una sola vez por publicación.
     *
     * Se hace cuando la publicacion llega y no en el constructor, porque al construir
     * todavia no hay datos. La condicion de `paso() === null` es lo que impide que vuelva
     * a saltar al principio cada vez que el store refresca tras subir una toma: sin ella,
     * subir la tercera devolveria el asistente a la cuarta y luego otra vez a la cuarta,
     * y la persona nunca llegaria al final.
     */
    effect(() => {
      const actual = this.publicacion();
      if (actual !== null && this.paso() === null) {
        this.paso.set(primerPasoPendiente(actual));
        void this.retomarBorrador(actual);
      }
    });

    /*
     * Engancha el flujo al elemento cuando el elemento existe, que no es cuando se
     * concede la camara. Es el mismo fallo que HU-002 dejo documentado en capture-field:
     * el visor vive dentro de un `@if`, asi que al volver de `getUserMedia` todavia no
     * esta en el documento y asignar ahi mismo asignaba sobre `undefined`.
     */
    effect(() => {
      const elemento = this.video()?.nativeElement;
      const flujo = this.flujo();

      if (elemento === undefined || flujo === null || elemento.srcObject === flujo) {
        return;
      }
      elemento.srcObject = flujo;
    });

    /*
     * Lleva el foco al titulo al entrar. El asistente se abre desde un enlace de la
     * rejilla y ocupa la pantalla entera: sin esto el foco se queda en el `body` y hay que
     * retabular desde la cabecera del sitio hasta el primer control.
     */
    effect(() => {
      const encabezado = this.titulo()?.nativeElement;
      if (encabezado !== undefined && !this.enfocado) {
        this.enfocado = true;
        encabezado.focus();
      }
    });

    inject(DestroyRef).onDestroy(() => this.apagar());
  }

  /**
   * Arranca: pide el permiso de sensores si hace falta y abre la cámara.
   *
   * <p>Va detrás de un botón porque **iOS descarta la solicitud de sensores que no venga de
   * un gesto**, y lo hace en silencio. Pedirlo al entrar en la pantalla no fallaría con un
   * error: simplemente no aparecería el diálogo, y el nivel no funcionaría nunca sin que
   * nada lo explicara.
   */
  protected async empezar(): Promise<void> {
    this.error.set(null);

    if (!this.soportada()) {
      this.error.set('listing.capture.entry.unsupported');
      return;
    }

    this.ocupada.set(true);
    try {
      await this.activarNivel();

      this.flujo.set(await this.camara.abrir(false, true));
      this.abierta.set(true);
    } catch {
      // Denegar la cámara es una decisión de la persona, no un fallo del sistema. Se
      // ofrece la galería, que es lo que el criterio 8 pide para este caso.
      this.error.set('listing.capture.entry.unsupported');
      this.apagar();
    } finally {
      this.ocupada.set(false);
    }
  }

  /**
   * Congela la toma y la sube.
   *
   * <p>Que se guarde en el borrador antes de subir, y se olvide al subir, lo hace el caso
   * de uso (`ListingStore.uploadShot`): es una sola secuencia y parte de ella aquí y parte
   * allá era tener el mismo caso de uso escrito en dos capas.
   */
  protected async tomar(): Promise<void> {
    const elemento = this.video()?.nativeElement;
    const actual = this.publicacion();
    const posicion = this.paso();

    // La puerta está aquí y no en un `disabled` del botón: el botón se queda alcanzable
    // para que quien navega con teclado no lo pierda justo cuando aparece la explicación
    // de por qué no puede usarlo (criterio 3), así que quien impide disparar es esto.
    if (!elemento || actual === null || posicion === null || this.ocupada() || !this.nivelado()) {
      return;
    }

    this.ocupada.set(true);
    this.error.set(null);

    try {
      const fotograma = await this.camara.capturar(elemento);

      await this.subir(actual.id, posicion, fotograma, false);
    } catch (fallo: unknown) {
      this.error.set(claveDelFallo(fallo));
    } finally {
      this.ocupada.set(false);
    }
  }

  /**
   * Sube una imagen elegida desde la galería. Criterio 8.
   *
   * <p>Pasa por el mismo recorte forzado que una toma de cámara —lo hace el store— y va
   * marcada como venida de galería, que le suma a la publicación una revisión más atenta.
   */
  protected async alElegirArchivo(evento: Event): Promise<void> {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];
    const actual = this.publicacion();
    const posicion = this.paso();

    // Se limpia siempre: sin esto, elegir el mismo archivo dos veces seguidas no vuelve a
    // disparar el evento, y quien reintenta tras un fallo no consigue nada.
    campo.value = '';

    if (!archivo || actual === null || posicion === null) {
      return;
    }

    this.ocupada.set(true);
    this.error.set(null);

    try {
      await this.subir(actual.id, posicion, archivo, true);
    } catch (fallo: unknown) {
      this.error.set(claveDelFallo(fallo));
    } finally {
      this.ocupada.set(false);
    }
  }

  protected irA(posicion: number): void {
    this.paso.set(posicion);
    this.error.set(null);
  }

  /** Sale del asistente y devuelve al formulario, que es donde está la rejilla. */
  protected async salir(): Promise<void> {
    const actual = this.publicacion();
    this.apagar();

    // Lo que no llegó a subirse en esta sesión ya no se va a subir por su cuenta, y
    // dejarlo son megabytes por publicación abandonada. Retomar sigue funcionando: lo que
    // se recupera es lo de una salida **no** voluntaria, que es lo que el criterio 7 dice.
    if (actual !== null) {
      await this.publicaciones.olvidarBorrador(actual.id);
    }

    await this.router.navigate(actual === null ? ['/mis-publicaciones'] : ['/publicar', actual.id]);
  }

  protected admite(): boolean {
    const actual = this.publicacion();
    return actual !== null && admiteAsistente(actual);
  }

  private async subir(
    id: string,
    posicion: number,
    imagen: Blob,
    desdeGaleria: boolean,
  ): Promise<void> {
    // Guardar en el borrador y olvidarlo al subir es del caso de uso, no de la pantalla:
    // lo hace `uploadShot`. Aquí solo se encadena lo siguiente.
    const actualizada = await this.publicaciones.uploadShot.mutateAsync({
      id,
      posicion,
      imagen,
      desdeGaleria,
    });

    this.anuncio.set(
      this.idioma.translate('listing.capture.upload.saved', { nombre: nombreDePaso(posicion) }),
    );
    this.avanzar(actualizada);
  }

  /**
   * Sube lo que quedó congelado y sin subir de una sesión anterior. Criterio 7.
   *
   * <p>«Cerrar el navegador por accidente no obliga a empezar de nuevo»: las tomas están
   * en el dispositivo, así que se terminan de mandar en vez de pedirlas otra vez. Se
   * suben en orden y de una en una, que es como se subirían recién tomadas.
   *
   * <p>Un fallo aquí **no interrumpe nada**: quien abrió el asistente puede seguir
   * capturando, y lo que no subió se queda en el borrador para el siguiente intento.
   */
  private async retomarBorrador(publicacion: Listing): Promise<void> {
    const pendientes = await this.publicaciones.tomasSinSubir(publicacion);

    if (pendientes.length === 0) {
      return;
    }
    this.recuperadas.set(pendientes.length);

    for (const toma of pendientes) {
      try {
        await this.subir(publicacion.id, toma.posicion, toma.imagen, false);
      } catch {
        this.error.set('listing.capture.upload.failed');
        return;
      }
    }
  }

  /**
   * Pasa al siguiente hueco, o termina.
   *
   * <p>Busca el siguiente **pendiente** y no la posición de al lado: quien entró a rellenar
   * los huecos de un borrador a medias no quiere que el asistente se pare en las que ya
   * tenía hechas.
   *
   * <p>Se decide sobre la publicación que **devuelve la subida**, no sobre la señal de la
   * consulta. Las dos acaban diciendo lo mismo, pero la señal lo dice cuando la librería
   * propaga y esto corre justo antes: leerla aquí dejaba el asistente clavado en el paso
   * que se acababa de completar.
   */
  private avanzar(actualizada: Listing): void {
    const pendiente = pasosDeCaptura(actualizada).find((paso) => !paso.hecha);

    if (pendiente === undefined) {
      void this.salir();
      return;
    }
    this.paso.set(pendiente.posicion);
  }

  /**
   * Enciende el nivel, si el aparato lo permite.
   *
   * <p>Que se niegue **no interrumpe nada**: se anota para poder avisar de que las tomas
   * pueden quedar desalineadas, y la captura sigue (criterio 4).
   */
  private async activarNivel(): Promise<void> {
    if (!this.sensores.soportada()) {
      this.permiso.set('negado');
      return;
    }

    const concedido = await this.sensores.pedirPermiso();
    this.permiso.set(concedido ? 'concedido' : 'negado');

    if (!concedido) {
      return;
    }

    this.bajaDelSensor = this.sensores.escuchar((lectura) => {
      const antes = this.nivelado();
      this.inclinacion.set(lectura);

      // Solo al cambiar de estado. El sensor emite decenas de veces por segundo y anunciar
      // cada lectura llenaría la cola del lector con lo mismo repetido.
      if (this.nivelado() !== antes) {
        this.anuncio.set(
          this.idioma.translate(
            this.nivelado() ? 'listing.capture.level.ok' : 'listing.capture.level.tilted',
          ),
        );
      }
    });
  }

  private apagar(): void {
    this.camara.cerrar(this.flujo());
    this.flujo.set(null);
    this.abierta.set(false);

    this.bajaDelSensor?.();
    this.bajaDelSensor = null;
    this.inclinacion.set(null);
  }
}

/**
 * La clave del texto que explica un fallo.
 *
 * <p>El motivo del normalizador se traduce por su código, que es lo que
 * `listing.capture.rejected.*` enumera. Cualquier otra cosa —la red, un 4xx del servidor—
 * ya tiene su mensaje en el interceptor de errores y no se pisa aquí.
 */
/** El nombre de una toma, ya traducido, para meterlo en un anuncio. */
function nombreDePaso(posicion: number): string {
  return String(posicion + 1);
}

function claveDelFallo(fallo: unknown): string {
  return fallo instanceof ImagenNoNormalizable
    ? `listing.capture.rejected.${fallo.motivo}`
    : 'listing.capture.upload.failed';
}
