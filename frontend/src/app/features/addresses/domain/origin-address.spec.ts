import { describe, expect, it } from 'vitest';

import { comoSeLeeElOrigen, elRemitenteEstaCompleto, type OriginAddress } from './origin-address';

/** Las reglas del origen que valen igual en el navegador y en el servidor. HU-017. */
describe('la dirección de origen', () => {
  const origen: OriginAddress = {
    departmentCode: '05',
    departmentName: 'Antioquia',
    municipalityCode: '05001',
    municipalityName: 'Medellín',
    municipalityActive: true,
    line: 'Carrera 70 # 45-12',
    complement: null,
    instructions: null,
    postalCode: null,
    senderName: 'Ana María',
    senderPhone: '3001234567',
    savedAt: '2026-09-14T10:00:00Z',
  };

  /** Criterio 5: un remitente sin teléfono no es remitente. */
  it('exige teléfono al remitente', () => {
    expect(elRemitenteEstaCompleto({ name: 'Ana', phone: '3001234567' })).toBe(true);
    expect(elRemitenteEstaCompleto({ name: 'Ana', phone: null })).toBe(false);
    expect(elRemitenteEstaCompleto({ name: 'Ana', phone: '   ' })).toBe(false);
  });

  /** El municipio nunca se lee solo: hay más de un San Pedro. */
  it('lee el municipio con su departamento al lado', () => {
    expect(comoSeLeeElOrigen(origen)).toBe('Medellín, Antioquia');
  });
});
