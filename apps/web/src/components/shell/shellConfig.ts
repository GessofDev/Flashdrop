import type { Role } from '@/session';

/** Copy of the three roles, in Chilean Spanish. */
export const ROLE_LABELS: Record<Role, string> = {
  CLIENTE: 'Cliente',
  RESTAURANTE: 'Mi Tienda',
  REPARTIDOR: 'Repartidor',
};

/**
 * Territorial context of the active role — the third zone of the top shell.
 *
 * The values come from the mockup of the canonical shell; they are declared
 * here, in one place, so the canonical fixture of the feature (task T13) can
 * replace them without touching the components.
 */
export interface ShellContext {
  /** Local or sector the user is working on. */
  local: string;
  /** Provider of the service in that context. */
  provider: string;
}

export const SHELL_CONTEXT: Record<Role, ShellContext> = {
  CLIENTE: { local: 'Providencia • Santiago', provider: 'FlashDrop Chile' },
  RESTAURANTE: { local: 'Local 04 - Providencia', provider: 'La Maestranza - Providencia' },
  REPARTIDOR: { local: 'Sector Oriente • RM', provider: 'Flota FlashDrop' },
};

/**
 * Line status of the top shell — the fourth zone.
 *
 * It states the state of the surface, never a number that the backend does
 * not send (no queue counters, no latency, no battery).
 */
export const ROLE_TELEMETRY: Record<Role, string> = {
  CLIENTE: 'En Línea • Sesión Activa',
  RESTAURANTE: 'En Línea • KDS Activo',
  REPARTIDOR: 'En Servicio • Activo (Moto)',
};
