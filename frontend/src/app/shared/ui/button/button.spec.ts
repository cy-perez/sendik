import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import { Button, type ButtonVariant } from './button';

@Component({
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button sendikButton [variant]="variante()" [loading]="cargando()" [disabled]="apagado()">
      Publicar
    </button>
  `,
})
class Anfitrion {
  readonly variante = signal<ButtonVariant>('primary');
  readonly cargando = signal(false);
  readonly apagado = signal(false);
}

@Component({
  imports: [Button],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: '<button sendikButton class="self-start">Publicar</button>',
})
class ConClasePropia {}

describe('Button', () => {
  let fixture: ReturnType<typeof TestBed.createComponent<Anfitrion>>;

  const boton = () => fixture.nativeElement.querySelector('button') as HTMLButtonElement;

  const render = async () => {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  };

  beforeEach(async () => {
    TestBed.configureTestingModule({ imports: [Anfitrion] });
    fixture = TestBed.createComponent(Anfitrion);
    await render();
  });

  /**
   * Es una directiva sobre el elemento nativo justamente para esto: el boton
   * sigue siendo un boton. Si algun dia se convirtiera en un componente que
   * envuelve a otro, esta prueba caeria.
   */
  it('deja intacto el elemento nativo y su texto', () => {
    expect(boton()).not.toBeNull();
    expect(boton().textContent?.trim()).toBe('Publicar');
    expect(boton().disabled).toBe(false);
  });

  it('el atributo disabled del elemento sigue funcionando', async () => {
    fixture.componentInstance.apagado.set(true);
    await render();

    expect(boton().disabled).toBe(true);
  });

  /**
   * El texto NO cambia al cargar. Cambiarlo mueve el foco de sitio para quien usa
   * lector de pantalla y hace que el boton cambie de tamano bajo el cursor.
   */
  it('en curso anuncia el estado sin tocar el texto', async () => {
    fixture.componentInstance.cargando.set(true);
    await render();

    expect(boton().getAttribute('aria-busy')).toBe('true');
    expect(boton().textContent?.trim()).toBe('Publicar');
  });

  it('en reposo no deja marcas de estado', () => {
    expect(boton().getAttribute('aria-busy')).toBeNull();

    // La directiva no toca aria-disabled: quien lo necesite lo pone, y dos
    // enlaces al mismo atributo se pisarian.
    expect(boton().getAttribute('aria-disabled')).toBeNull();
  });

  /**
   * El boton principal va en tinta, no en bronce. El acento aparece una vez por
   * pantalla y siempre en la insignia de vendedor verificado; un boton bronce es
   * la forma mas rapida de romper esa regla. Aqui se fija que ninguna variante
   * pinte con el acento.
   */
  it('ninguna variante usa el acento bronce', async () => {
    for (const variante of ['primary', 'secondary', 'text', 'ghost'] as const) {
      fixture.componentInstance.variante.set(variante);
      await render();

      expect(boton().getAttribute('class')).not.toContain('accent');
    }
  });

  /**
   * El control neutro del sistema: la caja de 44 con borde de control. Existe
   * porque estaba escrito a mano en tres sitios —cuenta, menu de sesion y los
   * botones de icono de la cabecera— y una regla copiada tres veces se corrige
   * en dos y se olvida en el tercero.
   */
  it('la variante neutra respeta el destino tactil', async () => {
    fixture.componentInstance.variante.set('ghost');
    await render();

    const clases = boton().getAttribute('class') ?? '';
    expect(clases).toContain('min-h-touch');
    expect(clases).toContain('min-w-touch');
    expect(clases).toContain('border-control-border');
  });

  /**
   * Una clase propia junto al atributo de la directiva. Es lo que hacen media
   * docena de pantallas —`class="self-start"`, `class="w-full"`— y si el enlace
   * de host la sustituyera, o ella al enlace, el boton se quedaria sin su
   * aspecto y solo se veria en un navegador.
   */
  it('convive con las clases propias del sitio donde se usa', async () => {
    const anfitrion = TestBed.createComponent(ConClasePropia);
    anfitrion.detectChanges();
    await anfitrion.whenStable();
    anfitrion.detectChanges();

    const propio = anfitrion.nativeElement.querySelector('button') as HTMLButtonElement;
    const clases = propio.getAttribute('class') ?? '';

    expect(clases, 'se perdio la clase del sitio donde se usa').toContain('self-start');
    expect(clases, 'se perdieron las clases de la directiva').toContain('bg-primary');
  });

  it('la variante principal pinta en tinta', () => {
    expect(boton().getAttribute('class')).toContain('bg-primary');
  });
});
