import { useMemo } from 'react';

import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Skeleton } from '@/components/ui';
import { isCompletedCourierRoute } from '@/lib/courierStatus';
import { formatOrderDate } from '@/lib/format';

import { useCourierRoutes } from './useCourierRoutes';

/**
 * Completed-delivery history of the courier (spec S13).
 *
 * It reads the same route endpoint as the console and keeps only the routes
 * whose status is `ENTREGADO`; the backend remains the source of truth.
 */
export function CourierHistoryPage() {
  const { routes, error, loading } = useCourierRoutes();

  const completedRoutes = useMemo(
    () => routes.filter((route) => isCompletedCourierRoute(route.status)),
    [routes],
  );

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-1">
        <p className="label-code text-xs text-primary">Repartidor</p>
        <h1 className="font-display text-2xl font-bold text-foreground">Historial de entregas</h1>
        <p className="text-sm text-foreground-secondary">
          Entregas ya completadas por el repartidor.
        </p>
      </header>

      {error !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={error} />
        </div>
      ) : null}

      {loading ? (
        <Skeleton label="Cargando historial" blocks={3} height="h-28" />
      ) : completedRoutes.length === 0 ? (
        <div className="rounded-container border border-dashed border-border bg-surface p-8 text-center">
          <p className="font-display text-lg font-bold text-foreground">
            Todavía no tienes entregas completadas
          </p>
          <p className="mt-1 text-sm text-foreground-secondary">
            Las rutas finalizadas aparecerán en este historial.
          </p>
        </div>
      ) : (
        <ul aria-label="Entregas completadas" className="flex flex-col gap-4">
          {completedRoutes.map((route) => (
            <li key={route.id}>
              <article
                aria-label={`Entrega ${route.code ?? route.orderId}`}
                className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card sm:flex-row sm:items-center sm:justify-between"
              >
                <div className="flex flex-col gap-1">
                  <span className="nums-tabular font-display text-lg font-bold text-foreground">
                    {route.code === null ? `Pedido ${route.orderId}` : `#${route.code}`}
                  </span>
                  <span className="text-sm text-foreground-secondary">
                    {route.deliveryAddress}
                  </span>
                  <span className="text-xs text-foreground-muted">
                    {formatOrderDate(route.createdAt)}
                  </span>
                </div>
                <Badge variant="success">Entregado</Badge>
              </article>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
