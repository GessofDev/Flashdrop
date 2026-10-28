/**
 * Canonical FlashDrop Test Fixture (Spec S16).
 *
 * Reconciles the conflicting mock data from `references/stitch_flashdrop_ui_design_specs`:
 *
 * 1. Company RUT: "77.492.120-K" (discards "76.892.410-K")
 * 2. Order #FD-0042: 3 items, subtotal $12.980, delivery fee $990, total $13.970
 *    (discards 2 items / "$12.970" and "$10.526")
 * 3. Client for order #FD-0042: "Matías Valenzuela" (discards "Carlos M.")
 * 4. Truffle rustic fries unit price: $3.490 (discards "$4.490" and "$2.990")
 * 5. Delivery PINs: "5821" and "4082" are strictly discarded — no PIN exists
 *    in backend orders-service or delivery-service, and is never rendered in UI (S17).
 */

export interface CanonicalOrderItem {
  readonly id: string;
  readonly name: string;
  readonly description: string;
  readonly quantity: number;
  readonly unitPrice: number;
  readonly total: number;
}

export interface CanonicalOrder {
  readonly id: string;
  readonly code: string;
  readonly displayCode: string;
  readonly status: 'Nuevo pedido' | 'Preparando' | 'Listo para retiro' | 'Retirado' | 'Entregado';
  readonly address: string;
  readonly subtotal: number;
  readonly deliveryFee: number;
  readonly total: number;
  readonly paymentMethod: string;
  readonly client: {
    readonly name: string;
    readonly email: string;
    readonly phone: string;
    readonly rut: string;
  };
  readonly restaurant: {
    readonly id: number;
    readonly name: string;
    readonly address: string;
    readonly phone: string;
  };
  readonly delivery: {
    readonly name: string;
    readonly phone: string;
    readonly vehicle: string;
  };
  readonly items: ReadonlyArray<CanonicalOrderItem>;
}

export const CANONICAL_COMPANY = {
  name: 'FlashDrop Chile SpA',
  rut: '77.492.120-K',
  discardedRuts: ['76.892.410-K'] as const,
} as const;

export const CANONICAL_ORDER_ITEMS: ReadonlyArray<CanonicalOrderItem> = [
  {
    id: '00000000-0000-0000-0000-000000000001',
    name: 'Doble Smash Cheddar Bacon',
    description: 'Dos hamburguesas de 110g con cheddar fundido y tocino crujiente.',
    quantity: 1,
    unitPrice: 7990,
    total: 7990,
  },
  {
    id: '00000000-0000-0000-0000-000000000002',
    name: 'Papas Rústicas Trufadas',
    description: 'Papas cortadas a mano con piel, trufa blanca y parmesano.',
    quantity: 1,
    unitPrice: 3490,
    total: 3490,
  },
  {
    id: '00000000-0000-0000-0000-000000000003',
    name: 'Bebida Sprite Zero 350cc',
    description: 'Lata helada sellada.',
    quantity: 1,
    unitPrice: 1500,
    total: 1500,
  },
] as const;

export const CANONICAL_ORDER: CanonicalOrder = {
  id: '00000000-0000-0000-0000-00000000002a',
  code: 'FD-42',
  displayCode: '#FD-0042',
  status: 'Preparando',
  address: 'Av. Pedro de Valdivia 1420, Depto 402, Providencia, Santiago',
  subtotal: 12980,
  deliveryFee: 990,
  total: 13970,
  paymentMethod: 'Efectivo',
  client: {
    name: 'Matías Valenzuela',
    email: 'matias@flashdrop.cl',
    phone: '+56 9 1234 5678',
    rut: '16.482.915-K',
  },
  restaurant: {
    id: 1,
    name: 'La Maestranza Burgers',
    address: 'Av. Providencia 1234, Providencia, Santiago',
    phone: '+56 2 2345 6789',
  },
  delivery: {
    name: 'Carlos Morales',
    phone: '+56 9 8765 4321',
    vehicle: 'Moto Honda CB125F',
  },
  items: CANONICAL_ORDER_ITEMS,
};

/**
 * Values explicitly rejected by Spec S16 because they stem from divergent UI mockups
 * and must never be reintroduced into the codebase.
 */
export const DISCARDED_FIXTURE_VALUES = {
  companyRuts: ['76.892.410-K'] as const,
  orderTotals: [12970, 10526] as const,
  clientNames: ['Carlos M.'] as const,
  rusticFriesPrices: [4490, 2990] as const,
  deliveryPins: ['5821', '4082'] as const,
} as const;
