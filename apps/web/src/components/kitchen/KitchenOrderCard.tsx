import type { ApiError, OrderDetail, OrderListItem } from '@/api';
import { Button } from '@/components/ui';
import { formatClp, formatOrderTime } from '@/lib/format';
import { nextKitchenTransition } from '@/lib/kitchenStatus';
import type { Role } from '@/session';

import { ElapsedTimer } from './ElapsedTimer';

export interface KitchenOrderCardProps {
  /** Order of the listing. */
  order: OrderListItem;
  /** Detail of the order, when it could be read. */
  detail: OrderDetail | null;
  /** Failure of the detail call, with the message of the backend. */
  detailError: ApiError | null;
  /** Whether the detail is still being read. */
  detailLoading: boolean;
  /** Current instant, in epoch milliseconds. */
  now: number;
  /** Role of the user working on the board. */
  role: Role;
  /** Whether the card is the one the keyboard shortcut acts on. */
  selected: boolean;
  /** Whether a change of status is in flight for this order. */
  pending: boolean;
  /** Select the card. */
  onSelect: (orderId: string) => void;
  /** Open the drawer of the detail. */
  onOpenDetail: (orderId: string) => void;
  /** Move the order to the next status of the kitchen. */
  onAdvance: (order: OrderListItem, target: string) => void;
}

/**
 * One card of the board.
 *
 * It shows what the kitchen needs to work: the code of the order, who asked
 * for it, how long it has been waiting and the dishes it carries. The dishes
 * are not part of the listing of the backend, so the card shows them once the
 * detail of the order could be read.
 */
export function KitchenOrderCard({
  order,
  detail,
  detailError,
  detailLoading,
  now,
  role,
  selected,
  pending,
  onSelect,
  onOpenDetail,
  onAdvance,
}: KitchenOrderCardProps) {
  const target = nextKitchenTransition(order.status, role);

  const classes = [
    'flex flex-col gap-3 rounded-container border bg-surface p-4 shadow-card',
    selected ? 'border-primary ring-2 ring-primary/20' : 'border-border',
  ].join(' ');

  return (
    <article
      aria-label={`Pedido ${order.code}`}
      tabIndex={0}
      onClick={() => onSelect(order.id)}
      onFocus={() => onSelect(order.id)}
      className={classes}
    >
      <div className="flex items-start justify-between gap-3">
        <span className="nums-tabular label-code font-display text-lg font-bold text-foreground">
          {`#${order.code}`}
        </span>
        <ElapsedTimer createdAt={order.createdAt} now={now} />
      </div>

      <div className="flex flex-col gap-0.5">
        <p className="font-display text-sm font-bold text-foreground">{order.clientName}</p>
        <p className="text-sm text-foreground-secondary">{order.address}</p>
        <p className="nums-tabular text-xs text-foreground-muted">
          {`Recibido ${formatOrderTime(order.createdAt)}`}
        </p>
      </div>

      {detail !== null ? (
        <ul className="flex flex-col gap-1 border-t border-border pt-2">
          {detail.items.map((item) => (
            <li key={item.id} className="flex items-start justify-between gap-2 text-sm">
              <span className="flex items-baseline gap-1 text-foreground">
                <span className="nums-tabular font-bold">{`${item.quantity}x`}</span>
                <span>{item.name}</span>
              </span>
              <span className="nums-tabular text-foreground-secondary">{formatClp(item.total)}</span>
            </li>
          ))}
        </ul>
      ) : detailError !== null ? (
        <p className="border-t border-border pt-2 text-sm text-destructive">
          {detailError.message}
        </p>
      ) : detailLoading ? (
        <p className="border-t border-border pt-2 text-sm text-foreground-muted">
          Cargando los platos del pedido
        </p>
      ) : null}

      <div className="flex items-center justify-between gap-2 border-t border-border pt-3">
        <div className="flex flex-col gap-0.5">
          <span className="label-code text-xs text-foreground-muted">Total</span>
          <span className="nums-tabular font-display text-base font-bold text-foreground">
            {formatClp(order.total)}
          </span>
        </div>

        <div className="flex items-center gap-2">
          <Button
            variant="secondary"
            size="sm"
            aria-label={`Ver detalle del pedido ${order.code}`}
            onClick={(event) => {
              event.stopPropagation();
              onOpenDetail(order.id);
            }}
          >
            Ver detalle
          </Button>

          {target !== null ? (
            <Button
              size="sm"
              disabled={pending}
              onClick={(event) => {
                event.stopPropagation();
                onAdvance(order, target);
              }}
            >
              {`Avanzar a ${target}`}
            </Button>
          ) : null}
        </div>
      </div>
    </article>
  );
}
