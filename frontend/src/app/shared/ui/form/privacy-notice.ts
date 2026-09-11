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
 * <p><strong>Tres variantes, porque los datos no son los mismos.</strong> En el
 * registro se piden datos de contacto; en la verificacion de vendedor se piden
 * documento de identidad, fotografia del rostro y cuenta bancaria, que el regimen
 * trata como sensibles y exigen decir dos cosas mas: que nadie esta obligado a
 * darlos, y para que sirven exactamente. Es lo que
 * `docs/operacion/datos-personales.md` pide cuando dice que la finalidad de la
 * verificacion se explica en el momento de pedirla y no solo en la politica.
 *
 * <p>La tercera, `entrega`, llego con HU-016 y tiene algo que las otras dos no: es
 * el unico formulario del producto donde se recogen el nombre y el telefono de
 * **otra persona** —quien recibe el paquete, que puede no ser quien compra
 * (RN-104)—. Alguien que nunca abrio una cuenta ni acepto nada. Por eso su texto
 * dice a donde va a ir ese dato y de quien depende que llegue.
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
   * `cuenta` para el registro; `verificacion` donde se piden datos sensibles;
   * `entrega` donde se recoge una direccion y, con ella, datos de un tercero.
   *
   * <p>Se elige a mano y no se deduce de la ruta a proposito: quien anada una
   * pantalla nueva que pida datos tiene que pararse a decidir cual le toca, y
   * equivocarse por omision seria dar el aviso mas flojo justo donde hace falta el
   * otro. HU-016 se dejo primero **sin ninguna**, que es el caso de omision que ese
   * comentario anticipaba; lo cazo la revision de seguridad.
   */
  readonly variante = input<'cuenta' | 'verificacion' | 'entrega'>('cuenta');

  private readonly empresa = inject(APP_CONFIG).company;

  protected readonly rutaDePrivacidad = RUTAS_LEGALES.privacy;

  protected readonly responsable = this.empresa.name;
  protected readonly identificacion = this.empresa.taxId;
  protected readonly direccion = this.empresa.address;
  protected readonly correoDeSoporte = this.empresa.supportEmail;

  /** Sin responsable ni identificacion, la primera linea no dice nada y sobra. */
  protected readonly hayResponsable = this.responsable !== null || this.identificacion !== null;

  protected readonly claveDeFinalidad = computed(() => {
    switch (this.variante()) {
      case 'verificacion':
        return 'legal.notice.purpose.verification';
      case 'entrega':
        return 'legal.notice.purpose.delivery';
      default:
        return 'legal.notice.purpose.account';
    }
  });
}
