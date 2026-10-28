import type { TokenPair } from '@/api/types';

import { isRole } from './types';
import type { Role, Session } from './types';

/**
 * Key of the session in `sessionStorage`.
 *
 * The access token lives in `sessionStorage`, never in `localStorage`: it is
 * a credential and it must not outlive the tab.
 */
export const SESSION_STORAGE_KEY = 'flashdrop.session.v1';

/** Everything the app remembers about the signed in user. */
export interface SessionState {
  session: Session | null;
  tokens: TokenPair | null;
}

const EMPTY_STATE: SessionState = { session: null, tokens: null };

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/** Narrow an unknown value to a {@link TokenPair}. */
export function isTokenPair(value: unknown): value is TokenPair {
  if (!isRecord(value)) {
    return false;
  }

  return (
    typeof value.accessToken === 'string' &&
    typeof value.refreshToken === 'string' &&
    typeof value.expiresInSeconds === 'number'
  );
}

function isSession(value: unknown): value is Session {
  if (!isRecord(value)) {
    return false;
  }

  if (typeof value.userId !== 'string' || typeof value.email !== 'string') {
    return false;
  }

  if (!Array.isArray(value.roles) || !value.roles.every((role) => isRole(role))) {
    return false;
  }

  if (!isRole(value.activeRole)) {
    return false;
  }

  return value.restaurantId === null || typeof value.restaurantId === 'string';
}

function readStorage(): SessionState {
  if (typeof window === 'undefined') {
    return EMPTY_STATE;
  }

  try {
    const raw = window.sessionStorage.getItem(SESSION_STORAGE_KEY);

    if (raw === null) {
      return EMPTY_STATE;
    }

    const parsed: unknown = JSON.parse(raw);

    if (!isRecord(parsed)) {
      return EMPTY_STATE;
    }

    return {
      session: isSession(parsed.session) ? parsed.session : null,
      tokens: isTokenPair(parsed.tokens) ? parsed.tokens : null,
    };
  } catch {
    return EMPTY_STATE;
  }
}

function writeStorage(state: SessionState): void {
  if (typeof window === 'undefined') {
    return;
  }

  try {
    if (state.session === null && state.tokens === null) {
      window.sessionStorage.removeItem(SESSION_STORAGE_KEY);
      return;
    }

    window.sessionStorage.setItem(SESSION_STORAGE_KEY, JSON.stringify(state));
  } catch {
    // A storage that refuses to write (private mode, quota) must not break
    // the session in memory.
  }
}

let cachedState: SessionState | null = null;
const listeners = new Set<() => void>();

function currentState(): SessionState {
  if (cachedState === null) {
    cachedState = readStorage();
  }

  return cachedState;
}

function update(next: SessionState): void {
  cachedState = next;
  writeStorage(next);

  for (const listener of listeners) {
    listener();
  }
}

/**
 * Store of the session.
 *
 * It is a plain module — not a React context — so the API client can read and
 * renew the tokens without depending on the component tree. The React
 * provider of `src/session/SessionContext.tsx` subscribes to it.
 */
export const sessionStore = {
  /** Current state; the reference changes only when the state changes. */
  getSnapshot(): SessionState {
    return currentState();
  },

  /** Subscribe to the changes of the store. */
  subscribe(listener: () => void): () => void {
    listeners.add(listener);

    return () => {
      listeners.delete(listener);
    };
  },

  getTokens(): TokenPair | null {
    return currentState().tokens;
  },

  setTokens(tokens: TokenPair | null): void {
    update({ ...currentState(), tokens });
  },

  getSession(): Session | null {
    return currentState().session;
  },

  /** Replace the whole state at once, with a single write to the storage. */
  set(state: SessionState): void {
    update(state);
  },

  setSession(session: Session | null): void {
    update({ ...currentState(), session });
  },

  /** The role the user is currently working on, `null` without a session. */
  getActiveRole(): Role | null {
    return currentState().session?.activeRole ?? null;
  },

  /** Move the session to another role of the same user. */
  setActiveRole(role: Role): void {
    const session = currentState().session;

    if (session === null) {
      return;
    }

    update({ ...currentState(), session: { ...session, activeRole: role } });
  },

  /** Forget the session and the tokens. */
  clear(): void {
    update(EMPTY_STATE);
  },

  /**
   * Reset the store to its initial state: no session, no tokens and nothing
   * in the storage. It exists for the tests, which need a clean store.
   */
  reset(): void {
    cachedState = null;
    writeStorage(EMPTY_STATE);

    for (const listener of listeners) {
      listener();
    }
  },
};
