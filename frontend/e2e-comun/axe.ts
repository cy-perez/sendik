import type { AxeBuilder } from '@axe-core/playwright';

/**
 * Lo que las dos suites comparten para auditar con axe.
 *
 * <p>Vive fuera de `e2e/` y de `e2e-completo/` a proposito. Las dos suites no se mezclan
 * -necesitan cosas distintas y son dos trabajos distintos en integracion continua- pero el
 * **nivel que se audita** no puede ser dos cosas: con la lista de etiquetas escrita en cada
 * sitio, subir el objetivo en uno y olvidarlo en el otro deja media auditoria en la version
 * vieja y las dos en verde.
 *
 * <p>Ninguno de los dos `testDir` apunta aqui, asi que este archivo no se recoge como
 * prueba.
 */

/**
 * WCAG 2.2 nivel AA, que es el objetivo del proyecto, con los niveles anteriores incluidos
 * porque cada version se apoya en la anterior.
 *
 * <p>Sin `best-practice`: son recomendaciones de Deque, no criterios de la norma, y
 * mezclarlas obligaria a discutir cual se acata cada vez que falle una.
 */
export const ETIQUETAS_WCAG = ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa', 'wcag22aa'];

/** Los dos modos, con el valor que espera la cookie que lee el servidor. */
export const MODOS = [
  { modo: 'claro', cookie: 'light', atributo: 'claro' },
  { modo: 'oscuro', cookie: 'dark', atributo: 'oscuro' },
] as const;

/**
 * El informe que se lee cuando falla.
 *
 * <p>Playwright imprime el mensaje de la asercion, y una lista de objetos serializados no
 * dice donde esta el problema: lo que sirve es la regla y el selector del nodo que la
 * incumple.
 */
export function informe(
  violaciones: Awaited<ReturnType<AxeBuilder['analyze']>>['violations'],
): string {
  return violaciones
    .map((violacion) => {
      const nodos = violacion.nodes.map((nodo) => `      ${nodo.target.join(' ')}`).join('\n');
      return `  ${violacion.id} [${violacion.impact ?? 'sin impacto'}]: ${violacion.help}\n${nodos}`;
    })
    .join('\n');
}
