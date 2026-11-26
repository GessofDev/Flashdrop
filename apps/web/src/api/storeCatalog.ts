import { asList } from './asList';
import { request } from './client';
import type {
  CreateMyStoreRequest,
  MerchantResponse,
  OpeningHours,
  ProductImageUpload,
  ProductResponse,
  RestaurantResponse,
} from './types';

/**
 * Body of the owner endpoints of the catalog.
 *
 * The service derives the owner from the token, so `restaurantId` never
 * travels in the body. Every field is optional because the endpoints accept
 * different shapes: the creation accepts a partial product and the update
 * requires `categoryId`, `name`, `price` and `available`.
 */
export interface OwnerProductPayload {
  /** Category the product belongs to. */
  categoryId?: number;
  /** Name of the product, up to 100 characters. */
  name?: string;
  /** Description of the product, up to 255 characters. */
  description?: string;
  /** Price of the product, in CLP. */
  price?: number;
  /** Key of the object of the picture, not its URL. */
  image?: string;
  /** Whether the product is offered to the customers. */
  available?: boolean;
}

/**
 * `catalog-service` — the local of the owner, with its coverage.
 *
 * The endpoints of the owner publish the coordinates of the local and the
 * radius of its dispatch, so the screen can show what the backend holds and
 * the owner knows whether the orders of the local will be accepted.
 */
export interface OwnerRestaurantResponse extends RestaurantResponse {
  /** Latitude of the local, in degrees; absent when it is not configured. */
  latitude?: number | null;
  /** Longitude of the local, in degrees; absent when it is not configured. */
  longitude?: number | null;
  /** Radius of dispatch of the local, in kilometres; absent when it is not configured. */
  deliveryRadiusKm?: number | null;
}

/**
 * Calls of the catalog of the owner (`catalog-service`).
 *
 * The service answers with the bare DTO — no `{ success, message, data }`
 * envelope — so every call declares `responseShape: 'bare'`. The calls carry
 * the session: the owner is resolved from the token, never from the body.
 */
export const storeCatalogApi = {
  /** `GET /api/catalog/my/restaurants` — all operable stores of the merchant. */
  listMyRestaurants(): Promise<OwnerRestaurantResponse[]> {
    return request<unknown>({
      path: '/api/catalog/my/restaurants',
      method: 'GET',
      responseShape: 'bare',
    }).then((answer) => asList<OwnerRestaurantResponse>(answer));
  },

  /** `GET /api/catalog/my/merchant` — the brand profile of the authenticated operator. */
  getMyMerchant(): Promise<MerchantResponse> {
    return request<MerchantResponse>({
      path: '/api/catalog/my/merchant',
      method: 'GET',
      responseShape: 'bare',
    });
  },

  /** `POST /api/catalog/my/restaurants` — creates a new store inside the merchant group. */
  createMyRestaurant(payload: CreateMyStoreRequest): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurants',
      method: 'POST',
      body: payload,
      responseShape: 'bare',
    });
  },

  /** `GET /api/catalog/my/restaurant` — the local of the token, with its opening. */
  getMyRestaurant(): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurant',
      method: 'GET',
      responseShape: 'bare',
    });
  },

  /** `PUT /api/catalog/my/restaurant/open-status` — the manual switch of the local. */
  setOpenStatus(acceptingOrders: boolean): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurant/open-status',
      method: 'PUT',
      body: { acceptingOrders },
      responseShape: 'bare',
    });
  },

  /** `PUT /api/catalog/my/restaurant/hours` — replaces the whole week of the local. */
  setHours(hours: OpeningHours[]): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurant/hours',
      method: 'PUT',
      body: { hours },
      responseShape: 'bare',
    });
  },

  /**
   * `PUT /api/catalog/my/restaurant/location` — the coordinates of the local.
   *
   * The owner comes from the token, so the body carries only the pair of
   * coordinates: the backend validates the latitude between -90 and 90 and
   * the longitude between -180 and 180.
   */
  setLocation(latitude: number, longitude: number): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurant/location',
      method: 'PUT',
      body: { latitude, longitude },
      responseShape: 'bare',
    });
  },

  /**
   * `PUT /api/catalog/my/restaurant/delivery-radius` — the radius of dispatch
   * of the local.
   *
   * The body carries only the radius: the backend validates it between 1 and
   * 50 kilometres, with up to two decimals.
   */
  setDeliveryRadius(deliveryRadiusKm: number): Promise<OwnerRestaurantResponse> {
    return request<OwnerRestaurantResponse>({
      path: '/api/catalog/my/restaurant/delivery-radius',
      method: 'PUT',
      body: { deliveryRadiusKm },
      responseShape: 'bare',
    });
  },

  /**
   * `GET /api/catalog/my/products` — the products of the restaurant of the
   * token, including the ones that are not available.
   */
  listMyProducts(): Promise<ProductResponse[]> {
    return request<unknown>({
      path: '/api/catalog/my/products',
      method: 'GET',
      responseShape: 'bare',
    }).then((answer) => asList<ProductResponse>(answer));
  },

  /**
   * `POST /api/catalog/my/products` — creates a product of the restaurant of
   * the token.
   */
  createProduct(payload: OwnerProductPayload): Promise<ProductResponse> {
    return request<ProductResponse>({
      path: '/api/catalog/my/products',
      method: 'POST',
      body: payload,
      responseShape: 'bare',
    });
  },

  /**
   * `PUT /api/catalog/my/products/{productId}` — replaces a product.
   *
   * There is no `PATCH` in this endpoint: the request is a full
   * replacement, so the caller sends the whole product.
   */
  updateProduct(
    productId: number,
    payload: OwnerProductPayload,
  ): Promise<ProductResponse> {
    return request<ProductResponse>({
      path: `/api/catalog/my/products/${productId}`,
      method: 'PUT',
      body: payload,
      responseShape: 'bare',
    });
  },

  /**
   * `DELETE /api/catalog/my/products/{productId}` — removes a product.
   *
   * The endpoint answers with a 204 and no body.
   */
  deleteProduct(productId: number): Promise<void> {
    return request<null>({
      path: `/api/catalog/my/products/${productId}`,
      method: 'DELETE',
      responseShape: 'bare',
    }).then(() => undefined);
  },

  /**
   * `POST /api/catalog/my/products/image` — uploads the picture of a product.
   *
   * The body is multipart with the part `file`; the client must not write the
   * `Content-Type` header, because the browser adds the boundary. The answer
   * carries the key of the object, and that key is the `image` of the
   * product.
   */
  uploadImage(file: File): Promise<ProductImageUpload> {
    const form = new FormData();

    form.append('file', file);

    return request<ProductImageUpload>({
      path: '/api/catalog/my/products/image',
      method: 'POST',
      body: form,
      responseShape: 'bare',
    });
  },
};
