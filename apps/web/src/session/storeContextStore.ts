import type { AvailableRestaurant } from '@/api/types';

export const SELECTED_STORE_STORAGE_KEY = 'flashdrop.selectedStoreId.v1';

export interface StoreContextSnapshot {
  selectedStoreId: number | null;
  localContextRequired: AvailableRestaurant[] | null;
}

function readStoredStoreId(): number | null {
  if (typeof window === 'undefined') {
    return null;
  }

  try {
    const raw = window.localStorage.getItem(SELECTED_STORE_STORAGE_KEY);
    if (raw === null) {
      return null;
    }

    const parsed = Number(raw);
    return Number.isInteger(parsed) && parsed > 0 ? parsed : null;
  } catch {
    return null;
  }
}

function writeStoredStoreId(storeId: number | null): void {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    if (storeId === null) {
      window.localStorage.removeItem(SELECTED_STORE_STORAGE_KEY);
    } else {
      window.localStorage.setItem(SELECTED_STORE_STORAGE_KEY, String(storeId));
    }
  } catch {
    // Ignore storage errors (private mode, quota).
  }
}

let cachedSnapshot: StoreContextSnapshot | null = null;
const listeners = new Set<() => void>();

function currentSnapshot(): StoreContextSnapshot {
  if (cachedSnapshot === null) {
    cachedSnapshot = {
      selectedStoreId: readStoredStoreId(),
      localContextRequired: null,
    };
  }

  return cachedSnapshot;
}

function emit(next: StoreContextSnapshot): void {
  cachedSnapshot = next;

  for (const listener of listeners) {
    listener();
  }
}

/**
 * Module store for the active store ID (`X-My-Store-Restaurant`) and any
 * `400 LOCAL_CONTEXT_REQUIRED` prompt.
 */
export const storeContextStore = {
  getSnapshot(): StoreContextSnapshot {
    return currentSnapshot();
  },

  subscribe(listener: () => void): () => void {
    listeners.add(listener);
    return () => {
      listeners.delete(listener);
    };
  },

  getSelectedStoreId(): number | null {
    return currentSnapshot().selectedStoreId;
  },

  setSelectedStoreId(storeId: number | null): void {
    const normalized = storeId !== null && Number.isInteger(storeId) && storeId > 0 ? storeId : null;
    writeStoredStoreId(normalized);
    emit({
      selectedStoreId: normalized,
      localContextRequired: normalized !== null ? null : currentSnapshot().localContextRequired,
    });
  },

  setLocalContextRequired(restaurants: AvailableRestaurant[] | null): void {
    emit({
      ...currentSnapshot(),
      localContextRequired: restaurants,
    });
  },

  clear(): void {
    writeStoredStoreId(null);
    emit({
      selectedStoreId: null,
      localContextRequired: null,
    });
  },

  reset(): void {
    writeStoredStoreId(null);
    cachedSnapshot = {
      selectedStoreId: null,
      localContextRequired: null,
    };
    for (const listener of listeners) {
      listener();
    }
  },
};
