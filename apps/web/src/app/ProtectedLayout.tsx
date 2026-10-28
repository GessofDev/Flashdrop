import { Navigate, Outlet, useLocation } from 'react-router-dom';

import { AppShell } from '@/components/shell';
import { useSession } from '@/session';

import { ROLE_HOME, roleOwningPath } from './roleRoutes';
import { routePaths } from './routePaths';

/**
 * Layout of the protected routes: the guard of session and role, plus the
 * canonical shell.
 *
 * - Without a session, every route of the shell goes to the login route.
 * - With a session, a route of another role goes to the home of the active
 *   role instead of a not-found page.
 */
export function ProtectedLayout() {
  const { session } = useSession();
  const location = useLocation();

  if (session === null) {
    return <Navigate to={routePaths.login} replace />;
  }

  const owner = roleOwningPath(location.pathname);

  if (owner !== null && owner !== session.activeRole) {
    return <Navigate to={ROLE_HOME[session.activeRole]} replace />;
  }

  return (
    <AppShell role={session.activeRole}>
      <Outlet />
    </AppShell>
  );
}
