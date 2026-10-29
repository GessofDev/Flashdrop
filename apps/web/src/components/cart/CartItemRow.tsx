import type { CartItem } from '@/cart';
import { formatClp } from '@/lib/format';

export interface CartItemRowProps {
  /** Dish the customer added to the cart. */
  item: CartItem;
  /** Add one unit of the dish. */
  onIncrease: () => void;
  /** Take one unit off the dish. */
  onDecrease: () => void;
  /** Take the dish out of the cart. */
  onRemove: () => void;
}

const CONTROL_CLASSES =
  'flex h-7 w-7 items-center justify-center rounded-control bg-surface text-sm font-bold text-foreground transition-colors hover:bg-surface-hover';

/**
 * Line of the cart.
 *
 * It shows the dish, its unit price and the units the customer wants. The
 * total of the line is not repeated here: the panel sums the cart and the
 * backend charges its own total.
 */
export function CartItemRow({ item, onIncrease, onDecrease, onRemove }: CartItemRowProps) {
  return (
    <li className="flex items-start justify-between gap-3 rounded-control p-2">
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-sm font-semibold text-foreground">{item.name}</span>
        <span
          className="nums-tabular text-sm font-semibold text-foreground"
          title="Precio unitario"
        >
          {formatClp(item.price)} c/u
        </span>
      </div>

      <div
        role="group"
        aria-label={`Cantidad de ${item.name}`}
        className="flex shrink-0 items-center gap-1 rounded-control bg-surface-hover p-1"
      >
        <button
          type="button"
          aria-label={`Disminuir cantidad de ${item.name}`}
          className={CONTROL_CLASSES}
          onClick={onDecrease}
        >
          −
        </button>
        <span className="nums-tabular w-6 text-center text-sm font-bold text-foreground">
          {item.quantity}
        </span>
        <button
          type="button"
          aria-label={`Aumentar cantidad de ${item.name}`}
          className={CONTROL_CLASSES}
          onClick={onIncrease}
        >
          +
        </button>
      </div>

      <button
        type="button"
        aria-label={`Quitar ${item.name} del carrito`}
        className="shrink-0 text-xs font-semibold text-foreground-muted transition-colors hover:text-destructive"
        onClick={onRemove}
      >
        Quitar
      </button>
    </li>
  );
}
