import { asList } from './asList';
import { request } from './client';
import type { CourierVehicle, DeliveryPerson } from './delivery';

/** `auth-service` — `AdminUserResponse`. */
export interface AdminUserResponse {
  id: number;
  name: string;
  lastName: string;
  email: string;
  roles: string[];
}

/** `auth-service` — `AssignUserRolesResult`. */
export interface AssignUserRolesResult {
  userId: number;
  roles: Array<{ id: number; name: string }>;
}

/** `catalog-service` — `AdminRestaurantResponse`. */
export interface AdminRestaurantResponse {
  id: number;
  name: string;
  address: string | null;
  ownerUserId: number | null;
  acceptingOrders: boolean;
  open: boolean;
  latitude: number | null;
  longitude: number | null;
}

/** `catalog-service` — `CreateRestaurantRequest`. */
export interface CreateAdminRestaurantRequest {
  name: string;
  address?: string;
  ownerUserId?: number | null;
  latitude?: number | null;
  longitude?: number | null;
}

/** `orders-service` — `AdminDeliveryTariffController.DimensionRangeBody`. */
export interface TariffDimensionRange {
  minDimension: number;
  maxDimension: number | null;
  vehicle: CourierVehicle;
  price: number;
}

/** `orders-service` — `AdminDeliveryTariffController.DistanceRangeBody`. */
export interface TariffDistanceRange {
  minKm: number;
  maxKm: number | null;
  price: number;
}

/** `orders-service` — `AdminDeliveryTariffController.TariffBody`. */
export interface DeliveryTariff {
  dimensionRanges: TariffDimensionRange[];
  distanceRanges: TariffDistanceRange[];
}

/** `orders-service` — `AdminOrderPolicyController.PolicyBody`. */
export interface OrderPolicy {
  paymentExpirationMinutes: number;
  customerCancelWindowMinutes: number;
  courierWaitMinutes: number;
}

/**
 * Admin endpoints across `auth-service`, `catalog-service`, `delivery-service`,
 * and `orders-service` (spec S11, T12).
 */
export const adminApi = {
  /** `GET /auth/admin/users?email=...` */
  findUserByEmail(email: string): Promise<AdminUserResponse> {
    return request<AdminUserResponse>({
      path: '/auth/admin/users',
      method: 'GET',
      responseShape: 'bare',
      query: { email },
    });
  },

  /** `PUT /auth/admin/users/{userId}/roles` */
  assignUserRoles(userId: number, roles: string[]): Promise<AssignUserRolesResult> {
    return request<AssignUserRolesResult>({
      path: `/auth/admin/users/${userId}/roles`,
      method: 'PUT',
      responseShape: 'bare',
      body: { roles },
    });
  },

  /** `GET /api/catalog/admin/restaurants` */
  listRestaurants(): Promise<AdminRestaurantResponse[]> {
    return request<unknown>({
      path: '/api/catalog/admin/restaurants',
      method: 'GET',
      responseShape: 'bare',
    }).then((answer) => asList<AdminRestaurantResponse>(answer));
  },

  /** `POST /api/catalog/admin/restaurants` */
  createRestaurant(payload: CreateAdminRestaurantRequest): Promise<AdminRestaurantResponse> {
    return request<AdminRestaurantResponse>({
      path: '/api/catalog/admin/restaurants',
      method: 'POST',
      responseShape: 'bare',
      body: payload,
    });
  },

  /** `PUT /api/catalog/admin/restaurants/{restaurantId}/owner` */
  assignRestaurantOwner(restaurantId: number, userId: number): Promise<AdminRestaurantResponse> {
    return request<AdminRestaurantResponse>({
      path: `/api/catalog/admin/restaurants/${restaurantId}/owner`,
      method: 'PUT',
      responseShape: 'bare',
      body: { userId },
    });
  },

  /** `POST /api/delivery/admin/persons` */
  createDeliveryPerson(userId: number, vehicle: CourierVehicle): Promise<DeliveryPerson> {
    return request<DeliveryPerson>({
      path: '/api/delivery/admin/persons',
      method: 'POST',
      responseShape: 'envelope',
      body: { userId, vehicle },
    });
  },

  /** `GET /api/admin/delivery-tariff` */
  getDeliveryTariff(): Promise<DeliveryTariff> {
    return request<DeliveryTariff>({
      path: '/api/admin/delivery-tariff',
      method: 'GET',
      responseShape: 'envelope',
    });
  },

  /** `PUT /api/admin/delivery-tariff` */
  replaceDeliveryTariff(tariff: DeliveryTariff): Promise<DeliveryTariff> {
    return request<DeliveryTariff>({
      path: '/api/admin/delivery-tariff',
      method: 'PUT',
      responseShape: 'envelope',
      body: tariff,
    });
  },

  /** `GET /api/admin/order-policy` */
  getOrderPolicy(): Promise<OrderPolicy> {
    return request<OrderPolicy>({
      path: '/api/admin/order-policy',
      method: 'GET',
      responseShape: 'envelope',
    });
  },

  /** `PUT /api/admin/order-policy` */
  replaceOrderPolicy(policy: OrderPolicy): Promise<OrderPolicy> {
    return request<OrderPolicy>({
      path: '/api/admin/order-policy',
      method: 'PUT',
      responseShape: 'envelope',
      body: policy,
    });
  },
};
