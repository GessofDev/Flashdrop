import { asList } from './asList';
import { request } from './client';

/** Maximum number of orders accepted by the atomic claim endpoint. */
export const MAX_CLAIM_ORDERS = 3;

/** Literal message returned when the authenticated user has no courier profile. */
export const MISSING_COURIER_PROFILE_MESSAGE = 'El usuario no tiene perfil de repartidor';

/** Vehicle accepted by the courier profile endpoint. */
export const COURIER_VEHICLES = ['MOTO', 'AUTO', 'BICICLETA', 'PIE'] as const;

export type CourierVehicle = (typeof COURIER_VEHICLES)[number];

/** `orders-service` — `AvailableDeliveryOrderResponse`. */
export interface AvailableDeliveryOrder {
  /** Numeric id consumed by the delivery service. */
  orderId: number;
  /** Numeric id of the restaurant that owns the order. */
  restaurantId: number;
  /** Human code of the order, as the backend spells it. */
  code: string;
  /** Status of the order, as the label of the backend. */
  status: string;
  /** Name of the restaurant. */
  restaurantName: string;
  /** Address where the courier picks the order up. */
  pickupAddress: string;
  /** Address where the courier delivers the order. */
  deliveryAddress: string;
  /** Name of the customer. */
  clientName: string;
  /** Total of the order, in CLP. */
  total: number;
  /** Distance of the route, in kilometres. */
  distanceKm: number | null;
  /** Estimated duration of the route, in minutes. */
  estimatedMinutes: number | null;
}

/** `delivery-service` — `RouteResponse`. */
export interface DeliveryRoute {
  /** Numeric id of the route. */
  id: number;
  /** Numeric id of the order carried by the route. */
  orderId: number;
  /** Address where the courier picks the order up. */
  pickupAddress: string;
  /** Address where the courier delivers the order. */
  deliveryAddress: string;
  /** Distance of the route, in kilometres. */
  distanceKm: number | null;
  /** Estimated duration of the route, in minutes. */
  estimatedMinutes: number | null;
  /** Status of the route, with the spelling of the backend. */
  status: string;
  /** Instant the route was created. */
  createdAt: string;
  /** Human code of the order, when the backend could resolve it. */
  code: string | null;
}

/** `delivery-service` — `DeliveryPersonResponse`. */
export interface DeliveryPerson {
  /** Numeric id of the courier profile. */
  id: number;
  /** Id of the user that owns the profile. */
  userId: string;
  /** Vehicle of the profile. */
  vehicle: string | null;
  /** Instant the profile was created. */
  createdAt: string;
}

/**
 * Calls of the courier console.
 *
 * `orders-service` and `delivery-service` answer with the
 * `{ success, message, data }` envelope, so every call declares
 * `responseShape: 'envelope'`.
 */
export const courierApi = {
  /**
   * `GET /api/orders/available-for-delivery` — the FIFO queue of one restaurant.
   *
   * The current endpoint is scoped by `restaurant_id`; the UI asks the catalog
   * for the restaurants and keeps the answer exactly in the order the backend
   * returned it.
   */
  listAvailableOrders(restaurantId: number, limit = 50): Promise<AvailableDeliveryOrder[]> {
    return request<unknown>({
      path: '/api/orders/available-for-delivery',
      method: 'GET',
      responseShape: 'envelope',
      query: { restaurant_id: restaurantId, limit },
    }).then((answer) => asList<AvailableDeliveryOrder>(answer));
  },

  /**
   * `POST /api/delivery/claim` — atomically claims up to three orders.
   *
   * The identity of the courier comes from the token, so only the ids of the
   * orders travel in the body.
   */
  claimOrders(orderIds: number[]): Promise<DeliveryPerson[]> {
    return request<DeliveryPerson[]>({
      path: '/api/delivery/claim',
      method: 'POST',
      responseShape: 'envelope',
      body: { orderIds },
    });
  },

  /** `GET /api/delivery/routes` — active and completed routes of the courier. */
  listRoutes(): Promise<DeliveryRoute[]> {
    return request<unknown>({
      path: '/api/delivery/routes',
      method: 'GET',
      responseShape: 'envelope',
    }).then((answer) => asList<DeliveryRoute>(answer));
  },

  /**
   * `POST /api/delivery/persons` — creates the courier profile of the token.
   *
   * The endpoint exists so a user with the courier role can recover from a
   * missing profile without leaving the console.
   */
  createProfile(vehicle: CourierVehicle): Promise<DeliveryPerson> {
    return request<DeliveryPerson>({
      path: '/api/delivery/persons',
      method: 'POST',
      responseShape: 'envelope',
      body: { vehicle },
    });
  },
};
