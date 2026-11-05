import { useCallback, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';

import { catalogApi } from '@/api';
import type { ProductResponse } from '@/api';
import { routePaths } from '@/app/routePaths';
import { useCart } from '@/cart';
import { CartPanel } from '@/components/cart';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { Skeleton } from '@/components/ui';

import { CatalogImage } from './CatalogImage';
import { ProductCard } from './ProductCard';
import { useCatalogResource } from './useCatalogResource';

/**
 * Page of one restaurant (spec S8).
 *
 * The customer picks a local from the marketplace and lands here to see its
 * dishes and add them to the cart. Everything it shows comes from the
 * catalog: the local (`GET /catalog/restaurants`) and its dishes
 * (`GET /catalog/products?restaurantId=`).
 */
export function RestaurantPage() {
  const { id } = useParams<{ id: string }>();
  const restaurantId = Number(id);
  const knownId = Number.isInteger(restaurantId) && restaurantId > 0;

  const loadRestaurants = useCallback(() => catalogApi.listRestaurants(), []);
  const loadProducts = useCallback(
    () => (knownId ? catalogApi.listProducts({ restaurantId }) : Promise.resolve([])),
    [knownId, restaurantId],
  );

  const restaurants = useCatalogResource(loadRestaurants);
  const products = useCatalogResource(loadProducts);

  const restaurant = useMemo(
    () => (restaurants.data ?? []).find((item) => item.id === restaurantId) ?? null,
    [restaurants.data, restaurantId],
  );

  const restaurantNames = useMemo(() => {
    const names = new Map<number, string>();

    for (const item of restaurants.data ?? []) {
      names.set(item.id, item.name);
    }

    return names;
  }, [restaurants.data]);

  const { addItem } = useCart();
  const [notice, setNotice] = useState<string | null>(null);

  const handleAdd = (product: ProductResponse): void => {
    addItem(product);
    setNotice(`Añadido: ${product.name}`);
  };

  const availableProducts = (products.data ?? []).filter((product) => product.available);
  const closed = restaurant?.open === false;

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
        <div className="flex flex-col gap-6 lg:col-span-8">
          <Link
            to={routePaths.marketplace}
            className="w-fit rounded-control text-sm font-medium text-foreground-secondary transition-colors hover:text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
          >
            ← Volver al marketplace
          </Link>

          {restaurants.error !== null ? <ApiErrorNotice error={restaurants.error} /> : null}

          {restaurants.loading ? (
            <Skeleton label="Cargando local" blocks={1} height="h-40" />
          ) : restaurant === null ? (
            <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
              No encontramos este local
            </p>
          ) : (
            <header className="flex flex-col gap-4 rounded-container border border-border bg-surface p-5 shadow-card sm:flex-row sm:items-center">
              <div className="aspect-video w-full overflow-hidden rounded-control sm:w-56">
                <CatalogImage src={restaurant.image} alt={restaurant.name} />
              </div>
              <div className="flex flex-col gap-1">
                <h1 className="font-display text-2xl font-bold tracking-tight text-foreground">
                  {restaurant.name}
                </h1>
                <p className="text-sm text-foreground-secondary">{restaurant.address}</p>
              </div>
            </header>
          )}

          {closed ? (
            <p className="rounded-container border border-border bg-surface p-4 text-sm text-foreground">
              Este local está cerrado. Puedes ver sus platos, pero no recibe pedidos en este momento.
            </p>
          ) : null}

          <section
            aria-labelledby="restaurant-products-title"
            className="flex flex-col gap-4"
          >
            <h2
              id="restaurant-products-title"
              className="font-display text-xl font-bold tracking-tight text-foreground"
            >
              Platos del local
            </h2>

            {products.error !== null ? (
              <ApiErrorNotice error={products.error} />
            ) : products.loading ? (
              <Skeleton label="Cargando platos" blocks={3} height="h-64" />
            ) : availableProducts.length === 0 ? (
              <p className="rounded-container border border-dashed border-border bg-surface p-6 text-center text-sm text-foreground-secondary">
                Este local no tiene platos disponibles
              </p>
            ) : (
              <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {availableProducts.map((product) => (
                  <ProductCard
                    key={product.id}
                    product={product}
                    closed={closed}
                    onAdd={handleAdd}
                  />
                ))}
              </div>
            )}
          </section>

          {notice !== null ? (
            <p role="status" className="text-sm font-medium text-success">
              {notice}
            </p>
          ) : null}
        </div>

        <div className="lg:col-span-4 lg:sticky lg:top-24">
          <CartPanel restaurantNames={restaurantNames} />
        </div>
      </div>
    </div>
  );
}
