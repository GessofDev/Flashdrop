import { Link } from 'react-router-dom';

import type { OrderListItem } from '@/api';
import { orderDetailPath } from '@/app/routePaths';
import { formatClp, formatOrderDate } from '@/lib/format';

import { OrderStatusBadge } from './OrderStatusBadge';

export interface OrderListCardProps {
  /** Order the backend answered with. */
  order: OrderListItem;
}

/**
 * Card of one order of the list.
 *
 * It shows what the list endpoint sends: the code, the restaurant, the status,
 * the total and the instant of the order. The address is there because the
 * customer has to know where the order goes.
 */
export function OrderListCard({ order }: OrderListCardProps) {
  return (
    <article className="flex flex-col gap-3 rounded-container border border-border bg-surface p-5 shadow-card">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="nums-tabular label-code font-bold text-foreground">#{order.code}</span>
        <OrderStatusBadge status={order.status} />
      </div>

      <div className="flex flex-col gap-1">
        <h2 className="font-display text-base font-bold text-foreground">{order.restaurantName}</h2>
        <p className="text-sm text-foreground-secondary">{order.address}</p>
      </div>

      <dl className="flex flex-wrap items-center justify-between gap-4 border-t border-border pt-3 text-sm">
        <div className="flex flex-col gap-0.5">
          <dt className="label-code text-xs text-foreground-muted">Total</dt>
          <dd className="nums-tabular font-display text-lg font-bold text-foreground">
            {formatClp(order.total)}
          </dd>
        </div>
        <div className="flex flex-col gap-0.5">
          <dt className="label-code text-xs text-foreground-muted">Fecha</dt>
          <dd className="nums-tabular text-foreground-secondary">
            {formatOrderDate(order.createdAt)}
          </dd>
        </div>
      </dl>

      <Link
        to={orderDetailPath(order.id)}
        className="self-start text-sm font-semibold text-primary transition-colors hover:text-primary-hover"
      >
        Ver seguimiento
      </Link>
    </article>
  );
}
