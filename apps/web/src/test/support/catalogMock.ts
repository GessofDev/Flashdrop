import { jsonResponse } from './fetchMock';
import type { RecordedCall } from './fetchMock';

/**
 * Catalog answers used by the marketplace and the cart tests.
 *
 * The shapes are the ones of `catalog-service`: the DTO is bare, `image` may
 * be `null` and the price is a plain number.
 */

export const RESTAURANTS = [
  {
    id: 1,
    name: 'La Maestranza Burgers',
    address: 'Av. Providencia 1234, Providencia, Santiago',
    phone: null,
    image: null,
  },
  {
    id: 2,
    name: 'Sakura Nikkei Fusion',
    address: 'Tobalaba 4321, Las Condes, Santiago',
    phone: null,
    image: 'https://cdn.flashdrop.cl/sakura.jpg',
  },
];

export const CATEGORIES = [
  { id: 3, name: 'Hamburguesas', description: 'Smash burgers artesanales', image: null },
  { id: 4, name: 'Pizzas', description: 'Horno de leña', image: null },
];

export const PRODUCTS = [
  {
    id: 10,
    categoryId: 3,
    restaurantId: 1,
    name: 'Doble Smash Cheddar Bacon',
    description: 'Dos hamburguesas de 110g con cheddar fundido y tocino crujiente.',
    price: 7990,
    image: null,
    available: true,
  },
  {
    id: 11,
    categoryId: 4,
    restaurantId: 2,
    name: 'Pizza Napolitana Da Luigi',
    description: 'Masa de fermentación lenta con tomate San Marzano y albahaca.',
    price: 12990,
    image: 'https://cdn.flashdrop.cl/pizza.jpg',
    available: true,
  },
  {
    id: 12,
    categoryId: 3,
    restaurantId: 1,
    name: 'Papas Rústicas Trufadas',
    description: 'Papas cortadas a mano con piel, trufa blanca y parmesano.',
    price: 3490,
    image: null,
    available: true,
  },
  {
    id: 13,
    categoryId: 3,
    restaurantId: 1,
    name: 'Hamburguesa Clásica',
    description: 'Plato que el local tiene agotado.',
    price: 5990,
    image: null,
    available: false,
  },
];

/**
 * Answer the catalog endpoints of `catalog-service`.
 *
 * The products endpoint applies the filter of the category, exactly as the
 * backend does with the `categoryId` of the query string.
 */
export function catalogResponse(call: RecordedCall): Response {
  const { pathname, searchParams } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/catalog/restaurants') {
    return jsonResponse(RESTAURANTS);
  }

  if (pathname === '/catalog/categories') {
    return jsonResponse(CATEGORIES);
  }

  if (pathname === '/catalog/products') {
    const categoryId = searchParams.get('categoryId');
    const products =
      categoryId === null
        ? PRODUCTS
        : PRODUCTS.filter((product) => String(product.categoryId) === categoryId);

    return jsonResponse(products);
  }

  return jsonResponse({ status: 404, error: 'NOT_FOUND', message: 'Recurso no encontrado' }, 404);
}
