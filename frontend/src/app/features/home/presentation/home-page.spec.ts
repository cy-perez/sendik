import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { HomePage } from './home-page';

describe('HomePage', () => {
  const render = async () => {
    const fixture = TestBed.createComponent(HomePage);
    await fixture.whenStable();
    return fixture;
  };

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
  });

  /** Texto de cada elemento que case, en orden de documento. */
  const textos = (raiz: HTMLElement, selector: string): string[] =>
    Array.from(raiz.querySelectorAll(selector) as NodeListOf<HTMLElement>).map(
      (elemento) => elemento.textContent?.trim() ?? '',
    );

  /** El enlace por su nombre accesible, que es como lo encuentra quien lo usa. */
  const enlace = (raiz: HTMLElement, nombre: string): HTMLAnchorElement | undefined =>
    Array.from(raiz.querySelectorAll('a') as NodeListOf<HTMLAnchorElement>).find(
      (candidato) => candidato.textContent?.trim() === nombre,
    );

  /** La region cuyo encabezado dice `titulo`. */
  const region = (raiz: HTMLElement, titulo: string): HTMLElement => {
    const encabezado = Array.from(raiz.querySelectorAll('h2') as NodeListOf<HTMLElement>).find(
      (candidato) => candidato.textContent?.trim() === titulo,
    );

    const seccion = encabezado?.closest('section');
    if (!seccion) {
      throw new Error(`No hay ninguna region titulada "${titulo}"`);
    }
    return seccion;
  };

  /** Criterio 1: un solo h1 y es la propuesta de valor. */
  it('tiene un unico h1 con la propuesta de valor', async () => {
    const fixture = await render();

    expect(textos(fixture.nativeElement, 'h1')).toEqual(['Compra y vende de forma ágil y segura']);
  });

  // La otra mitad del criterio 1: el texto de apoyo explica el respaldo del pago.
  it('acompana el titular con el texto que explica el respaldo', async () => {
    const fixture = await render();
    const hero = fixture.nativeElement.querySelector('.hero') as HTMLElement;

    expect(hero.textContent).toContain('El pago queda retenido hasta que confirmas');
  });

  /**
   * RN-031: el recaudo lo hace la pasarela y Sendik no recibe ni custodia dinero
   * de terceros. La portada no puede decir lo contrario, ni con un verbo suelto.
   *
   * <p>El glosario lo deja escrito en su lista de palabras que no se usan:
   * escrow, custodia y fideicomiso describen figuras financieras que Sendik no
   * ejerce, y tienen lectura regulatoria en Colombia. "Guardamos tu pago" y
   * "nosotros retenemos el dinero" son la misma afirmacion en lenguaje llano, y
   * es lo que decia esta pantalla antes de la revision.
   */
  it('no dice que Sendik guarde ni custodie el dinero', async () => {
    const fixture = await render();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).not.toMatch(/guardamos|custodia|nosotros retenemos|en nuestra cuenta/i);
  });

  // Y el titular y su apoyo viven dentro de la franja, que es lo que rescata el
  // anillo de foco: sobre tinta, sin ella, seria invisible.
  it('pone el titular dentro de la franja oscura', async () => {
    const fixture = await render();
    const titulo = fixture.nativeElement.querySelector('h1') as HTMLElement;

    expect(titulo.closest('.franja-tinta')).not.toBeNull();
  });

  /**
   * Criterio 2. Una sola llamada a la accion por pantalla, siempre como relleno.
   * Se cuenta por clase porque el criterio nombra la clase: lo que se cuenta es
   * una decision de marca, no un rol.
   *
   * <p>El relleno es tinta, no bronce. El manual de Sendik reserva el bronce
   * para lo verificado y lo garantizado, y dentro de la franja el boton se
   * invierte a relleno claro con tinta encima, porque un boton en tinta sobre
   * fondo de tinta no se veria.
   *
   * <p>Aqui se cuenta dentro de la portada; que tampoco haya otro en la cabecera
   * ni en el pie se comprueba sobre la pagina completa en e2e/portada.spec.ts.
   */
  it('pinta exactamente una llamada a la accion', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelectorAll('.btn-primario')).toHaveLength(1);
  });

  /**
   * Criterio 3. Es un enlace y no un boton: lo que hace es navegar, asi que
   * funciona sin JavaScript y se puede abrir en otra pestana.
   *
   * <p>Lleva siempre a /registro, tambien con sesion abierta: el servidor no
   * puede saber si la hay, y un destino condicional dejaria el hero sin boton en
   * el HTML servido (criterio 15) o lo cambiaria al hidratar.
   */
  it('el boton principal es un enlace al registro', async () => {
    const fixture = await render();
    const cta = enlace(fixture.nativeElement, 'Crear cuenta');

    expect(cta?.tagName).toBe('A');
    expect(cta?.getAttribute('href')).toBe('/registro');
  });

  it('el boton principal es el que lleva el relleno', async () => {
    const fixture = await render();

    expect(enlace(fixture.nativeElement, 'Crear cuenta')?.classList).toContain('btn-primario');
  });

  /** Criterio 4: publicar es gratis y solo se cobra al vender. */
  it('dice que publicar es gratis y que solo se cobra al vender', async () => {
    const fixture = await render();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).toContain('Publicar es gratis');
    expect(texto).toContain('Solo cobramos cuando vendes');
  });

  /**
   * RN-026: el porcentaje no se escribe en la plantilla. Se mira la portada
   * entera y no solo la nota, porque la cifra puede colarse en cualquier texto.
   */
  it('no escribe el porcentaje de la comision en ninguna parte', async () => {
    const fixture = await render();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).not.toMatch(/%|\bcinco por ciento\b/i);
  });

  /** Criterio 5: publicar, vender y cobrar, en ese orden. */
  it('muestra los tres pasos en orden', async () => {
    const fixture = await render();
    const pasos = region(fixture.nativeElement, 'Cómo funciona');

    expect(textos(pasos, 'h3')).toEqual([
      'Publicas tu prenda',
      'Alguien la compra',
      'Cobras al confirmarse la entrega',
    ]);
  });

  // Lista ordenada y no una cualquiera: el orden es parte del mensaje y asi se
  // lo anuncia un lector de pantalla.
  it('presenta los pasos como lista ordenada', async () => {
    const fixture = await render();
    const pasos = region(fixture.nativeElement, 'Cómo funciona');

    expect(pasos.querySelector('ol')).not.toBeNull();
  });

  /**
   * Criterio 6. La ruta /como-funciona llega con HU-005; hasta entonces no se
   * pinta el enlace.
   *
   * <p>Se afirma solo eso, y no el conjunto entero de enlaces de la portada: con
   * la lista completa congelada, cualquier enlace legitimo que se agregue
   * despues rompe la prueba sin que nada este roto.
   *
   * <p>Ojo: hoy no hay mecanismo que consulte las rutas existentes, solo la
   * ausencia del enlace. El mecanismo entra con HU-005 y esta prueba se
   * convertira entonces en la que compruebe que si aparece.
   */
  it('no enlaza la pagina de como funciona mientras no exista', async () => {
    const fixture = await render();
    const destinos = Array.from(
      fixture.nativeElement.querySelectorAll('a[href]') as NodeListOf<HTMLAnchorElement>,
    ).map((candidato) => candidato.getAttribute('href'));

    expect(destinos).not.toContain('/como-funciona');
  });

  /**
   * Criterio 7, reinterpretado con la marca Sendik. La portada YA NO lleva regla
   * de corte propia.
   *
   * <p>El corte del isotipo es la firma de la marca y el manual es explicito:
   * «una sola vez por pieza». En el sitio esa vez es el borde superior del pie,
   * que sale en todas las pantallas —es donde la maqueta del kit la coloca, entre
   * el ultimo bloque y el pie—. Con una regla propia aqui, la portada mostraba
   * dos y dejaba de cumplir la regla justo en la pantalla mas visible.
   *
   * <p>Lo que el criterio queria —que los dos bloques se lean separados— lo da el
   * espaciado de .bloque. La separacion no se pierde; lo que se quita es
   * gastar la firma de la marca dos veces en la misma pantalla. Ver ADR-0022.
   */
  it('no repite la regla de corte: la unica de la pantalla es la del pie', async () => {
    const fixture = await render();

    expect(fixture.nativeElement.querySelectorAll('hr.regla-corte')).toHaveLength(0);
  });

  /** Criterio 8: retencion del pago, vendedores verificados y publicaciones moderadas. */
  it('muestra las tres tarjetas de confianza', async () => {
    const fixture = await render();
    const confianza = region(fixture.nativeElement, 'Por qué es seguro');

    expect(textos(confianza, 'h3')).toEqual([
      'El pago queda retenido',
      'Vendedores verificados',
      'Publicaciones revisadas',
    ]);
  });

  /**
   * Criterio 9. La maqueta original prometia "Devolucion en 3 dias" y esa
   * politica no existe: devoluciones es Fase 4 y no hay documento legal que la
   * respalde. Se prohiben tambien los tiempos de entrega, que tampoco estan
   * decididos. La prueba existe para que no vuelvan por la puerta de atras.
   */
  it('no promete devoluciones, reembolsos ni plazos', async () => {
    const fixture = await render();
    const texto = (fixture.nativeElement as HTMLElement).textContent ?? '';

    expect(texto).not.toMatch(/devoluci[oó]n|reembolso/i);
    expect(texto).not.toMatch(/\b\d+\s*(d[ií]as?|horas?|h)\b/i);
  });

  /** Criterio 17: sin saltos de nivel. Del h1 se pasa a h2 y de ahi a h3. */
  it('no salta ningun nivel de encabezado', async () => {
    const fixture = await render();
    const niveles = Array.from(
      fixture.nativeElement.querySelectorAll('h1, h2, h3, h4') as NodeListOf<HTMLElement>,
    ).map((titulo) => Number(titulo.tagName.slice(1)));

    // Empieza en 1 y nunca baja mas de un nivel de golpe. Subir varios de vuelta
    // si es valido: cerrar dos secciones anidadas y abrir otra de primer nivel.
    const saltos = niveles.slice(1).map((nivel, indice) => nivel - (niveles[indice] ?? nivel));

    expect(niveles.at(0)).toBe(1);
    expect(saltos.every((salto) => salto <= 1)).toBe(true);
  });

  // Cada bloque anuncia su nombre, para quien navega por regiones.
  it('da nombre accesible a las dos regiones de contenido', async () => {
    const fixture = await render();

    expect(region(fixture.nativeElement, 'Cómo funciona').getAttribute('aria-labelledby')).toBe(
      'pasos-titulo',
    );
    expect(region(fixture.nativeElement, 'Por qué es seguro').getAttribute('aria-labelledby')).toBe(
      'confianza-titulo',
    );
  });
});
