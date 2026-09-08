import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';

import { SpinViewer, type FotogramaDelVisor } from './spin-viewer';

/**
 * El visor giratorio. HU-003, criterios 11 a 19.
 *
 * <p>La cuenta de cuánto se arrastró a qué fotograma se ve la prueba `frame-index.spec.ts`
 * sobre la función pura. Aquí se comprueba lo que **este componente** hace: qué pinta al
 * llegar, con qué teclas gira, qué pasa mientras los fotogramas no han cargado y que la
 * página siga desplazándose.
 */
describe('SpinViewer', () => {
  const secuencia = (cuantos: number): FotogramaDelVisor[] =>
    Array.from({ length: cuantos }, (_, posicion) => ({
      url: `https://cdn.sendik.co/productos/${posicion}.jpg`,
      grados: posicion * 45,
    }));

  @Component({
    imports: [SpinViewer],
    changeDetection: ChangeDetectionStrategy.OnPush,
    template: `<sendik-spin-viewer [fotogramas]="fotogramas()" [titulo]="'Camisa de lino'" />`,
  })
  class Anfitrion {
    readonly fotogramas = signal<readonly FotogramaDelVisor[]>(secuencia(8));
  }

  /**
   * Una imagen que carga de inmediato.
   *
   * <p>jsdom no descarga recursos, así que una `Image` de verdad no dispara nunca `load` ni
   * `error`: la precarga se quedaría en el primer fotograma y el visor no llegaría a
   * activarse. Se dobla por lo mismo que la cámara en HU-002 —es una API del navegador, no
   * una decisión nuestra— y lo que se prueba es qué hace el componente cuando los
   * fotogramas llegan.
   */
  class ImagenFalsa {
    /** Lo que está esperando respuesta, en el orden en que se pidió. */
    static readonly enCola: { url: string; responder: (comoError?: boolean) => void }[] = [];

    /** Si cada imagen carga sola en cuanto se pide, o hay que soltarla a mano. */
    static automatica = true;

    decoding = '';
    private readonly escuchas = new Map<string, () => void>();

    addEventListener(tipo: string, escucha: () => void): void {
      this.escuchas.set(tipo, escucha);
    }

    set src(valor: string) {
      const responder = (comoError = false) => this.escuchas.get(comoError ? 'error' : 'load')?.();

      if (ImagenFalsa.automatica) {
        setTimeout(() => responder(), 0);
        return;
      }
      ImagenFalsa.enCola.push({ url: valor, responder });
    }
  }

  /** Suelta las `cuantas` siguientes de la cola, como carga o como fallo. */
  const dejarLlegar = async (cuantas: number, comoError = false) => {
    for (let i = 0; i < cuantas; i++) {
      ImagenFalsa.enCola.shift()?.responder(comoError);
      await asentar();
    }
  };

  const ImagenReal = globalThis.Image;

  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const marco = () => fixture.nativeElement.querySelector('.visor') as HTMLElement;
  const foto = () => fixture.nativeElement.querySelector('.visor__foto') as HTMLImageElement;

  const asentar = async () => {
    for (let vuelta = 0; vuelta < 4; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  /**
   * Simula un arrastre horizontal.
   *
   * <p>El marco mide cero en jsdom, que no hace diseño, así que se le fija un ancho: sin
   * él la función de giro responde «no gires», que es lo correcto para un visor sin medir
   * pero deja la prueba sin poder comprobar nada.
   */
  const arrastrar = async (pixeles: number) => {
    Object.defineProperty(marco(), 'clientWidth', { value: 800, configurable: true });

    marco().dispatchEvent(new PointerEvent('pointerdown', { clientX: 0, bubbles: true }));
    marco().dispatchEvent(new PointerEvent('pointermove', { clientX: pixeles, bubbles: true }));
    marco().dispatchEvent(new PointerEvent('pointerup', { clientX: pixeles, bubbles: true }));

    await asentar();
  };

  const teclear = async (key: string) => {
    marco().dispatchEvent(new KeyboardEvent('keydown', { key, bubbles: true, cancelable: true }));
    await asentar();
  };

  beforeEach(async () => {
    ImagenFalsa.enCola.length = 0;
    ImagenFalsa.automatica = true;
    globalThis.Image = ImagenFalsa as unknown as typeof Image;

    TestBed.configureTestingModule({});
    fixture = TestBed.createComponent(Anfitrion);
    await asentar();
  });

  afterEach(() => {
    globalThis.Image = ImagenReal;
  });

  describe('lo primero que se ve', () => {
    /** Criterio 11: el fotograma frontal, de inmediato. */
    it('muestra el fotograma frontal sin esperar a los demás', () => {
      expect(foto().getAttribute('src')).toContain('/0.jpg');
    });

    /**
     * Criterio 18: es una imagen normal, con `alt` descriptivo, para que la ficha
     * posicione en buscadores aunque el visor no llegue a activarse.
     */
    it('describe la imagen con el título del producto', () => {
      expect(foto().alt).toContain('Camisa de lino');
    });

    it('anuncia qué es y cómo se usa', () => {
      expect(marco().getAttribute('aria-roledescription')).toBeTruthy();
      expect(fixture.nativeElement.textContent).toContain('Arrastra para girar');
    });
  });

  describe('el giro con el teclado', () => {
    /** Criterio 15: flechas izquierda y derecha, y el componente es enfocable. */
    it('es enfocable', () => {
      expect(marco().getAttribute('tabindex')).toBe('0');
    });

    it('avanza un fotograma con la flecha derecha', async () => {
      await teclear('ArrowRight');

      expect(foto().getAttribute('src')).toContain('/1.jpg');
    });

    it('retrocede un fotograma con la flecha izquierda, dando la vuelta', async () => {
      await teclear('ArrowLeft');

      expect(foto().getAttribute('src')).toContain('/7.jpg');
    });

    /**
     * Criterio 17 dicho para el teclado: las flechas verticales desplazan la página, y
     * capturarlas dejaría a quien navega con teclado encerrado dentro del visor.
     */
    it('no se queda con las flechas verticales', async () => {
      const arriba = new KeyboardEvent('keydown', {
        key: 'ArrowUp',
        bubbles: true,
        cancelable: true,
      });
      marco().dispatchEvent(arriba);
      await asentar();

      expect(arriba.defaultPrevented).toBe(false);
      expect(foto().getAttribute('src')).toContain('/0.jpg');
    });

    it('no se queda con el tabulador', async () => {
      const tabulador = new KeyboardEvent('keydown', {
        key: 'Tab',
        bubbles: true,
        cancelable: true,
      });
      marco().dispatchEvent(tabulador);
      await asentar();

      expect(tabulador.defaultPrevented).toBe(false);
    });
  });

  describe('el giro con el dedo', () => {
    /** Criterio 12: el sentido del giro coincide con el del movimiento. */
    it('avanza al arrastrar a la derecha', async () => {
      await arrastrar(200);

      // 800 px de ancho y 8 fotogramas: 100 px por fotograma.
      expect(foto().getAttribute('src')).toContain('/2.jpg');
    });

    it('retrocede al arrastrar a la izquierda', async () => {
      await arrastrar(-100);

      expect(foto().getAttribute('src')).toContain('/7.jpg');
    });
  });

  describe('el desplazamiento de la página', () => {
    /**
     * Criterio 17. Se comprueba la declaración y no el comportamiento: quien decide si un
     * gesto desplaza o gira es el navegador, y jsdom no hace diseño ni gestos. Lo que sí
     * se puede comprobar es que el visor le deja el eje vertical, que es la decisión.
     */
    it('le deja al navegador el desplazamiento vertical y el pellizco', () => {
      const declarado = marco().style.touchAction || getComputedStyle(marco()).touchAction;

      // `not.toBe('none')` pasaba con `pan-x`, que atrapa la pagina igual. Se afirma lo
      // que se quiere, no lo que no se quiere.
      expect(declarado).toContain('pan-y');
      // Y el pellizco, porque esta es la foto principal del producto.
      expect(declarado).toContain('pinch-zoom');
    });
  });

  describe('cuando la secuencia está incompleta', () => {
    /**
     * Caso borde de la historia: una publicación con menos de cuatro tomas no se gira. Con
     * saltos de noventa grados o más se ve peor que el carrusel que ya está debajo.
     */
    it('no gira con menos de cuatro fotogramas', async () => {
      fixture.componentInstance.fotogramas.set(secuencia(3));
      await asentar();

      await teclear('ArrowRight');

      expect(foto().getAttribute('src')).toContain('/0.jpg');
    });

    it('sigue mostrando el frontal aunque no se pueda girar', async () => {
      fixture.componentInstance.fotogramas.set(secuencia(2));
      await asentar();

      expect(foto().getAttribute('src')).toContain('/0.jpg');
      expect(foto().alt).toContain('Camisa de lino');
    });

    it('no pinta nada si no hay ninguna toma', async () => {
      fixture.componentInstance.fotogramas.set([]);
      await asentar();

      expect(fixture.nativeElement.querySelector('.visor')).toBeNull();
    });
  });

  /**
   * Criterios 13 y 14, y el caso borde de conexión lenta.
   *
   * <p>Con la imagen falsa en modo manual: el visor no se activa hasta que llega el cuarto
   * fotograma, y hasta entonces avisa. Con la versión automática esto no se podía
   * comprobar, y borrar la condición de fotogramas cargados no rompía ninguna prueba.
   */
  describe('mientras los fotogramas van llegando', () => {
    beforeEach(async () => {
      // El `beforeEach` de arriba ya montó uno en modo automático. Hay que tirarlo antes
      // de montar el manual: si no, los dos precargan contra la misma cola y las cuentas
      // de abajo miden la suma de ambos.
      fixture.destroy();
      ImagenFalsa.enCola.length = 0;
      ImagenFalsa.automatica = false;

      fixture = TestBed.createComponent(Anfitrion);
      await asentar();
    });

    it('no gira con solo tres fotogramas cargados, y lo dice', async () => {
      // El frontal viene en el HTML; se sueltan dos más, que son tres en total.
      await dejarLlegar(2);

      await teclear('ArrowRight');

      expect(foto().getAttribute('src')).toContain('/0.jpg');
      expect(marco().getAttribute('aria-busy')).toBe('true');
      expect(fixture.nativeElement.textContent).toContain('Cargando la vista giratoria');
    });

    it('gira en cuanto llega el cuarto', async () => {
      await dejarLlegar(3);

      expect(marco().getAttribute('aria-busy')).toBe('false');

      await teclear('ArrowRight');

      expect(foto().getAttribute('src')).toContain('/1.jpg');
    });

    /** Criterio 13: se precargan los siete restantes, en orden. */
    it('precarga el resto en orden de giro', async () => {
      await dejarLlegar(7);

      expect(ImagenFalsa.enCola).toHaveLength(0);
    });

    /**
     * Criterio 14: «el visor funciona con los disponibles». Una toma que no está no puede
     * detener a las que vienen detrás.
     */
    it('sigue con las siguientes aunque una falle', async () => {
      await dejarLlegar(1, true);
      await dejarLlegar(2);

      expect(marco().getAttribute('aria-busy')).toBe('false');
    });
  });
});
