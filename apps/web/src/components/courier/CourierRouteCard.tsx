import type { DeliveryRoute } from '@/api/delivery';
import { Badge } from '@/components/ui';
import type { BadgeVariant } from '@/components/ui';
import { MISSING_VALUE } from '@/lib/format';
import { routeStatusLabel } from '@/lib/routeStatus';

function routeBadgeVariant(status: string): BadgeVariant {
  return status.trim().toUpperCase() === 'ENTREGADO' ? 'success' : 'info';
}

function formatDistance(distanceKm: number | null): string {
  if (distanceKm === null) {
    return MISSING_VALUE;
  }

  return `${new Intl.NumberFormat('es-CL', { maximumFractionDigits: 1 }).format(distanceKm)} km`;
}

function formatMinutes(estimatedMinutes: number | null): string {
  if (estimatedMinutes === null) {
    return MISSING_VALUE;
  }

  return `${estimatedMinutes} min`;
}

export interface CourierRouteCardProps {
  /** Route returned by the delivery service. */
  route: DeliveryRoute;
  /** Accessible name of the article: an active route or a completed delivery. */
  articleLabel: string;
  /** Label of the action allowed by the current status, when there is one. */
  actionLabel: string | null;
  /** Whether the action is in flight. */
  pending: boolean;
  /** Run the action of the route. */
  onAdvance: (route: DeliveryRoute, actionLabel: string) => void;
}

/** One route of the courier, active or completed. */
export function CourierRouteCard({
  route,
  articleLabel,
  actionLabel,
  pending,
  onAdvance,
}: CourierRouteCardProps) {
  const identifier = route.code ?? String(route.orderId);

  return (
    <article
      aria-label={`${articleLabel} ${identifier}`}
      className="overflow-hidden rounded-container border border-border bg-surface shadow-card"
    >
      <div className="flex flex-wrap items-center justify-between gap-3 bg-foreground px-5 py-4 text-white">
        <div className="flex flex-col gap-1">
          <span className="label-code text-xs text-white/70">Pedido de reparto</span>
          <span className="nums-tabular font-display text-xl font-bold">
            {route.code === null ? `Pedido ${route.orderId}` : `#${route.code}`}
          </span>
        </div>
        <Badge variant={routeBadgeVariant(route.status)}>{routeStatusLabel(route.status)}</Badge>
      </div>

      <div className="grid gap-4 p-5 sm:grid-cols-2">
        <div className="flex flex-col gap-1 rounded-control bg-canvas p-3">
          <span className="label-code text-xs text-foreground-muted">Origen</span>
          <span className="text-sm font-semibold text-foreground">
            {route.pickupAddress.trim().length > 0 ? route.pickupAddress : MISSING_VALUE}
          </span>
        </div>
        <div className="flex flex-col gap-1 rounded-control bg-primary-surface p-3">
          <span className="label-code text-xs text-primary">Destino</span>
          <span className="text-sm font-semibold text-foreground">
            {route.deliveryAddress.trim().length > 0 ? route.deliveryAddress : MISSING_VALUE}
          </span>
        </div>
      </div>

      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-border px-5 py-4">
        <div className="flex gap-5 text-sm text-foreground-secondary">
          <span className="nums-tabular">{formatDistance(route.distanceKm)}</span>
          <span className="nums-tabular">{formatMinutes(route.estimatedMinutes)}</span>
        </div>

        {actionLabel !== null ? (
          <button
            type="button"
            disabled={pending}
            className="inline-flex h-10 items-center justify-center rounded-control bg-primary px-4 text-sm font-semibold text-white transition-colors hover:bg-primary-hover disabled:pointer-events-none disabled:cursor-not-allowed disabled:opacity-45"
            onClick={() => {
              onAdvance(route, actionLabel);
            }}
          >
            {actionLabel}
          </button>
        ) : null}
      </div>
    </article>
  );
}
