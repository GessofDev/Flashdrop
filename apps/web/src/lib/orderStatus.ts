/**
 * The order machine, as the customer sees it (spec S6).
 *
 * `orders-service` owns the statuses of an order and the transitions between
 * them. The app does not reimplement them: it only reads the status the
 * backend sends and shows how far the order got.
 *
 * The statuses of the backend are, in order:
 * `Nuevo pedido`, `Preparando`, `Listo para retiro`, `Retirado`, `En camino`
 * and `Entregado`. `En camino` is legacy — no role can set it anymore — so it
 * is **not** a step of the tracking: the phase of the transit comes from the
 * status of the route (see `src/lib/routeStatus.ts`).
 */

/**
 * The five steps of the tracking of an order.
 *
 * They are the states the customer follows, in order, from the reception of
 * the order to its delivery.
 */
export const ORDER_STEPS = [
  'Confirmado',
  'En cocina',
  'Listo para retiro',
  'Retirado',
  'Entregado',
] as const;

/** One step of the tracking. */
export type OrderStep = (typeof ORDER_STEPS)[number];

/**
 * Steps already done, by status of the backend.
 *
 * The count is the number of steps the order completed, so the stepper shows
 * the milestone reached and the one that comes next. A legacy `En camino` has
 * the same progress as `Retirado`: the order is already on its way.
 */
const COMPLETED_STEPS_BY_STATUS: Readonly<Record<string, number>> = {
  'nuevo pedido': 1,
  preparando: 2,
  'listo para retiro': 3,
  retirado: 4,
  'en camino': 4,
  entregado: 5,
};

/**
 * How many steps of the tracking the order completed.
 *
 * An unknown status completes no step: the app never guesses the progress of
 * an order it cannot read.
 */
export function completedStepsForStatus(status: string): number {
  return COMPLETED_STEPS_BY_STATUS[status.trim().toLowerCase()] ?? 0;
}

/**
 * Whether the status is one of the statuses of the backend.
 *
 * The screens use it to tell a known milestone from a value nobody can
 * explain.
 */
export function isKnownOrderStatus(status: string): boolean {
  return status.trim().toLowerCase() in COMPLETED_STEPS_BY_STATUS;
}
