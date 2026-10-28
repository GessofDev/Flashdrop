import { createContext, useCallback, useContext, useMemo, useSyncExternalStore } from 'react';
import type { ReactNode } from 'react';

import type { AuthResult, TokenPair } from '@/api/types';

import { toRoles } from './roles';
import { sessionStore } from './tokenStore';
import type { Role, Session } from './types';

export interface LoginOptions {
  /** Role the user lands on; the first role of the account when omitted. */
  activeRole?: Role;
  /** Store of the active role, when the user owns one. */
  restaurantId?: string | null;
}

export interface SessionContextValue {
  /** The active session, or `null` when nobody is signed in. */
  session: Session | null;
  /** The token pair of the session, or `null` when nobody is signed in. */
  tokens: TokenPair | null;
  /** The role the user is working on, or `null` when nobody is signed in. */
  activeRole: Role | null;
  setSession: (session: Session | null) => void;
  setTokens: (tokens: TokenPair | null) => void;
  setActiveRole: (role: Role) => void;
  /**
   * Open a session with the answer of `POST /auth/login`.
   *
   * The role names of the backend are translated into the roles of the app;
   * an unknown name is dropped instead of being invented.
   */
  login: (result: AuthResult, options?: LoginOptions) => void;
  /**
   * Forget the session and the tokens.
   *
   * The revocation of the refresh token (`POST /auth/logout`) belongs to the
   * caller: the session module never talks to the backend.
   */
  logout: () => void;
  clearSession: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export interface SessionProviderProps {
  children: ReactNode;
  /**
   * Session to show instead of the stored one.
   *
   * Defaults to the session of the store, which is hydrated from
   * `sessionStorage` the first time it is read.
   */
  initialSession?: Session | null;
}

/**
 * Holds the session of the signed-in user.
 *
 * The state lives in the session store (`src/session/tokenStore.ts`), so the
 * API client can renew the tokens without depending on the component tree;
 * this provider subscribes to that store and exposes it to the components.
 */
export function SessionProvider({ children, initialSession = null }: SessionProviderProps) {
  const stored = useSyncExternalStore(
    sessionStore.subscribe,
    sessionStore.getSnapshot,
    sessionStore.getSnapshot,
  );

  const session = initialSession ?? stored.session;
  const tokens = stored.tokens;

  const setSession = useCallback((next: Session | null) => sessionStore.setSession(next), []);
  const setTokens = useCallback((next: TokenPair | null) => sessionStore.setTokens(next), []);
  const setActiveRole = useCallback((role: Role) => sessionStore.setActiveRole(role), []);
  const clearSession = useCallback(() => sessionStore.clear(), []);
  const logout = useCallback(() => sessionStore.clear(), []);

  const login = useCallback((result: AuthResult, options?: LoginOptions) => {
    const roles = toRoles(result.roles);
    const activeRole = options?.activeRole ?? roles[0] ?? 'CLIENTE';

    sessionStore.set({
      session: {
        userId: String(result.userId),
        name: result.name,
        email: result.email,
        roles,
        activeRole,
        restaurantId: options?.restaurantId ?? null,
      },
      tokens: {
        accessToken: result.accessToken,
        refreshToken: result.refreshToken,
        expiresInSeconds: result.expiresInSeconds,
      },
    });
  }, []);

  const value = useMemo<SessionContextValue>(
    () => ({
      session,
      tokens,
      activeRole: session?.activeRole ?? null,
      setSession,
      setTokens,
      setActiveRole,
      login,
      logout,
      clearSession,
    }),
    [session, tokens, setSession, setTokens, setActiveRole, login, logout, clearSession],
  );

  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

/** Read the session. Throws when used outside of a `SessionProvider`. */
export function useSession(): SessionContextValue {
  const context = useContext(SessionContext);

  if (context === null) {
    throw new Error('useSession must be used within a SessionProvider');
  }

  return context;
}
