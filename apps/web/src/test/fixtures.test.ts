import { describe, expect, it } from 'vitest';

import {
  CANONICAL_COMPANY,
  CANONICAL_ORDER,
  CANONICAL_ORDER_ITEMS,
  DISCARDED_FIXTURE_VALUES,
} from './fixtures/flashdrop.fixture';
import { listSourceFiles } from './support/sourceFiles';

describe('canonical flashdrop fixture (spec S16)', () => {
  it('defines company RUT as 77.492.120-K and explicitly rejects 76.892.410-K', () => {
    expect(CANONICAL_COMPANY.rut).toBe('77.492.120-K');
    expect(CANONICAL_COMPANY.discardedRuts).toContain('76.892.410-K');
  });

  it('defines canonical order #FD-0042 with 3 items totaling $13.970', () => {
    expect(CANONICAL_ORDER.items).toHaveLength(3);

    const calculatedSubtotal = CANONICAL_ORDER.items.reduce((sum, item) => sum + item.total, 0);
    expect(calculatedSubtotal).toBe(12980);
    expect(CANONICAL_ORDER.subtotal).toBe(calculatedSubtotal);

    expect(CANONICAL_ORDER.deliveryFee).toBe(990);
    expect(CANONICAL_ORDER.total).toBe(calculatedSubtotal + CANONICAL_ORDER.deliveryFee);
    expect(CANONICAL_ORDER.total).toBe(13970);
  });

  it('defines order client as Matías Valenzuela (rejecting Carlos M.)', () => {
    expect(CANONICAL_ORDER.client.name).toBe('Matías Valenzuela');
    expect(DISCARDED_FIXTURE_VALUES.clientNames).toContain('Carlos M.');
  });

  it('defines Truffle Rustic Fries price as $3.490 (rejecting $4.490 and $2.990)', () => {
    const fries = CANONICAL_ORDER_ITEMS.find((item) => item.name === 'Papas Rústicas Trufadas');
    expect(fries).toBeDefined();
    expect(fries?.unitPrice).toBe(3490);
    expect(DISCARDED_FIXTURE_VALUES.rusticFriesPrices).toContain(4490);
    expect(DISCARDED_FIXTURE_VALUES.rusticFriesPrices).toContain(2990);
  });

  it('records delivery PINs (5821, 4082) as discarded per S16 and S17', () => {
    expect(DISCARDED_FIXTURE_VALUES.deliveryPins).toEqual(['5821', '4082']);
  });

  it('ensures no screen or component hardcodes discarded mockup literals', () => {
    const sourceFiles = [...listSourceFiles('components'), ...listSourceFiles('app')];

    // Check for discarded company RUT
    const rutOffenders = sourceFiles
      .filter((file) => file.content.includes('76.892.410-K'))
      .map((file) => file.path);
    expect(rutOffenders, 'Discarded RUT 76.892.410-K must not appear in UI code').toEqual([]);

    // Check for discarded delivery PINs
    const pinOffenders = sourceFiles
      .filter((file) => /\b(5821|4082)\b/.test(file.content))
      .map((file) => file.path);
    expect(pinOffenders, 'Discarded delivery PINs must not appear in UI code').toEqual([]);

    // Check that no component has hardcoded literal `#FD-` without interpolation
    // e.g. text node `#FD-0042` or `#FD-42` directly in JSX
    const hardcodedOrderCodeOffenders = sourceFiles
      .filter((file) => />\s*#FD-\d+\s*</.test(file.content))
      .map((file) => file.path);
    expect(
      hardcodedOrderCodeOffenders,
      'Order codes must be read dynamically from backend response, never hardcoded in JSX',
    ).toEqual([]);
  });
});
