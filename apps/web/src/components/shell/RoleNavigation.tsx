import { NavLink } from 'react-router-dom';

import { ROLE_NAVIGATION } from '@/app/roleRoutes';
import { routePaths } from '@/app/routePaths';
import { useStoreContext } from '@/components/store';
import { formatRestaurantName } from '@/lib/format';
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
 * per surface (comensal, store and courier). When in store mode and operable
 * stores exist, it also renders the active store selector (`Marca — Sucursal`).
 */
export function RoleNavigation({ role }: RoleNavigationProps) {
  const { restaurants, selectedStoreId, selectStore } = useStoreContext();

  return (
    <nav
      aria-label={`Navegación de ${ROLE_LABELS[role]}`}
      className="border-b border-border bg-surface"
    >
      <div className="mx-auto flex w-full max-w-app items-center justify-between gap-6 overflow-x-auto px-4 md:px-8">
        <div className="flex items-center gap-6">
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

        {role === 'RESTAURANTE' && restaurants.length > 0 ? (
          <div className="flex shrink-0 items-center gap-2 py-2">
            <label
              htmlFor="active-store-selector"
              className="label-code text-xs font-semibold text-foreground-muted"
            >
              Local
            </label>
            <select
              id="active-store-selector"
              aria-label="Local activo"
              value={selectedStoreId !== null ? String(selectedStoreId) : ''}
              onChange={(event) => {
                const nextId = Number(event.target.value);
                selectStore(Number.isInteger(nextId) && nextId > 0 ? nextId : null);
              }}
              className="h-8 rounded-control border border-border bg-surface px-2.5 text-xs font-semibold text-foreground focus-visible:outline-2 focus-visible:outline-primary focus-visible:outline-offset-2"
            >
              {restaurants.map((restaurant) => (
                <option key={restaurant.id} value={String(restaurant.id)}>
                  {formatRestaurantName(restaurant)}
                </option>
              ))}
            </select>
          </div>
        ) : null}
      </div>
    </nav>
  );
}
