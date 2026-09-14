import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  ElementRef,
  inject,
  Injector,
  signal,
  viewChild,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { ApiError } from '../../../core/http/api-error';
import { OriginAddressStore } from '../application/origin-address.store';
import { comoSeLeeElOrigen, type OriginAddress } from '../domain/origin-address';
import { OriginAddressForm } from './origin-address-form';

/**
 * La dirección de origen del vendedor. HU-017, criterios 1, 2, 4, 9 y 14.
 *
 * <p>Es una pantalla de un solo elemento y no una lista: la tarjeta del origen si lo hay,
 * con el remitente tomado del perfil debajo, y dos acciones. Reutiliza la tarjeta y el
 * formulario de la libreta; no estrena componente.
 *
 * <p><strong>Sin guard, como `/mis-direcciones`.</strong> Quien llega sin sesión ve una
 * explicación y la forma de entrar. <strong>No se renderiza con datos en el servidor</strong>:
 * es dato personal.
 *
 * <p><strong>El acento bronce no aparece</strong>, y <strong>en ninguna parte se promete
 * que desde ese municipio se pueda recoger</strong> (criterio 24, RN-080): el estado vacío
 * habla en futuro de lo que el origen servirá, nunca de lo que ya hace.
 */
@Component({
  selector: 'sendik-origin-address-page',
  imports: [OriginAddressForm, RouterLink, TranslocoPipe],
  templateUrl: './origin-address-page.html',
  styleUrl: './origin-address-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OriginAddressPage {
  private readonly store = inject(OriginAddressStore);
  private readonly injector = inject(Injector);

  /** El encabezado, que recoge el foco cuando se destruye lo que se acaba de pulsar. */
  private readonly titulo = viewChild<ElementRef<HTMLElement>>('titulo');

  constructor() {
    this.store.abrir(true);
    inject(DestroyRef).onDestroy(() => this.store.abrir(false));
  }

  protected readonly formularioAbierto = signal(false);

  /** El aviso de qué pasó, como clave de Transloco, anunciado por la región viva. */
  protected readonly aviso = signal<string | null>(null);

  /** Lo que falló al borrar. Las escrituras también tienen su estado de error. */
  protected readonly falloDeAccion = signal<string | null>(null);

  protected readonly haySesion = computed(() => this.store.haySesion());

  protected readonly cargando = computed(
    () =>
      !this.store.sesionResuelta() ||
      (this.haySesion() && (this.store.origen.isPending() || this.store.remitente.isPending())),
  );

  protected readonly fallo = computed(
    () => this.store.origen.isError() || this.store.remitente.isError(),
  );

  protected readonly origen = computed<OriginAddress | null>(() => this.store.actual() ?? null);
  protected readonly remitente = computed(() => this.store.quienEnvia());
  protected readonly borrando = computed(() => this.store.borrando());

  protected readonly vacio = computed(
    () => !this.cargando() && !this.fallo() && this.origen() === null,
  );

  protected comoSeLee(origen: OriginAddress): string {
    return comoSeLeeElOrigen(origen);
  }

  protected abrirFormulario(): void {
    this.limpiarAvisos();
    this.formularioAbierto.set(true);
  }

  protected cerrarFormulario(): void {
    this.formularioAbierto.set(false);
    this.devolverElFoco();
  }

  protected alGuardar(): void {
    this.formularioAbierto.set(false);
    this.anunciar('originAddress.notices.saved');
    this.devolverElFoco();
  }

  /**
   * Borra el origen. Sin diálogo de confirmación, como en la libreta: lo que se pierde se
   * vuelve a escribir, y el botón no va junto a la acción principal.
   */
  protected async borrar(): Promise<void> {
    if (this.borrando()) {
      return;
    }
    this.limpiarAvisos();

    try {
      await this.store.borrado.mutateAsync();
    } catch (error) {
      this.falloDeAccion.set(claveDelError(error));
      return;
    }

    this.anunciar('originAddress.notices.removed');
    this.devolverElFoco();
  }

  protected reintentar(): void {
    this.limpiarAvisos();
    void this.store.origen.refetch();
    void this.store.remitente.refetch();
  }

  private anunciar(clave: string): void {
    this.aviso.set(clave);
  }

  private limpiarAvisos(): void {
    this.aviso.set(null);
    this.falloDeAccion.set(null);
  }

  private devolverElFoco(): void {
    afterNextRender(() => this.titulo()?.nativeElement.focus(), { injector: this.injector });
  }
}

function claveDelError(error: unknown): string {
  return error instanceof ApiError ? error.translationKey : 'errors.fallback';
}
