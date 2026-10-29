import { render, screen } from '@testing-library/react';
import { act } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';

import { SessionProvider, sessionStore, useSession } from '@/session';

function SessionProbe() {
  const { session } = useSession();

  return <p data-testid="session-state">{session === null ? 'no-session' : 'has-session'}</p>;
}

function SessionDetailsProbe() {
  const { session, tokens, activeRole, setTokens, setActiveRole, login, logout } = useSession();

  return (
    <div>
      <p data-testid="user-id">{session?.userId ?? 'none'}</p>
      <p data-testid="roles">{session?.roles.join(',') ?? 'none'}</p>
      <p data-testid="active-role">{activeRole ?? 'none'}</p>
      <p data-testid="access-token">{tokens?.accessToken ?? 'none'}</p>
      <p data-testid="refresh-token">{tokens?.refreshToken ?? 'none'}</p>
      <button
        type="button"
        onClick={() =>
          setTokens({ accessToken: 'access-9', refreshToken: 'refresh-9', expiresInSeconds: 900 })
        }
      >
        set tokens
      </button>
      <button type="button" onClick={() => setActiveRole('REPARTIDOR')}>
        set role
      </button>
      <button
        type="button"
        onClick={() =>
          login({
            userId: 42,
            name: 'Demo',
            email: 'demo@flashdrop.cl',
            roles: ['Cliente', 'Repartidor'],
            accessToken: 'access-1',
            refreshToken: 'refresh-1',
            expiresInSeconds: 900,
          })
        }
      >
        login
      </button>
      <button type="button" onClick={logout}>
        logout
      </button>
    </div>
  );
}

describe('session context', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  it('starts without a session', () => {
    render(
      <SessionProvider>
        <SessionProbe />
      </SessionProvider>,
    );

    expect(screen.getByTestId('session-state')).toHaveTextContent('no-session');
  });

  it('fails loudly when the hook is used outside the provider', () => {
    expect(() => render(<SessionProbe />)).toThrow(/SessionProvider/);
  });
});

describe('session tokens', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  it('stores the token pair in sessionStorage and not in localStorage', () => {
    render(
      <SessionProvider>
        <SessionDetailsProbe />
      </SessionProvider>,
    );

    act(() => {
      screen.getByRole('button', { name: 'set tokens' }).click();
    });

    expect(screen.getByTestId('access-token')).toHaveTextContent('access-9');
    expect(screen.getByTestId('refresh-token')).toHaveTextContent('refresh-9');

    expect(window.sessionStorage.getItem('flashdrop.session.v1')).toContain('access-9');
    expect(window.localStorage.getItem('flashdrop.session.v1')).toBeNull();
  });

  it('keeps the token pair and the active role after a reload', () => {
    const { unmount } = render(
      <SessionProvider>
        <SessionDetailsProbe />
      </SessionProvider>,
    );

    act(() => {
      screen.getByRole('button', { name: 'login' }).click();
    });

    expect(screen.getByTestId('active-role')).toHaveTextContent('CLIENTE');
    expect(screen.getByTestId('user-id')).toHaveTextContent('42');
    expect(screen.getByTestId('roles')).toHaveTextContent('CLIENTE,REPARTIDOR');

    unmount();

    render(
      <SessionProvider>
        <SessionDetailsProbe />
      </SessionProvider>,
    );

    expect(screen.getByTestId('access-token')).toHaveTextContent('access-1');
    expect(screen.getByTestId('active-role')).toHaveTextContent('CLIENTE');
  });

  it('changes the active role without losing the session', () => {
    render(
      <SessionProvider>
        <SessionDetailsProbe />
      </SessionProvider>,
    );

    act(() => {
      screen.getByRole('button', { name: 'login' }).click();
    });

    act(() => {
      screen.getByRole('button', { name: 'set role' }).click();
    });

    expect(screen.getByTestId('active-role')).toHaveTextContent('REPARTIDOR');
    expect(screen.getByTestId('access-token')).toHaveTextContent('access-1');
  });

  it('clears the session and the tokens on logout', () => {
    render(
      <SessionProvider>
        <SessionDetailsProbe />
      </SessionProvider>,
    );

    act(() => {
      screen.getByRole('button', { name: 'login' }).click();
    });

    act(() => {
      screen.getByRole('button', { name: 'logout' }).click();
    });

    expect(screen.getByTestId('access-token')).toHaveTextContent('none');
    expect(screen.getByTestId('user-id')).toHaveTextContent('none');
    expect(screen.getByTestId('active-role')).toHaveTextContent('none');
    expect(window.sessionStorage.getItem('flashdrop.session.v1')).toBeNull();
  });
});
