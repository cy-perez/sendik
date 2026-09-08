import {
  afterRenderEffect,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import { LanguageService } from '../../../core/i18n/language.service';
import { formatearComision } from './business-figures';

/**
 * Preguntas frecuentes. HU-005, criterios 12 a 18.
 *
 * <p>Con `details`/`summary` nativos antes que ARIA: el contenido queda dentro
 * del documento aunque este plegado, que es lo que resuelve a la vez el criterio
 * 14 (teclado y lector de pantalla, gratis) y el 18 (el texto completo se lee sin
 * ejecutar JavaScript).
 *
 * <p>Cada pregunta lleva identificador propio para poder enviarse por enlace
 * (criterio 13): al llegar con el fragmento en la direccion, esa pregunta se
 * despliega y recibe el foco.
 *
 * <p>Criterio 15: cada afirmacion sobre comision, pago, moderacion, verificacion
 * o medios de pago corresponde a una regla existente. La regla va citada en el
 * comentario de cada grupo, no en pantalla.
 */
@Component({
  selector: 'sendik-faq-page',
  imports: [TranslocoPipe],
  templateUrl: './faq-page.html',
  styleUrl: './content-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class FaqPage {
  private readonly negocio = inject(APP_CONFIG).business;
  private readonly idioma = inject(LanguageService);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  /**
   * El fragmento de la direccion, como senal. No llega por `withComponentInputBinding`:
   * ese enlaza parametros, consulta y datos de la ruta, nunca el fragmento.
   */
  private readonly fragmento = toSignal(inject(ActivatedRoute).fragment);

  /**
   * En el orden en que se las hace quien duda: primero si el vendedor es real,
   * despues a quien le paga, y solo entonces los detalles.
   *
   * RN-011, RN-012 (real), RN-031, RN-033 (whoGetsPaid), RN-034, RN-052
   * (release), RN-050 a RN-056 (claim), RN-050, RN-057 (fit), RN-027, RN-038,
   * RN-039 (shippingCost), RN-032 (payment), RN-042 (multiSeller).
   */
  protected readonly preguntasDelComprador = [
    'real',
    'whoGetsPaid',
    'release',
    'claim',
    'fit',
    'shippingCost',
    'payment',
    'multiSeller',
  ] as const;

  /**
   * RN-026 (commission), RN-034, RN-052 (payout), RN-008 y RN-010 a RN-012
   * (requirements), RN-016 a RN-019 (photos), RN-024 (new, forbidden), RN-015 y
   * RN-022 (moderation), RN-025 (stock), RN-055 (dispute).
   */
  protected readonly preguntasDelVendedor = [
    'commission',
    'payout',
    'requirements',
    'photos',
    'new',
    'forbidden',
    'moderation',
    'stock',
    'dispute',
  ] as const;

  protected readonly comision = computed(() =>
    formatearComision(this.negocio.commissionRate, this.idioma.current()),
  );

  protected readonly ventanaDeReclamo = this.negocio.claimWindowDays;

  constructor() {
    /**
     * Criterio 13: una respuesta concreta tiene que poder enviarse por enlace.
     *
     * <p>Con `details` plegado no basta el desplazamiento que ya hace el router
     * (`anchorScrolling`): quien abre el enlace ve el titular de la pregunta y la
     * respuesta sigue oculta, que es justo lo que el enlace prometia enseñar. Hay
     * que desplegarla y llevarle el foco.
     *
     * <p>Va en `afterRenderEffect` y no en un `effect`: necesita el DOM ya
     * pintado, y estos ganchos no corren en el servidor, donde no hay foco que
     * poner ni fragmento que el servidor llegue a ver —el navegador no lo envia—.
     * Al ser efecto y no `afterNextRender`, tambien responde a un fragmento que
     * cambie sin recargar, que es lo que pasa al pulsar dos enlaces seguidos.
     */
    afterRenderEffect(() => {
      const id = this.fragmento();
      if (id == null || id === '') {
        return;
      }

      // Un fragmento que no corresponde a ninguna pregunta no rompe nada: la
      // pagina ya esta pintada y aqui simplemente no hay a quien abrir.
      const pregunta = this.preguntaPorId(id);
      if (pregunta === null) {
        return;
      }

      pregunta.open = true;
      // El foco va al `summary`, que es lo enfocable: el `details` no lo es. Al
      // enfocarlo el navegador lo trae a la vista, asi que no hace falta
      // desplazar a mano.
      pregunta.querySelector('summary')?.focus();
    });
  }

  /**
   * Se recorren las preguntas y se compara el identificador, en vez de armar un
   * selector con el fragmento dentro. El fragmento lo escribe quien envia el
   * enlace: una direccion terminada en `#1)` no daria "no encontrado", daria un
   * selector invalido, y `querySelector` lanza. Comparando cadenas no hay nada
   * que escapar ni nada que pueda lanzar.
   */
  private preguntaPorId(id: string): HTMLDetailsElement | null {
    const preguntas = this.host.nativeElement.querySelectorAll<HTMLDetailsElement>('details');

    return [...preguntas].find((pregunta) => pregunta.id === id) ?? null;
  }
}
