import { ChangeDetectionStrategy, Component, DOCUMENT } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { PAGINAS_DE_CONTENIDO, RUTAS_CONTENIDO } from '../../../core/routes/content-routes';
import { SiteHeader } from './site-header';
import { ThemeService } from '../../../core/theme/theme.service';

/** Destino de relleno: aqui no se prueba a donde se llega, sino que se navegue. */
@Component({
  selector: 'sendik-destino',
  template: '',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
class Destino {}

/**
 * Se consulta por rol y por texto accesible, que es como lo encuentra quien usa
 * el sitio: una prueba atada al selector CSS se rompe al maquetar y no dice
 * nada sobre si el control se puede usar.
 */
describe('SiteHeader', () => {
  let document: Document;

  const render = async () => {
    const fixture = TestBed.createComponent(SiteHeader);
    await fixture.whenStable();
    return fixture;
  };

  const byLabel = (label: string): HTMLElement | null =>
    document.querySelector(`[aria-label="${label}"]`);

  beforeEach(() => {
    // Las rutas de contenido existen de verdad en el router de la prueba: pulsar
    // un enlace del menu navega, y sin ellas el router lanza NG04002 y la prueba
    // falla por un motivo que no tiene que ver con lo que comprueba.
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          PAGINAS_DE_CONTENIDO.map((pagina) => ({
            path: RUTAS_CONTENIDO[pagina].slice(1),
            component: Destino,
          })),
        ),
      ],
    });
    document = TestBed.inject(DOCUMENT);
    document.documentElement.setAttribute('data-tema', 'claro');
  });

  it('ofrece un enlace para saltar al contenido como primer elemento enfocable', async () => {
    const fixture = await render();
    const link = fixture.nativeElement.querySelector('a');

    expect(link?.getAttribute('href')).toBe('#contenido');
    expect(link?.textContent?.trim()).toBe('Saltar al contenido');
  });

  /**
   * Cuatro imagenes: lockup y isotipo, cada uno en su version para fondo claro y
   * para fondo oscuro. Cual se ve lo deciden dos ejes de CSS —tema y ancho—, no
   * el componente, asi que las cuatro estan siempre en el DOM. Lo que este caso
   * protege es que ninguna de ellas aporte nombre accesible: si una llevara alt,
   * el lector de pantalla anunciaria la marca dos veces seguidas.
   */
  it('nombra el logo una sola vez, en el enlace y no en las imagenes', async () => {
    const fixture = await render();
    const home = byLabel('Sendik, ir al inicio');
    const images = fixture.nativeElement.querySelectorAll('img');

    expect(home).not.toBeNull();
    expect(images).toHaveLength(4);
    for (const image of images) {
      expect((image as HTMLImageElement).getAttribute('alt')).toBe('');
    }
  });

  it('presenta los idiomas disponibles con su etiqueta asociada', async () => {
    const fixture = await render();
    const select = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
    const label = fixture.nativeElement.querySelector(`label[for="${select.id}"]`);

    expect(label?.textContent?.trim()).toBe('Idioma');
    expect([...select.options].map((option) => option.value)).toEqual(['es', 'en']);
    expect([...select.options].map((option) => option.textContent?.trim())).toEqual([
      'Español',
      'English',
    ]);
  });

  // La etiqueta anuncia a donde lleva el boton, no en que estado esta: es lo
  // que espera oir quien no ve el icono.
  it('el conmutador de tema anuncia la accion y refleja el estado', async () => {
    const fixture = await render();
    const button = byLabel('Cambiar a modo oscuro') as HTMLButtonElement;

    expect(button).not.toBeNull();
    expect(button.getAttribute('aria-pressed')).toBe('false');

    button.click();
    await fixture.whenStable();

    expect(byLabel('Cambiar a modo claro')).not.toBeNull();
    expect(TestBed.inject(ThemeService).current()).toBe('dark');
    expect(document.documentElement.getAttribute('data-tema')).toBe('oscuro');
  });

  /**
   * La navegacion principal. HU-005: lleva a las cuatro paginas informativas y a
   * ninguna otra parte.
   */
  describe('navegacion principal', () => {
    const enlacesDeNav = (raiz: HTMLElement) => [
      ...raiz.querySelectorAll<HTMLAnchorElement>('.enlace-nav'),
    ];

    it('enlaza las cuatro paginas informativas', async () => {
      const fixture = await render();
      const destinos = enlacesDeNav(fixture.nativeElement).map((a) => a.getAttribute('href'));

      expect(destinos).toEqual([
        '/como-funciona',
        '/sobre-sendik',
        '/preguntas-frecuentes',
        '/contacto',
      ]);
    });

    /**
     * Criterio 25. Catalogo, publicacion y busqueda son de Fase 2 y 3: un enlace
     * a cualquiera de las tres seria un 404 servido desde la cabecera de todas
     * las paginas del sitio.
     */
    it('no lleva a catalogo, publicacion ni busqueda', async () => {
      const fixture = await render();
      const destinos = enlacesDeNav(fixture.nativeElement).map((a) => a.getAttribute('href') ?? '');

      for (const destino of destinos) {
        expect(destino).not.toMatch(/catalogo|productos|publicar|buscar|carrito/);
      }
    });

    /**
     * El HTML servido lleva los enlaces dentro aunque el menu este cerrado. Es lo
     * que separa "el menu se despliega con JavaScript" de "el buscador no ve la
     * navegacion del sitio".
     */
    it('los enlaces estan en el documento con el menu cerrado', async () => {
      const fixture = await render();

      expect(enlacesDeNav(fixture.nativeElement)).toHaveLength(4);
    });

    it('el boton del menu anuncia si esta desplegado', async () => {
      const fixture = await render();
      const boton = byLabel('Abrir el menú') as HTMLButtonElement;

      expect(boton.getAttribute('aria-expanded')).toBe('false');
      expect(boton.getAttribute('aria-controls')).toBe('menu-principal');

      boton.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(byLabel('Cerrar el menú')?.getAttribute('aria-expanded')).toBe('true');
    });

    // Dejar el menu abierto encima de la pagina nueva no lo espera nadie.
    it('navegar cierra el menu', async () => {
      const fixture = await render();
      (byLabel('Abrir el menú') as HTMLButtonElement).click();
      await fixture.whenStable();
      fixture.detectChanges();

      enlacesDeNav(fixture.nativeElement)[0]!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(byLabel('Abrir el menú')?.getAttribute('aria-expanded')).toBe('false');
    });

    /**
     * Escape cierra y devuelve el foco al boton. Sin lo segundo el foco se queda
     * en un elemento que acaba de ocultarse y el navegador lo manda al principio
     * del documento: quien navega con teclado recorreria la pagina otra vez.
     */
    it('Escape cierra el menu y devuelve el foco al boton', async () => {
      const fixture = await render();
      const abrir = byLabel('Abrir el menú') as HTMLButtonElement;
      abrir.click();
      await fixture.whenStable();
      fixture.detectChanges();

      // El menu solo atrapa el foco en modo compacto, que es lo que aqui se
      // simula: en jsdom no hay ventana que consultar con matchMedia.
      const componente = fixture.componentInstance as unknown as {
        compacto: { set: (v: boolean) => void };
      };
      componente.compacto.set(true);
      fixture.detectChanges();

      const cerrar = byLabel('Cerrar el menú') as HTMLButtonElement;
      cerrar.focus();
      cerrar.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      await fixture.whenStable();
      fixture.detectChanges();

      expect(byLabel('Abrir el menú')?.getAttribute('aria-expanded')).toBe('false');
      expect(document.activeElement).toBe(byLabel('Abrir el menú'));
    });
  });

  /**
   * El foco atrapado mientras el menu esta desplegado (HU-005, alcance).
   *
   * <p>Sin el, tabular desde el ultimo enlace del menu lleva al contenido de la
   * pagina que hay debajo, que en movil ni siquiera se ve. Quien navega con
   * teclado se queda moviendose por una pagina invisible sin forma de volver.
   *
   * <p>Solo se atrapa en modo compacto: en escritorio la navegacion es una fila
   * mas de la cabecera y atrapar ahi seria encerrar a la persona en el sitio.
   */
  describe('foco atrapado en el menu compacto', () => {
    const enCompacto = async () => {
      const fixture = await render();
      (byLabel('Abrir el menú') as HTMLButtonElement).click();
      await fixture.whenStable();

      const componente = fixture.componentInstance as unknown as {
        compacto: { set: (v: boolean) => void };
        atrapaFoco: () => boolean;
      };
      componente.compacto.set(true);
      fixture.detectChanges();
      return { fixture, componente };
    };

    const tabular = (desde: HTMLElement, shift = false) => {
      desde.focus();
      desde.dispatchEvent(
        new KeyboardEvent('keydown', { key: 'Tab', shiftKey: shift, bubbles: true }),
      );
    };

    it('se activa solo con el menu abierto y en compacto', async () => {
      const { componente } = await enCompacto();

      expect(componente.atrapaFoco()).toBe(true);

      componente.compacto.set(false);
      expect(componente.atrapaFoco()).toBe(false);
    });

    it('desde el ultimo enfocable el tabulador vuelve al boton', async () => {
      const { fixture } = await enCompacto();
      const dentro = [
        ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
          '.panel a[href], .panel button, .panel select',
        ),
      ];

      tabular(dentro[dentro.length - 1]!);

      expect(document.activeElement).toBe(byLabel('Cerrar el menú'));
    });

    it('con shift desde el boton se va al ultimo enfocable', async () => {
      const { fixture } = await enCompacto();
      const dentro = [
        ...(fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(
          '.panel a[href], .panel button, .panel select',
        ),
      ];

      tabular(byLabel('Cerrar el menú') as HTMLElement, true);

      expect(document.activeElement).toBe(dentro[dentro.length - 1]);
    });

    // En escritorio el tabulador tiene que salir de la cabecera con normalidad.
    it('no atrapa nada cuando no esta en compacto', async () => {
      const fixture = await render();
      const componente = fixture.componentInstance as unknown as {
        compacto: { set: (v: boolean) => void };
      };
      componente.compacto.set(false);
      fixture.detectChanges();

      const enlace = (fixture.nativeElement as HTMLElement).querySelector(
        '.enlace-nav',
      ) as HTMLElement;
      tabular(enlace);

      expect(document.activeElement).toBe(enlace);
    });
  });
});

/**
 * El umbral del modo compacto, probado por el cableado real y no poniendo la
 * senal a mano.
 *
 * <p>jsdom trae `matchMedia`, pero contesta siempre `matches: false`. Por eso
 * las pruebas de arriba tienen que empujar la senal privada, y por eso nadie
 * estaba comprobando ni la consulta ni el punto de quiebre: cambiar el umbral a
 * 440px dejaba la suite entera en verde y el menu desaparecia en la mitad de los
 * telefonos de Colombia.
 *
 * <p>Aqui se sustituye `matchMedia` por una que evalua la consulta de verdad
 * contra un ancho dado. Lo que se afirma es comportamiento observable: por
 * debajo de 640px Escape cierra el menu, porque el foco esta atrapado; a 640 no,
 * porque la navegacion es una fila mas de la cabecera.
 */
describe('SiteHeader, umbral del modo compacto', () => {
  let document: Document;
  let matchMediaOriginal: typeof window.matchMedia;

  const simularAncho = (inicial: number) => {
    let ancho = inicial;
    const oyentes = new Set<(evento: MediaQueryListEvent) => void>();
    const coincide = (consulta: string) => {
      const maximo = Number(/max-width:\s*([\d.]+)px/.exec(consulta)?.[1]);
      return Number.isFinite(maximo) && ancho <= maximo;
    };

    window.matchMedia = ((consulta: string) => ({
      media: consulta,
      get matches() {
        return coincide(consulta);
      },
      addEventListener: (_: string, oyente: (evento: MediaQueryListEvent) => void) =>
        oyentes.add(oyente),
      removeEventListener: (_: string, oyente: (evento: MediaQueryListEvent) => void) =>
        oyentes.delete(oyente),
      addListener: () => undefined,
      removeListener: () => undefined,
      onchange: null,
      dispatchEvent: () => true,
    })) as unknown as typeof window.matchMedia;

    return (nuevo: number) => {
      ancho = nuevo;
      for (const oyente of oyentes) {
        oyente({ matches: coincide('(max-width: 639.98px)') } as MediaQueryListEvent);
      }
    };
  };

  const byLabel = (label: string): HTMLElement | null =>
    document.querySelector(`[aria-label="${label}"]`);

  const abrirMenu = async () => {
    const fixture = TestBed.createComponent(SiteHeader);
    await fixture.whenStable();
    (byLabel('Abrir el menú') as HTMLButtonElement).click();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  };

  const pulsarEscape = () => {
    const cerrar = byLabel('Cerrar el menú') as HTMLButtonElement | null;
    (cerrar ?? (byLabel('Abrir el menú') as HTMLButtonElement)).dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }),
    );
  };

  beforeEach(() => {
    matchMediaOriginal = window.matchMedia;
    TestBed.configureTestingModule({
      providers: [
        provideRouter(
          PAGINAS_DE_CONTENIDO.map((pagina) => ({
            path: RUTAS_CONTENIDO[pagina].slice(1),
            component: Destino,
          })),
        ),
      ],
    });
    document = TestBed.inject(DOCUMENT);
    document.documentElement.setAttribute('data-tema', 'claro');
  });

  afterEach(() => {
    window.matchMedia = matchMediaOriginal;
  });

  it('a 639px es compacto: Escape cierra el menu', async () => {
    simularAncho(639);
    const fixture = await abrirMenu();

    expect(byLabel('Cerrar el menú')?.getAttribute('aria-expanded')).toBe('true');

    pulsarEscape();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byLabel('Abrir el menú')?.getAttribute('aria-expanded')).toBe('false');
  });

  it('a 640px ya no es compacto: no hay foco atrapado que Escape deshaga', async () => {
    simularAncho(640);
    const fixture = await abrirMenu();

    pulsarEscape();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byLabel('Cerrar el menú')?.getAttribute('aria-expanded')).toBe('true');
  });

  /**
   * Girar el telefono. Sin escuchar el cambio, quien abrio el menu en vertical
   * se queda con el foco atrapado en una navegacion que ya se ve entera.
   */
  it('al pasar a escritorio el menu se cierra solo', async () => {
    const cambiarA = simularAncho(639);
    const fixture = await abrirMenu();

    expect(byLabel('Cerrar el menú')).not.toBeNull();

    cambiarA(900);
    await fixture.whenStable();
    fixture.detectChanges();

    expect(byLabel('Abrir el menú')?.getAttribute('aria-expanded')).toBe('false');
  });
});
