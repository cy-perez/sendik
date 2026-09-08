import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  input,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { RouterLink } from '@angular/router';

import { ApiError } from '../../../core/http/api-error';
import { AuthStore } from '../application/auth.store';

/**
 * Pantalla que abre el enlace del correo. Criterios 7 a 9 de HU-001.
 *
 * <p>El token llega por parametro de consulta y se envia al servidor en cuanto
 * carga. Como la ruta se renderiza en servidor, el estado inicial ya viaja en el
 * HTML y no hay un parpadeo de "cargando" para quien tenga la red lenta.
 *
 * <p>Criterio 9: la persona entra directamente al verificar. El servidor
 * devuelve la sesion junto con el resultado y el almacen la guarda, asi que al
 * llegar a esta pantalla ya esta dentro y no vuelve a escribir la contrasena.
 */
@Component({
  selector: 'sendik-verify-email-page',
  imports: [TranslocoPipe, RouterLink],
  templateUrl: './verify-email-page.html',
  styleUrl: './verify-email-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyEmailPage {
  /**
   * Llega de la ruta por withComponentInputBinding.
   *
   * <p>El tipo admite undefined y no solo cadena: cuando el parametro no viene
   * en la direccion, el router asigna undefined y el valor por omision del
   * input() no se aplica. Sin esto, abrir /verificar-correo sin token dejaba la
   * pagina en blanco con un error en el registro del servidor, que es
   * exactamente el fallo en silencio que esta pantalla debe evitar.
   */
  readonly token = input<string | undefined>(undefined);

  /** Normalizado una sola vez: el resto del componente ya trabaja con una cadena. */
  private readonly tokenLimpio = computed(() => (this.token() ?? '').trim());

  private readonly store = inject(AuthStore);

  protected readonly verificacion = this.store.verification;
  protected readonly reenvio = this.store.resend;

  protected readonly sinToken = computed(() => this.tokenLimpio() === '');

  /** Solo se ofrece reenviar cuando el enlace caduco; si nunca existio, no hay que reenviar. */
  protected readonly caducado = computed(() => {
    const fallo = this.verificacion.error();
    return fallo instanceof ApiError && fallo.code === 'AUTH_VERIFICATION_TOKEN_EXPIRED';
  });

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.verificacion.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected readonly errorDelReenvio = computed(() => {
    const fallo = this.reenvio.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  constructor() {
    // Solo en el navegador, nunca durante el renderizado en servidor.
    //
    // Verificar no es leer: consume el token, que es de un solo uso (RN-003). Si
    // corriera en el servidor, cualquiera que pidiera esta direccion lo gastaria
    // sin que la persona llegara a verla. Y no hace falta un atacante: la vista
    // previa de un enlace en WhatsApp abre la pagina para sacar el titulo, y con
    // eso solo la cuenta quedaria verificada antes del primer clic y el enlace
    // ya no serviria al abrirlo.
    afterNextRender(() => {
      const token = this.tokenLimpio();
      if (token !== '' && this.verificacion.isIdle()) {
        this.verificacion.mutate(token);
      }
    });
  }

  protected reenviar(): void {
    this.reenvio.mutate(this.tokenLimpio());
  }
}
