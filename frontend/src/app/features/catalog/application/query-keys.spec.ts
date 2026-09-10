import { describe, expect, it } from 'vitest';

import { queryKeys } from './query-keys';

/**
 * Las claves de consulta del catálogo. HU-014.
 *
 * <p>Existe por el criterio 22 visto desde el cliente. Los criterios entran en la clave del
 * listado para que cambiar el orden empiece una consulta nueva; si alguien quita ese
 * argumento, la caché se comparte, el siguiente «ver más» manda un cursor nacido bajo otro
 * orden y quien busca recibe un 400 en la cara. Nada lo afirmaba: la prueba de la pantalla
 * comprueba que los criterios llegan a la petición, que sigue siendo cierto con la clave
 * rota.
 */
describe('queryKeys', () => {
  it('separa dos listados que se pidieron distinto', () => {
    expect(queryKeys.list('camisas', 'sort=price%2Casc')).not.toEqual(
      queryKeys.list('camisas', 'sort=price%2Cdesc'),
    );
    expect(queryKeys.list('camisas', 'q=tenis')).not.toEqual(queryKeys.list('camisas', ''));
  });

  it('comparte la entrada cuando se pidió lo mismo', () => {
    expect(queryKeys.list('camisas', 'q=tenis')).toEqual(queryKeys.list('camisas', 'q=tenis'));
  });

  it('sigue separando por categoría, que es de HU-009', () => {
    expect(queryKeys.list('camisas', 'q=tenis')).not.toEqual(queryKeys.list('jeans', 'q=tenis'));
    expect(queryKeys.list(null, '')).not.toEqual(queryKeys.list('camisas', ''));
  });
});
