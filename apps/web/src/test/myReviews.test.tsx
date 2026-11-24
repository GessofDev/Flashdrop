import { screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '@/api';
import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const SAMPLE_PROFILE: UserProfile = {
  userId: 101,
  name: 'Carlos',
  lastName: 'Morales Valenzuela',
  email: 'carlos.morales@flashdrop.cl',
  phone: '+56987654321',
  rut: '18.239.402-1',
  photo: null,
  roles: ['CLIENTE'],
};

/**
 * Customer review history in Profile (T11, spec S12):
 * lists the reviews written by the signed-in user from `GET /api/users/me/reviews`.
 */
describe('my reviews in profile', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the reviews written by the user from GET /api/users/me/reviews', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/auth/profile') {
        return jsonResponse(SAMPLE_PROFILE);
      }

      if (pathname === '/api/users/me/reviews') {
        return jsonResponse({
          items: [
            {
              id: 501,
              authorUserId: 101,
              rating: 5,
              comment: 'Excelente atención y entrega rápida',
              createdAt: '2026-11-20T18:30:00Z',
            },
            {
              id: 502,
              authorUserId: 101,
              rating: 4,
              comment: null,
              createdAt: '2026-11-21T19:00:00Z',
            },
          ],
          page: 0,
          size: 20,
          totalElements: 2,
          totalPages: 1,
        });
      }

      return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
    });

    renderApp('/perfil');

    const section = await screen.findByRole('region', { name: 'Mis reseñas' });

    expect(
      await within(section).findByText('Excelente atención y entrega rápida'),
    ).toBeInTheDocument();
    expect(within(section).getAllByRole('listitem')).toHaveLength(2);
  });

  it('shows an empty message when the user has not written any reviews', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/auth/profile') {
        return jsonResponse(SAMPLE_PROFILE);
      }

      if (pathname === '/api/users/me/reviews') {
        return jsonResponse({
          items: [],
          page: 0,
          size: 20,
          totalElements: 0,
          totalPages: 0,
        });
      }

      return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
    });

    renderApp('/perfil');

    const section = await screen.findByRole('region', { name: 'Mis reseñas' });
    expect(
      await within(section).findByText('Todavía no escribiste reseñas.'),
    ).toBeInTheDocument();
  });

  it('shows the backend error message when GET /api/users/me/reviews fails with 500', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/auth/profile') {
        return jsonResponse(SAMPLE_PROFILE);
      }

      if (pathname === '/api/users/me/reviews') {
        return jsonResponse(
          { status: 500, error: 'INTERNAL_ERROR', message: 'Error al consultar tus reseñas' },
          500,
        );
      }

      return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
    });

    renderApp('/perfil');

    const section = await screen.findByRole('region', { name: 'Mis reseñas' });
    expect(await within(section).findByRole('alert')).toHaveTextContent(
      'Error al consultar tus reseñas',
    );
  });
});
