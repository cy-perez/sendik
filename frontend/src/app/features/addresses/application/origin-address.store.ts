import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import type { OriginAddress, OriginDraft, Sender } from '../domain/origin-address';
import { AddressesApi } from '../infrastructure/addresses.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la dirección de origen. HU-017.
 *
 * <p>Separado de `AddressesStore` porque es otro dato con otro ciclo: uno solo, sin
 * predeterminada, sin lista. Lo que sí comparte con la libreta —los dos selectores de la
 * división— lo pide a través de aquel store, que es quien los tiene.
 *
 * <p><strong>Nada de aquí se renderiza en el servidor.</strong> Es dato personal de quien
 * mira: las consultas están deshabilitadas mientras la sesión no sea `abierta`, y en el
 * servidor no lo es nunca.
 *
 * <p><strong>La ciudad del perfil cambia cuando el origen cambia</strong> (criterios 11 y
 * 12), y este store no toca la caché del perfil: es de otra funcionalidad y
 * `features/x` no importa de `features/y`. No hace falta: la consulta del perfil va con
 * `staleTime: 0` y se vuelve a pedir al entrar en `/mi-cuenta`.
 */
@Injectable({ providedIn: 'root' })
export class OriginAddressStore {
  private readonly api = inject(AddressesApi);
  private readonly sesion = inject(SessionStore);
  private readonly consultas = inject(QueryClient);

  /** Si alguien está mirando la pantalla. Sin esto, el almacén de raíz pediría el origen siempre. */
  private readonly mirando = signal(false);

  readonly haySesion = computed(() => this.sesion.status() === 'abierta');
  readonly sesionResuelta = computed(() => this.sesion.status() !== 'desconocida');

  readonly origen = injectQuery(() => ({
    queryKey: queryKeys.origin,
    queryFn: () => this.api.origen(),
    enabled: this.mirando() && this.haySesion(),
    staleTime: 0,
    retry: false,
  }));

  /**
   * El remitente, leído del perfil cada vez que se abre la pantalla.
   *
   * <p>Sin frescura: el teléfono se pone en `/mi-cuenta` y la persona vuelve aquí a
   * guardar; un remitente en caché seguiría diciendo que falta.
   */
  readonly remitente = injectQuery(() => ({
    queryKey: queryKeys.sender,
    queryFn: () => this.api.remitente(),
    enabled: this.mirando() && this.haySesion(),
    staleTime: 0,
    retry: false,
  }));

  /**
   * Guardar escribe lo que devolvió el servidor en la caché: es exactamente lo que hay en
   * la base, con los nombres del municipio y del departamento ya puestos.
   */
  readonly guardado = injectMutation(() => ({
    mutationFn: (datos: OriginDraft) => this.api.guardarOrigen(datos),
    onSuccess: (guardado: OriginAddress) => {
      this.consultas.setQueryData(queryKeys.origin, guardado);
    },
  }));

  readonly borrado = injectMutation(() => ({
    mutationFn: () => this.api.borrarOrigen(),
    onSuccess: () => {
      this.consultas.setQueryData(queryKeys.origin, null);
    },
  }));

  /** `undefined` mientras no se sabe; `null` cuando se sabe que no hay. */
  readonly actual = computed<OriginAddress | null | undefined>(() => this.origen.data());

  readonly quienEnvia = computed<Sender | null>(() => this.remitente.data() ?? null);

  readonly guardando = computed(() => this.guardado.isPending());
  readonly borrando = computed(() => this.borrado.isPending());

  abrir(mirando: boolean): void {
    this.mirando.set(mirando);
  }
}
