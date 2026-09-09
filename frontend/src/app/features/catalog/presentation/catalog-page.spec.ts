import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Meta } from '@angular/platform-browser';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { BehaviorSubject } from 'rxjs';

import {
  apiUrlInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import { CatalogPage } from './catalog-page';

/**
 * El catálogo público. HU-009, criterios 1 a 10.
 *
 * <p>Se prueba lo que ve quien entra: que la rejilla pinta lo que llega, que los tres
 * estados existen, que una categoría retirada del árbol se dice y no se pinta vacía, y que
 * «ver más» pide el tramo siguiente con el cursor que dio el anterior.
 *
 * <p><strong>Ninguna prueba pone sesión</strong>, y eso es parte de lo que se prueba: el
 * catálogo sirve igual a quien no tiene cuenta.
 */
describe('CatalogPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const CAMISAS = 'id-camisas';

  const arbol = [
    {
      id: 'id-tops',
      slug: 'tops',
      nameEs: 'Parte superior',
      nameEn: 'Tops',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: CAMISAS,
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'tops',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
  ];

  const publicacion = (id: string, titulo: string) => ({
    id,
    sellerId: 'vendedor',
    publishedAt: '2026-08-27T15:00:00Z',
    images: [
      { id: `${id}-0`, kind: 'SELLER_SHOT', position: 0, angleDegrees: 0, url: `/${id}.jpg` },
    ],
    product: {
      categoryId: CAMISAS,
      title: titulo,
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: {},
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: null,
      isSealed: null,
      warrantyMonths: null,
    },
  });

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;
  let consulta: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({}));
    consulta = new BehaviorSubject(convertToParamMap({}));

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ActivatedRoute,
          useValue: {
            paramMap: parametros.asObservable(),
            queryParamMap: consulta.asObservable(),
          },
        },
        provideHttpClient(
          withInterceptors([apiUrlInterceptor, languageInterceptor, errorInterceptor]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  const montar = async () => {
    const fixture = TestBed.createComponent(CatalogPage);
    const backend = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
    await bombear(fixture);
    return { fixture, backend };
  };

  /**
   * Deja que TanStack asiente sus consultas.
   *
   * <p>Con `Promise.resolve()` no basta: la consulta infinita encadena varias tareas de
   * macrocola antes de publicar el tramo, y la pantalla se quedaba en «cargando». Es el
   * mismo ayudante que usa `publish-page.spec.ts`.
   */
  const bombear = async (fixture: ComponentFixture<CatalogPage>) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const responder = async (
    fixture: ComponentFixture<CatalogPage>,
    backend: HttpTestingController,
    tramo: object,
  ) => {
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    backend.expectOne((llamada) => llamada.url === `${API}/listings`).flush(tramo);
    await bombear(fixture);
  };

  /** Criterio 1: lo publicado, en una rejilla. */
  it('pinta lo que llega del catálogo', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino'), publicacion('dos', 'Jean recto')],
      nextCursor: null,
      hasMore: false,
    });

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(fixture.nativeElement.textContent).toContain('Jean recto');
  });

  /** Criterio 5: sin nada publicado se dice, no se deja la página en blanco. */
  it('dice que no hay nada cuando el catálogo está vacío, criterio 5', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    expect(fixture.nativeElement.textContent).toContain('Todavía no hay nada publicado');
  });

  it('avisa y ofrece reintentar cuando el catálogo falla', async () => {
    const { fixture, backend } = await montar();

    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    backend
      .expectOne((llamada) => llamada.url === `${API}/listings`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await bombear(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * Criterio 9. Una categoría que no está en el árbol se dice, no se pinta vacía.
   *
   * <p>Un listado vacío se leería como «esta categoría existe y no tiene nada», que es
   * otra cosa y además mentira.
   */
  it('dice que una categoría retirada no existe, criterio 9', async () => {
    parametros.next(convertToParamMap({ familia: 'tops', categoria: 'inventada' }));

    const { fixture, backend } = await montar();
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Esta categoría no existe');
  });

  /** Criterio 8: la categoría de la dirección llega a la petición. */
  it('pide solo la categoría abierta', async () => {
    parametros.next(convertToParamMap({ familia: 'tops', categoria: 'camisas-y-blusas' }));

    const { fixture, backend } = await montar();
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    await bombear(fixture);

    const peticion = backend.expectOne((llamada) => llamada.url === `${API}/listings`);
    expect(peticion.request.params.get('category')).toBe(CAMISAS);
  });

  /** Criterio 3: «ver más» sigue por el cursor que dio el tramo anterior. */
  it('pide el tramo siguiente con el cursor del anterior', async () => {
    const { fixture, backend } = await montar();

    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino')],
      nextCursor: 'el-cursor',
      hasMore: true,
    });

    const boton = [...fixture.nativeElement.querySelectorAll('button')].find(
      (candidato: HTMLButtonElement) => candidato.textContent?.includes('Ver más'),
    ) as HTMLButtonElement;
    boton.click();
    await bombear(fixture);

    const siguiente = backend.expectOne((llamada) => llamada.url === `${API}/listings`);
    expect(siguiente.request.params.get('cursor')).toBe('el-cursor');
  });

  /** El árbol puede caerse sin llevarse el listado por delante. */
  it('sigue sirviendo el listado aunque el árbol de categorías falle', async () => {
    const { fixture, backend } = await montar();

    backend
      .expectOne((llamada) => llamada.url === `${API}/categories`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    backend
      .expectOne((llamada) => llamada.url === `${API}/listings`)
      .flush({
        items: [publicacion('uno', 'Camisa de lino')],
        nextCursor: null,
        hasMore: false,
      });
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Camisa de lino');
    expect(fixture.nativeElement.textContent).toContain('No pudimos cargar las categorías');
  });
  // ------------------------------------------------------- la búsqueda. HU-014

  /** Criterio 15: lo que dice la dirección es lo que se le pide al servidor. */
  it('lleva el texto y los filtros de la dirección a la petición', async () => {
    consulta.next(
      convertToParamMap({
        q: 'camisa de lino',
        color: ['BLUE', 'GREEN'],
        minPrice: '50000',
        sort: 'price,asc',
      }),
    );

    const { fixture, backend } = await montar();

    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    const pedida = backend.expectOne((llamada) => llamada.url === `${API}/listings`);

    expect(pedida.request.params.get('q')).toBe('camisa de lino');
    expect(pedida.request.params.getAll('color')).toEqual(['BLUE', 'GREEN']);
    expect(pedida.request.params.get('minPrice')).toBe('50000');
    expect(pedida.request.params.get('sort')).toBe('price,asc');

    pedida.flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);
  });

  /**
   * RN-083. Escribir en la caja suelta la categoría que se estaba navegando.
   *
   * <p>Quien busca «tenis» sin darse cuenta de que estaba dentro de Accesorios leería «no
   * hay» donde sí hay. Se comprueba sobre la navegación y no sobre la petición porque es
   * ahí donde ocurre: la dirección es la fuente de lo que se pide.
   */
  it('busca en todo el catálogo aunque se venga de una categoría, RN-083', async () => {
    parametros.next(convertToParamMap({ familia: 'tops', categoria: 'camisas-y-blusas' }));

    const { fixture, backend } = await montar();

    // Con una categoría en la dirección el listado espera al árbol: hasta que no llega, no
    // se sabe qué filtro va. Por eso no sirve aquí el ayudante que responde a las dos.
    backend.expectOne((llamada) => llamada.url === `${API}/categories`).flush(arbol);
    await bombear(fixture);
    backend
      .expectOne((llamada) => llamada.url === `${API}/listings`)
      .flush({ items: [], nextCursor: null, hasMore: false });
    await bombear(fixture);

    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    // Por rol y no por clase ni por identificador: lo que la prueba afirma es que hay una
    // caja de búsqueda que se puede enviar, no cómo se llama su CSS. Con el selector de
    // clase, renombrar el bloque ponía esto en rojo sin que nada cambiara para quien busca.
    const caja = fixture.nativeElement.querySelector('input[type="search"]') as HTMLInputElement;
    caja.value = 'tenis';
    caja.dispatchEvent(new Event('input'));
    (fixture.nativeElement.querySelector('[role="search"]') as HTMLFormElement).dispatchEvent(
      new Event('submit'),
    );

    expect(navegar).toHaveBeenCalledWith(['/catalogo'], { queryParams: { q: 'tenis' } });
  });

  /** RN-086: los filtros puestos se ven siempre, y cada uno se quita por su cuenta. */
  it('enseña una ficha por cada filtro puesto y la quita al pulsarla, RN-086', async () => {
    consulta.next(convertToParamMap({ color: ['BLUE', 'GREEN'] }));

    const { fixture, backend } = await montar();
    await responder(fixture, backend, {
      items: [publicacion('uno', 'Camisa de lino')],
      nextCursor: null,
      hasMore: false,
    });

    // Por el nombre accesible, que es lo que la persona oye y lo que RN-086 exige que
    // diga qué quita. La clase es implementación.
    const fichas = [
      ...fixture.nativeElement.querySelectorAll('button[aria-label^="Quitar el filtro"]'),
    ];
    expect(fichas).toHaveLength(2);

    const router = TestBed.inject(Router);
    const navegar = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    (fichas[0] as HTMLButtonElement).click();

    // Se va el azul y se queda el verde: quitar una ficha no vacía el filtro entero.
    expect(navegar).toHaveBeenCalledWith(
      [],
      expect.objectContaining({ queryParams: { color: ['GREEN'] } }),
    );
  });

  /**
   * RN-086. El vacío se dice y no se rellena, y la salida está a la vista.
   *
   * <p>Sin ella, un filtro activo e invisible se lee como «Sendik no tiene nada».
   */
  it('dice que no hay nada con esos filtros y ofrece quitarlos, RN-086', async () => {
    consulta.next(convertToParamMap({ color: 'BLUE' }));

    const { fixture, backend } = await montar();
    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    expect(fixture.nativeElement.textContent).toContain('No hay nada con esos filtros');
    expect(fixture.nativeElement.textContent).toContain('Quitar los filtros');
  });

  /** Y con texto se dice qué se buscó, que no es lo mismo que decir que no hay nada. */
  it('dice qué texto no encontró nada', async () => {
    consulta.next(convertToParamMap({ q: 'bicicleta' }));

    const { fixture, backend } = await montar();
    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    expect(fixture.nativeElement.textContent).toContain('bicicleta');
  });

  /**
   * Criterio 24: una búsqueda no se indexa.
   *
   * <p>Son combinaciones casi infinitas de filtros que producen páginas repetidas y casi
   * vacías, y eso perjudica al sitio entero. Las categorías y las fichas, que es de donde
   * vienen las visitas, siguen indexadas.
   */
  it('marca la búsqueda como no indexable y deja el catálogo indexado, criterio 24', async () => {
    consulta.next(convertToParamMap({ q: 'camisa' }));

    const { fixture, backend } = await montar();
    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    const meta = TestBed.inject(Meta);
    expect(meta.getTag("name='robots'")?.content).toBe('noindex');

    // Y al soltar la búsqueda vuelve a ser indexable.
    consulta.next(convertToParamMap({}));
    await bombear(fixture);

    expect(meta.getTag("name='robots'")).toBeNull();
  });

  /** El orden solo no es una búsqueda: el catálogo ordenado sigue siendo el catálogo. */
  it('no marca como no indexable el catálogo ordenado de otra manera', async () => {
    consulta.next(convertToParamMap({ sort: 'price,asc' }));

    const { fixture, backend } = await montar();
    await responder(fixture, backend, { items: [], nextCursor: null, hasMore: false });

    expect(TestBed.inject(Meta).getTag("name='robots'")).toBeNull();
  });
});
