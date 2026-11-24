import { Link } from 'react-router-dom';

import type { OrderListItem } from '@/api';
import { orderDetailPath } from '@/app/routePaths';
import { Badge } from '@/components/ui';
import { formatClp, formatOrderDate } from '@/lib/format';

import { orderReception } from './OrderReceptionPanel';
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
 *
 * The reception mode and the reason of an automatic cancellation are there
 * too, so the customer reads why an order was cancelled without opening it.
 */
export function OrderListCard({ order }: OrderListCardProps) {
  const reception = orderReception(order);

  const cancelled =
    reception.cancelReason !== null ||
    reception.cancelReasonMessage !== null ||
    reception.cancelledAt !== null;

  return (
    <article className="flex flex-col gap-3 rounded-container border border-border bg-surface p-5 shadow-card">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <span className="nums-tabular label-code font-bold text-foreground">#{order.code}</span>
        <div className="flex flex-wrap items-center gap-2">
          {reception.modeLabel !== null ? (
            <Badge variant={reception.modeTone}>{reception.modeLabel}</Badge>
          ) : null}
          <OrderStatusBadge status={order.status} />
        </div>
      </div>

      <div className="flex flex-col gap-1">
        <h2 className="font-display text-base font-bold text-foreground">{order.restaurantName}</h2>
        <p className="text-sm text-foreground-secondary">{order.address}</p>
      </div>

      {cancelled ? (
        <div className="flex flex-col gap-1 rounded-control bg-destructive-surface p-3">
          <span className="label-code text-xs text-destructive">Motivo de la cancelación</span>
          {reception.cancelReasonMessage !== null ? (
            <p className="text-sm font-medium text-destructive">{reception.cancelReasonMessage}</p>
          ) : null}
          {reception.cancelReason !== null ? (
            <p className="text-xs text-destructive">
              Código del motivo: {reception.cancelReason}
            </p>
          ) : null}
          {reception.cancelledAt !== null ? (
            <p className="nums-tabular text-xs text-destructive">
              Cancelado el {formatOrderDate(reception.cancelledAt)}
            </p>
          ) : null}
        </div>
      ) : null}

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
