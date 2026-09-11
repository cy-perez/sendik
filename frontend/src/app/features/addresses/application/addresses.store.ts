import { computed, inject, Injectable, signal } from '@angular/core';
import { injectMutation, injectQuery, QueryClient } from '@tanstack/angular-query-experimental';

import { SessionStore } from '../../../core/session/session.store';
import type { AddressDraft, ShippingAddress } from '../domain/shipping-address';
import { AddressesApi } from '../infrastructure/addresses.api';
import { queryKeys } from './query-keys';

/**
 * El estado de la libreta de direcciones. HU-016.
 *
 * <p>Envuelve TanStack Query para que los componentes no vean la librería
 * (frontend/CLAUDE.md).
 *
 * <p><strong>Nada de aquí se renderiza en el servidor.</strong> Es dato personal de quien
 * mira, y el HTML servido no puede llevar dentro dónde vive alguien. Que las consultas
 * estén deshabilitadas mientras la sesión no sea `abierta` es lo que lo garantiza: en el
 * servidor no lo es nunca.
 *
 * <p>Ninguna consulta reintenta. Un 401 o un 404 son respuestas normales aquí y
 * reintentarlas tres veces solo retrasa lo que la pantalla ya sabe decir.
 */
@Injectable({ providedIn: 'root' })
export class AddressesStore {
  private readonly api = inject(AddressesApi);
  private readonly sesion = inject(SessionStore);
  private readonly consultas = inject(QueryClient);

  /** Qué departamento está elegido en el formulario. Lo fija el propio formulario. */
  private readonly departamento = signal<string | null>(null);

  /** Si alguien está mirando la pantalla. Sin esto, el almacén de raíz pediría la libreta siempre. */
  private readonly mirando = signal(false);

  /**
   * Si el formulario está abierto.
   *
   * <p>Separado de {@link mirando} a propósito: la división político-administrativa la
   * necesita el formulario y no la lista, así que quien solo mira sus direcciones no pide
   * treinta y tres departamentos que no va a usar. Lo enciende el propio formulario, que es
   * quien los consume; hacer que dependiera de la pantalla dejaría al formulario sin
   * opciones si algún día se abre desde otro sitio.
   */
  private readonly formularioAbierto = signal(false);

  readonly haySesion = computed(() => this.sesion.status() === 'abierta');

  /** Si ya se sabe la respuesta, sea cual sea. Es la diferencia entre «no sé» y «no hay nadie». */
  readonly sesionResuelta = computed(() => this.sesion.status() !== 'desconocida');

  readonly libreta = injectQuery(() => ({
    queryKey: queryKeys.book,
    queryFn: () => this.api.libreta(),
    enabled: this.mirando() && this.haySesion(),
    // Sin frescura: es dato personal y cambia porque su dueña lo cambia, a veces en otra
    // pestana. Cuanto menos viva en la cache, mejor
    // (docs/operacion/datos-personales.md).
    staleTime: 0,
    retry: false,
  }));

  /**
   * Los departamentos.
   *
   * <p>Frescura larga y no cero, al reves que la libreta: no es dato de nadie y cambia una
   * vez cada varios anos. Pedirlos de nuevo en cada apertura del formulario seria traer
   * treinta y tres filas que no han cambiado desde 1991.
   */
  readonly departamentos = injectQuery(() => ({
    queryKey: queryKeys.departments,
    queryFn: () => this.api.departamentos(),
    enabled: this.formularioAbierto() && this.haySesion(),
    staleTime: Infinity,
    retry: false,
  }));

  readonly municipios = injectQuery(() => ({
    queryKey: queryKeys.municipalities(this.departamento() ?? 'ninguno'),
    queryFn: () => this.api.municipios(this.departamento() ?? ''),
    enabled: this.departamento() !== null && this.haySesion(),
    staleTime: Infinity,
    retry: false,
  }));

  readonly agregado = injectMutation(() => ({
    mutationFn: (datos: AddressDraft) => this.api.agregar(datos),
    onSuccess: () => this.refrescarLaLibreta(),
  }));

  readonly edicion = injectMutation(() => ({
    mutationFn: ({ id, datos }: { id: string; datos: AddressDraft }) => this.api.editar(id, datos),
    onSuccess: () => this.refrescarLaLibreta(),
  }));

  /**
   * Las dos de la lista no refrescan solas, y quien las llama espera el refresco.
   *
   * <p>Es la diferencia con las dos del formulario. La pantalla necesita **leer la libreta
   * ya refrescada** para poder decir cuál quedó de predeterminada (criterio 11), y con una
   * invalidación en `onSuccess` eso no está garantizado: la mutación resuelve y la lectura
   * siguiente todavía ve la libreta vieja. Se vio en la prueba, que anunciaba el nombre de
   * la dirección recién borrada.
   */
  readonly borrado = injectMutation(() => ({
    mutationFn: (id: string) => this.api.quitar(id),
  }));

  readonly marcado = injectMutation(() => ({
    mutationFn: (id: string) => this.api.marcarPredeterminada(id),
  }));

  readonly direcciones = computed<readonly ShippingAddress[]>(() => this.libreta.data() ?? []);

  /**
   * Si hay una operación de la **lista** en curso, para no dejar pulsar dos veces.
   *
   * <p>No se usa para deshabilitar un botón en el mismo tic del clic: HU-011 enseñó que eso
   * mata el foco. Se usa para no mandar la segunda petición.
   */
  readonly ocupada = computed(() => this.borrado.isPending() || this.marcado.isPending());

  /**
   * Y si hay una del **formulario**.
   *
   * <p>Separadas, y se corrigió tras la revisión de accesibilidad: con una sola, borrar una
   * dirección desde la lista deshabilitaba el botón de envío del formulario —que sí usa
   * `[disabled]`— por una mutación que no era suya.
   */
  readonly guardando = computed(() => this.agregado.isPending() || this.edicion.isPending());

  abrirLibreta(mirando: boolean): void {
    this.mirando.set(mirando);
  }

  abrirFormulario(abierto: boolean): void {
    this.formularioAbierto.set(abierto);
    if (!abierto) {
      // Sin esto, cerrar el formulario y volver a abrirlo dejaria el municipio pedido para
      // el departamento de la vez anterior.
      this.departamento.set(null);
    }
  }

  elegirDepartamento(codigo: string | null): void {
    this.departamento.set(codigo);
  }

  /**
   * Vuelve a pedir la libreta entera después de cada escritura.
   *
   * <p><strong>Y no se toca la caché a mano</strong>, aunque las cuatro mutaciones sepan qué
   * cambiaron. Borrar la predeterminada mueve la marca a otra fila (criterio 11) y eso lo
   * decide el servidor: reconstruir aquí el estado resultante sería escribir por segunda vez
   * una regla que ya vive en un sitio, y lo que se ve en pantalla dejaría de ser lo que hay
   * guardado en cuanto las dos versiones se separaran.
   */
  private async refrescarLaLibreta(): Promise<void> {
    await this.consultas.invalidateQueries({ queryKey: queryKeys.book });
  }

  /**
   * Vuelve a pedir la libreta y **devuelve lo que llegó**.
   *
   * <p>La usa la pantalla después de quitar o de marcar, porque lo que dice a continuación
   * depende de lo que quedó guardado y no de lo que había antes.
   *
   * <p><strong>Devuelve la lista en vez de dejar que la lea la señal</strong>, y esa es la
   * parte que costó: `refetch()` resuelve antes de que `data()` se haya propagado, así que
   * leer la señal justo después seguía dando la libreta vieja —la pantalla anunciaba el
   * nombre de la dirección recién borrada—.
   */
  async refrescarLibreta(): Promise<readonly ShippingAddress[] | null> {
    const resultado = await this.libreta.refetch();
    // **Nulo cuando no se pudo, y no una lista vacía.** `refetch()` no rechaza: ante un
    // fallo resuelve sin datos, y devolver `[]` hacía que quien lo llama dedujera que la
    // libreta se quedó vacía.
    return resultado.isError ? null : (resultado.data ?? []);
  }
}
