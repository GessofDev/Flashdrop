import { vi } from 'vitest';

/**
 * A single call captured by {@link mockFetch}.
 *
 * Header names are lower-cased so tests can assert on `authorization`
 * without depending on the casing the client chose.
 */
export interface RecordedCall {
  url: string;
  method: string;
  headers: Record<string, string>;
  /** Parsed JSON body, or the raw string when it is not JSON. */
  body: unknown;
  signal: AbortSignal | null;
}

export type Responder = (call: RecordedCall) => Response | Promise<Response>;

export interface FetchMock {
  /** Every call, in order. */
  calls: RecordedCall[];
}

function normalizeHeaders(headers: HeadersInit | undefined): Record<string, string> {
  if (headers === undefined) {
    return {};
  }

  const entries: [string, string][] =
    headers instanceof Headers
      ? [...headers.entries()]
      : Array.isArray(headers)
        ? headers
        : Object.entries(headers);

  return Object.fromEntries(entries.map(([name, value]) => [name.toLowerCase(), String(value)]));
}

function parseBody(body: BodyInit | null | undefined): unknown {
  if (typeof body !== 'string') {
    return body ?? null;
  }

  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

/**
 * Replace the global `fetch` with a recorder that answers with `responder`.
 *
 * The client under test is the only thing that talks to the network, so a
 * stub is enough to exercise the whole transport layer — including the
 * timeout, which needs the abort signal the client attaches.
 */
export function mockFetch(responder: Responder): FetchMock {
  const calls: RecordedCall[] = [];

  vi.stubGlobal(
    'fetch',
    vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = typeof input === 'string' ? input : input.toString();
      const call: RecordedCall = {
        url,
        method: init?.method ?? 'GET',
        headers: normalizeHeaders(init?.headers),
        body: parseBody(init?.body),
        signal: init?.signal ?? null,
      };

      calls.push(call);

      return responder(call);
    }),
  );

  return { calls };
}

/** A JSON response, the shape every FlashDrop service answers with. */
export function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

/** An empty response, the shape of `POST /auth/logout` (204). */
export function noContentResponse(): Response {
  return new Response(null, { status: 204 });
}

/** A response whose body is not JSON, e.g. an HTML error page from a proxy. */
export function textResponse(body: string, status = 500): Response {
  return new Response(body, { status, headers: { 'Content-Type': 'text/html' } });
}
