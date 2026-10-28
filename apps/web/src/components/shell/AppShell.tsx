import type { ReactNode } from 'react';

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
  return (
    <div className="flex min-h-screen flex-col bg-canvas">
      <div className="sticky top-0 z-50 shadow-card">
        <TopBar role={role} />
        <RoleNavigation role={role} />
      </div>
      <main className="flex-1">{children}</main>
      <ShellFooter role={role} />
    </div>
  );
}
