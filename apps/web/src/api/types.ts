/**
 * DTOs of the FlashDrop backend that the web client consumes today.
 *
 * Every shape mirrors the Java record it comes from; the source of truth is
 * the service itself:
 *
 * - `auth-service`: `TokenPair`, `AuthResult`, `LoginRequest`,
 *   `RefreshRequest`, `TokenClaims`, `UserProfile`.
 * - `catalog-service`: the bare DTO of the catalog — `RestaurantResponse`,
 *   `ProductResponse`, `CategoryResponse`.
 * - `orders-service` / `delivery-service`: the `{ success, message, data }`
 *   envelope.
 *
 * The KDS and courier DTOs are deliberately absent: they belong to the tasks
 * that build those screens.
 */

/**
 * Response envelope of `orders-service` and `delivery-service`.
 *
 * `message` is present on both the success and the failure shape, so it is
 * the field the client reads when the backend reports a problem.
 */
export interface ApiEnvelope<T> {
  success: boolean;
  message?: string | null;
  data?: T | null;
}

/** `auth-service` — `TokenPair`. */
export interface TokenPair {
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

/** `auth-service` — `AuthResult`, the answer of `POST /auth/login`. */
export interface AuthResult {
  userId: number;
  name: string;
  email: string;
  /** Role names as the backend spells them: `CLIENTE`, `RESTAURANTE`, `REPARTIDOR`. */
  roles: string[];
  accessToken: string;
  refreshToken: string;
  expiresInSeconds: number;
}

/** `auth-service` — `LoginRequest`. The `login` field carries the identifier. */
export interface LoginRequest {
  login: string;
  password: string;
}

/** `auth-service` — `RegisterRequest`, the body of `POST /auth/register`. */
export interface RegisterRequest {
  email: string;
  password: string;
  rut?: string | null;
  name?: string | null;
  lastName?: string | null;
  phone?: string | null;
}

/** `auth-service` — `RegisterUserResult`, the answer of `POST /auth/register`. */
export interface RegisterUserResult {
  userId: number;
}

/** `auth-service` — `RefreshRequest`. */
export interface RefreshRequest {
  refreshToken: string;
}

/** `auth-service` — `TokenClaims`, the answer of `GET /auth/validate`. */
export interface TokenClaims {
  userId: number;
  email: string;
  roles: string[];
}

/** `auth-service` — `UserProfile`, the answer of `GET|PUT /auth/profile`. */
export interface UserProfile {
  userId: number;
  name: string;
  lastName: string;
  email: string;
  phone: string | null;
  rut: string | null;
  photo: string | null;
  roles: string[];
}

/** `auth-service` — `UpdateProfileRequest`, the body of `PUT /auth/profile`. */
export interface UpdateProfileRequest {
  name: string;
  lastName: string;
  phone?: string | null;
  photo?: string | null;
}

/** `catalog-service` — `RestaurantResponse`, the answer of `GET /catalog/restaurants`. */
export interface RestaurantResponse {
  id: number;
  name: string;
  address: string;
  phone: string | null;
  /** The catalog may answer without a picture. */
  image: string | null;
}

/** `catalog-service` — `ProductResponse`, the answer of `GET /catalog/products`. */
export interface ProductResponse {
  id: number;
  categoryId: number;
  restaurantId: number;
  name: string;
  description: string;
  price: number;
  image: string | null;
  available: boolean;
}

/** `catalog-service` — `CategoryResponse`, the answer of `GET /catalog/categories`. */
export interface CategoryResponse {
  id: number;
  name: string;
  description: string;
  image: string | null;
}

/**
 * `catalog-service` — `ProductImageUploadResponse`, the answer of
 * `POST /api/catalog/my/products/image`.
 */
export interface ProductImageUpload {
  /**
   * Key of the object in the storage.
   *
   * It is the value of the `image` field of a product: the pattern the
   * backend validates is `products/<year>/<month>/<uuid>.<extension>`.
   */
  objectKey: string;
  /** Public URL of the uploaded image. */
  url: string;
}

/** `orders-service` — `CreateOrderRequest`, the body of `POST /api/orders`. */
export interface CreateOrderRequest {
  /** Delivery address; the backend rejects an empty one. */
  address: string;
  /** Payment method declared by the customer: `Efectivo`, `Tarjeta` or `Transferencia`. */
  payment_method: string;
  /** Estimated distance in kilometres; the backend applies its own default. */
  distance_km?: number;
  /** Estimated minutes of the dispatch; the backend applies its own default. */
  estimated_minutes?: number;
  items: OrderItemRequest[];
}

/** `orders-service` — `CreateOrderRequest.ItemRequest`. */
export interface OrderItemRequest {
  product_id: number;
  quantity: number;
}

/** `orders-service` — `CreatedOrderResult`, the answer of `POST /api/orders`. */
export interface CreatedOrderResult {
  /** Id of the created order. */
  id: string;
  /** Total charged by the backend, in CLP. */
  total: number;
}

/**
 * `orders-service` — `OrderListResponse`, the answer of `GET /api/orders`.
 *
 * It is the abbreviated DTO of the listing: the identity of the order, the
 * restaurant, the status and the money. The client and the delivery fields
 * are there for the other roles of the list, so the customer screen uses only
 * the ones it needs.
 */
export interface OrderListItem {
  /** Id of the order. */
  id: string;
  /** Human code of the order, e.g. `FD-42`. The backend owns its format. */
  code: string;
  /** Status of the order, as the label of the `OrderStatus` of the backend. */
  status: string;
  /** Delivery address of the order. */
  address: string;
  /** Sum of the dishes, in CLP. */
  subtotal: number;
  /** Price of the dispatch, in CLP. */
  deliveryFee: number;
  /** Total charged by the backend, in CLP. */
  total: number;
  /** Payment method declared by the customer. */
  paymentMethod: string;
  /** Name of the customer of the order. */
  clientName: string;
  /** Phone of the customer of the order. */
  clientPhone: string;
  /** Email of the customer of the order. */
  clientEmail: string;
  /** Name of the restaurant the order belongs to. */
  restaurantName: string;
  /** Id of the courier, when the order already has one. */
  deliveryId: string | null;
  /** Instant the order was created, as an ISO-8601 offset date time. */
  createdAt: string;
}

/**
 * `orders-service` — `OrderDetailResponse`, the answer of `GET /api/orders/{id}`.
 *
 * The `client`, `restaurant`, `delivery` and `route` blocks are nullable: the
 * backend only fills them when it can resolve the data, so the UI shows them
 * only when they arrive.
 */
export interface OrderDetail {
  /** Id of the order. */
  id: string;
  /** Human code of the order, e.g. `FD-42`. The backend owns its format. */
  code: string;
  /** Status of the order, as the label of the `OrderStatus` of the backend. */
  status: string;
  /** Delivery address of the order. */
  address: string;
  /** Sum of the dishes, in CLP. */
  subtotal: number;
  /** Price of the dispatch, in CLP. */
  deliveryFee: number;
  /** Total charged by the backend, in CLP. */
  total: number;
  /** Payment method declared by the customer. */
  paymentMethod: string;
  /** Instant the order was created, as an ISO-8601 offset date time. */
  createdAt: string;
  /** Customer of the order. */
  client: OrderClient | null;
  /** Restaurant the order belongs to. */
  restaurant: OrderRestaurant | null;
  /** Courier that carries the order. */
  delivery: OrderDelivery | null;
  /** Route of the delivery. */
  route: OrderRoute | null;
  /** Dishes of the order. */
  items: OrderItem[];
}

/** `orders-service` — `OrderDetailResponse.ClientDto`. */
export interface OrderClient {
  name: string;
  email: string | null;
  phone: string | null;
}

/** `orders-service` — `OrderDetailResponse.RestaurantDto`. */
export interface OrderRestaurant {
  name: string;
  address: string | null;
}

/** `orders-service` — `OrderDetailResponse.DeliveryDto`. */
export interface OrderDelivery {
  name: string;
  phone: string | null;
  vehicle: string | null;
}

/**
 * `orders-service` — `OrderDetailResponse.RouteDto`.
 *
 * The route carries the two addresses and the status of the delivery. It
 * carries no coordinates: the backend does not expose the position of the
 * courier, so the map of the app can only be a schematic one.
 */
export interface OrderRoute {
  pickupAddress: string;
  deliveryAddress: string;
  distanceKm: number | null;
  estimatedMinutes: number | null;
  /** Status of the route, as the `RouteStatus` of `delivery-service`. */
  status: string;
}

/** `orders-service` — `OrderDetailResponse.ItemDto`. */
export interface OrderItem {
  id: string;
  name: string;
  description: string | null;
  image: string | null;
  quantity: number;
  /** Price of one unit, in CLP. */
  unitPrice: number;
  /** Price of the line, in CLP. */
  total: number;
}

/** Value accepted in a query string. */
export type QueryValue = string | number | boolean | null | undefined;

/** Query string parameters of a request. */
export type QueryParams = Record<string, QueryValue>;
