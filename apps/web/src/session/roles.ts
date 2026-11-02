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

/**
 * Role the session is working on, restricted to the roles of the account.
 *
 * A session can only be in a role the user holds: a session stored by an
 * older build, or an account whose roles changed, must not leave the app in
 * a role the backend rejects.
 *
 * @param roles Roles the account holds.
 * @param activeRole Role the session claims to be working on.
 * @return The role to work on: the claimed one when the account holds it,
 *         the first role of the account otherwise.
 */
export function resolveActiveRole(roles: readonly Role[], activeRole: Role): Role {
  if (roles.includes(activeRole)) {
    return activeRole;
  }

  return roles[0] ?? 'CLIENTE';
}
