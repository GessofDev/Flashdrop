import { useCallback, useEffect, useState } from 'react';

import { asApiError, courierApi } from '@/api';
import type { ApiError, DeliveryRoute } from '@/api';

export interface CourierRoutesState {
  /** Routes returned by the backend. */
  routes: DeliveryRoute[];
  /** Failure of the listing, with the message of the backend. */
  error: ApiError | null;
  /** Whether the listing is still in flight. */
  loading: boolean;
}

/** Load the routes of the authenticated courier. */
export function useCourierRoutes(): CourierRoutesState & { reload: () => void } {
  const [routes, setRoutes] = useState<DeliveryRoute[]>([]);
  const [error, setError] = useState<ApiError | null>(null);
  const [loading, setLoading] = useState(true);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;

    setLoading(true);
    setError(null);

    courierApi
      .listRoutes()
      .then((answer) => {
        if (active) {
          setRoutes(answer);
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (active) {
          setError(asApiError(cause));
          setLoading(false);
        }
      });

    return () => {
      active = false;
    };
  }, [reloadToken]);

  const reload = useCallback(() => {
    setReloadToken((token) => token + 1);
  }, []);

  return { routes, error, loading, reload };
}
