/**
 * El perfil de una persona sobre su propia cuenta. Criterio 21 de HU-001.
 *
 * <p>TypeScript puro, sin Angular: son las reglas que valen igual en el
 * navegador y en el servidor (frontend/CLAUDE.md).
 *
 * <p>De la foto llega su **direccion**, no la clave con la que esta guardada. La
 * clave es un detalle del almacen y lo unico que se hace con la foto aqui es
 * pintarla (ADR-0018).
 */
export interface Profile {
  readonly email: string;
  readonly emailVerified: boolean;
  readonly displayName: string;
  /** Nulos cuando la persona no los tiene puestos: no se piden al registrarse. */
  readonly city: string | null;
  /**
   * Falso cuando la ciudad viene de la direccion de origen (HU-017, ADR-0042): entonces es
   * el municipio con su departamento al lado y se cambia desde el origen, no desde aqui.
   */
  readonly cityEditable: boolean;
  readonly phone: string | null;
  /** Nula mientras no haya foto. */
  readonly avatarUrl: string | null;
}

/** Lo que se manda al guardar. Se manda entero, tambien lo que quedo vacio. */
export interface ProfileEdit {
  readonly displayName: string;
  readonly city: string | null;
  readonly phone: string | null;
}

/**
 * Los dos tipos que el servidor acepta (ADR-0018).
 *
 * <p>Se repiten aqui a proposito: no son configuracion sino parte del contrato de
 * la API, y saberlos permite decirlo antes de gastar una subida. El servidor
 * decide igual, y lo hace mirando los bytes de cabecera y no lo que declare el
 * navegador, asi que esta lista no es una defensa: es una cortesia.
 *
 * <p>WebP no esta, y no es un olvido: el servidor no puede recodificarlo para
 * quitarle el EXIF, asi que no lo acepta.
 */
export const TIPOS_DE_IMAGEN_ACEPTADOS = ['image/jpeg', 'image/png'] as const;

/** Para el atributo `accept` del campo de archivo. */
export const ACEPTA_IMAGENES = TIPOS_DE_IMAGEN_ACEPTADOS.join(',');

/**
 * Si el navegador dice que el archivo es de un tipo aceptado.
 *
 * <p>Lo que dice el navegador se cree solo para adelantar el mensaje. Quien decide
 * es el servidor, que mira el contenido: un archivo renombrado a `.jpg` pasa por
 * aqui y se rechaza alli, que es el orden correcto.
 *
 * <p>No se comprueba el tamano. El tope vive en la configuracion del servidor y
 * repetirlo aqui crearia dos fuentes de verdad que se separan en cuanto una
 * cambie; cuando se pasa, el servidor responde 413 y la pantalla traduce ese
 * codigo.
 */
export function elTipoDeImagenEsAceptado(tipo: string): boolean {
  return (TIPOS_DE_IMAGEN_ACEPTADOS as readonly string[]).includes(tipo.toLowerCase());
}

const LARGO_MINIMO_DEL_NOMBRE = 2;
const LARGO_MAXIMO_DEL_NOMBRE = 80;
const LARGO_MAXIMO_DE_CIUDAD = 80;

/** Un telefono con o sin indicativo y con los separadores que la gente escribe. */
const TELEFONO = /^\+?\d{7,15}$/;
const SEPARADORES = /[\s().-]/g;

/**
 * Vacio y ausente son lo mismo: la persona no quiere tener ese dato.
 *
 * <p>Se unifica aqui, en el borde, para que el resto del camino tenga una sola
 * forma de decirlo. Sin esto, borrar la ciudad desde un formulario seria
 * imposible: el campo vaciado llegaria como cadena vacia y no como ausencia.
 */
export function comoDatoOpcional(valor: string): string | null {
  const limpio = valor.trim();
  return limpio === '' ? null : limpio;
}

export function elNombreEsValido(valor: string): boolean {
  const limpio = valor.trim();
  return limpio.length >= LARGO_MINIMO_DEL_NOMBRE && limpio.length <= LARGO_MAXIMO_DEL_NOMBRE;
}

export function laCiudadEsValida(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || opcional.length <= LARGO_MAXIMO_DE_CIUDAD;
}

/**
 * La misma regla que el dominio del servidor, que es quien decide. Aqui solo
 * evita un viaje y un mensaje tardio.
 *
 * <p>No se valida contra el plan de numeracion colombiano: un vendedor puede
 * tener un numero de otro pais y este dato no enruta llamadas, solo permite que
 * alguien le escriba.
 */
export function elTelefonoEsValido(valor: string): boolean {
  const opcional = comoDatoOpcional(valor);
  return opcional === null || TELEFONO.test(opcional.replace(SEPARADORES, ''));
}

/**
 * Pedir el cambio al correo que ya se tiene no es un error, pero tampoco hay
 * nada que hacer: se evita el viaje y se dice en la pantalla.
 */
export function esElMismoCorreo(escrito: string, actual: string): boolean {
  return escrito.trim().toLowerCase() === actual.trim().toLowerCase();
}
