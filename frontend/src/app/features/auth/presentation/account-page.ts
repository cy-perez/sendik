import {
  ChangeDetectionStrategy,
  Component,
  computed,
  inject,
  PLATFORM_ID,
  signal,
} from '@angular/core';
import { DatePipe, isPlatformBrowser } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { SessionStore } from '../../../core/session/session.store';
import { AuthStore } from '../application/auth.store';
import { laConfirmacionCoincide } from '../domain/account';
import { AvatarForm } from './avatar-form';
import { EmailChangeForm } from './email-change-form';
import { ProfileForm } from './profile-form';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Lo que una persona puede hacer sobre su propia cuenta. Criterios 17, 21, 22 y 23.
 *
 * <p>Todo vive junto porque es la misma idea: el control sobre los propios
 * datos. Separarlo en cuatro pantallas obligaria a buscarlas.
 *
 * <p>El perfil y el cambio de correo son componentes aparte: cada uno tiene su
 * formulario, su validacion y su estado de envio, y mezclarlos aqui obligaria a
 * distinguir con prefijos cual error es de cual campo.
 *
 * <p>El cierre va al final y detras de una confirmacion escrita, no porque
 * estorbe sino porque no se deshace.
 */
@Component({
  selector: 'sendik-account-page',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    DatePipe,
    TextField,
    SubmitButton,
    ProfileForm,
    AvatarForm,
    EmailChangeForm,
  ],
  templateUrl: './account-page.html',
  styleUrl: './account-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AccountPage {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly enElNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  protected readonly sesion = inject(SessionStore);

  protected readonly sesiones = this.store.sessions;
  protected readonly cierreDeSesion = this.store.sessionRevocation;
  protected readonly descarga = this.store.dataExport;
  protected readonly cierreDeCuenta = this.store.accountClosure;

  /** Se abre solo cuando la persona lo pide: cerrar no es una accion de paso. */
  protected readonly cerrando = signal(false);

  /**
   * El control va dentro de un grupo aunque sea uno solo. Sin {@code formGroup} en
   * el formulario, Angular no instala la directiva que emite {@code ngSubmit} y el
   * envio no llega nunca al componente.
   */
  protected readonly form = new FormGroup({
    confirmacion: new FormControl('', { nonNullable: true }),
  });

  private readonly escrito = toSignal(this.form.controls.confirmacion.valueChanges, {
    initialValue: '',
  });

  protected readonly intentado = signal(false);

  /**
   * La misma comprobacion que hace el servidor. Aqui solo evita un viaje: quien
   * decide sigue siendo el servidor.
   */
  private readonly coincide = computed(() =>
    laConfirmacionCoincide(this.escrito(), this.sesion.user()?.email ?? ''),
  );

  protected readonly errorDeConfirmacion = computed(() =>
    this.intentado() && !this.coincide() ? 'auth.account.close.errors.confirmation' : null,
  );

  protected readonly errorDeLista = computed(() => {
    const fallo = this.sesiones.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected readonly errorDeCierre = computed(() => {
    const fallo = this.cierreDeCuenta.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected control(): FormControl<string> {
    return this.form.controls.confirmacion;
  }

  protected cerrarSesion(id: string): void {
    this.cierreDeSesion.mutate(id);
  }

  /**
   * Criterio 22. El navegador guarda el archivo con un enlace temporal.
   *
   * <p>Se construye aqui y no se abre la direccion de la API directamente porque
   * esa peticion necesita la cabecera de autorizacion, y una navegacion del
   * navegador no la lleva: acabaria en un 401.
   */
  protected descargar(): void {
    if (!this.enElNavegador) {
      return;
    }

    this.descarga.mutate(undefined, {
      onSuccess: (contenido: string) => {
        const enlace = document.createElement('a');
        const url = URL.createObjectURL(new Blob([contenido], { type: 'application/json' }));

        enlace.href = url;
        enlace.download = 'sendik-mis-datos.json';
        enlace.click();

        // Sin esto el contenido queda retenido en memoria mientras viva la
        // pestana, y es un archivo con datos personales.
        URL.revokeObjectURL(url);
      },
    });
  }

  protected cerrarCuenta(): void {
    this.intentado.set(true);
    if (!this.coincide()) {
      return;
    }

    this.cierreDeCuenta.mutate(this.form.getRawValue().confirmacion, {
      onSuccess: () => {
        void this.router.navigateByUrl('/');
      },
    });
  }
}
