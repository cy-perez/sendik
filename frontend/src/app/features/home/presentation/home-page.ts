import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

/** Un paso del bloque de como funciona. El numero lo pinta la plantilla. */
interface Paso {
  readonly id: string;
}

/** Una tarjeta de confianza. Cada promesa apunta a una regla de negocio. */
interface Confianza {
  readonly id: string;
}

/**
 * Portada. HU-004.
 *
 * <p>No carga nada remoto: no tiene estado de carga, vacio ni error. Todo lo que
 * se ve viaja dentro del HTML que sirve el servidor, que es de lo que vive el
 * posicionamiento del sitio.
 *
 * <p><strong>El host es `display: contents`</strong> (ver home-page.css) para que
 * las tres secciones sean hijas directas de la rejilla de `main` y el hero pueda
 * pedir el carril a sangre completa.
 */
@Component({
  selector: 'sendik-home-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './home-page.html',
  styleUrl: './home-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HomePage {
  /**
   * Publicar, vender, cobrar. En ese orden y siempre los tres.
   *
   * <p>Se recorren en vez de escribirse tres veces para que agregar o quitar uno
   * sea tocar esta lista y las traducciones, y no copiar un bloque que acabaria
   * separandose del resto.
   */
  protected readonly pasos: readonly Paso[] = [{ id: 'publish' }, { id: 'sell' }, { id: 'charge' }];

  /**
   * Las tres promesas de la portada: retencion del pago (RN-034), vendedores
   * verificados (RN-011) y publicaciones moderadas (RN-015).
   *
   * <p>Ninguna menciona devoluciones ni plazos de reembolso: esa politica no
   * existe todavia y prometerla aqui seria inventarla.
   */
  protected readonly confianza: readonly Confianza[] = [
    { id: 'hold' },
    { id: 'verified' },
    { id: 'moderated' },
  ];
}
