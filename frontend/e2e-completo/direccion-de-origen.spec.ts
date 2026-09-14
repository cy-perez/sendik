import { expect, test } from '@playwright/test';

import { correoNuevo, ingresar, registrar, salirSiHaySesion } from './recorridos';

/**
 * La dirección de origen del vendedor, de punta a punta. HU-017.
 *
 * <p><strong>Lo que solo se puede demostrar aquí:</strong>
 *
 * <ul>
 *   <li>Que el contrato entre las dos mitades se sostiene: el 204 sin origen que el cliente
 *       lee como «no hay», el 422 de `USER_PHONE_REQUIRED` cuando el perfil no tiene
 *       teléfono, y el código postal escrito con espacio que el cliente normaliza.
 *   <li>Que la ciudad del perfil sigue al origen contra PostgreSQL: pasa a ser el municipio
 *       con su departamento al guardar (criterio 11) y queda vacía al borrar (criterio 12).
 *   <li>Que el cierre de cuenta desde la interfaz no se atasca teniendo origen. Que la fila
 *       desaparezca lo prueba `OriginAddressPersonalDataTest`.
 * </ul>
 */
test.use({ locale: 'es-CO' });

const RUTA = '/mi-direccion-de-origen';

async function escribirOrigen(
  page: import('@playwright/test').Page,
  datos: { departamento: string; municipio: string; linea: string; codigoPostal?: string },
): Promise<void> {
  await page.getByLabel('Departamento').selectOption({ label: datos.departamento });
  await expect(
    page.getByLabel('Municipio').locator('option', { hasText: datos.municipio }),
  ).toHaveCount(1);
  await page.getByLabel('Municipio').selectOption({ label: datos.municipio });
  await page.getByLabel('Dirección', { exact: true }).fill(datos.linea);
  if (datos.codigoPostal !== undefined) {
    await page.getByLabel('Código postal (opcional)').fill(datos.codigoPostal);
  }
}

test.describe('direccion de origen', () => {
  test('exige telefono, se guarda, mueve la ciudad del perfil, se reemplaza, se borra y se va con la cuenta', async ({
    page,
  }) => {
    const correo = correoNuevo(`origen-${Date.now()}`);
    await salirSiHaySesion(page);
    await registrar(page, correo);
    await ingresar(page, correo);

    // Una ciudad escrita a mano, que el origen va a reemplazar (criterio 10).
    await page.goto('/mi-cuenta');
    await page.getByLabel('Ciudad').fill('bogota');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Guardado.')).toBeVisible();

    await page.goto(RUTA);

    // Criterio 1: el vacío es una pantalla.
    await expect(page.getByText('Todavía no tienes dirección de origen')).toBeVisible();
    await page.getByRole('button', { name: 'Agregar mi dirección de origen' }).click();

    // Criterio 5: sin teléfono en el perfil se dice antes de guardar.
    await expect(page.getByRole('alert').filter({ hasText: 'no tiene teléfono' })).toBeVisible();
    await page.getByRole('link', { name: 'Cambiar nombre o teléfono en mi cuenta' }).click();
    await page.getByLabel('Teléfono').fill('300 123 4567');
    await page.getByRole('button', { name: 'Guardar cambios' }).click();
    await expect(page.getByText('Guardado.')).toBeVisible();

    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Agregar mi dirección de origen' }).click();
    await expect(page.getByText('3001234567')).toBeVisible();

    // El código postal con espacio, a propósito: el cliente lo normaliza.
    await escribirOrigen(page, {
      departamento: 'Bogotá, D.C.',
      municipio: 'Bogotá, D.C.',
      linea: 'Carrera 15 # 93-47',
      codigoPostal: '110 221',
    });
    await page.getByRole('button', { name: 'Guardar dirección de origen' }).click();

    await expect(page.getByRole('status')).toContainText('Dirección de origen guardada');
    await expect(page.getByText('Carrera 15 # 93-47')).toBeVisible();
    await expect(page.getByText('Bogotá, D.C., Bogotá, D.C.')).toBeVisible();
    await expect(page.getByText('110221')).toBeVisible();

    // Criterio 11: la ciudad del perfil ya no es lo escrito a mano y no se edita.
    await page.goto('/mi-cuenta');
    await expect(page.getByLabel('Ciudad')).toHaveCount(0);
    await expect(page.getByText('Bogotá, D.C., Bogotá, D.C.')).toBeVisible();
    await expect(page.getByText('Viene de tu dirección de origen')).toBeVisible();

    // Criterio 3: reemplazar deja uno, y la ciudad lo sigue.
    await page.getByRole('link', { name: 'Cambiarla allí' }).click();
    await page.getByRole('button', { name: 'Editar' }).click();
    await escribirOrigen(page, {
      departamento: 'Antioquia',
      municipio: 'Medellín',
      linea: 'Carrera 70 # 45-12',
    });
    await page.getByRole('button', { name: 'Guardar dirección de origen' }).click();
    await expect(page.getByText('Medellín, Antioquia')).toBeVisible();
    await expect(page.getByText('Carrera 15 # 93-47')).toHaveCount(0);

    // Y sobrevive a la recarga (criterio 2).
    await page.reload();
    await expect(page.getByText('Carrera 70 # 45-12')).toBeVisible();

    // Criterio 12: borrar deja la ciudad vacía y otra vez editable.
    await page.getByRole('button', { name: 'Quitar' }).click();
    await expect(page.getByRole('status')).toContainText('Dirección de origen quitada');
    await expect(page.getByText('Todavía no tienes dirección de origen')).toBeVisible();

    await page.goto('/mi-cuenta');
    await expect(page.getByLabel('Ciudad')).toHaveValue('');

    // El cierre, con origen: se observa que termina. La fila la prueba el backend.
    await page.goto(RUTA);
    await page.getByRole('button', { name: 'Agregar mi dirección de origen' }).click();
    await escribirOrigen(page, {
      departamento: 'Antioquia',
      municipio: 'Medellín',
      linea: 'Carrera 70 # 45-12',
    });
    await page.getByRole('button', { name: 'Guardar dirección de origen' }).click();
    await expect(page.getByText('Medellín, Antioquia')).toBeVisible();

    await page.goto('/mi-cuenta');
    await page.getByRole('button', { name: 'Quiero cerrar mi cuenta' }).click();
    await page.getByLabel('Escribe tu correo para confirmar').fill(correo);
    await page.getByRole('button', { name: 'Cerrar mi cuenta definitivamente' }).click();

    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
  });

  /** Sin sesión se explica y se ofrece entrar; no se redirige (criterio 14). */
  test('sin sesion invita a entrar y no ensena ningun origen', async ({ page }) => {
    await salirSiHaySesion(page);
    await page.goto(RUTA);

    await expect(page.getByText('Entra a tu cuenta')).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' }).first()).toBeVisible();
  });
});
