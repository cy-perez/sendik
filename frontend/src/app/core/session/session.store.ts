import { computed, inject, Injectable, signal } from '@angular/core';
import { QueryClient } from '@tanstack/angular-query-experimental';

import type { Session } from './session';

/**
 * La sesion vive en memoria y solo en memoria.
 *
 * <p>No va a localStorage ni a sessionStorage: cualquier script inyectado en la
 * pagina podria leer el token de ahi. Al recargar se pierde a proposito y se
 * recupera con el token de refresco, que viaja en una cookie HttpOnly que el
 * JavaScript no puede ver. Ver docs/arquitectura/contrato-api.md.
 *
 * <p>Sustituye al anterior AccessTokenStore, que solo guardaba la cadena del
 * token. El refresco devuelve la sesion completa y el usuario cambia con ella
 * (al verificar el correo, por ejemplo): con dos almacenes separados uno de los
 * dos quedaba viejo sin que nada lo delatara.
 */
/**
 * Los tres estados en que puede estar la sesion.
 *
 * <p>El tercero, `desconocida`, es el que suele faltar y el que se nota. Al
 * cargar la pagina el token vive en memoria y por tanto no existe todavia: hay
 * que preguntarle al servidor con la cookie de refresco. Sin este estado, ese
 * rato se pinta como "no hay sesion", y a quien si la tiene le aparece "Entrar"
 * y despues su nombre. Ese cambio bajo el cursor es peor que esperar un momento.
 */
export type SessionStatus = 'desconocida' | 'anonima' | 'abierta';

@Injectable({ providedIn: 'root' })
export class SessionStore {
  private readonly consultas = inject(QueryClient);

  private readonly sesion = signal<Session | null>(null);

  /**
   * Si ya se sabe la respuesta, sea cual sea. Empieza en falso: nadie ha
   * preguntado todavia. En el servidor se queda asi, y es correcto, porque el
   * renderizado no tiene la cookie de nadie.
   */
  private readonly resuelta = signal(false);

  readonly current = this.sesion.asReadonly();

  readonly status = computed<SessionStatus>(() => {
    if (!this.resuelta()) {
      return 'desconocida';
    }
    return this.sesion() === null ? 'anonima' : 'abierta';
  });

  readonly isAuthenticated = computed(() => this.sesion() !== null);

  readonly user = computed(() => this.sesion()?.user ?? null);

  /**
   * Falso tambien cuando no hay sesion. Quien pregunta por el aviso de
   * verificacion pendiente comprueba antes que haya alguien dentro; devolver
   * "no verificado" a un visitante anonimo seria mentirle a esa comprobacion.
   */
  readonly emailVerified = computed(() => this.sesion()?.user.emailVerified ?? false);

  token(): string | null {
    return this.sesion()?.accessToken ?? null;
  }

  set(sesion: Session): void {
    this.sesion.set(sesion);
    this.resuelta.set(true);
  }

  /**
   * Cambia el nombre visible sin tocar el token. Criterio 21.
   *
   * <p>Lo que se ve en la cabecera sale de aqui. Al guardar el perfil, dejarlo
   * con el nombre anterior hasta la siguiente recarga haria dudar de si se
   * guardo, y esa duda lleva a guardar otra vez.
   *
   * <p>No abre sesion: si no habia ninguna, no hay nada que renombrar.
   */
  renombrar(displayName: string): void {
    this.sesion.update((actual) =>
      actual === null ? null : { ...actual, user: { ...actual.user, displayName } },
    );
  }

  /**
   * Sin sesion, y ya se sabe.
   *
   * <p>Sirve para las dos cosas que acaban igual: cerrar sesion y descubrir que
   * no habia ninguna que recuperar. En ambos casos la respuesta pasa a ser
   * conocida, que es lo que saca a la interfaz del estado de espera.
   *
   * <p><strong>Y si habia sesion, se lleva por delante lo que el servidor habia
   * respondido.</strong> El perfil, las sesiones abiertas y la lista de favoritos son
   * datos privados que TanStack conserva en la cache de la pestana: sin borrarlos, quien
   * entre despues en ese mismo navegador -sin recargar- los ve un instante antes de que
   * llegue la respuesta suya, porque la cache se sirve primero y se revalida despues. En
   * un equipo compartido «cerre sesion» tiene que significar que no queda nada
   * recuperable (docs/operacion/datos-personales.md), y RN-070 dice que los favoritos no
   * los ve nadie mas que quien los marco.
   *
   * <p><strong>Aqui y no en cada sitio que cierra sesion.</strong> Hay tres: el boton de
   * salir, el cierre de cuenta y el refresco que caduca. El primer intento lo puso solo en
   * el boton y los otros dos se quedaron fuera; centralizado, cualquier camino que acabe
   * sin sesion lo arrastra.
   *
   * <p><strong>Solo cuando de verdad habia sesion.</strong> Este metodo lo llama tambien
   * el arranque de cada visita anonima al descubrir que no hay nada que recuperar, y
   * purgar ahi tiraria la cache que llega del renderizado en servidor: cada pagina
   * publica volveria a pedir lo que acababa de recibir dentro del HTML.
   */
  clear(): void {
    const habiaSesion = this.sesion() !== null;

    this.sesion.set(null);
    this.resuelta.set(true);

    if (habiaSesion) {
      this.consultas.removeQueries();

      // **Y la caché de mutaciones, que es otra.** `removeQueries` solo vacía la de
      // consultas, así que el resultado de la última mutación sobrevivía al cierre: la
      // fusión del carrito guarda ahí el carrito fusionado entero de quien acaba de salir,
      // con su recuento de lo que no entró, y `CartStore` es de raíz. La siguiente persona
      // que entrara en esa pestaña veía un aviso sobre productos de otra.
      //
      // El javadoc de arriba promete que no queda nada recuperable. Con una sola de las dos
      // cachés no era cierto.
      this.consultas.getMutationCache().clear();
    }
  }
}
