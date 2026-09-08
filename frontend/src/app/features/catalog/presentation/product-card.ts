import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { NgOptimizedImage } from '@angular/common';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { precioFormateado } from '../../../shared/domain/listing';
import { portada, type PublicListing } from '../domain/public-listing';

/**
 * Una publicación en la rejilla del catálogo. HU-009, criterio 6.
 *
 * <p>Muestra la toma frontal, el título, el precio y la condición. Nada más: es una
 * tarjeta, no una ficha resumida, y cada dato de más es una decisión que se toma en la
 * ficha y aquí solo estorba.
 *
 * <p><strong>Aquí no va la insignia de vendedor verificado.</strong> El acento bronce
 * aparece una vez por pantalla y veinte tarjetas con insignia son veinte acentos. Va en
 * la ficha y en el perfil, que es donde la confianza se decide.
 *
 * <p>La foto lleva `NgOptimizedImage` con dimensiones explícitas: es lo que decide la
 * mayor pintura con contenido del listado, y sin dimensiones la rejilla salta al cargar.
 */
@Component({
  selector: 'sendik-product-card',
  standalone: true,
  imports: [NgOptimizedImage, RouterLink, TranslocoPipe],
  templateUrl: './product-card.html',
  styleUrl: './product-card.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProductCard {
  private readonly idioma = inject(TranslocoService);

  readonly publicacion = input.required<PublicListing>();

  /**
   * Si es la primera de la rejilla.
   *
   * <p>Solo cambia una cosa: su foto se carga con prioridad en vez de en diferido. La de
   * arriba es la que el navegador debería empezar a traer de inmediato; las demás pueden
   * esperar a estar cerca del pliegue.
   */
  readonly primera = input(false);

  protected readonly toma = computed(() => portada(this.publicacion()));

  protected readonly titulo = computed(() => this.publicacion().product.title ?? '');

  protected readonly precio = computed(() => {
    const valor = this.publicacion().product.price;
    return valor === null ? null : precioFormateado(valor, this.idioma.getActiveLang());
  });

  protected readonly condicion = computed(() => this.publicacion().product.condition);
}
