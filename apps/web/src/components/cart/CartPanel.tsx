import { useCallback, useEffect, useState } from 'react';

import { ApiError, asApiError, EMPTY_ORDER_MESSAGE, ordersApi } from '@/api';
import type { CreateOrderRequest, CreatedOrderResult, DeliveryQuote } from '@/api';
import { useCart } from '@/cart';
import type { CartItem } from '@/cart';
import type { Coordinates } from '@/lib/geolocation';
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
 * Message shown when the backend answers the quote without data.
 *
 * The quote of the backend is the only source of the price of the dispatch,
 * so the app never calculates one of its own.
 */
const NO_QUOTE_MESSAGE = 'El backend no devolvió la cotización del envío.';

/** State of the quote of the dispatch. */
interface DeliveryQuoteState {
  /** Quote of the backend, or null when there is none. */
  quote: DeliveryQuote | null;
  /** Literal message of the backend when the quote fails. */
  quoteError: string | null;
}

/**
 * Ask the backend for the price of the dispatch.
 *
 * The quote needs the coordinates of the destination and the items of the
 * cart, so it is asked when both are there and forgotten when one of them
 * is gone.
 */
function useDeliveryQuote(
  items: readonly CartItem[],
  coordinates: Coordinates | null,
): DeliveryQuoteState {
  const [quote, setQuote] = useState<DeliveryQuote | null>(null);
  const [quoteError, setQuoteError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    if (coordinates !== null && items.length > 0) {
      void ordersApi
        .quoteDelivery({
          items: items.map((item) => ({
            product_id: item.productId,
            quantity: item.quantity,
          })),
          latitude: coordinates.latitude,
          longitude: coordinates.longitude,
        })
        .then((answer) => {
          if (cancelled) {
            return;
          }

          setQuote(answer);
          setQuoteError(answer === null ? NO_QUOTE_MESSAGE : null);
        })
        .catch((cause) => {
          if (cancelled) {
            return;
          }

          setQuote(null);
          setQuoteError(asApiError(cause).message);
        });
    } else {
      setQuote(null);
      setQuoteError(null);
    }

    return () => {
      cancelled = true;
    };
  }, [coordinates, items]);

  return { quote, quoteError };
}

/**
 * Cart of the customer and its declarative checkout (spec S9).
 *
 * The cart lives in the browser. The checkout sends the address, the declared
 * payment method and the items to `POST /api/orders`; the total the customer
 * sees after the order is the one the backend answered with.
 *
 * The panel owns the items, so it is the one that asks for the quote of the
 * dispatch (`POST /api/orders/delivery-quote`) once the checkout resolves the
 * coordinates of the destination.
 */
export function CartPanel({ restaurantNames }: CartPanelProps) {
  const { items, itemCount, subtotal, increaseItem, decreaseItem, removeItem, clear } = useCart();

  const [error, setError] = useState<ApiError | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [createdOrder, setCreatedOrder] = useState<CreatedOrderResult | null>(null);
  const [coordinates, setCoordinates] = useState<Coordinates | null>(null);
  const { quote, quoteError } = useDeliveryQuote(items, coordinates);

  const localNames = [
    ...new Set(
      items.map((item) => restaurantNames.get(item.restaurantId) ?? `Local ${item.restaurantId}`),
    ),
  ];

  const merchantLabel =
    localNames.length === 1 ? (localNames[0] ?? '') : 'Varios locales';

  /**
   * A pedido belongs to a single local: the backend rejects the one that
   * mixes two of them. The cart says it before the customer confirms, but
   * the answer of the backend is the one that ends up on the screen.
   */
  const mixedLocals = localNames.length > 1;

  /**
   * Keep the coordinates the checkout resolved.
   *
   * The same place is kept as it is, so the quote is not asked again for the
   * same destination.
   */
  const handleCoordinatesChange = useCallback((next: Coordinates | null): void => {
    setCoordinates((current) => {
      if (current === null || next === null) {
        return next;
      }

      return current.latitude === next.latitude && current.longitude === next.longitude
        ? current
        : next;
    });
  }, []);

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
      const body: CreateOrderRequest = {
        address: values.address.trim(),
        payment_method: values.paymentMethod,
        items: items.map((item) => ({
          product_id: item.productId,
          quantity: item.quantity,
        })),
      };

      if (values.coordinates !== null) {
        // With the coordinates the backend calculates the distance itself.
        body.latitude = values.coordinates.latitude;
        body.longitude = values.coordinates.longitude;
      } else if (values.distanceKm.trim().length > 0) {
        body.distance_km = Number(values.distanceKm);
      }

      if (values.estimatedMinutes.trim().length > 0) {
        body.estimated_minutes = Number(values.estimatedMinutes);
      }

      const order = await ordersApi.createOrder(body);

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

              {mixedLocals ? (
                <p
                  role="status"
                  className="rounded-control bg-warning-surface px-3 py-2 text-sm font-semibold text-warning"
                >
                  Un pedido es de un solo local: quita los platos del otro local.
                </p>
              ) : null}

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

          <CheckoutForm
            onSubmit={handleSubmit}
            submitting={submitting}
            error={error}
            onCoordinatesChange={handleCoordinatesChange}
            quote={quote}
            quoteError={quoteError}
          />
        </>
      )}
    </aside>
  );
}
