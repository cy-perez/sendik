import type { Color, Condition, SizeSystem } from '../../../shared/domain/listing';

/**
 * Lo que se pidió: el texto, los filtros y el orden. HU-014.
 *
 * <p>TypeScript puro y sin Angular, porque es una regla y no una pantalla: pasar de una
 * dirección a unos criterios y volver es la mecánica del criterio 15 —«la dirección los
 * lleva, y abrirla en otra pestaña devuelve el mismo resultado»— y se prueba sin TestBed.
 *
 * <p><strong>La categoría no está aquí.</strong> Vive en la ruta —`/catalogo/:familia`— y
 * no en la cadena de consulta, porque una categoría es una página que se comparte y se
 * indexa, y estos criterios son justo lo que **no** se indexa (criterio 24).
 */

/** Los cuatro de RN-088, con los nombres que el contrato publica. */
export const ORDENES = ['relevance', 'publishedAt,desc', 'price,asc', 'price,desc'] as const;

export type CatalogSort = (typeof ORDENES)[number];

/** Las cuatro de RN-064, en el orden en que se ofrecen. */
export const CONDICIONES: readonly Condition[] = ['NEW', 'LIKE_NEW', 'GOOD', 'WITH_FLAWS'];

/** Los quince de lista cerrada. El orden es el del glosario, no alfabético. */
export const COLORES: readonly Color[] = [
  'BLACK',
  'WHITE',
  'GRAY',
  'BEIGE',
  'BROWN',
  'RED',
  'PINK',
  'ORANGE',
  'YELLOW',
  'GREEN',
  'BLUE',
  'PURPLE',
  'GOLD',
  'SILVER',
  'MULTICOLOR',
];

export const SISTEMAS_DE_TALLA: readonly SizeSystem[] = [
  'ALPHA',
  'NUMERIC_CO',
  'WAIST_INCHES',
  'FOOTWEAR_CO',
  'ONE_SIZE',
];

export interface SearchCriteria {
  readonly q: string | null;
  readonly conditions: readonly Condition[];
  readonly sizeSystem: SizeSystem | null;
  readonly size: string | null;
  readonly colors: readonly Color[];
  readonly minPrice: number | null;
  readonly maxPrice: number | null;
  readonly sort: CatalogSort | null;
}

/** Sin nada puesto. Con esto, buscar y ver el catálogo son la misma cosa (criterio 8). */
export const SIN_CRITERIOS: SearchCriteria = {
  q: null,
  conditions: [],
  sizeSystem: null,
  size: null,
  colors: [],
  minPrice: null,
  maxPrice: null,
  sort: null,
};

/**
 * Lo que la dirección trae, ya validado contra las listas cerradas.
 *
 * <p><strong>Lo que no reconoce lo descarta</strong>, y aquí sí es lo correcto, al revés
 * que en el servidor: una dirección la puede haber recortado un chat, un correo o el
 * historial del navegador, y responder con un error a un enlace pegado a medias sería
 * castigar a quien no escribió nada. El servidor sigue rechazando lo que no entiende, que
 * es donde importa.
 */
export function desdeParametros(params: {
  get(nombre: string): string | null;
  getAll(nombre: string): readonly string[];
}): SearchCriteria {
  return {
    q: texto(params.get('q')),
    conditions: soloConocidos(params.getAll('condition'), CONDICIONES),
    sizeSystem: primeroConocido(params.get('sizeSystem'), SISTEMAS_DE_TALLA),
    size: texto(params.get('size')),
    colors: soloConocidos(params.getAll('color'), COLORES),
    minPrice: entero(params.get('minPrice')),
    maxPrice: entero(params.get('maxPrice')),
    sort: primeroConocido(params.get('sort'), ORDENES),
  };
}

/**
 * Los criterios como parámetros de la dirección.
 *
 * <p>Lo que está en blanco **no aparece**: una dirección con `?q=&color=` es la misma
 * búsqueda con peor aspecto, y además se comparte tal cual.
 */
export function aParametros(criterios: SearchCriteria): Record<string, string | string[]> {
  const parametros: Record<string, string | string[]> = {};

  if (criterios.q !== null) {
    parametros['q'] = criterios.q;
  }
  if (criterios.conditions.length > 0) {
    parametros['condition'] = [...criterios.conditions];
  }
  if (criterios.colors.length > 0) {
    parametros['color'] = [...criterios.colors];
  }
  if (criterios.sizeSystem !== null && criterios.size !== null) {
    parametros['sizeSystem'] = criterios.sizeSystem;
    parametros['size'] = criterios.size;
  }
  if (criterios.minPrice !== null) {
    parametros['minPrice'] = String(criterios.minPrice);
  }
  if (criterios.maxPrice !== null) {
    parametros['maxPrice'] = String(criterios.maxPrice);
  }
  if (criterios.sort !== null) {
    parametros['sort'] = criterios.sort;
  }

  return parametros;
}

/** Si no se pidió nada: ni texto, ni filtros, ni orden. */
export function estaVacio(criterios: SearchCriteria): boolean {
  return Object.keys(aParametros(criterios)).length === 0;
}

/**
 * Si la página **no se debe indexar**. Criterio 24.
 *
 * <p>El orden solo no cuenta: `?sort=price,asc` sobre el catálogo entero sigue siendo el
 * catálogo, ordenado de otra manera, y no una de las combinaciones casi infinitas que la
 * decisión 4 de la historia quiere mantener fuera del índice.
 */
export function esBusqueda(criterios: SearchCriteria): boolean {
  return !estaVacio({ ...criterios, sort: null });
}

/** Una ficha de filtro puesto, de las que se quitan una a una. */
export interface FiltroPuesto {
  /** Qué quita, para el nombre accesible del botón y para {@link sinFiltro}. */
  readonly campo: keyof SearchCriteria;
  /** Cuál valor, cuando el filtro admite varios. */
  readonly valor: string | null;
  /** Clave de Transloco de la etiqueta, y su parámetro si lo lleva. */
  readonly etiqueta: string;
  readonly parametro?: Record<string, string | number>;
}

/**
 * Los filtros puestos, para verlos siempre. RN-086.
 *
 * <p>El orden no es alfabético ni casual: es el mismo en que se ofrecen en el panel, para
 * que la ficha esté donde la vista ya está buscando.
 *
 * <p><strong>El texto no es una ficha.</strong> Vive en la caja de búsqueda, que se ve
 * siempre y ya lleva su forma de borrarlo; repetirlo aquí daría dos sitios donde quitar lo
 * mismo. El orden tampoco: no acota nada, así que quitarlo no cambia qué se ve.
 */
export function filtrosPuestos(criterios: SearchCriteria): readonly FiltroPuesto[] {
  const fichas: FiltroPuesto[] = [];

  for (const condicion of criterios.conditions) {
    fichas.push({
      campo: 'conditions',
      valor: condicion,
      etiqueta: `listing.condition.${condicion}`,
    });
  }
  for (const color of criterios.colors) {
    fichas.push({ campo: 'colors', valor: color, etiqueta: `listing.color.${color}` });
  }
  if (criterios.sizeSystem !== null && criterios.size !== null) {
    fichas.push({
      campo: 'size',
      valor: criterios.size,
      etiqueta: 'catalog.filters.chips.size',
      parametro: { talla: criterios.size },
    });
  }
  if (criterios.minPrice !== null || criterios.maxPrice !== null) {
    fichas.push({
      campo: 'minPrice',
      valor: null,
      etiqueta: etiquetaDePrecio(criterios),
      parametro: {
        desde: criterios.minPrice ?? 0,
        hasta: criterios.maxPrice ?? 0,
      },
    });
  }

  return fichas;
}

/** Los mismos criterios sin ese filtro. Es lo que hace el botón de cada ficha. */
export function sinFiltro(criterios: SearchCriteria, ficha: FiltroPuesto): SearchCriteria {
  switch (ficha.campo) {
    case 'conditions':
      return { ...criterios, conditions: criterios.conditions.filter((c) => c !== ficha.valor) };
    case 'colors':
      return { ...criterios, colors: criterios.colors.filter((c) => c !== ficha.valor) };
    case 'size':
      // Los dos juntos: una talla sin su sistema no filtra nada (RN-087).
      return { ...criterios, sizeSystem: null, size: null };
    case 'minPrice':
      return { ...criterios, minPrice: null, maxPrice: null };
    default:
      return criterios;
  }
}

/** Quitarlo todo menos el texto: es la salida del vacío honesto de RN-086. */
export function sinFiltros(criterios: SearchCriteria): SearchCriteria {
  return { ...SIN_CRITERIOS, q: criterios.q, sort: criterios.sort };
}

function etiquetaDePrecio(criterios: SearchCriteria): string {
  if (criterios.minPrice !== null && criterios.maxPrice !== null) {
    return 'catalog.filters.chips.priceBetween';
  }
  return criterios.minPrice !== null
    ? 'catalog.filters.chips.priceFrom'
    : 'catalog.filters.chips.priceTo';
}

function texto(crudo: string | null): string | null {
  const limpio = crudo?.trim() ?? '';
  return limpio === '' ? null : limpio;
}

/** Entero de pesos y nada más: RN-029 tampoco admite decimales aquí. */
function entero(crudo: string | null): number | null {
  if (crudo === null || crudo.trim() === '') {
    return null;
  }
  const numero = Number(crudo);
  return Number.isSafeInteger(numero) && numero >= 0 ? numero : null;
}

function soloConocidos<T extends string>(crudos: readonly string[], conocidos: readonly T[]): T[] {
  return crudos.filter((crudo): crudo is T => (conocidos as readonly string[]).includes(crudo));
}

function primeroConocido<T extends string>(
  crudo: string | null,
  conocidos: readonly T[],
): T | null {
  return crudo !== null && (conocidos as readonly string[]).includes(crudo) ? (crudo as T) : null;
}
