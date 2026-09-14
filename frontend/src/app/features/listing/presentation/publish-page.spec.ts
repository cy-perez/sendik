import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter, Router } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import {
  apiUrlInterceptor,
  authInterceptor,
  errorInterceptor,
  languageInterceptor,
} from '../../../core/http/interceptors';
import type { Session } from '../../../core/session/session';
import { APP_CONFIG } from '../../../core/config/app-config';
import { SessionStore } from '../../../core/session/session.store';
import { PhotoNormalizer } from '../../../shared/infrastructure/photo-normalizer';
import { ESPERA_DE_GUARDADO, PublishPage } from './publish-page';

/**
 * El formulario de publicar. HU-007.
 *
 * Lo que se comprueba es lo que la persona ve y puede hacer, no los métodos del
 * componente: qué se ofrece según el estado, qué se marca cuando el servidor dice que
 * falta algo, y que el envío no se ofrezca antes de tiempo.
 */
describe('PublishPage', () => {
  const API = 'https://api.pruebas.sendik.co/api/v1';
  const ID = 'af8b9a52-4a3f-4a52-9a1e-8d9a2f1c4b70';

  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana Maria', emailVerified: true, roles: [] },
  };

  /**
   * La configuración con otro plazo.
   *
   * <p>Se escribe entera y no se deriva de la de pruebas con un `TestBed.inject`: esa
   * inyección instancia el módulo antes de que `overrideProvider` llegue a correr.
   */
  const CONFIG_CON_PLAZO_DE_CINCO = {
    apiBaseUrl: API,
    defaultLocale: 'es',
    availableLocales: ['es', 'en'],
    enableDevtools: false,
    sentryDsn: null,
    legalVersions: {
      terms: 'borrador-local',
      privacy: 'borrador-local',
      cookies: 'borrador-local',
    },
    company: { name: null, taxId: null, address: null, supportEmail: null },
    business: {
      commissionRate: 0.05,
      claimWindowDays: 3,
      verificationReviewDays: 2,
      listingReviewDays: 5,
    },
    features: {
      catalog: true,
      checkout: true,
      publishing: true,
      sellerVerification: true,
      search: true,
    },
  };

  const ARBOL = [
    {
      id: 'familia-tops',
      slug: 'tops',
      nameEs: 'Parte superior',
      nameEn: 'Tops',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: true,
      children: [
        {
          id: 'hoja-camisas',
          slug: 'camisas-y-blusas',
          nameEs: 'Camisas y blusas',
          nameEn: 'Shirts and blouses',
          familySlug: 'tops',
          sizeSystems: ['ALPHA'],
          requiredMeasurements: ['CHEST', 'LENGTH'],
          allowsUsed: true,
          children: [],
        },
      ],
    },
    {
      id: 'familia-tech',
      slug: 'tech',
      nameEs: 'Tecnología',
      nameEn: 'Tech',
      familySlug: null,
      sizeSystems: [],
      requiredMeasurements: [],
      allowsUsed: false,
      children: [
        {
          id: 'hoja-celulares',
          slug: 'celulares-y-tabletas',
          nameEs: 'Celulares y tabletas',
          nameEn: 'Phones and tablets',
          familySlug: 'tech',
          sizeSystems: ['ONE_SIZE'],
          requiredMeasurements: ['HEIGHT', 'WIDTH', 'DEPTH'],
          allowsUsed: false,
          children: [],
        },
      ],
    },
  ];

  const borrador = (cambios: Record<string, unknown> = {}) => ({
    id: ID,
    sellerId: 'vendedor',
    status: 'DRAFT',
    product: {
      categoryId: 'hoja-camisas',
      title: 'Camisa de lino',
      description: 'Usada dos veces.',
      brand: null,
      condition: 'LIKE_NEW',
      size: { system: 'ALPHA', value: 'M' },
      measurements: { CHEST: 52 },
      color: 'BEIGE',
      price: { amount: 185000, currency: 'COP' },
      shipping: { weightGrams: 600, lengthCm: 30, widthCm: 20, heightCm: 10 },
      isSealed: null,
      warrantyMonths: null,
    },
    images: [],
    requiredShots: 8,
    requiresAttention: false,
    attentionReasons: [],
    rejectionReason: null,
    rejectionNote: null,
    publishedAt: null,
    createdAt: '2026-08-26T10:00:00Z',
    updatedAt: '2026-08-26T10:00:00Z',
    version: 1,
    ...cambios,
  });

  const conOchoTomas = () =>
    Array.from({ length: 8 }, (_, position) => ({
      id: `toma-${position}`,
      kind: 'SELLER_SHOT',
      position,
      angleDegrees: position * 45,
      url: `https://cdn.sendik.co/productos/${position}.jpg`,
    }));

  const asentar = async (fixture: {
    whenStable: () => Promise<unknown>;
    detectChanges: () => void;
  }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
      await fixture.whenStable();
    }
  };

  /**
   * Monta la página con —o sin— identificador en la ruta.
   *
   * <p>La ruta se suple con un {@code ActivatedRoute} de prueba en lugar de navegar de
   * verdad: lo único que la página lee de ella es el `paramMap`, y montar un enrutador
   * completo para eso metería en la prueba una pieza que no se está probando.
   *
   * <p>Va por un sujeto con valor inicial porque la página lee el parámetro de forma
   * síncrona al construirse: un observable sin valor la dejaría sin ruta.
   */
  /**
   * Deja pasar unos turnos y vuelve a pintar, sin esperar la estabilidad.
   *
   * <p>`whenStable` no sirve cuando queda alguna peticion sin responder: Angular la
   * cuenta como tarea pendiente y la espera no termina hasta que la prueba conteste.
   */
  const bombear = async (fixture: { detectChanges: () => void }) => {
    for (let vuelta = 0; vuelta < 5; vuelta++) {
      await new Promise((listo) => setTimeout(listo, 0));
      fixture.detectChanges();
    }
  };

  const montar = async (publicacion: object | null) => {
    if (publicacion !== null) {
      parametros.next(convertToParamMap({ id: ID }));
    }
    // La sesión se pone aquí y no en el beforeEach: alli instancia el modulo de pruebas
    // y ninguna prueba puede ya sustituir un proveedor.
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);

    backend
      .expectOne((peticion) => peticion.method === 'GET' && peticion.url === `${API}/categories`)
      .flush(ARBOL);

    if (publicacion !== null) {
      backend
        .expectOne(
          (peticion) => peticion.method === 'GET' && peticion.url === `${API}/listings/${ID}`,
        )
        .flush(publicacion);
    }
    await asentar(fixture);

    return { fixture, backend };
  };

  /**
   * Escribe en un control y deja pasar la espera del guardado automático.
   *
   * <p>Con relojes reales el debounce de 1,5 s es inalcanzable en una prueba: sin esto,
   * el guardado de avance que pide el criterio 5 no se puede comprobar.
   */
  const escribir = async (
    fixture: {
      nativeElement: HTMLElement;
      whenStable: () => Promise<unknown>;
      detectChanges: () => void;
    },
    selector: string,
    valor: string,
  ) => {
    const campo = fixture.nativeElement.querySelector(selector) as HTMLInputElement;
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));

    // Sin `whenStable`: Angular cuenta una peticion HTTP sin responder como tarea
    // pendiente, asi que esperar la estabilidad aqui se queda colgado hasta que la
    // prueba responda.
    await bombear(fixture);
  };

  const boton = (fixture: { nativeElement: HTMLElement }, texto: string) =>
    [...fixture.nativeElement.querySelectorAll('button')].find((candidato) =>
      candidato.textContent?.includes(texto),
    ) as HTMLButtonElement | undefined;

  let parametros: BehaviorSubject<ReturnType<typeof convertToParamMap>>;

  beforeEach(() => {
    parametros = new BehaviorSubject(convertToParamMap({}));

    TestBed.configureTestingModule({
      providers: [
        // Desde HU-003 toda toma pasa por el recorte a 3:4 antes de subir (criterio 8).
        // El normalizador vive en un worker con `OffscreenCanvas`, y ninguno de los dos
        // existe en jsdom: se dobla por uno que devuelve la imagen tal cual. Lo que aqui
        // se comprueba es el formulario, no el recorte, que prueban photo-crop.spec.ts
        // sobre la aritmetica y la suite de extremo a extremo sobre pixeles de verdad.
        {
          provide: PhotoNormalizer,
          useValue: { soportado: () => true, normalizar: async (imagen: Blob) => imagen },
        },
        provideRouter([{ path: 'publicar/:id', component: PublishPage }]),
        { provide: ActivatedRoute, useValue: { paramMap: parametros.asObservable() } },
        // Sin espera: el guardado automático se comprueba por lo que manda, no por
        // cuánto tarda en mandarlo.
        { provide: ESPERA_DE_GUARDADO, useValue: 0 },
        provideHttpClient(
          withInterceptors([
            apiUrlInterceptor,
            authInterceptor,
            languageInterceptor,
            errorInterceptor,
          ]),
        ),
        provideHttpClientTesting(),
      ],
    });
  });

  /** Sin identificador es el paso previo: elegir categoría y crear el borrador. */
  it('pide la categoría antes de crear el borrador', async () => {
    const { fixture } = await montar(null);

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('Publica tu producto');
    expect(fixture.nativeElement.querySelector('#categoria-nueva')).not.toBeNull();
    expect(boton(fixture, 'Empezar')?.disabled).toBe(true);
  });

  /** Criterio 19: en revisión no se edita, y lo que se ofrece es retirar. */
  it('bloquea el formulario y ofrece retirar mientras está en revisión', async () => {
    const { fixture } = await montar(
      borrador({ status: 'PENDING_REVIEW', images: conOchoTomas() }),
    );

    // Por la salida accesible y no por una clase de estilo: `matches(':disabled')` sí
    // considera el fieldset que envuelve al campo, mientras que `input.disabled` refleja
    // solo su propio atributo.
    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    expect(titulo.matches(':disabled')).toBe(true);
    expect(boton(fixture, 'Retirar de revisión')).toBeDefined();
    expect(boton(fixture, 'Enviar a revisión')).toBeUndefined();
  });

  /** Criterio 22: el vendedor ve el motivo y la nota, y puede retomar. */
  it('muestra el motivo del rechazo y ofrece corregir', async () => {
    const { fixture } = await montar(
      borrador({
        status: 'REJECTED',
        rejectionReason: 'PHOTOS_UNUSABLE',
        rejectionNote: 'Se ven movidas.',
        images: conOchoTomas(),
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('No pudimos publicarla');
    expect(texto).toContain('Las fotos no se pueden usar');
    expect(texto).toContain('Se ven movidas.');
    expect(boton(fixture, 'Corregir y volver a enviar')).toBeDefined();
  });

  /** RN-016 y RN-017: sin las ocho tomas no se ofrece enviar. */
  it('no deja enviar sin las tomas completas', async () => {
    const { fixture } = await montar(borrador());

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(true);
  });

  it('deja enviar con las ocho tomas puestas', async () => {
    const { fixture } = await montar(borrador({ images: conOchoTomas() }));

    expect(boton(fixture, 'Enviar a revisión')?.disabled).toBe(false);
  });

  /**
   * El plazo sale de configuración y nunca quemado en el texto.
   *
   * <p>Con el valor por omisión —que también es 2— la prueba daba verde aunque alguien
   * escribiera el número en el archivo de traducción. Con otro distinto, solo pasa si de
   * verdad viene de la configuración.
   */
  it('promete el plazo de revisión que dice la configuración', async () => {
    TestBed.overrideProvider(APP_CONFIG, { useValue: CONFIG_CON_PLAZO_DE_CINCO });

    const { fixture } = await montar(borrador());

    expect(fixture.nativeElement.textContent).toContain('5 días hábiles');
    expect(fixture.nativeElement.textContent).not.toContain('2 días hábiles');
  });

  /**
   * Criterio 6: el servidor manda los campos que faltan y la pantalla los nombra.
   *
   * Sin esto la persona lee «faltan datos» y tiene que adivinar cuáles.
   */
  it('nombra los campos que el servidor dice que faltan', async () => {
    const { fixture, backend } = await montar(borrador({ images: conOchoTomas() }));

    boton(fixture, 'Enviar a revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (peticion) =>
          peticion.method === 'POST' && peticion.url === `${API}/listings/${ID}/submission`,
      )
      .flush(
        {
          code: 'CATALOG_LISTING_INCOMPLETE',
          errors: [
            { field: 'title', code: 'VALIDATION_REQUIRED' },
            { field: 'price', code: 'VALIDATION_REQUIRED' },
          ],
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    // Se asierta sobre la lista, no sobre el texto de la página: «Título» y «Precio»
    // son las etiquetas del formulario y se pintan siempre, así que buscarlas en el
    // textContent daba verde aunque la lista no existiera.
    const faltantes = fixture.nativeElement.querySelector('.publicar__faltantes');
    expect(faltantes?.textContent).toContain('Título');
    expect(faltantes?.textContent).toContain('Precio');

    // Y la validación por campo, que es lo que pide la historia: el campo queda
    // marcado y su descripción apunta al mensaje que lo nombra.
    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    expect(titulo.getAttribute('aria-invalid')).toBe('true');
    expect(titulo.getAttribute('aria-describedby')).toContain('falta-title');
    expect(fixture.nativeElement.querySelector('#falta-title')).not.toBeNull();

    // El que no falta no se marca.
    const marca = fixture.nativeElement.querySelector('#marca') as HTMLInputElement;
    expect(marca.getAttribute('aria-invalid')).toBeNull();
  });

  /**
   * Las medidas llegan como `measurements.CHEST`.
   *
   * <p>Componer `listing.form.` con eso daba una clave que no existe, y en pantalla salía
   * el nombre de la clave en crudo en vez del nombre de la medida.
   */
  it('nombra también las medidas que faltan, criterio 10', async () => {
    const { fixture, backend } = await montar(borrador({ images: conOchoTomas() }));

    boton(fixture, 'Enviar a revisión')?.click();
    await fixture.whenStable();

    backend
      .expectOne(
        (llamada) =>
          llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/submission`,
      )
      .flush(
        {
          code: 'CATALOG_LISTING_INCOMPLETE',
          errors: [{ field: 'measurements.CHEST', code: 'VALIDATION_REQUIRED' }],
        },
        { status: 422, statusText: 'Unprocessable Content' },
      );
    await asentar(fixture);

    const faltantes = fixture.nativeElement.querySelector('.publicar__faltantes');
    expect(faltantes?.textContent).toContain('Pecho');
    expect(faltantes?.textContent).not.toContain('measurements');
  });

  /** RN-064: en tecnología solo se ofrece «nuevo», y aparecen sellado y garantía. */
  it('ofrece solo «nuevo», sellado y garantía en tecnología', async () => {
    const { fixture } = await montar(
      borrador({
        product: { ...borrador().product, categoryId: 'hoja-celulares', condition: 'NEW' },
      }),
    );

    const texto = fixture.nativeElement.textContent ?? '';
    expect(texto).toContain('En esta categoría solo se publica lo nuevo');
    expect(texto).toContain('Está sellado, sin abrir');
    expect(texto).toContain('Meses de garantía del fabricante');
    expect(texto).not.toContain('Con detalles');
  });

  /** Criterio 27: editar una viva la devuelve a moderación, y hay que avisarlo antes. */
  it('avisa de que editar una publicada la devuelve a revisión', async () => {
    const { fixture } = await montar(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));

    expect(fixture.nativeElement.textContent).toContain('vuelve a revisión');
  });

  // --- Criterio 4: crear el borrador y llegar a su formulario -------------

  it('crea el borrador con la categoría elegida y lleva a su formulario', async () => {
    const { fixture, backend } = await montar(null);
    const navegar = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const select = fixture.nativeElement.querySelector('#categoria-nueva') as HTMLSelectElement;
    select.value = 'hoja-camisas';
    select.dispatchEvent(new Event('change'));
    await asentar(fixture);

    expect(boton(fixture, 'Empezar')?.disabled).toBe(false);
    boton(fixture, 'Empezar')?.click();
    await fixture.whenStable();

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings`,
    );
    expect(peticion.request.body).toEqual({ categoryId: 'hoja-camisas' });

    peticion.flush(borrador());
    await asentar(fixture);

    expect(navegar).toHaveBeenCalledWith(['/publicar', ID]);
  });

  // --- Criterio 5: el avance no se pierde ---------------------------------

  /**
   * Salir a la mitad y volver retoma donde iba.
   *
   * <p>Se leen los valores de los controles y no el texto de la página: el texto no
   * incluye lo que hay dentro de un input, así que una prueba sobre textContent daría
   * verde aunque el formulario naciera vacío.
   */
  it('retoma el borrador con lo que ya estaba escrito, criterio 5', async () => {
    const { fixture } = await montar(borrador());

    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    const precio = fixture.nativeElement.querySelector('#precio') as HTMLInputElement;
    const categoria = fixture.nativeElement.querySelector('#categoria') as HTMLSelectElement;

    expect(titulo.value).toBe('Camisa de lino');
    expect(precio.value).toBe('185000');
    expect(categoria.value).toBe('hoja-camisas');
  });

  it('guarda solo lo que lleva cuando se deja de escribir, criterio 5', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#titulo', 'Camisa de lino con detalles');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );
    expect(peticion.request.body.title).toBe('Camisa de lino con detalles');

    peticion.flush(borrador());
    await asentar(fixture);
    expect(fixture.nativeElement.textContent).toContain('Guardado');
  });

  /**
   * La respuesta de subir una toma llega con el producto entero, y el producto entero
   * del servidor todavía no tiene lo que se acaba de escribir.
   *
   * <p>Volcándolo tal cual, el título tecleado se borraba solo y el `markAsPristine` de
   * después cancelaba el guardado que iba a salvarlo: quien escribía y arrastraba una
   * foto seguida perdía lo escrito, en silencio y sin poder recuperarlo. Es exactamente
   * lo contrario de lo que la pantalla promete.
   *
   * <p>Lo destapó `e2e-completo/moderacion-de-publicaciones.spec.ts`, que rellena el
   * formulario y sube las ocho tomas sin pausas: llegaba al final con el formulario en
   * blanco y el servidor respondiendo que faltaba todo.
   */
  it('no pierde lo escrito cuando llega la respuesta de una toma, criterio 5', async () => {
    const sinTitulo = { product: { ...borrador().product, title: null } };
    const { fixture, backend } = await montar(borrador(sinTitulo));

    await escribir(fixture, '#titulo', 'Camisa de lino color hueso');

    // El guardado sale, pero todavía no ha vuelto: es la ventana en la que se perdía.
    const guardado = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );

    const campo = fixture.nativeElement.querySelector('#toma-0') as HTMLInputElement;
    Object.defineProperty(campo, 'files', {
      value: [new File(['x'], 'toma.jpg', { type: 'image/jpeg' })],
    });
    campo.dispatchEvent(new Event('change'));
    await bombear(fixture);

    // **La subida espera su turno.** Subir reescribe el agregado igual que el guardado, y
    // las dos peticiones a la vez leen la misma versión: el bloqueo optimista del criterio
    // 34 tumba a una. Antes salían juntas y por ahí se colaba el 409.
    backend.expectNone(
      (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
    );

    // El guardado vuelve con lo que el servidor tenía, que todavía no es el título.
    guardado.flush(borrador(sinTitulo));
    await bombear(fixture);

    // Y ahora sí sale la subida, con el producto del servidor otra vez sin título.
    backend
      .expectOne(
        (llamada) => llamada.method === 'POST' && llamada.url === `${API}/listings/${ID}/images`,
      )
      .flush(borrador({ ...sinTitulo, images: [conOchoTomas()[0]] }));
    await bombear(fixture);

    const titulo = fixture.nativeElement.querySelector('#titulo') as HTMLInputElement;
    expect(titulo.value).toBe('Camisa de lino color hueso');

    // Y el guardado que iba en camino sigue llevándolo.
    expect(guardado.request.body.title).toBe('Camisa de lino color hueso');
  });

  /** El guardado prometía que no se pierde nada y fallaba sin decirlo. */
  it('avisa cuando el guardado automático falla', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#titulo', 'Otro título');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * Esta pantalla no pide las cifras del panel, y conviene que una prueba lo sostenga.
   *
   * <p>`ListingStore` es de raiz, asi que sus consultas nacen en cuanto alguien lo inyecta.
   * El resumen de HU-012 lo usa solo `/mis-publicaciones`; sin acotarlo, esta pantalla lo
   * pedia sin usarlo y **cada guardado suyo lo volvia a pedir**, porque la invalidacion de
   * la lista casa por prefijo y arrastra al resumen. Una peticion por guardado que nadie
   * mira, en la pantalla que mas guarda.
   */
  it('no pide las cifras del panel, que son de otra pantalla', async () => {
    const { fixture, backend } = await montar(borrador());

    backend.expectNone(
      (llamada) => llamada.url === `${API}/users/me/listings/summary`,
      'el resumen es de /mis-publicaciones',
    );

    // Y tampoco despues de guardar, que es cuando la invalidacion por prefijo lo arrastraria.
    await escribir(fixture, '#titulo', 'Camisa de lino');
    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush(borrador());
    await asentar(fixture);

    backend.expectNone(
      (llamada) => llamada.url === `${API}/users/me/listings/summary`,
      'guardar aqui no puede despertar una consulta de otra pantalla',
    );
  });

  // --- Que dos escrituras no se pisen (criterio 34) -----------------------

  /**
   * Escribe en una medida del producto, que no es un control del formulario.
   *
   * <p>Sin bombear después: las pruebas de aquí abajo necesitan escribir varias seguidas
   * dentro de la misma ventana del debounce, que es justo la tanda que antes se partía en
   * una petición por tecla.
   */
  const escribirMedida = (
    fixture: { nativeElement: HTMLElement },
    indice: number,
    valor: string,
  ) => {
    const grupo = [...fixture.nativeElement.querySelectorAll('fieldset')].find((candidato) =>
      candidato.querySelector('legend')?.textContent?.includes('Medidas'),
    );
    const campo = [...(grupo?.querySelectorAll('input') ?? [])][indice];
    if (campo === undefined) {
      throw new Error(`La categoría de la prueba no declara la medida ${indice}`);
    }
    campo.value = valor;
    campo.dispatchEvent(new Event('input'));
  };

  /**
   * Las medidas viven en una señal y no en el formulario, así que no pasaban por
   * `valueChanges`: llamaban al guardado en el acto, **una petición por tecla**. Llenar
   * las cuatro medidas que declara una categoría eran cuatro guardados pegados sobre el
   * mismo borrador, y el bloqueo optimista del criterio 34 tumbaba a alguno.
   */
  it('manda un solo guardado aunque se escriban varias medidas seguidas', async () => {
    const { fixture, backend } = await montar(borrador());

    // Las dos seguidas y **sin bombear entre medio**: es la tanda que antes salía partida
    // en dos peticiones.
    escribirMedida(fixture, 0, '52');
    escribirMedida(fixture, 1, '41');
    await bombear(fixture);

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );

    // Una sola, y llevándose las dos: coalescen, no se pierde ninguna.
    expect(Object.values(peticion.request.body.measurements)).toEqual(
      expect.arrayContaining([52, 41]),
    );

    peticion.flush(borrador());
    await asentar(fixture);
  });

  /**
   * Los controles solo vuelven a limpio cuando aterriza la respuesta, así que seguir
   * escribiendo mientras un guardado va en camino disparaba el siguiente encima. Los dos
   * leían la misma versión y el servidor tumbaba al segundo con un 409.
   */
  it('no manda un guardado encima de otro que sigue en vuelo', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#titulo', 'Camisa de lino');
    const primero = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );

    await escribir(fixture, '#descripcion', 'Usada dos veces, sin manchas.');
    backend.expectNone(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );

    primero.flush(borrador());
    await bombear(fixture);

    // Al volver el primero sale el segundo, con lo que se escribió después.
    const segundo = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`,
    );
    expect(segundo.request.body.description).toBe('Usada dos veces, sin manchas.');
    segundo.flush(borrador());
    await asentar(fixture);
  });

  /**
   * El precio y el envío tienen cada uno su ruta desde el criterio 28, y cuando se tocan
   * los dos en la misma tanda salían **a la vez**. Las dos reescriben la misma
   * publicación: era un 409 asegurado, y el que caía se perdía sin decir nada.
   */
  it('manda el precio y el envío uno después del otro, no a la vez', async () => {
    const publicada = borrador({ status: 'PUBLISHED', images: conOchoTomas() });
    const { fixture, backend } = await montar(publicada);

    const precio = fixture.nativeElement.querySelector('#precio') as HTMLInputElement;
    precio.value = '120000';
    precio.dispatchEvent(new Event('input'));
    const peso = fixture.nativeElement.querySelector(
      '[formcontrolname="weightGrams"]',
    ) as HTMLInputElement;
    peso.value = '900';
    peso.dispatchEvent(new Event('input'));
    await bombear(fixture);

    const primera = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/price`,
    );
    backend.expectNone(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/shipping`,
    );

    primera.flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await bombear(fixture);

    backend
      .expectOne(
        (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/shipping`,
      )
      .flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await asentar(fixture);
  });

  /**
   * La pantalla solo miraba el PATCH general, y el precio y el envío tienen la suya. Sobre
   * una publicación viva —que es justo cuando se usan esas dos rutas— un guardado perdido
   * no dejaba ni un mensaje: ni error, ni «Guardando», ni «Guardado».
   */
  it('avisa cuando falla el guardado del envío por su ruta propia', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '[formcontrolname="weightGrams"]', '900');

    backend
      .expectOne(
        (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/shipping`,
      )
      .flush({ code: 'CATALOG_LISTING_INVALID_STATE' }, { status: 409, statusText: 'Conflict' });
    await asentar(fixture);

    expect(
      fixture.nativeElement.querySelector('.publicar__acciones [role="alert"]'),
    ).not.toBeNull();
  });

  // --- Criterio 28: el precio y el envío no pasan por moderación ----------

  /**
   * La que faltaba, y la que importa.
   *
   * <p>Mandar el precio por el PATCH general devuelve a moderación una publicación viva
   * (RN-062): tocar el precio de algo publicado lo sacaba de circulación, que es lo
   * contrario de lo que promete el criterio 28.
   */
  it('cambia el precio de una publicada por su propia ruta, criterio 28', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '#precio', '120000');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/price`,
    );
    expect(peticion.request.body).toEqual({ price: { amount: 120000, currency: 'COP' } });
    peticion.flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await asentar(fixture);
  });

  it('cambia el envío de una publicada por su propia ruta, criterio 28', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '[formcontrolname="weightGrams"]', '900');

    const peticion = backend.expectOne(
      (llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}/shipping`,
    );
    expect(peticion.request.body.weightGrams).toBe(900);
    peticion.flush(borrador({ status: 'PUBLISHED', images: conOchoTomas() }));
    await asentar(fixture);
  });

  /** Criterio 27: el contenido sí vuelve a moderación, y va por el PATCH general. */
  it('manda el contenido de una publicada por el PATCH general, criterio 27', async () => {
    const { fixture, backend } = await montar(
      borrador({ status: 'PUBLISHED', images: conOchoTomas() }),
    );

    await escribir(fixture, '#titulo', 'Otro título');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush(borrador({ status: 'PENDING_REVIEW', images: conOchoTomas() }));
    await asentar(fixture);
  });

  /** Sobre un borrador da igual la ruta: no hay moderación de por medio. */
  it('manda todo junto en un borrador, aunque solo cambie el precio', async () => {
    const { fixture, backend } = await montar(borrador());

    await escribir(fixture, '#precio', '99000');

    backend
      .expectOne((llamada) => llamada.method === 'PATCH' && llamada.url === `${API}/listings/${ID}`)
      .flush(borrador());
    await asentar(fixture);
  });

  // --- Estados de carga y de error ---------------------------------------

  it('pinta el esqueleto mientras carga', async () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { paramMap: parametros.asObservable() },
    });
    parametros.next(convertToParamMap({ id: ID }));
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);
    const pendiente = backend.expectOne(
      (llamada) => llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}`,
    );
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('.esqueleto')).not.toBeNull();
    // El encabezado está en las cuatro ramas, también mientras carga.
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();

    pendiente.flush(borrador());
    await asentar(fixture);
  });

  /** Criterio 33: la publicación de otro responde 404, y eso es un error de pantalla. */
  it('muestra el error cuando la publicación no es suya', async () => {
    TestBed.overrideProvider(ActivatedRoute, {
      useValue: { paramMap: parametros.asObservable() },
    });
    parametros.next(convertToParamMap({ id: ID }));
    TestBed.inject(SessionStore).set(SESION);

    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    const backend = TestBed.inject(HttpTestingController);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/listings/${ID}`)
      .flush({ code: 'COMMON_NOT_FOUND' }, { status: 404, statusText: 'Not Found' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('h1')).not.toBeNull();
  });

  it('muestra el error cuando el árbol de categorías falla', async () => {
    TestBed.inject(SessionStore).set(SESION);
    const fixture = TestBed.createComponent(PublishPage);
    await fixture.whenStable();

    TestBed.inject(HttpTestingController)
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush({ code: 'COMMON_UNEXPECTED' }, { status: 500, statusText: 'Server Error' });
    await asentar(fixture);

    expect(fixture.nativeElement.querySelector('[role="alert"]')).not.toBeNull();
  });

  /**
   * La regresión que fija frontend/CLAUDE.md: en una carga de página el componente nace
   * antes de que la sesión llegue por la cookie de refresco. Aquí hay dos formas de que
   * la consulta nazca deshabilitada y no se reactive —la sesión y el identificador de la
   * ruta—, así que importa el doble.
   */
  it('pide la publicación aunque la sesión llegue después de crear el componente', async () => {
    parametros.next(convertToParamMap({ id: ID }));

    const fixture = TestBed.createComponent(PublishPage);
    // Turnos, no estabilidad: sin sesion hay ya una peticion en vuelo —el arbol es
    // publico— y esperar la estabilidad seria esperar a responderla uno mismo.
    await bombear(fixture);

    const backend = TestBed.inject(HttpTestingController);

    // El arbol es publico y no espera a nadie: llega antes que la sesion, igual que en
    // una carga de verdad.
    backend
      .expectOne((llamada) => llamada.method === 'GET' && llamada.url === `${API}/categories`)
      .flush(ARBOL);

    // Y ahora si, la sesion, por la cookie de refresco. Se deja pasar un turno en vez de
    // esperar la estabilidad: la peticion que la sesion desbloquea cuenta como tarea
    // pendiente, y esperarla aqui seria esperar a responderla uno mismo.
    TestBed.inject(SessionStore).set(SESION);
    await new Promise((listo) => setTimeout(listo, 0));
    fixture.detectChanges();

    // Se responde a todas las que haya: la consulta puede dispararse mas de una vez
    // segun en que orden lleguen la sesion y el identificador de la ruta, y lo que esta
    // prueba fija es que llegue a pedirse, no cuantas veces.
    const pendientes = backend.match((llamada) => llamada.url === `${API}/listings/${ID}`);
    expect(pendientes.length).toBeGreaterThan(0);
    pendientes.forEach((peticion) => peticion.flush(borrador()));
    await bombear(fixture);

    expect(fixture.nativeElement.textContent).toContain('Borrador');
  });

  /** Las categorías se ofrecen agrupadas por familia, con su nombre visible. */
  it('ofrece el árbol agrupado por familias', async () => {
    const { fixture } = await montar(null);

    const grupos = [...fixture.nativeElement.querySelectorAll('optgroup')] as HTMLElement[];
    expect(grupos.map((grupo) => grupo.getAttribute('label'))).toEqual([
      'Parte superior',
      'Tecnología',
    ]);
    expect(fixture.nativeElement.textContent).toContain('Camisas y blusas');
  });
});
