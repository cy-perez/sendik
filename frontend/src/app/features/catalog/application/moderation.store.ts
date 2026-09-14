import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import type { ListingRejectionReason } from '../../../shared/domain/listing';
import type { RevocationReason } from '../../../shared/domain/revocation-reason';
import { ModerationApi } from '../infrastructure/moderation.api';
import { queryKeys } from './query-keys';

/** El rol que habilita las dos acciones. El mismo que exigen las rutas de moderación. */
/**
 * Deshacer lo que un moderador ya decidió, desde las pantallas públicas. HU-010.
 *
 * <p><strong>Nada de esto existe para quien no modera.</strong> `puedeModerar` sale de la
 * sesión, que en el servidor de renderizado no está resuelta nunca: la cookie de refresco
 * es de quien navega y el SSR no la usa. Eso hace que el criterio 2 se cumpla solo —la
 * acción no está en el HTML que sale del servidor— en vez de depender de un `@if` que
 * alguien pueda mover.
 *
 * <p>La comprobación de pantalla no es la regla: el servidor exige el rol en las tres
 * rutas y lo volvería a rechazar. Esconder el botón evita enterarse al pulsar, con un
 * correo ya prometido.
 */
@Injectable({ providedIn: 'root' })
export class ModerationStore {
  private readonly api = inject(ModerationApi);
  private readonly sesion = inject(SessionStore);
  private readonly consultas = inject(QueryClient);

  readonly puedeModerar = this.sesion.esModerador;

  /** De qué vendedor se quiere saber si tiene sello. Lo fija el perfil al abrirse. */
  private readonly vendedor = signal<string | null>(null);

  /**
   * La verificación de ese vendedor.
   *
   * <p>Solo sale si quien mira modera: para cualquier otra persona la ruta responde 403 y
   * pedirla sería fabricar un error en cada visita al perfil de un vendedor.
   *
   * <p>Sin reintentos. El 404 es la respuesta normal a «esta persona nunca empezó la
   * verificación», y reintentarlo tres veces solo retrasa que la pantalla deje de ofrecer
   * lo que no hay.
   */
  readonly verificacion = injectQuery(() => ({
    queryKey: queryKeys.verification(this.vendedor() ?? 'ninguno'),
    queryFn: () => this.api.verificacionDe(this.vendedor() ?? ''),
    enabled: this.vendedor() !== null && this.puedeModerar(),
    retry: false,
  }));

  /** Si hay sello que quitar. Cualquier otro estado no ofrece la acción (criterio 11). */
  readonly haySello = computed(() => this.verificacion.data()?.status === 'VERIFIED');

  readonly bajar = injectMutation(() => ({
    mutationFn: (orden: {
      readonly id: string;
      readonly motivo: ListingRejectionReason;
      readonly nota: string | null;
    }) => this.api.bajar(orden.id, orden.motivo, orden.nota),
    // La ficha se vuelve a pedir: ya no está publicada, así que la respuesta pasa a ser
    // 404 y la pantalla dice que no está disponible. Es el criterio 5, y sale solo.
    onSuccess: (_resultado, orden) => {
      // Las dos vistas de la ficha, no solo la pública: quien acaba de bajarla está mirando
      // la suya, y con la copia vieja seguía viendo la acción de bajar algo ya archivado.
      void this.consultas.invalidateQueries({ queryKey: queryKeys.anyOne(orden.id) });
      void this.consultas.invalidateQueries({ queryKey: ['catalog', 'public', 'list'] });
    },
  }));

  readonly revocar = injectMutation(() => ({
    mutationFn: (orden: {
      readonly verificacion: string;
      readonly motivo: RevocationReason;
      readonly nota: string | null;
    }) => this.api.revocar(orden.verificacion, orden.motivo, orden.nota),
    // El perfil se vuelve a pedir para que la insignia desaparezca (criterio 14), y con él
    // la verificación, que ya no está en VERIFIED y no debe volver a ofrecer la acción.
    onSuccess: () => {
      const quien = this.vendedor();
      if (quien === null) {
        return;
      }
      void this.consultas.invalidateQueries({ queryKey: queryKeys.seller(quien) });
      void this.consultas.invalidateQueries({ queryKey: queryKeys.verification(quien) });
    },
  }));

  /** El perfil fija de quién se está mirando el sello al resolver la ruta. */
  mirarVendedor(id: string | null): void {
    this.vendedor.set(id);
  }
}
