import type { Role } from '@/session';

/** Order labels the courier is allowed to set. */
export type CourierOrderTransition = 'Retirado' | 'Entregado';

function routeStatusKey(status: string): string {
  return status.trim().toUpperCase().replace(/\s+/g, '_');
}

/**
 * Next order transition allowed from the status of a route.
 *
 * `orders-service` synchronizes the route when the order changes:
 * `Listo para retiro` becomes `RETIRAR_PEDIDO`, `Retirado` becomes
 * `EN_CAMINO`, and `Entregado` becomes `ENTREGADO`. The route status is
 * therefore the only state the routes endpoint needs to expose the next
 * action of the courier.
 */
export function nextCourierTransition(
  routeStatus: string,
  role: Role,
): CourierOrderTransition | null {
  if (role !== 'REPARTIDOR') {
    return null;
  }

  switch (routeStatusKey(routeStatus)) {
    case 'ASSIGNED':
    case 'RETIRAR_PEDIDO':
    case 'LISTO_PARA_RETIRO':
      return 'Retirado';
    case 'EN_CAMINO':
      return 'Entregado';
    default:
      return null;
  }
}

/** Whether a route belongs to the completed-delivery history. */
export function isCompletedCourierRoute(routeStatus: string): boolean {
  return routeStatusKey(routeStatus) === 'ENTREGADO';
}
