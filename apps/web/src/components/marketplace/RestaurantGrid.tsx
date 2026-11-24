import type { ReactNode } from 'react';

import type { ApiError, RestaurantResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

import { RestaurantCard } from './RestaurantCard';
import { Skeleton } from './Skeleton';

export interface RestaurantGridProps {
  /** Restaurants the catalog answered with. */
  restaurants: RestaurantResponse[];
  /** Whether the catalog is still answering. */
  loading: boolean;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Empty state message override. */
  emptyMessage?: string;
  /** Optional action rendered inside the empty state. */
  emptyAction?: ReactNode;
}

/**
 * Grid of the restaurants of the catalog.
 *
 * The grid shows every restaurant of the answer: the mockup offers a
 * "FlashPick" selection, but the backend has no criterion to select them
 * with.
 */
export function RestaurantGrid({
  restaurants,
  loading,
  error,
  emptyMessage,
  emptyAction,
}: RestaurantGridProps) {
  return (
    <section aria-labelledby="marketplace-restaurants-title" className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h2
          id="marketplace-restaurants-title"
          className="font-display text-xl font-bold tracking-tight text-foreground"
        >
          Restaurantes
        </h2>
        {!loading && restaurants.length > 0 ? (
          <span className="text-sm text-foreground-secondary">
            {restaurants.length} {restaurants.length === 1 ? 'local' : 'locales'}
          </span>
        ) : null}
      </div>

      {error !== null ? (
        <ApiErrorNotice error={error} />
      ) : loading ? (
        <Skeleton label="Cargando restaurantes" blocks={2} height="h-56" />
      ) : restaurants.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
          <p>{emptyMessage ?? 'Todavía no hay locales disponibles'}</p>
          {emptyAction ?? null}
        </div>
      ) : (
        <div className="grid grid-cols-1 gap-5 md:grid-cols-2">
          {restaurants.map((restaurant) => (
            <RestaurantCard key={restaurant.id} restaurant={restaurant} />
          ))}
        </div>
      )}
    </section>
  );
}
