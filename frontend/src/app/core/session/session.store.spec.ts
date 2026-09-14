import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it } from 'vitest';

import type { Session } from './session';
import { SessionStore } from './session.store';

describe('SessionStore', () => {
  const SESION: Session = {
    accessToken: 'un-token',
    user: { email: 'ana@correo.co', displayName: 'Ana', emailVerified: false, roles: [] },
  };

  let almacen: SessionStore;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    almacen = TestBed.inject(SessionStore);
  });

  /**
   * Al arrancar nadie ha preguntado todavia. Es distinto de "no hay sesion": con
   * esa respuesta, la cabecera pintaria "Entrar" a quien si la tiene y se lo
   * cambiaria por su nombre un instante despues.
   */
  it('empieza sin saber si hay sesion', () => {
    expect(almacen.status()).toBe('desconocida');
    expect(almacen.isAuthenticated()).toBe(false);
    expect(almacen.token()).toBeNull();
  });

  it('queda abierta al guardar una sesion', () => {
    almacen.set(SESION);

    expect(almacen.status()).toBe('abierta');
    expect(almacen.isAuthenticated()).toBe(true);
    expect(almacen.token()).toBe('un-token');
    expect(almacen.user()?.email).toBe('ana@correo.co');
  });

  // clear() no es solo "no hay sesion": es "ya se pregunto y no la hay".
  it('queda anonima al limpiarse, no desconocida', () => {
    almacen.clear();

    expect(almacen.status()).toBe('anonima');
    expect(almacen.isAuthenticated()).toBe(false);
  });

  it('vuelve a anonima al cerrar una sesion abierta', () => {
    almacen.set(SESION);
    almacen.clear();

    expect(almacen.status()).toBe('anonima');
    expect(almacen.token()).toBeNull();
    expect(almacen.user()).toBeNull();
  });

  /**
   * Falso tambien sin sesion: quien pregunta por el aviso de verificacion
   * pendiente comprueba antes que haya alguien dentro, y devolver "no verificado"
   * a un visitante anonimo seria mentirle a esa comprobacion.
   */
  it('sabe si quien esta dentro modera, y no lo dice de nadie sin sesion', () => {
    expect(almacen.esModerador()).toBe(false);

    almacen.set(SESION);
    expect(almacen.esModerador()).toBe(false);

    almacen.set({ ...SESION, user: { ...SESION.user, roles: ['MODERATOR'] } });
    expect(almacen.esModerador()).toBe(true);
  });

  it('no dice que falta verificar cuando no hay nadie dentro', () => {
    expect(almacen.emailVerified()).toBe(false);

    almacen.set(SESION);
    expect(almacen.emailVerified()).toBe(false);

    almacen.set({ ...SESION, user: { ...SESION.user, emailVerified: true } });
    expect(almacen.emailVerified()).toBe(true);
  });
});
