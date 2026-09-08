import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import { LanguageService } from '../../../core/i18n/language.service';
import { formatearComision } from './business-figures';

/**
 * Como funciona, con los dos recorridos. HU-005, criterios 5 a 9.
 *
 * <p>Los dos van uno tras otro y ambos visibles: ninguno queda detras de una
 * pestana ni de un acordeon que un buscador no pueda seguir (criterio 5).
 *
 * <p>Las cifras salen de la configuracion y se interpolan en el texto; ninguna
 * esta escrita en la plantilla ni en el archivo de traduccion (criterio 8). En
 * Colombia lo que se anuncia es exigible, asi que una cifra que viva en dos
 * sitios es una promesa que puede contradecirse a si misma.
 *
 * <p>Sin datos remotos: no tiene estado de carga, vacio ni error.
 *
 * <p>Es la unica de las cuatro que termina llamando a registrarse, y por eso la
 * unica con boton principal: quien acaba de leer como funciona esto es justo
 * quien esta listo para abrir cuenta.
 */
@Component({
  selector: 'sendik-how-it-works-page',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './how-it-works-page.html',
  styleUrl: './content-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HowItWorksPage {
  private readonly negocio = inject(APP_CONFIG).business;
  private readonly idioma = inject(LanguageService);

  /** Los cuatro pasos del comprador, en orden. El numero lo pinta la plantilla. */
  protected readonly pasosDelComprador = ['browse', 'pay', 'hold', 'confirm'] as const;

  /** Los cinco del vendedor. Verificarse es un paso: sin eso no se puede publicar. */
  protected readonly pasosDelVendedor = ['verify', 'publish', 'review', 'ship', 'charge'] as const;

  protected readonly deberesDelVendedor = ['describe', 'refund', 'forbidden'] as const;

  protected readonly ventajas = ['alreadyPaid', 'free', 'photos', 'payout'] as const;

  /**
   * Se recalcula al cambiar de idioma: el separador y el espacio del porcentaje
   * no son los mismos en los dos, y el cambio ocurre en caliente desde la
   * cabecera sin recargar la pagina.
   */
  protected readonly comision = computed(() =>
    formatearComision(this.negocio.commissionRate, this.idioma.current()),
  );

  protected readonly ventanaDeReclamo = this.negocio.claimWindowDays;
}
