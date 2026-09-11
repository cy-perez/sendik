#!/usr/bin/env node
// PostToolUse sobre Edit/Write/MultiEdit. El archivo ya se escribio; el hook lo
// relee y devuelve al agente la lista de convenciones incumplidas para que las
// corrija en el mismo turno. Solo entran reglas verificables con certeza: una
// regla con falsos positivos se vuelve ruido y se termina ignorando.

import { readFileSync, existsSync } from 'node:fs';

const entrada = JSON.parse(leerStdin() || '{}');
const ruta = (entrada.tool_input?.file_path ?? '').replace(/\\/g, '/');
if (!ruta || !existsSync(ruta)) process.exit(0);

let texto = '';
try { texto = readFileSync(ruta, 'utf8'); } catch { process.exit(0); }

const hallazgos = [];
const es = (...ext) => ext.some((e) => ruta.endsWith(e));
const enFrontend = ruta.includes('frontend/');
const enBackend = ruta.includes('backend/');

// Los documentos legales de `public/legal/` no son plantillas de Angular ni
// hojas de estilo: son contenido, y el unico .html del frontend que se lee como
// prosa. Ninguna regla de este archivo les aplica, y aplicarselas da falsos
// positivos garantizados: la palabra inglesa "document." dispara la regla de API
// del navegador, y su texto visible dispara la de Transloco —que ademas seria
// imposible de cumplir, porque su idioma va en el nombre del archivo,
// `terms.<version>.es.html` y `.en.html`, que es el mecanismo que define
// docs/operacion/textos-legales.md—. Sin esta excepcion la regla rechaza los
// seis archivos que ya estaban en el repositorio, que es exactamente el falso
// positivo que la cabecera de este archivo dice no admitir.
const esDocumentoLegal = /frontend\/public\/legal\//.test(ruta);

// Las pruebas de extremo a extremo son codigo de Node que conduce un navegador,
// no codigo que se renderice en el servidor. Lo que va dentro de `page.evaluate`
// se ejecuta en la pagina, asi que ahi `document` es lo correcto y no hay
// plataforma que comprobar ni afterNextRender donde aislarlo: pedirselo dejaria
// dos salidas, las dos malas —escribir la prueba peor, o acostumbrarse a ignorar
// el hook—, que es el falso positivo que la cabecera de este archivo dice no
// admitir. La excepcion es solo para la regla de API del navegador; el resto de
// las reglas se les siguen aplicando, aunque ninguna pueda dispararse en un
// archivo de pruebas.
const esPruebaDeNavegador =
  /frontend\/(e2e|e2e-completo|e2e-comun)\//.test(ruta) ||
  // Y las unitarias, por lo mismo. Una prueba de Vitest se ejecuta en un entorno de
  // navegador y nunca se renderiza en el servidor, asi que ahi `localStorage` es lo
  // correcto: es justo lo que hay que tocar para comprobar que un almacen guarda lo que
  // dice guardar, o para dejarlo sucio a proposito y ver que no revienta al leerlo. Sin
  // esto la regla rechaza `favorite-intent.spec.ts`, que ya esta en el repositorio.
  /\.spec\.ts$/.test(ruta);

// --- Estilos: ningun valor visual suelto ---------------------------------
const esHojaDelSistema = /(src\/styles|docs\/ui)\/(tokens|tipografia|marca|fuentes)\.css$/.test(ruta);

if (enFrontend && es('.css', '.scss', '.html') && !esHojaDelSistema && !esDocumentoLegal) {
  // Fuera de la revision: los comentarios, y el unico sitio donde un HEX no
  // puede ir por variable. `<meta name="theme-color">` lo lee el navegador para
  // tenir su propia interfaz antes de aplicar ninguna hoja de estilos, asi que
  // no acepta var(): un color de marca literal ahi no es una fuga del sistema,
  // es la unica forma que existe. El mismo valor esta en site.webmanifest, y
  // los dos salen de docs/marca/dist/web/head-snippet.html.
  const sinComentarios = texto
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/<!--[\s\S]*?-->/g, '')
    .replace(/<meta\s+name="theme-color"[^>]*>/g, '');
  if (/#[0-9a-fA-F]{3,8}\b/.test(sinComentarios)) {
    hallazgos.push('Color literal en HEX. Usa una variable de tokens.css, por ejemplo var(--color-superficie). Si el color no existe, el sistema esta incompleto: se nombra en marca.css y se documenta.');
  }
  if (/\b(rgb|rgba|hsl)\(/.test(sinComentarios)) {
    hallazgos.push('Color literal en rgb/hsl. Misma regla: va por variable.');
  }
  const px = sinComentarios.match(/(?<![\w-])(?:padding|margin|gap|border-radius|font-size)\s*:\s*[^;]*\d+px/g);
  if (px) {
    hallazgos.push('Medida en px fuera del sistema. Espaciados con var(--esp-N), radios con var(--radio-*), tipografia con var(--texto-*). Las unicas medidas fijas permitidas son las del sistema: cabecera 72/56, logo 34 (isotipo 32), ancho maximo 1140, destino tactil 44.');
  }
  if (/font-size\s*:|font-family\s*:|font-weight\s*:\s*\d/.test(sinComentarios)) {
    hallazgos.push('Tipografia definida fuera del sistema. tipografia.css es la unica fuente de verdad del tipo: se aplica el rol (.tipo-h2, .tipo-cuerpo, .tipo-titulo-tarjeta, .precio, .tipo-secundario), no el tamano ni la familia. Si falta un rol, se agrega alli y se documenta.');
  }
  if (/outline\s*:\s*(none|0)/.test(sinComentarios)) {
    hallazgos.push('Se esta eliminando el indicador de foco. El foco visible de 3px es requisito de accesibilidad y no se retira.');
  }
}

// --- Angular: API vigentes ------------------------------------------------
if (enFrontend && es('.ts', '.html') && !esDocumentoLegal) {
  // Fuera de la revision: los comentarios, igual que en el bloque de estilos de arriba.
  // Este codigo se documenta explicando por que NO se hace algo —"con document.querySelector
  // el foco podia acabar en otro sitio", "en IndexedDB y no en localStorage"—, y castigar
  // esa prosa deja dos salidas, las dos malas: empeorar la explicacion, o acostumbrarse a
  // ignorar el hook. Es justo el falso positivo que la cabecera de este archivo dice que no
  // se admite. Lo que se busca son llamadas, y una llamada no vive en un comentario.
  // El comentario de linea solo se quita cuando ocupa la linea entera. Quitar tambien el
  // que va al final de una linea de codigo obligaria a distinguir un `//` de comentario de
  // uno dentro de una cadena, y no distinguirlo abre un agujero de verdad: cualquier linea
  // con `https://` perderia todo lo que va detras, incluido un `document.cookie`. Se
  // acepta a cambio que un comentario al final de una linea de codigo pueda dar un falso
  // positivo; los dos que motivaron esto eran de bloque.
  const codigo = texto
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/^[ \t]*\/\/[^\n]*$/gm, '')
    .replace(/<!--[\s\S]*?-->/g, '');

  const reglas = [
    [/\*ngIf|\*ngFor|\*ngSwitch/, 'Directivas estructurales antiguas. Este proyecto usa el flujo de control de plantilla: @if, @for con track, @switch.'],
    [/@NgModule/, 'NgModule. Todos los componentes son standalone.'],
    [/@Input\(|@Output\(/, 'Decoradores @Input/@Output. Se usan las funciones input(), output() y model().'],
    [/@ViewChild\(|@ContentChild\(/, 'Decorador de consulta antiguo. Se usan viewChild() y contentChild().'],
    [/HttpClientModule/, 'HttpClientModule. Se usa provideHttpClient().'],
  ];
  // Un archivo que YA hace lo que el mensaje pide no puede seguir recibiendolo.
  //
  // La regla dice «aislalo tras afterNextRender o una comprobacion de plataforma», y
  // cuando el archivo trae una de las dos cosas, eso es exactamente lo que hizo. Sin esta
  // salida la regla rechaza los seis archivos que ya estan en el repositorio y que son el
  // patron sancionado del proyecto —`favorite-intent.ts`, `session.store.ts`, `locale.ts`,
  // `language.service.ts` y los dos de idioma—, que es el falso positivo que la cabecera
  // de este archivo dice no admitir. La regla se escribio despues que ellos y nunca se
  // ejecuto encima.
  //
  // Lo que sigue cazando, que es lo que importa: un componente que toca `window.` sin
  // aislar nada. Lo que deja pasar: un segundo acceso sin aislar dentro de un archivo que
  // ya aisla el primero. Ese caso no lo puede ver una expresion regular, y para eso estan
  // el revisor de accesibilidad y las pruebas de renderizado en servidor.
  const aislaLaPlataforma = /isPlatformBrowser|afterNextRender|PLATFORM_ID/.test(codigo);
  if (!esPruebaDeNavegador && !aislaLaPlataforma) {
    reglas.push([/localStorage|sessionStorage|window\.|document\./, 'Acceso directo a API del navegador. Rompe el renderizado en servidor: aislalo tras afterNextRender o una comprobacion de plataforma.']);
  }
  for (const [patron, mensaje] of reglas) if (patron.test(codigo)) hallazgos.push(mensaje);

  if (es('.ts') && /@Component\(/.test(texto) && !/OnPush/.test(texto)) {
    hallazgos.push('Componente sin ChangeDetectionStrategy.OnPush.');
  }
  if (es('.html') && /@for\s*\([^)]*\)\s*\{/.test(texto) && !/track\s/.test(texto)) {
    hallazgos.push('@for sin track. Debe seguir un identificador estable.');
  }
}

// --- Spring Boot 4: API que cambiaron ------------------------------------

// La prueba de arquitectura es el catalogo de APIs prohibidas: tiene que
// nombrarlas para poder prohibirlas, y ahi aparecen como cadenas, nunca como
// import. Es el unico archivo exento y se identifica por ruta completa, para
// que crear un ArchitectureTest.java en otra carpeta no sirva de atajo.
const esCatalogoDeApisProhibidas =
  /backend\/bootstrap\/src\/test\/java\/co\/sendik\/ArchitectureTest\.java$/.test(ruta);

if (enBackend && es('.java') && !esCatalogoDeApisProhibidas) {
  const reglas = [
    [/com\.fasterxml\.jackson/, 'Jackson 2. Spring Boot 4 usa Jackson 3: paquete tools.jackson.'],
    // Solo los espacios de nombres que de verdad se movieron a jakarta. Un
    // /javax\./ a secas acusaba tambien a javax.crypto, javax.net y javax.sql, que
    // son del JDK y nunca se renombraron: la firma HS256 del token de acceso
    // necesita javax.crypto.SecretKey y no existe otra forma de escribirla.
    [
      /javax\.(persistence|servlet|validation|annotation|inject|transaction|ejb|jms|mail|enterprise|faces|batch|json|ws\.rs|xml\.bind|xml\.ws)\b/,
      'Espacio de nombres de Java EE. Se usa jakarta.',
    ],
    [/RestTemplate/, 'RestTemplate. Se usa RestClient o una interfaz @HttpExchange.'],
    [/@MockBean|@SpyBean/, 'Anotacion retirada. Se usa @MockitoBean y @MockitoSpyBean.'],
    [/WebSecurityConfigurerAdapter/, 'Clase eliminada hace varias versiones. La seguridad se configura con un bean SecurityFilterChain.'],
    [/@Autowired\s+(private|protected)/, 'Inyeccion por campo. Se inyecta por constructor, sin anotacion.'],
    [/ddl-auto/, 'Generacion automatica de esquema. El esquema lo gobierna Flyway.'],
    [/\b(double|float)\s+\w*([Pp]rice|[Aa]mount|[Mm]oney|[Tt]otal|[Cc]ommission)/, 'Dinero en coma flotante. Se usa el objeto de valor Money con BigDecimal.'],
  ];
  for (const [patron, mensaje] of reglas) if (patron.test(texto)) hallazgos.push(mensaje);

  if (/\/domain\//.test(ruta) && /import\s+(org\.springframework|jakarta\.persistence|tools\.jackson|org\.hibernate)/.test(texto)) {
    hallazgos.push('El modulo domain esta importando infraestructura. La dependencia va siempre hacia adentro: domain no conoce ningun framework.');
  }
  if (/\/presentation\//.test(ruta) && /@Transactional/.test(texto)) {
    hallazgos.push('@Transactional en presentacion. La transaccion pertenece al caso de uso.');
  }
}

// --- Textos visibles sin traducir ----------------------------------------
if (enFrontend && es('.html') && !esDocumentoLegal) {
  const conTexto = texto.replace(/<!--[\s\S]*?-->/g, '').match(/>\s*[A-Za-zÁÉÍÓÚÑáéíóúñ][^<>{}]{3,}</g);
  if (conTexto && !/transloco/i.test(texto)) {
    hallazgos.push('Texto visible escrito en la plantilla. Todo texto pasa por una clave de Transloco, en es y en en.');
  }
}

if (hallazgos.length) {
  process.stderr.write(`Convenciones incumplidas en ${ruta}:\n` + hallazgos.map((h) => `- ${h}`).join('\n') + '\nCorrigelo ahora, antes de continuar con la tarea.\n');
  process.exit(2);
}
process.exit(0);

function leerStdin() {
  try { return readFileSync(0, 'utf8'); } catch { return ''; }
}
