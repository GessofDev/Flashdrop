/**
 * Route paths of the FlashDrop web app.
 *
 * The three roles of the product map to three route groups:
 *  - customer: `/` (marketplace) and `/pedidos`
 *  - store:    `/tienda/*`
 *  - courier:  `/repartidor`
 *
 * The shell with the role switcher (task T3) will own the navigation; these
 * constants are the single place where the paths are declared.
 */
export const routePaths = {
  marketplace: '/',
  orders: '/pedidos',
  storeKds: '/tienda/kds',
  storeCatalog: '/tienda/catalogo',
  storeAnalytics: '/tienda/analiticas',
  courier: '/repartidor',
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
