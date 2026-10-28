import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import type { Session } from './types';

export interface SessionContextValue {
  /** The active session, or `null` when nobody is signed in. */
  session: Session | null;
  setSession: (session: Session | null) => void;
  clearSession: () => void;
}

const SessionContext = createContext<SessionContextValue | null>(null);

export interface SessionProviderProps {
  children: ReactNode;
  /** Defaults to no session; the real login flow lives in a later task. */
  initialSession?: Session | null;
}

/**
 * Holds the session of the signed-in user.
 *
 * It starts empty on purpose: no token is read from storage and no request is
 * issued here. The API client task is the one that hydrates it.
 */
export function SessionProvider({ children, initialSession = null }: SessionProviderProps) {
  const [session, setSession] = useState<Session | null>(initialSession);
  const clearSession = useCallback(() => setSession(null), []);

  const value = useMemo<SessionContextValue>(
    () => ({ session, setSession, clearSession }),
    [session, clearSession],
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
