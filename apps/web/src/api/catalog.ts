import { asList } from './asList';
import { request } from './client';
import type { CategoryResponse, ProductResponse, RestaurantResponse } from './types';

/**
 * Filters of the product list.
 *
 * The backend accepts both of them and answers only the products that are
 * available.
 */
export interface ProductQuery {
  categoryId?: number;
  restaurantId?: number;
}

/**
 * Calls of `catalog-service`.
 *
 * The service answers with the bare DTO — no `{ success, message, data }`
 * envelope — so every call declares `responseShape: 'bare'`. The catalog is
 * publicly navigable (see the routes of the gateway), so the calls do not
 * attach the session.
 */
export const catalogApi = {
  /** `GET /catalog/restaurants` — the restaurants of the catalog. */
  listRestaurants(): Promise<RestaurantResponse[]> {
    return request<unknown>({
      path: '/catalog/restaurants',
      responseShape: 'bare',
      session: 'public',
    }).then((answer) => asList<RestaurantResponse>(answer));
  },

  /** `GET /catalog/categories` — the categories of the catalog. */
  listCategories(): Promise<CategoryResponse[]> {
    return request<unknown>({
      path: '/catalog/categories',
      responseShape: 'bare',
      session: 'public',
    }).then((answer) => asList<CategoryResponse>(answer));
  },

  /** `GET /catalog/products` — the available products, filtered by the query. */
  listProducts(query: ProductQuery = {}): Promise<ProductResponse[]> {
    return request<unknown>({
      path: '/catalog/products',
      responseShape: 'bare',
      session: 'public',
      query: { categoryId: query.categoryId, restaurantId: query.restaurantId },
    }).then((answer) => asList<ProductResponse>(answer));
  },
};
