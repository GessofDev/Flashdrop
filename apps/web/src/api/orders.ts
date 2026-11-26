import { asList } from './asList';
import { request } from './client';
import type {
  CreateOrderRequest,
  CreatedOrderResult,
  DeliveryQuote,
  DeliveryQuoteRequest,
  OpenOrderPayment,
  OrderDetail,
  OrderListItem,
  OrderPayment,
  SalesRange,
  SalesSummaryResponse,
} from './types';

/**
 * Payment methods accepted by `orders-service`.
 *
 * They are the values of its `PaymentMethod` enum; the checkout only declares
 * one of them, because the app does not integrate a payment gateway.
 */
export const PAYMENT_METHODS = ['Efectivo', 'Tarjeta', 'Transferencia'] as const;

/**
 * View the backend filters the listing by.
 *
 * The parameter is optional in the endpoint: without it the backend uses
 * every role of the token. The screens of the app always state the view they
 * mean, so the listing never mixes the orders of two roles.
 */
export type OrderListView = 'client' | 'restaurant';

/**
 * Literal message of `orders-service` for an order without products.
 *
 * It is the message of the rule `Order.validateSingleRestaurant` of the
 * backend. The client shows it when the cart is empty: the endpoint would
 * answer with the message of the missing product instead, because it falls
 * back to the legacy shape of a single product.
 */
export const EMPTY_ORDER_MESSAGE = 'El pedido debe contener al menos un producto';

/**
 * Calls of `orders-service`.
 *
 * The service answers with the `{ success, message, data }` envelope, so the
 * call declares `responseShape: 'envelope'` and the client unwraps the data
 * of the answer.
 */
export const ordersApi = {
  /**
   * `GET /api/orders` — the orders of the signed in user.
   *
   * The identity always comes from the token, so the call never sends the
   * user id: the backend rejects the request with a 403 when the id of the
   * query does not match the one of the token.
   */
  listOrders(view: OrderListView = 'client'): Promise<OrderListItem[]> {
    return request<unknown>({
      path: '/api/orders',
      method: 'GET',
      responseShape: 'envelope',
      query: { view },
    }).then((answer) => asList<OrderListItem>(answer));
  },

  /**
   * `POST /api/orders` — creates the order of the customer.
   *
   * The order carries the address, the declared payment method and the items
   * of the cart; the backend answers with the id and the total it computed.
   *
   * The coordinates of the destination travel only when the customer resolved
   * them: with them the backend calculates the distance itself, and without
   * them it rejects the order asking for an address with location.
   */
  createOrder(order: CreateOrderRequest): Promise<CreatedOrderResult> {
    return request<CreatedOrderResult>({
      path: '/api/orders',
      method: 'POST',
      body: order,
      responseShape: 'envelope',
    });
  },

  /**
   * `POST /api/orders/delivery-quote` — quotes the dispatch of the order.
   *
   * The quote needs the coordinates of the destination: without them the
   * backend cannot validate the coverage of the local and answers with a
   * 400. The answer carries the amounts the backend computed, so the client
   * never calculates a price of its own.
   */
  quoteDelivery(quote: DeliveryQuoteRequest): Promise<DeliveryQuote | null> {
    return request<DeliveryQuote | null>({
      path: '/api/orders/delivery-quote',
      method: 'POST',
      body: quote,
      responseShape: 'envelope',
    });
  },

  /**
   * `GET /api/orders/{id}` — detail of one order.
   *
   * The create endpoint answers with the id and the total only, so the code
   * the customer reads comes from here: the format of the code belongs to the
   * backend and the client must not reimplement it.
   *
   * The detail also carries the client, the restaurant, the courier, the
   * route and the dishes of the order — the blocks the backend can resolve.
   */
  getOrder(orderId: string): Promise<OrderDetail> {
    return request<OrderDetail>({
      path: `/api/orders/${orderId}`,
      method: 'GET',
      responseShape: 'envelope',
    });
  },

  /**
   * `PUT /api/orders/{id}/status` — moves the order to another status.
   *
   * The value of the body is the **label** of the status of the backend
   * (`Preparando`, `Listo para retiro`), not the name of the enum: that is
   * what `OrderStatus.fromValue` reads. The endpoint answers without data, so
   * the caller reads the new state from the listing.
   *
   * The backend owns the rules of the change: the role that may set the
   * status, the ownership of the order and the transition from the current
   * status. The client only sends the status it means.
   */
  updateOrderStatus(orderId: string, status: string): Promise<void> {
    return request<null>({
      path: `/api/orders/${orderId}/status`,
      method: 'PUT',
      body: { status },
      responseShape: 'envelope',
    }).then(() => undefined);
  },

  /**
   * `POST /api/orders/{id}/deliver` — confirms the delivery of the order.
   *
   * It is the only way to reach the delivered status: `PUT /status` rejects
   * it in the secure mode. The body carries the six digit code the customer
   * dictates; in the normal mode the code is the public `000000`.
   *
   * A wrong code answers with a 422 and the message of the backend, and an
   * exhausted attempt answers with a 423, so the caller shows the message
   * of the backend as it is.
   */
  deliverOrder(orderId: string, code: string): Promise<void> {
    return request<null>({
      path: `/api/orders/${orderId}/deliver`,
      method: 'POST',
      body: { code },
      responseShape: 'envelope',
    }).then(() => undefined);
  },

  /**
   * `POST /api/orders/{id}/release` — releases an assigned order before pickup.
   */
  releaseOrder(orderId: string, reason: string): Promise<void> {
    return request<null>({
      path: `/api/orders/${orderId}/release`,
      method: 'POST',
      body: { reason },
      responseShape: 'envelope',
    }).then(() => undefined);
  },

  /**
   * `POST /api/orders/{id}/cancel-after-pickup` — cancels or rejects an order
   * after pickup (`Cancelado` or `Rechazado`).
   */
  cancelAfterPickup(
    orderId: string,
    status: 'Cancelado' | 'Rechazado',
    reason: string,
  ): Promise<void> {
    return request<null>({
      path: `/api/orders/${orderId}/cancel-after-pickup`,
      method: 'POST',
      body: { status, reason },
      responseShape: 'envelope',
    }).then(() => undefined);
  },

  /**
   * `GET /api/orders/{id}/payments` — how the payment of the order goes.
   *
   * The answer carries the status of the order and the state of its last
   * attempt of payment: `paymentStatus` is the **name** of the
   * `PaymentStatus` of the backend, not its label.
   *
   * The customer always comes from the token, so the call never sends a user
   * id: the backend answers with a 403 when the caller does not own the
   * order.
   */
  getOrderPayment(orderId: string): Promise<OrderPayment> {
    return request<OrderPayment>({
      path: `/api/orders/${orderId}/payments`,
      method: 'GET',
      responseShape: 'envelope',
    });
  },

  /**
   * `POST /api/orders/{id}/payments` — opens a new attempt of payment.
   *
   * The backend opens an attempt for the same order or answers with the one
   * that is still open, and it owns the rules: an order that is not waiting
   * for its payment answers with a 400 (`El pedido no esta pendiente de
   * pago`) and an expired payment window answers with a 400 (`El plazo para
   * pagar este pedido ya vencio`).
   *
   * The body carries nothing: the customer is always resolved from the token.
   */
  openOrderPayment(orderId: string): Promise<OpenOrderPayment> {
    return request<OpenOrderPayment>({
      path: `/api/orders/${orderId}/payments`,
      method: 'POST',
      responseShape: 'envelope',
    });
  },

  /**
   * `GET /api/orders/restaurants/{id}/sales-summary?range=day|week|month`
   *
   * Sales summary of the store (spec S14). The backend restricts this to the
   * restaurant owner (403 if role is not RESTAURANTE or if the caller does not
   * own the restaurant).
   */
  getSalesSummary(
    restaurantId: number,
    range: SalesRange | string = 'week',
  ): Promise<SalesSummaryResponse> {
    return request<SalesSummaryResponse>({
      path: `/api/orders/restaurants/${restaurantId}/sales-summary`,
      method: 'GET',
      responseShape: 'envelope',
      query: { range },
    });
  },

  /**
   * `GET /api/orders/store/summary?restaurantId=&range=day|week|month`
   *
   * Sales summary for a specific store or aggregated across all stores of the
   * merchant when `restaurantId` is omitted.
   */
  getStoreSummary(
    restaurantId?: number,
    range: SalesRange | string = 'week',
  ): Promise<SalesSummaryResponse> {
    return request<SalesSummaryResponse>({
      path: '/api/orders/store/summary',
      method: 'GET',
      responseShape: 'envelope',
      query: restaurantId !== undefined ? { restaurantId, range } : { range },
    });
  },
};
