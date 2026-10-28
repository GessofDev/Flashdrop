import { useEffect, useMemo, useState } from 'react';

import {
  asApiError,
  catalogApi,
  courierApi,
  MAX_CLAIM_ORDERS,
  MISSING_COURIER_PROFILE_MESSAGE,
} from '@/api';
import type { ApiError, AvailableDeliveryOrder, CourierVehicle, RestaurantResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Button, Skeleton } from '@/components/ui';
import { useSession } from '@/session';

import { AvailableOrderCard } from './AvailableOrderCard';
import { CourierProfileForm } from './CourierProfileForm';

interface AvailableQueueState {
  /** Orders returned by the backend, in its own order. */
  orders: AvailableDeliveryOrder[];
  /** Failure of the queue call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the queue is still in flight. */
  loading: boolean;
}

function isMissingCourierProfile(message: string): boolean {
  return (
    message === MISSING_COURIER_PROFILE_MESSAGE ||
    message.startsWith('Delivery person not found for user ID:')
  );
}

/**
 * FIFO queue of available orders (spec S7, S13 and S17).
 *
 * The endpoint is scoped by restaurant, so the screen asks the catalog for
 * the restaurants and reads one queue at a time. The order of the cards is
 * exactly the order of the backend response; the client never sorts it by
 * distance, amount or customer.
 */
export function CourierAvailablePage() {
  const { activeRole, session } = useSession();
  const [restaurants, setRestaurants] = useState<RestaurantResponse[]>([]);
  const [restaurantsError, setRestaurantsError] = useState<ApiError | null>(null);
  const [restaurantsLoading, setRestaurantsLoading] = useState(true);
  const [selectedRestaurantId, setSelectedRestaurantId] = useState<number | null>(null);
  const [queue, setQueue] = useState<AvailableQueueState>({
    orders: [],
    error: null,
    loading: true,
  });
  const [selectedOrderIds, setSelectedOrderIds] = useState<Set<number>>(() => new Set());
  const [actionError, setActionError] = useState<ApiError | null>(null);
  const [profileError, setProfileError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [claiming, setClaiming] = useState(false);
  const [creatingProfile, setCreatingProfile] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const canClaim =
    activeRole === 'REPARTIDOR' && (session?.roles.includes('REPARTIDOR') ?? false);

  useEffect(() => {
    let active = true;

    catalogApi
      .listRestaurants()
      .then((answer) => {
        if (active) {
          const firstRestaurantId = answer[0]?.id ?? null;
          setRestaurants(answer);
          setSelectedRestaurantId(firstRestaurantId);
          setRestaurantsLoading(false);
          if (firstRestaurantId !== null) {
            setQueue((current) => ({ ...current, loading: true }));
          }
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setRestaurantsError(asApiError(cause));
          setRestaurantsLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, []);

  useEffect(() => {
    let active = true;

    if (selectedRestaurantId === null) {
      if (!restaurantsLoading) {
        setQueue({ orders: [], error: null, loading: false });
      }
      return () => {
        active = false;
      };
    }

    setQueue((current) => ({ ...current, loading: true, error: null }));

    courierApi
      .listAvailableOrders(selectedRestaurantId)
      .then((orders) => {
        if (active) {
          setQueue({ orders, error: null, loading: false });
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setQueue({ orders: [], error: asApiError(cause), loading: false });
        }
      });

    return () => {
      active = false;
    };
  }, [reloadToken, restaurantsLoading, selectedRestaurantId]);

  const selectedRestaurant = useMemo(
    () => restaurants.find((restaurant) => restaurant.id === selectedRestaurantId) ?? null,
    [restaurants, selectedRestaurantId],
  );

  const toggleOrder = (orderId: number): void => {
    if (!canClaim) {
      return;
    }

    setSelectedOrderIds((current) => {
      const next = new Set(current);

      if (next.has(orderId)) {
        next.delete(orderId);
        return next;
      }

      if (next.size >= MAX_CLAIM_ORDERS) {
        return current;
      }

      next.add(orderId);
      return next;
    });
  };

  const handleClaim = async (): Promise<void> => {
    if (selectedOrderIds.size === 0 || !canClaim) {
      return;
    }

    setClaiming(true);
    setActionError(null);
    setProfileError(null);
    setNotice(null);

    try {
      await courierApi.claimOrders([...selectedOrderIds]);
      setSelectedOrderIds(new Set());
      setNotice('La cola fue actualizada.');
    } catch (cause: unknown) {
      setActionError(asApiError(cause));
    } finally {
      setClaiming(false);
      setReloadToken((token) => token + 1);
    }
  };

  const handleProfileCreation = async (vehicle: CourierVehicle): Promise<void> => {
    setCreatingProfile(true);
    setProfileError(null);
    setNotice(null);

    try {
      await courierApi.createProfile(vehicle);
      setActionError(null);
      setNotice('Perfil de repartidor creado. La cola fue actualizada.');
      setReloadToken((token) => token + 1);
    } catch (cause: unknown) {
      setProfileError(asApiError(cause));
    } finally {
      setCreatingProfile(false);
    }
  };

  const showProfileForm =
    actionError !== null && isMissingCourierProfile(actionError.message);
  const selectedCount = selectedOrderIds.size;
  const claimButtonLabel =
    selectedCount === 1 ? 'Tomar 1 pedido' : `Tomar ${selectedCount} pedidos`;

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <header className="mb-6 flex flex-col gap-4 lg:flex-row lg:items-end lg:justify-between">
        <div className="flex flex-col gap-1">
          <p className="label-code text-xs text-primary">Cola de despacho</p>
          <h1 className="font-display text-2xl font-bold text-foreground">Pedidos disponibles</h1>
          <p className="text-sm text-foreground-secondary">
            Toma hasta {MAX_CLAIM_ORDERS} pedidos del mismo restaurante. El orden es el del backend.
          </p>
        </div>

        <div className="flex flex-col gap-1.5 sm:min-w-64">
          <label className="text-sm font-semibold text-foreground" htmlFor="courier-restaurant">
            Restaurante
          </label>
          <select
            id="courier-restaurant"
            value={selectedRestaurantId === null ? '' : String(selectedRestaurantId)}
            disabled={restaurants.length === 0}
            onChange={(event) => {
              const restaurantId = Number(event.target.value);
              setSelectedRestaurantId(Number.isFinite(restaurantId) ? restaurantId : null);
              setSelectedOrderIds(new Set());
              setActionError(null);
            }}
            className="h-10 rounded-control border border-border bg-surface px-3 text-sm text-foreground"
          >
            {restaurants.map((restaurant) => (
              <option key={restaurant.id} value={String(restaurant.id)}>
                {restaurant.name}
              </option>
            ))}
          </select>
        </div>
      </header>

      {restaurantsError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={restaurantsError} />
        </div>
      ) : null}

      {actionError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice
            error={actionError}
            onRetry={() => {
              void handleClaim();
            }}
          />
        </div>
      ) : null}

      {profileError !== null ? (
        <div className="mb-6">
          <ApiErrorNotice error={profileError} />
        </div>
      ) : null}

      {notice !== null ? (
        <p role="status" className="mb-6 text-sm font-medium text-success">
          {notice}
        </p>
      ) : null}

      {showProfileForm ? (
        <div className="mb-6">
          <CourierProfileForm
            pending={creatingProfile}
            onSubmit={(vehicle) => {
              void handleProfileCreation(vehicle);
            }}
          />
        </div>
      ) : null}

      {restaurantsLoading || queue.loading ? (
        <Skeleton label="Cargando pedidos disponibles" blocks={3} height="h-40" />
      ) : queue.error !== null ? (
        <ApiErrorNotice error={queue.error} />
      ) : queue.orders.length === 0 ? (
        <div className="rounded-container border border-dashed border-border bg-surface p-8 text-center">
          <p className="font-display text-lg font-bold text-foreground">
            No hay pedidos disponibles
          </p>
          <p className="mt-1 text-sm text-foreground-secondary">
            {selectedRestaurant === null
              ? 'No hay restaurantes para consultar.'
              : `No hay pedidos listos en ${selectedRestaurant.name}.`}
          </p>
        </div>
      ) : (
        <section className="flex flex-col gap-4" aria-label="Cola de pedidos">
          <div className="flex flex-wrap items-center justify-between gap-3 rounded-container border border-border bg-surface p-4 shadow-card">
            <p className="text-sm text-foreground-secondary">
              {selectedRestaurant === null
                ? 'Selecciona un restaurante para consultar su cola.'
                : `Pedidos listos en ${selectedRestaurant.name}.`}
            </p>
            {canClaim ? (
              <Button disabled={selectedCount === 0 || claiming} onClick={() => void handleClaim()}>
                {claimButtonLabel}
              </Button>
            ) : null}
          </div>

          <ul aria-label="Pedidos disponibles" className="flex flex-col gap-4">
            {queue.orders.map((order) => {
              const selected = selectedOrderIds.has(order.orderId);
              const selectionDisabled = !selected && selectedOrderIds.size >= MAX_CLAIM_ORDERS;

              return (
                <li key={order.orderId}>
                  <AvailableOrderCard
                    order={order}
                    selected={selected}
                    selectionDisabled={selectionDisabled}
                    canSelect={canClaim}
                    onToggle={toggleOrder}
                  />
                </li>
              );
            })}
          </ul>
        </section>
      )}
    </div>
  );
}
