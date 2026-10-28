import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { authApi } from '@/api';
import { sessionStore } from '@/session/tokenStore';

import { jsonResponse, mockFetch, noContentResponse } from './support/fetchMock';

const AUTH_RESULT = {
  userId: 42,
  name: 'Demo',
  email: 'demo@flashdrop.cl',
  roles: ['CLIENTE', 'REPARTIDOR'],
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresInSeconds: 900,
};

describe('auth api', () => {
  beforeEach(() => {
    sessionStore.reset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('posts the credentials to the login endpoint and returns the token pair', async () => {
    const fetchMock = mockFetch(() => jsonResponse(AUTH_RESULT));

    const result = await authApi.login({ login: 'demo@flashdrop.cl', password: 'clave' });

    expect(result).toEqual(AUTH_RESULT);
    expect(fetchMock.calls[0]?.url).toBe('/auth/login');
    expect(fetchMock.calls[0]?.method).toBe('POST');
    expect(fetchMock.calls[0]?.body).toEqual({ login: 'demo@flashdrop.cl', password: 'clave' });
  });

  it('posts the refresh token to the refresh endpoint', async () => {
    const fetchMock = mockFetch(() =>
      jsonResponse({ accessToken: 'access-2', refreshToken: 'refresh-2', expiresInSeconds: 900 }),
    );

    const result = await authApi.refresh('refresh-1');

    expect(result.accessToken).toBe('access-2');
    expect(fetchMock.calls[0]?.url).toBe('/auth/refresh');
    expect(fetchMock.calls[0]?.body).toEqual({ refreshToken: 'refresh-1' });
  });

  it('revokes the refresh token on logout', async () => {
    const fetchMock = mockFetch(() => noContentResponse());

    await authApi.logout('refresh-1');

    expect(fetchMock.calls[0]?.url).toBe('/auth/logout');
    expect(fetchMock.calls[0]?.method).toBe('POST');
    expect(fetchMock.calls[0]?.body).toEqual({ refreshToken: 'refresh-1' });
  });

  it('sends the access token when it reads the profile', async () => {
    sessionStore.setTokens({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      expiresInSeconds: 900,
    });
    const fetchMock = mockFetch(() =>
      jsonResponse({
        userId: 42,
        name: 'Demo',
        lastName: 'Flashdrop',
        email: 'demo@flashdrop.cl',
        phone: '+56911111111',
        rut: '12.345.678-9',
        photo: null,
        roles: ['CLIENTE'],
      }),
    );

    const profile = await authApi.profile();

    expect(profile.rut).toBe('12.345.678-9');
    expect(fetchMock.calls[0]?.url).toBe('/auth/profile');
    expect(fetchMock.calls[0]?.headers.authorization).toBe('Bearer access-1');
  });
});
