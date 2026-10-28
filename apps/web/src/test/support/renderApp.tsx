import { render } from '@testing-library/react';
import type { RenderResult } from '@testing-library/react';
import { MemoryRouter, useLocation } from 'react-router-dom';

import { AppRoutes } from '@/app/AppRoutes';
import { SessionRedirectBridge } from '@/app/SessionRedirectBridge';
import { SessionProvider, sessionStore } from '@/session';
import type { Role, Session } from '@/session';

/** Token pair used by the signed-in fixtures of the tests. */
export const TEST_TOKENS = {
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresInSeconds: 900,
};

export interface SignInOptions {
  /** Role the user is working on. */
  activeRole?: Role;
  /** Roles the account holds. */
  roles?: Role[];
  /** Display name of the user. */
  name?: string;
}

/**
 * Open a session in the store, the way the login of the app does.
 *
 * The session travels through `sessionStorage`, so a test can read it back
 * after a reload with `sessionStore.reload()`.
 */
export function signIn(options: SignInOptions = {}): Session {
  const session: Session = {
    userId: '42',
    name: options.name ?? 'Matías Valenzuela',
    email: 'demo@flashdrop.cl',
    roles: options.roles ?? ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
    activeRole: options.activeRole ?? 'CLIENTE',
    restaurantId: null,
  };

  sessionStore.set({ session, tokens: TEST_TOKENS });

  return session;
}

function LocationProbe() {
  const location = useLocation();

  return <p data-testid="location">{location.pathname}</p>;
}

/** Render the whole app — session, router, bridge and routes — at a path. */
export function renderApp(path = '/'): RenderResult {
  return render(
    <SessionProvider>
      <MemoryRouter initialEntries={[path]}>
        <SessionRedirectBridge />
        <LocationProbe />
        <AppRoutes />
      </MemoryRouter>
    </SessionProvider>,
  );
}
