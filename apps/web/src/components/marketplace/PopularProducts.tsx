import type { ApiError, ProductResponse } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

import { ProductCard } from './ProductCard';
import { Skeleton } from './Skeleton';

export interface PopularProductsProps {
  /** Dishes the catalog answered with. */
  products: ProductResponse[];
  /** Whether the catalog is still answering. */
  loading: boolean;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
  /** Name of every restaurant, to say which local each dish belongs to. */
  restaurantNames: Map<number, string>;
  /** Add a dish to the cart. */
  onAdd: (product: ProductResponse) => void;
}

/**
 * Grid of the dishes of the catalog.
 *
 * The section keeps the name of the mockup, but the dishes it shows are the
 * ones the catalog sends as available: the backend has no metric of
 * popularity.
 */
export function PopularProducts({
  products,
  loading,
  error,
  restaurantNames,
  onAdd,
}: PopularProductsProps) {
  return (
    <section aria-labelledby="marketplace-products-title" className="flex flex-col gap-4">
      <h2
        id="marketplace-products-title"
        className="font-display text-xl font-bold tracking-tight text-foreground"
      >
        Platos Populares
      </h2>

      {error !== null ? (
        <ApiErrorNotice error={error} />
      ) : loading ? (
        <Skeleton label="Cargando platos" blocks={3} height="h-64" />
      ) : products.length === 0 ? (
        <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
          Todavía no hay platos disponibles
        </p>
      ) : (
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
          {products.map((product) => (
            <ProductCard
              key={product.id}
              product={product}
              restaurantName={restaurantNames.get(product.restaurantId)}
              onAdd={onAdd}
            />
          ))}
        </div>
      )}
    </section>
  );
}
