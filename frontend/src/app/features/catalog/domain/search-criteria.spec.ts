import { describe, expect, it } from 'vitest';

import {
  aParametros,
  desdeParametros,
  esBusqueda,
  estaVacio,
  filtrosPuestos,
  SIN_CRITERIOS,
  sinFiltro,
  sinFiltros,
  type FiltroPuesto,
  type SearchCriteria,
} from './search-criteria';

/** La ficha que hay en esa posición, o revienta: una prueba no navega sobre indefinidos. */
function ficha(criterios: SearchCriteria, posicion = 0): FiltroPuesto {
  const encontrada = filtrosPuestos(criterios)[posicion];
  if (encontrada === undefined) {
    throw new Error(`No hay ficha en la posición ${posicion}`);
  }
  return encontrada;
}

/** Un `ParamMap` de mentira, que es todo lo que estas funciones necesitan. */
function parametros(crudos: Record<string, string | string[]>) {
  return {
    get: (nombre: string) => {
      const valor = crudos[nombre];
      return valor === undefined ? null : Array.isArray(valor) ? (valor[0] ?? null) : valor;
    },
    getAll: (nombre: string) => {
      const valor = crudos[nombre];
      return valor === undefined ? [] : Array.isArray(valor) ? valor : [valor];
    },
  };
}

describe('los criterios de búsqueda', () => {
  describe('leídos de la dirección', () => {
    it('lee el texto, los filtros y el orden', () => {
      const criterios = desdeParametros(
        parametros({
          q: 'camisa de lino',
          condition: ['NEW', 'LIKE_NEW'],
          color: ['BLUE'],
          sizeSystem: 'ALPHA',
          size: 'M',
          minPrice: '50000',
          maxPrice: '100000',
          sort: 'price,asc',
        }),
      );

      expect(criterios).toEqual({
        q: 'camisa de lino',
        conditions: ['NEW', 'LIKE_NEW'],
        colors: ['BLUE'],
        sizeSystem: 'ALPHA',
        size: 'M',
        minPrice: 50000,
        maxPrice: 100000,
        sort: 'price,asc',
      });
    });

    it('no trae nada de una dirección desnuda', () => {
      expect(desdeParametros(parametros({}))).toEqual(SIN_CRITERIOS);
    });

    /**
     * Descartar y no fallar: una dirección la puede haber recortado un chat o un correo, y
     * responder con un error a un enlace pegado a medias castigaría a quien no lo escribió.
     * El servidor sigue rechazando lo que no entiende, que es donde importa.
     */
    it('descarta los valores que no están en su lista cerrada', () => {
      const criterios = desdeParametros(
        parametros({ color: ['AZUL', 'BLUE'], condition: ['SEMINUEVA'], sort: 'favorites,desc' }),
      );

      expect(criterios.colors).toEqual(['BLUE']);
      expect(criterios.conditions).toEqual([]);
      expect(criterios.sort).toBeNull();
    });

    it('descarta un precio que no es un entero de pesos', () => {
      const criterios = desdeParametros(parametros({ minPrice: '50000.5', maxPrice: '-1' }));

      expect(criterios.minPrice).toBeNull();
      expect(criterios.maxPrice).toBeNull();
    });

    it('trata el texto en blanco como si no hubiera texto', () => {
      expect(desdeParametros(parametros({ q: '   ' })).q).toBeNull();
    });
  });

  describe('escritos en la dirección', () => {
    /** Criterio 15: lo que se pidió va y vuelve igual. */
    it('vuelve a leerse igual que se escribió', () => {
      const criterios: SearchCriteria = {
        q: 'camisa',
        conditions: ['NEW'],
        colors: ['BLUE', 'GREEN'],
        sizeSystem: 'ALPHA',
        size: 'M',
        minPrice: 50000,
        maxPrice: null,
        sort: 'price,desc',
      };

      expect(desdeParametros(parametros(aParametros(criterios)))).toEqual(criterios);
    });

    it('no escribe lo que está vacío', () => {
      expect(aParametros(SIN_CRITERIOS)).toEqual({});
    });

    /** Una talla sin su sistema no filtra nada, así que no viaja sola (RN-087). */
    it('no escribe la talla sin su sistema', () => {
      expect(aParametros({ ...SIN_CRITERIOS, size: 'M' })).toEqual({});
      expect(aParametros({ ...SIN_CRITERIOS, sizeSystem: 'ALPHA' })).toEqual({});
    });
  });

  describe('qué se indexa', () => {
    /** Criterio 24: las combinaciones de filtros no se indexan. */
    it('con texto o con un filtro puesto, es búsqueda', () => {
      expect(esBusqueda({ ...SIN_CRITERIOS, q: 'camisa' })).toBe(true);
      expect(esBusqueda({ ...SIN_CRITERIOS, colors: ['BLUE'] })).toBe(true);
    });

    /**
     * El orden solo no lo es: el catálogo ordenado de otra manera sigue siendo el catálogo,
     * y no una de las combinaciones casi infinitas que la decisión 4 quiere fuera del índice.
     */
    it('con solo el orden puesto, no es búsqueda', () => {
      expect(esBusqueda({ ...SIN_CRITERIOS, sort: 'price,asc' })).toBe(false);
      expect(estaVacio({ ...SIN_CRITERIOS, sort: 'price,asc' })).toBe(false);
    });
  });

  describe('las fichas de filtro puesto', () => {
    /** RN-086: un filtro activo e invisible se lee como «Sendik no tiene nada». */
    it('saca una ficha por cada valor de cada filtro', () => {
      const fichas = filtrosPuestos({
        ...SIN_CRITERIOS,
        conditions: ['NEW'],
        colors: ['BLUE', 'GREEN'],
        sizeSystem: 'ALPHA',
        size: 'M',
        minPrice: 50000,
      });

      expect(fichas).toHaveLength(5);
      expect(fichas.map((ficha) => ficha.campo)).toEqual([
        'conditions',
        'colors',
        'colors',
        'size',
        'minPrice',
      ]);
    });

    /**
     * El texto no es una ficha: vive en la caja, que se ve siempre y ya lleva su forma de
     * borrarlo. Dos sitios para quitar lo mismo es un sitio de más.
     */
    it('no saca ficha del texto ni del orden', () => {
      expect(filtrosPuestos({ ...SIN_CRITERIOS, q: 'camisa', sort: 'price,asc' })).toHaveLength(0);
    });

    it('quita un solo valor y deja los demás', () => {
      const criterios: SearchCriteria = { ...SIN_CRITERIOS, colors: ['BLUE', 'GREEN'] };
      const azul = ficha(criterios);

      expect(sinFiltro(criterios, azul).colors).toEqual(['GREEN']);
    });

    /** La talla se va entera: sin su sistema no significa nada. */
    it('quita la talla con su sistema', () => {
      const criterios: SearchCriteria = { ...SIN_CRITERIOS, sizeSystem: 'ALPHA', size: 'M' };
      const talla = ficha(criterios);

      expect(sinFiltro(criterios, talla)).toEqual(SIN_CRITERIOS);
    });

    it('quita el rango de precio entero aunque solo tuviera un extremo', () => {
      const criterios: SearchCriteria = { ...SIN_CRITERIOS, minPrice: 50000, maxPrice: 90000 };
      const precio = ficha(criterios);

      expect(sinFiltro(criterios, precio).minPrice).toBeNull();
      expect(sinFiltro(criterios, precio).maxPrice).toBeNull();
    });

    /** La salida del vacío honesto quita los filtros, no lo que se estaba buscando. */
    it('quitarlos todos conserva el texto y el orden', () => {
      const criterios: SearchCriteria = {
        ...SIN_CRITERIOS,
        q: 'camisa',
        colors: ['BLUE'],
        sort: 'price,asc',
      };

      expect(sinFiltros(criterios)).toEqual({ ...SIN_CRITERIOS, q: 'camisa', sort: 'price,asc' });
    });
  });
});
