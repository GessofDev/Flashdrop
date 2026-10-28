import type { ProductResponse } from '@/api';
import { Button } from '@/components/ui';
import { formatClp } from '@/lib/format';

import { CatalogImage } from './CatalogImage';

export interface ProductCardProps {
  /** Dish the catalog answered with. */
  product: ProductResponse;
  /** Name of the restaurant of the dish, when the catalog knows it. */
  restaurantName: string | undefined;
  /** Add the dish to the cart. */
  onAdd: (product: ProductResponse) => void;
}

/**
 * Card of a dish.
 *
 * The catalog sends the name, the description, the price and the picture, so
 * the card shows exactly those. The price is in Chilean pesos, with the
 * tabular figures the design system asks for.
 */
export function ProductCard({ product, restaurantName, onAdd }: ProductCardProps) {
  return (
    <article className="flex flex-col justify-between gap-3 rounded-container border border-border bg-surface p-4 shadow-card">
      <div className="flex flex-col gap-3">
        <div className="aspect-video w-full overflow-hidden rounded-control">
          <CatalogImage src={product.image} alt={product.name} />
        </div>
        <div className="flex flex-col gap-1">
          {restaurantName !== undefined ? (
            <span className="text-xs font-medium text-foreground-muted">{restaurantName}</span>
          ) : null}
          <h3 className="font-display text-base font-bold text-foreground">{product.name}</h3>
          {product.description.length > 0 ? (
            <p className="text-sm text-foreground-secondary">{product.description}</p>
          ) : null}
        </div>
      </div>
      <div className="flex items-center justify-between gap-3 pt-2">
        <span className="nums-tabular font-display text-lg font-bold text-foreground">
          {formatClp(product.price)}
        </span>
        <Button
          size="sm"
          aria-label={`Añadir ${product.name} al carrito`}
          onClick={() => onAdd(product)}
        >
          Añadir
        </Button>
      </div>
    </article>
  );
}
