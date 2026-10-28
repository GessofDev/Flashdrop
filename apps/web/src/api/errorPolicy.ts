import { LOGIN_PATH } from '@/app/routePaths';

import type { ApiError } from './ApiError';

/**
 * What the UI has to do with a failed call.
 *
 * The three rules of the spec S7 are encoded here:
 *
 * - 401 → the session is gone, the app sends the user to the login route.
 * - 403 → the role of the user does not own the resource, so the app shows
 *   the view of the wrong role.
 * - 409 → the operation conflicts with the current state, so the app shows
 *   the message of the backend and offers the retry.
 *
 * `message` always comes from the backend; the policy never writes a message
 * of its own.
 */
export type ApiErrorOutcome =
  | { kind: 'redirect-to-login'; path: string }
  | { kind: 'wrong-role'; message: string }
  | { kind: 'conflict'; message: string; canRetry: boolean }
  | { kind: 'not-found'; message: string }
  | { kind: 'message'; message: string };

/** Translate an {@link ApiError} into the decision the UI takes. */
export function resolveApiErrorOutcome(error: ApiError): ApiErrorOutcome {
  switch (error.kind) {
    case 'unauthorized':
      return { kind: 'redirect-to-login', path: LOGIN_PATH };
    case 'forbidden':
      return { kind: 'wrong-role', message: error.message };
    case 'conflict':
      return { kind: 'conflict', message: error.message, canRetry: true };
    case 'notFound':
      return { kind: 'not-found', message: error.message };
    default:
      return { kind: 'message', message: error.message };
  }
}
