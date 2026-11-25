import { Link } from 'react-router-dom';

import type { RestaurantResponse } from '@/api';
import { restaurantPath } from '@/app/routePaths';
import { RatingAverage } from '@/components/ratings';
import { Badge } from '@/components/ui';
import { formatRestaurantName } from '@/lib/format';

import { CatalogImage } from './CatalogImage';

export interface RestaurantCardProps {
  /** Restaurant the catalog answered with. */
  restaurant: RestaurantResponse;
}

/**
 * Card of a restaurant.
 *
 * The catalog sends the name, the address, the picture and whether the local
 * takes orders now, so the card shows exactly those. It also sends the
 * average and the count of the scores once it holds the aggregate of the
 * ratings, and the card shows them only then: the backend sends no delivery
 * time and no shipping cost, so the card cannot show them.
 *
 * The card is the way into the local: it links to the page where the
 * customer sees its dishes.
 */
export function RestaurantCard({ restaurant }: RestaurantCardProps) {
  const displayName = formatRestaurantName(restaurant);

  return (
    <Link
      to={restaurantPath(restaurant.id)}
      className="flex flex-col overflow-hidden rounded-container border border-border bg-surface shadow-card transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
    >
      <div className="aspect-video w-full overflow-hidden">
        <CatalogImage src={restaurant.image} alt={displayName} />
      </div>
      <div className="flex flex-col gap-1 p-4">
        <div className="flex items-center justify-between gap-2">
          <h3 className="font-display text-base font-bold text-foreground">{displayName}</h3>
          {restaurant.open === false ? <Badge variant="destructive">Cerrado</Badge> : null}
        </div>
        <p className="text-sm text-foreground-secondary">{restaurant.address}</p>
        <RatingAverage
          average={restaurant.ratingAverage ?? null}
          count={restaurant.ratingCount ?? null}
        />
      </div>
    </Link>
  );
}
