import { computed, Directive, input } from '@angular/core';

/**
 * Las tres variantes del sistema, y son solo tres.
 *
 * <p>No hay variante de acento: el bronce no es el boton. Aparece una vez por
 * pantalla y siempre en la insignia de vendedor verificado. Un boton bronce es
 * la forma mas rapida de romper esa regla, asi que aqui no existe el valor.
 */
export type ButtonVariant = 'primary' | 'secondary' | 'text' | 'ghost';

const BASE =
  'inline-flex items-center justify-center gap-2 rounded-md border border-transparent ' +
  'font-semibold cursor-pointer no-underline transition-colors-no-outline ' +
  'disabled:cursor-not-allowed disabled:bg-disabled disabled:text-surface ' +
  'disabled:border-transparent ' +
  // aria-disabled se pinta igual que disabled y no solo se anuncia. El obturador
  // de la captura lleva aria-disabled y NO disabled, para no perder el foco de
  // quien navega con teclado; sin esta linea se veia identico al habilitado y el
  // criterio 3 de HU-003 quedaba roto en lo visual. La pantalla lo parcheaba con
  // un selector .btn[aria-disabled] suyo, que es una decision del boton.
  'aria-disabled:cursor-not-allowed aria-disabled:border-transparent ' +
  'aria-disabled:bg-disabled aria-disabled:text-surface';

/**
 * Las tres variantes. El destino tactil de 44px va en las dos primeras y no en
 * la de texto: un enlace dentro de un parrafo con 44px de alto separa las lineas
 * de forma visible. La de texto se usa solo en linea, donde el destino lo da el
 * renglon.
 */
const VARIANTS: Record<ButtonVariant, string> = {
  primary:
    'min-h-touch px-6 py-3 bg-primary text-on-primary hover:bg-primary-hover active:bg-primary-pressed',
  /*
   * En oscuro el texto y el borde NO van en --brand-primary. Ese token es tinta
   * en claro pero un gris medio en oscuro, donde su papel es ser RELLENO de
   * boton: usarlo como texto da 3.66:1 sobre la superficie. Es la misma regla
   * que prohibe cruzar los dos bronces, y ya se cobro dos piezas —el enlace del
   * aviso de privacidad y este boton— antes de quedar escrita.
   */
  secondary:
    'min-h-touch px-6 py-3 bg-transparent text-primary border-primary ' +
    'hover:bg-primary-soft dark:text-text dark:border-text',
  text: 'px-2 py-3 bg-transparent text-primary hover:underline dark:text-text',

  /**
   * Control neutro: la caja de 44 con borde de control y fondo de superficie.
   *
   * <p>Es la cuarta variante y entra con motivo, no por gusto. Estaba escrita a
   * mano como `.accion` en account-page.css y en session-menu.css, y otra vez en
   * los botones de icono de la cabecera: tres copias de las mismas diez
   * declaraciones. Una regla copiada tres veces se corrige en dos sitios.
   *
   * <p>px-3 no es arbitrario: con el icono de 20 del sistema da exactamente los
   * 44 de destino tactil, asi que un boton de solo icono sale cuadrado sin
   * pedirlo.
   */
  ghost:
    'min-h-touch min-w-touch px-3 font-normal bg-surface text-text ' +
    'border-control-border hover:bg-primary-soft disabled:text-disabled',
};

/**
 * Boton del sistema de diseno.
 *
 * <p>Es una directiva sobre el elemento nativo y no un componente que lo
 * envuelve. La diferencia no es de estilo: un {@code <sendik-button>} con un
 * {@code <button>} dentro obliga a reenviar a mano {@code type},
 * {@code form}, {@code disabled} y el foco, y cualquiera que se olvide produce
 * un boton que no envia el formulario o que no se puede deshabilitar. Puesto
 * como atributo, el elemento sigue siendo el nativo y todo eso lo da el
 * navegador.
 *
 * <p>No sabe de traducciones: el texto entra proyectado, ya traducido por quien
 * lo usa. La capa shared/ui no depende de Transloco.
 *
 * <p>El anillo de foco no se declara aqui a proposito. Es global, de 3px, y
 * vive en styles.css sin capa para que ninguna utilidad pueda retirarlo.
 */
@Directive({
  // label entra por un motivo concreto y no por comodidad: el patron del campo
  // de archivo oculto necesita que la ETIQUETA sea el control visible —el input
  // va con .solo-lectores para no salir del orden de tabulacion— y esa etiqueta
  // tiene que verse como un boton. Estaba resuelto copiando diez utilidades a
  // mano en avatar-form y en el asistente de captura.
  selector: 'button[sendikButton], a[sendikButton], label[sendikButton]',
  host: {
    '[class]': 'classes()',
    // La variante, expuesta como atributo. No es decoracion: hay una regla de
    // producto —UNA sola llamada a la accion por pantalla— y hasta ahora se
    // vigilaba contando elementos con la clase .btn-primario. Contar clases de
    // Tailwind seria contar utilidades; contar un atributo estable es contar la
    // decision. Lo comprueban home-page.spec y content-pages.spec.
    '[attr.data-variant]': 'variant()',
    // Solo aria-busy. La directiva NO enlaza aria-disabled a proposito: hay
    // controles que gestionan el suyo —el obturador de la captura lo pone sin
    // poner disabled, para no perder el foco— y dos enlaces al mismo atributo se
    // pisan. El ESTILO de aria-disabled si lo trae la variante de BASE, asi que
    // quien lo ponga se ve deshabilitado sin tener que pintarlo a mano.
    '[attr.aria-busy]': 'loading() ? "true" : null',
  },
})
export class Button {
  readonly variant = input<ButtonVariant>('primary');

  /**
   * En curso. Marca {@code aria-busy} y no cambia el texto.
   *
   * <p>Sustituir «Crear cuenta» por «Enviando…» mueve el foco de sitio para
   * quien usa lector de pantalla y hace que el boton cambie de tamano mientras
   * se pulsa. El texto se queda; lo que cambia es el estado anunciado.
   */
  readonly loading = input(false);

  protected readonly classes = computed(() => `${BASE} ${VARIANTS[this.variant()]}`);
}
