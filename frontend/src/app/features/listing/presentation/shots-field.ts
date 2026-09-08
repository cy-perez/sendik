import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import {
  posicionesAPintar,
  POSICIONES_CANONICAS,
  tomaEn,
  tomasDelVendedor,
  type Listing,
  type ListingImage,
} from '../../../shared/domain/listing';
import { admiteAsistente } from '../domain/capture-steps';
import { canonicasQueFaltan, gradosDe, tomasQueFaltan } from '../domain/publish-rules';

/** Una casilla de la rejilla: su posición, sus grados y la toma que tenga. */
export interface Casilla {
  readonly posicion: number;
  readonly grados: number;
  readonly canonica: boolean;
  readonly toma: ListingImage | null;
}

/**
 * La rejilla de tomas. HU-007, criterios 14 a 18.
 *
 * <p>Pinta ocho casillas, o cuatro si la publicación es tecnología declarada sellada
 * (RN-065). **Cuántas se exigen lo dice el servidor** en `requiredShots`: calcularlo aquí
 * sería tener la misma regla en dos sitios con dos formas de estar mal.
 *
 * <p>Las cuatro canónicas —frente, lado, atrás y el otro lado— se rotulan aparte porque
 * son las únicas obligatorias por sí mismas (RN-016): sin ellas el envío se rechaza
 * aunque el total cuadre.
 *
 * <p>No valida la imagen. Lo hace el servidor, que decide el tipo por los bytes de
 * cabecera, quita el EXIF y comprueba proporción y mínimo (ADR-0018). Comprobar aquí el
 * tamaño daría una segunda respuesta a la misma pregunta, y la del servidor es la que
 * manda.
 */
@Component({
  selector: 'sendik-shots-field',
  imports: [NgOptimizedImage, RouterLink, TranslocoPipe],
  templateUrl: './shots-field.html',
  styleUrl: './shots-field.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ShotsField {
  readonly publicacion = input.required<Listing>();
  readonly deshabilitado = input<boolean>(false);
  readonly subiendo = input<number | null>(null);

  readonly subir = output<{ posicion: number; imagen: File }>();
  readonly quitar = output<string>();

  protected readonly casillas = computed<readonly Casilla[]>(() =>
    posicionesAPintar(this.publicacion()).map((posicion) => ({
      posicion,
      grados: gradosDe(posicion),
      canonica: POSICIONES_CANONICAS.includes(posicion),
      toma: tomaEn(this.publicacion(), posicion),
    })),
  );

  /** HU-003: el asistente solo se ofrece a la secuencia de ocho (RN-065). */
  protected readonly conAsistente = computed(() => admiteAsistente(this.publicacion()));

  protected readonly puestas = computed(() => tomasDelVendedor(this.publicacion()).length);
  protected readonly exigidas = computed(() => this.publicacion().requiredShots);
  protected readonly faltan = computed(() => tomasQueFaltan(this.publicacion()));
  protected readonly canonicasPendientes = computed(() => canonicasQueFaltan(this.publicacion()));

  /**
   * El archivo elegido, o nada.
   *
   * <p>El campo se limpia después de emitir para que elegir **el mismo archivo** dos
   * veces seguidas vuelva a disparar el evento. Sin eso, quien sube una foto, ve que
   * falló y la elige otra vez no consigue nada y no entiende por qué.
   */
  protected alElegir(evento: Event, posicion: number): void {
    const campo = evento.target as HTMLInputElement;
    const imagen = campo.files?.[0];

    if (imagen) {
      this.subir.emit({ posicion, imagen });
    }
    campo.value = '';
  }
}
