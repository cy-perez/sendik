import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';

/**
 * Sobre Sendik. HU-005, criterios 10 y 11.
 *
 * <p>Muestra razon social, NIT y direccion desde la configuracion, nunca escritos
 * en la plantilla, y cada dato se omite por separado si falta: un despliegue sin
 * direccion sigue enseñando el NIT (mismo criterio que el pie en HU-004).
 *
 * <p>No lleva mision, vision ni valores: no los lee nadie y en una marca nueva
 * suenan a relleno (docs/producto/textos-web.md).
 */
@Component({
  selector: 'sendik-about-page',
  imports: [TranslocoPipe],
  templateUrl: './about-page.html',
  styleUrl: './content-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AboutPage {
  private readonly config = inject(APP_CONFIG);

  protected readonly empresa = this.config.company;

  /**
   * Si no hay ni un dato, la seccion entera sobra en vez de pintarse vacia.
   *
   * <p>Una constante y no un `computed`: APP_CONFIG es un objeto plano que se lee
   * una vez al arrancar, no una senal, y derivar de algo que no cambia con
   * `computed` solo hace creer que cambia.
   */
  protected readonly hayDatosDeEmpresa =
    this.empresa.name !== null || this.empresa.taxId !== null || this.empresa.address !== null;
}
