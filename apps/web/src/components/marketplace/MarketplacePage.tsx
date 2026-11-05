import { useCallback, useEffect, useMemo, useState } from 'react';

import { catalogApi } from '@/api';
import type { ProductResponse } from '@/api';
import { useCart } from '@/cart';
import { CartPanel } from '@/components/cart';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';

import { CategoryChips } from './CategoryChips';
import { MarketplaceHero } from './MarketplaceHero';
import { MarketplaceSearch } from './MarketplaceSearch';
import { PopularProducts } from './PopularProducts';
import { RestaurantGrid } from './RestaurantGrid';
import { useCatalogResource } from './useCatalogResource';

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
  const [searchTerm, setSearchTerm] = useState('');
  const [cachedAllProducts, setCachedAllProducts] = useState<ProductResponse[]>([]);
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

  useEffect(() => {
    if (selectedCategoryId === null && products.data && products.data.length > 0) {
      setCachedAllProducts(products.data);
    }
  }, [selectedCategoryId, products.data]);

  const filteredRestaurants = useMemo(() => {
    const list = restaurants.data ?? [];
    const term = searchTerm.trim().toLowerCase();
    if (term === '') {
      return list;
    }

    const productsToCheck =
      cachedAllProducts.length > 0 ? cachedAllProducts : (products.data ?? []);

    const matchingRestaurantIds = new Set<number>();
    for (const product of productsToCheck) {
      if (product.available && product.name.toLowerCase().includes(term)) {
        matchingRestaurantIds.add(product.restaurantId);
      }
    }

    return list.filter(
      (restaurant) =>
        restaurant.name.toLowerCase().includes(term) ||
        matchingRestaurantIds.has(restaurant.id),
    );
  }, [restaurants.data, searchTerm, cachedAllProducts, products.data]);

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

  const closedRestaurantIds = useMemo(
    () =>
      new Set(
        (restaurants.data ?? [])
          .filter((restaurant) => restaurant.open === false)
          .map((restaurant) => restaurant.id),
      ),
    [restaurants.data],
  );

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

          <MarketplaceSearch
            value={searchTerm}
            onChange={setSearchTerm}
            onClear={() => setSearchTerm('')}
          />

          <CategoryChips
            categories={categories.data ?? []}
            selectedId={selectedCategoryId}
            onSelect={setSelectedCategoryId}
          />

          {categories.error !== null ? <ApiErrorNotice error={categories.error} /> : null}

          <RestaurantGrid
            restaurants={filteredRestaurants}
            loading={restaurants.loading}
            error={restaurants.error}
            emptyMessage={
              searchTerm.trim() !== ''
                ? 'No encontramos locales que coincidan con tu búsqueda'
                : undefined
            }
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
            closedRestaurantIds={closedRestaurantIds}
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
