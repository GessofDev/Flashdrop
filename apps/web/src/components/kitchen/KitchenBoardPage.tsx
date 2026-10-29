import { useCallback, useEffect, useMemo, useRef, useState } from 'react';

import { asApiError, ordersApi } from '@/api';
import type { ApiError, OrderDetail, OrderListItem } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button, Skeleton } from '@/components/ui';
import { createKitchenChime } from '@/lib/kitchenChime';
import type { KitchenChime } from '@/lib/kitchenChime';
import { isKitchenStatus, KITCHEN_COLUMNS, matchesKitchenColumn, nextKitchenTransition } from '@/lib/kitchenStatus';
import { useSession } from '@/session';
import type { Role } from '@/session';

import { KitchenColumn } from './KitchenColumn';
import { KitchenOrderCard } from './KitchenOrderCard';
import { OrderDetailDrawer } from './OrderDetailDrawer';

interface KitchenState {
  /** Orders the backend answered with. */
  orders: OrderListItem[];
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the first call is still in flight. */
  loading: boolean;
}

/**
 * The current instant, in epoch milliseconds.
 *
 * The board measures the elapsed time of every card, so it needs a clock. It
 * ticks once per second and it is the only place of the board that keeps a
 * timer alive.
 */
function useNow(intervalMs = 1000): number {
  const [now, setNow] = useState(() => Date.now());

  useEffect(() => {
    const timer = setInterval(() => {
      setNow(Date.now());
    }, intervalMs);

    return () => {
      clearInterval(timer);
    };
  }, [intervalMs]);

  return now;
}

/**
 * Whether the keyboard event happened inside a field.
 *
 * The shortcut of the board must never swallow a space the user is typing, so
 * the fields are left alone.
 */
function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) {
    return false;
  }

  const tag = target.tagName;

  return tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT' || target.isContentEditable;
}

/**
 * Monitor de cocina of the restaurant (spec S6 and S12).
 *
 * The board is built from the mockup
 * `monitor_de_cocina_y_gestor_de_pedidos_mi_tienda`: three columns — the
 * orders to accept, the ones in the kitchen and the ones waiting for the
 * courier —, the elapsed time of every card measured from its creation, the
 * chime of a new order and the keyboard shortcut that advances a card.
 *
 * The board reads the orders of the restaurant with `view=restaurant`, and it
 * moves them with the only two statuses the role may set.
 */
export function KitchenBoardPage() {
  const { activeRole } = useSession();
  const role: Role = activeRole ?? 'CLIENTE';

  const [state, setState] = useState<KitchenState>({ orders: [], error: null, loading: true });
  const [details, setDetails] = useState<Record<string, OrderDetail>>({});
  const [detailErrors, setDetailErrors] = useState<Record<string, ApiError>>({});
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [pendingOrderId, setPendingOrderId] = useState<string | null>(null);
  const [openOrderId, setOpenOrderId] = useState<string | null>(null);
  const [selectedOrderId, setSelectedOrderId] = useState<string | null>(null);
  const [search, setSearch] = useState('');
  const [muted, setMuted] = useState(false);
  const [chime] = useState<KitchenChime>(() => createKitchenChime());
  const [reloadToken, setReloadToken] = useState(0);

  const now = useNow();
  const mounted = useRef(true);
  const requestedDetails = useRef<Set<string>>(new Set());
  const knownPending = useRef<Set<string>>(new Set());

  useEffect(() => {
    mounted.current = true;

    return () => {
      mounted.current = false;
      chime.dispose();
    };
  }, [chime]);

  useEffect(() => {
    let active = true;

    ordersApi
      .listOrders('restaurant')
      .then((orders) => {
        if (active) {
          setState({ orders, error: null, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setState((current) => ({
            ...current,
            error: asApiError(cause),
            loading: false,
          }));
        }
      });

    return () => {
      active = false;
    };
  }, [reloadToken]);

  /**
   * Read the detail of every order the board shows.
   *
   * The listing of the backend carries no dishes, so the board reads the
   * detail of every card once: the card shows the dishes and the drawer
   * reuses the same answer instead of asking again.
   */
  useEffect(() => {
    for (const order of state.orders) {
      if (requestedDetails.current.has(order.id)) {
        continue;
      }

      requestedDetails.current.add(order.id);

      ordersApi
        .getOrder(order.id)
        .then((detail) => {
          if (mounted.current) {
            setDetails((current) => ({ ...current, [order.id]: detail }));
          }
        })
        .catch((cause: unknown) => {
          if (mounted.current) {
            setDetailErrors((current) => ({ ...current, [order.id]: asApiError(cause) }));
          }
        });
    }
  }, [state.orders]);

  /**
   * Ring the chime when a new order enters the board.
   *
   * The chime is silent when the kitchen silenced it; the mute lives in the
   * chime itself, so the effect does not have to repeat the rule.
   */
  useEffect(() => {
    const pending = new Set(
      state.orders
        .filter((order) => order.status.trim().toLowerCase() === 'nuevo pedido')
        .map((order) => order.id),
    );

    const fresh = [...pending].filter((orderId) => !knownPending.current.has(orderId));

    if (fresh.length > 0) {
      chime.play();
    }

    knownPending.current = pending;
  }, [chime, state.orders]);

  useEffect(() => {
    chime.setMuted(muted);
  }, [chime, muted]);

  /**
   * Move an order to the next status of the kitchen.
   *
   * The endpoint answers without the new status, so the board reads the
   * listing again: the card moves to the column the backend answers with.
   */
  const advance = useCallback(async (order: OrderListItem, target: string): Promise<void> => {
    setPendingOrderId(order.id);
    setActionError(null);
    setNotice(null);

    try {
      await ordersApi.updateOrderStatus(order.id, target);
      setNotice(`Pedido ${order.code} movido a ${target}`);
      setReloadToken((token) => token + 1);
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    } finally {
      setPendingOrderId(null);
    }
  }, []);

  /**
   * The shortcut of the keyboard: the space key advances the selected card.
   *
   * The shortcut is ignored inside a field, so it never swallows a space the
   * user is typing.
   */
  useEffect(() => {
    const handler = (event: KeyboardEvent): void => {
      if (event.key !== ' ' && event.code !== 'Space') {
        return;
      }

      if (event.defaultPrevented || isEditableTarget(event.target)) {
        return;
      }

      if (selectedOrderId === null) {
        return;
      }

      const order = state.orders.find((candidate) => candidate.id === selectedOrderId);

      if (order === undefined) {
        return;
      }

      const target = nextKitchenTransition(order.status, role);

      if (target === null) {
        return;
      }

      event.preventDefault();
      void advance(order, target);
    };

    document.addEventListener('keydown', handler);

    return () => {
      document.removeEventListener('keydown', handler);
    };
  }, [advance, role, selectedOrderId, state.orders]);

  const visibleOrders = useMemo(() => {
    const needle = search.trim().toLowerCase();

    return state.orders.filter((order) => {
      if (!isKitchenStatus(order.status)) {
        return false;
      }

      if (needle.length === 0) {
        return true;
      }

      return (
        order.code.toLowerCase().includes(needle) ||
        order.clientName.toLowerCase().includes(needle)
      );
    });
  }, [search, state.orders]);

  const openOrder =
    openOrderId === null ? null : state.orders.find((order) => order.id === openOrderId) ?? null;

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-center lg:justify-between">
        <div className="flex flex-col gap-1">
          <h1 className="font-display text-2xl font-bold text-foreground">Monitor de cocina</h1>
          <p className="text-sm text-foreground-secondary">
            Los pedidos de tu local, de la recepción a la entrega al repartidor.
          </p>
        </div>

        <div className="flex flex-wrap items-center gap-3">
          <span className="rounded-control bg-surface px-3 py-2 text-sm text-foreground-secondary shadow-card">
            Pedidos activos:{' '}
            <span className="nums-tabular font-bold text-foreground">{visibleOrders.length}</span>
          </span>

          <input
            type="search"
            aria-label="Buscar pedido"
            placeholder="Buscar por código o cliente"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
            className="h-10 rounded-control border border-border bg-surface px-3 text-sm text-foreground placeholder:text-foreground-muted"
          />

          <Button
            variant="secondary"
            size="sm"
            onClick={() => setReloadToken((token) => token + 1)}
          >
            Actualizar
          </Button>

          <Button
            variant="secondary"
            size="sm"
            aria-pressed={muted}
            onClick={() => {
              setMuted((value) => !value);
            }}
          >
            {muted ? 'Chime KDS: Silenciado' : 'Chime KDS: Activo'}
          </Button>
        </div>
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

      {state.error !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={state.error} />
        </div>
      ) : null}

      {state.loading ? (
        <Skeleton label="Cargando pedidos" blocks={3} height="h-36" />
      ) : (
        <div className="grid grid-cols-1 items-start gap-4 lg:grid-cols-3">
          {KITCHEN_COLUMNS.map((column) => {
            const orders = visibleOrders.filter((order) =>
              matchesKitchenColumn(order.status, column.status),
            );

            return (
              <KitchenColumn key={column.key} column={column} count={orders.length}>
                {orders.length === 0 ? (
                  <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
                    Sin pedidos
                  </p>
                ) : (
                  orders.map((order) => (
                    <KitchenOrderCard
                      key={order.id}
                      order={order}
                      detail={details[order.id] ?? null}
                      detailError={detailErrors[order.id] ?? null}
                      detailLoading={
                        details[order.id] === undefined && detailErrors[order.id] === undefined
                      }
                      now={now}
                      role={role}
                      selected={selectedOrderId === order.id}
                      pending={pendingOrderId === order.id}
                      onSelect={(orderId) => {
                        setSelectedOrderId(orderId);
                      }}
                      onOpenDetail={(orderId) => {
                        setOpenOrderId(orderId);
                      }}
                      onAdvance={(targetOrder, target) => {
                        void advance(targetOrder, target);
                      }}
                    />
                  ))
                )}
              </KitchenColumn>
            );
          })}
        </div>
      )}

      <OrderDetailDrawer
        order={openOrder}
        detail={openOrderId === null ? null : details[openOrderId] ?? null}
        error={openOrderId === null ? null : detailErrors[openOrderId] ?? null}
        loading={
          openOrderId !== null &&
          details[openOrderId] === undefined &&
          detailErrors[openOrderId] === undefined
        }
        role={role}
        pending={pendingOrderId !== null}
        onClose={() => {
          setOpenOrderId(null);
        }}
        onAdvance={(targetOrder, target) => {
          void advance(targetOrder, target);
        }}
      />
    </div>
  );
}
