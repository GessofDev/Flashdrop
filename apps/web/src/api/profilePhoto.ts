import { sessionStore } from '@/session/tokenStore';

import { ApiError } from './ApiError';
import { apiBaseUrl } from './baseUrl';
import { request } from './client';
import { readErrorCode, readErrorMessage } from './errorBody';
import type { UserProfile } from './types';

/**
 * Photo of the profile (`auth-service`).
 *
 * The service answers with the bare DTO — no `{ success, message, data }`
 * envelope — so every call declares `responseShape: 'bare'`. The user always
 * comes from the token: no call of this module sends an id.
 */

/** Path of the photo of the profile in `auth-service`. */
const PHOTO_PATH = '/auth/profile/photo';

/**
 * Message used when the backend answers without a message.
 *
 * It never replaces a message of the backend: it only covers the case of an
 * answer that carries no `message` at all.
 */
const MISSING_MESSAGE = 'El backend no devolvió un mensaje de error.';

/**
 * Read the body of a failed answer.
 *
 * The body of an error is JSON, like the rest of the answers of the backend;
 * a body that is not JSON is read as no body at all.
 */
async function readBody(response: Response): Promise<unknown> {
  try {
    const text = await response.text();

    return text.length > 0 ? (JSON.parse(text) as unknown) : null;
  } catch {
    return null;
  }
}

/**
 * Turn a failed answer into an {@link ApiError}.
 *
 * The bytes of the photo do not travel through the JSON client, so the body
 * of the failure is read here, with the same reader the rest of the app uses.
 */
async function readFailure(response: Response): Promise<ApiError> {
  const body = await readBody(response);

  return new ApiError(readErrorMessage(body) ?? MISSING_MESSAGE, {
    status: response.status,
    code: readErrorCode(body),
    body,
  });
}

export const profilePhotoApi = {
  /**
   * `GET /auth/profile/photo` — the bytes of the photo of the signed in user.
   *
   * The endpoint needs the `Authorization` header, so an `<img src>` cannot
   * load it: the bytes travel through this call and the app shows them with
   * an object URL.
   *
   * It answers `null` when the user has no photo stored: the backend answers
   * 404 for that case, and it is not a failure the customer has to read about.
   */
  async read(): Promise<Blob | null> {
    const tokens = sessionStore.getTokens();
    const headers: Record<string, string> = { Accept: 'image/jpeg' };

    if (tokens !== null) {
      headers.Authorization = `Bearer ${tokens.accessToken}`;
    }

    const response = await fetch(`${apiBaseUrl()}${PHOTO_PATH}`, {
      method: 'GET',
      headers,
    });

    if (response.status === 404) {
      return null;
    }

    if (!response.ok) {
      throw await readFailure(response);
    }

    return await response.blob();
  },

  /**
   * `POST /auth/profile/photo` — uploads or replaces the photo.
   *
   * The body is multipart with the part `file`; the client must not write the
   * `Content-Type` header, because the browser adds the boundary. The answer
   * is the profile of the owner, with the new photo.
   */
  upload(file: File): Promise<UserProfile> {
    const form = new FormData();

    form.append('file', file);

    return request<UserProfile>({
      path: PHOTO_PATH,
      method: 'POST',
      body: form,
      responseShape: 'bare',
    });
  },

  /**
   * `DELETE /auth/profile/photo` — removes the photo.
   *
   * The endpoint answers 204 with no body, so the call declares the bare
   * shape: the envelope shape would read the empty body as a broken answer.
   */
  async remove(): Promise<void> {
    await request<null>({
      path: PHOTO_PATH,
      method: 'DELETE',
      responseShape: 'bare',
    });
  },
};
