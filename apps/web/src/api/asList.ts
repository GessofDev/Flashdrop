import { ApiError } from './ApiError';

/**
 * Read the answer of the backend as a list.
 *
 * Some endpoints promise a list and can answer with something else — a
 * wrapper, an error payload, `null`. The client raises the same error it
 * raises for any other unexpected shape, so the UI shows a notice instead of
 * breaking while it maps the answer.
 */
export function asList<T>(answer: unknown): T[] {
  if (!Array.isArray(answer)) {
    throw new ApiError('El backend devolvió una respuesta con un formato inesperado.', {
      status: 200,
      kind: 'unknown',
      body: answer,
    });
  }

  return answer as T[];
}
