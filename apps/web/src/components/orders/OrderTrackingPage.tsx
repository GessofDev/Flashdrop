import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { ApiError, asApiError, ordersApi } from '@/api';
import type { OrderDetail } from '@/api';
import { routePaths } from '@/app/routePaths';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

import { CourierCard } from './CourierCard';
import { GuaranteeCard } from './GuaranteeCard';
import { OrderStatusBadge } from './OrderStatusBadge';
import { OrderStepper } from './OrderStepper';
import { OrderSummary } from './OrderSummary';
import { OrderTimeline } from './OrderTimeline';
import { StaticRouteMap } from './StaticRouteMap';

interface TrackingState {
  /** Order the backend answered with. */
  order: OrderDetail | null;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the call is still in flight. */
  loading: boolean;
}

/**
 * Tracking of one order (spec S6 and S10).
 *
 * The screen is built from the mockup
 * `seguimiento_de_pedido_en_vivo_fd_0042_flashdrop_v2.0`: the stepper of the
 * five states of the order, the trace of the order, the summary and the
 * schematic view of the route.
 *
 * Everything it shows comes from `GET /api/orders/{id}`. The blocks the
 * backend cannot resolve — the courier and the route — are left out instead
 * of being filled with something the app would have to invent.
 */
export function OrderTrackingPage() {
  const { id } = useParams<{ id: string }>();
  const orderId = id ?? '';

  const [state, setState] = useState<TrackingState>({
    order: null,
    error: null,
    loading: true,
  });

  useEffect(() => {
    let active = true;

    setState({ order: null, error: null, loading: true });

    if (orderId.length === 0) {
      setState({
        order: null,
        error: new ApiError('Pedido no encontrado', { status: 404 }),
        loading: false,
      });

      return () => {
        active = false;
      };
    }

    ordersApi
      .getOrder(orderId)
      .then((order) => {
        if (active) {
          setState({ order, error: null, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState({ order: null, error: asApiError(cause), loading: false });
        }
      });

    return () => {
      active = false;
    };
  }, [orderId]);

  const order = state.order;

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <nav aria-label="Ruta de navegación" className="mb-4 flex items-center gap-2 text-sm">
        <Link to={routePaths.orders} className="font-semibold text-primary hover:text-primary-hover">
          Mis Pedidos
        </Link>
        {order !== null ? (
          <>
            <span className="text-foreground-muted">/</span>
            <span className="nums-tabular font-semibold text-foreground">Orden #{order.code}</span>
          </>
        ) : null}
      </nav>

      {state.error !== null ? (
        <ApiErrorNotice error={state.error} />
      ) : state.loading || order === null ? (
        <Skeleton label="Cargando el pedido" blocks={2} height="h-48" />
      ) : (
        <>
          <section className="mb-6 flex flex-col gap-5 rounded-container border border-border bg-surface p-5 shadow-card">
            <div className="flex flex-wrap items-center justify-between gap-3">
              <div className="flex flex-col gap-1">
                <span className="label-code text-xs text-foreground-muted">Estado del pedido</span>
                <span className="nums-tabular font-display text-xl font-bold text-foreground">
                  #{order.code}
                </span>
              </div>
              <OrderStatusBadge status={order.status} />
            </div>

            <OrderStepper status={order.status} />
          </section>

          <div className="grid grid-cols-1 items-start gap-6 lg:grid-cols-12">
            <div className="flex flex-col gap-6 lg:col-span-7">
              {order.route !== null ? (
                <StaticRouteMap
                  pickupAddress={order.route.pickupAddress}
                  deliveryAddress={order.route.deliveryAddress}
                  status={order.route.status}
                />
              ) : null}

              {order.delivery !== null ? (
                <CourierCard
                  name={order.delivery.name}
                  phone={order.delivery.phone}
                  vehicle={order.delivery.vehicle}
                />
              ) : null}

              <OrderTimeline
                status={order.status}
                routeStatus={order.route?.status ?? null}
                createdAt={order.createdAt}
              />
            </div>

            <div className="flex flex-col gap-6 lg:col-span-5">
              <OrderSummary order={order} />
              <GuaranteeCard />
            </div>
          </div>
        </>
      )}
    </div>
  );
}
