/**
 * La libreta de direcciones de entrega. HU-016.
 *
 * <p>TypeScript puro, sin Angular: son las reglas que valen igual en el navegador y en el
 * servidor (frontend/CLAUDE.md).
 *
 * <p><strong>Aquí no vive el tope de RN-101</strong>, y esa ausencia es deliberada. El
 * carrito tuvo que repetir su veinte en las dos mitades porque el carrito sin sesión lo
 * arma el navegador y allí no hay servidor que lo haga cumplir; HU-015 dejó anotado que esa
 * duplicación era la deuda más concreta que dejaba, sin ningún guardián que avisara si solo
 * se cambiaba una. Aquí toda escritura es autenticada, así que el número vive en un solo
 * sitio y la pantalla se entera del rechazo por el código de error. Que el mensaje pueda
 * nombrar el tope sin saberlo es lo que resuelve {@link laLibretaEstaLlena}.
 */

/** Un sitio de la división político-administrativa: lo que se manda y lo que se lee. */
export interface Place {
  readonly code: string;
  readonly name: string;
}

/** Una dirección guardada, tal como la devuelve la API. */
export interface ShippingAddress {
  readonly id: string;
  readonly recipientName: string;
  readonly phone: string;
  readonly departmentCode: string;
  readonly departmentName: string;
  readonly municipalityCode: string;
  readonly municipalityName: string;
  /**
   * Falso cuando el DANE suprimió ese municipio. La dirección se sigue leyendo igual; lo
   * que cambia es que al editarla hay que elegir otro (criterio 23).
   */
  readonly municipalityActive: boolean;
  readonly line: string;
  readonly complement: string | null;
  readonly instructions: string | null;
  readonly postalCode: string | null;
  readonly isDefault: boolean;
  readonly savedAt: string;
}

/** Lo que se manda al guardar. Se manda entero, también lo que quedó vacío. */
export interface AddressDraft {
  readonly recipientName: string;
  readonly phone: string;
  readonly municipalityCode: string;
  readonly line: string;
  readonly complement: string | null;
  readonly instructions: string | null;
  readonly postalCode: string | null;
}

const LARGO_MINIMO_DEL_NOMBRE = 2;
const LARGO_MAXIMO_DEL_NOMBRE = 80;
const LARGO_MINIMO_DE_LA_LINEA = 5;
const LARGO_MAXIMO_DE_LA_LINEA = 120;
const LARGO_MAXIMO_DEL_COMPLEMENTO = 60;
const LARGO_MAXIMO_DE_LAS_INDICACIONES = 200;

const TELEFONO = /^\+?\d{7,15}$/;
const SEPARADORES = /[\s().-]/g;
const CODIGO_DE_MUNICIPIO = /^\d{5}$/;
const CODIGO_POSTAL = /^\d{6}$/;

/**
 * Vacío y ausente son lo mismo: la persona no quiere tener ese dato.
 *
 * <p>Se unifica en el borde para que el resto del camino tenga una sola forma de decirlo,
 * igual que en el perfil. Sin esto, vaciar el complemento desde el formulario sería
 * imposible: el campo llegaría como cadena vacía y no como ausencia.
 */
export function comoDatoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === '' ? null : limpio;
}

export function elNombreDeQuienRecibeEsValido(valor: string): boolean {
  const limpio = valor.trim();
  return limpio.length >= LARGO_MINIMO_DEL_NOMBRE && limpio.length <= LARGO_MAXIMO_DEL_NOMBRE;
}

/**
 * La misma regla que el dominio del servidor, que es quien decide. Aquí solo evita un viaje
 * y un mensaje tardío.
 *
 * <p>No se valida contra el plan de numeración colombiano: entre un celular de diez dígitos
 * y un fijo con indicativo, y con los separadores que la gente escribe, un patrón rígido
 * rechaza números reales.
 */
export function elTelefonoEsValido(valor: string): boolean {
  const limpio = valor.trim();
  return limpio !== '' && TELEFONO.test(limpio.replace(SEPARADORES, ''));
}

/**
 * La línea de dirección solo se mide, no se reconoce.
 *
 * <p>Una dirección colombiana lleva almohadilla y guion y se escribe con «Cra», «Dg»,
 * «Tv», «Mz» y media docena de convenciones más. Cualquier expresión regular que intente
 * reconocerla rechaza direcciones reales, y este texto no se procesa: se imprime en una
 * guía para que un mensajero lo lea.
 */
export function laLineaEsValida(valor: string): boolean {
  const limpio = valor.trim();
  return limpio.length >= LARGO_MINIMO_DE_LA_LINEA && limpio.length <= LARGO_MAXIMO_DE_LA_LINEA;
}

export function elComplementoEsValido(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || opcional.length <= LARGO_MAXIMO_DEL_COMPLEMENTO;
}

export function lasIndicacionesSonValidas(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || opcional.length <= LARGO_MAXIMO_DE_LAS_INDICACIONES;
}

/** Seis dígitos, y opcional: en Colombia casi nadie se lo sabe. */
export function elCodigoPostalEsValido(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || CODIGO_POSTAL.test(opcional.replace(SEPARADORES, ''));
}

export function elMunicipioEsValido(valor: string): boolean {
  return CODIGO_DE_MUNICIPIO.test(valor.trim());
}

/**
 * El departamento de un municipio, que son sus dos primeros dígitos.
 *
 * <p>Es la misma propiedad de la que depende que el cuerpo de la API no pida departamento
 * (RN-100), y aquí sirve para otra cosa: al abrir una dirección guardada para editarla, el
 * selector de departamento tiene que llegar ya elegido para que el de municipio se pueda
 * poblar.
 */
export function departamentoDe(municipio: string): string {
  return municipio.slice(0, 2);
}

/**
 * Cuántas direcciones hay.
 *
 * <p>Existe con nombre propio por lo que significa **cuando el servidor acaba de rechazar
 * por tope**: en ese instante, cuántas hay es exactamente el máximo, así que el mensaje
 * puede nombrarlo sin que el número viva en dos sitios. Es la deuda que HU-015 dejó anotada
 * con el veinte del carrito, y aquí no hace falta contraerla.
 *
 * <p>Se llamaba `laLibretaEstaLlena`, que prometía un predicado y devolvía un número. Lo
 * cazó la revisión de pruebas.
 */
export function cuantasHay(direcciones: readonly ShippingAddress[]): number {
  return direcciones.length;
}

/**
 * El teléfono tal como viaja: solo dígitos, con un más opcional delante.
 *
 * <p><strong>Normalizar antes de mandar es del cliente.</strong> El contrato dice dígitos y
 * la gente escribe «300 123 4567»; el dominio del servidor también normaliza, pero el borde
 * valida la forma antes y rechazaría lo que llegue con separadores.
 */
export function comoViajaElTelefono(valor: string): string {
  return valor.trim().replace(SEPARADORES, '');
}

/**
 * El código postal tal como viaja, o nulo si no hay.
 *
 * <p><strong>Esto era un fallo de verdad y lo encontró la revisión de pruebas.</strong> El
 * formulario aceptaba «110 111» —que es como se escribe—, lo mandaba tal cual, y el borde lo
 * rechazaba con un 400 genérico: las tres suites en verde y nadie podía guardar su código
 * postal con un espacio. El servidor normaliza en el dominio, pero el borde valida antes.
 */
export function comoViajaElCodigoPostal(valor: string): string | null {
  const opcional = comoDatoOpcional(valor);
  return opcional === null ? null : opcional.replace(SEPARADORES, '');
}

/**
 * La predeterminada, si hay alguna. Exactamente una mientras la libreta no esté vacía.
 *
 * <p>La usa la pantalla después de borrar, para poder decir **cuál** quedó (criterio 11)
 * leyéndolo de la libreta refrescada en vez de adivinarlo: la regla del relevo vive en el
 * servidor.
 */
export function laPredeterminada(direcciones: readonly ShippingAddress[]): ShippingAddress | null {
  return direcciones.find((direccion) => direccion.isDefault) ?? null;
}

/**
 * Cómo se lee un municipio: siempre con su departamento al lado.
 *
 * <p>Criterio 24. Hay más de un «San Pedro» y más de una «Santa María» en Colombia, así
 * que el municipio solo no identifica un sitio. Vive aquí y no en la plantilla para que
 * haya una sola forma de escribirlo en toda la pantalla.
 */
export function comoSeLee(direccion: ShippingAddress): string {
  return `${direccion.municipalityName}, ${direccion.departmentName}`;
}
