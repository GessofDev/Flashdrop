/**
 * Readers of the error bodies of the backend.
 *
 * The three services do not share one error envelope:
 *
 * - `orders-service` and `catalog-service` answer `{ status, error, message }`
 *   (`ErrorResponse`).
 * - `delivery-service` answers `{ timestamp, status, error, message }`.
 * - `auth-service` answers `{ code, service, trace_id, message }` on its public
 *   endpoints and `{ status, error, message }` on the internal ones.
 * - The gateway answers `{ statusCode, error, message, timestamp }`.
 *
 * `message` is the only field they all share, so it is the one the client
 * shows; `error` / `code` are read as the machine readable code.
 */

export function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

/**
 * The literal message of the backend, or `null` when the body carries none.
 */
export function readErrorMessage(body: unknown): string | null {
  if (!isRecord(body)) {
    return null;
  }

  const message = body.message;

  return typeof message === 'string' && message.length > 0 ? message : null;
}

/**
 * The machine readable code of the backend (`FORBIDDEN`, `CONFLICT`,
 * `INVALID_CREDENTIALS`, ...), or `null` when the body carries none.
 */
export function readErrorCode(body: unknown): string | null {
  if (!isRecord(body)) {
    return null;
  }

  for (const key of ['error', 'code'] as const) {
    const value = body[key];
    if (typeof value === 'string' && value.length > 0) {
      return value;
    }
  }

  return null;
}
