import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  input,
  signal,
  viewChild,
  ViewEncapsulation,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import type { LegalContent } from '../application/legal-content.resolver';
import { esBorrador } from '../domain/legal-document';

/**
 * Cualquiera de los tres documentos legales del sitio.
 *
 * <p>Un solo componente y no tres casi iguales: lo unico que cambia entre ellos
 * es el titulo y el texto, y tres copias de la misma pagina se separan sola en
 * cuanto alguien toque una.
 *
 * <p>El texto llega ya resuelto desde la ruta, asi que viaja en el HTML del
 * servidor. La version se muestra en pantalla a proposito: es lo que permite
 * comprobar, meses despues, que el texto que alguien acepto es este.
 */
/**
 * <p><strong>Unico componente del proyecto sin encapsulacion de estilos.</strong>
 * El texto entra por {@code innerHTML} y ese HTML no recibe el atributo que
 * Angular usa para acotar los estilos, asi que las reglas normales no lo
 * alcanzarian y el documento saldria sin maquetar. La alternativa era
 * {@code ::ng-deep}, que esta obsoleto. Como contrapartida, todas las reglas de
 * legal-page.css cuelgan de .documento-legal y ninguna se escribe suelta.
 */
@Component({
  selector: 'sendik-legal-page',
  imports: [TranslocoPipe],
  templateUrl: './legal-page.html',
  styleUrl: './legal-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
  encapsulation: ViewEncapsulation.None,
})
export class LegalPage {
  /** Lo entrega el resolutor de la ruta a traves de withComponentInputBinding. */
  readonly contenido = input.required<LegalContent>();

  protected readonly documento = computed(() => this.contenido().documento);

  protected readonly html = computed(() => this.contenido().html);

  /** Clave del titulo: auth.legal.terms.title y sus hermanas. */
  protected readonly tituloKey = computed(() => `legal.${this.documento().id}.title`);

  /**
   * Un borrador enlazado desde una casilla de consentimiento es peor que no
   * tener pagina: la persona creeria haber leido algo que no la obliga a nada, y
   * el sitio tendria guardada la evidencia de que lo acepto. Se dice en voz alta.
   */
  protected readonly esUnBorrador = computed(() => esBorrador(this.documento().version));

  /**
   * La medida vive en {@code marca.css}, en {@code --altura-recorte-legal}, y se lee
   * de ahi en vez de repetirla aqui.
   *
   * <p>No es purismo: son dos usos del mismo numero -el CSS recorta a esa altura y
   * esto decide si merece la pena recortar- y si se separan, el documento se recorta
   * a una altura y el boton aparece a otra. Un umbral mayor que el recorte deja
   * documentos cortados sin boton con el que abrirlos, que es la peor de las dos
   * averias porque se lleva por delante el final de un contrato.
   */
  private static readonly MEDIDA_DEL_RECORTE = '--altura-recorte-legal';

  private readonly texto = viewChild<ElementRef<HTMLElement>>('textoDelDocumento');

  /**
   * Si este documento es lo bastante largo como para ofrecer el recorte.
   *
   * <p><strong>Nace en falso y solo puede encenderse en el navegador.</strong> No es
   * un detalle de implementacion: es lo que hace que el recorte sea seguro en un
   * documento legal. El HTML que sale del servidor lleva el texto entero y sin
   * recortar, asi que quien no ejecute JavaScript, quien imprima la pagina o quien
   * la lea con un buscador ve el documento completo. Recortar en el servidor
   * dejaria un contrato a medias detras de un boton que quiza nunca funcione, y el
   * deber de informacion no se cumple con un texto que la persona no puede
   * alcanzar.
   */
  protected readonly recortable = signal(false);

  protected readonly expandido = signal(false);

  /** El recorte solo se aplica mientras nadie lo haya abierto. */
  protected readonly recortado = computed(() => this.recortable() && !this.expandido());

  protected readonly claveDelBoton = computed(() =>
    this.expandido() ? 'legal.expand.less' : 'legal.expand.more',
  );

  constructor() {
    afterNextRender(() => {
      const elemento = this.texto()?.nativeElement;
      if (!elemento) {
        return;
      }

      const declarada = getComputedStyle(elemento)
        .getPropertyValue(LegalPage.MEDIDA_DEL_RECORTE)
        .trim();
      const altura = Number.parseInt(declarada, 10);

      // Sin la variable no se recorta. Es preferible un documento largo a uno
      // cortado por un numero inventado aqui.
      if (Number.isFinite(altura) && elemento.scrollHeight > altura) {
        this.recortable.set(true);
      }
    });
  }

  protected alternar(): void {
    this.expandido.update((abierto) => !abierto);
  }
}
