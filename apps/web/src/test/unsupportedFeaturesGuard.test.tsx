import { describe, expect, it } from 'vitest';

import { listSourceFiles } from './support/sourceFiles';

/**
 * Closed list of unsupported features and mock artifacts that MUST NEVER
 * appear anywhere in FlashDrop's web client (Spec S17).
 *
 * Each entry pairs a descriptive rule name with regexes to enforce via
 * static analysis across all authored UI components (`src/components/`, `src/app/`).
 */
interface UnsupportedFeatureRule {
  readonly id: string;
  readonly description: string;
  readonly patterns: ReadonlyArray<RegExp>;
}

export const UNSUPPORTED_FEATURES_RULES: ReadonlyArray<UnsupportedFeatureRule> = [
  {
    id: 'ratings-and-reviews',
    description: 'No ratings, star reviews, or customer review counts',
    patterns: [/★/, /\brating\b/i, /calificaci[oó]n/i, /reseñas?/i],
  },
  {
    id: 'free-shipping-and-marketplace-delivery-fee',
    description: 'No free shipping badges or per-restaurant delivery fee claims in marketplace',
    patterns: [/env[ií]o\s*gratis/i, /costo\s*de\s*env[ií]o\s*:\s*\$0/i],
  },
  {
    id: 'eta-per-restaurant',
    description: 'No ETA estimates per restaurant in marketplace listings',
    patterns: [/\beta\s*:\s*\d+/i, /tiempo\s*estimado\s*por\s*local/i],
  },
  {
    id: 'coupons-and-flashhour',
    description: 'No #FLASH30 coupon banners or FlashHour marketing campaigns',
    patterns: [/#FLASH30\b/i, /\bFlashHour\b/i],
  },
  {
    id: 'flashpoints-loyalty',
    description: 'No FlashPoints loyalty program rewards',
    patterns: [/\bFlashPoints\b/i, /puntos\s*flashdrop/i],
  },
  {
    id: 'delivery-pin',
    description: 'No delivery confirmation PIN code inputs or prompts',
    patterns: [/PIN\s*de\s*entrega/i, /c[oó]digo\s*pin\s*de\s*seguridad/i],
  },
  {
    id: 'courier-chat',
    description: 'No courier-customer chat window or messaging triggers',
    patterns: [/chat\s*con\s*el\s*repartidor/i, /chat\s*con\s*el\s*cliente/i, /iniciar\s*chat/i],
  },
  {
    id: 'pos-and-thermal-printers',
    description: 'No POS integration status or thermal printer connection indicators',
    patterns: [/\bPOS\b/, /impresora\s*t[eé]rmica/i, /ticket\s*fiscal/i],
  },
  {
    id: 'sii-electronic-billing',
    description: 'No SII electronic invoice (boleta electrónica) generation',
    patterns: [/boleta\s*electr[oó]nica/i, /\bSII\b/, /factura\s*electr[oó]nica/i],
  },
  {
    id: 'tips-and-gratuity',
    description: 'No courier tipping or gratuity options',
    patterns: [/\bpropina\b/i, /agregar\s*propina/i],
  },
  {
    id: 'live-gps-and-speed',
    description: 'No live GPS tracking telemetry or vehicle speed display',
    patterns: [/\bkm\/h\b/i, /velocidad\s*actual/i, /gps\s*en\s*vivo/i, /telemetr[ií]a\s*de\s*ruta/i],
  },
  {
    id: 'kitchen-sla-and-lunch-shift',
    description: 'No kitchen SLA timers or lunch shift counters',
    patterns: [/\bSLA\s*de\s*cocina\b/i, /turno\s*almuerzo/i, /meta\s*tiempo\s*sla/i],
  },
  {
    id: 'hourly-sales-curves',
    description: 'No hourly sales curves or intra-day graph charts',
    patterns: [/curva\s*horaria/i, /ventas\s*por\s*hora/i],
  },
  {
    id: 'bank-settlement-and-payouts',
    description: 'No bank settlement, payout accounts or liquidation tables',
    patterns: [/liquidaci[oó]n\s*bancaria/i, /cuenta\s*de\s*abono/i, /transferencia\s*a\s*cuenta\s*corriente/i],
  },
  {
    id: 'product-margin-and-series-7d',
    description: 'No product margin metrics or 7-day sales breakdown series',
    patterns: [/margen\s*por\s*producto/i, /margen\s*bruto/i, /serie\s*7d/i, /ventas\s*7d/i],
  },
  {
    id: 'ai-suggestions-and-stock-alerts',
    description: 'No AI automated suggestions or automatic low-stock alarms',
    patterns: [/sugerencias?\s*de\s*ia/i, /recomendado\s*por\s*ia/i, /alertas?\s*de\s*stock/i],
  },
];

describe('unsupported features guard (spec S17)', () => {
  const uiSourceFiles = [...listSourceFiles('components'), ...listSourceFiles('app')];

  it.each(UNSUPPORTED_FEATURES_RULES)(
    'ensures no authored UI file mentions $id ($description)',
    ({ id, patterns }) => {
      const offenders: Array<{ path: string; match: string }> = [];

      for (const file of uiSourceFiles) {
        for (const pattern of patterns) {
          const match = pattern.exec(file.content);
          if (match) {
            offenders.push({ path: file.path, match: match[0] });
          }
        }
      }

      expect(
        offenders,
        `Forbidden feature "${id}" found in UI source code. Spec S17 prohibits this functionality.`,
      ).toEqual([]);
    },
  );
});
