import { inject, Injectable } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { ApiError } from '../../../core/http/api-error';
import { SessionStore } from '../../../core/session/session.store';
import { AuthApi } from '../infrastructure/auth.api';
import { queryKeys } from './query-keys';
import type { Credentials } from '../domain/credentials';
import type { PasswordReset, PasswordResetRequest } from '../domain/password-reset';
import type { ProfileEdit } from '../domain/profile';
import type { Registration } from '../domain/registration';

/**
 * Casos de uso de cuentas para la interfaz.
 *
 * <p>Envuelve TanStack Query: los componentes no ven la libreria, solo senales
 * de estado y una funcion que ejecutar (frontend/CLAUDE.md).
 *
 * <p>Un registro no es una consulta, es una mutacion: no se cachea, no se
 * reintenta solo y no se vuelve a ejecutar al volver a la pestana.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly sesion = inject(SessionStore);
  private readonly consultas = inject(QueryClient);

  readonly registration = injectMutation(() => ({
    mutationFn: (registro: Registration) => this.api.register(registro),
    // Sin reintentos: un registro repetido por la libreria hashearia la
    // contrasena dos veces en el servidor sin que nadie lo pidiera.
    retry: false,
  }));

  /** Criterio 9: al verificar se entra directamente, sin escribir la contrasena. */
  readonly verification = injectMutation(() => ({
    mutationFn: (token: string) => this.api.verifyEmail(token),
    retry: false,
    onSuccess: (verificado) => {
      this.sesion.set(verificado.session);
    },
  }));

  readonly resend = injectMutation(() => ({
    mutationFn: (expiredToken: string) => this.api.resendVerification(expiredToken),
    retry: false,
  }));

  /**
   * Criterio 10. Sin reintentos por partida doble: un reintento automatico
   * gastaria uno de los cinco intentos que RN-006 permite antes de bloquear, sin
   * que la persona hubiera escrito nada dos veces.
   */
  readonly login = injectMutation(() => ({
    mutationFn: (credenciales: Credentials) => this.api.login(credenciales),
    retry: false,
    onSuccess: (abierta) => {
      this.sesion.set(abierta);
      // Solo la otra: reset() sobre la mutacion que esta corriendo cancela los
      // callbacks que le paso quien la invoco, y el ingreso se quedaria sin
      // navegar. La suya la limpia la pantalla, que es la ultima en actuar.
      this.registration.reset();
    },
  }));

  /**
   * Criterio 16. La sesion local se limpia pase lo que pase.
   *
   * <p>Si la llamada falla, el servidor puede haber revocado igual y el navegador
   * no tiene forma de saberlo. Dejar la pantalla como si la persona siguiera
   * dentro es lo unico que seguro esta mal: pulso salir.
   */
  readonly logout = injectMutation(() => ({
    mutationFn: () => this.api.logout(),
    retry: false,
    onSettled: () => {
      // `clear()` se lleva tambien lo que el servidor habia respondido: el perfil, las
      // sesiones y la lista de favoritos. Vive alli y no aqui porque hay tres caminos que
      // acaban sin sesion -este, el cierre de cuenta y el refresco que caduca- y el primer
      // intento solo cubrio este.
      this.sesion.clear();
      this.olvidarLoEscrito();
    },
  }));

  /**
   * Criterio 19. Sin reintentos: el servidor responde igual exista o no el correo,
   * asi que un reintento automatico solo mandaria dos correos a quien si tiene
   * cuenta.
   */
  readonly passwordResetRequest = injectMutation(() => ({
    mutationFn: (peticion: PasswordResetRequest) => this.api.requestPasswordReset(peticion),
    retry: false,
  }));

  /**
   * Criterio 20. Al terminar se olvida lo escrito: la contrasena nueva no puede
   * quedarse en el estado de la mutacion, igual que no se queda la del ingreso.
   */
  readonly passwordReset = injectMutation(() => ({
    mutationFn: (cambio: PasswordReset) => this.api.resetPassword(cambio),
    retry: false,
  }));

  /**
   * Criterio 17. Es una consulta y no una mutacion: se puede volver a pedir sola
   * y no cambia nada del servidor.
   *
   * <p>Sin tiempo de frescura: una sesion cerrada desde otro dispositivo tiene
   * que desaparecer de la lista en cuanto se mire, no un minuto despues.
   */
  readonly sessions = injectQuery(() => ({
    queryKey: queryKeys.sessions,
    queryFn: () => this.api.sessions(),
    staleTime: 0,
    retry: false,
    // Sin sesion no se pregunta. Es una ruta autenticada: sin token la respuesta
    // solo puede ser 401, y pedirla igual gasta una peticion para descubrir algo
    // que ya se sabia. En el renderizado del servidor no hay sesion nunca
    // (session.store.ts), asi que alli esta consulta no llega a salir.
    //
    // **La senal se lee aqui, en las opciones, y no dentro de una funcion.**
    //
    // Escrito como `enabled: () => this.sesion.isAuthenticated()`, la lectura
    // ocurre cuando TanStack invoca esa funcion, fuera del ambito reactivo de
    // estas opciones: la senal no queda registrada como dependencia y la consulta
    // no se reactiva cuando la sesion llega mas tarde.
    //
    // Eso rompia /mi-cuenta por completo, no solo al recargar. Este almacen es de
    // raiz y `SessionMenu` lo inyecta desde app.html, asi que su observador nace
    // en **cada** carga de pagina, siempre antes de que termine la recuperacion de
    // la sesion por la cookie de refresco. La consulta nacia deshabilitada y se
    // quedaba asi para toda la vida de la aplicacion: el perfil y la lista de
    // sesiones no se cargaban nunca y la pantalla decia "Cargando tus datos" sin
    // fin. Ninguna prueba de componente lo veia porque todas ponen la sesion antes
    // de crear el componente; lo encontro la suite de extremo a extremo completa.
    //
    // Contrapartida asumida: al entrar, estas dos consultas salen aunque la
    // persona no vaya a /mi-cuenta. Son dos peticiones autenticadas por inicio de
    // sesion. Evitarlas exige sacar estas consultas del almacen de raiz para que
    // solo existan mientras la pantalla de cuenta este montada, y eso es un cambio
    // de estructura que no toca hacer dentro de esta correccion.
    enabled: this.sesion.isAuthenticated(),
  }));

  /** Criterio 17: cerrar una sesion concreta y refrescar la lista. */
  readonly sessionRevocation = injectMutation(() => ({
    mutationFn: (id: string) => this.api.revokeSession(id),
    retry: false,
    onSuccess: () => {
      void this.consultas.invalidateQueries({ queryKey: queryKeys.sessions });
    },
  }));

  /**
   * Criterio 21. El perfil es una consulta: no cambia nada y se puede volver a
   * pedir sola.
   *
   * <p>Sin tiempo de frescura, como las sesiones: un correo confirmado desde otro
   * dispositivo tiene que aparecer al mirar, no un minuto despues.
   *
   * <p><strong>Y desde HU-017 la ciudad depende de este cero.</strong> Guardar o borrar la
   * direccion de origen cambia la ciudad del perfil, y `features/addresses` no puede
   * invalidar esta consulta sin importar de `features/auth`: se apoya en que al volver a
   * `/mi-cuenta` se vuelve a pedir. Subir `staleTime` dejaria la ciudad vieja en pantalla.
   */
  readonly profile = injectQuery(() => ({
    queryKey: queryKeys.profile,
    queryFn: () => this.api.profile(),
    staleTime: 0,
    retry: false,
    // Igual que las sesiones: ruta autenticada, sin token no hay nada que pedir. Y
    // la senal se lee aqui y no dentro de una funcion, por el mismo motivo: ver la
    // nota larga en `sessions`.
    enabled: this.sesion.isAuthenticated(),
  }));

  /**
   * Criterio 21. Al guardar se refresca la consulta con lo que devolvio el
   * servidor, sin pedirlo otra vez: es exactamente lo que hay en la base.
   *
   * <p>Tambien se actualiza el nombre de la sesion en memoria. Es lo que ve la
   * cabecera, y dejarlo con el nombre viejo haria dudar de si se guardo.
   */
  readonly profileUpdate = injectMutation(() => ({
    mutationFn: (cambio: ProfileEdit) => this.api.updateProfile(cambio),
    retry: false,
    onSuccess: (guardado) => {
      this.consultas.setQueryData(queryKeys.profile, guardado);
      this.sesion.renombrar(guardado.displayName);
    },
  }));

  /**
   * Criterio 21: la foto de perfil.
   *
   * <p>Como al guardar el perfil, se refresca la consulta con lo que devolvio el
   * servidor en lugar de pedirlo otra vez: es exactamente lo que hay en la base, y
   * ademas evita que la foto tarde un viaje mas en aparecer.
   *
   * <p>Sin reintentos. Subir una imagen no es idempotente en coste: un reintento
   * automatico manda el archivo dos veces, y si el primero si llego, deja un
   * archivo huerfano.
   */
  readonly avatarUpload = injectMutation(() => ({
    mutationFn: (archivo: File) => this.api.uploadAvatar(archivo),
    retry: false,
    onSuccess: (guardado) => {
      this.consultas.setQueryData(queryKeys.profile, guardado);
    },
  }));

  /** Criterio 21: quitar la foto. */
  readonly avatarRemoval = injectMutation(() => ({
    mutationFn: () => this.api.removeAvatar(),
    retry: false,
    onSuccess: (guardado) => {
      this.consultas.setQueryData(queryKeys.profile, guardado);
    },
  }));

  /**
   * Criterio 21. Sin reintentos: el servidor responde igual este la direccion
   * libre u ocupada, asi que un reintento automatico solo mandaria dos correos a
   * quien si esta esperando el enlace.
   */
  readonly emailChangeRequest = injectMutation(() => ({
    mutationFn: (nuevoCorreo: string) => this.api.requestEmailChange(nuevoCorreo),
    retry: false,
  }));

  /** Criterio 21: consume el enlace del correo nuevo. De un solo uso. */
  readonly emailChangeConfirmation = injectMutation(() => ({
    mutationFn: (token: string) => this.api.confirmEmailChange(token),
    retry: false,
    onSuccess: () => {
      // El correo de la cuenta acaba de cambiar: lo que este en cache ya no es
      // cierto. Se invalida en vez de escribirlo porque esta pantalla no sabe
      // como quedo el resto del perfil.
      void this.consultas.invalidateQueries({ queryKey: queryKeys.profile });
    },
  }));

  /** Criterio 22. Devuelve el archivo tal cual lo genero el servidor. */
  readonly dataExport = injectMutation(() => ({
    mutationFn: () => this.api.exportData(),
    retry: false,
  }));

  /**
   * Criterio 23. No se reintenta nunca: cerrar una cuenta no se deshace, y un
   * reintento automatico de la libreria seria la peor forma posible de perderla.
   */
  readonly accountClosure = injectMutation(() => ({
    mutationFn: (confirmacion: string) => this.api.closeAccount(confirmacion),
    retry: false,
    onSuccess: () => {
      // La cuenta ya no existe: lo que quede en memoria solo puede confundir.
      this.sesion.clear();
      this.olvidarLoEscrito();
    },
  }));

  /** Criterio 13: el reenvio desde dentro, para quien entro sin verificar. */
  readonly emailVerificationRequest = injectMutation(() => ({
    mutationFn: () => this.api.requestEmailVerification(),
    retry: false,
  }));

  /**
   * Borra del estado de las mutaciones lo que la persona escribio.
   *
   * <p>TanStack conserva en cada mutacion sus {@code variables} y su
   * {@code data} mientras viva la pestana, y este almacen es de raiz: destruir
   * el formulario no borra esa copia. Sin esto, despues de entrar y salir,
   * {@code login.variables()} seguia devolviendo la contrasena en claro y
   * {@code login.data()} el token de la sesion anterior. En un equipo compartido
   * "cerre sesion" tiene que significar que no queda nada recuperable
   * (docs/operacion/datos-personales.md).
   *
   * <p>Es el ultimo cierre, no el unico: cada pantalla limpia lo suyo en cuanto
   * deja de necesitarlo. Esto recoge lo que quede.
   */
  private olvidarLoEscrito(): void {
    this.login.reset();
    this.registration.reset();
    this.passwordReset.reset();
  }

  /**
   * Traduce el fallo de una mutacion a una clave de Transloco.
   *
   * <p>El servidor nunca manda texto para mostrar: manda un codigo estable y el
   * texto sale de aqui (docs/arquitectura/contrato-api.md).
   */
  static claveDeError(error: unknown): string {
    return error instanceof ApiError ? error.translationKey : 'errors.fallback';
  }
}
