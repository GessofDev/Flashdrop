import { useCallback, useEffect, useMemo, useState } from 'react';

import { asApiError, courierApi, ordersApi } from '@/api';
import type { ApiError, DeliveryRoute } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Badge, Skeleton } from '@/components/ui';
import { isCompletedCourierRoute, nextCourierTransition } from '@/lib/courierStatus';
import { formatOrderDate } from '@/lib/format';
import { LocationError, requestBrowserLocation } from '@/lib/geolocation';
import { useSession } from '@/session';

import { CourierRouteCard } from './CourierRouteCard';
import { DeliveryCodeForm } from './DeliveryCodeForm';
import { RouteIncidentForm } from './RouteIncidentForm';
import type { RouteIncidentMode } from './RouteIncidentForm';
import { useCourierRoutes } from './useCourierRoutes';

function actionLabelFor(transition: 'Retirado' | 'Entregado'): string {
  return transition === 'Retirado' ? 'Confirmar retiro' : 'Confirmar entrega';
}

/**
 * Public code of the normal reception mode.
 *
 * `orders-service` only protects the secure mode, so a normal order is
 * confirmed with this code and the courier does not type anything.
 */
const NORMAL_DELIVERY_CODE = '000000';

/** Interval for reporting courier live location while sharing is active. */
const LOCATION_REPORT_INTERVAL_MS = 10_000;

/**
 * Courier console (spec S6, S7 and S13).
 *
 * The screen is built from the mockup
 * `consola_del_repartidor_flashdrop_courier`, reduced to the live facts of the
 * backend: the active routes of the courier and the latest completed
 * deliveries. It never paints a map or a delivery secret.
 */
export function CourierConsolePage() {
  const { activeRole, session } = useSession();
  const { routes, error, loading, reload } = useCourierRoutes();
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<number | null>(null);
  const [codeRoute, setCodeRoute] = useState<DeliveryRoute | null>(null);
  const [incidentTarget, setIncidentTarget] = useState<{
    route: DeliveryRoute;
    mode: RouteIncidentMode;
  } | null>(null);
  const [sharingLocation, setSharingLocation] = useState(false);
  const [locationStatus, setLocationStatus] = useState<string | null>(null);
  const [locationError, setLocationError] = useState<string | null>(null);

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

  const reportCurrentLocation = useCallback(async (): Promise<void> => {
    try {
      const coords = await requestBrowserLocation();
      await courierApi.reportLocation({
        latitude: coords.latitude,
        longitude: coords.longitude,
        recordedAt: new Date().toISOString(),
      });
      setLocationError(null);
      setLocationStatus(`Ubicación enviada (${formatOrderDate(new Date().toISOString())})`);
    } catch (cause: unknown) {
      const message =
        cause instanceof LocationError
          ? cause.message
          : asApiError(cause).message;
      setLocationError(message);
    }
  }, []);

  useEffect(() => {
    if (!sharingLocation || !canAct || activeRoutes.length === 0) {
      return;
    }

    void reportCurrentLocation();
    const timer = window.setInterval(() => {
      void reportCurrentLocation();
    }, LOCATION_REPORT_INTERVAL_MS);

    return () => {
      window.clearInterval(timer);
    };
  }, [sharingLocation, canAct, activeRoutes.length, reportCurrentLocation]);

  /**
   * Move the order to `Retirado`.
   *
   * The backend owns the transition rules: it rejects the change when the
   * order is not assigned to this courier or when the status does not allow
   * it, and the message of the backend is shown as it is.
   */
  const moveToPickup = async (route: DeliveryRoute): Promise<void> => {
    setPendingOrderId(route.orderId);
    setActionError(null);
    setNotice(null);

    try {
      await ordersApi.updateOrderStatus(String(route.orderId), 'Retirado');
      setNotice(`Pedido ${route.code ?? route.orderId} actualizado a Retirado`);
      reload();
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
      reload();
    } finally {
      setPendingOrderId(null);
    }
  };

  /**
   * Reception mode of the order.
   *
   * The route declares it; when an old route does not, the detail of the
   * order answers it, so a secure order is never confirmed with the public
   * code.
   */
  const receptionModeOf = async (route: DeliveryRoute): Promise<'NORMAL' | 'SEGURO'> => {
    const declared = route.receptionMode?.trim().toUpperCase();

    if (declared === 'SEGURO' || declared === 'NORMAL') {
      return declared;
    }

    const detail = await ordersApi.getOrder(String(route.orderId));

    return detail.receptionMode?.trim().toUpperCase() === 'SEGURO' ? 'SEGURO' : 'NORMAL';
  };

  /**
   * Confirm the delivery with the code.
   *
   * `POST /api/orders/{id}/deliver` is the only way to reach the delivered
   * status: the backend rejects `PUT /status` with `Entregado` in the secure
   * mode.
   */
  const confirmDelivery = async (route: DeliveryRoute, deliveryCode: string): Promise<void> => {
    setPendingOrderId(route.orderId);
    setActionError(null);

    try {
      await ordersApi.deliverOrder(String(route.orderId), deliveryCode);
      setNotice(`Pedido ${route.code ?? route.orderId} entregado`);
      setCodeRoute(null);
      reload();
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    } finally {
      setPendingOrderId(null);
    }
  };

  /**
   * Start the confirmation of the delivery.
   *
   * The secure mode asks the customer for the code, so the courier types it;
   * the normal mode is confirmed with the public code.
   */
  const startDelivery = async (route: DeliveryRoute): Promise<void> => {
    setActionError(null);
    setNotice(null);

    try {
      const mode = await receptionModeOf(route);

      if (mode === 'SEGURO') {
        setCodeRoute(route);

        return;
      }

      await confirmDelivery(route, NORMAL_DELIVERY_CODE);
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    }
  };

  /** Send the code the customer dictated. */
  const submitCode = async (deliveryCode: string): Promise<void> => {
    if (codeRoute === null) {
      return;
    }

    await confirmDelivery(codeRoute, deliveryCode);
  };

  const advance = async (route: DeliveryRoute, actionLabel: string): Promise<void> => {
    if (actionLabel === 'Confirmar retiro') {
      await moveToPickup(route);

      return;
    }

    await startDelivery(route);
  };

  const submitIncident = async (payload: {
    mode: RouteIncidentMode;
    status: 'Cancelado' | 'Rechazado';
    reason: string;
  }): Promise<void> => {
    if (incidentTarget === null) {
      return;
    }

    const target = incidentTarget.route;
    setPendingOrderId(target.orderId);
    setActionError(null);
    setNotice(null);

    try {
      if (payload.mode === 'release') {
        await ordersApi.releaseOrder(String(target.orderId), payload.reason);
        setNotice(`Pedido ${target.code ?? target.orderId} liberado`);
      } else {
        await ordersApi.cancelAfterPickup(String(target.orderId), payload.status, payload.reason);
        setNotice(`Pedido ${target.code ?? target.orderId} anulado`);
      }
      setIncidentTarget(null);
      reload();
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
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

      {canAct && activeRoutes.length > 0 ? (
        <section
          aria-label="Ubicación en vivo"
          className="flex flex-wrap items-center justify-between gap-3 rounded-container border border-border bg-surface p-4 shadow-card"
        >
          <div className="flex flex-col gap-0.5">
            <span className="text-sm font-semibold text-foreground">Ubicación en vivo</span>
            <span className="text-xs text-foreground-secondary">
              {locationError !== null
                ? locationError
                : sharingLocation
                  ? (locationStatus ?? 'Enviando tu posición al cliente...')
                  : 'Comparte tu posición mientras entregas tus rutas activas.'}
            </span>
          </div>
          <button
            type="button"
            onClick={() => {
              setLocationError(null);
              if (sharingLocation) {
                setSharingLocation(false);
                setLocationStatus(null);
              } else {
                setSharingLocation(true);
              }
            }}
            className="inline-flex items-center justify-center rounded-control border border-border bg-surface-hover px-3.5 py-2 text-sm font-semibold text-foreground transition-colors hover:bg-border/40 focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
          >
            {sharingLocation ? 'Detener ubicación en vivo' : 'Compartir ubicación en vivo'}
          </button>
        </section>
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
                const secondaryLabel =
                  transition === 'Retirado'
                    ? 'Liberar pedido'
                    : transition === 'Entregado'
                      ? 'Anular tras retiro'
                      : null;

                return (
                  <CourierRouteCard
                    key={route.id}
                    route={route}
                    articleLabel="Ruta"
                    actionLabel={canAct ? actionLabel : null}
                    secondaryActionLabel={canAct ? secondaryLabel : null}
                    pending={pendingOrderId === route.orderId}
                    onAdvance={(targetRoute, targetAction) => {
                      void advance(targetRoute, targetAction);
                    }}
                    onSecondaryAction={(targetRoute) => {
                      setActionError(null);
                      setNotice(null);
                      setIncidentTarget({
                        route: targetRoute,
                        mode: transition === 'Retirado' ? 'release' : 'cancel-after-pickup',
                      });
                    }}
                  />
                );
              })
            )}

            {incidentTarget !== null ? (
              <RouteIncidentForm
                route={incidentTarget.route}
                mode={incidentTarget.mode}
                pending={pendingOrderId === incidentTarget.route.orderId}
                onSubmit={submitIncident}
                onCancel={() => {
                  setActionError(null);
                  setIncidentTarget(null);
                }}
              />
            ) : null}

            {codeRoute !== null ? (
              <DeliveryCodeForm
                route={codeRoute}
                pending={pendingOrderId === codeRoute.orderId}
                onSubmit={(deliveryCode) => {
                  void submitCode(deliveryCode);
                }}
                onCancel={() => {
                  setCodeRoute(null);
                }}
              />
            ) : null}
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
