import { useMemo, useState } from 'react';

import { asApiError, ordersApi } from '@/api';
import type { ApiError, DeliveryRoute } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Skeleton } from '@/components/ui';
import { isCompletedCourierRoute, nextCourierTransition } from '@/lib/courierStatus';
import { formatOrderDate } from '@/lib/format';
import { useSession } from '@/session';

import { CourierRouteCard } from './CourierRouteCard';
import { useCourierRoutes } from './useCourierRoutes';

function actionLabelFor(transition: 'Retirado' | 'Entregado'): string {
  return transition === 'Retirado' ? 'Confirmar retiro' : 'Confirmar entrega';
}

/**
 * Courier console (spec S6, S7 and S13).
 *
 * The screen is built from the mockup
 * `consola_del_repartidor_flashdrop_courier`, reduced to the live facts of the
 * backend: the active routes of the courier and the latest completed
 * deliveries. It never paints a map, a position or a delivery secret.
 */
export function CourierConsolePage() {
  const { activeRole, session } = useSession();
  const { routes, error, loading, reload } = useCourierRoutes();
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<number | null>(null);

  const canAct =
    activeRole === 'REPARTIDOR' && (session?.roles.includes('REPARTIDOR') ?? false);

  const activeRoutes = useMemo(
    () => routes.filter((route) => !isCompletedCourierRoute(route.status)),
    [routes],
  );

  const completedRoutes = useMemo(
    () => routes.filter((route) => isCompletedCourierRoute(route.status)),
    [routes],
  );

  const advance = async (route: DeliveryRoute, actionLabel: string): Promise<void> => {
    const target = actionLabel === 'Confirmar retiro' ? 'Retirado' : 'Entregado';

    setPendingOrderId(route.orderId);
    setActionError(null);
    setNotice(null);

    try {
      await ordersApi.updateOrderStatus(String(route.orderId), target);
      setNotice(`Pedido ${route.code ?? route.orderId} actualizado a ${target}`);
      reload();
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
      reload();
    } finally {
      setPendingOrderId(null);
    }
  };

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-3 lg:flex-row lg:items-end lg:justify-between">
        <div className="flex flex-col gap-1">
          <p className="label-code text-xs text-primary">Repartidor</p>
          <h1 className="font-display text-2xl font-bold text-foreground">Consola del repartidor</h1>
          <p className="text-sm text-foreground-secondary">
            Gestiona tus rutas activas y revisa las últimas entregas completadas.
          </p>
        </div>
        <Badge variant="info">{`Rutas activas: ${activeRoutes.length}`}</Badge>
      </header>

      {actionError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={actionError} />
        </div>
      ) : null}

      {notice !== null ? (
        <p role="status" className="mb-6 text-sm font-medium text-success">
          {notice}
        </p>
      ) : null}

      {error !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={error} />
        </div>
      ) : null}

      {loading ? (
        <Skeleton label="Cargando rutas" blocks={2} height="h-48" />
      ) : (
        <div className="grid items-start gap-6 lg:grid-cols-12">
          <section aria-label="Rutas activas" className="flex flex-col gap-4 lg:col-span-7">
            <h2 className="font-display text-lg font-bold text-foreground">Ruta activa</h2>

            {activeRoutes.length === 0 ? (
              <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
                No tienes rutas activas
              </p>
            ) : (
              activeRoutes.map((route) => {
                const transition = nextCourierTransition(route.status, activeRole ?? 'CLIENTE');
                const actionLabel = transition === null ? null : actionLabelFor(transition);

                return (
                  <CourierRouteCard
                    key={route.id}
                    route={route}
                    articleLabel="Ruta"
                    actionLabel={canAct ? actionLabel : null}
                    pending={pendingOrderId === route.orderId}
                    onAdvance={(targetRoute, targetAction) => {
                      void advance(targetRoute, targetAction);
                    }}
                  />
                );
              })
            )}
          </section>

          <section
            aria-label="Últimas entregas"
            className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card lg:col-span-5"
          >
            <div className="flex items-center justify-between gap-3">
              <h2 className="font-display text-lg font-bold text-foreground">Últimas entregas</h2>
              <span className="nums-tabular text-sm text-foreground-muted">
                {completedRoutes.length}
              </span>
            </div>

            {completedRoutes.length === 0 ? (
              <p className="text-sm text-foreground-secondary">Todavía no tienes entregas completadas</p>
            ) : (
              <ul className="flex flex-col gap-3">
                {completedRoutes.slice(0, 4).map((route) => (
                  <li key={route.id}>
                    <article
                      aria-label={`Entrega ${route.code ?? route.orderId}`}
                      className="flex flex-col gap-2 rounded-control bg-canvas p-3"
                    >
                      <div className="flex items-center justify-between gap-3">
                        <span className="nums-tabular font-display font-bold text-foreground">
                          {route.code === null ? `Pedido ${route.orderId}` : `#${route.code}`}
                        </span>
                        <Badge variant="success">Entregado</Badge>
                      </div>
                      <span className="text-sm text-foreground-secondary">
                        {route.deliveryAddress}
                      </span>
                      <span className="text-xs text-foreground-muted">
                        {formatOrderDate(route.createdAt)}
                      </span>
                    </article>
                  </li>
                ))}
              </ul>
            )}
          </section>
        </div>
      )}
    </div>
  );
}
