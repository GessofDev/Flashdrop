import type { ProductResponse } from '@/api';

/**
 * Item of the cart.
 *
 * The cart keeps a copy of the dish it holds: the name, the price and the
 * restaurant come from the catalog, and the cart never asks the backend
 * again for them. The price of the line is only a preview — the total the
 * customer pays is the one the backend computes.
 */
export interface CartItem {
  /** Id of the dish in the catalog. */
  productId: number;
  name: string;
  /** Unit price in CLP, as the catalog sent it. */
  price: number;
  quantity: number;
  /** Restaurant the dish belongs to; the backend rejects an order that mixes them. */
  restaurantId: number;
  image: string | null;
}

/** Build the item of a dish, with its first unit. */
export function cartItemFromProduct(product: ProductResponse): CartItem {
  return {
    productId: product.id,
    name: product.name,
    price: product.price,
    quantity: 1,
    restaurantId: product.restaurantId,
    image: product.image,
  };
}

/** Sum of the lines of the cart, in CLP. */
export function cartSubtotal(items: readonly CartItem[]): number {
  return items.reduce((total, item) => total + item.price * item.quantity, 0);
}
