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
 *
 * The column carries the color of its status — the band of the header, the
 * dot and the title — so the three sections of the board are told apart at a
 * glance instead of reading three identical white boxes.
 */
export function KitchenColumn({ column, count, children }: KitchenColumnProps) {
  return (
    <section
      aria-label={`Columna ${column.title}`}
      className={`flex flex-col gap-3 rounded-container p-3 ${column.accent.body}`}
    >
      <header
        className={`flex items-center justify-between gap-2 rounded-control border-2 px-3 py-2 shadow-card ${column.accent.header}`}
      >
        <div className="flex items-center gap-2">
          <span aria-hidden="true" className={`h-2.5 w-2.5 rounded-full ${column.accent.dot}`} />
          <h2 className={`label-code font-display text-sm font-bold ${column.accent.text}`}>
            {column.title}
          </h2>
        </div>
        <span className={`nums-tabular label-code text-xs font-bold ${column.accent.text}`}>
          {count}
        </span>
      </header>
      <div className="flex flex-col gap-3">{children}</div>
    </section>
  );
}
