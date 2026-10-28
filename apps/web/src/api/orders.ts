import { request } from './client';
import type { CreateOrderRequest, CreatedOrderResult, OrderDetail } from './types';

/**
 * Payment methods accepted by `orders-service`.
 *
 * They are the values of its `PaymentMethod` enum; the checkout only declares
 * one of them, because the app does not integrate a payment gateway.
 */
export const PAYMENT_METHODS = ['Efectivo', 'Tarjeta', 'Transferencia'] as const;

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
   */
  getOrder(orderId: string): Promise<OrderDetail> {
    return request<OrderDetail>({
      path: `/api/orders/${orderId}`,
      method: 'GET',
      responseShape: 'envelope',
    });
  },
};
