import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  input,
  output,
  signal,
  viewChild,
  type ElementRef,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { CameraService } from '../../../shared/infrastructure/camera.service';
import { SharpnessService } from '../infrastructure/sharpness.service';

/** Qué se está encuadrando. Decide la forma de la guía y qué cámara se pide. */
export type Encuadre = 'documento' | 'rostro';

/**
 * Tomar una foto con la cámara, con guía de encuadre y rechazo de lo borroso.
 * Criterios 2 y 3 de HU-002.
 *
 * <p>**No hay selector de archivos, y esa ausencia es el criterio 3.** No se ofrece subir
 * desde la galería. Lo que el criterio pide es exactamente eso —que no esté ofrecido—, y
 * no que sea imposible: una cámara virtual pasa por aquí igual, y prometer lo contrario
 * sería mentir. Está dicho en HU-002.
 *
 * <p>El desenfoque se mide en el cliente y la foto borrosa no llega a subirse. Es lo que
 * el criterio 2 pide, y de paso le ahorra a la persona esperar una subida para que se la
 * rechacen.
 *
 * <p>La cámara se apaga al destruir el componente. Sin eso, el indicador del dispositivo
 * se queda encendido después de salir de la pantalla, que es la clase de detalle que hace
 * desconfiar de un sitio donde acabas de subir tu cédula.
 */
@Component({
  selector: 'sendik-capture-field',
  imports: [TranslocoPipe],
  templateUrl: './capture-field.html',
  styleUrl: './capture-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CaptureField {
  private readonly camara = inject(CameraService);
  private readonly nitidez = inject(SharpnessService);

  readonly encuadre = input.required<Encuadre>();
  readonly labelKey = input.required<string>();

  /** La foto aceptada. Solo se emite lo que pasó el umbral de nitidez. */
  readonly capturada = output<Blob>();

  private readonly video = viewChild<ElementRef<HTMLVideoElement>>('video');

  protected readonly abierta = signal(false);
  protected readonly tomada = signal<string | null>(null);
  protected readonly borrosa = signal(false);
  protected readonly error = signal<string | null>(null);
  protected readonly ocupada = signal(false);

  protected readonly soportada = computed(() => this.camara.soportada());

  protected readonly guia = computed(() =>
    this.encuadre() === 'rostro'
      ? 'sellerVerification.capture.guideFace'
      : 'sellerVerification.capture.guideDocument',
  );

  private readonly flujo = signal<MediaStream | null>(null);

  constructor() {
    /*
     * Engancha el flujo al elemento cuando el elemento existe, que no es cuando se
     * concede la camara.
     *
     * El visor vive dentro de un `@if`, asi que al volver de `getUserMedia` todavia no
     * esta en el documento: `abierta.set(true)` marca la vista para revisar, no la
     * revisa. Asignando `srcObject` ahi mismo se asignaba sobre `undefined` y la camara
     * quedaba concedida y encendida **sin imagen**, con el boton de tomar la foto
     * ofrecido y `capturar()` fallando por un fotograma de cero por cero. Lo encontro la
     * prueba de extremo a extremo; ninguna prueba de componente podia, porque todas usan
     * un doble que no necesita un elemento de verdad.
     *
     * Es uno de los usos legitimos de `effect`: el destino esta fuera del marco. La
     * consulta de vista es una senal, asi que esto se vuelve a ejecutar solo en cuanto el
     * elemento aparece.
     */
    effect(() => {
      const elemento = this.video()?.nativeElement;
      const flujo = this.flujo();

      if (elemento === undefined || flujo === null || elemento.srcObject === flujo) {
        return;
      }

      // Solo se engancha, no se llama a `play()`. El elemento lleva `autoplay`, `muted` y
      // `playsinline`, que es lo que las politicas de reproduccion automatica exigen para
      // arrancar sola; llamarlo a mano ademas no aporta nada y devuelve una promesa que
      // hay que atrapar. Que de verdad reproduzca lo comprueba la prueba de extremo a
      // extremo, que espera a que el visor no este pausado.
      elemento.srcObject = flujo;
    });

    inject(DestroyRef).onDestroy(() => {
      this.apagar();
      this.soltarVistaPrevia();
    });
  }

  protected async abrir(): Promise<void> {
    this.error.set(null);
    this.borrosa.set(false);

    if (!this.soportada()) {
      this.error.set('sellerVerification.capture.unsupported');
      return;
    }

    this.ocupada.set(true);
    try {
      // El orden importa: primero el flujo y despues abrir, para que cuando el visor se
      // pinte ya haya algo que engancharle.
      this.flujo.set(await this.camara.abrir(this.encuadre() === 'rostro'));
      this.abierta.set(true);
    } catch {
      // Denegar la cámara no es un fallo del sistema: es una decisión de la persona, y lo
      // que hace falta es explicarle cómo cambiarla (caso borde de HU-002).
      this.error.set('sellerVerification.capture.denied');
      this.apagar();
    } finally {
      this.ocupada.set(false);
    }
  }

  protected async tomar(): Promise<void> {
    const elemento = this.video()?.nativeElement;
    if (!elemento) {
      return;
    }

    this.ocupada.set(true);
    try {
      const fotograma = await this.camara.capturar(elemento);

      if (!(await this.nitidez.estaNitida(fotograma))) {
        // No se emite y no se sube: se pide otra. La cámara se queda abierta para que
        // repetir sea un botón y no volver a empezar.
        this.borrosa.set(true);
        return;
      }

      this.borrosa.set(false);
      this.tomada.set(URL.createObjectURL(fotograma));
      this.capturada.emit(fotograma);
      this.apagar();
    } catch {
      this.error.set('sellerVerification.capture.unsupported');
    } finally {
      this.ocupada.set(false);
    }
  }

  protected async repetir(): Promise<void> {
    this.soltarVistaPrevia();
    this.tomada.set(null);
    this.borrosa.set(false);
    await this.abrir();
  }

  private apagar(): void {
    this.camara.cerrar(this.flujo());
    this.flujo.set(null);
    this.abierta.set(false);
  }

  /**
   * Una URL de objeto que no se revoca es memoria retenida mientras viva la pestaña, y
   * aquí lo retenido es la foto de una cédula.
   */
  private soltarVistaPrevia(): void {
    const anterior = this.tomada();
    if (anterior !== null) {
      URL.revokeObjectURL(anterior);
    }
  }
}
