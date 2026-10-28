import { asList } from './asList';
import { request } from './client';
import type {
  CreateOrderRequest,
  CreatedOrderResult,
  OrderDetail,
  OrderListItem,
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
};
