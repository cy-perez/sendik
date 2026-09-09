import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import { RUTAS_LEGALES } from '../../../core/routes/legal-routes';

/**
 * El aviso de privacidad: la version corta que se entrega **donde se pide el dato**.
 *
 * <p><strong>No es la politica de tratamiento y no la sustituye.</strong> Confundir
 * las dos es el error mas frecuente en esto. La politica es el documento completo y
 * publico, vive en su ruta y se versiona; el aviso es un parrafo que se muestra en el
 * momento de recoger el dato, para que quien lo entrega sepa quien lo va a tratar,
 * para que, y como se echa atras. Uno no vale por el otro: el regimen colombiano
 * pide que las finalidades se informen **antes** de recoger, y una politica enlazada
 * a la que nadie entra no informa nada.
 *
 * <p>De ahi que sea un componente y no una pagina: se pone junto al formulario, no en
 * un sitio al que haya que ir.
 *
 * <p><strong>Dos variantes, porque los datos no son los mismos.</strong> En el
 * registro se piden datos de contacto; en la verificacion de vendedor se piden
 * documento de identidad, fotografia del rostro y cuenta bancaria, que el regimen
 * trata como sensibles y exigen decir dos cosas mas: que nadie esta obligado a
 * darlos, y para que sirven exactamente. Es lo que
 * `docs/operacion/datos-personales.md` pide cuando dice que la finalidad de la
 * verificacion se explica en el momento de pedirla y no solo en la politica.
 *
 * <p>El responsable, su identificacion, su direccion y el canal salen de la
 * configuracion y nunca del texto: son datos de negocio y no pueden quedar quemados
 * en un archivo de traduccion (`docs/operacion/configuracion.md`). Si faltan, el
 * aviso se pinta sin ellos en vez de mentir con un valor inventado. La direccion no
 * es adorno: el articulo 15 del Decreto 1377 de 2013 pide los **datos de contacto**
 * del responsable, no solo su nombre.
 */
@Component({
  selector: 'sendik-privacy-notice',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './privacy-notice.html',
  styleUrl: './privacy-notice.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PrivacyNotice {
  /**
   * `cuenta` para el registro; `verificacion` donde se piden datos sensibles.
   *
   * <p>Se elige a mano y no se deduce de la ruta a proposito: quien anada una
   * pantalla nueva que pida datos tiene que pararse a decidir cual de las dos le
   * toca, y equivocarse por omision seria dar el aviso mas flojo justo donde hace
   * falta el otro.
   */
  readonly variante = input<'cuenta' | 'verificacion'>('cuenta');

  private readonly empresa = inject(APP_CONFIG).company;

  protected readonly rutaDePrivacidad = RUTAS_LEGALES.privacy;

  protected readonly responsable = this.empresa.name;
  protected readonly identificacion = this.empresa.taxId;
  protected readonly direccion = this.empresa.address;
  protected readonly correoDeSoporte = this.empresa.supportEmail;

  /** Sin responsable ni identificacion, la primera linea no dice nada y sobra. */
  protected readonly hayResponsable = this.responsable !== null || this.identificacion !== null;

  protected readonly claveDeFinalidad = computed(() =>
    this.variante() === 'verificacion'
      ? 'legal.notice.purpose.verification'
      : 'legal.notice.purpose.account',
  );
}
