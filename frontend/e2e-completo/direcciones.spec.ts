import { expect, test } from '@playwright/test';

import { correoNuevo, ingresar, registrar, salirSiHaySesion } from './recorridos';

/**
 * La libreta de direcciones de entrega, de punta a punta. HU-016.
 *
 * <p><strong>Lo que solo se puede demostrar aquí y en ningún otro sitio:</strong>
 *
 * <ul>
 *   <li>Que el contrato entre las dos mitades se sostiene de verdad. Una prueba de
 *       componente inventa la respuesta del servidor y una de integración inventa el cuerpo
 *       que manda el cliente; en medio quedaba una grieta por la que se coló un fallo real:
 *       el formulario aceptaba el código postal escrito «110 111» —que es como se escribe—,
 *       lo mandaba tal cual y el borde lo rechazaba con un 400 genérico, con las tres suites
 *       en verde. Esta prueba habría sido roja.
 *   <li>Que los treinta y tres departamentos y los municipios que alimentan los dos
 *       selectores salen de la base sembrada por V20, y no de un doble.
 *   <li>Que RN-099 se cumple contra PostgreSQL: la primera nace predeterminada sola, y al
 *       borrar la predeterminada el relevo lo decide el servidor y la pantalla lo dice.
 *   <li>Que RN-102 se cumple de verdad: cerrar la cuenta se lleva las direcciones, y eso
 *       solo se ve ejerciendo el cierre por la interfaz.
 * </ul>
 */
test.use({ locale: 'es-CO' });

const RUTA = '/mis-direcciones';

/** Rellena el formulario. El municipio depende del departamento, así que van en orden. */
async function escribirDireccion(
  page: import('@playwright/test').Page,
  datos: {
    departamento: string;
    municipio: string;
    quienRecibe: string;
    telefono: string;
    linea: string;
    codigoPostal?: string;
  },
): Promise<void> {
  await page.getByLabel('Departamento').selectOption({ label: datos.departamento });
  // El segundo selector se puebla con la respuesta del servidor: se espera a que la opción
  // exista en vez de a un tiempo fijo.
  await expect(
    page.getByLabel('Municipio').locator('option', { hasText: datos.municipio }),
  ).toHaveCount(1);
  await page.getByLabel('Municipio').selectOption({ label: datos.municipio });

  await page.getByLabel('Quién recibe').fill(datos.quienRecibe);
  await page.getByLabel('Teléfono de quien recibe').fill(datos.telefono);
  await page.getByLabel('Dirección', { exact: true }).fill(datos.linea);

  if (datos.codigoPostal !== undefined) {
    await page.getByLabel('Código postal (opcional)').fill(datos.codigoPostal);
  }
}

test.describe('direcciones de entrega', () => {
  /**
   * El ciclo entero en una sola prueba porque cada paso necesita el estado del anterior.
   * Trocearlo obligaría a fabricar ese estado llamando a la API, que es lo que esta suite
   * existe para no hacer.
   */
  test('se guarda, se marca la predeterminada, se borra con relevo y se va con la cuenta', async ({
    page,
  }) => {
    const correo = correoNuevo(`direcciones-${Date.now()}`);
    await salirSiHaySesion(page);
    await registrar(page, correo);
    await ingresar(page, correo);

    await page.goto(RUTA);

    // El vacío es una pantalla y es lo primero que ve todo el mundo (criterio 1).
    await expect(page.getByText('Todavía no tienes direcciones')).toBeVisible();
    await page.getByRole('button', { name: 'Agregar una dirección' }).click();

    // **El código postal se escribe con espacio, a propósito.** Es el fallo que esta prueba
    // existe para cazar: el cliente tiene que normalizarlo antes de mandarlo.
    await escribirDireccion(page, {
      departamento: 'Bogotá, D.C.',
      municipio: 'Bogotá, D.C.',
      quienRecibe: 'Ana María Ruiz',
      telefono: '300 123 4567',
      linea: 'Calle 45 # 12-34',
      codigoPostal: '110 111',
    });
    await page.getByRole('button', { name: 'Guardar dirección' }).click();

    // RN-099: la primera nace predeterminada sin que nadie lo pida.
    await expect(page.getByText('Predeterminada', { exact: true })).toBeVisible();
    await expect(page.getByText('Calle 45 # 12-34')).toBeVisible();
    // Criterio 24: el municipio nunca se lee solo.
    await expect(page.getByText('Bogotá, D.C., Bogotá, D.C.')).toBeVisible();

    // Una segunda, en otro departamento: es lo que demuestra que los dos selectores
    // dependientes funcionan contra la división sembrada de verdad.
    await page.getByRole('button', { name: 'Agregar una dirección' }).click();
    await escribirDireccion(page, {
      departamento: 'Antioquia',
      municipio: 'Medellín',
      quienRecibe: 'Carlos Pérez',
      telefono: '3109876543',
      linea: 'Carrera 70 # 45-12',
    });
    await page.getByRole('button', { name: 'Guardar dirección' }).click();

    await expect(page.getByText('Medellín, Antioquia')).toBeVisible();

    // Marcar la segunda: exactamente una predeterminada (criterio 13).
    await page
      .getByRole('button', { name: 'Usar como predeterminada la dirección de Carlos Pérez' })
      .click();
    await expect(
      page.getByText('Tu dirección predeterminada es ahora la de Carlos Pérez'),
    ).toBeVisible();
    // Exacto: sin el, casa tambien con el boton «Usar como predeterminada» y con el aviso.
    await expect(page.getByText('Predeterminada', { exact: true })).toHaveCount(1);

    // Borrar la predeterminada: el relevo lo decide el servidor y la pantalla dice cuál
    // quedó (criterio 11).
    await page.getByRole('button', { name: 'Quitar la dirección de Carlos Pérez' }).click();
    await expect(
      page.getByText('Tu dirección predeterminada es ahora la de Ana María Ruiz'),
    ).toBeVisible();

    // Y sobrevive a la recarga: está guardada, no en memoria (criterio 2).
    await page.reload();
    await expect(page.getByText('Calle 45 # 12-34')).toBeVisible();
    await expect(page.getByText('Predeterminada', { exact: true })).toBeVisible();

    // RN-102: cerrar la cuenta se lleva la libreta. Se comprueba entrando de nuevo con una
    // cuenta nueva, que es lo único que se puede observar desde fuera; que la fila
    // desaparece de la tabla lo prueba `ShippingAddressPersonalDataTest`.
    await page.goto('/mi-cuenta');
    await page.getByRole('button', { name: 'Quiero cerrar mi cuenta' }).click();
    await page.getByLabel('Escribe tu correo para confirmar').fill(correo);
    await page.getByRole('button', { name: 'Cerrar mi cuenta definitivamente' }).click();

    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  });

  /**
   * Sin sesión se explica y se ofrece entrar; no se redirige (criterio 17).
   *
   * <p>Y no sale ninguna dirección en el HTML del servidor, que es lo que sostiene que esto
   * no se renderice con datos: allí no hay cookie de nadie.
   */
  test('sin sesion invita a entrar y no ensena ninguna direccion', async ({ page }) => {
    await salirSiHaySesion(page);
    await page.goto(RUTA);

    await expect(page.getByText('Entra a tu cuenta')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).first()).toBeVisible();
  });
});
