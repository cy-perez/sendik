import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
  linkedSignal,
  output,
  signal,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import type { Color, Condition, SizeSystem } from '../../../shared/domain/listing';
import {
  COLORES,
  CONDICIONES,
  ORDENES,
  SISTEMAS_DE_TALLA,
  type CatalogSort,
  type SearchCriteria,
} from '../domain/search-criteria';

/**
 * El panel de filtros del catálogo. HU-014, criterios 9 a 14 y 16 a 18.
 *
 * <p><strong>Panel lateral en escritorio y hoja que se abre desde un botón en móvil</strong>,
 * y las dos son el mismo marcado: lo que cambia es el CSS. Dos plantillas serían dos sitios
 * donde olvidar un filtro.
 *
 * <p><strong>Cada cambio aplica.</strong> No hay botón de «aplicar» porque no hace falta
 * uno: los filtros van a la dirección y la lista se vuelve a pedir, igual que al navegar
 * entre categorías. Lo que sí tiene botón es el texto, que se escribe letra a letra.
 *
 * <p><strong>La talla se pide en dos partes</strong>, el sistema y el valor, porque una M
 * por letra y una 38 numérica no son comparables (RN-087). El valor va en un campo de texto
 * y no en una lista, por la misma razón que en el formulario de publicar: los valores de
 * cada escala están **sin confirmar** —lo anota `SizeSystem` en el dominio— y copiarlos aquí
 * los daría por buenos en dos sitios a la vez.
 *
 * <p>Nada de esto lleva acento: el bronce sigue apareciendo una sola vez por pantalla y en
 * la insignia de vendedor verificado.
 */
@Component({
  selector: 'sendik-catalog-filters',
  standalone: true,
  imports: [TranslocoPipe],
  templateUrl: './catalog-filters.html',
  styleUrl: './catalog-filters.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CatalogFilters {
  readonly criterios = input.required<SearchCriteria>();

  /** Cuántos filtros hay puestos, para decirlo en el botón que abre la hoja en móvil. */
  readonly puestos = input(0);

  readonly cambiar = output<SearchCriteria>();

  protected readonly condiciones = CONDICIONES;
  protected readonly colores = COLORES;
  protected readonly sistemas = SISTEMAS_DE_TALLA;
  protected readonly ordenes = ORDENES;

  /** Solo en móvil: en escritorio el panel se ve siempre y el botón no existe. */
  protected readonly abierto = signal(false);

  /**
   * Los dos extremos del precio, mientras se escriben.
   *
   * <p>Local y no derivado directo, porque un rango a medio escribir —«5» camino de
   * «50000»— no es una búsqueda que valga la pena hacer. Se aplica al salir del campo.
   * Vuelve a seguir a la dirección cuando cambia por fuera, que es lo que hace falta al
   * quitar la ficha del precio.
   */
  protected readonly minimo = linkedSignal(() => textoDe(this.criterios().minPrice));
  protected readonly maximo = linkedSignal(() => textoDe(this.criterios().maxPrice));

  protected readonly ordenActual = computed(() => this.criterios().sort ?? '');

  protected alternarPanel(): void {
    this.abierto.update((estaba) => !estaba);
  }

  protected tieneCondicion(condicion: Condition): boolean {
    return this.criterios().conditions.includes(condicion);
  }

  protected tieneColor(color: Color): boolean {
    return this.criterios().colors.includes(color);
  }

  /** Dentro de un mismo filtro basta con uno, así que marcar suma en vez de sustituir. */
  protected alternarCondicion(condicion: Condition, marcada: boolean): void {
    this.cambiar.emit({
      ...this.criterios(),
      conditions: alternar(this.criterios().conditions, condicion, marcada),
    });
  }

  protected alternarColor(color: Color, marcado: boolean): void {
    this.cambiar.emit({
      ...this.criterios(),
      colors: alternar(this.criterios().colors, color, marcado),
    });
  }

  protected elegirSistema(sistema: string): void {
    const elegido = sistema === '' ? null : (sistema as SizeSystem);

    // Sin sistema no hay talla que comparar: se van los dos (RN-087).
    this.cambiar.emit({
      ...this.criterios(),
      sizeSystem: elegido,
      size: elegido === null ? null : this.criterios().size,
    });
  }

  protected escribirTalla(valor: string): void {
    const limpio = valor.trim();
    this.cambiar.emit({ ...this.criterios(), size: limpio === '' ? null : limpio });
  }

  protected escribirMinimo(valor: string): void {
    this.minimo.set(valor);
  }

  protected escribirMaximo(valor: string): void {
    this.maximo.set(valor);
  }

  /** Al salir del campo, no en cada tecla: ver {@link minimo}. */
  protected aplicarPrecio(): void {
    this.cambiar.emit({
      ...this.criterios(),
      minPrice: numeroDe(this.minimo()),
      maxPrice: numeroDe(this.maximo()),
    });
  }

  protected elegirOrden(orden: string): void {
    this.cambiar.emit({
      ...this.criterios(),
      sort: orden === '' ? null : (orden as CatalogSort),
    });
  }
}

function alternar<T>(valores: readonly T[], valor: T, marcado: boolean): T[] {
  return marcado ? [...valores, valor] : valores.filter((candidato) => candidato !== valor);
}

function textoDe(valor: number | null): string {
  return valor === null ? '' : String(valor);
}

/**
 * Un entero de pesos, o nada.
 *
 * <p>Lo que no lo es se descarta en vez de mandarse: el servidor respondería 400 y la
 * pantalla enseñaría un error de red por haber escrito una coma. Lo que el servidor sí
 * rechaza —y aquí no se puede saber— es un mínimo por encima del máximo.
 */
function numeroDe(crudo: string): number | null {
  const limpio = crudo.trim();
  if (limpio === '') {
    return null;
  }
  const numero = Number(limpio);
  return Number.isSafeInteger(numero) && numero >= 0 ? numero : null;
}
