import type { ProductResponse } from '@/api';

export interface AvailabilitySwitchProps {
  /** Product the switch belongs to. */
  product: ProductResponse;
  /** Whether a change of this product is in flight. */
  pending: boolean;
  /** Ask for the change of the availability. */
  onToggle: (product: ProductResponse) => void;
}

/**
 * Switch of the availability of a product.
 *
 * The switch shows the state the backend confirmed: it is never moved by the
 * click itself, so a rejected change leaves the product as it was. The
 * accessible name names the product, because a table shows one switch per
 * row.
 */
export function AvailabilitySwitch({ product, pending, onToggle }: AvailabilitySwitchProps) {
  return (
    <label className="inline-flex items-center">
      <input
        type="checkbox"
        className="peer sr-only"
        aria-label={`Disponibilidad de ${product.name}`}
        checked={product.available}
        disabled={pending}
        onChange={() => onToggle(product)}
      />
      <span
        aria-hidden="true"
        className="relative h-6 w-11 cursor-pointer rounded-full bg-border-strong transition-colors peer-checked:bg-success peer-focus-visible:outline-2 peer-focus-visible:outline-primary peer-focus-visible:outline-offset-2 peer-disabled:cursor-not-allowed peer-disabled:opacity-50 after:absolute after:left-0.5 after:top-0.5 after:h-5 after:w-5 after:rounded-full after:bg-surface after:shadow-card after:transition-transform peer-checked:after:translate-x-5"
      />
    </label>
  );
}
