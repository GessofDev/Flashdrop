import { jsonResponse } from './fetchMock';
import type { RecordedCall } from './fetchMock';

/**
 * Answers of `orders-service` used by the tests of the kitchen board.
 *
 * The listing answers with the abbreviated DTO of the service — it carries no
 * dishes — so the board reads the dishes of every card with its own call to
 * `GET /api/orders/{id}`, and the drawer reuses that answer.
 */

export const KITCHEN_PENDING_ID = '00000000-0000-0000-0000-000000000046';
export const KITCHEN_PREPARING_ID = '00000000-0000-0000-0000-000000000042';
export const KITCHEN_READY_ID = '00000000-0000-0000-0000-000000000040';
export const KITCHEN_CLOSED_ID = '00000000-0000-0000-0000-000000000039';

/** Listing of the restaurant, with the shape of `OrderListResponse`. */
export const KITCHEN_ORDERS = [
  {
    id: KITCHEN_PENDING_ID,
    code: 'FD-46',
    status: 'Nuevo pedido',
    address: 'Av. Pocuro 1930, Depto 402, Providencia, Santiago',
    subtotal: 18470,
    deliveryFee: 1990,
    total: 20460,
    paymentMethod: 'Efectivo',
    clientName: 'Camila Rojas',
    clientPhone: '+56 9 8765 4321',
    clientEmail: 'camila@flashdrop.cl',
    restaurantName: 'La Maestranza Burgers',
    deliveryId: null,
    createdAt: '2026-10-28T22:15:00Z',
  },
  {
    id: KITCHEN_PREPARING_ID,
    code: 'FD-42',
    status: 'Preparando',
    address: 'Tobalaba 4321, Depto 12, Las Condes, Santiago',
    subtotal: 13490,
    deliveryFee: 1490,
    total: 14980,
    paymentMethod: 'Transferencia',
    clientName: 'Nicolás Valenzuela',
    clientPhone: '+56 9 1111 2222',
    clientEmail: 'nicolas@flashdrop.cl',
    restaurantName: 'La Maestranza Burgers',
    deliveryId: null,
    createdAt: '2026-10-28T22:20:00Z',
  },
  {
    id: KITCHEN_READY_ID,
    code: 'FD-40',
    status: 'Listo para retiro',
    address: 'Los Militares 4700, Depto 305, Las Condes, Santiago',
    subtotal: 9980,
    deliveryFee: 990,
    total: 10970,
    paymentMethod: 'Tarjeta',
    clientName: 'Valentina Mora',
    clientPhone: '+56 9 3333 4444',
    clientEmail: 'valentina@flashdrop.cl',
    restaurantName: 'La Maestranza Burgers',
    deliveryId: null,
    createdAt: '2026-10-28T22:24:00Z',
  },
  {
    id: KITCHEN_CLOSED_ID,
    code: 'FD-39',
    status: 'Entregado',
    address: 'Av. Apoquindo 4500, Depto 91, Las Condes, Santiago',
    subtotal: 8990,
    deliveryFee: 1990,
    total: 10980,
    paymentMethod: 'Efectivo',
    clientName: 'Camila Rojas',
    clientPhone: '+56 9 8765 4321',
    clientEmail: 'camila@flashdrop.cl',
    restaurantName: 'La Maestranza Burgers',
    deliveryId: '00000000-0000-0000-0000-0000000000c3',
    createdAt: '2026-10-28T21:00:00Z',
  },
];

/** Detail of every order the board shows, with the shape of `OrderDetailResponse`. */
export const KITCHEN_DETAILS: Record<string, unknown> = {
  [KITCHEN_PENDING_ID]: {
    id: KITCHEN_PENDING_ID,
    code: 'FD-46',
    status: 'Nuevo pedido',
    address: 'Av. Pocuro 1930, Depto 402, Providencia, Santiago',
    subtotal: 18470,
    deliveryFee: 1990,
    total: 20460,
    paymentMethod: 'Efectivo',
    createdAt: '2026-10-28T22:15:00Z',
    client: {
      name: 'Camila Rojas',
      email: 'camila@flashdrop.cl',
      phone: '+56 9 8765 4321',
    },
    restaurant: {
      name: 'La Maestranza Burgers',
      address: 'Av. Providencia 1234, Providencia, Santiago',
    },
    delivery: null,
    route: null,
    items: [
      {
        id: '00000000-0000-0000-0000-000000000046',
        name: 'Doble Smash Burger',
        description: 'Dos hamburguesas de 110g con cheddar fundido.',
        image: null,
        quantity: 2,
        unitPrice: 7490,
        total: 14980,
      },
      {
        id: '00000000-0000-0000-0000-000000000047',
        name: 'Papas Rústicas Trufadas',
        description: 'Papas con piel, trufa blanca y parmesano.',
        image: null,
        quantity: 1,
        unitPrice: 3490,
        total: 3490,
      },
    ],
  },
  [KITCHEN_PREPARING_ID]: {
    id: KITCHEN_PREPARING_ID,
    code: 'FD-42',
    status: 'Preparando',
    address: 'Tobalaba 4321, Depto 12, Las Condes, Santiago',
    subtotal: 13490,
    deliveryFee: 1490,
    total: 14980,
    paymentMethod: 'Transferencia',
    createdAt: '2026-10-28T22:20:00Z',
    client: {
      name: 'Nicolás Valenzuela',
      email: 'nicolas@flashdrop.cl',
      phone: '+56 9 1111 2222',
    },
    restaurant: {
      name: 'La Maestranza Burgers',
      address: 'Av. Providencia 1234, Providencia, Santiago',
    },
    delivery: null,
    route: null,
    items: [
      {
        id: '00000000-0000-0000-0000-000000000042',
        name: 'Bacon Crispy Burger',
        description: 'Hamburguesa de 110g con tocino crujiente.',
        image: null,
        quantity: 1,
        unitPrice: 8490,
        total: 8490,
      },
      {
        id: '00000000-0000-0000-0000-000000000043',
        name: 'Coca-Cola Zero 350ml',
        description: 'Lata helada sellada.',
        image: null,
        quantity: 2,
        unitPrice: 2500,
        total: 5000,
      },
    ],
  },
  [KITCHEN_READY_ID]: {
    id: KITCHEN_READY_ID,
    code: 'FD-40',
    status: 'Listo para retiro',
    address: 'Los Militares 4700, Depto 305, Las Condes, Santiago',
    subtotal: 9980,
    deliveryFee: 990,
    total: 10970,
    paymentMethod: 'Tarjeta',
    createdAt: '2026-10-28T22:24:00Z',
    client: {
      name: 'Valentina Mora',
      email: 'valentina@flashdrop.cl',
      phone: '+56 9 3333 4444',
    },
    restaurant: {
      name: 'La Maestranza Burgers',
      address: 'Av. Providencia 1234, Providencia, Santiago',
    },
    delivery: null,
    route: null,
    items: [
      {
        id: '00000000-0000-0000-0000-000000000040',
        name: 'Smash Simple con Queso',
        description: 'Hamburguesa de 110g con queso mantecoso.',
        image: null,
        quantity: 1,
        unitPrice: 5990,
        total: 5990,
      },
      {
        id: '00000000-0000-0000-0000-000000000041',
        name: 'Aros de Cebolla Crunch',
        description: 'Aros de cebolla empanizados.',
        image: null,
        quantity: 1,
        unitPrice: 3990,
        total: 3990,
      },
    ],
  },
};

/**
 * Mutable state of the mock.
 *
 * The tests change it between calls — to add an order to the board, to reject
 * a status change or to answer with a failure — without rebuilding the
 * responder.
 */
export interface KitchenMockState {
  /** Orders the listing answers with. */
  orders: unknown[];
  /** Details the endpoint answers with, by order id. */
  details: Record<string, unknown>;
  /** Answer of the status update. */
  statusResponse: () => Response;
}

/** A state that answers the happy path of the three endpoints. */
export function kitchenMockState(): KitchenMockState {
  return {
    orders: KITCHEN_ORDERS,
    details: KITCHEN_DETAILS,
    statusResponse: () =>
      jsonResponse({ success: true, message: 'Estado actualizado', data: null }),
  };
}

/**
 * Answer of the three endpoints of the kitchen board.
 *
 * - `GET /api/orders` — the listing of the restaurant.
 * - `GET /api/orders/{id}` — the detail of one order, with its dishes.
 * - `PUT /api/orders/{id}/status` — the change of the status.
 */
export function kitchenResponse(call: RecordedCall, state: KitchenMockState): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/api/orders') {
    return jsonResponse({ success: true, message: 'Pedidos encontrados', data: state.orders });
  }

  if (pathname.endsWith('/status')) {
    const response = state.statusResponse();

    // The backend persists the new status, so the listing answers with it.
    if (response.ok) {
      const body = call.body as { status?: unknown } | null;
      const status = body?.status;

      if (typeof status === 'string') {
        const orderId = pathname.replace('/api/orders/', '').replace('/status', '');

        state.orders = state.orders.map((order) =>
          (order as { id: string }).id === orderId ? { ...(order as object), status } : order,
        );
      }
    }

    return response;
  }

  const orderId = pathname.replace('/api/orders/', '');

  if (orderId.length > 0) {
    const detail = state.details[orderId];

    if (detail !== undefined) {
      return jsonResponse({ success: true, message: 'Pedido encontrado', data: detail });
    }
  }

  return jsonResponse(
    { status: 404, error: 'NOT_FOUND', message: 'Pedido no encontrado' },
    404,
  );
}
