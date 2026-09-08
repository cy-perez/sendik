import {
  ChangeDetectionStrategy,
  Component,
  computed,
  effect,
  inject,
  untracked,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { Meta, Title } from '@angular/platform-browser';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { MOTIVOS_DE_REVOCACION } from '../../../shared/domain/revocation-reason';
import { UndoAction, type DecisionDeshecha } from '../../../shared/ui/moderation/undo-action';
import { CatalogStore } from '../application/catalog.store';
import { ModerationStore } from '../application/moderation.store';
import { ProductCard } from './product-card';

/**
 * El perfil público de un vendedor. HU-009, criterios 18 a 21.
 *
 * <p><strong>Aquí no aparece ningún dato personal más allá del nombre y la foto</strong>, y
 * no porque esta pantalla se acuerde de no pintarlos: la respuesta no los trae. El backend
 * responde `SellerProfileResponse`, que tiene tres campos y ninguno donde quepa un correo.
 *
 * <p>Sin reseñas: son Fase 3. El perfil dice quién es y qué vende, no qué tal le fue a
 * nadie.
 */
@Component({
  selector: 'sendik-seller-page',
  standalone: true,
  imports: [ProductCard, RouterLink, TranslocoPipe, UndoAction],
  templateUrl: './seller-page.html',
  styleUrl: './seller-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SellerPage {
  private readonly store = inject(CatalogStore);
  private readonly idioma = inject(TranslocoService);
  private readonly title = inject(Title);
  private readonly meta = inject(Meta);

  private readonly parametros = toSignal(inject(ActivatedRoute).paramMap);

  protected readonly id = computed(() => this.parametros()?.get('id') ?? null);

  protected readonly vendedor = computed(() => this.store.vendedor.data() ?? null);
  protected readonly cargando = computed(() => this.store.vendedor.isPending());

  /** Criterio 19: no existe, no es de nadie y cuenta cerrada responden lo mismo. */
  protected readonly noEncontrado = computed(() => this.store.vendedor.isError());

  protected readonly publicaciones = this.store.publicacionesDelVendedor;
  protected readonly cargandoPublicaciones = computed(() => this.store.deVendedor.isPending());

  /** Criterio 20: sin nada publicado se dice, y no es un error. */
  protected readonly vacio = computed(
    () => !this.cargandoPublicaciones() && this.publicaciones().length === 0,
  );

  protected readonly hayMas = computed(() => this.store.deVendedor.hasNextPage());

  // --- HU-010: revocar el sello ----------------------------------------------

  private readonly moderacion = inject(ModerationStore);

  protected readonly motivos = MOTIVOS_DE_REVOCACION;

  /**
   * Si se ofrece revocar. Criterios 9, 10 y 11.
   *
   * <p>La condición del sello no sale de `quien.verified`, que es lo que la pantalla ya
   * tenía a mano, sino del estado de la verificación. Son dos cosas distintas: la insignia
   * dice que hay sello, y para revocar hace falta además el identificador de la solicitud
   * sobre la que actuar, que es lo que trae esta consulta. Usar la bandera y luego no tener
   * identificador sería ofrecer un botón que no puede hacer nada.
   *
   * <p>Falso para quien no modera, y en el servidor de renderizado también: allí la sesión
   * no está resuelta, así que la acción no llega al HTML que sale (criterio 10).
   */
  protected readonly puedeRevocar = computed(
    () => this.moderacion.puedeModerar() && this.moderacion.haySello(),
  );

  protected readonly revocando = computed(() => this.moderacion.revocar.isPending());

  protected readonly errorAlRevocar = computed(() => {
    if (!this.moderacion.revocar.isError()) {
      return null;
    }
    const fallo = this.moderacion.revocar.error() as { status?: number } | null;

    if (fallo?.status === 409) {
      return 'moderation.undo.alreadyDone';
    }
    return fallo?.status === 403 ? 'moderation.undo.ownSeal' : 'moderation.undo.failed';
  });

  protected revocar(decision: DecisionDeshecha): void {
    const verificacion = this.moderacion.verificacion.data()?.id;
    if (verificacion === undefined) {
      return;
    }

    this.moderacion.revocar.mutate({
      verificacion,
      motivo: decision.motivo as (typeof MOTIVOS_DE_REVOCACION)[number],
      nota: decision.nota,
    });
  }

  constructor() {
    effect(() => {
      const id = this.id();
      untracked(() => {
        this.store.abrirPerfil(id);
        this.moderacion.mirarVendedor(id);
      });
    });

    effect(() => {
      const quien = this.vendedor();
      const idioma = this.idioma.getActiveLang();

      if (quien === null) {
        return;
      }

      untracked(() => this.rotular(quien.name, idioma));
    });
  }

  protected verMas(): void {
    this.store.siguienteTramoDelVendedor();
  }

  private rotular(nombre: string, idioma: string): void {
    const rotulo = this.idioma.translate('meta.sellerProfile.title', { nombre });
    this.title.setTitle(rotulo);
    this.meta.updateTag({ property: 'og:title', content: rotulo });

    const descripcion = this.idioma.translate('meta.sellerProfile.description', { nombre });
    this.meta.updateTag({ name: 'description', content: descripcion });
    this.meta.updateTag({ property: 'og:description', content: descripcion });
    this.meta.updateTag({ property: 'og:locale', content: idioma });
  }
}
