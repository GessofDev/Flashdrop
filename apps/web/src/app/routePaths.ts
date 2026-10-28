/**
 * Route paths of the FlashDrop web app.
 *
 * The three roles of the product map to three route groups:
 *  - customer: `/` (marketplace) and `/pedidos`
 *  - store:    `/tienda/*`
 *  - courier:  `/repartidor/*`
 *
 * The shell with the role switcher (task T3) owns the navigation; these
 * constants are the single place where the paths are declared.
 */
export const routePaths = {
  marketplace: '/',
  orders: '/pedidos',
  orderDetail: '/pedidos/:id',
  storeKds: '/tienda/kds',
  storeCatalog: '/tienda/catalogo',
  storeDispatchHistory: '/tienda/despachos',
  storeAnalytics: '/tienda/analiticas',
  courier: '/repartidor',
  courierAvailable: '/repartidor/pedidos',
  courierHistory: '/repartidor/historial',
  profile: '/perfil',
  login: '/login',
} as const;

export type RoutePath = (typeof routePaths)[keyof typeof routePaths];

/**
 * Route the app sends the user to when the session is gone.
 *
 * The API layer redirects here, so the path is declared once next to the
 * other routes instead of being repeated as a literal.
 */
export const LOGIN_PATH = routePaths.login;

/**
 * Route of the tracking of one order.
 *
 * The list links every order to its tracking, so the path is built here
 * instead of being written in the components.
 */
export function orderDetailPath(orderId: string): string {
  return `${routePaths.orders}/${encodeURIComponent(orderId)}`;
}
