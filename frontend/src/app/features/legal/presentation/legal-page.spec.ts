import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { LegalContent } from '../application/legal-content.resolver';
import { LegalPage } from './legal-page';

describe('LegalPage', () => {
  const render = async (contenido: LegalContent) => {
    const fixture = TestBed.createComponent(LegalPage);
    fixture.componentRef.setInput('contenido', contenido);
    await fixture.whenStable();
    return fixture;
  };

  const conTexto = (html: string | null, version = '2026-08-01'): LegalContent => ({
    documento: { id: 'privacy', version, locale: 'es' },
    html,
  });

  beforeEach(() => {
    TestBed.configureTestingModule({});
  });

  it('muestra el titulo del documento que le toca', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('h1')?.textContent).toContain(
      'Política de tratamiento de datos',
    );
  });

  /**
   * La version es lo unico que permite comprobar, meses despues, que el texto
   * que alguien acepto es este. Sin ella la evidencia guardada apunta a un
   * documento que no se puede identificar (docs/operacion/datos-personales.md).
   */
  it('muestra en pantalla la version vigente', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.textContent).toContain('2026-08-01');
  });

  it('inserta el texto del documento', async () => {
    const fixture = await render(conTexto('<h2>Primera parte</h2><p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('h2')?.textContent).toBe('Primera parte');
  });

  /**
   * El texto pasa por el desinfectante de Angular. Es un archivo nuestro, pero
   * la proteccion no se apaga por eso: bypassSecurityTrust no se usa aqui.
   */
  it('descarta cualquier guion que venga dentro del texto', async () => {
    const fixture = await render(conTexto('<p>Antes</p><script>alert(1)</script><p>Despues</p>'));

    expect(fixture.nativeElement.querySelector('script')).toBeNull();
    expect(fixture.nativeElement.textContent).toContain('Despues');
  });

  /**
   * Un borrador enlazado desde una casilla de consentimiento es peor que no
   * tener pagina: la persona creeria haber leido algo que no la obliga a nada.
   */
  it('avisa cuando la version es un borrador', async () => {
    const fixture = await render(conTexto('<p>Relleno.</p>', 'borrador-local'));

    const aviso = fixture.nativeElement.querySelector('aside') as HTMLElement;
    expect(aviso.textContent).toContain('no tiene valor legal');
    // La advertencia se distingue por su etiqueta, no solo por el color del
    // borde: un color no puede ser el unico portador de informacion.
    expect(aviso.querySelector('strong')?.textContent).toBe('Aviso:');
  });

  /**
   * Sin region viva. El aviso ya esta en la pagina al primer renderizado, y una
   * que nace con su contenido no se anuncia: quien llegue por la direccion
   * directa no oiria nada. Al navegar desde el pie si dispararia, interrumpiendo
   * el titulo de la pagina. Dos comportamientos distintos segun como se llegue.
   */
  it('no usa una region viva para un aviso que ya estaba', async () => {
    const fixture = await render(conTexto('<p>Relleno.</p>', 'borrador-local'));

    expect(fixture.nativeElement.querySelectorAll('[role="alert"]')).toHaveLength(0);
    expect(fixture.nativeElement.querySelectorAll('[role="status"]')).toHaveLength(0);
  });

  it('no avisa de borrador con una version publicada', async () => {
    const fixture = await render(conTexto('<p>El texto.</p>'));

    expect(fixture.nativeElement.querySelector('aside')).toBeNull();
  });

  // Falta el archivo de esa version: es un fallo de despliegue, no del visitante.
  // Se dice, en vez de dejar la pagina en blanco.
  it('explica que el documento no se pudo cargar', async () => {
    const fixture = await render(conTexto(null));

    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar este documento');
  });

  /**
   * El recorte de los documentos largos (HU pendiente de numerar, 5/9/2026).
   *
   * <p>Lo que se prueba aqui no es el aspecto sino **la garantia**: que el recorte
   * no pueda dejar un contrato a medias. De ahi que la prueba que mas importa sea
   * la negativa.
   */
  describe('recorte de los documentos largos', () => {
    const alto = (pixeles: number) =>
      vi.spyOn(HTMLElement.prototype, 'scrollHeight', 'get').mockReturnValue(pixeles);

    /**
     * jsdom no maqueta ni resuelve variables CSS heredadas, asi que declarar la
     * medida en el documento no llega al componente. Se simula el unico dato que
     * el componente pide, y el resto de `getComputedStyle` sigue siendo el real
     * para no romper lo que Angular consulte por su cuenta.
     */
    const conMedidaDeclarada = (valor: string) => {
      const original = window.getComputedStyle.bind(window);
      vi.spyOn(window, 'getComputedStyle').mockImplementation(((
        elemento: Element,
        pseudo?: string | null,
      ) => {
        const real = original(elemento, pseudo ?? undefined);
        return new Proxy(real, {
          get(objetivo, propiedad) {
            if (propiedad === 'getPropertyValue') {
              return (nombre: string) =>
                nombre === '--container-legal-clamp' ? valor : objetivo.getPropertyValue(nombre);
            }
            const leido = Reflect.get(objetivo, propiedad);
            return typeof leido === 'function' ? leido.bind(objetivo) : leido;
          },
        });
      }) as typeof window.getComputedStyle);
    };

    afterEach(() => {
      vi.restoreAllMocks();
    });

    it('ofrece ver mas cuando el documento pasa de la altura de recorte', async () => {
      conMedidaDeclarada('100px');
      alto(5000);

      const fixture = await render(conTexto('<p>Un documento muy largo.</p>'));

      const boton = fixture.nativeElement.querySelector('button.ver-mas');
      expect(boton?.textContent?.trim()).toBe('Ver más');
      expect(boton?.getAttribute('aria-expanded')).toBe('false');
      expect(fixture.nativeElement.querySelector('.texto-del-documento.recortado')).not.toBeNull();
    });

    it('no recorta un documento que cabe', async () => {
      conMedidaDeclarada('100px');
      alto(50);

      const fixture = await render(conTexto('<p>Corto.</p>'));

      expect(fixture.nativeElement.querySelector('button.ver-mas')).toBeNull();
      expect(fixture.nativeElement.querySelector('.texto-del-documento.recortado')).toBeNull();
    });

    /**
     * <strong>La que de verdad importa.</strong> Si la medida no esta declarada, la
     * unica salida segura es no recortar: un documento largo se lee entero, y un
     * documento recortado sin boton se queda sin la mitad de un contrato. Ante la
     * duda, entero.
     */
    it('no recorta si la medida del sistema no esta declarada', async () => {
      alto(5000);

      const fixture = await render(conTexto('<p>Un documento muy largo.</p>'));

      expect(fixture.nativeElement.querySelector('.texto-del-documento.recortado')).toBeNull();
      expect(fixture.nativeElement.querySelector('button.ver-mas')).toBeNull();
    });

    it('abre el documento entero al pulsar, y lo dice en aria-expanded', async () => {
      conMedidaDeclarada('100px');
      alto(5000);

      const fixture = await render(conTexto('<p>Un documento muy largo.</p>'));
      fixture.nativeElement.querySelector('button.ver-mas').click();
      await fixture.whenStable();

      const boton = fixture.nativeElement.querySelector('button.ver-mas');
      expect(boton?.textContent?.trim()).toBe('Ver menos');
      expect(boton?.getAttribute('aria-expanded')).toBe('true');
      expect(fixture.nativeElement.querySelector('.texto-del-documento.recortado')).toBeNull();
    });

    /**
     * El texto nunca sale del documento: recortar es visual. Quien lee con
     * asistencia tecnica tiene delante el contrato entero aunque este recortado,
     * que es lo que exige el deber de informacion.
     */
    it('deja el texto completo en el documento aunque este recortado', async () => {
      conMedidaDeclarada('100px');
      alto(5000);

      const fixture = await render(conTexto('<p>Principio.</p><p>Final del contrato.</p>'));

      expect(fixture.nativeElement.textContent).toContain('Final del contrato.');
    });
  });
});
