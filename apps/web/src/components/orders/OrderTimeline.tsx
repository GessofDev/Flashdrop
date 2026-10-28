import { formatOrderDate } from '@/lib/format';
import { completedStepsForStatus } from '@/lib/orderStatus';
import { isRouteOnTheWay } from '@/lib/routeStatus';

export interface OrderTimelineProps {
  /** Status of the order, as the backend labels it. */
  status: string;
  /** Status of the route, when the order has one. */
  routeStatus: string | null;
  /** Instant the order was created, as an ISO-8601 offset date time. */
  createdAt: string;
}

/** One milestone of the trace of the order. */
interface TimelineEntry {
  key: string;
  /** Milestone, in Chilean Spanish. */
  label: string;
  /** What the milestone means. */
  detail: string;
  /** Instant of the milestone, when the backend sent one. */
  timestamp: string | null;
  /** Whether the milestone is the one happening right now. */
  current: boolean;
}

/**
 * Trace of the order (spec S10).
 *
 * The milestones are derived from the data the backend sends: the instant of
 * the reception, the status of the order and the status of the route. A
 * milestone whose data is missing is **not** drawn, and the only instant the
 * backend sends is the one of the creation — the app never invents the hour
 * of a milestone it cannot read.
 */
export function OrderTimeline({ status, routeStatus, createdAt }: OrderTimelineProps) {
  const completed = completedStepsForStatus(status);
  const entries: TimelineEntry[] = [
    {
      key: 'received',
      label: 'Pedido recibido',
      detail: 'El pedido entró al sistema con el pago declarado.',
      timestamp: formatOrderDate(createdAt),
      current: completed === 0,
    },
  ];

  if (completed >= 2) {
    entries.push({
      key: 'kitchen',
      label: 'En cocina',
      detail: 'El restaurante comenzó a preparar el pedido.',
      timestamp: null,
      current: completed === 2,
    });
  }

  if (completed >= 3) {
    entries.push({
      key: 'ready',
      label: 'Listo para retiro',
      detail: 'El pedido quedó listo para que el repartidor lo retire.',
      timestamp: null,
      current: completed === 3,
    });
  }

  if (completed >= 4) {
    entries.push({
      key: 'picked-up',
      label: 'Retirado',
      detail: 'El repartidor retiró el pedido del restaurante.',
      timestamp: null,
      current: completed === 4,
    });
  }

  if (routeStatus !== null && isRouteOnTheWay(routeStatus)) {
    entries.push({
      key: 'on-the-way',
      label: 'En ruta',
      detail: 'El reparto salió hacia la dirección de entrega.',
      timestamp: null,
      current: false,
    });
  }

  return (
    <section
      aria-labelledby="order-timeline-title"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex items-center justify-between gap-3">
        <h2 id="order-timeline-title" className="font-display text-lg font-bold text-foreground">
          Bitácora del pedido
        </h2>
        <span className="text-xs text-foreground-muted">
          {entries.length} {entries.length === 1 ? 'hito' : 'hitos'} registrados
        </span>
      </div>

      <ol aria-label="Bitácora del pedido" className="flex flex-col gap-4 border-l border-border pl-4">
        {entries.map((entry) => (
          <li key={entry.key} className="flex flex-col gap-1">
            <div className="flex flex-wrap items-baseline justify-between gap-2">
              <span className="text-sm font-bold text-foreground">{entry.label}</span>
              {entry.current ? (
                <span className="text-xs font-bold text-primary">En curso</span>
              ) : entry.timestamp !== null ? (
                <span className="nums-tabular text-xs text-foreground-muted">
                  {entry.timestamp}
                </span>
              ) : null}
            </div>
            <p className="text-sm text-foreground-secondary">{entry.detail}</p>
          </li>
        ))}
      </ol>
    </section>
  );
}
