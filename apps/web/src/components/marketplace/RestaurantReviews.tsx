import type { TargetReviewsResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { ReviewList } from '@/components/ratings';
import { Skeleton } from '@/components/ui';

import type { CatalogResource } from './useCatalogResource';

export interface RestaurantReviewsProps {
  /** Answer of the reviews service for this local. */
  resource: CatalogResource<TargetReviewsResponse | null>;
}

/**
 * Public ratings of a local (spec S8).
 *
 * The list shows the comments the backend published, with the author, the
 * score and the date. A local without ratings says so instead of showing an
 * empty list, and a failed call shows the message of the backend.
 */
export function RestaurantReviews({ resource }: RestaurantReviewsProps) {
  const reviews = resource.data?.reviews ?? [];

  return (
    <section aria-labelledby="restaurant-reviews-title" className="flex flex-col gap-4">
      <h2
        id="restaurant-reviews-title"
        className="font-display text-xl font-bold tracking-tight text-foreground"
      >
        Reseñas del local
      </h2>

      {resource.error !== null ? (
        <ApiErrorNotice error={resource.error} />
      ) : resource.loading ? (
        <Skeleton label="Cargando reseñas" blocks={2} height="h-24" />
      ) : reviews.length === 0 ? (
        <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
          Este local todavía no tiene reseñas
        </p>
      ) : (
        <ReviewList reviews={reviews} />
      )}
    </section>
  );
}
