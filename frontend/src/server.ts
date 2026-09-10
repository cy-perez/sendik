import {
  AngularNodeAppEngine,
  createNodeRequestHandler,
  isMainModule,
  writeResponseToNodeResponse,
} from '@angular/ssr/node';
import express from 'express';
import { join, sep } from 'node:path';

import {
  assertRenderingEnvironment,
  avisosDeConfiguracion,
  readAppConfig,
} from './app/core/config/read-app-config';

const browserDistFolder = join(import.meta.dirname, '../browser');

const app = express();
/**
 * Detras de Cloud Run hay un proxy, y hay que decirselo a @angular/ssr.
 *
 * <p>`sanitizeRequestHeaders` borra toda cabecera `x-forwarded-*` que no este
 * declarada aqui y, al hacerlo, marca la peticion para que **no se renderice en
 * el servidor**: devuelve la pagina de renderizado en cliente, con la raiz
 * vacia. No lanza y no registra mas que un aviso, asi que la respuesta sigue
 * siendo 200 y el fallo no se ve mirando el estado.
 *
 * <p>El sintoma es una pagina en blanco, y no por el cascaron en si: sin
 * renderizado en servidor no hay estado transferido, y `APP_CONFIG` llega por
 * ahi, asi que la aplicacion no arranca en el navegador (ADR-0021). Pasa en
 * Cloud Run y no en local porque solo alli hay un proxy delante.
 *
 * <p>Es tambien lo que ADR-0006 existe para impedir: el buscador y la vista
 * previa de WhatsApp reciben un documento vacio mientras el sitio parece
 * funcionar.
 *
 * <p>Se declaran las dos que manda Cloud Run y ninguna mas. Que son estas dos no
 * se supone: el aviso de arriba se emite por cada cabecera no confiable, asi que
 * al declarar solo `x-forwarded-for` quedo `x-forwarded-proto` sola en el
 * registro, y con las dos declaradas no queda ninguna.
 *
 * <p>**`x-forwarded-host` se queda fuera a proposito**, y es la que importa: es
 * la que elige el nombre de dominio con el que se renderiza la pagina, o sea la
 * falsificacion de peticiones del lado del servidor contra la que existe
 * `NG_ALLOWED_HOSTS` (ADR-0006). `-port` y `-prefix` tampoco llegan.
 *
 * <p>El precio de dejarlas fuera es que una peticion que traiga una de ellas
 * —solo puede ponerla quien la manda— se sirve sin renderizar. Afecta a esa
 * peticion y a nadie mas, y es preferible a confiar en el nombre de dominio.
 */
const angularApp = new AngularNodeAppEngine({
  trustProxyHeaders: ['x-forwarded-for', 'x-forwarded-proto'],
});

/** Un anio, en segundos: lo que se cachean las fuentes. */
const UN_ANIO = 31_536_000;

/** Cinco minutos: lo que se cachea un documento legal. */
const CINCO_MINUTOS = 300;

/**
 * Las cuatro cabeceras de seguridad del sitio, en toda respuesta (ADR-0019).
 *
 * <p>Vivian en `vercel.json` y estan aqui porque son del sitio y no del
 * hospedaje. Como configuracion de un proveedor habia que reimplementarlas en
 * cada mudanza, y ya hubo una: Vercel quedo descartado y el hospedaje esta por
 * definir. Como codigo viajan con la aplicacion, valen para cualquier proveedor y
 * las comprueba `e2e/cabeceras.spec.ts`.
 *
 * <p>Que sea lo primero que se registra es a proposito: asi las llevan tambien las
 * respuestas que no renderiza Angular —los archivos estaticos y el 500 de
 * configuracion incompleta de mas abajo—, que son justo las que se olvidan cuando
 * esto se pone al lado del renderizado.
 *
 * <p>`Strict-Transport-Security` se manda siempre, tambien en local sobre HTTP. El
 * navegador la ignora en una conexion sin cifrar, asi que no hace dano; ponerla
 * condicionada al esquema significaria que la cabecera que protege produccion es
 * la unica que nunca se prueba.
 */
app.use((_request, response, next) => {
  // Sin esto, un archivo subido por alguien se puede servir como el tipo que el
  // navegador adivine y no como el que declaramos.
  response.setHeader('X-Content-Type-Options', 'nosniff');
  // La direccion completa no sale de esta pagina, ni hacia terceros ni hacia nosotros
  // mismos. Con `strict-origin-when-cross-origin` solo se recortaba hacia fuera: dentro
  // del mismo origen viajaba entera, asi que cada recurso que carga
  // `/catalogo?q=camison+de+maternidad` mandaba ese texto en la cabecera `Referer`, y el
  // registro de peticiones de Cloud Run lo guarda junto a la IP. Es el historial de
  // busquedas que HU-014 decidio no tener, reconstruido por accidente
  // (docs/operacion/datos-personales.md). Nada del sitio lee el referente.
  response.setHeader('Referrer-Policy', 'strict-origin');
  // Nadie mete el sitio en un marco. Es la defensa contra el robo de clics sobre
  // los formularios de sesion.
  response.setHeader('X-Frame-Options', 'DENY');
  response.setHeader('Strict-Transport-Security', `max-age=${UN_ANIO}; includeSubDomains`);
  next();
});

/**
 * Accept-CH pide al navegador que, a partir de la siguiente peticion, incluya su
 * preferencia de esquema de color. Con ella el HTML sale ya en modo oscuro sin
 * esperar al JavaScript. En la primera visita no llega todavia y se sirve claro.
 *
 * Vary es obligatorio aqui: la misma direccion devuelve HTML distinto segun el
 * idioma, la cookie y esa preferencia. Sin Vary, una cache intermedia le daria a
 * un visitante la pagina renderizada para otro.
 */
app.use((_request, response, next) => {
  response.setHeader('Accept-CH', 'Sec-CH-Prefers-Color-Scheme');
  response.setHeader('Critical-CH', 'Sec-CH-Prefers-Color-Scheme');
  response.setHeader('Vary', 'Cookie, Accept-Language, Sec-CH-Prefers-Color-Scheme');
  next();
});

/**
 * Archivos estaticos de /browser. Los que construye Angular llevan hash en el
 * nombre, asi que se pueden cachear un ano sin riesgo.
 *
 * <p>Dos carpetas de `public/` no siguen esa regla y llevan politica propia
 * (ADR-0019), tambien heredada de `vercel.json`:
 *
 * <ul>
 *   <li><strong>`/fuentes/`</strong> se cachea un anio y ademas `immutable`: el
 *   nombre del archivo lleva la familia, asi que un cambio de tipografia es un
 *   archivo distinto y el navegador no tiene por que revalidar nunca. Con las
 *   variables ya no lleva el grosor —un solo archivo cubre el rango entero— pero
 *   el razonamiento no cambia: lo que identifica al archivo sigue siendo su
 *   nombre, y sustituir una familia significa otro nombre.
 *   <li><strong>`/legal/`</strong> se cachea cinco minutos y no un anio, que es lo
 *   que le daria la regla general por estar en la misma carpeta. Un cambio de
 *   version de los terminos o de la politica de datos tiene que llegar pronto:
 *   quedarse un anio en una cache seria servir un texto legal que ya no rige.
 * </ul>
 *
 * <p>La ruta que llega al callback es del sistema de archivos, asi que en Windows
 * viene con barras invertidas. Se normaliza con el separador de la plataforma
 * antes de mirarla: sin eso la comprobacion pasaria en la maquina de integracion,
 * que es Linux, y fallaria en la de desarrollo.
 */
app.use(
  express.static(browserDistFolder, {
    maxAge: '1y',
    index: false,
    redirect: false,
    setHeaders: (response, rutaDelArchivo) => {
      const ruta = rutaDelArchivo.split(sep).join('/');

      if (ruta.includes('/fuentes/')) {
        response.setHeader('Cache-Control', `public, max-age=${UN_ANIO}, immutable`);
      } else if (ruta.includes('/legal/')) {
        response.setHeader('Cache-Control', `public, max-age=${CINCO_MINUTOS}`);
      }
    },
  }),
);

/**
 * Comprobacion del entorno, una sola vez y sin lanzar.
 *
 * <p>No puede lanzar aqui: este modulo tambien se carga al construir, y ahi no
 * hay ni debe haber configuracion de ejecucion. Devuelve el motivo en vez de
 * tumbar el proceso, y quien lo consume decide que hacer con el.
 */
function errorDeEntorno(): string | null {
  try {
    readAppConfig(process.env);
    return null;
  } catch (fallo) {
    return fallo instanceof Error ? fallo.message : String(fallo);
  }
}

const entornoInvalido = errorDeEntorno();
let yaAvisado = false;

/**
 * Sin configuracion valida no se renderiza: se responde 500 diciendo que falta.
 *
 * <p>El arranque de mas abajo ya se cae por esto, pero **solo cuando este archivo
 * es el modulo principal**. Bajo `ng serve` no lo es: el CLI importa reqHandler y
 * ese bloque no se ejecuta nunca. La aplicacion arrancaba entonces con la
 * configuracion de relleno de readAppConfigForBootstrap, con apiBaseUrl vacia;
 * la primera peticion a la API lanzaba dentro del renderizado, la consulta no
 * llegaba a resolverse y la respuesta se quedaba colgada para siempre, sin una
 * sola linea en el registro.
 *
 * <p>Colgarse en silencio es la peor forma de fallar que hay: parece que compila,
 * el navegador gira y no hay nada que leer. La guarda existe para que el unico
 * entorno donde alguien programa no sea justo el que no valida nada.
 *
 * <p>Solo comprueba lo que impide renderizar, que es lo que exige readAppConfig.
 * NG_ALLOWED_HOSTS no entra: sin ella la pagina sale sin renderizar pero sale, y
 * el servidor de desarrollo del CLI funciona sin declararla. Exigirla aqui
 * romperia el caso que hoy va bien; el arranque real si la exige, que es donde
 * importa.
 */
app.use((_request, response, next) => {
  if (entornoInvalido === null) {
    next();
    return;
  }

  // Se avisa en la consola la primera vez: quien arranco el servidor mira ahi,
  // no el cuerpo de la respuesta.
  if (!yaAvisado) {
    yaAvisado = true;
    console.error(`[configuracion] ${entornoInvalido}`);
  }

  response
    .status(500)
    .type('text/plain; charset=utf-8')
    .send(
      `No se puede renderizar: la configuracion del servidor esta incompleta.\n\n${entornoInvalido}\n\n` +
        'En local suele ser que falta el archivo .env de la raiz del repositorio.\n' +
        'Se crea copiando .env.example y completando los valores (ver README.md).\n',
    );
});

/** El resto lo renderiza Angular. */
app.use((request, response, next) => {
  angularApp
    .handle(request)
    .then((rendered) => (rendered ? writeResponseToNodeResponse(rendered, response) : next()))
    .catch(next);
});

if (isMainModule(import.meta.url) || process.env['pm_id']) {
  // Arranque real: aqui se vuelve a validar, pero ahora si se lanza. La guarda de
  // mas arriba responde 500 a cada peticion, que es lo mejor que se puede hacer
  // cuando el proceso lo gobierna el CLI; cuando el proceso es nuestro, lo
  // correcto es no levantarlo. Es preferible no arrancar a descubrirlo con un
  // visitante dentro.
  const config = readAppConfig(process.env);
  assertRenderingEnvironment(process.env);

  // Lo que falta pero no impide servir. Se dice al arrancar: un despliegue a
  // medias no debe descubrirse mirando el pie de la pagina en produccion.
  for (const aviso of avisosDeConfiguracion(config)) {
    console.warn(`[configuracion] ${aviso}`);
  }

  const port = process.env['PORT'] ?? 4000;
  app.listen(port, (error) => {
    if (error) {
      throw error;
    }
    console.log(`Servidor de Sendik escuchando en http://localhost:${port}`);
  });
}

/** Lo usa el CLI durante el desarrollo y la construccion. */
export const reqHandler = createNodeRequestHandler(app);
