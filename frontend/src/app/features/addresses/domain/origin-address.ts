/**
 * La dirección de origen del vendedor. HU-017.
 *
 * <p>TypeScript puro, sin Angular. Es la pareja de la libreta de direcciones: aquella dice a
 * dónde llega lo que alguien compra y esta dice desde dónde sale lo que alguien vende. Por
 * eso vive en la misma funcionalidad y reutiliza sus reglas de campo: la línea, el
 * complemento, las indicaciones y el código postal se miden igual.
 *
 * <p><strong>Una por cuenta y sin identificador</strong>: la cuenta es la clave, y guardar
 * otra reemplaza la que había.
 *
 * <p><strong>Sin nombre ni teléfono propios.</strong> El remitente es el titular de la
 * cuenta (RN-078) y sus datos son los del perfil; aquí solo se leen.
 */

/** El origen guardado, tal como lo devuelve la API. */
export interface OriginAddress {
  readonly departmentCode: string;
  readonly departmentName: string;
  readonly municipalityCode: string;
  readonly municipalityName: string;
  /** Falso cuando el DANE suprimió ese municipio: se lee igual y al editar hay que elegir otro. */
  readonly municipalityActive: boolean;
  readonly line: string;
  readonly complement: string | null;
  readonly instructions: string | null;
  readonly postalCode: string | null;
  /** El remitente, tomado del perfil. El teléfono puede faltar si se quitó después. */
  readonly senderName: string;
  readonly senderPhone: string | null;
  readonly savedAt: string;
}

/** Lo que se manda al guardar. Se manda entero, también lo que quedó vacío. */
export interface OriginDraft {
  readonly municipalityCode: string;
  readonly line: string;
  readonly complement: string | null;
  readonly instructions: string | null;
  readonly postalCode: string | null;
}

/**
 * Quién figura como remitente: el titular de la cuenta, con lo que tenga en el perfil.
 *
 * <p>Se lee del perfil y no se escribe aquí. Sin teléfono no se puede guardar el origen
 * (criterio 5), y la pantalla lo dice antes de que nadie escriba nada.
 */
export interface Sender {
  readonly name: string;
  readonly phone: string | null;
}

/** Criterio 5: un remitente sin teléfono no es remitente. */
export function elRemitenteEstaCompleto(remitente: Sender): boolean {
  return remitente.phone !== null && remitente.phone.trim() !== '';
}

/** Cómo se lee el municipio del origen: siempre con su departamento al lado. */
export function comoSeLeeElOrigen(origen: OriginAddress): string {
  return `${origen.municipalityName}, ${origen.departmentName}`;
}
