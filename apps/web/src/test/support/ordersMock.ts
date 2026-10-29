import { jsonResponse } from './fetchMock';
import type { RecordedCall } from './fetchMock';

/**
 * Answers of `orders-service` used by the tests of the orders screens.
 *
 * The shapes are the ones of the service: the envelope `{ success, message,
 * data }` and the DTOs `OrderListResponse` and `OrderDetailResponse`. The
 * prices of the dishes are the ones of the catalog mock, so both suites speak
 * of the same local.
 */

export const ORDER_ID = '00000000-0000-0000-0000-00000000002a';

/** Two orders of the customer, with the shape of `OrderListResponse`. */
export const ORDER_LIST = [
  {
    id: ORDER_ID,
    code: 'FD-42',
    status: 'Preparando',
    address: 'Av. Pedro de Valdivia 1420, Depto 402, Providencia, Santiago',
    subtotal: 12980,
    deliveryFee: 990,
    total: 13970,
    paymentMethod: 'Efectivo',
    clientName: 'Matías Valenzuela',
    clientPhone: '+56 9 1234 5678',
    clientEmail: 'matias@flashdrop.cl',
    restaurantName: 'La Maestranza Burgers',
    deliveryId: null,
    createdAt: '2026-10-28T22:15:00Z',
  },
  {
    id: '00000000-0000-0000-0000-00000000002b',
    code: 'FD-41',
    status: 'Entregado',
    address: 'Tobalaba 4321, Las Condes, Santiago',
    subtotal: 11480,
    deliveryFee: 1490,
    total: 12970,
    paymentMethod: 'Transferencia',
    clientName: 'Matías Valenzuela',
    clientPhone: '+56 9 1234 5678',
    clientEmail: 'matias@flashdrop.cl',
    restaurantName: 'Sakura Nikkei Fusion',
    deliveryId: '00000000-0000-0000-0000-0000000000c3',
    createdAt: '2026-10-27T20:05:00Z',
  },
];

/** The detail of the order, with the shape of `OrderDetailResponse`. */
export const ORDER_DETAIL = {
  id: ORDER_ID,
  code: 'FD-42',
  status: 'Retirado',
  address: 'Av. Pedro de Valdivia 1420, Depto 402, Providencia, Santiago',
  subtotal: 12980,
  deliveryFee: 990,
  total: 13970,
  paymentMethod: 'Efectivo',
  createdAt: '2026-10-28T22:15:00Z',
  client: {
    name: 'Matías Valenzuela',
    email: 'matias@flashdrop.cl',
    phone: '+56 9 1234 5678',
  },
  restaurant: {
    name: 'La Maestranza Burgers',
    address: 'Av. Providencia 1234, Providencia, Santiago',
  },
  delivery: {
    name: 'Carlos Morales',
    phone: '+56 9 8765 4321',
    vehicle: 'Moto Honda CB125F',
  },
  route: {
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia, Santiago',
    deliveryAddress: 'Av. Pedro de Valdivia 1420, Depto 402, Providencia, Santiago',
    distanceKm: 3.2,
    estimatedMinutes: 20,
    status: 'EN_CAMINO',
  },
  items: [
    {
      id: '00000000-0000-0000-0000-000000000001',
      name: 'Doble Smash Cheddar Bacon',
      description: 'Dos hamburguesas de 110g con cheddar fundido y tocino crujiente.',
      image: null,
      quantity: 1,
      unitPrice: 7990,
      total: 7990,
    },
    {
      id: '00000000-0000-0000-0000-000000000002',
      name: 'Papas Rústicas Trufadas',
      description: 'Papas cortadas a mano con piel, trufa blanca y parmesano.',
      image: null,
      quantity: 1,
      unitPrice: 3490,
      total: 3490,
    },
    {
      id: '00000000-0000-0000-0000-000000000003',
      name: 'Bebida Sprite Zero 350cc',
      description: 'Lata helada sellada.',
      image: null,
      quantity: 1,
      unitPrice: 1500,
      total: 1500,
    },
  ],
};

/** Answer of `GET /api/orders`: the envelope of `orders-service`. */
export function orderListResponse(list: unknown = ORDER_LIST): Response {
  return jsonResponse({ success: true, message: 'Pedidos encontrados', data: list });
}

/** Answer of `GET /api/orders/{id}`: the envelope of `orders-service`. */
export function orderDetailResponse(detail: unknown = ORDER_DETAIL): Response {
  return jsonResponse({ success: true, message: 'Pedido encontrado', data: detail });
}

/**
 * Answer the two endpoints of the orders screens.
 *
 * The list and the detail live under the same path, so the detail is the one
 * that carries a segment after `/api/orders/`.
 */
export function ordersResponse(call: RecordedCall): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/api/orders') {
    return orderListResponse();
  }

  if (pathname.startsWith('/api/orders/')) {
    return orderDetailResponse();
  }

  return jsonResponse(
    { status: 404, error: 'NOT_FOUND', message: 'Pedido no encontrado' },
    404,
  );
}
