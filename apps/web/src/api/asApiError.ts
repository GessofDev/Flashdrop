import { ApiError } from './ApiError';

/**
 * Translate an unknown failure into an {@link ApiError}.
 *
 * The client always raises an `ApiError`; the wrapper only covers the
 * unexpected case, so the UI always has a message to show and never an
 * exception in the middle of a render.
 */
export function asApiError(cause: unknown): ApiError {
  if (cause instanceof ApiError) {
    return cause;
  }

  return new ApiError('No se pudo completar la solicitud.', {
    status: 0,
    kind: 'unknown',
    cause,
  });
}
