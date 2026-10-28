import { useState } from 'react';

import { ApiError, asApiError, EMPTY_ORDER_MESSAGE, ordersApi } from '@/api';
import type { CreatedOrderResult } from '@/api';
import { useCart } from '@/cart';
import { formatClp } from '@/lib/format';

import { CartItemRow } from './CartItemRow';
import { CheckoutForm } from './CheckoutForm';
import type { CheckoutValues } from './CheckoutForm';
import { OrderConfirmation } from './OrderConfirmation';

export interface CartPanelProps {
  /** Name of every restaurant of the catalog, to name the local of the cart. */
  restaurantNames: Map<number, string>;
}

/**
 * Cart of the customer and its declarative checkout (spec S9).
 *
 * The cart lives in the browser. The checkout sends the address, the declared
 * payment method and the items to `POST /api/orders`; the total the customer
 * sees after the order is the one the backend answered with.
 */
export function CartPanel({ restaurantNames }: CartPanelProps) {
  const { items, itemCount, subtotal, increaseItem, decreaseItem, removeItem, clear } = useCart();

  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdOrder, setCreatedOrder] = useState<CreatedOrderResult | null>(null);

  const localNames = [
    ...new Set(
      items.map((item) => restaurantNames.get(item.restaurantId) ?? `Local ${item.restaurantId}`),
    ),
  ];

  const merchantLabel =
    localNames.length === 1 ? (localNames[0] ?? '') : 'Varios locales';

  const handleSubmit = async (values: CheckoutValues): Promise<void> => {
    setError(null);

    if (items.length === 0) {
      setError(
        new ApiError(EMPTY_ORDER_MESSAGE, { status: 400, kind: 'badRequest' }),
      );
      return;
    }

    setSubmitting(true);

    try {
      const order = await ordersApi.createOrder({
        address: values.address.trim(),
        payment_method: values.paymentMethod,
        distance_km: values.distanceKm.trim().length > 0 ? Number(values.distanceKm) : undefined,
        estimated_minutes:
          values.estimatedMinutes.trim().length > 0 ? Number(values.estimatedMinutes) : undefined,
        items: items.map((item) => ({
          product_id: item.productId,
          quantity: item.quantity,
        })),
      });

      setCreatedOrder(order);
      clear();
    } catch (cause) {
      setError(asApiError(cause));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <aside
      aria-labelledby="cart-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="cart-title" className="font-display text-lg font-bold text-foreground">
          Mi Carrito
        </h2>
        <span className="rounded-full bg-surface-hover px-2.5 py-0.5 text-xs font-semibold text-foreground-secondary">
          {itemCount} {itemCount === 1 ? 'ítem' : 'ítems'}
        </span>
      </div>

      {createdOrder !== null ? (
        <OrderConfirmation order={createdOrder} />
      ) : (
        <>
          {items.length === 0 ? (
            <div className="flex flex-col gap-1">
              <p className="text-sm font-semibold text-foreground">Tu carrito está vacío</p>
              <p className="text-sm text-foreground-secondary">
                Agrega platos del catálogo para continuar.
              </p>
            </div>
          ) : (
            <>
              <p className="rounded-control bg-surface-hover px-3 py-2 text-sm font-semibold text-foreground-secondary">
                {merchantLabel}
              </p>

              <ul className="flex flex-col gap-2">
                {items.map((item) => (
                  <CartItemRow
                    key={item.productId}
                    item={item}
                    onIncrease={() => increaseItem(item.productId)}
                    onDecrease={() => decreaseItem(item.productId)}
                    onRemove={() => removeItem(item.productId)}
                  />
                ))}
              </ul>

              <div className="flex items-baseline justify-between gap-3 border-t border-border pt-3">
                <span className="text-sm text-foreground-secondary">Subtotal</span>
                <span className="nums-tabular font-display text-lg font-bold text-foreground">
                  {formatClp(subtotal)}
                </span>
              </div>
            </>
          )}

          <CheckoutForm onSubmit={handleSubmit} submitting={submitting} error={error} />
        </>
      )}
    </aside>
  );
}
