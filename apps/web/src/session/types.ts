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
 * The authentication flow (login, refresh, logout) belongs to the API client
 * task, so this is intentionally a plain data carrier with no side effects.
 */
export interface Session {
  userId: string;
  email: string;
  roles: Role[];
  activeRole: Role;
  /** Set only when the active role owns a store. */
  restaurantId: string | null;
}
