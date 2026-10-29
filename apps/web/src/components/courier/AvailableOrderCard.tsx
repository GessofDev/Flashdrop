import type { AvailableDeliveryOrder } from '@/api/delivery';
import { formatClp, MISSING_VALUE } from '@/lib/format';

/** Distance in the Chilean format used by the interface. */
function formatDistance(distanceKm: number | null): string {
  if (distanceKm === null) {
    return MISSING_VALUE;
  }

  return `${new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 }).format(distanceKm)} km`;
}

/** Estimated duration in the Chilean format used by the interface. */
function formatMinutes(estimatedMinutes: number | null): string {
  if (estimatedMinutes === null) {
    return MISSING_VALUE;
  }

  return `${estimatedMinutes} min`;
}

export interface AvailableOrderCardProps {
  /** Order returned by the available-orders endpoint. */
  order: AvailableDeliveryOrder;
  /** Whether the order is selected for the claim. */
  selected: boolean;
  /** Whether the selection control is disabled. */
  selectionDisabled: boolean;
  /** Whether the current session may claim orders. */
  canSelect: boolean;
  /** Select or deselect the order. */
  onToggle: (orderId: number) => void;
}

/** One order of the FIFO queue. */
export function AvailableOrderCard({
  order,
  selected,
  selectionDisabled,
  canSelect,
  onToggle,
}: AvailableOrderCardProps) {
  return (
    <article
      aria-label={`Pedido ${order.code}`}
      className="flex flex-col gap-4 rounded-container border border-border border-l-4 border-l-primary bg-surface p-4 shadow-card"
    >
      <div className="flex flex-wrap items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <span className="nums-tabular label-code font-display text-lg font-bold text-foreground">
            {`#${order.code}`}
          </span>
          <span className="text-sm font-semibold text-foreground">{order.restaurantName}</span>
        </div>
        <span className="nums-tabular font-display text-lg font-bold text-primary">
          {formatClp(order.total)}
        </span>
      </div>

      <dl className="grid gap-3 text-sm sm:grid-cols-2">
        <div className="flex flex-col gap-1">
          <dt className="label-code text-xs text-foreground-muted">Retiro</dt>
          <dd className="text-foreground-secondary">
            {order.pickupAddress.trim().length > 0 ? order.pickupAddress : MISSING_VALUE}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="label-code text-xs text-foreground-muted">Entrega</dt>
          <dd className="text-foreground-secondary">
            {order.deliveryAddress.trim().length > 0 ? order.deliveryAddress : MISSING_VALUE}
          </dd>
        </div>
        <div className="flex flex-col gap-1">
          <dt className="label-code text-xs text-foreground-muted">Cliente</dt>
          <dd className="font-medium text-foreground">
            {order.clientName.trim().length > 0 ? order.clientName : MISSING_VALUE}
          </dd>
        </div>
        <div className="flex gap-4 text-foreground-secondary">
          <span className="nums-tabular">{formatDistance(order.distanceKm)}</span>
          <span className="nums-tabular">{formatMinutes(order.estimatedMinutes)}</span>
        </div>
      </dl>

      {canSelect ? (
        <label className="flex cursor-pointer items-center gap-3 border-t border-border pt-3 text-sm font-medium text-foreground">
          <input
            type="checkbox"
            className="h-4 w-4 accent-primary"
            checked={selected}
            disabled={selectionDisabled}
            onChange={() => {
              onToggle(order.orderId);
            }}
          />
          <span>{`Seleccionar pedido ${order.code}`}</span>
        </label>
      ) : null}
    </article>
  );
}
