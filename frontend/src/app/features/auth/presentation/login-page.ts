import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  ElementRef,
  inject,
  Injector,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';

import { AuthStore } from '../application/auth.store';
import { esCorreoValido, faltaLaContrasena } from '../domain/credentials';
import { SubmitButton } from '../../../shared/ui/form/submit-button';
import { TextField } from '../../../shared/ui/form/text-field';

/**
 * Un origen que no existe, para resolver el destino de la vuelta sin tocar `window`.
 *
 * <p>Cualquier destino que al resolverse se salga de aqui es de otro sitio, y no se
 * obedece. El dominio real no importa: lo que se comprueba es que el destino sea relativo.
 */
const BASE_SINTETICA = 'http://sendik.invalid';

/**
 * Formulario de ingreso. Criterios 10 a 13 de HU-001.
 *
 * <p><strong>Lo que esta pantalla no hace es lo que importa.</strong> No dice si
 * el correo existe, no dice cual de los dos campos fallo y no distingue una
 * cuenta bloqueada de una contrasena equivocada mas de lo que ya distingue el
 * servidor. Todo eso convertiria el formulario en un detector de cuentas
 * (criterio 11).
 *
 * <p>La validacion local se limita a lo que se ve sin preguntar: que haya algo
 * escrito y que el correo tenga forma de correo. Quien decide es el servidor.
 */
@Component({
  selector: 'sendik-login-page',
  imports: [ReactiveFormsModule, TranslocoPipe, RouterLink, TextField, SubmitButton],
  templateUrl: './login-page.html',
  styleUrl: './login-page.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoginPage {
  private readonly store = inject(AuthStore);
  private readonly router = inject(Router);
  private readonly ruta = inject(ActivatedRoute);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly form = new FormGroup({
    email: new FormControl('', { nonNullable: true }),
    password: new FormControl('', { nonNullable: true }),
  });

  protected readonly intentado = signal(false);

  private readonly valores = toSignal(this.form.valueChanges, {
    initialValue: this.form.getRawValue(),
  });

  protected readonly ingreso = this.store.login;

  protected readonly emailError = computed(() =>
    this.errorSi(!esCorreoValido(this.valores().email ?? ''), 'auth.login.errors.email'),
  );

  protected readonly passwordError = computed(() =>
    this.errorSi(faltaLaContrasena(this.valores().password ?? ''), 'auth.login.errors.password'),
  );

  protected readonly hayErrores = computed(
    () => this.emailError() !== null || this.passwordError() !== null,
  );

  protected readonly errorDelServidor = computed(() => {
    const fallo = this.ingreso.error();
    return fallo === null ? null : AuthStore.claveDeError(fallo);
  });

  protected enviar(): void {
    this.intentado.set(true);
    if (this.hayErrores()) {
      this.enfocarElPrimerError();
      return;
    }

    const valores = this.form.getRawValue();
    this.ingreso.mutate(
      { email: valores.email, password: valores.password },
      {
        // Se navega desde aqui y no desde el almacen: quien decide a donde ir
        // despues de entrar es la pantalla, y manana puede ser a la direccion
        // que la persona intentaba abrir. El almacen solo guarda la sesion.
        //
        // La contrasena queda escrita en el formulario mientras tanto; al
        // cambiar de ruta el componente se destruye y se va con el.
        onSuccess: () => {
          void this.router.navigateByUrl(this.aDondeVolver());

          // La contrasena escrita no vive solo en el formulario: TanStack la
          // guarda en las variables de la mutacion, y el almacen es de raiz, asi
          // que destruir esta pantalla no se la lleva. Se olvida en cuanto deja
          // de hacer falta (docs/operacion/datos-personales.md).
          //
          // Va aqui y no en el almacen porque reset() dentro del onSuccess de la
          // propia mutacion cancela este callback, y entonces no se navegaria.
          this.ingreso.reset();
        },
      },
    );
  }

  /**
   * A donde ir despues de entrar. ADR-0029.
   *
   * <p>Lo pide quien mando aqui, con `?redirectTo=`, y por omision es la portada. Es lo que
   * este archivo ya anticipaba: «manana puede ser a la direccion que la persona intentaba
   * abrir». El primero que lo necesita es el control de favorito de HU-011.
   *
   * <p><strong>Se valida y no se obedece.</strong> Solo se admite una ruta relativa de este
   * sitio: que empiece por una barra y no por dos. Sin esa comprobacion, este formulario
   * queda convertido en un redirector abierto —`?redirectTo=https://otro-sitio`, o
   * `//otro-sitio`, que el navegador lee como otro dominio— y manda a alguien recien
   * autenticado a un sitio ajeno con la confianza puesta y el nombre de Sendik en la
   * barra de la que viene.
   *
   * <p>Se lee del parametro y no de un estado guardado: es navegacion, tiene que funcionar
   * con el boton de atras y no tiene nada que esconder. Lo que si se guarda aparte es la
   * intencion —que gesto habia pendiente—, porque una accion no puede viajar en un enlace
   * que cualquiera fabrica.
   */
  private aDondeVolver(): string {
    const destino = this.ruta.snapshot.queryParamMap.get('redirectTo');

    if (destino === null || !destino.startsWith('/')) {
      return '/';
    }

    // **Se resuelve como URL y se comprueba que no se haya movido de sitio.** La version
    // anterior miraba solo que no empezara por `//`, y eso deja pasar `/\evil.com`,
    // `/%5C%5Cevil.com` -el parametro llega ya descodificado- y variantes con tabuladores
    // o saltos de linea: la especificacion de URL trata la barra invertida como barra en
    // los esquemas http, asi que las tres son `//evil.com` al resolverlas.
    //
    // Hoy nada de eso sale del sitio, porque `navigateByUrl` no puede: lo que el enrutador
    // no entiende acaba en la ruta comodin. Pero entonces quien protege es el enrutador y
    // no esta comprobacion, y ADR-0029 contempla en «Cuando revisar» el dia en que el
    // ingreso pase por un proveedor externo y la vuelta deje de ser una navegacion
    // interna. Ese dia esta linea tiene que seguir siendo la que decide.
    //
    // La base es sintetica y no `window.location.origin` a proposito: esta clase se
    // renderiza tambien en el servidor, donde `window` no existe, y ademas asi la
    // comprobacion no depende de en que dominio este desplegado el sitio.
    try {
      const resuelto = new URL(destino, BASE_SINTETICA);
      return resuelto.origin === BASE_SINTETICA
        ? `${resuelto.pathname}${resuelto.search}${resuelto.hash}`
        : '/';
    } catch {
      return '/';
    }
  }

  protected control(nombre: 'email' | 'password'): FormControl<string> {
    return this.form.controls[nombre];
  }

  /**
   * Al fallar la validacion el foco esta en el boton de envio, al final del
   * formulario, y los mensajes salen arriba: con el texto al 200% quedan fuera
   * de la vista y no hay nada que indique que paso algo. Llevarlo al primer
   * campo invalido pone el problema y el foco en el mismo sitio.
   *
   * <p>Se hace despues de que el marcado se actualice: los mensajes de error
   * dependen de la senal {@code intentado}, que se acaba de poner.
   */
  private enfocarElPrimerError(): void {
    const id = this.emailError() !== null ? 'correo' : 'contrasena';

    afterNextRender(
      () => {
        this.host.nativeElement.querySelector<HTMLInputElement>(`#${id}`)?.focus();
      },
      { injector: this.injector },
    );
  }

  private errorSi(condicion: boolean, clave: string): string | null {
    return this.intentado() && condicion ? clave : null;
  }
}
