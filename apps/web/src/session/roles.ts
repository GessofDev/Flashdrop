import { ROLES } from './types';
import type { Role } from './types';

/**
 * Role names as the backend spells them.
 *
 * `auth-service` puts the enum name in the JWT (`CLIENTE`, `RESTAURANTE`,
 * `REPARTIDOR`), but the label of the role (`Cliente`, `Restaurante`,
 * `Repartidor`) is what the seed data and the API documentation use. The
 * normalizer accepts both so the client does not depend on the spelling.
 */
const ROLE_BY_LABEL: Readonly<Record<string, Role>> = {
  cliente: 'CLIENTE',
  restaurante: 'RESTAURANTE',
  repartidor: 'REPARTIDOR',
};

/** Translate one role name of the backend into a {@link Role}. */
export function toRole(value: string): Role | null {
  return ROLE_BY_LABEL[value.trim().toLowerCase()] ?? null;
}

/**
 * Translate the role names of the backend into the roles of the app.
 *
 * Unknown names are dropped — the app never invents a role — and the result
 * keeps the canonical order of `ROLES`.
 */
export function toRoles(values: readonly string[]): Role[] {
  const known = new Set<Role>();

  for (const value of values) {
    const role = toRole(value);
    if (role !== null) {
      known.add(role);
    }
  }

  return ROLES.filter((role) => known.has(role));
}
