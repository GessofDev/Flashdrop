import { ROLES } from '@/session';
import type { Role } from '@/session';

import { ROLE_LABELS } from './shellConfig';

export interface RoleSwitcherProps {
  /** Role the user is working on. */
  activeRole: Role;
  /**
   * Roles the account holds.
   *
   * The options of the other roles stay visible but disabled: the shell
   * shows the three surfaces of the product, and only the ones of the
   * profile can be entered.
   */
  roles: readonly Role[];
  /** Called with the role the user selects. */
  onSelect: (role: Role) => void;
}

/**
 * Segmented control of the three roles — the second zone of the top shell.
 *
 * It never asks for credentials: it changes the role of the session and the
 * route of the app, and nothing else. A role the account does not hold is
 * shown, but disabled: the user sees the three surfaces of the product and
 * enters only the one of its profile.
 */
export function RoleSwitcher({ activeRole, roles, onSelect }: RoleSwitcherProps) {
  return (
    <div
      role="group"
      aria-label="Rol activo"
      className="flex items-center gap-1 rounded-container bg-surface-hover p-1"
    >
      {ROLES.map((role) => {
        const active = role === activeRole;
        const enabled = roles.includes(role);

        return (
          <button
            key={role}
            type="button"
            aria-pressed={active}
            aria-disabled={!enabled}
            disabled={!enabled}
            title={enabled ? undefined : `Tu perfil no tiene el rol de ${ROLE_LABELS[role]}`}
            onClick={() => onSelect(role)}
            className={[
              'rounded-control px-3 py-1.5 text-sm font-semibold transition-colors',
              enabled
                ? active
                  ? 'bg-primary text-white shadow-card'
                  : 'text-foreground-secondary hover:bg-surface hover:text-foreground'
                : 'cursor-not-allowed text-foreground-muted opacity-40',
            ].join(' ')}
          >
            {ROLE_LABELS[role]}
          </button>
        );
      })}
    </div>
  );
}
