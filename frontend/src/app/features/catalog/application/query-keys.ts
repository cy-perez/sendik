/**
 * Claves de consulta del catálogo público.
 *
 * Centralizadas y nunca como arreglo literal suelto (frontend/CLAUDE.md).
 *
 * <p>`categories` es **la misma clave** que usa el formulario de publicar, y a propósito:
 * es el mismo recurso, el mismo endpoint y los mismos treinta y siete nombres. Compartir
 * la entrada de caché hace que ir de `/publicar` a `/catalogo` no vuelva a pedir el árbol.
 * No crea acoplamiento entre funcionalidades —ninguna importa de la otra—, solo evita
 * pedir dos veces lo que no cambia.
 *
 * <p>`list` depende de la categoría porque cada una es un listado distinto: con una clave
 * común, abrir «Camisas» pintaría por un instante lo que quedó de «Todo».
 */
export const queryKeys = {
  categories: ['catalog', 'categories'] as const,
  /**
   * Un listado, y **qué se le pidió**. HU-014.
   *
   * <p>Los criterios entran en la clave porque cada búsqueda es un listado distinto: con
   * una clave común, escribir en la caja pintaría un instante los resultados de lo
   * anterior, y volver atrás devolvería lo que ya no se está pidiendo. Van serializados
   * como la dirección los lleva, así que dos peticiones equivalentes comparten entrada.
   */
  list: (categoria: string | null, criterios = '') =>
    ['catalog', 'public', 'list', categoria ?? 'todo', criterios] as const,
  /**
   * Una publicación, y **desde qué perspectiva se pidió**.
   *
   * <p>El segundo tramo no es un capricho. `GET /listings/{id}` responde una forma u otra
   * según quién pregunte, así que la respuesta para un moderador y la de un visitante son
   * dos cosas distintas guardadas bajo el mismo nombre. Sin distinguirlas pasa esto: al
   * cargar la página, la consulta sale antes de que la cookie de refresco devuelva la
   * sesión, cachea la forma pública —sin estado— y no vuelve a pedirla nunca; un moderador
   * que abre una ficha por su dirección no ve la acción de bajarla, y al recargar tampoco.
   *
   * <p>Para quien no modera la clave no cambia entre el servidor y el cliente, así que el
   * catálogo público no pide nada de más: solo se vuelve a pedir para quien sí.
   */
  /**
   * Las dos entradas de una misma publicación, para invalidarlas juntas.
   *
   * <p>Hace falta porque {@link one} devuelve una clave **completa** y no un prefijo: al
   * bajar una publicación hay que tirar tanto la copia pública como la del moderador, y
   * pasar `one(id)` invalida solo la primera. El síntoma era que quien acababa de bajarla
   * seguía viendo la acción de bajarla.
   */
  anyOne: (id: string) => ['catalog', 'public', 'listing', id] as const,
  one: (id: string, comoModerador = false) =>
    ['catalog', 'public', 'listing', id, comoModerador ? 'moderacion' : 'publica'] as const,
  seller: (id: string) => ['catalog', 'public', 'seller', id] as const,
  sellerListings: (id: string) => ['catalog', 'public', 'seller', id, 'listings'] as const,
  /**
   * El sello de un vendedor, para quien modera. HU-010.
   *
   * <p>Cuelga del vendedor y no de la verificación, y es a propósito: la pantalla parte
   * del identificador del perfil y no conoce el de la verificación hasta que llega la
   * respuesta. Con la clave puesta en el identificador que sí se tiene, revocar puede
   * invalidar las dos entradas de esa persona sin volver a preguntar cuál era.
   */
  verification: (vendedor: string) => ['catalog', 'moderation', 'verification', vendedor] as const,
  /**
   * La lista propia de favoritos. HU-011.
   *
   * <p>No lleva de quién es, y no hace falta: la ruta responde siempre la de quien tiene
   * el token, así que no hay dos listas que distinguir dentro de una misma sesión. Al
   * cerrar sesión, `QueryClient` se limpia y con él esta entrada.
   */
  favorites: ['catalog', 'favorites'] as const,
  /**
   * El estado del control para una publicación concreta.
   *
   * <p>Entrada aparte de la de la ficha a propósito. `one(id)` guarda lo que responde
   * `GET /listings/{id}`, que es igual para todo el mundo y se renderiza en el servidor;
   * esto es de la sesión y se pide desde el navegador. Con una sola clave, el estado del
   * favorito viajaría dentro del HTML servido y la ficha dejaría de ser pública.
   */
  favorite: (id: string) => ['catalog', 'favorites', 'one', id] as const,
  /**
   * El carrito, y **de qué origen**. HU-015.
   *
   * <p>Los dos tramos del final no son adorno. El carrito de quien tiene sesión lo sirve
   * `GET /users/me/cart` y el de quien no la tiene se arma con los identificadores del
   * navegador: son dos respuestas distintas, y con una clave común entrar pintaría por un
   * instante el carrito anónimo dentro de la sesión recién abierta.
   *
   * <p>Los identificadores entran en la clave por lo mismo que los criterios de búsqueda
   * entran en la de `list`: cada conjunto es una petición distinta, y quitar un producto sin
   * sesión tiene que pedir de nuevo en vez de servir lo de antes.
   */
  cart: (haySesion: boolean, ids: readonly string[]) =>
    ['catalog', 'cart', haySesion ? 'sesion' : 'navegador', ids.join(',')] as const,
  /**
   * El prefijo de todo lo del carrito, para invalidarlo junto.
   *
   * <p>Hace falta porque {@link cart} devuelve una clave completa: tras agregar o quitar hay
   * que tirar el carrito sea cual sea el origen con el que se pidió, y pasar `cart(...)`
   * invalidaría solo el que coincida. Es el mismo problema que resolvió `anyOne`.
   */
  cartAny: ['catalog', 'cart'] as const,
  /**
   * El estado del control para una publicación concreta.
   *
   * <p>Entrada aparte de la de la ficha, por lo mismo que la de favoritos: `one(id)` guarda
   * lo que responde `GET /listings/{id}`, que es igual para todo el mundo y se renderiza en
   * el servidor; esto es de la sesión y se pide desde el navegador.
   */
  cartItem: (id: string) => ['catalog', 'cart', 'one', id] as const,
} as const;
