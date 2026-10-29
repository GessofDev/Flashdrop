import type { RestaurantResponse } from '@/api';

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
 */
export function RestaurantCard({ restaurant }: RestaurantCardProps) {
  return (
    <article className="flex flex-col overflow-hidden rounded-container border border-border bg-surface shadow-card">
      <div className="aspect-video w-full overflow-hidden">
        <CatalogImage src={restaurant.image} alt={restaurant.name} />
      </div>
      <div className="flex flex-col gap-1 p-4">
        <h3 className="font-display text-base font-bold text-foreground">{restaurant.name}</h3>
        <p className="text-sm text-foreground-secondary">{restaurant.address}</p>
      </div>
    </article>
  );
}
