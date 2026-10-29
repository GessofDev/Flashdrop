import { NavLink } from 'react-router-dom';

import { ROLE_NAVIGATION } from '@/app/roleRoutes';
import { routePaths } from '@/app/routePaths';
import type { Role } from '@/session';

import { ROLE_LABELS } from './shellConfig';

export interface RoleNavigationProps {
  /** Role the user is working on. */
  role: Role;
}

/**
 * Secondary navigation of the shell.
 *
 * It shows the entries of the active role only: the mockup declares one set
 * per surface (comensal, store and courier).
 */
export function RoleNavigation({ role }: RoleNavigationProps) {
  return (
    <nav
      aria-label={`Navegación de ${ROLE_LABELS[role]}`}
      className="border-b border-border bg-surface"
    >
      <div className="mx-auto flex w-full max-w-app items-center gap-6 overflow-x-auto px-4 md:px-8">
        {ROLE_NAVIGATION[role].map((item) => (
          <NavLink
            key={item.path}
            to={item.path}
            end={item.path === routePaths.marketplace}
            className={({ isActive }) =>
              [
                'border-b-2 py-3 text-sm font-medium whitespace-nowrap transition-colors',
                isActive
                  ? 'border-primary font-bold text-primary'
                  : 'border-transparent text-foreground-secondary hover:text-foreground',
              ].join(' ')
            }
          >
            {item.label}
          </NavLink>
        ))}
      </div>
    </nav>
  );
}
