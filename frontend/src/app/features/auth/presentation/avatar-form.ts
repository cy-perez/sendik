import { NgOptimizedImage } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import { ACEPTA_IMAGENES, elTipoDeImagenEsAceptado } from '../domain/profile';

/**
 * La foto de perfil. Criterio 21 de HU-001.
 *
 * <p>Componente aparte del resto del perfil porque no comparte su ritmo: el nombre,
 * la ciudad y el telefono se guardan juntos al enviar un formulario, y la foto se
 * sube en el momento en que se elige. Mezclarlos obligaria a que guardar el nombre
 * arrastrara una subida, o a que elegir una foto no hiciera nada hasta enviar.
 *
 * <p>No hay boton de subir: se sube al elegir el archivo. Un paso mas ahi no
 * protege de nada —cambiar la foto se deshace cambiandola otra vez— y en cambio
 * deja a mucha gente creyendo que ya la subio.
 *
 * <p>Quitar la foto si lleva confirmacion en dos pasos. No es simetrico a
 * proposito: subir se corrige subiendo otra, y borrar no se corrige con nada si el
 * archivo original ya no esta en el telefono.
 */
@Component({
  selector: 'sendik-avatar-form',
  imports: [NgOptimizedImage, TranslocoPipe],
  templateUrl: './avatar-form.html',
  styleUrl: './avatar-form.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AvatarForm {
  private readonly store = inject(AuthStore);

  protected readonly perfil = this.store.profile;
  protected readonly subida = this.store.avatarUpload;
  protected readonly borrado = this.store.avatarRemoval;

  protected readonly acepta = ACEPTA_IMAGENES;

  /** Se abre solo cuando se pide: borrar no es una accion de paso. */
  protected readonly borrando = signal(false);

  /**
   * El rechazo que decide el navegador, antes de gastar la subida.
   *
   * <p>Es una cortesia, no una defensa: quien decide es el servidor mirando los
   * bytes de cabecera, y un archivo renombrado pasa por aqui y se rechaza alli.
   */
  private readonly rechazoLocal = signal<string | null>(null);

  protected readonly avatarUrl = computed(() => this.perfil.data()?.avatarUrl ?? null);

  /**
   * El error que se muestra, venga de donde venga.
   *
   * <p>El del navegador manda sobre el del servidor: es el mas reciente, porque se
   * acaba de elegir un archivo nuevo.
   */
  protected readonly error = computed(() => {
    const local = this.rechazoLocal();
    if (local !== null) {
      return local;
    }
    const fallo = this.subida.error() ?? this.borrado.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected readonly trabajando = computed(
    () => this.subida.isPending() || this.borrado.isPending(),
  );

  protected elegir(evento: Event): void {
    const campo = evento.target as HTMLInputElement;
    const archivo = campo.files?.[0];

    // Se limpia siempre el campo: sin esto, elegir el mismo archivo dos veces
    // seguidas no emite `change` y un reintento tras un fallo no haria nada.
    campo.value = '';

    if (archivo === undefined) {
      return;
    }

    if (!elTipoDeImagenEsAceptado(archivo.type)) {
      this.rechazoLocal.set('errors.byCode.FILE_TYPE_UNSUPPORTED');
      return;
    }

    this.rechazoLocal.set(null);
    this.subida.mutate(archivo);
  }

  protected quitar(): void {
    this.rechazoLocal.set(null);
    this.borrando.set(false);
    this.borrado.mutate();
  }
}
