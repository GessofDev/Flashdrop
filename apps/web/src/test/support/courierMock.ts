import { jsonResponse } from './fetchMock';
import type { RecordedCall } from './fetchMock';

/** Restaurants used to discover the ids accepted by the available-orders endpoint. */
export const COURIER_RESTAURANTS = [
  {
    id: 1,
    name: 'La Maestranza Burgers',
    address: 'Av. Providencia 1234, Providencia, Santiago',
    phone: null,
    image: null,
  },
];

/** Available orders deliberately returned out of distance order. */
export const AVAILABLE_DELIVERY_ORDERS = [
  {
    orderId: 501,
    restaurantId: 1,
    code: 'FD-50',
    status: 'Listo para retiro',
    restaurantName: 'La Maestranza Burgers',
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
    deliveryAddress: 'El Bosque Norte 230, Las Condes',
    clientName: 'Matías Valenzuela',
    total: 3200,
    distanceKm: 2.2,
    estimatedMinutes: 24,
  },
  {
    orderId: 502,
    restaurantId: 1,
    code: 'FD-49',
    status: 'Listo para retiro',
    restaurantName: 'La Maestranza Burgers',
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
    deliveryAddress: 'Costanera Center, Torre 2, Piso 14',
    clientName: 'Camila Rojas',
    total: 2600,
    distanceKm: 0.9,
    estimatedMinutes: 12,
  },
  {
    orderId: 503,
    restaurantId: 1,
    code: 'FD-51',
    status: 'Listo para retiro',
    restaurantName: 'La Maestranza Burgers',
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
    deliveryAddress: 'Pocuro 2140, Providencia',
    clientName: 'Valentina Mora',
    total: 3850,
    distanceKm: 1.8,
    estimatedMinutes: 19,
  },
  {
    orderId: 504,
    restaurantId: 1,
    code: 'FD-52',
    status: 'Listo para retiro',
    restaurantName: 'La Maestranza Burgers',
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
    deliveryAddress: 'Los Militares 4700, Las Condes',
    clientName: 'Nicolás Valenzuela',
    total: 4100,
    distanceKm: 3.1,
    estimatedMinutes: 31,
  },
];

/** One active route and one completed route. */
export const COURIER_ROUTES = [
  {
    id: 901,
    orderId: 501,
    pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
    deliveryAddress: 'El Bosque Norte 230, Las Condes',
    distanceKm: 2.2,
    estimatedMinutes: 24,
    status: 'ASSIGNED',
    createdAt: '2026-10-28T22:15:00Z',
    code: 'FD-50',
  },
  {
    id: 899,
    orderId: 499,
    pickupAddress: 'Sakura Nikkei, Tobalaba 4321, Las Condes',
    deliveryAddress: 'Av. Apoquindo 4500, Depto 91, Las Condes',
    distanceKm: 4.6,
    estimatedMinutes: 36,
    status: 'ENTREGADO',
    createdAt: '2026-10-28T20:30:00Z',
    code: 'FD-48',
  },
];

export interface CourierMockState {
  /** Orders returned by the available-orders endpoint. */
  availableOrders: unknown[];
  /** Routes returned by the routes endpoint. */
  routes: unknown[];
  /** Number of calls to the available-orders endpoint. */
  availableCalls: number;
  /** Optional replacement for the available-orders answer. */
  availableResponse?: () => Response;
  /** Optional replacement for the claim answer. */
  claimResponse?: () => Response;
  /** Optional replacement for the route listing answer. */
  routesResponse?: () => Response;
  /** Optional replacement for the profile creation answer. */
  profileResponse?: () => Response;
}

/** Mutable state for the courier endpoints. */
export function courierMockState(): CourierMockState {
  return {
    availableOrders: AVAILABLE_DELIVERY_ORDERS,
    routes: COURIER_ROUTES,
    availableCalls: 0,
  };
}

/** Answer the endpoints used by the courier screens. */
export function courierResponse(call: RecordedCall, state: CourierMockState): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/catalog/restaurants') {
    return jsonResponse(COURIER_RESTAURANTS);
  }

  if (pathname === '/api/orders/available-for-delivery') {
    state.availableCalls += 1;

    if (state.availableResponse !== undefined) {
      return state.availableResponse();
    }

    return jsonResponse({
      success: true,
      message: 'Pedidos disponibles',
      data: state.availableOrders,
    });
  }

  if (pathname === '/api/delivery/claim') {
    if (state.claimResponse !== undefined) {
      return state.claimResponse();
    }

    return jsonResponse(
      {
        success: true,
        message: 'Delivery claimed successfully',
        data: [
          {
            id: 7,
            userId: '42',
            vehicle: 'MOTO',
            createdAt: '2026-10-28T22:00:00Z',
          },
        ],
      },
      201,
    );
  }

  if (pathname === '/api/delivery/persons') {
    if (state.profileResponse !== undefined) {
      return state.profileResponse();
    }

    return jsonResponse(
      {
        success: true,
        message: 'Delivery person profile created',
        data: {
          id: 7,
          userId: '42',
          vehicle: 'MOTO',
          createdAt: '2026-10-28T22:00:00Z',
        },
      },
      201,
    );
  }

  if (pathname === '/api/delivery/routes') {
    if (state.routesResponse !== undefined) {
      return state.routesResponse();
    }

    return jsonResponse({
      success: true,
      message: 'Routes retrieved successfully',
      data: state.routes,
    });
  }

  if (pathname.startsWith('/api/orders/') && pathname.endsWith('/status')) {
    const body = call.body as { status?: unknown } | null;
    const orderId = Number(pathname.replace('/api/orders/', '').replace('/status', ''));

    if (body?.status === 'Retirado' || body?.status === 'Entregado') {
      const routeStatus = body.status === 'Retirado' ? 'EN_CAMINO' : 'ENTREGADO';

      state.routes = state.routes.map((route) =>
        (route as { orderId: number }).orderId === orderId
          ? { ...(route as object), status: routeStatus }
          : route,
      );
    }

    return jsonResponse({ success: true, message: 'Estado actualizado', data: null });
  }

  return jsonResponse(
    { status: 404, error: 'NOT_FOUND', message: 'Recurso no encontrado' },
    404,
  );
}
