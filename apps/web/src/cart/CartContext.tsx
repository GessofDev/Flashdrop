import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import type { ProductResponse } from '@/api';

import { cartItemFromProduct, cartSubtotal } from './types';
import type { CartItem } from './types';

export interface CartContextValue {
  /** Dishes of the cart, in the order they were added. */
  items: CartItem[];
  /** Number of units in the cart. */
  itemCount: number;
  /** Sum of the lines of the cart, in CLP. */
  subtotal: number;
  /** Add a dish to the cart, or add one more unit when it is already there. */
  addItem: (product: ProductResponse) => void;
  /** Change the units of a dish; a quantity of zero removes it. */
  setQuantity: (productId: number, quantity: number) => void;
  /** Add one unit of a dish. */
  increaseItem: (productId: number) => void;
  /** Take one unit off a dish; the dish leaves the cart when it reaches zero. */
  decreaseItem: (productId: number) => void;
  /** Take a dish out of the cart. */
  removeItem: (productId: number) => void;
  /** Empty the cart. */
  clear: () => void;
}

const CartContext = createContext<CartContextValue | null>(null);

export interface CartProviderProps {
  children: ReactNode;
}

/**
 * Cart of the customer.
 *
 * The state lives in the browser: the cart is not an order, and the backend
 * has no endpoint for it. It survives the navigation inside the app and dies
 * with the tab.
 */
export function CartProvider({ children }: CartProviderProps) {
  const [items, setItems] = useState<CartItem[]>([]);

  const addItem = useCallback((product: ProductResponse) => {
    setItems((current) => {
      const existing = current.find((item) => item.productId === product.id);

      if (existing === undefined) {
        return [...current, cartItemFromProduct(product)];
      }

      return current.map((item) =>
        item.productId === product.id ? { ...item, quantity: item.quantity + 1 } : item,
      );
    });
  }, []);

  const setQuantity = useCallback((productId: number, quantity: number) => {
    setItems((current) =>
      quantity <= 0
        ? current.filter((item) => item.productId !== productId)
        : current.map((item) =>
            item.productId === productId ? { ...item, quantity } : item,
          ),
    );
  }, []);

  const increaseItem = useCallback((productId: number) => {
    setItems((current) =>
      current.map((item) =>
        item.productId === productId ? { ...item, quantity: item.quantity + 1 } : item,
      ),
    );
  }, []);

  const decreaseItem = useCallback((productId: number) => {
    setItems((current) =>
      current
        .map((item) =>
          item.productId === productId ? { ...item, quantity: item.quantity - 1 } : item,
        )
        .filter((item) => item.quantity > 0),
    );
  }, []);

  const removeItem = useCallback((productId: number) => {
    setItems((current) => current.filter((item) => item.productId !== productId));
  }, []);

  const clear = useCallback(() => {
    setItems([]);
  }, []);

  const value = useMemo<CartContextValue>(
    () => ({
      items,
      itemCount: items.reduce((count, item) => count + item.quantity, 0),
      subtotal: cartSubtotal(items),
      addItem,
      setQuantity,
      increaseItem,
      decreaseItem,
      removeItem,
      clear,
    }),
    [items, addItem, setQuantity, increaseItem, decreaseItem, removeItem, clear],
  );

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

/** Read the cart. Throws when used outside of a `CartProvider`. */
export function useCart(): CartContextValue {
  const context = useContext(CartContext);

  if (context === null) {
    throw new Error('useCart must be used within a CartProvider');
  }

  return context;
}
