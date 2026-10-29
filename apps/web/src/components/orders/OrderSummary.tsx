import type { OrderDetail } from '@/api';
import { formatClp, MISSING_VALUE } from '@/lib/format';

export interface OrderSummaryProps {
  /** Order the backend answered with. */
  order: OrderDetail;
}

/**
 * Summary of the order (spec S10).
 *
 * The money shown is the money of the backend: the subtotal, the price of the
 * dispatch and the total come from the detail of the order, never from a
 * calculation of the client.
 */
export function OrderSummary({ order }: OrderSummaryProps) {
  const restaurantAddress = order.restaurant?.address ?? null;

  return (
    <section
      aria-labelledby="order-summary-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <h2 id="order-summary-title" className="font-display text-lg font-bold text-foreground">
        Resumen del pedido
      </h2>

      <div className="flex flex-col gap-0.5 border-b border-border pb-3">
        <span className="font-display text-base font-bold text-foreground">
          {order.restaurant?.name ?? MISSING_VALUE}
        </span>
        {restaurantAddress !== null ? (
          <span className="text-sm text-foreground-secondary">{restaurantAddress}</span>
        ) : null}
      </div>

      <div className="flex flex-col gap-3">
        <span className="label-code text-xs text-foreground-muted">
          Platos del pedido ({order.items.length})
        </span>

        {order.items.length === 0 ? (
          <p className="text-sm text-foreground-secondary">
            El pedido no tiene platos en su detalle.
          </p>
        ) : (
          <ul className="flex flex-col gap-3">
            {order.items.map((item) => {
              const description = item.description ?? null;

              return (
                <li key={item.id} className="flex items-start justify-between gap-3 text-sm">
                  <span className="flex flex-col gap-0.5">
                    <span className="font-semibold text-foreground">
                      <span className="nums-tabular text-primary">{item.quantity}x</span> {item.name}
                    </span>
                    {description !== null ? (
                      <span className="text-xs text-foreground-secondary">{description}</span>
                    ) : null}
                  </span>
                  <span className="nums-tabular shrink-0 font-bold text-foreground">
                    {formatClp(item.total)}
                  </span>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      <dl className="flex flex-col gap-2 rounded-control bg-canvas p-3 text-sm">
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Subtotal productos</dt>
          <dd className="nums-tabular font-semibold text-foreground">
            {formatClp(order.subtotal)}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Despacho</dt>
          <dd className="nums-tabular font-semibold text-foreground">
            {formatClp(order.deliveryFee)}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-3 border-t border-border pt-2">
          <dt className="font-display font-bold text-foreground">Total</dt>
          <dd className="nums-tabular font-display text-lg font-bold text-primary">
            {formatClp(order.total)}
          </dd>
        </div>
      </dl>

      <dl className="flex flex-col gap-2 text-sm">
        <div className="flex items-start justify-between gap-3">
          <dt className="shrink-0 text-foreground-secondary">Dirección de entrega</dt>
          <dd className="text-right font-medium text-foreground">
            {order.address !== '' ? order.address : MISSING_VALUE}
          </dd>
        </div>
        <div className="flex items-center justify-between gap-3">
          <dt className="text-foreground-secondary">Método de pago</dt>
          <dd className="font-medium text-foreground">
            {order.paymentMethod !== '' ? order.paymentMethod : MISSING_VALUE}
          </dd>
        </div>
      </dl>
    </section>
  );
}
