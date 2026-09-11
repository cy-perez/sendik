import { describe, expect, it } from 'vitest';

import {
  comoDatoOpcional,
  comoSeLee,
  departamentoDe,
  elCodigoPostalEsValido,
  elComplementoEsValido,
  elMunicipioEsValido,
  elNombreDeQuienRecibeEsValido,
  elTelefonoEsValido,
  laLibretaEstaLlena,
  laLineaEsValida,
  lasIndicacionesSonValidas,
  type ShippingAddress,
} from './shipping-address';

/**
 * Las reglas de la libreta que valen igual en el navegador y en el servidor. HU-016.
 *
 * <p>Aquí se prueba lo que el servidor va a volver a comprobar: estas funciones no deciden
 * nada, solo evitan un viaje y un mensaje tardío.
 */
describe('la dirección de entrega', () => {
  const direccion = (campos: Partial<ShippingAddress> = {}): ShippingAddress => ({
    id: 'una',
    recipientName: 'Ana María Ruiz',
    phone: '3001234567',
    departmentCode: '11',
    departmentName: 'Bogotá, D.C.',
    municipalityCode: '11001',
    municipalityName: 'Bogotá, D.C.',
    municipalityActive: true,
    line: 'Calle 45 # 12-34',
    complement: null,
    instructions: null,
    postalCode: null,
    isDefault: false,
    savedAt: '2026-09-11T10:00:00Z',
    ...campos,
  });

  describe('la línea de dirección', () => {
    /**
     * Lo que este caso protege es que nadie meta después una expresión regular que «valide
     * direcciones»: estas son todas reales y ninguna encaja en un patrón común.
     */
    it.each([
      'Calle 45 # 12-34',
      'Cra 7 No. 71-21 Of. 502',
      'Dg 61C Bis # 24-30',
      'Tv 93 # 53-48 Mz 5 Et 2',
      'Km 3 vía Siberia-Cota',
      'Av. El Dorado #69-76',
    ])('acepta «%s»', (texto) => {
      expect(laLineaEsValida(texto)).toBe(true);
    });

    it('rechaza lo demasiado corto y lo demasiado largo', () => {
      expect(laLineaEsValida('Cll')).toBe(false);
      expect(laLineaEsValida('   ')).toBe(false);
      expect(laLineaEsValida('x'.repeat(121))).toBe(false);
    });
  });

  describe('el teléfono', () => {
    it.each(['3001234567', '300 123 4567', '(601) 555-1234', '+573001234567'])(
      'acepta «%s», con los separadores que la gente escribe',
      (texto) => {
        expect(elTelefonoEsValido(texto)).toBe(true);
      },
    );

    it('rechaza lo que no sean de 7 a 15 dígitos', () => {
      expect(elTelefonoEsValido('')).toBe(false);
      expect(elTelefonoEsValido('123456')).toBe(false);
      expect(elTelefonoEsValido('1234567890123456')).toBe(false);
      expect(elTelefonoEsValido('no es un teléfono')).toBe(false);
    });
  });

  describe('quién recibe', () => {
    /** RN-104: puede no ser el titular, y puede no parecer un nombre de persona. */
    it.each(['Ana María Ruiz', 'Portería Torre 3', 'Almacén Sendik S.A.S.'])(
      'acepta «%s»',
      (texto) => {
        expect(elNombreDeQuienRecibeEsValido(texto)).toBe(true);
      },
    );

    it('rechaza lo demasiado corto y lo demasiado largo', () => {
      expect(elNombreDeQuienRecibeEsValido('A')).toBe(false);
      expect(elNombreDeQuienRecibeEsValido('x'.repeat(81))).toBe(false);
    });
  });

  describe('los opcionales', () => {
    /** Vacío y ausente son lo mismo: sin esto, vaciar un campo sería imposible. */
    it('trata el vacío como ausencia', () => {
      expect(comoDatoOpcional('   ')).toBeNull();
      expect(comoDatoOpcional('')).toBeNull();
      expect(comoDatoOpcional(' Apto 802 ')).toBe('Apto 802');
    });

    it('acepta que falten', () => {
      expect(elComplementoEsValido('')).toBe(true);
      expect(lasIndicacionesSonValidas('')).toBe(true);
      expect(elCodigoPostalEsValido('')).toBe(true);
    });

    it('los acota cuando están', () => {
      expect(elComplementoEsValido('x'.repeat(61))).toBe(false);
      expect(lasIndicacionesSonValidas('x'.repeat(201))).toBe(false);
    });

    it('exige seis dígitos al código postal, con o sin espacios', () => {
      expect(elCodigoPostalEsValido('110111')).toBe(true);
      expect(elCodigoPostalEsValido('110 111')).toBe(true);
      expect(elCodigoPostalEsValido('11011')).toBe(false);
      expect(elCodigoPostalEsValido('11011a')).toBe(false);
    });
  });

  describe('el municipio', () => {
    it('son cinco dígitos y nada más', () => {
      expect(elMunicipioEsValido('11001')).toBe(true);
      expect(elMunicipioEsValido('05001')).toBe(true);
      expect(elMunicipioEsValido('1100')).toBe(false);
      expect(elMunicipioEsValido('bogota')).toBe(false);
    });

    /**
     * La misma propiedad de la que depende que el cuerpo de la API no pida departamento,
     * usada aquí para lo contrario: al editar, el selector de departamento tiene que llegar
     * ya elegido para que el de municipio se pueda poblar.
     */
    it('lleva dentro el código de su departamento', () => {
      expect(departamentoDe('11001')).toBe('11');
      expect(departamentoDe('05001')).toBe('05');
      expect(departamentoDe('88564')).toBe('88');
    });
  });

  describe('la libreta', () => {
    /** Criterio 24: el municipio nunca se lee solo, porque hay homónimos. */
    it('lee el municipio con su departamento al lado', () => {
      expect(
        comoSeLee(direccion({ municipalityName: 'Santa María', departmentName: 'Huila' })),
      ).toBe('Santa María, Huila');
    });

    /**
     * El tope no vive aquí: cuando el servidor rechaza, cuántas hay es exactamente el tope.
     * Es lo que permite que el mensaje lo nombre sin duplicar el número, que es la deuda
     * que HU-015 dejó anotada con el veinte del carrito.
     */
    it('sabe cuántas hay para poder nombrar el tope sin conocerlo', () => {
      expect(laLibretaEstaLlena([direccion(), direccion()])).toBe(2);
      expect(laLibretaEstaLlena([])).toBe(0);
    });
  });
});
