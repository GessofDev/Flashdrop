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
  /**
   * Whether the local takes orders right now. The web and the catalog deploy
   * apart, so only an explicit `false` means closed.
   */
  open?: boolean;
  /** Manual switch of the local. */
  acceptingOrders?: boolean;
  /** Week of the local; empty when it depends only on the switch. */
  hours?: OpeningHours[];
  /**
   * Average of the scores of the local.
   *
   * The catalog publishes it when it already holds the aggregate of the
   * ratings; until then the field does not travel and the screens show
   * nothing instead of a number nobody can back.
   */
  ratingAverage?: number | null;
  /** Number of scores behind the average of the local. */
  ratingCount?: number | null;
}

/** One day of the week of a local. `closesAt` before `opensAt` ends the next day. */
export interface OpeningHours {
  /** ISO day: 1 is Monday. */
  day: number;
  /** `HH:mm`, local time. */
  opensAt: string;
  /** `HH:mm`, local time. */
  closesAt: string;
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
  /**
   * Latitude of the destination.
   *
   * It travels with the longitude, and the backend rejects one without the
   * other. With the coordinates the backend calculates the distance itself
   * and ignores the declared `distance_km`.
   */
  latitude?: number;
  /** Longitude of the destination; it always travels with the latitude. */
  longitude?: number;
  /** Estimated distance in kilometres; the backend applies its own default. */
  distance_km?: number;
  /** Estimated minutes of the dispatch; the backend applies its own default. */
  estimated_minutes?: number;
  items: OrderItemRequest[];
}

/**
 * `orders-service` — `QuoteOrderRequest`, the body of
 * `POST /api/orders/delivery-quote`.
 *
 * The quote needs the coordinates of the destination: without them the
 * backend cannot validate the coverage of the local.
 */
export interface DeliveryQuoteRequest {
  /** Dishes of the cart the quote is calculated for. */
  items: OrderItemRequest[];
  /** Latitude of the destination; it travels with the longitude. */
  latitude?: number;
  /** Longitude of the destination; it travels with the latitude. */
  longitude?: number;
}

/**
 * `orders-service` — `QuoteResponse`, the answer of
 * `POST /api/orders/delivery-quote`.
 *
 * The amounts are in CLP. `defaultFee` means the local has no tariff loaded
 * and the fallback fee was charged, so the breakdown does not apply and the
 * vehicle and the costs travel empty.
 */
export interface DeliveryQuote {
  /** Sum of the dishes, in CLP. */
  subtotal: number;
  /** Price of the dispatch, in CLP. */
  deliveryFee: number;
  /** Total of the order, in CLP. */
  total: number;
  /** Whether the price of the dispatch is the fallback one. */
  defaultFee: boolean;
  /** Vehicle the dispatch needs, when the tariff declares one. */
  vehicle: string | null;
  /** Dimension of the order, the sum of the dimensions of its dishes. */
  dimension: number | null;
  /** Distance between the local and the destination, in kilometres. */
  distanceKm: number | null;
  /** Part of the price of the dispatch that comes from the dimension. */
  dimensionCost: number | null;
  /** Part of the price of the dispatch that comes from the distance. */
  distanceCost: number | null;
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
  /**
   * Reception mode of the order: `NORMAL` or `SEGURO`.
   *
   * The backend fixes it when the order is created and never changes it.
   */
  receptionMode: string | null;
  /** Reason code of an automatic cancellation. */
  cancelReason?: string | null;
  /** Text of the cancellation, ready to show. */
  cancelReasonMessage?: string | null;
  /** Instant of the cancellation, as an ISO-8601 offset date time. */
  cancelledAt?: string | null;
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
  /**
   * Reception mode of the order: `NORMAL` or `SEGURO`.
   *
   * The backend fixes it when the order is created and never changes it.
   */
  receptionMode: string | null;
  /**
   * Verification code of a secure order.
   *
   * The backend only fills it for the customer who owns the order, in the
   * secure mode and while the order is not delivered.
   */
  deliveryCode?: string | null;
  /** Reason code of an automatic cancellation. */
  cancelReason?: string | null;
  /** Text of the cancellation, ready to show. */
  cancelReasonMessage?: string | null;
  /** Instant of the cancellation. */
  cancelledAt?: string | null;
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
  id?: number | null;
  pickupAddress: string;
  deliveryAddress: string;
  distanceKm: number | null;
  estimatedMinutes: number | null;
  /** Status of the route, as the `RouteStatus` of `delivery-service`. */
  status: string;
}

/** `delivery-service` — `RouteLocationResponse`, the answer of `GET /api/delivery/routes/{routeId}/location`. */
export interface RouteLocationResponse {
  routeId: number;
  latitude: number;
  longitude: number;
  recordedAt: string;
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

/**
 * `orders-service` — `PaymentView`, the answer of `GET /api/orders/{id}/payments`.
 *
 * It is the state of the order and of its last attempt of payment. The
 * `paymentStatus` is the **name** of the `PaymentStatus` of the backend
 * (`PENDIENTE`, `APROBADO`, `RECHAZADO`, `EXPIRADO`), not a label: it is
 * empty while the order has no payment yet.
 */
export interface OrderPayment {
  /** Status of the order, as the label of the `OrderStatus` of the backend. */
  orderStatus: string;
  /** Name of the `PaymentStatus` of the backend, or `null` without a payment. */
  paymentStatus: string | null;
  /** Id of the last attempt of payment. */
  paymentId: number | null;
  /**
   * Url of the payment session.
   *
   * The backend answers with a fake scheme (`fake://flashdrop-pay/...`):
   * the app never opens it, because there is no payment gateway behind it.
   */
  paymentUrl: string | null;
  /** Deadline of the payment window, as an ISO-8601 offset date time. */
  paymentExpiresAt: string | null;
}

/**
 * `orders-service` — `OpenPayment`, the answer of `POST /api/orders/{id}/payments`.
 *
 * It is the attempt that is open after the retry: the backend opens a new one
 * or returns the one that was still open.
 */
export interface OpenOrderPayment {
  /** Id of the open attempt of payment. */
  paymentId: number;
  /** Url of the payment session; the app never opens it. */
  paymentUrl: string;
  /** Deadline of the payment window, as an ISO-8601 offset date time. */
  paymentExpiresAt: string | null;
}

/** Range accepted by `GET /api/orders/restaurants/{id}/sales-summary`. */
export const SALES_RANGES = ['day', 'week', 'month'] as const;
export type SalesRange = (typeof SALES_RANGES)[number];

/** `orders-service` — `SalesSummaryResponse.TopProductDto`. */
export interface TopProductSummary {
  productId: string;
  productName: string;
  quantitySold: number;
  revenue: number;
}

/** `orders-service` — `SalesSummaryResponse`. */
export interface SalesSummaryResponse {
  restaurantId: number;
  range: string;
  from: string;
  to: string;
  totalOrders: number;
  totalRevenue: number;
  averageTicket: number;
  topProducts: TopProductSummary[];
}

/** Value accepted in a query string. */
export type QueryValue = string | number | boolean | null | undefined;

/** Query string parameters of a request. */
export type QueryParams = Record<string, QueryValue>;
