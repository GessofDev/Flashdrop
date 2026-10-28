/**
 * The status of the route of a delivery (spec S6).
 *
 * `delivery-service` owns the `RouteStatus` of a route: `PENDIENTE`,
 * `ASSIGNED`, `RETIRAR_PEDIDO`, `EN_CAMINO` and `ENTREGADO`. The route is the
 * one that says whether the order is on its way, because the order status
 * `En camino` is legacy and no role can set it anymore.
 *
 * The backend writes the status with the two spellings of the enum — the name
 * (`EN_CAMINO`) and the label of the database (`En camino`) — so the reader
 * accepts both and answers with the label of the enum.
 */

/** Label of each status of the route. */
const ROUTE_STATUS_LABELS: Readonly<Record<string, string>> = {
  PENDIENTE: 'Pendiente',
  ASSIGNED: 'Asignado',
  RETIRAR_PEDIDO: 'Listo para retiro',
  LISTO_PARA_RETIRO: 'Listo para retiro',
  EN_CAMINO: 'En camino',
  ENTREGADO: 'Entregado',
};

/** Key of a status, whatever the spelling the backend used. */
function routeStatusKey(status: string): string {
  return status.trim().toUpperCase().replace(/\s+/g, '_');
}

/**
 * Label of the status of the route.
 *
 * A status nobody knows is shown as it arrives: the app does not replace what
 * the backend sent with a label of its own.
 */
export function routeStatusLabel(status: string): string {
  const key = routeStatusKey(status);

  return ROUTE_STATUS_LABELS[key] ?? status.trim();
}

/**
 * Whether the route is on its way to the address of the customer.
 *
 * It is the only source of the phase of the transit: the app never derives it
 * from the status of the order.
 */
export function isRouteOnTheWay(status: string): boolean {
  return routeStatusKey(status) === 'EN_CAMINO';
}
