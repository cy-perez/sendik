import {
  ChangeDetectionStrategy,
  Component,
  computed,
  DOCUMENT,
  effect,
  ElementRef,
  inject,
  PLATFORM_ID,
  signal,
  viewChild,
} from '@angular/core';
import { isPlatformBrowser } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { LanguageService } from '../../../core/i18n/language.service';
import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from '../../../core/routes/content-routes';
import { ThemeService } from '../../../core/theme/theme.service';

/**
 * Por debajo de esto la navegacion se sustituye por el menu. Punto de quiebre
 * del sistema, y una sola fuente: la consulta se construye desde el numero para
 * que el respaldo sin matchMedia no pueda quedarse en otro ancho.
 */
const ANCHO_COMPACTO = 640;
const COMPACTO = `(max-width: ${ANCHO_COMPACTO - 0.02}px)`;

@Component({
  selector: 'sendik-site-header',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './site-header.html',
  styleUrl: './site-header.css',
  // El teclado se escucha en el host y no en un elemento de la plantilla: tiene
  // que llegarle lo que se pulse en cualquier parte de la cabecera, y el boton
  // que abre el menu vive fuera del panel. Puesto en el `header` de dentro, el
  // linter pide hacerlo enfocable, y meter un contenedor en el orden de
  // tabulacion es una parada de mas para quien navega con teclado.
  host: { '(keydown)': 'alPulsarTecla($event)' },
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SiteHeader {
  private readonly theme = inject(ThemeService);
  private readonly language = inject(LanguageService);
  private readonly document = inject(DOCUMENT);
  private readonly esNavegador = isPlatformBrowser(inject(PLATFORM_ID));

  private readonly panel = viewChild<ElementRef<HTMLElement>>('panel');
  private readonly boton = viewChild<ElementRef<HTMLButtonElement>>('boton');

  protected readonly currentTheme = this.theme.current;
  protected readonly currentLocale = this.language.current;
  protected readonly locales = this.language.available;

  /**
   * Los enlaces salen de core/routes, nunca escritos a mano: si una direccion
   * cambia, este menu cambia con ella, y no puede nombrar una ruta que no exista
   * (criterio 25 de HU-005). Por eso tampoco hay forma de que aparezca aqui un
   * enlace a catalogo o busqueda: no estan en esa lista.
   */
  protected readonly paginas = PAGINAS_DE_CONTENIDO;

  protected readonly abierto = signal(false);

  /**
   * Si la pantalla esta en el modo compacto. En el servidor es falso y el menu
   * sale cerrado: no hay ventana que medir, y el HTML servido lleva igualmente
   * todos los enlaces dentro, que es lo que importa para el buscador.
   */
  private readonly compacto = signal(false);

  /** El foco solo se atrapa cuando hay algo que atrapar: un panel desplegado. */
  protected readonly atrapaFoco = computed(() => this.abierto() && this.compacto());

  protected readonly themeLabelKey = computed(() =>
    this.currentTheme() === 'dark' ? 'layout.theme.toLight' : 'layout.theme.toDark',
  );

  protected readonly menuLabelKey = computed(() =>
    this.abierto() ? 'layout.nav.close' : 'layout.nav.open',
  );

  constructor() {
    // Se comprueba que matchMedia exista y no solo que haya ventana: hay
    // entornos con `window` y sin ella, y la cabecera se pinta en todas las
    // paginas del sitio. Sin la guarda, uno de esos entornos no ve un menu algo
    // peor colocado: no ve nada, porque el componente raiz lanza al construirse.
    const ventana = this.esNavegador ? this.document.defaultView : null;
    if (ventana != null && typeof ventana.matchMedia === 'function') {
      const consulta = ventana.matchMedia(COMPACTO);
      this.compacto.set(consulta.matches);
      consulta.addEventListener('change', (evento) => this.compacto.set(evento.matches));
    } else if (ventana != null) {
      // Respaldo para el entorno con ventana y sin matchMedia. El CSS no depende
      // de JavaScript, asi que ahi la hamburguesa se ve igual; sin esta medida el
      // menu se abria sin Escape y sin foco atrapado, que es la peor de las tres
      // combinaciones posibles. No se vuelve a medir al girar el telefono, pero
      // se abre y se cierra como debe.
      this.compacto.set(ventana.innerWidth < ANCHO_COMPACTO);
    }

    /**
     * Al pasar a escritorio el menu se cierra solo. Sin esto, quien abre el menu
     * en el movil y luego gira el telefono se queda con el foco atrapado en una
     * navegacion que ya se ve entera y sin boton visible para cerrarla.
     */
    effect(() => {
      if (!this.compacto()) {
        this.abierto.set(false);
      }
    });
  }

  protected alternarMenu(): void {
    this.abierto.update((estaba) => !estaba);
  }

  /** Navegar cierra el menu: dejarlo abierto sobre la pagina nueva no lo espera nadie. */
  protected cerrarMenu(): void {
    this.abierto.set(false);
  }

  protected toggleTheme(): void {
    this.theme.toggle();
  }

  protected onLanguageChange(event: Event): void {
    const select = event.target as HTMLSelectElement;
    this.language.change(select.value);
  }

  /**
   * Escape cierra y **devuelve el foco al boton**. Sin lo segundo el foco se
   * queda en un elemento que acaba de ocultarse y el navegador lo manda al
   * principio del documento: quien navega con teclado tendria que recorrer la
   * pagina entera otra vez.
   */
  protected alPulsarTecla(evento: KeyboardEvent): void {
    if (!this.atrapaFoco()) {
      return;
    }

    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.cerrarMenu();
      this.boton()?.nativeElement.focus();
      return;
    }

    if (evento.key === 'Tab') {
      this.atrapar(evento);
    }
  }

  /**
   * El ciclo del tabulador dentro del panel.
   *
   * <p>Se consultan los elementos enfocables en el momento, no al abrir: el menu
   * lleva dentro el hueco proyectado de la sesion, que cambia segun haya sesion
   * o no, y una lista guardada al abrir se quedaria vieja.
   */
  private atrapar(evento: KeyboardEvent): void {
    const dentro = this.enfocables();
    if (dentro.length === 0) {
      return;
    }

    const primero = dentro[0]!;
    const ultimo = dentro[dentro.length - 1]!;
    const activo = this.document.activeElement;

    if (evento.shiftKey && activo === primero) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && activo === ultimo) {
      evento.preventDefault();
      primero.focus();
    }
  }

  private enfocables(): HTMLElement[] {
    const raiz = this.panel()?.nativeElement;
    if (raiz === undefined) {
      return [];
    }

    // El boton entra en el ciclo aunque este fuera del panel: es el que cierra, y
    // dejarlo fuera obliga a usar Escape para salir.
    const boton = this.boton()?.nativeElement;
    const delPanel = [
      // input, textarea, summary y contenteditable entran aunque hoy el panel no
      // los use: lo que se proyecta ahi es contenido de sesion, y el dia que
      // traiga un campo el ciclo del tabulador tiene que incluirlo.
      ...raiz.querySelectorAll<HTMLElement>(
        'a[href], button:not([disabled]), select:not([disabled]), input:not([disabled]), textarea:not([disabled]), summary, [contenteditable="true"], [tabindex]',
      ),
    ].filter((elemento) => elemento.tabIndex !== -1);

    return boton === undefined ? delPanel : [boton, ...delPanel];
  }

  protected rutaDe(pagina: (typeof PAGINAS_DE_CONTENIDO)[number]): string {
    return RUTAS_CONTENIDO[pagina];
  }
}
