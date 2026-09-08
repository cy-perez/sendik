import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import { Button } from '../../../shared/ui/button/button';
import { PrivacyNotice } from '../../../shared/legal/privacy-notice';
import { VerificationStore } from '../application/verification.store';
import { BankAccountForm } from './bank-account-form';
import { DocumentCaptureForm } from './document-capture-form';
import { SelfieCaptureForm } from './selfie-capture-form';
import {
  admiteEdicion,
  agotoIntentos,
  PASOS,
  pasoEntregado,
  puedeEnviar,
  type Paso,
  type SellerVerification,
} from '../domain/verification';

/**
 * El progreso de la verificación de vendedor. HU-002.
 *
 * Es la pantalla a la que se vuelve: muestra en qué punto va el proceso, qué falta y,
 * cuando están los tres pasos, ofrece enviar. El caso borde de la historia —salir a la
 * mitad y retomar donde iba— se resuelve aquí y no con estado en memoria: lo que se
 * pinta sale del servidor, así que retomar es volver a abrir la página.
 *
 * **No decide sola si se puede enviar.** Eso lo dice `complete`, que viene del servidor
 * e incluye la coincidencia de titular de RN-012. Comparar los dos nombres en el cliente
 * sería reimplementar la regla con otro criterio, y el servidor normaliza acentos y
 * espacios.
 */
@Component({
  selector: 'sendik-verification-page',
  imports: [
    TranslocoPipe,
    BankAccountForm,
    DocumentCaptureForm,
    SelfieCaptureForm,
    PrivacyNotice,
    Button,
  ],
  templateUrl: './verification-page.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerificationPage {
  private readonly store = inject(VerificationStore);

  protected readonly consulta = this.store.verification;
  protected readonly inicio = this.store.start;
  protected readonly envio = this.store.review;

  /** El plazo que se promete, por configuración y nunca quemado en el texto. */
  protected readonly diasDeRevision = inject(APP_CONFIG).business.verificationReviewDays;

  protected readonly pasos = PASOS;

  protected readonly verificacion = computed<SellerVerification | null>(
    () => this.consulta.data() ?? null,
  );

  /**
   * Que no haya solicitud no es un error: es no haber empezado.
   *
   * El servidor responde 404 para eso, así que un fallo con la consulta resuelta y sin
   * datos se trata como «empieza tú», no como «algo salió mal».
   */
  protected readonly sinEmpezar = computed(
    () => !this.consulta.isPending() && this.verificacion() === null,
  );

  protected readonly puedeEnviarla = computed(() => {
    const actual = this.verificacion();
    return actual !== null && puedeEnviar(actual);
  });

  protected readonly sinIntentos = computed(() => {
    const actual = this.verificacion();
    return actual !== null && agotoIntentos(actual);
  });

  protected readonly trabajando = computed(() => this.inicio.isPending() || this.envio.isPending());

  protected readonly error = computed(() => {
    const fallo = this.inicio.error() ?? this.envio.error();
    return fallo === null ? null : VerificationStore.claveDeError(fallo);
  });

  /**
   * Si tocar los datos tiene sentido ahora mismo. En revisión no: una solicitud enviada
   * no se toca mientras alguien la mira (RN-059), y ofrecer un formulario que el servidor
   * va a rechazar es peor que no ofrecerlo.
   */
  protected readonly editable = computed(() => {
    const actual = this.verificacion();
    return actual !== null && admiteEdicion(actual);
  });

  protected entregado(paso: Paso): boolean {
    const actual = this.verificacion();
    return actual !== null && pasoEntregado(actual, paso);
  }

  protected empezar(): void {
    this.inicio.mutate();
  }

  protected enviar(): void {
    this.envio.mutate();
  }
}
