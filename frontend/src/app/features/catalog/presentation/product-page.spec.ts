import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Title } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { SessionStore } from '../../../core/session/session.store';
import type { Session } from '../../../core/session/session';
import { ProductPage } from './product-page';

/**
 * La ficha de producto. HU-009, criterios 11 a 17 y 21.
 *
 * <p>Ninguna prueba pone sesión: la ficha sirve igual a quien no tiene cuenta.
 */
describe('ProductPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = '01a04385-47b7-79c7-b3f2-62c03a8d4a88';
  const VENDEDOR = '01a04385-47b7-79c7-b3f2-62c03a8d4a99';

  const toma = (position: number) => ({
    id: `toma-${position}`,
    kind: 'SELLER_SHOT',
    position,
    angleDegrees: position * 45,
    url: `/toma-${position}.jpg`,
  });

  const publicacion = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: VENDEDOR,
    publishedAt: '2026-08-27T15:00:00Z',
    images: [toma(2), toma(0), toma(4)],
    product: {
      categoryId: 'camisas',
      title: 'Camisa de lino color hueso',
      description: 'Usada dos veces, sin manchas.',
      brand: 'Zara',
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52, SHOULDERS: 41 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
      ...cambios,
    },
  });

  const vendedor = (verified = true) => ({
    id: VENDEDOR,
    name: 'Ana María',
    avatarUrl: null,
    verified,
  });

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({ id: ID }));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          // Con `snapshot`, que el objeto real siempre trae: el control de favorito lee de
          // ahi el pase de la vuelta del ingreso (HU-011, ADR-0029).
          useValue: {
            paramMap: parametros.asObservable(),
            snapshot: { queryParamMap: convertToParamMap({}) },
          },
        },
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const bombear = async (fixture: ComponentFixture<ProductPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async () => {
    const fixture = TestBed.createComponent(ProductPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  const responder = async (
    fixture: ComponentFixture<ProductPage>,
    backend: HttpTestingController,
    cuerpo: object,
    quien: object | null = vendedor(),
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/listings/${ID}`).flush(cuerpo);
    await bombear(fixture);

    if (quien !== null) {
      backend.expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`).flush(quien);
      await bombear(fixture);
    }
  };

  /** Criterio 12: lo que el vendedor declaró. */
  it('muestra los datos declarados del producto', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    const texto = fixture.nativeElement.textContent;
    expect(texto).toContain('Camisa de lino color hueso');
    expect(texto).toContain('Usada dos veces, sin manchas.');
    expect(texto).toContain('Zara');
    expect(texto).toContain('Como nuevo');
    expect(texto).toContain('Beige');
    expect(texto).toContain('52');
  });

  /** Criterio 11: la frontal primero, aunque llegue en otro orden. */
  it('abre el carrusel por la toma frontal', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    const primera = fixture.nativeElement.querySelector('.ficha__toma img') as HTMLImageElement;
    expect(primera.getAttribute('src')).toBe('/toma-0.jpg');
  });

  /**
   * Criterio 14, RN-066. El rótulo va en el carrusel, no solo debajo.
   *
   * <p>Sin él, una foto del fabricante junto a fotos reales lleva a creer que el producto
   * se ve así, que es exactamente lo que la regla existe para impedir.
   */
  it('rotula la imagen de referencia dentro del carrusel, RN-066', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      ...publicacion({ isSealed: true }),
      images: [
        toma(0),
        { id: 'ref', kind: 'REFERENCE', position: 0, angleDegrees: null, url: '/caja.jpg' },
      ],
    });

    const rotulo = fixture.nativeElement.querySelector('.ficha__referencia');
    expect(rotulo).not.toBeNull();
    expect(rotulo.textContent).toContain('Imagen de referencia');
    expect(fixture.nativeElement.textContent).toContain('No la tomó el vendedor');
  });

  /** Sin imágenes de referencia no se rotula nada: es de tecnología sellada y nada más. */
  it('no rotula nada cuando todas las fotos son del vendedor', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(fixture.nativeElement.querySelector('.ficha__referencia')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Imagen de referencia');
  });

  /** Criterio 15 y 21: quién vende, con su sello, y el enlace a su perfil. */
  it('dice quién vende, muestra la insignia y enlaza a su perfil', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(fixture.nativeElement.textContent).toContain('Ana María');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).not.toBeNull();

    const enlace = fixture.nativeElement.querySelector(
      `a[href="/vendedor/${VENDEDOR}"]`,
    ) as HTMLAnchorElement;
    expect(enlace).not.toBeNull();
  });

  /** Sin sello no hay insignia: el acento bronce solo aparece donde algo lo respalda. */
  it('no muestra insignia si el vendedor no está verificado', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion(), vendedor(false));

    expect(fixture.nativeElement.textContent).toContain('Ana María');
    expect(fixture.nativeElement.querySelector('.insignia-verificado')).toBeNull();
  });

  /** Criterio 13: no publicada y no existe dicen lo mismo. */
  it('dice que ya no está disponible cuando la API responde 404, criterio 13', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('ya no está disponible');
  });

  /**
   * Criterio 16: el título describe este producto, no la plantilla.
   *
   * <p>Es lo que un buscador indexa y lo que se ve al compartir el enlace, así que un
   * título genérico deja a todas las fichas compitiendo por el mismo resultado.
   */
  it('pone el título de la publicación, criterio 16', async () => {
    const { fixture, backend } = await montar();
    await responder(fixture, backend, publicacion());

    expect(TestBed.inject(Title).getTitle()).toBe('Camisa de lino color hueso');
  });

  /**
   * La garantía del fabricante, RN-067. Escrita el 8 de septiembre de 2026.
   *
   * <p>Lo que estas cuatro protegen no es la maquetación: es el reparto de responsabilidad.
   * La ficha dice los meses **y quién responde por ellos**, y las dos cosas van juntas o no
   * va ninguna. Una ficha que anuncie doce meses sin decir de quién son deja al lector
   * suponiendo que responde Sendik, que es exactamente lo que RN-067 prohíbe.
   */
  describe('la garantía del fabricante, RN-067', () => {
    const conGarantia = (meses: number) => publicacion({ isSealed: true, warrantyMonths: meses });

    it('no dice nada cuando el vendedor no declaró ninguna', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, publicacion());

      expect(fixture.nativeElement.textContent).not.toContain('Garantía del fabricante');
    });

    /**
     * El cero es la otra forma de decir que no hay, y se trata igual que el nulo.
     *
     * <p>Nunca «sin garantía»: la garantía legal de la Ley 1480 rige igual sobre un producto
     * nuevo, así que anunciar su ausencia sería decir algo falso sobre los derechos de quien
     * compra.
     */
    it('tampoco con cero meses, y en ningún caso dice «sin garantía»', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, conGarantia(0));

      const texto = fixture.nativeElement.textContent;
      expect(texto).not.toContain('Garantía del fabricante');
      expect(texto).not.toContain('Sin garantía');
    });

    it('dice los meses y quién responde por ellos', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, conGarantia(12));

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('Garantía del fabricante');
      expect(texto).toContain('12 meses, declarados por el vendedor');
      expect(texto).toContain('Quien responde por esta garantía es el vendedor, no Sendik.');
    });

    /** «1 meses» se nota, y esta es la pantalla que más gente lee sin tener cuenta. */
    it('usa el singular con un solo mes', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, conGarantia(1));

      const texto = fixture.nativeElement.textContent;
      expect(texto).toContain('1 mes, declarado por el vendedor');
      expect(texto).not.toContain('1 meses');
    });

    /**
     * RN-057: la ficha no enuncia plazos ni condiciones por su cuenta, enlaza al documento
     * que los tiene. El enlace sale de `RUTAS_LEGALES`, así que esto también comprueba que
     * no se escribió a mano.
     */
    it('enlaza a los términos, que es donde vive la cláusula', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, conGarantia(12));

      const enlace = fixture.nativeElement.querySelector('.ficha__garantia a') as HTMLAnchorElement;
      expect(enlace.getAttribute('href')).toBe('/terminos');
      expect(enlace.textContent?.trim()).toBe('Leer los términos y condiciones');
    });

    /** La palabra que no puede aparecer aquí ni parecida (glosario, RN-067). */
    it('no roza la palabra Respaldo', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, conGarantia(12));

      expect(fixture.nativeElement.textContent).not.toMatch(/respaldo/i);
    });
  });

  /**
   * El visor giratorio de HU-003, montado sobre la ficha de HU-009.
   *
   * <p>Aquí se comprueba **cuándo se ofrece**, que es la regla; cómo gira lo prueba
   * `spin-viewer.spec.ts` sobre el componente, y la aritmética `frame-index.spec.ts`.
   */
  describe('el visor giratorio', () => {
    const ocho = () => [0, 1, 2, 3, 4, 5, 6, 7].map(toma);

    const referencia = (position: number) => ({
      id: `referencia-${position}`,
      kind: 'REFERENCE',
      position,
      angleDegrees: null,
      url: `/referencia-${position}.jpg`,
    });

    const visor = (fixture: ComponentFixture<ProductPage>) =>
      fixture.nativeElement.querySelector('sendik-spin-viewer');

    it('se ofrece con la secuencia completa de ocho tomas', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), images: ocho() });

      expect(visor(fixture)).not.toBeNull();
    });

    /**
     * Caso borde de la historia: «publicación antigua con menos de ocho tomas: el visor no
     * se ofrece y se muestra solo el carrusel».
     */
    it('no se ofrece con siete tomas, y el carrusel sigue estando', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), images: ocho().slice(0, 7) });

      expect(visor(fixture)).toBeNull();
      expect(fixture.nativeElement.querySelectorAll('.ficha__toma').length).toBe(7);
    });

    it('no se ofrece con las tres tomas sueltas de una publicación vieja', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, publicacion());

      expect(visor(fixture)).toBeNull();
    });

    /**
     * RN-066: una imagen de referencia es del fabricante y no del producto que se recibe,
     * así que no completa la secuencia ni entra en el giro.
     */
    it('no cuenta una imagen de referencia para completar las ocho', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, {
        ...publicacion(),
        images: [...ocho().slice(0, 7), referencia(7)],
      });

      expect(visor(fixture)).toBeNull();
    });
  });

  // --- HU-010: bajar una publicación visible ---------------------------------

  describe('bajar la publicación', () => {
    const SESION_DE_MODERADORA: Session = {
      accessToken: 'un-token',
      user: {
        email: 'moderadora@sendik.co',
        displayName: 'Quien Modera',
        emailVerified: true,
        roles: ['MODERATOR'],
      },
    };

    const SESION_CUALQUIERA: Session = {
      ...SESION_DE_MODERADORA,
      user: { ...SESION_DE_MODERADORA.user, roles: ['BUYER', 'SELLER'] },
    };

    const accion = (fixture: ComponentFixture<ProductPage>) =>
      Array.from(fixture.nativeElement.querySelectorAll('button')).find((boton) =>
        (boton as HTMLButtonElement).textContent?.includes('Bajar esta publicación'),
      ) as HTMLButtonElement | undefined;

    /**
     * Criterio 2, y es el que más importa de los tres.
     *
     * <p>Se comprueba sobre el HTML y no sobre una señal: la regla es que la acción **no
     * exista** para quien no modera, no que esté escondida con CSS. Sin sesión es además
     * el caso del renderizado en servidor, donde nunca hay ninguna.
     */
    it('no existe para quien no tiene sesión', async () => {
      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PUBLISHED' });

      expect(accion(fixture)).toBeUndefined();
      expect(fixture.nativeElement.textContent).not.toContain('Bajar esta publicación');
    });

    it('no existe para una cuenta sin el rol', async () => {
      TestBed.inject(SessionStore).set(SESION_CUALQUIERA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PUBLISHED' });

      expect(accion(fixture)).toBeUndefined();
    });

    it('se ofrece a quien modera sobre algo publicado', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PUBLISHED' });

      expect(accion(fixture)).toBeDefined();
    });

    /** Criterio 3: bajar es para lo que ya fue visible. Una pausada también lo fue. */
    it('se ofrece también sobre una pausada', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PAUSED' });

      expect(accion(fixture)).toBeDefined();
    });

    /**
     * Criterio 3: lo que espera revisión se decide en su bandeja, que es donde está el
     * motivo del rechazo. Un moderador puede llegar aquí por la dirección directa.
     */
    it('no se ofrece sobre lo que todavía no fue visible', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PENDING_REVIEW' });

      expect(accion(fixture)).toBeUndefined();
    });

    it('no se ofrece sobre algo ya archivado', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'ARCHIVED' });

      expect(accion(fixture)).toBeUndefined();
    });

    /**
     * La sesion llega DESPUES de que la pantalla se monte, que es lo que pasa de verdad.
     *
     * <p>En una carga de pagina el componente nace primero y la sesion despues, por la
     * cookie de refresco. Poner la sesion antes de montar —lo que hacen las demas pruebas
     * de este bloque— no prueba la carga real, y es la advertencia que frontend/CLAUDE.md
     * dejo escrita a raiz del perfil de /mi-cuenta.
     *
     * <p><strong>Aqui fallo de verdad.</strong> La ficha salia sin token, el servidor
     * respondia la forma publica —que no lleva estado— y la consulta se quedaba con ella
     * para siempre: un moderador que abria una ficha por su direccion no veia la accion, y
     * al recargar tampoco. Lo encontro el recorrido de extremo a extremo, no estas pruebas.
     * La arregla la clave de consulta, que ahora distingue desde que perspectiva se pidio.
     */
    it('ofrece la accion cuando la sesion se recupera despues de cargar', async () => {
      const { fixture, backend } = await montar();

      // Primera vuelta sin sesion: es la que el servidor responde en forma publica.
      backend.expectOne((llamada) => llamada.url === `${API}/listings/${ID}`).flush(publicacion());
      await bombear(fixture);
      backend
        .expectOne((llamada) => llamada.url === `${API}/sellers/${VENDEDOR}`)
        .flush(vendedor());
      await bombear(fixture);

      expect(accion(fixture)).toBeUndefined();

      // Y ahora llega la sesion, como llega en la vida real.
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);
      await bombear(fixture);

      backend
        .expectOne((llamada) => llamada.url === `${API}/listings/${ID}`)
        .flush({ ...publicacion(), status: 'PUBLISHED' });
      await bombear(fixture);

      expect(accion(fixture)).toBeDefined();
    });

    /** Criterios 4 y 8: el motivo viaja, y la nota vacía va en nulo y no en blanco. */
    it('manda el motivo elegido a la ruta de retiro', async () => {
      TestBed.inject(SessionStore).set(SESION_DE_MODERADORA);

      const { fixture, backend } = await montar();
      await responder(fixture, backend, { ...publicacion(), status: 'PUBLISHED' });

      accion(fixture)?.click();
      fixture.detectChanges();

      const motivo = fixture.nativeElement.querySelector('select') as HTMLSelectElement;
      motivo.value = 'PROHIBITED_ITEM';
      motivo.dispatchEvent(new Event('change'));
      fixture.detectChanges();

      const confirmar = Array.from(fixture.nativeElement.querySelectorAll('button')).find((boton) =>
        (boton as HTMLButtonElement).textContent?.includes('Confirmar'),
      ) as HTMLButtonElement | undefined;

      confirmar?.click();
      await bombear(fixture);

      const peticion = backend.expectOne(
        (llamada) => llamada.url === `${API}/listings/${ID}/removal`,
      );
      expect(peticion.request.method).toBe('POST');
      expect(peticion.request.body).toEqual({ reason: 'PROHIBITED_ITEM', note: null });
      peticion.flush({});
    });
  });
});
