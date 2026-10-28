import type { ApiError, OrderDetail, OrderListItem } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button, Skeleton } from '@/components/ui';
import { formatClp, formatOrderDate } from '@/lib/format';
import { completedKitchenMilestones, KITCHEN_MILESTONES, nextKitchenTransition } from '@/lib/kitchenStatus';
import type { Role } from '@/session';

export interface OrderDetailDrawerProps {
  /** Order the drawer shows; `null` when it is closed. */
  order: OrderListItem | null;
  /** Detail of the order, when it could be read. */
  detail: OrderDetail | null;
  /** Failure of the detail call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the detail is still being read. */
  loading: boolean;
  /** Role of the user working on the board. */
  role: Role;
  /** Whether a change of status is in flight for this order. */
  pending: boolean;
  /** Close the drawer. */
  onClose: () => void;
  /** Move the order to the next status of the kitchen. */
  onAdvance: (order: OrderListItem, target: string) => void;
}

/**
 * Drawer with the detail of one order.
 *
 * It carries the dishes with their quantity, the address, the totals the
 * backend computed and the milestones the order reached inside the kitchen.
 * When the detail cannot be read, the drawer shows the message of the backend
 * as it comes.
 */
export function OrderDetailDrawer({
  order,
  detail,
  error,
  loading,
  role,
  pending,
  onClose,
  onAdvance,
}: OrderDetailDrawerProps) {
  if (order === null) {
    return null;
  }

  const target = nextKitchenTransition(order.status, role);
  const completed = completedKitchenMilestones(order.status);
  const totals = detail ?? order;

  return (
    <aside
      role="dialog"
      aria-modal="true"
      aria-label={`Detalle del pedido ${order.code}`}
      className="fixed inset-y-0 right-0 z-50 flex w-full max-w-md flex-col gap-4 overflow-y-auto border-l border-border bg-surface p-6 shadow-card"
    >
      <header className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="nums-tabular label-code font-display text-xl font-bold text-foreground">
            {`#${order.code}`}
          </h2>
          <span className="text-sm text-foreground-secondary">{order.status}</span>
        </div>
        <Button variant="secondary" size="sm" onClick={onClose}>
          Cerrar
        </Button>
      </header>

      {error !== null ? (
        <ApiErrorNotice error={error} />
      ) : loading || detail === null ? (
        <Skeleton label="Cargando el detalle del pedido" blocks={2} height="h-24" />
      ) : (
        <>
          <section className="flex flex-col gap-2">
            <h3 className="label-code text-xs font-bold text-foreground-muted">
              Composición del pedido
            </h3>
            <ul className="flex flex-col gap-3">
              {detail.items.map((item) => (
                <li key={item.id} className="flex flex-col gap-0.5">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-sm font-bold text-foreground">
                      {`${item.quantity}x ${item.name}`}
                    </span>
                    <span className="nums-tabular text-sm text-foreground-secondary">
                      {formatClp(item.total)}
                    </span>
                  </div>
                  {item.description !== null && item.description.trim().length > 0 ? (
                    <span className="text-sm text-foreground-secondary">{item.description}</span>
                  ) : null}
                </li>
              ))}
            </ul>
          </section>

          <section className="flex flex-col gap-1">
            <h3 className="label-code text-xs font-bold text-foreground-muted">
              Dirección de entrega
            </h3>
            <p className="text-sm text-foreground">{order.address}</p>
          </section>

          <section className="flex flex-col gap-1">
            <h3 className="label-code text-xs font-bold text-foreground-muted">Totales</h3>
            <dl className="flex flex-col gap-1 text-sm">
              <div className="flex items-center justify-between gap-2">
                <dt className="text-foreground-secondary">Subtotal</dt>
                <dd className="nums-tabular text-foreground">{formatClp(totals.subtotal)}</dd>
              </div>
              <div className="flex items-center justify-between gap-2">
                <dt className="text-foreground-secondary">Tarifa de despacho</dt>
                <dd className="nums-tabular text-foreground">{formatClp(totals.deliveryFee)}</dd>
              </div>
              <div className="flex items-center justify-between gap-2 border-t border-border pt-1">
                <dt className="font-bold text-foreground">Total</dt>
                <dd className="nums-tabular font-display text-base font-bold text-primary">
                  {formatClp(totals.total)}
                </dd>
              </div>
            </dl>
          </section>

          <section className="flex flex-col gap-2">
            <h3 className="label-code text-xs font-bold text-foreground-muted">
              Línea de tiempo del pedido
            </h3>
            <ol aria-label="Línea de tiempo del pedido" className="flex flex-col gap-2 border-l border-border pl-4">
              {KITCHEN_MILESTONES.map((milestone, index) => (
                <li key={milestone} className="flex flex-col gap-0.5">
                  <span className="text-sm font-bold text-foreground">{milestone}</span>
                  <span className="text-xs text-foreground-secondary">
                    {index === 0
                      ? formatOrderDate(order.createdAt)
                      : index < completed
                        ? 'Completado'
                        : 'Pendiente'}
                  </span>
                </li>
              ))}
            </ol>
          </section>
        </>
      )}

      {target !== null ? (
        <Button
          disabled={pending}
          onClick={() => {
            onAdvance(order, target);
          }}
        >
          {`Avanzar a ${target}`}
        </Button>
      ) : null}
    </aside>
  );
}
