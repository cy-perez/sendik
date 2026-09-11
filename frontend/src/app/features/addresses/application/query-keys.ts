/**
 * Claves de consulta de la libreta de direcciones. HU-016.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 */
export const queryKeys = {
  /**
   * La libreta propia.
   *
   * <p>No lleva de quién es, y no hace falta: la ruta responde siempre la de quien tiene el
   * token, así que no hay dos libretas que distinguir dentro de una misma sesión. Al cerrar
   * sesión, `QueryClient` se limpia y con él esta entrada.
   */
  book: ['addresses', 'book'] as const,

  /**
   * Los departamentos. Una sola entrada para toda la sesión.
   *
   * <p>Cambian una vez cada varios años, así que aquí sí tiene sentido una frescura larga:
   * es lo contrario de la libreta, que es dato personal y cuanto menos viva en la caché,
   * mejor.
   */
  departments: ['addresses', 'departments'] as const,

  /**
   * Los municipios de un departamento.
   *
   * <p>El código entra en la clave porque cada departamento es una lista distinta: con una
   * clave común, cambiar de departamento pintaría por un instante los municipios del
   * anterior.
   */
  municipalities: (departamento: string) => ['addresses', 'municipalities', departamento] as const,
} as const;
