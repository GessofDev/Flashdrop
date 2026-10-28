import { ROLES } from '@/session';
import type { Role } from '@/session';

import { ROLE_LABELS } from './shellConfig';

export interface RoleSwitcherProps {
  /** Role the user is working on. */
  activeRole: Role;
  /** Called with the role the user selects. */
  onSelect: (role: Role) => void;
}

/**
 * Segmented control of the three roles — the second zone of the top shell.
 *
 * It never asks for credentials: it changes the role of the session and the
 * route of the app, and nothing else.
 */
export function RoleSwitcher({ activeRole, onSelect }: RoleSwitcherProps) {
  return (
    <div
      role="group"
      aria-label="Rol activo"
      className="flex items-center gap-1 rounded-container bg-surface-hover p-1"
    >
      {ROLES.map((role) => {
        const active = role === activeRole;

        return (
          <button
            key={role}
            type="button"
            aria-pressed={active}
            onClick={() => onSelect(role)}
            className={[
              'rounded-control px-3 py-1.5 text-sm font-semibold transition-colors',
              active
                ? 'bg-primary text-white shadow-card'
                : 'text-foreground-secondary hover:bg-surface hover:text-foreground',
            ].join(' ')}
          >
            {ROLE_LABELS[role]}
          </button>
        );
      })}
    </div>
  );
}
