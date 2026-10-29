import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, request } from '@/api';
import { setSessionExpiredHandler } from '@/session/sessionEvents';
import { sessionStore } from '@/session/tokenStore';

import { jsonResponse, mockFetch } from './support/fetchMock';

const TOKENS = { accessToken: 'access-1', refreshToken: 'refresh-1', expiresInSeconds: 900 };

describe('api client', () => {
  beforeEach(() => {
    sessionStore.reset();
    setSessionExpiredHandler(null);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.unstubAllEnvs();
  });

  it('unwraps the envelope of a successful response', async () => {
    mockFetch(() =>
      jsonResponse({ success: true, message: 'Pedido creado', data: { id: 'FD-0042' } }),
    );

    const result = await request<{ id: string }>({
      path: '/api/orders',
      method: 'POST',
      body: { address: 'San Martin 123' },
      responseShape: 'envelope',
    });

    expect(result).toEqual({ id: 'FD-0042' });
  });

  it('returns the raw body of an endpoint that does not use the envelope', async () => {
    mockFetch(() => jsonResponse({ accessToken: 'access-1', refreshToken: 'refresh-1', expiresInSeconds: 900 }));

    const result = await request<{ accessToken: string }>({
      path: '/auth/refresh',
      method: 'POST',
      body: { refreshToken: 'refresh-0' },
      responseShape: 'bare',
      session: 'public',
    });

    expect(result.accessToken).toBe('access-1');
  });

  it('sends the Authorization header when a session is active', async () => {
    sessionStore.setTokens(TOKENS);
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: [] }));

    await request({ path: '/api/orders', responseShape: 'envelope' });

    expect(fetchMock.calls[0]?.headers.authorization).toBe('Bearer access-1');
  });

  it('does not send an Authorization header when there is no token', async () => {
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: [] }));

    await request({ path: '/api/orders', responseShape: 'envelope' });

    expect(fetchMock.calls[0]?.headers.authorization).toBeUndefined();
  });

  it('retries the original request once with the renewed access token after a 401', async () => {
    sessionStore.setTokens(TOKENS);

    const fetchMock = mockFetch((call) => {
      if (call.url.endsWith('/auth/refresh')) {
        return jsonResponse({
          accessToken: 'access-2',
          refreshToken: 'refresh-2',
          expiresInSeconds: 900,
        });
      }

      if (call.url.endsWith('/api/orders')) {
        const orderCalls = fetchMock.calls.filter((entry) => entry.url.endsWith('/api/orders')).length;
        return orderCalls === 1
          ? jsonResponse({ status: 401, error: 'UNAUTHORIZED', message: 'No autenticado' }, 401)
          : jsonResponse({ success: true, message: 'ok', data: [{ id: 'FD-0042' }] });
      }

      return jsonResponse({ success: true, data: null });
    });

    const result = await request<{ id: string }[]>({
      path: '/api/orders',
      responseShape: 'envelope',
    });

    expect(result).toEqual([{ id: 'FD-0042' }]);

    const orderCalls = fetchMock.calls.filter((call) => call.url.endsWith('/api/orders'));
    expect(orderCalls).toHaveLength(2);
    expect(orderCalls[0]?.headers.authorization).toBe('Bearer access-1');
    expect(orderCalls[1]?.headers.authorization).toBe('Bearer access-2');

    const refreshCalls = fetchMock.calls.filter((call) => call.url.endsWith('/auth/refresh'));
    expect(refreshCalls).toHaveLength(1);
    expect(refreshCalls[0]?.body).toEqual({ refreshToken: 'refresh-1' });
    expect(refreshCalls[0]?.headers.authorization).toBeUndefined();

    expect(sessionStore.getTokens()).toEqual({
      accessToken: 'access-2',
      refreshToken: 'refresh-2',
      expiresInSeconds: 900,
    });
  });

  it('clears the session and redirects to /login when the refresh token is rejected', async () => {
    sessionStore.setTokens(TOKENS);
    const onSessionExpired = vi.fn();
    setSessionExpiredHandler(onSessionExpired);

    mockFetch((call) => {
      if (call.url.endsWith('/auth/refresh')) {
        return jsonResponse(
          { code: 'INVALID_TOKEN', service: 'auth-service', trace_id: 'trace-1', message: 'Refresh token invalido' },
          401,
        );
      }

      return jsonResponse({ status: 401, error: 'UNAUTHORIZED', message: 'No autenticado' }, 401);
    });

    const error = await request({ path: '/api/orders', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(401);
    expect((error as ApiError).message).toBe('No autenticado');

    expect(sessionStore.getTokens()).toBeNull();
    expect(sessionStore.getSession()).toBeNull();
    expect(onSessionExpired).toHaveBeenCalledWith('/login');
  });

  it('does not refresh the session when the endpoint is public', async () => {
    sessionStore.setTokens(TOKENS);
    const onSessionExpired = vi.fn();
    setSessionExpiredHandler(onSessionExpired);

    const fetchMock = mockFetch(() =>
      jsonResponse({ code: 'INVALID_CREDENTIALS', service: 'auth-service', message: 'Credenciales invalidas' }, 401),
    );

    const error = await request({ path: '/auth/login', method: 'POST', body: {}, responseShape: 'bare', session: 'public' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(fetchMock.calls).toHaveLength(1);
    expect(fetchMock.calls[0]?.url).toContain('/auth/login');
    expect(fetchMock.calls[0]?.headers.authorization).toBeUndefined();
    expect(sessionStore.getTokens()).toEqual(TOKENS);
    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it('never refreshes more than once for a single request', async () => {
    sessionStore.setTokens(TOKENS);

    const fetchMock = mockFetch((call) => {
      if (call.url.endsWith('/auth/refresh')) {
        return jsonResponse({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresInSeconds: 900 });
      }

      return jsonResponse({ status: 401, error: 'UNAUTHORIZED', message: 'No autenticado' }, 401);
    });

    const error = await request({ path: '/api/orders', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(fetchMock.calls.filter((call) => call.url.endsWith('/api/orders'))).toHaveLength(2);
    expect(fetchMock.calls.filter((call) => call.url.endsWith('/auth/refresh'))).toHaveLength(1);
  });

  it('surfaces the backend message of a 403 on an ApiError', async () => {
    mockFetch(() =>
      jsonResponse(
        { status: 403, error: 'FORBIDDEN', message: 'No puedes consultar este pedido' },
        403,
      ),
    );

    const error = await request({ path: '/api/orders/FD-0042', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(403);
    expect((error as ApiError).kind).toBe('forbidden');
    expect((error as ApiError).message).toBe('No puedes consultar este pedido');
  });

  it('surfaces the backend message of a 409 on an ApiError', async () => {
    mockFetch(() =>
      jsonResponse(
        { status: 409, error: 'CONFLICT', message: 'Transicion de estado no permitida: ENTREGADO -> RETIRADO' },
        409,
      ),
    );

    const error = await request({ path: '/api/orders/FD-0042/status', method: 'PUT', body: {}, responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).status).toBe(409);
    expect((error as ApiError).kind).toBe('conflict');
    expect((error as ApiError).message).toBe('Transicion de estado no permitida: ENTREGADO -> RETIRADO');
  });

  it('exposes the message of an envelope that reports a failure', async () => {
    mockFetch(() =>
      jsonResponse({ success: false, message: 'No route found for orderId=42', data: null }, 400),
    );

    const error = await request({ path: '/api/delivery/routes', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe('No route found for orderId=42');
    expect((error as ApiError).status).toBe(400);
  });

  it('rejects with a typed error when the request times out', async () => {
    mockFetch(
      (call) =>
        new Promise<Response>((_resolve, reject) => {
          call.signal?.addEventListener('abort', () => {
            reject(new DOMException('The operation was aborted.', 'AbortError'));
          });
        }),
    );

    const error = await request({
      path: '/api/orders',
      responseShape: 'envelope',
      timeoutMs: 10,
    }).catch((thrown: unknown) => thrown);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).kind).toBe('timeout');
    expect((error as ApiError).status).toBe(0);
    expect((error as ApiError).message).not.toBe('');
  });

  it('rejects with a typed error when the network is unreachable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    const error = await request({ path: '/api/orders', responseShape: 'envelope' }).catch(
      (thrown: unknown) => thrown,
    );

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).kind).toBe('network');
    expect((error as ApiError).message).not.toBe('');
  });

  it('uses the API base URL override when it is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://api.test');
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: null }));

    await request({ path: '/api/orders', responseShape: 'envelope' });

    expect(fetchMock.calls[0]?.url).toBe('http://api.test/api/orders');
  });

  it('uses a relative URL when no API base URL is configured', async () => {
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: null }));

    await request({ path: '/api/orders', responseShape: 'envelope' });

    expect(fetchMock.calls[0]?.url).toBe('/api/orders');
  });

  it('appends the query parameters that are present', async () => {
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: null }));

    await request({
      path: '/api/orders/restaurants/1/sales-summary',
      responseShape: 'envelope',
      query: { range: 'day', empty: undefined },
    });

    expect(fetchMock.calls[0]?.url).toBe('/api/orders/restaurants/1/sales-summary?range=day');
  });

  it('keeps sending a JSON body with the JSON content type', async () => {
    const fetchMock = mockFetch(() => jsonResponse({ success: true, data: null }));

    await request({
      path: '/api/orders',
      method: 'POST',
      body: { address: 'San Martin 123' },
      responseShape: 'envelope',
    });

    expect(fetchMock.calls[0]?.headers['content-type']).toBe('application/json');
    expect(fetchMock.calls[0]?.body).toEqual({ address: 'San Martin 123' });
  });

  it('sends FormData without setting Content-Type so the browser can add the boundary', async () => {
    const fetchMock = mockFetch(() =>
      jsonResponse({ objectKey: 'products/2026/10/image.webp', url: '/catalog/images/image.webp' }, 201),
    );
    const form = new FormData();
    const file = new File(['image-bytes'], 'image.webp', { type: 'image/webp' });
    form.append('file', file);

    await request({
      path: '/api/catalog/my/products/image',
      method: 'POST',
      body: form,
      responseShape: 'bare',
    });

    expect(fetchMock.calls[0]?.headers['content-type']).toBeUndefined();
    expect(fetchMock.calls[0]?.body).toBe(form);
    expect((fetchMock.calls[0]?.body as FormData).get('file')).toBe(file);
  });
});
