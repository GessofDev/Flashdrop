import { notifySessionExpired } from '@/session/sessionEvents';
import { isTokenPair, sessionStore } from '@/session/tokenStore';

import { ApiError } from './ApiError';
import { isRecord, readErrorCode, readErrorMessage } from './errorBody';
import { resolveApiErrorOutcome } from './errorPolicy';
import type { QueryParams, RefreshRequest, TokenPair } from './types';

/** HTTP verbs the client uses. */
export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';

/**
 * Shape of the body of a successful answer.
 *
 * It is declared per endpoint, never assumed: `orders-service` and
 * `delivery-service` answer with the `{ success, message, data }` envelope,
 * while `auth-service` and `catalog-service` answer with the bare DTO.
 */
export type ResponseShape = 'envelope' | 'bare';

/**
 * How a call treats the session.
 *
 * - `authenticated` — the `Authorization` header is attached when there is a
 *   token; a 401 renews the token once and retries the call; when the renewal
 *   fails the session is cleared and the app sends the user to the login
 *   route.
 * - `public` — no `Authorization` header, no renewal and no redirect: the
 *   answer is the answer, including a 401 (a wrong password, for instance).
 */
export type SessionMode = 'authenticated' | 'public';

export interface RequestOptions {
  /** Path of the endpoint, relative to the API base. */
  path: string;
  /** Shape of the body of a successful answer. */
  responseShape: ResponseShape;
  method?: HttpMethod;
  /** Body of the request; it is sent as JSON. */
  body?: unknown;
  query?: QueryParams;
  headers?: Record<string, string>;
  /** How the call treats the session. Defaults to `authenticated`. */
  session?: SessionMode;
  /** Deadline of the call in milliseconds. */
  timeoutMs?: number;
  /** Signal of the caller, used to cancel the call. */
  signal?: AbortSignal;
}

/** Deadline of a call when the caller does not set one. */
export const DEFAULT_TIMEOUT_MS = 15_000;

/**
 * Message used when the backend answers without a message.
 *
 * It never replaces a message of the backend: it only covers the case of an
 * answer that carries no `message` at all, such as an HTML error page of a
 * proxy.
 */
const MISSING_MESSAGE = 'El backend no devolvió un mensaje de error.';

/** Message used when the answer does not have the declared shape. */
const UNEXPECTED_SHAPE_MESSAGE = 'El backend devolvió una respuesta con un formato inesperado.';

/**
 * Whether the body of the request is a `FormData`.
 *
 * A multipart body carries its own boundary, so the client must not write the
 * `Content-Type` header: the browser writes it with the boundary it
 * generated, and a hand-written header would break the upload.
 */
function isFormData(body: unknown): body is FormData {
  return typeof FormData !== 'undefined' && body instanceof FormData;
}

/**
 * Encode the body of the request.
 *
 * A `FormData` travels as it is; every other body travels as JSON.
 */
function encodeBody(body: unknown): BodyInit | undefined {
  if (body === undefined) {
    return undefined;
  }

  if (isFormData(body)) {
    return body;
  }

  return JSON.stringify(body);
}

function apiBaseUrl(): string {
  const configured = import.meta.env.VITE_API_BASE_URL;

  return typeof configured === 'string' ? configured.replace(/\/+$/, '') : '';
}

function buildUrl(path: string, query: QueryParams | undefined): string {
  const url = `${apiBaseUrl()}${path}`;

  if (query === undefined) {
    return url;
  }

  const params = new URLSearchParams();

  for (const [name, value] of Object.entries(query)) {
    if (value === undefined || value === null) {
      continue;
    }

    params.append(name, String(value));
  }

  const search = params.toString();

  return search.length > 0 ? `${url}?${search}` : url;
}

function buildHeaders(options: RequestOptions): Record<string, string> {
  const headers: Record<string, string> = { Accept: 'application/json' };

  if (options.body !== undefined && !isFormData(options.body)) {
    headers['Content-Type'] = 'application/json';
  }

  if (options.session !== 'public') {
    const tokens = sessionStore.getTokens();

    if (tokens !== null) {
      headers.Authorization = `Bearer ${tokens.accessToken}`;
    }
  }

  return { ...headers, ...options.headers };
}

function forwardAbort(source: AbortSignal | undefined, target: AbortController): () => void {
  if (source === undefined) {
    return () => undefined;
  }

  if (source.aborted) {
    target.abort();

    return () => undefined;
  }

  const onAbort = (): void => target.abort();
  source.addEventListener('abort', onAbort);

  return () => source.removeEventListener('abort', onAbort);
}

async function readBody(response: Response): Promise<unknown> {
  let text: string;

  try {
    text = await response.text();
  } catch {
    return null;
  }

  if (text.length === 0) {
    return null;
  }

  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

/**
 * Perform a call and unwrap its answer.
 *
 * It never renews the token: the renewal lives in {@link request}, so the
 * call that renews cannot trigger another renewal.
 */
async function send<T>(options: RequestOptions): Promise<T> {
  const url = buildUrl(options.path, options.query);
  const headers = buildHeaders(options);
  const method = options.method ?? 'GET';
  const payload = encodeBody(options.body);

  const controller = new AbortController();
  let timedOut = false;
  const timer = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  const detach = forwardAbort(options.signal, controller);

  let response: Response;

  try {
    response = await fetch(url, { method, headers, body: payload, signal: controller.signal });
  } catch (cause) {
    if (timedOut) {
      throw new ApiError('La solicitud excedió el tiempo de espera.', {
        status: 0,
        kind: 'timeout',
        cause,
      });
    }

    if (options.signal?.aborted === true) {
      throw new ApiError('La solicitud fue cancelada.', { status: 0, kind: 'aborted', cause });
    }

    throw new ApiError('No se pudo conectar con el backend.', { status: 0, kind: 'network', cause });
  } finally {
    clearTimeout(timer);
    detach();
  }

  const body = await readBody(response);

  if (!response.ok) {
    throw new ApiError(readErrorMessage(body) ?? MISSING_MESSAGE, {
      status: response.status,
      code: readErrorCode(body),
      body,
    });
  }

  if (options.responseShape === 'bare') {
    return body as T;
  }

  if (!isRecord(body) || typeof body.success !== 'boolean') {
    throw new ApiError(UNEXPECTED_SHAPE_MESSAGE, {
      status: response.status,
      kind: 'unknown',
      body,
    });
  }

  if (!body.success) {
    throw new ApiError(readErrorMessage(body) ?? MISSING_MESSAGE, {
      status: response.status,
      code: readErrorCode(body),
      body,
    });
  }

  return (body.data ?? null) as T;
}

let pendingRefresh: Promise<string | null> | null = null;

/**
 * Renew the access token.
 *
 * The refresh token rotates on every renewal, so the new pair replaces the
 * old one in the store. Concurrent calls share a single renewal: the answer
 * is the same for all of them and the backend would reject the second
 * rotation.
 */
function renewAccessToken(): Promise<string | null> {
  if (pendingRefresh !== null) {
    return pendingRefresh;
  }

  const refreshToken = sessionStore.getTokens()?.refreshToken;

  if (refreshToken === undefined || refreshToken.length === 0) {
    return Promise.resolve(null);
  }

  const attempt = send<TokenPair>({
    path: '/auth/refresh',
    method: 'POST',
    responseShape: 'bare',
    session: 'public',
    body: { refreshToken } satisfies RefreshRequest,
  })
    .then((pair) => {
      if (!isTokenPair(pair)) {
        return null;
      }

      sessionStore.setTokens(pair);

      return pair.accessToken;
    })
    .catch(() => null)
    .finally(() => {
      pendingRefresh = null;
    });

  pendingRefresh = attempt;

  return attempt;
}

/**
 * Forget the session and announce it, so the app sends the user to the login
 * route.
 */
function handleSessionExpired(error: ApiError): void {
  const outcome = resolveApiErrorOutcome(error);

  if (outcome.kind !== 'redirect-to-login') {
    return;
  }

  sessionStore.clear();
  notifySessionExpired(outcome.path);
}

/**
 * Call the backend and return the data of its answer.
 *
 * A 401 on an authenticated call renews the token **once** and retries the
 * original call with the new token. When the renewal fails, the session is
 * cleared and the app is told to send the user to the login route; the
 * original error is raised, so the UI still shows the message of the backend.
 */
export async function request<T>(options: RequestOptions): Promise<T> {
  try {
    return await send<T>(options);
  } catch (error) {
    if (error instanceof ApiError && error.kind === 'unauthorized' && options.session !== 'public') {
      const renewed = await renewAccessToken();

      if (renewed !== null) {
        return await send<T>(options);
      }

      handleSessionExpired(error);
    }

    throw error;
  }
}
