import type { ReactNode } from 'react';

import type { KitchenColumnDefinition } from '@/lib/kitchenStatus';

export interface KitchenColumnProps {
  /** Column of the board. */
  column: KitchenColumnDefinition;
  /** Number of orders the column holds. */
  count: number;
  /** Cards of the column. */
  children: ReactNode;
}

/**
 * One column of the board of the kitchen.
 *
 * The board has three columns because the restaurant only sets two statuses:
 * the orders it has not accepted yet, the ones it is preparing and the ones
 * that wait for the courier.
 */
export function KitchenColumn({ column, count, children }: KitchenColumnProps) {
  return (
    <section
      aria-label={`Columna ${column.title}`}
      className="flex flex-col gap-3 rounded-container bg-canvas p-3"
    >
      <header className="flex items-center justify-between gap-2 rounded-control bg-surface px-3 py-2 shadow-card">
        <h2 className="label-code font-display text-sm font-bold text-foreground">
          {column.title}
        </h2>
        <span className="nums-tabular label-code text-xs font-bold text-foreground-secondary">
          {count}
        </span>
      </header>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  );
}
