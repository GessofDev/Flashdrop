import { useEffect, useState } from 'react';

import { asApiError } from '@/api';
import type { ApiError } from '@/api';

export interface CatalogResource<T> {
  /** Answer of the catalog; `null` while it is loading or after a failure. */
  data: T | null;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Whether the call is still in flight. */
  loading: boolean;
}

/**
 * Load a resource of the catalog.
 *
 * The loader is stable, so the section reloads only when the filter of the
 * section changes.
 */
export function useCatalogResource<T>(loader: () => Promise<T>): CatalogResource<T> {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;

    setLoading(true);
    setError(null);
    setData(null);

    loader()
      .then((answer) => {
        if (active) {
          setData(answer);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(asApiError(cause));
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
  }, [loader]);

  return { data, error, loading };
}
