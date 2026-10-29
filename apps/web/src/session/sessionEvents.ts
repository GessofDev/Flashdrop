/**
 * Session expiry notifications.
 *
 * The API layer clears the session and announces it here; the app listens
 * and redirects to the login route. The transport stays free of the router,
 * so it can be used outside of React (tests, workers, future SSR).
 */

/** Called with the route the app has to navigate to. */
export type SessionExpiredHandler = (path: string) => void;

let handler: SessionExpiredHandler | null = null;

/** Register the handler of the app; `null` removes it. */
export function setSessionExpiredHandler(next: SessionExpiredHandler | null): void {
  handler = next;
}

/** Announce that the session is gone. */
export function notifySessionExpired(path: string): void {
  if (handler === null) {
    return;
  }

  handler(path);
}
