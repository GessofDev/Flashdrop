import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  useSyncExternalStore,
} from 'react';
import type { ReactNode } from 'react';

import { storeCatalogApi } from '@/api';
import type { AvailableRestaurant, OwnerRestaurantResponse } from '@/api';
import { useSession } from '@/session';
import { storeContextStore } from '@/session/storeContextStore';

export interface StoreContextValue {
  /** All operable stores of the merchant for the signed-in operator. */
  restaurants: OwnerRestaurantResponse[];
  /** Currently selected store ID sent via `X-My-Store-Restaurant`. */
  selectedStoreId: number | null;
  /** Currently selected store record, if loaded. */
  selectedStore: OwnerRestaurantResponse | null;
  /** Stores returned by a `400 LOCAL_CONTEXT_REQUIRED` response when no store was selected. */
  localContextRequired: AvailableRestaurant[] | null;
  /** Whether the store list is currently loading. */
  loading: boolean;
  /** Select an active store ID (persisted in localStorage). */
  selectStore: (storeId: number | null) => void;
  /** Reload the list of stores from `GET /api/catalog/my/restaurants`. */
  refreshStores: () => void;
}

const StoreContext = createContext<StoreContextValue | null>(null);

export interface StoreProviderProps {
  children: ReactNode;
}

/**
 * Loads `GET /api/catalog/my/restaurants` when the user is in `RESTAURANTE`
 * role, tracks the active `selectedStoreId`, and surfaces `400 LOCAL_CONTEXT_REQUIRED`
 * so the store shell can prompt the operator to pick a store.
 */
export function StoreProvider({ children }: StoreProviderProps) {
  const { session, activeRole } = useSession();
  const snapshot = useSyncExternalStore(
    storeContextStore.subscribe,
    storeContextStore.getSnapshot,
    storeContextStore.getSnapshot,
  );

  const [restaurants, setRestaurants] = useState<OwnerRestaurantResponse[]>([]);
  const [loading, setLoading] = useState(false);
  const [reloadToken, setReloadToken] = useState(0);

  const isStoreRole = session !== null && activeRole === 'RESTAURANTE';

  useEffect(() => {
    if (!isStoreRole) {
      setRestaurants([]);
      setLoading(false);
      return;
    }

    let active = true;
    setLoading(true);

    storeCatalogApi
      .listMyRestaurants()
      .then((list) => {
        if (!active) {
          return;
        }

        setRestaurants(list);

        const firstStore = list[0];
        if (firstStore !== undefined) {
          const currentId = storeContextStore.getSelectedStoreId();
          const stillValid = currentId !== null && list.some((item) => item.id === currentId);
          if (!stillValid) {
            storeContextStore.setSelectedStoreId(firstStore.id);
          }
        }
      })
      .catch(() => {
        if (active) {
          setRestaurants([]);
        }
      })
      .finally(() => {
        if (active) {
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [isStoreRole, session?.userId, reloadToken]);

  const selectStore = useCallback((storeId: number | null) => {
    storeContextStore.setSelectedStoreId(storeId);
  }, []);

  const refreshStores = useCallback(() => {
    setReloadToken((token) => token + 1);
  }, []);

  const selectedStore = useMemo(
    () => restaurants.find((item) => item.id === snapshot.selectedStoreId) ?? null,
    [restaurants, snapshot.selectedStoreId],
  );

  const value = useMemo<StoreContextValue>(
    () => ({
      restaurants,
      selectedStoreId: snapshot.selectedStoreId,
      selectedStore,
      localContextRequired: snapshot.localContextRequired,
      loading,
      selectStore,
      refreshStores,
    }),
    [
      restaurants,
      snapshot.selectedStoreId,
      selectedStore,
      snapshot.localContextRequired,
      loading,
      selectStore,
      refreshStores,
    ],
  );

  return <StoreContext.Provider value={value}>{children}</StoreContext.Provider>;
}

/**
 * Read the active store context.
 *
 * Falls back to reading {@link storeContextStore} directly when used in
 * standalone component tests without a {@link StoreProvider}.
 */
export function useStoreContext(): StoreContextValue {
  const context = useContext(StoreContext);
  const snapshot = useSyncExternalStore(
    storeContextStore.subscribe,
    storeContextStore.getSnapshot,
    storeContextStore.getSnapshot,
  );

  if (context !== null) {
    return context;
  }

  return {
    restaurants: [],
    selectedStoreId: snapshot.selectedStoreId,
    selectedStore: null,
    localContextRequired: snapshot.localContextRequired,
    loading: false,
    selectStore: (storeId) => storeContextStore.setSelectedStoreId(storeId),
    refreshStores: () => undefined,
  };
}
