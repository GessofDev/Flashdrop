import { useEffect, useState } from 'react';

import { asApiError, ordersApi } from '@/api';
import type { ApiError, OrderListItem } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

import { OrderListCard } from './OrderListCard';

interface OrdersState {
  /** Orders the backend answered with. */
  orders: OrderListItem[];
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the call is still in flight. */
  loading: boolean;
}

/**
 * "Mis pedidos" of the customer (spec S6 and S10).
 *
 * The screen lists the orders the backend answers with: the code, the
 * restaurant, the status, the total and the date. Every order links to its
 * tracking.
 */
export function OrderListPage() {
  const [state, setState] = useState<OrdersState>({ orders: [], error: null, loading: true });

  useEffect(() => {
    let active = true;

    setState({ orders: [], error: null, loading: true });

    ordersApi
      .listOrders('client')
      .then((orders) => {
        if (active) {
          setState({ orders, error: null, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState({ orders: [], error: asApiError(cause), loading: false });
        }
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-1">
        <h1 className="font-display text-2xl font-bold text-foreground">Mis pedidos</h1>
        <p className="text-sm text-foreground-secondary">
          Sigue el estado de cada pedido que hiciste.
        </p>
      </header>

      {state.error !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={state.error} />
        </div>
      ) : null}

      {state.loading ? (
        <Skeleton label="Cargando pedidos" blocks={3} height="h-36" />
      ) : state.orders.length === 0 ? (
        <p className="rounded-container border border-dashed border-border bg-surface p-8 text-center text-sm text-foreground-secondary">
          Todavía no tienes pedidos
        </p>
      ) : (
        <ul aria-label="Pedidos del cliente" className="flex flex-col gap-4">
          {state.orders.map((order) => (
            <li key={order.id}>
              <OrderListCard order={order} />
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
