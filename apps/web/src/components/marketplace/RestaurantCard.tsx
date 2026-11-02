import { Link } from 'react-router-dom';

import type { RestaurantResponse } from '@/api';
import { restaurantPath } from '@/app/routePaths';

import { CatalogImage } from './CatalogImage';

export interface RestaurantCardProps {
  /** Restaurant the catalog answered with. */
  restaurant: RestaurantResponse;
}

/**
 * Card of a restaurant.
 *
 * The catalog only sends the name, the address and the picture, so the card
 * shows exactly those three things: the backend sends no customer score, no
 * delivery time and no shipping cost, so the card cannot show them.
 *
 * The card is the way into the local: it links to the page where the
 * customer sees its dishes.
 */
export function RestaurantCard({ restaurant }: RestaurantCardProps) {
  return (
    <Link
      to={restaurantPath(restaurant.id)}
      className="flex flex-col overflow-hidden rounded-container border border-border bg-surface shadow-card transition-colors hover:border-primary focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
    >
      <div className="aspect-video w-full overflow-hidden">
        <CatalogImage src={restaurant.image} alt={restaurant.name} />
      </div>
      <div className="flex flex-col gap-1 p-4">
        <h3 className="font-display text-base font-bold text-foreground">{restaurant.name}</h3>
        <p className="text-sm text-foreground-secondary">{restaurant.address}</p>
      </div>
    </Link>
  );
}
