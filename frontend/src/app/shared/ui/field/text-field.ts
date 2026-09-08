import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';

/**
 * Campo de texto del sistema de diseno.
 *
 * <p>Recibe los textos ya traducidos, como cadenas. La version anterior recibia
 * claves de Transloco y las traducia dentro, lo que ataba una primitiva de
 * shared/ui al catalogo de traducciones de la aplicacion: no se podia usar en
 * una prueba sin montar Transloco, ni reutilizar con un texto que viniera del
 * servidor. Quien lo usa traduce y le pasa el resultado.
 *
 * <p>El error llega ya decidido desde arriba en vez de deducirlo aqui de los
 * validadores. Con deteccion de cambios sin zonas, leer el estado de un
 * FormControl dentro de un computed no vuelve a evaluarse cuando ese estado
 * cambia; el padre si tiene senales y sabe cuando mostrarlo.
 */
@Component({
  selector: 'sendik-text-field',
  imports: [ReactiveFormsModule],
  template: `
    <div class="mb-6 flex flex-col gap-1">
      <!-- Etiqueta siempre visible, y asociada por for. Un marcador de posicion
           como etiqueta desaparece al escribir y deja a quien vuelve al
           formulario sin saber que iba ahi. -->
      <label class="tipo-secundario text-text" [for]="fieldId()">{{ label() }}</label>

      <!-- La pista va ANTES del campo y no despues: se lee al llegar, que es
           cuando sirve para escribir bien. -->
      @if (hint(); as texto) {
        <p class="tipo-leyenda m-0 text-text-muted" [id]="hintId()">{{ texto }}</p>
      }

      <!-- El estado de error se anuncia por aria-invalid y por el texto; el borde
           es refuerzo, no la unica senal. Quien no distingue colores tiene el
           mensaje. Va por variante aria-invalid y no por una clase calculada,
           para que la senal visual y la semantica no puedan separarse. -->
      <input
        class="control-field"
        [id]="fieldId()"
        [type]="type()"
        [formControl]="control()"
        [attr.autocomplete]="autocomplete()"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="describedBy()"
      />

      @if (error(); as texto) {
        <!-- role="alert" para que el lector de pantalla lo anuncie al aparecer,
             sin esperar a que el foco llegue al campo. -->
        <p class="tipo-leyenda m-0 text-error" [id]="errorId()" role="alert">{{ texto }}</p>
      }
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TextField {
  readonly control = input.required<FormControl<string>>();
  readonly label = input.required<string>();
  readonly fieldId = input.required<string>();
  readonly type = input<'text' | 'email' | 'password' | 'date'>('text');
  readonly autocomplete = input<string>('off');
  readonly hint = input<string | null>(null);
  readonly error = input<string | null>(null);

  protected readonly errorId = computed(() => `${this.fieldId()}-error`);
  protected readonly hintId = computed(() => `${this.fieldId()}-hint`);

  /**
   * El lector de pantalla anuncia primero la pista y luego el error. Si se
   * apuntara solo al error, quien no ve el campo pierde la indicacion de formato
   * en cuanto se equivoca una vez.
   */
  protected readonly describedBy = computed(() => {
    const partes = [this.hint() ? this.hintId() : null, this.error() ? this.errorId() : null];
    const unidas = partes.filter((parte) => parte !== null).join(' ');
    return unidas.length > 0 ? unidas : null;
  });
}
