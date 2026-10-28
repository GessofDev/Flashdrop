import type { Role } from '@/session';

/**
 * The board of the kitchen (spec S12).
 *
 * `orders-service` lets the restaurant set only two statuses of the order
 * machine — `Preparando` and `Listo para retiro`
 * (`RoleTransitionPolicy`) — so the board has exactly three columns: the
 * orders it has not accepted yet, the ones it is preparing and the ones that
 * wait for the courier.
 *
 * The labels are the ones of the backend: `OrderStatus.fromValue` compares
 * them case-insensitively and the PUT of the status sends them as they are.
 */

/** The three statuses the kitchen owns, one per column. */
export const KITCHEN_STATUSES = ['Nuevo pedido', 'Preparando', 'Listo para retiro'] as const;

/** One status of the kitchen board. */
export type KitchenStatus = (typeof KITCHEN_STATUSES)[number];

/** The three columns of the board. */
export interface KitchenColumnDefinition {
  /** Stable key of the column. */
  key: 'pending' | 'preparing' | 'ready';
  /** Title of the column, as the mockup writes it. */
  title: string;
  /** Status of the backend the column holds. */
  status: KitchenStatus;
}

/** The columns of the board, in the order the kitchen reads them. */
export const KITCHEN_COLUMNS: readonly KitchenColumnDefinition[] = [
  { key: 'pending', title: 'POR ACEPTAR', status: 'Nuevo pedido' },
  { key: 'preparing', title: 'EN COCINA', status: 'Preparando' },
  { key: 'ready', title: 'LISTO / RETIRO', status: 'Listo para retiro' },
];

/** The milestones of an order inside the kitchen, in order. */
export const KITCHEN_MILESTONES = ['Pedido recibido', 'En cocina', 'Listo para retiro'] as const;

/**
 * Whether the status is one of the statuses the kitchen owns.
 *
 * The board hides the orders that already left the kitchen — the ones the
 * courier carries or delivered — because no column of the board can hold them.
 */
export function isKitchenStatus(status: string): boolean {
  const needle = status.trim().toLowerCase();

  return KITCHEN_STATUSES.some((candidate) => candidate.toLowerCase() === needle);
}

/**
 * Whether the status of the order is the one the column holds.
 *
 * The comparison ignores the case and the spaces, like
 * `OrderStatus.fromValue` does in the backend.
 */
export function matchesKitchenColumn(status: string, columnStatus: KitchenStatus): boolean {
  return status.trim().toLowerCase() === columnStatus.toLowerCase();
}

/**
 * The status the kitchen sets next, for the status the order carries.
 *
 * The restaurant may set `Preparando` and `Listo para retiro`; no other role
 * may set a status of the kitchen, so a customer never sees a status action.
 * An order that is already `Listo para retiro` waits for the courier and
 * offers nothing.
 */
export function nextKitchenTransition(status: string, role: Role): KitchenStatus | null {
  if (role !== 'RESTAURANTE') {
    return null;
  }

  switch (status.trim().toLowerCase()) {
    case 'nuevo pedido':
      return 'Preparando';
    case 'preparando':
      return 'Listo para retiro';
    default:
      return null;
  }
}

/**
 * How many milestones of the kitchen the order reached.
 *
 * The milestones come from the status the backend sent; the instant of the
 * reception is the only one the backend sends, so the app never invents the
 * hour of a milestone it cannot read.
 *
 * Only the three statuses of the board count: an order that already left the
 * kitchen never reaches the board, and the legacy in-transit status of the
 * backend is not a state of the board.
 */
export function completedKitchenMilestones(status: string): number {
  switch (status.trim().toLowerCase()) {
    case 'nuevo pedido':
      return 1;
    case 'preparando':
      return 2;
    case 'listo para retiro':
      return 3;
    default:
      return 0;
  }
}
