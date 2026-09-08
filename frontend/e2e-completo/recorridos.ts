import { expect, type Page, type Response } from '@playwright/test';

import { MODERADORA } from '../playwright.completo.config';
import { capturarLasOchoTomas, tomarUnaFoto } from './camara';
import { esperarEnlace, enlacesVistos, rutaRelativa } from './correo-de-consola';
import { pngDe } from './png';

/**
 * Los recorridos que las suites comparten.
 *
 * <p>Nacieron dentro de `moderacion-de-publicaciones.spec.ts` porque fue la primera que los
 * necesitó. Se sacaron aquí al llegar HU-009: el catálogo público necesita exactamente lo
 * mismo —una vendedora verificada, una publicación enviada y un moderador que la apruebe—
 * para tener algo que enseñar, y copiar ciento cincuenta líneas de recorrido en una segunda
 * suite es garantizar que un día las dos hagan cosas distintas.
 *
 * <p><strong>Son recorridos, no utilidades.</strong> Cada uno deja el sistema en un estado
 * que una prueba puede dar por cierto: «hay una vendedora con el sello», «hay una
 * publicación esperando revisión». Por eso pasan siempre por la interfaz y nunca llaman a
 * la API: una suite que se salta la pantalla para llegar antes deja de probar la pantalla.
 */

export const CONTRASENA = 'una-contrasena-larga-de-verdad';
export const RUTA_PUBLICAR = '/publicar';
export const RUTA_MIS_PUBLICACIONES = '/mis-publicaciones';
export const RUTA_VERIFICACION = '/verificacion-de-vendedor';

/** El mínimo de RN-019. Una más pequeña se rechaza y la prueba no llegaría a nada. */
export const TOMA = () => ({ name: 'toma.png', mimeType: 'image/png', buffer: pngDe(900, 1200) });

/**
 * De donde salen las ocho tomas de una publicacion.
 *
 * <p>Por omision la galeria, que es lo que hacian las tres suites que ya existian y lo que
 * les sirve: ninguna prueba la captura, y el campo de archivo llega al mismo sitio en una
 * fraccion del tiempo. `camara` recorre el asistente de HU-003 paso por paso, y solo lo
 * pide la suite que existe para probarlo.
 */
export type OrigenDeLasTomas = 'galeria' | 'camara';

/**
 * Los meses de garantía que declara el dispositivo de prueba (RN-067).
 *
 * <p>Se exporta porque la prueba que mira la ficha tiene que afirmar **este** número y no
 * uno escrito a mano en los dos sitios: un valor duplicado que se cambia en uno solo deja
 * la prueba comprobando otra cosa que la que se publicó.
 *
 * <p>Doce y no uno: el singular tiene su propia clave y su propia prueba de unidad; lo que
 * este recorrido comprueba es el camino completo, y para eso el plural es el caso normal.
 */
export const MESES_DE_GARANTIA = 12;

export const NOMBRE_MODERADORA = 'Quien Modera';

export function correoNuevo(que: string): string {
  return `${que}-${Date.now()}-${Math.floor(Math.random() * 1000)}@example.test`;
}

export function cedulaNueva(): string {
  return String(Date.now()).slice(-9) + String(Math.floor(Math.random() * 900) + 100);
}

export async function registrar(page: Page, correo: string, nombre = 'Ana María'): Promise<void> {
  const yaVistos = enlacesVistos('/verificar-correo');

  await page.goto('/registro');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Nombre').fill(nombre);
  await page.getByLabel('Contraseña').fill(CONTRASENA);
  await page.getByLabel('Fecha de nacimiento').fill('1990-03-04');
  // Por identificador y no por texto: la redaccion de los dos consentimientos
  // la fija el regimen de datos personales y se retoca sin avisar. Lo que este
  // recorrido necesita es marcar las casillas; de comprobar como estan
  // redactadas responde e2e/registro.spec.ts, que para eso existe.
  await page.locator('#terminos').check();
  await page.locator('#privacidad').check();
  await page.getByRole('button', { name: 'Crear cuenta' }).click();
  await expect(page.getByRole('heading', { name: 'Revisa tu correo' })).toBeVisible();

  await page.goto(rutaRelativa(await esperarEnlace('/verificar-correo', yaVistos)));
  await expect(page.getByRole('link', { name: nombre })).toBeVisible();
}

/**
 * Entra, y no vuelve hasta que el servidor haya contestado.
 *
 * <p><strong>Esperar la respuesta no es prudencia de mas.</strong> `click()` espera al
 * clic, no a la peticion. Quien llama y navega acto seguido —`dejarUnaVendedoraVerificada`
 * lo hace, y va derecho a /publicar— aborta el `POST /auth/login` en vuelo: en la traza
 * sale con estado -1. La sesion no llega a existir, la cookie de refresco tampoco, y lo
 * que se ve despues es un 401 en la primera peticion con token de la pantalla siguiente,
 * que no se parece en nada a la causa.
 *
 * <p>Se espera la respuesta y no el enlace de la cuenta en la cabecera, porque esto lo
 * usa tambien quien todavia no tiene cuenta: ahi el 401 es la respuesta correcta y quien
 * llama decide que hacer con ella.
 */
/**
 * Rellena el formulario de ingreso y espera la respuesta.
 *
 * <p><strong>No comprueba que el ingreso saliera bien</strong>, y es a propósito: un 401 o
 * un 429 también son respuestas, y {@link entrarComoModeradora} necesita que un ingreso
 * fallido se tolere —así crea la cuenta la primera vez que corre la suite.
 *
 * <p>La consecuencia es que quien necesite la sesión abierta tiene que **comprobarlo
 * después**, y comprobarlo afirmando lo que tiene que estar. Costó una corrida entenderlo:
 * un ingreso que falla aquí no rompe nada, sigue de largo, y el fallo aparece cinco pasos
 * más allá con un síntoma que no lo explica.
 */
export async function ingresar(page: Page, correo: string): Promise<void> {
  await page.goto('/ingresar');
  await page.getByLabel('Correo electrónico').fill(correo);
  await page.getByLabel('Contraseña').fill(CONTRASENA);

  await Promise.all([
    page.waitForResponse((respuesta) => respuesta.url().includes('/auth/login')),
    page.getByRole('button', { name: 'Entrar' }).click(),
  ]);
}

export async function salirSiHaySesion(page: Page): Promise<void> {
  await page.goto('/');

  const salir = page.getByRole('button', { name: 'Salir' });
  const entrar = page.getByRole('link', { name: 'Entrar' });

  // **Se espera a que la cabecera diga algo antes de preguntar.** Al cargar la página, la
  // sesión se recupera con la cookie de refresco, y hasta que esa vuelta termina no hay ni
  // «Salir» ni «Entrar». Preguntando antes, una sesión abierta se lee como «no hay
  // ninguna» y el recorrido sigue con ella puesta: es lo que dejaba a quien moderaba
  // dentro cuando la prueba siguiente esperaba a un visitante anónimo.
  await expect(salir.or(entrar)).toBeVisible();

  if (await salir.isVisible()) {
    await salir.click();
    await expect(entrar).toBeVisible();
  }
}

/**
 * Entra con la cuenta que modera, creándola si hace falta.
 *
 * <p>El correo es fijo y el backend se reutiliza entre ejecuciones en local, así que la
 * cuenta puede existir ya. El rol lo concede `SECURITY_BOOTSTRAP_MODERATORS`, y funciona
 * porque se otorga también al registrarse.
 */
export async function entrarComoModeradora(page: Page): Promise<void> {
  await salirSiHaySesion(page);
  await ingresar(page, MODERADORA);

  const dentro = await page
    .getByRole('link', { name: NOMBRE_MODERADORA })
    .waitFor({ timeout: 5_000 })
    .then(() => true)
    .catch(() => false);

  if (!dentro) {
    await registrar(page, MODERADORA, NOMBRE_MODERADORA);
  }

  await expect(page.getByRole('link', { name: NOMBRE_MODERADORA })).toBeVisible();
}

/**
 * Espera a que la bandeja termine de cargar, sea cual sea el resultado.
 *
 * <p>Hace falta porque `isVisible()` no espera: responde por lo que hay en pantalla en ese
 * instante. Preguntado nada más llegar, mientras la pantalla todavía muestra el esqueleto,
 * dice que no está la fila aunque vaya a estarlo, y el recorrido se va a buscarla a una
 * página siguiente que no existe. Era una carrera, y de ahí salían las pruebas que fallaban
 * unas veces sí y otras no.
 *
 * <p>Se espera a cualquiera de los tres finales de la carga —la lista, el estado vacío o el
 * error— y no solo a la lista: si la bandeja queda vacía o rompe, esperar la lista sería
 * esperar para siempre, y quien lea el fallo merece verlo en la comprobación que importa y
 * no en un tiempo agotado aquí.
 *
 * <p>Y se espera además a que la lista deje de estar ocupada. Hizo falta al arreglar el
 * foco: la bandeja conserva la página anterior mientras llega la siguiente, así que ver
 * filas dejó de significar que sean las de esta página. Sin esto, el recorrido decide
 * sobre contenido viejo y se salta una página entera.
 */
async function esperarAQueAterriceLaBandeja(page: Page): Promise<void> {
  // Las filas se reconocen por lo que todas dicen —desde cuándo espera la solicitud— que es
  // texto de la pantalla y no una clase de CSS. El esqueleto no cuenta: está oculto a la
  // accesibilidad, así que ningún localizador por rol lo alcanza.
  const algunaFila = page.getByRole('link').filter({ hasText: 'Espera desde' }).first();
  const vacia = page.getByText('No hay nada por revisar');
  const rota = page.getByText('No pudimos cargar la bandeja');

  await expect(algunaFila.or(vacia).or(rota)).toBeVisible();

  // Y que lo que se ve sea de ahora. Al cambiar de página la bandeja conserva la anterior
  // en pantalla —para no desmontar la paginación con el foco dentro— así que «hay filas»
  // ya no significa «son las de esta página». Mientras llega la nueva, la lista se declara
  // ocupada, que es la misma señal que recibe un lector de pantalla.
  await expect(page.locator('[aria-busy="true"]')).toHaveCount(0);
}

/**
 * Abre en la bandeja la solicitud de un titular concreto, buscándola por sus páginas.
 *
 * <p><strong>Recorre, y no mira solo la primera página.</strong> La bandeja es FIFO —lo
 * más viejo primero— así que la solicitud que se acaba de enviar está al final, y contra
 * una base que arrastra pendientes de corridas anteriores no aparece en la primera. Es
 * lo que dejaba esta suite sin poder pasar dos veces seguidas contra la misma base.
 *
 * <p>Se recorre por la interfaz, con el botón que usa quien modera, y no llamando a la
 * API: una suite que se salta la pantalla para llegar antes deja de probar la pantalla.
 */
async function abrirEnLaBandeja(page: Page, titular: string): Promise<void> {
  await page.goto('/moderacion/verificaciones');

  const fila = page.getByRole('link').filter({ hasText: titular });

  // Acotado a la navegación de páginas: en esta pantalla puede haber más de un
  // `role="status"` —el aviso de lo que se acaba de decidir, y el de «cargando»— y
  // preguntar por todos hace fallar el localizador por ambigüedad.
  const paginacion = page.getByRole('navigation', { name: 'Páginas de la bandeja' });
  const siguiente = paginacion.getByRole('button', { name: 'Siguiente' });

  // Acotado: sin tope, una bandeja que devolviera siempre la página llena daría una vuelta
  // infinita, y el fallo sería un tiempo de espera agotado sin ninguna pista de por qué.
  for (let numeroDePagina = 1; numeroDePagina <= 50; numeroDePagina++) {
    await esperarAQueAterriceLaBandeja(page);

    if (await fila.first().isVisible()) {
      await fila.first().click();
      await expect(page.getByRole('button', { name: 'Aprobar' })).toBeVisible();
      return;
    }

    // La navegación de páginas solo se pinta cuando hay a dónde ir: con una sola página
    // no está en el DOM, que es el caso normal contra una base recién levantada. Se
    // pregunta primero si está, porque cualquier cosa que se le pida a un localizador que
    // no resuelve —`getAttribute` incluida— espera a que aparezca, y aquí eso significa
    // agotar el tiempo de la prueba esperando algo que no va a llegar.
    if (!(await siguiente.isVisible())) {
      break;
    }

    // `aria-disabled` y no `disabled`: los botones siguen habilitados a propósito para no
    // perder el foco al llegar al extremo, así que preguntar por `isDisabled()` diría
    // siempre que no.
    if ((await siguiente.getAttribute('aria-disabled')) !== 'false') {
      break;
    }

    await siguiente.click();

    // Se espera al número, y no a que el botón exista: pulsar dispara una petición, y sin
    // esperar a que aterrice la vuelta siguiente miraría todavía la página anterior.
    await expect(paginacion.getByRole('status')).toHaveText(`Página ${numeroDePagina + 1}`);
  }

  throw new Error(
    `No se encontró en la bandeja ninguna solicitud de «${titular}». ` +
      'Si la bandeja arrastra muchas pendientes, se recorrieron 50 páginas sin dar con ella.',
  );
}

/**
 * Deja una cuenta nueva verificada como vendedora.
 *
 * <p>RN-011: sin el sello no se puede publicar, así que el recorrido de HU-008 empieza
 * necesariamente por el de HU-002. Se hace por la interfaz y no llamando a la API, que es
 * justo lo que esta suite existe para no hacer.
 */
export async function dejarUnaVendedoraVerificada(page: Page, quien: string): Promise<string> {
  const correo = correoNuevo(quien);
  // El titular tiene que ser unico: la bandeja de verificaciones lo muestra, y es lo unico
  // que permite aprobar LA solicitud de esta prueba. Con un nombre fijo se aprobaba la mas
  // vieja de la cola, que en local puede ser de otra ejecucion, y esta vendedora se quedaba
  // sin sello: el sintoma era «Empezar» deshabilitado en /publicar.
  const titular = `Ana Maria ${quien} ${Date.now()}`;
  await registrar(page, correo);

  await page.goto(RUTA_VERIFICACION);
  await page.getByRole('button', { name: 'Empezar' }).click();

  await page.getByLabel('Tipo de documento').selectOption('CC');
  await page.getByLabel('Número del documento').fill(cedulaNueva());
  await page.getByLabel('Nombre completo, como aparece en el documento').fill(titular);
  await tomarUnaFoto(page);
  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar el documento' }).click();
  await expect(page.getByText('Guardamos tu documento.')).toBeVisible();

  await tomarUnaFoto(page);
  await page.getByRole('button', { name: 'Guardar la foto' }).click();
  await expect(page.getByText('Guardamos tu foto.')).toBeVisible();

  await page.getByLabel('Entidad').selectOption('bancolombia');
  await page.getByLabel('Tipo de cuenta').selectOption('SAVINGS');
  await page.getByLabel('Número de cuenta').fill('91500123456');
  await page.getByLabel('Nombre del titular').fill(titular);
  await page.getByRole('button', { name: 'Guardar la cuenta' }).click();
  await expect(page.getByText('Guardamos tu cuenta.')).toBeVisible();

  await page.getByRole('button', { name: 'Enviar para revisión' }).click();
  await expect(page.getByText('Estamos revisando tu solicitud.')).toBeVisible();

  // La aprueba quien modera, que es el único camino que hay.
  await entrarComoModeradora(page);
  await abrirEnLaBandeja(page, titular);
  await page.getByRole('button', { name: 'Aprobar' }).click();
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.getByText('Verificación aprobada')).toBeVisible();

  await salirSiHaySesion(page);
  await ingresar(page, correo);

  // **Que la sesión quedó abierta.** `ingresar` no lo comprueba —no puede, ver su
  // documentación— así que se afirma aquí, y se afirma sobre algo que solo existe con
  // sesión. Sin esto, un ingreso que falla sigue adelante y el recorrido se rompe después
  // en otro ayudante: en integración continua se vio como un `Título` que no llegaba
  // nunca, cinco pasos más allá, porque no había sesión con la que crear el borrador.
  await expect(page.getByRole('button', { name: 'Salir' })).toBeVisible();

  // Se comprueba el sello antes de seguir: sin verificacion, la pantalla lo dice en vez
  // de ofrecer el formulario (RN-011).
  //
  // **Se afirma también lo que tiene que estar, y no solo lo que no.** La ausencia del
  // aviso se cumple igual en una pantalla que no es esta —la de ingresar, por ejemplo—,
  // así que sola no distingue «está verificada» de «no llegamos aquí».
  await page.goto(RUTA_PUBLICAR);
  await expect(page.getByLabel('Categoría')).toBeVisible();
  await expect(page.getByText('Verifícate como vendedor para poder publicar.')).toHaveCount(0);

  return correo;
}

/**
 * Qué se publica. De la familia dependen la categoría, la condición admisible, las
 * medidas que se piden y cuántas tomas exige el formulario (RN-064, RN-065).
 *
 * <p>Es un parámetro y no dos funciones porque lo que cambia entre las dos es **el
 * formulario**, no el recorrido: crear el borrador, esperar el guardado automático del
 * envío, subir las tomas y enviar a revisión es lo mismo, y esa espera —la del guardado—
 * es la parte delicada de este archivo. Duplicarla para publicar un dispositivo sería
 * duplicar justo lo que cuesta mantener.
 */
export type FamiliaDeLoQueSePublica = 'moda' | 'tecnologia';

/**
 * Un borrador completo, con sus tomas, enviado a revisión.
 *
 * <p>Con `tecnologia` publica un dispositivo **sellado**: condición nueva, que es la única
 * que RN-064 admite en esa familia, y las cuatro tomas del empaque en vez de las ocho
 * (RN-065). Declara además meses de garantía del fabricante, que es lo que RN-067 pide
 * enunciar en la ficha.
 *
 * @returns el identificador de la publicación, que es lo que permite volver a ella sin
 *     tener que encontrarla en una lista compartida entre pruebas
 */
export async function publicarYEnviarARevision(
  page: Page,
  titulo: string,
  tomas: OrigenDeLasTomas = 'galeria',
  familia: FamiliaDeLoQueSePublica = 'moda',
): Promise<string> {
  const esDispositivo = familia === 'tecnologia';

  await page.goto(RUTA_PUBLICAR);

  // La categoria se elige antes de crear el borrador, y no es un capricho de navegacion:
  // de ella dependen las condiciones admisibles, los sistemas de talla y que medidas se
  // piden. Hasta elegirla, «Empezar» esta deshabilitado.
  await page
    .getByLabel('Categoría')
    .selectOption({ label: esDispositivo ? 'Celulares y tabletas' : 'Camisas y blusas' });
  await page.getByRole('button', { name: 'Empezar' }).click();

  // Si la creacion falla, la pantalla lo dice en un `role="alert"`. Se mira primero para
  // que el fallo diga el motivo y no un tiempo de espera agotado buscando el formulario.
  const fallo = page.locator('.publicar__error');
  if (await fallo.isVisible().catch(() => false)) {
    throw new Error(`No se pudo crear el borrador: ${await fallo.textContent()}`);
  }

  await expect(page.getByLabel('Título')).toBeVisible();

  // El identificador, de donde ya está: crear el borrador navega a `/publicar/:id`. No
  // hace falta pedírselo a nadie ni buscarlo después en ninguna lista.
  const id = new URL(page.url()).pathname.split('/').pop() ?? '';
  expect(id, `No se pudo leer el identificador de ${page.url()}`).not.toBe('');

  await page.getByLabel('Título').fill(titulo);
  await page
    .getByLabel('Descripción')
    .fill(
      esDispositivo
        ? 'Sellado de fábrica, nunca abierto. Factura a nombre del vendedor.'
        : 'Usada dos veces, sin manchas ni descosidos.',
    );
  await page.getByLabel('Marca').fill(esDispositivo ? 'Samsung' : 'Zara');

  // La condición no es una preferencia: en tecnología solo se publica lo nuevo y el
  // formulario no ofrece las otras tres (RN-064).
  await page.getByRole('radio', { name: esDispositivo ? 'Nuevo' : 'Como nuevo' }).check();

  await page
    .getByLabel('Sistema de talla')
    .selectOption({ label: esDispositivo ? 'Talla única' : 'Letra (XS a XXL)' });
  // «U» y no «Única»: `SizeSystem.ONE_SIZE` admite ese único valor y el dominio rechaza
  // cualquier otro, así que el guardado automático responde 400 y la publicación se queda
  // sin envío. El rótulo que ve quien publica es «Talla única»; lo que se guarda es «U».
  await page.getByLabel('Valor de la talla').fill(esDispositivo ? 'U' : 'M');

  // Las medidas del grupo que declara la categoría. Sin ellas el envío se rechaza con
  // CATALOG_MEASUREMENTS_INCOMPLETE (RN-021), y van acotadas a su grupo porque «Largo»
  // es también una de las tres dimensiones de la caja. En tecnología el grupo es `DEVICE`
  // y pide otras tres: alto, ancho y fondo.
  const medidas = page.getByRole('group', { name: 'Medidas' });
  // Como tuplas y no como `string[][]`: sin el tipo, al desestructurar TypeScript da
  // `string | undefined` y `getByLabel` no lo acepta.
  const declaradas: readonly (readonly [string, string])[] = esDispositivo
    ? [
        ['Alto', '16'],
        ['Ancho', '8'],
        ['Fondo', '1'],
      ]
    : [
        ['Pecho', '52'],
        ['Hombros', '41'],
        ['Manga', '60'],
        ['Largo', '70'],
      ];
  for (const [medida, valor] of declaradas) {
    await medidas.getByLabel(medida).fill(valor);
  }

  await page.getByLabel('Color').selectOption({ label: esDispositivo ? 'Negro' : 'Beige' });
  await page.getByLabel('Precio').fill(esDispositivo ? '2400000' : '185000');

  // Lo que solo existe en tecnología. **Antes del envío a propósito:** declarar el
  // dispositivo sellado cambia las tomas que se exigen de ocho a cuatro (RN-065), y esa
  // cuenta la manda el servidor en `requiredShots`. Puestas las tomas primero, se subirían
  // ocho a un formulario que después pinta cuatro casillas.
  if (esDispositivo) {
    await page.getByLabel('Está sellado, sin abrir').check();
    await page.getByLabel('Meses de garantía del fabricante').fill(String(MESES_DE_GARANTIA));
  }

  // El envío entero: el peso y las tres dimensiones son un grupo y media caja no es una
  // caja. Faltaba, y era una de las dos razones por las que esto no llegaba a enviarse.
  const envio = page.getByRole('group', { name: 'Envío' });
  await envio.getByLabel('Peso en gramos').fill('600');
  await envio.getByLabel('Largo').fill('30');
  await envio.getByLabel('Ancho').fill('20');

  // El guardado es automático y sale 1,5 s después de dejar de escribir, y hay que verlo
  // aterrizar antes de seguir: con el guardado todavía sin salir, «Enviar a revisión»
  // manda la publicación sin el envío y el servidor la rechaza con
  // `CATALOG_LISTING_INCOMPLETE`, tres pantallas más allá del motivo real.
  //
  // **Ya no se espera por lo que decía antes.** Este comentario sostenía que una subida y
  // un guardado en vuelo a la vez se tumbaban entre sí por el bloqueo optimista del
  // criterio 34, y era cierto: la pantalla mandaba escrituras solapadas y alguna volvía
  // con un 409. Eso se arregló donde estaba —las escrituras sobre una publicación salen
  // de una en una desde `ListingStore`—, así que lo que queda aquí es solo la
  // sincronización con el debounce, no un rodeo para esquivar un defecto.
  //
  // Se espera la respuesta que ya trae el envío —lo último que se escribe— y no el
  // cartel de «Guardado», que puede seguir puesto de un guardado anterior.
  //
  // **Se arma antes de escribir lo último, y eso no es estilo.** `waitForResponse` solo
  // ve lo que ocurre desde que se registra, así que armándolo después queda un hueco
  // entre el último `fill` y esta línea; si el guardado aterriza ahí, se espera para
  // siempre algo que ya pasó. Con 1,5 s de margen casi nunca ocurre, y por eso esta
  // prueba fallaba una vez de cada muchas en integración continua y nunca en local.
  //
  // Y se espera **el alto**, no un envío cualquiera: escribiendo de campo en campo el
  // guardado puede salir a medias -con peso, largo y ancho ya puestos- y esa respuesta
  // también traería `shipping`. Darla por buena dejaría el alto sin confirmar, y el alto
  // es lo que hace que la caja esté completa.
  // **Un guardado que falla también cierra la espera.** Esperando solo el exito, un
  // guardado rechazado -por el bloqueo optimista, por un 401, por lo que sea- no produce
  // ninguna respuesta que casar y esto se queda aguardando algo que ya no va a pasar: se
  // agota el tiempo de la prueba y el fallo culpa a la espera, que es lo unico que se ve.
  // Paso en integracion continua con sesenta segundos de presupuesto, asi que no era el
  // presupuesto: el guardado no llegaba.
  //
  // Cerrando la espera tambien con el fallo, el motivo real -el estado que devolvio- sale
  // en la asercion de abajo en vez de perderse.
  //
  // **Se apunta todo lo que pasa por la red mientras se espera.** Cuando esto falla en
  // integracion continua -y falla- el sintoma es siempre el mismo: se agoto el tiempo. Eso
  // no distingue las tres causas posibles, que piden arreglos distintos: que el guardado
  // saliera y su cuerpo no trajera el alto, que saliera y fallara, o que **no llegara a
  // salir**. Sin esta libreta las tres se ven igual y no hay por donde empezar.
  const patchesVistos: string[] = [];
  const anotar = (respuesta: Response) => {
    if (respuesta.request().method() === 'PATCH') {
      patchesVistos.push(`${respuesta.status()} ${new URL(respuesta.url()).pathname}`);
    }
  };
  page.on('response', anotar);

  // Tiempo propio y menor que el de la prueba: si se agota el de la prueba, esta muere
  // aqui mismo y el diagnostico de abajo no llega a escribirse.
  //
  // **El predicado mira lo que se envio, no lo que se respondio, y es sincrono.** Antes leia
  // el cuerpo de la respuesta con `respuesta.json()`, y eso es una trampa: `body()` espera a
  // que el cuerpo llegue entero, asi que una respuesta que se queda a medias deja ese
  // predicado sin resolver y **bloquea la evaluacion de las siguientes**, incluida la del
  // guardado bueno. El sintoma es este mismo -respuestas que llegan y una espera que no casa
  // ninguna- y no se distingue de que el guardado no saliera.
  //
  // Mirando `postDataJSON()` no hace falta ningun cuerpo: la peticion ya esta en memoria, y
  // lo que identifica al guardado que interesa es que lleve el alto, que es justo lo que se
  // acaba de escribir.
  const guardadoDelEnvio = page
    .waitForResponse(
      (respuesta) => {
        const peticion = respuesta.request();
        if (peticion.method() !== 'PATCH' || !respuesta.url().includes('/listings/')) {
          return false;
        }
        if (!respuesta.ok()) {
          return true;
        }
        const enviado = peticion.postDataJSON() as { shipping?: { heightCm?: unknown } } | null;
        return enviado?.shipping?.heightCm != null;
      },
      { timeout: 25_000 },
    )
    .catch(() => null);

  await envio.getByLabel('Alto').fill('10');

  const respuestaDelGuardado = await guardadoDelEnvio;
  page.off('response', anotar);

  if (respuestaDelGuardado === null) {
    const enPantalla = await page
      .locator('.publicar__error')
      .allInnerTexts()
      .catch(() => []);
    const alto = await envio
      .getByLabel('Alto')
      .inputValue()
      .catch(() => '?');

    throw new Error(
      'El guardado automatico del envio no llego. ' +
        `PATCH vistos durante la espera: ${JSON.stringify(patchesVistos)}. ` +
        `Valor del alto en el formulario: "${alto}". ` +
        `Errores en pantalla: ${JSON.stringify(enPantalla)}. ` +
        'Lista vacia de PATCH significa que el guardado no llego a salir, que es otro ' +
        'defecto -y otro arreglo- que uno que sale y responde mal.',
    );
  }

  expect(
    respuestaDelGuardado.ok(),
    `El guardado automatico del envio respondio ${respuestaDelGuardado.status()}. ` +
      'Sin ese guardado la publicacion queda sin envio y el envio a revision se rechaza ' +
      'despues con CATALOG_LISTING_INCOMPLETE, tres pantallas mas alla.',
  ).toBeTruthy();

  // Las tomas. Por el campo de archivo salvo que se pida lo contrario: la cámara es de
  // HU-003, y para las suites que prueban el ciclo de moderación o el catálogo es un rodeo
  // de ocho capturas para llegar al mismo estado.
  //
  // Un dispositivo sellado pide **cuatro** y no ocho, y no son las cuatro primeras: son las
  // canónicas —0, 90, 180 y 270 grados—, que en posiciones son 0, 2, 4 y 6. El formulario
  // solo pinta esas casillas, así que pedir `#toma-1` esperaría por algo que no existe.
  const posiciones = esDispositivo ? [0, 2, 4, 6] : [0, 1, 2, 3, 4, 5, 6, 7];

  if (tomas === 'camara') {
    await capturarLasOchoTomas(page);
  } else {
    for (const posicion of posiciones) {
      await page.locator(`#toma-${posicion}`).setInputFiles(TOMA());
      await expect(page.locator(`#toma-${posicion}`)).toHaveCount(0);
    }
  }

  await page.getByRole('button', { name: 'Enviar a revisión' }).click();

  // Se comprueba el estado y no un cartel de confirmación: `listing.submit.sent`
  // —«Enviada a revisión»— está en el archivo de textos pero ninguna plantilla lo usa,
  // así que la prueba esperaba algo que la pantalla no pinta. Lo que sí se ve es que la
  // publicación pasó a revisión: la acción de enviar deja su sitio a la de retirar.
  await expect(page.getByRole('button', { name: 'Retirar de revisión' })).toBeVisible();

  return id;
}

/**
 * Devuelve a borrador una publicación que está esperando revisión.
 *
 * <p><strong>Es limpieza, no un recorrido.</strong> Una prueba que necesita algo *en
 * revisión* lo deja detrás al terminar, y la cola del moderador es compartida y FIFO: lo
 * que se acumula de corridas anteriores empuja fuera de la primera página lo que envía la
 * siguiente. Ahí es donde la suite dejaba de pasar dos veces seguidas contra la misma base.
 *
 * <p>Retirar es el gesto del propio vendedor y no necesita moderador, así que la prueba
 * puede recoger lo suyo sin cambiar de cuenta. Quien llama tiene que tener abierta la
 * sesión de quien publicó.
 */
export async function retirarDeRevision(page: Page, id: string): Promise<void> {
  await page.goto(`/publicar/${id}`);
  await page.getByRole('button', { name: 'Retirar de revisión' }).click();
  await expect(page.getByRole('button', { name: 'Enviar a revisión' })).toBeVisible();
}

/**
 * Deja una publicación **visible en el catálogo**, con su vendedora verificada.
 *
 * <p>Es el recorrido completo de la fase 2 en una llamada: alguien se verifica, publica,
 * un moderador aprueba y la publicación pasa a `PUBLISHED`. Lo necesita HU-009, que no
 * tiene otra forma de conseguir algo que enseñar: RN-068 dice que en el catálogo solo se
 * ve lo aprobado, y aprobarlo por la API sería saltarse la mitad de lo que esta suite
 * existe para probar.
 *
 * <p>Cierra la sesión al terminar: quien llama va a mirar el catálogo, y todo el sentido
 * de esa comprobación es que se ve igual sin cuenta.
 *
 * @returns el correo de la vendedora, para quien necesite volver a entrar como ella
 */
export async function publicarYAprobar(
  page: Page,
  titulo: string,
  quien: string,
  tomas: OrigenDeLasTomas = 'galeria',
  familia: FamiliaDeLoQueSePublica = 'moda',
): Promise<string> {
  const correo = await dejarUnaVendedoraVerificada(page, quien);
  const id = await publicarYEnviarARevision(page, titulo, tomas, familia);

  await entrarComoModeradora(page);

  // **Al detalle por su identificador, y no buscándolo en la cola.** La cola es FIFO
  // —lo más viejo primero, veinte por página— así que lo que se acaba de enviar va al
  // final. Contra una base recién creada eso da igual y contra una que arrastra
  // pendientes de corridas anteriores no aparece en la primera página, que es lo único
  // que este recorrido miraba: era la causa de que la suite entera dejara de pasar dos
  // veces seguidas en local.
  //
  // No se pierde nada por el camino: encontrarla en la cola es lo que prueba
  // `moderacion-de-publicaciones.spec.ts`, que existe para eso. Aquí la cola no es el
  // objeto de la prueba sino el camino hacia un estado, y el detalle es la misma
  // pantalla del moderador a la que se llega pulsando la fila.
  await page.goto(`/moderacion/publicaciones/${id}`);
  await expect(page.getByRole('heading', { name: titulo })).toBeVisible();

  await page.getByRole('button', { name: 'Aprobar' }).click();
  await page.getByRole('button', { name: 'Confirmar' }).click();
  await expect(page.getByText('Publicación aprobada')).toBeVisible();

  await salirSiHaySesion(page);

  return correo;
}
