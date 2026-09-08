import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { APP_CONFIG } from '../../../core/config/app-config';
import {
  type ContentPageId,
  PAGINAS_DE_CONTENIDO,
  RUTAS_CONTENIDO,
} from '../../../core/routes/content-routes';
import {
  DOCUMENTOS_LEGALES,
  type LegalDocumentId,
  RUTAS_LEGALES,
} from '../../../core/routes/legal-routes';

@Component({
  selector: 'sendik-site-footer',
  imports: [RouterLink, TranslocoPipe],
  templateUrl: './site-footer.html',
  styleUrl: './site-footer.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SiteFooter {
  protected readonly documentosLegales = DOCUMENTOS_LEGALES;

  /**
   * Criterio 13 de HU-004: el pie enlaza las paginas informativas. Salen de
   * core/routes igual que las legales, asi que no puede haber aqui un enlace a
   * una ruta que no exista.
   */
  protected readonly paginas = PAGINAS_DE_CONTENIDO;

  /**
   * Criterio 11: quien responde por el sitio, siempre desde la configuracion.
   *
   * <p>Cada campo puede faltar y la plantilla lo omite uno por uno. No se pinta
   * un pie a medias con huecos: se pinta con lo que hay.
   */
  protected readonly empresa = inject(APP_CONFIG).company;

  protected rutaDe(documento: LegalDocumentId): string {
    return RUTAS_LEGALES[documento];
  }

  protected rutaDePagina(pagina: ContentPageId): string {
    return RUTAS_CONTENIDO[pagina];
  }
}
