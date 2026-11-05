import { ROLES } from '@/session';
import type { Role } from '@/session';

import { routePaths } from './routePaths';

/**
 * Navigation of the shell, by role (spec S5).
 *
 * The canonical shell of the mockup
 * `flashdrop_shell_can_nica_gu_a_de_navegaci_n_por_rol` declares one set of
 * entries per role; this module is the single place where they live, so the
 * switcher, the secondary navigation and the guard of role always agree.
 */
export interface NavigationItem {
  /** Copy of the entry, in Chilean Spanish. */
  label: string;
  /** Route the entry opens. */
  path: string;
}

/** Route each role lands on. */
export const ROLE_HOME: Record<Role, string> = {
  CLIENTE: routePaths.marketplace,
  RESTAURANTE: routePaths.storeKds,
  REPARTIDOR: routePaths.courier,
};

/** Entries of the secondary navigation, by role. */
export const ROLE_NAVIGATION: Record<Role, readonly NavigationItem[]> = {
  CLIENTE: [
    { label: 'Marketplace & Consumo', path: routePaths.marketplace },
    { label: 'Mis Pedidos', path: routePaths.orders },
  ],
  RESTAURANTE: [
    { label: 'KDS Pantalla Cocina', path: routePaths.storeKds },
    { label: 'Catálogo & Menú', path: routePaths.storeCatalog },
    { label: 'Horario & Apertura', path: routePaths.storeHours },
    { label: 'Historial Despachos', path: routePaths.storeDispatchHistory },
    { label: 'Analíticas & Ventas', path: routePaths.storeAnalytics },
  ],
  REPARTIDOR: [
    { label: 'Ruta Activa', path: routePaths.courier },
    { label: 'Pedidos Disponibles (FIFO)', path: routePaths.courierAvailable },
    { label: 'Historial de Entregas', path: routePaths.courierHistory },
  ],
};

/**
 * Paths that continue a screen of a role without having an entry of their
 * own in the navigation.
 *
 * The page of a restaurant is the continuation of the marketplace: it is
 * where the customer picks the dishes of the local they chose.
 */
const ROLE_CONTINUATIONS: Record<Role, readonly string[]> = {
  CLIENTE: ['/restaurante/'],
  RESTAURANTE: [],
  REPARTIDOR: [],
};

/**
 * Role that owns a path, or `null` when the path is shared by every role
 * (the profile) or does not exist.
 *
 * The navigation is the source of truth: a path that belongs to a role is a
 * path of its navigation. A path that continues one of them belongs to the
 * same role — the tracking of an order (`/pedidos/:id`) is a screen of the
 * customer, like the list it comes from.
 */
export function roleOwningPath(pathname: string): Role | null {
  for (const role of ROLES) {
    const owns =
      ROLE_NAVIGATION[role].some(
        (item) => pathname === item.path || pathname.startsWith(`${item.path}/`),
      ) || ROLE_CONTINUATIONS[role].some((prefix) => pathname.startsWith(prefix));

    if (owns) {
      return role;
    }
  }

  return null;
}
