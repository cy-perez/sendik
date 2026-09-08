import { expect, test, type Page } from '@playwright/test';

/**
 * HU-005, las cuatro paginas informativas. Aqui esta lo que ninguna prueba de
 * componente puede demostrar: el HTML que sale del servidor, el ancho real del
 * documento, y el `details` abriendose con un teclado de verdad.
 *
 * El idioma se fija como en el resto de las pruebas de navegador: Chromium
 * arranca en ingles y estas leen textos en espanol. Que la negociacion de idioma
 * funciona lo demuestra ssr.spec.ts, que es donde corresponde.
 */
test.use({ locale: 'es-CO' });

/** Direccion y titular de cada pagina, que es lo que las distingue. */
const PAGINAS = [
  { ruta: '/como-funciona', h1: 'Cómo funciona Sendik' },
  { ruta: '/sobre-sendik', h1: 'Por qué existe Sendik' },
  { ruta: '/preguntas-frecuentes', h1: 'Preguntas frecuentes' },
  { ruta: '/contacto', h1: 'Escríbenos' },
] as const;

/** Si el documento es mas ancho que la ventana, hay barra horizontal. */
const desbordaHorizontalmente = (page: Page): Promise<boolean> =>
  page.locator('html').evaluate((raiz) => raiz.scrollWidth > raiz.clientWidth);

/**
 * El texto visible del HTML servido, sin scripts ni etiquetas. Se mira esto y no
 * el documento crudo porque ahi viajan tambien el estado transferido y el codigo
 * de Angular, donde cualquier palabra aparece por casualidad.
 */
const textoServido = (html: string): string =>
  html
    .slice(html.indexOf('<body'))
    .replace(/<script[\s\S]*?<\/script>/gi, ' ')
    .replace(/<style[\s\S]*?<\/style>/gi, ' ')
    .replace(/<[^>]+>/g, ' ');

test.describe('paginas informativas, HTML servido', () => {
  /**
   * Criterio 1. Cada una responde 200 con su propio `h1`, distinto del de las
   * otras tres. Una ruta que se olvide en app.routes.server.ts cae en el comodin
   * y llega con estado 404 aunque se pinte entera: el visitante no lo nota y el
   * buscador la descarta.
   */
  for (const { ruta, h1 } of PAGINAS) {
    test(`${ruta} responde 200 y trae su titular`, async ({ request }) => {
      const response = await request.get(ruta);

      expect(response.status()).toBe(200);
      expect(textoServido(await response.text())).toContain(h1);
    });
  }

  test('los cuatro titulares son distintos entre si', async ({ request }) => {
    const titulares = await Promise.all(
      PAGINAS.map(async ({ ruta }) => {
        const html = await (await request.get(ruta)).text();
        return (
          /<h1[^>]*>([\s\S]*?)<\/h1>/
            .exec(html)?.[1]
            ?.replace(/<[^>]+>/g, '')
            .trim() ?? ''
        );
      }),
    );

    expect(new Set(titulares).size).toBe(4);
  });

  /**
   * Criterio 2, que es la razon de ser de estas paginas: el contenido completo
   * ya viene dentro del documento, sin ejecutar JavaScript. Se comprueban textos
   * del final de cada pagina, no del principio: mover un bloque a `@defer`
   * vaciaria el HTML servido y una prueba que solo mire el titular seguiria en
   * verde.
   */
  test('como funciona llega entera, con los dos recorridos', async ({ request }) => {
    const texto = textoServido(await (await request.get('/como-funciona')).text());

    // Criterio 5: los dos recorridos rotulados, ninguno tras una interaccion.
    expect(texto).toContain('Si vas a comprar');
    expect(texto).toContain('Si vas a vender');
    // Criterio 6: pago retenido, confirmacion del comprador y que no cubre.
    expect(texto).toContain('El pago queda retenido');
    expect(texto).toContain('Qué no entra en el reporte');
    // Criterio 7: verificacion, publicar gratis y la comision a cargo del vendedor.
    expect(texto).toContain('Te verificas');
    expect(texto).toContain('Publicar no cuesta');
    // Lo ultimo de la pagina, que es lo primero que se perderia con un @defer.
    expect(texto).toContain('Por qué aquí y no en un grupo de Facebook');
  });

  test('sobre Sendik llega entera, con los datos de la empresa', async ({ request }) => {
    const texto = textoServido(await (await request.get('/sobre-sendik')).text());

    expect(texto).toContain('Quién responde');
    // Criterio 10: razon social, NIT y direccion salen de la configuracion.
    expect(texto).toContain('Sendik S.A.S.');
    expect(texto).toContain('000000000-0');
  });

  /**
   * Criterio 18: el texto completo de las respuestas esta en el documento aunque
   * las preguntas se sirvan plegadas. Es lo que da `details` nativo y lo que se
   * perderia el dia que alguien lo cambie por un desplegable escrito a mano.
   */
  test('preguntas frecuentes llega con las respuestas dentro, aunque plegadas', async ({
    request,
  }) => {
    const html = await (await request.get('/preguntas-frecuentes')).text();
    const texto = textoServido(html);

    expect(texto).toContain('Si vas a comprar');
    expect(texto).toContain('Si vas a vender');
    // Respuestas, no solo preguntas, y de los dos grupos.
    expect(texto).toContain(
      'El reintegro sale de ese dinero y no depende de que el vendedor colabore',
    );
    expect(texto).toContain('no a un saldo dentro de la plataforma');
    // Y plegadas: ningun `details` sale abierto de origen.
    expect(html).not.toMatch(/<details[^>]*\bopen\b/);
  });

  test('contacto llega entera, con el correo de soporte y los derechos', async ({ request }) => {
    const html = await (await request.get('/contacto')).text();
    const texto = textoServido(html);

    // Criterio 19: el correo como enlace mailto: y tambien como texto legible.
    expect(html).toContain('mailto:soporte@example.test');
    expect(texto).toContain('soporte@example.test');
    // Criterio 20: los plazos de la Ley 1581.
    expect(texto).toContain('Tus datos personales');
    expect(texto).toContain('diez días hábiles');
    expect(texto).toContain('quince');
  });

  /**
   * Criterio 3: cada una declara su propio titulo y su propia descripcion, y los
   * resuelve el servidor. Cuatro paginas con la misma descripcion son cuatro
   * paginas que el buscador trata como duplicadas.
   */
  test('cada pagina trae su propio titulo y su propia descripcion', async ({ request }) => {
    const metadatos = await Promise.all(
      PAGINAS.map(async ({ ruta }) => {
        const html = await (await request.get(ruta)).text();
        return {
          ruta,
          titulo: /<title>([\s\S]*?)<\/title>/.exec(html)?.[1] ?? '',
          descripcion: /<meta name="description" content="([^"]*)"/.exec(html)?.[1] ?? '',
        };
      }),
    );

    for (const { ruta, titulo, descripcion } of metadatos) {
      expect(titulo, ruta).not.toBe('');
      expect(titulo, ruta).not.toMatch(/^meta\./);
      expect(descripcion, ruta).not.toBe('');
      expect(descripcion, ruta).not.toMatch(/^meta\./);
    }

    expect(new Set(metadatos.map((m) => m.titulo)).size).toBe(4);
    expect(new Set(metadatos.map((m) => m.descripcion)).size).toBe(4);
  });

  /**
   * Criterio 4: cambia el texto y no la direccion. Es la mitad que la prueba de
   * paridad de traducciones no puede ver, porque compara arboles y no paginas.
   */
  test('las cuatro llegan traducidas al ingles en la misma direccion', async ({ request }) => {
    const enIngles = [
      { ruta: '/como-funciona', ingles: 'How Sendik works', espanol: 'Cómo funciona Sendik' },
      { ruta: '/sobre-sendik', ingles: 'Why Sendik exists', espanol: 'Por qué existe Sendik' },
      {
        ruta: '/preguntas-frecuentes',
        ingles: 'Frequently asked questions',
        espanol: 'Preguntas frecuentes',
      },
      { ruta: '/contacto', ingles: 'Write to us', espanol: 'Escríbenos' },
    ];

    for (const { ruta, ingles, espanol } of enIngles) {
      const response = await request.get(ruta, { headers: { 'Accept-Language': 'en' } });
      const texto = textoServido(await response.text());

      expect(new URL(response.url()).pathname, ruta).toBe(ruta);
      expect(texto, ruta).toContain(ingles);
      expect(texto, ruta).not.toContain(espanol);
    }
  });
});

test.describe('paginas informativas en el navegador', () => {
  /**
   * Criterio 25: ninguno de sus enlaces internos lleva a una ruta que no exista,
   * y ninguno lleva a catalogo, publicacion ni busqueda, que son de fases
   * posteriores. Se recorren las cuatro y se comprueban todos sus enlaces, los
   * de la cabecera y el pie incluidos: un enlace roto lo es igual en el pie.
   */
  for (const { ruta } of PAGINAS) {
    test(`ningun enlace de ${ruta} esta roto`, async ({ page, request }) => {
      await page.goto(ruta);
      const destinos = await page
        .locator('a[href^="/"]')
        .evaluateAll((enlaces) =>
          Array.from(new Set(enlaces.map((enlace) => enlace.getAttribute('href') ?? ''))),
        );

      expect(destinos.length).toBeGreaterThan(0);
      for (const destino of destinos) {
        expect(destino, `${ruta} enlaza a una fase posterior: ${destino}`).not.toMatch(
          /catalogo|productos|publicar|buscar|carrito/,
        );
        expect((await request.get(destino)).status(), `enlace roto en ${ruta}: ${destino}`).toBe(
          200,
        );
      }
    });
  }

  /**
   * El recorrido que hace quien duda: llega a la portada, se lee como funciona y
   * entonces se registra. Es el motivo por el que existe la historia, y lo unico
   * que demuestra que las tres piezas estan cableadas entre si.
   */
  test('de la portada a como funciona y de ahi al registro', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('navigation', { name: 'Principal' }).getByText('Cómo funciona').click();
    await expect(page).toHaveURL('/como-funciona');
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Cómo funciona Sendik');

    await page.getByRole('link', { name: 'Crear cuenta' }).click();
    await expect(page).toHaveURL('/registro');
  });

  /** Criterio 23: un solo h1 por pagina, tambien con la cabecera y el pie puestos. */
  for (const { ruta, h1 } of PAGINAS) {
    test(`${ruta} tiene un solo h1`, async ({ page }) => {
      await page.goto(ruta);

      await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
      await expect(page.getByRole('heading', { level: 1 })).toContainText(h1);
    });
  }

  /**
   * Criterio 24. 360px es el ancho real de buena parte de los telefonos en
   * Colombia y de ahi llega la mayoria de las visitas.
   */
  for (const { ruta } of PAGINAS) {
    test(`${ruta} no desborda a 360px`, async ({ page }) => {
      await page.setViewportSize({ width: 360, height: 740 });
      await page.goto(ruta);

      expect(await desbordaHorizontalmente(page)).toBe(false);
    });
  }

  /**
   * La pagina ocupa el carril de contenido de la rejilla de <main>, no un carril
   * de sangria.
   *
   * <p>Existe por un fallo que no se veia en ninguna prueba: sin `grid-column`
   * en el host, la rejilla auto-colocaba las cuatro paginas en la sangria y se
   * pintaban en una columna de 40px en escritorio. El texto estaba, los
   * encabezados estaban, no habia desplazamiento horizontal y las respuestas
   * llegaban en el HTML servido: todo verde, y el sitio ilegible. Se mide el
   * ancho, que es lo unico que lo delata.
   */
  for (const { ruta } of PAGINAS) {
    test(`${ruta} ocupa el carril de contenido`, async ({ page }) => {
      for (const ventana of [360, 1280]) {
        await page.setViewportSize({ width: ventana, height: 900 });
        await page.goto(ruta);
        await page.getByRole('heading', { level: 1 }).waitFor();

        // Se mide el ancho y, con el, el ancho de lectura que la propia pagina se
        // impone: `getComputedStyle(...).maxWidth` es `none` cuando no se impone
        // ninguno.
        const { ancho, lectura } = await page
          .locator('main > *:not(router-outlet)')
          .first()
          .evaluate((pagina) => {
            const maximo = Number.parseFloat(getComputedStyle(pagina).maxWidth);
            return {
              ancho: Math.round(pagina.getBoundingClientRect().width),
              lectura: Number.isFinite(maximo) ? Math.floor(maximo) : Number.POSITIVE_INFINITY,
            };
          });

        // El umbral sale de la sangria del sistema (16px en movil, 24px en
        // escritorio) y del ancho de lectura, y NO de un numero quemado. Estuvo
        // quemado en 700 y las cuatro informativas empezaron a fallar con la
        // tipografia nueva de marca: se topan justo en `--medida`, que son 70ch, y
        // con esa fuente 70ch resuelve exactamente a 700px. El umbral coincidia al
        // pixel con el ancho legitimo de la pagina. Leido del elemento, la prueba
        // sigue cazando lo que existe para cazar —el host en un carril de sangria,
        // que da una columna de 40px— sin depender de las metricas de una fuente.
        expect(ancho, `${ruta} a ${ventana}px`).toBeGreaterThanOrEqual(
          Math.min(ventana * 0.8, lectura),
        );
      }
    });
  }

  /**
   * Criterio 24, la otra mitad: 44px de destino tactil. Se mide sobre lo que se
   * pulsa de verdad en estas paginas —los `summary` de las preguntas y los
   * enlaces del contenido—, no sobre cualquier enlace del documento: los del pie
   * y los de dentro de un parrafo son texto corrido y tienen su propia regla.
   */
  test('los destinos tactiles de las preguntas miden 44px o mas', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/preguntas-frecuentes');
    // Se espera a que la pagina este montada antes de medir: `evaluateAll` no
    // reintenta, y al hidratar hay un instante con el contenido de la ruta
    // todavia sin pintar. Sin esta espera la prueba mide una lista vacia.
    await expect(page.locator('details').first()).toBeVisible();

    const alturas = await page
      .locator('details summary')
      .evaluateAll((titulares) =>
        titulares.map((titular) => Math.round(titular.getBoundingClientRect().height)),
      );

    expect(alturas.length).toBeGreaterThan(0);
    for (const alto of alturas) {
      expect(alto).toBeGreaterThanOrEqual(44);
    }
  });
});

/**
 * Caso borde de la historia: imprimir la pagina de contacto. Es la que alguien
 * guarda en papel, y ahi van el correo de soporte y los plazos de la Ley 1581.
 *
 * <p>Se prueba en modo oscuro porque es el unico caso en que falla: el papel es
 * blanco, los navegadores no imprimen el fondo, y el texto casi blanco del modo
 * oscuro desaparece. Se emula el medio `print` de verdad; comprobar la regla CSS
 * seria comprobar que la escribimos, no que sirve.
 */
/** Contraste de un `rgb(...)` calculado contra el blanco del papel (WCAG 2.1). */
const contrasteConPapel = (color: string): number => {
  const canales = [...color.matchAll(/\d+/g)].slice(0, 3).map((n) => Number(n[0]) / 255);
  const lineal = canales.map((c) => (c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4));
  const luminancia =
    0.2126 * (lineal[0] ?? 0) + 0.7152 * (lineal[1] ?? 0) + 0.0722 * (lineal[2] ?? 0);

  return 1.05 / (luminancia + 0.05);
};

test.describe('contacto impreso', () => {
  test('sobre papel blanco el correo y los plazos siguen legibles', async ({ page, context }) => {
    await context.addCookies([
      { name: 'sendik_theme', value: 'dark', url: 'http://localhost:4173' },
    ]);
    await page.goto('/contacto');
    await expect(page.locator('html')).toHaveAttribute('data-tema', 'oscuro');

    await page.emulateMedia({ media: 'print' });

    // Acotado al contenido de la ruta: el pie repite el correo de soporte y esa
    // es otra pieza, con su propia hoja.
    const pagina = page.locator('main');
    const colores = await pagina
      .getByRole('link', { name: 'soporte@example.test' })
      .evaluate((enlace) => getComputedStyle(enlace).color);
    const cuerpo = await pagina
      .getByText('diez días hábiles')
      .evaluate((parrafo) => getComputedStyle(parrafo).color);

    // Tinta sobre el blanco del papel. Se mide el contraste y no un valor
    // concreto: lo que el caso borde pide es que se lea, y clavar 'rgb(0, 0, 0)'
    // ataba la prueba al color exacto en vez de al resultado. En pantalla oscura
    // este mismo texto es casi blanco, que sobre papel es tinta invisible.
    expect(contrasteConPapel(colores)).toBeGreaterThanOrEqual(4.5);
    expect(contrasteConPapel(cuerpo)).toBeGreaterThanOrEqual(4.5);

    // Y el pie, que repite el correo y los tres documentos legales. Es la mitad
    // del caso borde que faltaba: su regla vive en marca.css, junto a
    // .franja-tinta, porque la franja la comparten el hero y el pie.
    const delPie = await page
      .locator('footer')
      .getByRole('link', { name: 'soporte@example.test' })
      .evaluate((enlace) => getComputedStyle(enlace).color);

    expect(contrasteConPapel(delPie)).toBeGreaterThanOrEqual(4.5);
  });
});

/**
 * Criterios 13 y 14. `details`/`summary` nativos dan el teclado y el anuncio de
 * estado gratis, pero eso solo lo demuestra un navegador de verdad: en una
 * prueba de componente se estaria comprobando lo que hace jsdom.
 */
test.describe('preguntas frecuentes, plegado y enlaces', () => {
  test('llegar con el fragmento deja la pregunta abierta y enfocada', async ({ page }) => {
    await page.goto('/preguntas-frecuentes#claim');

    const pregunta = page.locator('details#claim');
    await expect(pregunta).toHaveAttribute('open', '');
    await expect(pregunta.locator('summary')).toBeFocused();
    await expect(pregunta).toContainText('el pago sigue retenido');
  });

  /** Caso borde: un fragmento que no existe carga normal y no falla. */
  test('un fragmento inexistente no rompe la pagina ni abre nada', async ({ page }) => {
    await page.goto('/preguntas-frecuentes#esta-no-existe');

    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    await expect(page.locator('details[open]')).toHaveCount(0);
  });

  test('se abren y se cierran solo con el teclado', async ({ page }) => {
    await page.goto('/preguntas-frecuentes');
    const primera = page.locator('details').first();

    await primera.locator('summary').focus();
    await page.keyboard.press('Enter');
    await expect(primera).toHaveAttribute('open', '');

    await page.keyboard.press('Enter');
    await expect(primera).not.toHaveAttribute('open', '');
  });

  /**
   * Y con Espacio, que es la tecla que mas usa quien navega con teclado sobre un
   * control desplegable. `details` nativo responde a las dos; un `div` con clase
   * y un `keydown` de Enter escrito a mano, solo a una.
   */
  test('tambien se abren y se cierran con la barra espaciadora', async ({ page }) => {
    await page.goto('/preguntas-frecuentes');
    const primera = page.locator('details').first();

    await primera.locator('summary').focus();
    await page.keyboard.press('Space');
    await expect(primera).toHaveAttribute('open', '');

    await page.keyboard.press('Space');
    await expect(primera).not.toHaveAttribute('open', '');
  });

  /**
   * Criterio 12: la pregunta es un encabezado de verdad, dentro del `summary`.
   * Sin esto las diecisiete quedan fuera del indice de encabezados, que es como
   * recorre la pagina quien usa lector de pantalla.
   */
  test('cada pregunta es un encabezado', async ({ page }) => {
    await page.goto('/preguntas-frecuentes');

    const preguntas = page.locator('details summary h3');
    const detalles = page.locator('details');

    // `count()` es una foto sin reintento: se tomaba justo despues de navegar y
    // con la maquina cargada devolvia 0, con lo que la prueba caia diciendo que
    // no habia preguntas. Esperar a la primera es lo que da el momento en que la
    // pagina ya esta pintada; a partir de ahi contar es seguro, porque las
    // diecisiete llegan juntas en el HTML del servidor.
    await expect(detalles.first()).toBeAttached();
    const total = await detalles.count();

    expect(total).toBeGreaterThan(0);
    await expect(preguntas).toHaveCount(total);
  });

  /**
   * El estado se anuncia a un lector de pantalla sin escribir un solo `aria-*`:
   * el rol nativo de `summary` expone `expanded`. Si alguien lo sustituye por un
   * `div` con clase, esto se cae.
   */
  test('el estado de cada pregunta se expone a la accesibilidad', async ({ page }) => {
    await page.goto('/preguntas-frecuentes');
    const primera = page.locator('details').first();
    const titular = primera.locator('summary');

    expect(await titular.evaluate((elemento) => elemento.matches(':is(summary)'))).toBe(true);

    await titular.click();
    await expect(primera).toHaveAttribute('open', '');
  });
});

/**
 * La navegacion principal y su menu movil, que entran con esta historia porque
 * hasta ahora no habia ninguna pagina a la que llevar.
 */
test.describe('navegacion principal', () => {
  test('lleva a las cuatro paginas informativas y a ninguna otra', async ({ page }) => {
    await page.goto('/');
    const navegacion = page.getByRole('navigation', { name: 'Principal' });

    const destinos = await navegacion
      .locator('a')
      .evaluateAll((enlaces) => enlaces.map((enlace) => enlace.getAttribute('href') ?? ''));

    expect(destinos.sort()).toEqual(
      ['/como-funciona', '/contacto', '/preguntas-frecuentes', '/sobre-sendik'].sort(),
    );
  });

  /**
   * Por debajo de 640px la navegacion se sustituye por el menu. Se comprueba que
   * el boton aparezca y que los enlaces no se vean hasta abrirlo: un menu que
   * siempre se ve no es un menu.
   */
  test('a 360px se sustituye por el menu y se opera con teclado', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/');

    const boton = page.getByRole('button', { name: 'Abrir el menú' });
    const enlace = page
      .getByRole('navigation', { name: 'Principal' })
      .getByRole('link', { name: 'Cómo funciona' });

    await expect(boton).toBeVisible();
    await expect(enlace).toBeHidden();

    await boton.focus();
    await page.keyboard.press('Enter');
    await expect(enlace).toBeVisible();

    // Escape cierra y devuelve el foco al boton: sin lo segundo, el foco se
    // queda en algo que acaba de ocultarse y el navegador lo manda al principio.
    await page.keyboard.press('Escape');
    await expect(enlace).toBeHidden();
    await expect(page.getByRole('button', { name: 'Abrir el menú' })).toBeFocused();
  });

  test('desde el menu movil se llega a una pagina informativa', async ({ page }) => {
    await page.setViewportSize({ width: 360, height: 740 });
    await page.goto('/');

    await page.getByRole('button', { name: 'Abrir el menú' }).click();
    await page
      .getByRole('navigation', { name: 'Principal' })
      .getByRole('link', { name: 'Preguntas frecuentes' })
      .click();

    await expect(page).toHaveURL('/preguntas-frecuentes');
    // Navegar cierra el menu: dejarlo abierto sobre la pagina nueva no lo espera nadie.
    await expect(page.getByRole('button', { name: 'Abrir el menú' })).toBeVisible();
  });
});
