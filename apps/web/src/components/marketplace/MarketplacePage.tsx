import { useCallback, useEffect, useMemo, useState } from 'react';

import { asApiError, catalogApi } from '@/api';
import type { ApiError, ProductResponse } from '@/api';
import { useCart } from '@/cart';
import { CartPanel } from '@/components/cart';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

import { CategoryChips } from './CategoryChips';
import { MarketplaceHero } from './MarketplaceHero';
import { PopularProducts } from './PopularProducts';
import { RestaurantGrid } from './RestaurantGrid';

interface CatalogResource<T> {
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
function useCatalogResource<T>(loader: () => Promise<T>): CatalogResource<T> {
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

/**
 * Marketplace of the customer (spec S8).
 *
 * The screen is built from the mockup
 * `marketplace_y_cat_logo_de_clientes_flashdrop_v2.0`: the hero, the chips of
 * category, the grid of restaurants, the dishes and the cart of the right
 * column. It shows only what the catalog answers with.
 */
export function MarketplacePage() {
  const [selectedCategoryId, setSelectedCategoryId] = useState<number | null>(null);
  const [notice, setNotice] = useState<string | null>(null);

  const { addItem } = useCart();

  const loadRestaurants = useCallback(() => catalogApi.listRestaurants(), []);
  const loadCategories = useCallback(() => catalogApi.listCategories(), []);
  const loadProducts = useCallback(
    () =>
      catalogApi.listProducts(
        selectedCategoryId === null ? {} : { categoryId: selectedCategoryId },
      ),
    [selectedCategoryId],
  );

  const restaurants = useCatalogResource(loadRestaurants);
  const categories = useCatalogResource(loadCategories);
  const products = useCatalogResource(loadProducts);

  const availableProducts = useMemo(
    () => (products.data ?? []).filter((product) => product.available),
    [products.data],
  );

  const restaurantNames = useMemo(() => {
    const names = new Map<number, string>();

    for (const restaurant of restaurants.data ?? []) {
      names.set(restaurant.id, restaurant.name);
    }

    return names;
  }, [restaurants.data]);

  const handleAdd = (product: ProductResponse): void => {
    addItem(product);
    setNotice(`Añadido: ${product.name}`);
  };

  const selectedCategory = (categories.data ?? []).find(
    (category) => category.id === selectedCategoryId,
  );

  return (
    <div className="mx-auto w-full max-w-app px-4 py-6 md:px-8">
      <div className="grid grid-cols-1 items-start gap-8 lg:grid-cols-12">
        <div className="flex flex-col gap-8 lg:col-span-8">
          <MarketplaceHero
            restaurantCount={restaurants.data?.length ?? 0}
            productCount={availableProducts.length}
            categoryCount={categories.data?.length ?? 0}
          />

          <CategoryChips
            categories={categories.data ?? []}
            selectedId={selectedCategoryId}
            onSelect={setSelectedCategoryId}
          />

          {categories.error !== null ? <ApiErrorNotice error={categories.error} /> : null}

          <RestaurantGrid
            restaurants={restaurants.data ?? []}
            loading={restaurants.loading}
            error={restaurants.error}
          />

          {selectedCategory !== undefined ? (
            <p className="text-sm text-foreground-secondary">
              Mostrando los platos de {selectedCategory.name}.
            </p>
          ) : null}

          <PopularProducts
            products={availableProducts}
            loading={products.loading}
            error={products.error}
            restaurantNames={restaurantNames}
            onAdd={handleAdd}
          />

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
