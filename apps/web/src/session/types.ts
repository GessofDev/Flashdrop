/**
 * Roles a FlashDrop user can hold.
 *
 * The values mirror the role names emitted by `auth-service` in the JWT
 * `roles` claim (`Cliente`, `Restaurante`, `Repartidor`). A user may hold
 * more than one role, so membership is always checked against the whole set.
 */
export const ROLES = ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'] as const;

export type Role = (typeof ROLES)[number];

/**
 * Minimal session shape.
 *
 * The identity of the signed in user. The tokens live next to it in the
 * session store (`src/session/tokenStore.ts`), never inside this record.
 */
export interface Session {
  userId: string;
  email: string;
  roles: Role[];
  activeRole: Role;
  /** Set only when the active role owns a store. */
  restaurantId: string | null;
}

/** Narrow an unknown value to a {@link Role}. */
export function isRole(value: unknown): value is Role {
  return typeof value === 'string' && (ROLES as readonly string[]).includes(value);
}
