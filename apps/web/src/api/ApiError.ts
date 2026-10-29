/**
 * Discriminant of an {@link ApiError}.
 *
 * It is derived from the HTTP status the backend answered with, so the UI can
 * branch on the rule of the spec S7 without parsing messages:
 *
 * - `unauthorized` — 401, the session must be renewed or the user sent to the login route.
 * - `forbidden` — 403, the role of the user does not own the resource.
 * - `notFound` — 404.
 * - `conflict` — 409, the operation can be retried.
 * - `badRequest` — 400.
 * - `clientError` — any other 4xx.
 * - `serverError` — any 5xx.
 * - `timeout` — the request was aborted by the client deadline.
 * - `network` — the request never reached the backend.
 * - `aborted` — the caller cancelled the request.
 * - `unknown` — the response could not be classified.
 */
export type ApiErrorKind =
  | 'unauthorized'
  | 'forbidden'
  | 'notFound'
  | 'conflict'
  | 'badRequest'
  | 'clientError'
  | 'serverError'
  | 'timeout'
  | 'network'
  | 'aborted'
  | 'unknown';

/** Map an HTTP status onto the discriminant of the error. */
export function apiErrorKindForStatus(status: number): ApiErrorKind {
  switch (status) {
    case 401:
      return 'unauthorized';
    case 403:
      return 'forbidden';
    case 404:
      return 'notFound';
    case 409:
      return 'conflict';
    case 400:
      return 'badRequest';
    default:
      if (status >= 500) {
        return 'serverError';
      }
      if (status >= 400) {
        return 'clientError';
      }
      return 'unknown';
  }
}

export interface ApiErrorOptions {
  /** HTTP status of the answer, or `0` when no answer was received. */
  status: number;
  /** Overrides the discriminant derived from the status. */
  kind?: ApiErrorKind;
  /** Machine readable code of the backend (`FORBIDDEN`, `CONFLICT`, ...). */
  code?: string | null;
  /** Raw parsed body of the answer, kept for diagnostics. */
  body?: unknown;
  /** Original failure, when the error wraps one. */
  cause?: unknown;
}

/**
 * A failed call to the backend.
 *
 * `message` is the literal message of the backend — the UI shows it as it
 * comes, without translating it and without replacing it with a message of
 * its own (spec S7).
 */
export class ApiError extends Error {
  readonly status: number;
  readonly kind: ApiErrorKind;
  readonly code: string | null;
  readonly body: unknown;

  constructor(message: string, options: ApiErrorOptions) {
    super(message, options.cause === undefined ? undefined : { cause: options.cause });

    this.name = 'ApiError';
    this.status = options.status;
    this.kind = options.kind ?? apiErrorKindForStatus(options.status);
    this.code = options.code ?? null;
    this.body = options.body;
  }
}
