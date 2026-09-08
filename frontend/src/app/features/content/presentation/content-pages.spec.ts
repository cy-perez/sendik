import type { Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it } from 'vitest';

import { APP_CONFIG, type AppConfig } from '../../../core/config/app-config';
import { SessionStore } from '../../../core/session/session.store';
import { AboutPage } from './about-page';
import { ContactPage } from './contact-page';
import { FaqPage } from './faq-page';
import { HowItWorksPage } from './how-it-works-page';

/**
 * Lo transversal de las cuatro paginas informativas (HU-005, criterios 1 y 23).
 *
 * <p>Se prueban juntas lo que tienen en comun —un solo `h1`, sin saltos de
 * nivel— porque es una propiedad de la clase de pagina, no de cada una: escrita
 * cuatro veces se acaba corrigiendo en tres.
 */
/*
 * Vale para las suites de todo el archivo, y va arriba a proposito: al final se
 * leia como si las siete de mas abajo no tuvieran router.
 */
beforeEach(() => {
  // Contacto enlaza a /mi-cuenta y al documento de tratamiento de datos, y
  // RouterLink pide ActivatedRoute. Sin rutas de verdad: aqui no se navega, solo
  // se comprueba que el enlace se pinte y a donde apunta.
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
});

const EMPRESA_COMPLETA: AppConfig['company'] = {
  name: 'Sendik S.A.S.',
  taxId: '000000000-0',
  address: 'Medellin, Colombia',
  supportEmail: 'soporte@example.test',
};

const SIN_EMPRESA: AppConfig['company'] = {
  name: null,
  taxId: null,
  address: null,
  supportEmail: null,
};

/** Igual que el de test-providers.ts, pero componible sin tocar el inyector. */
const CONFIG_BASE: AppConfig = {
  apiBaseUrl: 'https://api.pruebas.sendik.co/api/v1',
  defaultLocale: 'es',
  availableLocales: ['es', 'en'],
  enableDevtools: false,
  sentryDsn: null,
  legalVersions: { terms: 'borrador-local', privacy: 'borrador-local', cookies: 'borrador-local' },
  company: EMPRESA_COMPLETA,
  business: {
    commissionRate: 0.05,
    claimWindowDays: 3,
    verificationReviewDays: 2,
    listingReviewDays: 2,
  },
};

describe('paginas informativas', () => {
  // Tipado como Type<unknown> y no inferido: con `as const` el arreglo se
  // estrecha a una union de las cuatro clases y createComponent deja de aceptar
  // el parametro, porque no puede saber cual de las cuatro le llega.
  const PAGINAS: readonly [string, Type<unknown>][] = [
    ['como funciona', HowItWorksPage],
    ['sobre Sendik', AboutPage],
    ['preguntas frecuentes', FaqPage],
    ['contacto', ContactPage],
  ];

  const encabezados = (raiz: HTMLElement) =>
    [...raiz.querySelectorAll('h1, h2, h3, h4, h5, h6')].map((e) => Number(e.tagName[1]));

  describe.each(PAGINAS)('%s', (_nombre, Pagina) => {
    it('tiene un solo h1', async () => {
      const fixture = TestBed.createComponent(Pagina);
      await fixture.whenStable();

      expect(fixture.nativeElement.querySelectorAll('h1')).toHaveLength(1);
    });

    /**
     * Un salto de nivel rompe la navegacion por encabezados, que es como recorre
     * la pagina quien usa lector de pantalla. Es requisito de aceptacion, no un
     * extra (frontend/CLAUDE.md).
     */
    it('no salta niveles de encabezado', async () => {
      const fixture = TestBed.createComponent(Pagina);
      await fixture.whenStable();

      const niveles = encabezados(fixture.nativeElement);
      expect(niveles[0]).toBe(1);

      for (const [anterior, actual] of niveles.slice(0, -1).map((n, i) => [n, niveles[i + 1]])) {
        expect(actual! - anterior!).toBeLessThanOrEqual(1);
      }
    });

    /**
     * Una clave que no existe se pinta tal cual, y con `prodMode` activo Transloco
     * ni siquiera avisa. Se buscan los prefijos reales y no un patron de
     * "algo.algo": el correo de soporte encaja en ese patron y daba un fallo
     * donde no lo habia.
     */
    it('no deja ninguna clave de traduccion sin resolver', async () => {
      const fixture = TestBed.createComponent(Pagina);
      await fixture.whenStable();

      expect(fixture.nativeElement.textContent).not.toMatch(
        /\b(howItWorks|about|faq|contact|layout|meta)\.[a-zA-Z]/,
      );
    });
  });
});

/**
 * Criterio 8: la cifra sale de la configuracion y llega al texto interpolada.
 *
 * <p>Esta prueba existe por un fallo concreto. Pasando `{ comision }` en vez de
 * `{ comision: comision() }` se le entrega a Transloco la **senal**, no su valor,
 * y la pagina servia `comisión del [Computed (comision): 5 %]`. Ni el tipado ni
 * las pruebas de encabezados lo veian: la clave se resolvia, el texto estaba, y
 * solo mirando el HTML servido se notaba.
 */
describe('cifras interpoladas en el texto', () => {
  /*
   * Las cifras de prueba NO son las de las reglas de negocio, y eso es
   * deliberado. Con 0.05 y 3 —los valores por omision de read-app-config.ts—
   * la prueba seguia verde si alguien escribia "la comisión del 5 %" literal en
   * es.json y borraba la interpolacion, que es exactamente lo que el criterio 8
   * prohibe. Con 8 % y 7 dias solo pasa si el texto viene de la configuracion.
   */
  const CIFRAS_AJENAS = {
    commissionRate: 0.08,
    claimWindowDays: 7,
    verificationReviewDays: 3,
    listingReviewDays: 4,
  };

  const textoDe = async (Pagina: Type<unknown>, business = CIFRAS_AJENAS) => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        { provide: APP_CONFIG, useValue: { ...CONFIG_BASE, business } },
      ],
    });
    const fixture = TestBed.createComponent(Pagina);
    await fixture.whenStable();
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  };

  it('como funciona escribe la comision como porcentaje, no como senal', async () => {
    const texto = await textoDe(HowItWorksPage);

    expect(texto).not.toContain('Computed');
    expect(texto).not.toContain('{{comision}}');
    expect(texto).toMatch(/comisión del 8\s?%/);
    // Y no la de las reglas: si apareciera, la cifra estaria en la plantilla.
    expect(texto).not.toMatch(/comisión del 5\s?%/);
  });

  it('como funciona escribe los dias de la ventana de reclamo', async () => {
    const texto = await textoDe(HowItWorksPage);

    expect(texto).not.toContain('{{dias}}');
    expect(texto).toContain('7 días hábiles');
    expect(texto).not.toContain('3 días hábiles');
  });

  it('preguntas frecuentes escribe la comision, no la senal', async () => {
    const texto = await textoDe(FaqPage);

    expect(texto).not.toContain('Computed');
    expect(texto).toMatch(/8\s?%/);
  });

  it('preguntas frecuentes escribe la ventana de reclamo desde la configuracion', async () => {
    const texto = await textoDe(FaqPage);

    expect(texto).not.toContain('{{dias}}');
    expect(texto).toContain('7 días hábiles');
    expect(texto).not.toContain('3 días hábiles');
  });
});

/**
 * En el sitio informativo la accion que importa es leer: solo /como-funciona
 * termina llamando a registrarse, y esa es su unica llamada a la accion.
 *
 * El boton va en tinta, no en bronce. El manual de Sendik reserva el bronce
 * para lo verificado y lo garantizado —la insignia de vendedor verificado— y
 * dice explicitamente que el boton principal va en tinta.
 */
describe('la llamada a registrarse', () => {
  const montar = async (Pagina: Type<unknown>) => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: APP_CONFIG, useValue: CONFIG_BASE }],
    });
    const fixture = TestBed.createComponent(Pagina);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it('como funciona cierra con un solo boton principal, y lleva al registro', async () => {
    const raiz = await montar(HowItWorksPage);
    const cta = raiz.querySelectorAll<HTMLAnchorElement>('.btn-primario');

    expect(cta).toHaveLength(1);
    expect(cta[0]?.getAttribute('href')).toBe('/registro');
  });

  /**
   * Las otras tres no llevan llamada a la accion. Que aparezca en dos sitios de
   * la misma pantalla es la erosion que el sistema visual quiere evitar, y
   * empieza siempre por copiar el cierre de una pagina a otra.
   */
  it.each([
    ['sobre Sendik', AboutPage],
    ['preguntas frecuentes', FaqPage],
    ['contacto', ContactPage],
  ] as [string, Type<unknown>][])('%s no lleva llamada a la accion', async (_nombre, Pagina) => {
    const raiz = await montar(Pagina);

    expect(raiz.querySelectorAll('.btn-primario')).toHaveLength(0);
  });
});

/**
 * Criterio 10: los datos de empresa salen de la configuracion y se omiten uno a
 * uno cuando faltan, igual que en el pie de HU-004. Nunca se escriben en la
 * plantilla.
 */
describe('AboutPage, datos de empresa', () => {
  /**
   * La configuracion se declara antes de tocar el inyector: leerla con
   * `TestBed.inject` para componerla instancia el modulo y ya no admite
   * sustituciones.
   */
  /*
   * Se afirma sobre el DOM renderizado y no sobre los miembros del componente.
   * Antes se leia `pagina['empresa'].taxId` sin montar la plantilla: borrar el
   * bloque entero del <address> dejaba las tres pruebas en verde y el criterio
   * 10 incumplido, y renombrar el campo las rompia las tres sin que hubiera
   * cambiado nada para quien visita la pagina.
   */
  const montar = async (company: AppConfig['company']) => {
    TestBed.configureTestingModule({
      providers: [{ provide: APP_CONFIG, useValue: { ...CONFIG_BASE, company } }],
    });
    const fixture = TestBed.createComponent(AboutPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  it('los pinta tomandolos de la configuracion', async () => {
    const raiz = await montar(EMPRESA_COMPLETA);
    const datos = raiz.querySelector('address');

    expect(datos?.textContent).toContain('Sendik S.A.S.');
    expect(datos?.textContent).toContain('000000000-0');
    expect(datos?.textContent).toContain('Medellin, Colombia');
    // La etiqueta del NIT es texto traducido, no una cadena de la plantilla.
    expect(datos?.textContent).toContain('NIT');
  });

  it('no pinta la seccion cuando no hay ni un dato', async () => {
    const raiz = await montar(SIN_EMPRESA);

    expect(raiz.querySelector('address')).toBeNull();
    expect(raiz.textContent).not.toContain('Quién responde');
  });

  /**
   * Criterio 10, la parte que no estaba probada: los datos se omiten **uno a
   * uno**. Un despliegue al que le falte la direccion sigue mostrando el NIT, y
   * no deja ni el hueco ni la etiqueta de los otros dos.
   */
  it('omite dato a dato y no deja huecos ni etiquetas sueltas', async () => {
    const raiz = await montar({ ...SIN_EMPRESA, taxId: '000000000-0' });
    const datos = raiz.querySelector('address');

    expect(datos).not.toBeNull();
    expect(datos?.textContent).toContain('000000000-0');
    expect(datos?.textContent).not.toContain('Sendik S.A.S.');
    expect(datos?.textContent).not.toContain('Medellin');
    expect(datos?.querySelectorAll('.dato')).toHaveLength(1);
  });
});

/**
 * Criterios 19 y 21. Sin correo de soporte no hay canal para ejercer los
 * derechos del titular de los datos, asi que la seccion no se pinta vacia: se
 * omite y el servidor ya lo registro como aviso al arrancar.
 */
describe('ContactPage', () => {
  const montar = async (company: AppConfig['company'], conSesion = false) => {
    TestBed.configureTestingModule({
      providers: [{ provide: APP_CONFIG, useValue: { ...CONFIG_BASE, company } }],
    });
    if (conSesion) {
      TestBed.inject(SessionStore).set({
        accessToken: 'un-token',
        user: { email: 'ana@correo.co', displayName: 'Ana', emailVerified: true, roles: [] },
      });
    }
    const fixture = TestBed.createComponent(ContactPage);
    await fixture.whenStable();
    return fixture.nativeElement as HTMLElement;
  };

  const enlaceA = (raiz: HTMLElement, destino: string) =>
    [...raiz.querySelectorAll<HTMLAnchorElement>('a')].find(
      (a) => a.getAttribute('href') === destino,
    );

  it('muestra el correo como enlace mailto y como texto legible', async () => {
    const raiz = await montar(EMPRESA_COMPLETA);

    // Las dos mitades del criterio 19: quien pueda pulsar y quien imprima.
    expect(enlaceA(raiz, 'mailto:soporte@example.test')).toBeDefined();
    expect(raiz.textContent).toContain('soporte@example.test');
  });

  it('sin correo configurado no pinta la seccion ni un enlace vacio', async () => {
    const raiz = await montar(SIN_EMPRESA);

    expect(raiz.querySelector('a[href^="mailto:"]')).toBeNull();
    expect(raiz.textContent).not.toContain('mailto');
    // Ni el encabezado suelto de una seccion sin contenido.
    expect(raiz.querySelector('#canal')).toBeNull();
  });

  /**
   * Sin canal no se afirma que haya canal. La frase "por este mismo canal" es la
   * que senala a la seccion del correo; los plazos de la Ley 1581 y el enlace a
   * la politica se enuncian igual, porque el titular tiene derecho a saberlos.
   */
  it('con canal, lo dice', async () => {
    const raiz = await montar(EMPRESA_COMPLETA);

    expect(raiz.textContent).toContain('Por este mismo canal');
  });

  it('sin canal no afirma que exista uno, pero sigue diciendo los plazos', async () => {
    const raiz = await montar(SIN_EMPRESA);

    expect(raiz.textContent).not.toContain('Por este mismo canal');
    expect(raiz.textContent).toContain('diez días hábiles');
    expect(raiz.querySelector('#tus-datos')).not.toBeNull();
  });

  it('sin sesion no ofrece el atajo a mi cuenta', async () => {
    const raiz = await montar(EMPRESA_COMPLETA);

    expect(enlaceA(raiz, '/mi-cuenta')).toBeUndefined();
  });

  it('con sesion si lo ofrece', async () => {
    const raiz = await montar(EMPRESA_COMPLETA, true);
    const atajo = enlaceA(raiz, '/mi-cuenta');

    expect(atajo).toBeDefined();
    expect(atajo?.textContent?.trim().length).toBeGreaterThan(0);
  });
});

/**
 * Criterio 13 y su caso borde: una respuesta concreta tiene que poder enviarse
 * por enlace, y un fragmento que no corresponde a nada no puede romper la
 * pagina.
 *
 * <p>El fragmento se inyecta como ActivatedRoute simulada en vez de navegar de
 * verdad: aqui se prueba lo que hace la pagina con el fragmento que reciba, no
 * que el router sepa leerlo, que es cosa suya y ya esta probada. El recorrido
 * completo, con navegador y teclado de verdad, esta en e2e/contenido.spec.ts.
 */
describe('FaqPage, fragmento de la direccion', () => {
  const conFragmento = async (fragmento: string | null) => {
    TestBed.configureTestingModule({
      providers: [
        { provide: APP_CONFIG, useValue: CONFIG_BASE },
        { provide: ActivatedRoute, useValue: { fragment: of(fragmento) } },
      ],
    });

    const fixture = TestBed.createComponent(FaqPage);
    await fixture.whenStable();

    return fixture.nativeElement as HTMLElement;
  };

  const preguntas = (raiz: HTMLElement) => [
    ...raiz.querySelectorAll<HTMLDetailsElement>('details'),
  ];

  const abiertas = (raiz: HTMLElement) => preguntas(raiz).filter((p) => p.open);

  it('despliega la pregunta que nombra el fragmento', async () => {
    const raiz = await conFragmento('claim');

    expect(abiertas(raiz).map((p) => p.id)).toEqual(['claim']);
  });

  /**
   * El foco va al `summary` y no al `details`: el `details` no es enfocable, y
   * dejar el foco donde estaba obliga a quien navega con teclado a recorrer la
   * pagina entera hasta la pregunta que el enlace ya le habia enseñado.
   */
  it('pone el foco en la pregunta, no solo la abre', async () => {
    const raiz = await conFragmento('claim');
    const summary = raiz.querySelector<HTMLElement>('details#claim summary');

    expect(document.activeElement).toBe(summary);
  });

  it('tambien encuentra las preguntas del vendedor', async () => {
    const raiz = await conFragmento('payout');

    expect(abiertas(raiz).map((p) => p.id)).toEqual(['payout']);
  });

  /**
   * Caso borde de la historia. Un fragmento inventado —o el de una pregunta que
   * se borro— no falla ni desplaza a ninguna parte: la pagina carga normal, con
   * todo plegado.
   */
  it('con un fragmento que no existe carga normal y no abre nada', async () => {
    const raiz = await conFragmento('esta-pregunta-no-existe');

    expect(abiertas(raiz)).toHaveLength(0);
    expect(raiz.querySelectorAll('h1')).toHaveLength(1);
  });

  /**
   * Un fragmento que ni siquiera es un identificador valido. Armar un selector
   * con el dentro haria lanzar a `querySelector` y se llevaria por delante el
   * renderizado de la pagina.
   */
  it('con un fragmento que no es un identificador valido tampoco falla', async () => {
    const raiz = await conFragmento('1) pregunta rara');

    expect(abiertas(raiz)).toHaveLength(0);
  });

  it('sin fragmento no abre ninguna', async () => {
    const raiz = await conFragmento(null);

    expect(abiertas(raiz)).toHaveLength(0);
  });

  /**
   * Criterio 18: el texto completo esta en el documento aunque las preguntas se
   * muestren plegadas. Es lo que da `details` nativo y lo que se perderia el dia
   * que alguien lo sustituya por un desplegable escrito a mano.
   */
  it('el texto de las respuestas esta en el DOM aunque esten plegadas', async () => {
    const raiz = await conFragmento(null);
    const respuestas = preguntas(raiz).map((p) => p.querySelector('p')?.textContent ?? '');

    expect(respuestas.length).toBeGreaterThan(0);
    for (const respuesta of respuestas) {
      expect(respuesta.trim().length).toBeGreaterThan(0);
    }
  });
});
