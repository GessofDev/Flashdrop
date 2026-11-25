import type { ReactNode } from 'react';

import { useStoreContext } from '@/components/store';
import { Button } from '@/components/ui';
import type { Role } from '@/session';

import { RoleNavigation } from './RoleNavigation';
import { ShellFooter } from './ShellFooter';
import { TopBar } from './TopBar';

export interface AppShellProps {
  /** Role the user is working on. */
  role: Role;
  /** Screen the shell holds. */
  children: ReactNode;
}

/**
 * Canonical shell of the app.
 *
 * It is mounted once, in the layout of the routes, so the top bar, the
 * navigation and the footer are not rebuilt on every screen.
 */
export function AppShell({ role, children }: AppShellProps) {
  const { localContextRequired, selectStore } = useStoreContext();

  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <div className="sticky top-0 z-50 shadow-card">
        <TopBar role={role} />
        <RoleNavigation role={role} />
      </div>
      <main className="flex-1">
        {role === 'RESTAURANTE' &&
        localContextRequired !== null &&
        localContextRequired.length > 0 ? (
          <section
            aria-label="Seleccionar local activo"
            className="mx-auto mt-4 w-full max-w-app px-4 md:px-8"
          >
            <div className="flex flex-col gap-3 rounded-container border border-border bg-surface p-4 shadow-card">
              <div className="flex flex-col gap-1">
                <h2 className="font-display text-base font-bold text-foreground">
                  Selecciona un local para operar
                </h2>
                <p className="text-sm text-foreground-secondary">
                  Tu marca tiene varios locales. Elige uno para continuar.
                </p>
              </div>
              <div className="flex flex-wrap gap-2">
                {localContextRequired.map((restaurant) => (
                  <Button
                    key={restaurant.id}
                    variant="secondary"
                    size="sm"
                    onClick={() => selectStore(restaurant.id)}
                  >
                    {restaurant.name}
                    {restaurant.address ? ` — ${restaurant.address}` : ''}
                  </Button>
                ))}
              </div>
            </div>
          </section>
        ) : null}
        {children}
      </main>
      <ShellFooter role={role} />
    </div>
  );
}
