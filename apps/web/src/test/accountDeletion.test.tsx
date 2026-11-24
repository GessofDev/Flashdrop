import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { UserProfile } from '@/api';
import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const PROFILE_PATH = '/perfil';
const ACCOUNT_PATH = '/auth/account';
const PHOTO_PATH = '/auth/profile/photo';

const SAMPLE_PROFILE: UserProfile = {
  userId: 101,
  name: 'Carlos',
  lastName: 'Morales Valenzuela',
  email: 'carlos.morales@flashdrop.cl',
  phone: '+56987654321',
  rut: '18.239.402-1',
  photo: null,
  roles: ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
};

/** The literal message of the backend when the password is wrong. */
const WRONG_PASSWORD_MESSAGE = 'Credenciales inválidas';

/** The answer of the backend when the deletion is accepted: 202 with no body. */
function acceptedDeletion(): Response {
  return new Response(null, { status: 202 });
}

/** Answer of the backend for the calls of the profile screen. */
function profileResponse(call: RecordedCall): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname === '/auth/profile' && call.method === 'GET') {
    return jsonResponse(SAMPLE_PROFILE);
  }

  if (pathname === PHOTO_PATH && call.method === 'GET') {
    return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
  }

  return jsonResponse({ status: 404, message: 'No encontrado' }, 404);
}

/** Open the confirmation of the deletion and write the password. */
async function openConfirmation(password: string): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: 'Eliminar mi cuenta' }));
  await userEvent.type(screen.getByLabelText('Contraseña de la cuenta'), password);
}

describe('deletion of the account (spec S9)', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it('asks for the password of the account before it deletes it', async () => {
    const calls: RecordedCall[] = [];

    mockFetch((call) => {
      calls.push(call);

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar mi cuenta' }));

    const passwordInput = screen.getByLabelText('Contraseña de la cuenta');

    expect(passwordInput).toBeRequired();
    expect(passwordInput).toHaveAttribute('type', 'password');

    // Without the password the deletion cannot happen: the confirmation stays
    // closed and the backend is never asked to delete anything.
    const confirmButton = screen.getByRole('button', { name: 'Eliminar cuenta' });

    expect(confirmButton).toBeDisabled();
    expect(calls.filter((call) => call.url === ACCOUNT_PATH)).toHaveLength(0);
  });

  it('sends the deletion with the password that was written', async () => {
    const calls: RecordedCall[] = [];

    mockFetch((call) => {
      calls.push(call);

      if (call.url === ACCOUNT_PATH && call.method === 'DELETE') {
        return acceptedDeletion();
      }

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await openConfirmation('Mi clave 123');

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));

    await waitFor(() => {
      const deletion = calls.find((call) => call.method === 'DELETE' && call.url === ACCOUNT_PATH);

      expect(deletion).toBeDefined();
      expect(deletion?.body).toEqual({ password: 'Mi clave 123' });
      expect(deletion?.headers.authorization).toBe('Bearer access-1');
    });
  });

  it('forgets the session and takes the customer to the login route after the deletion', async () => {
    mockFetch((call) => {
      if (call.url === ACCOUNT_PATH && call.method === 'DELETE') {
        return acceptedDeletion();
      }

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await openConfirmation('Mi clave 123');

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));

    await waitFor(() => {
      expect(sessionStore.getSession()).toBeNull();
    });

    await waitFor(() => {
      expect(screen.getByTestId('location')).toHaveTextContent('/login');
    });
  });

  it('shows the literal message of the backend and keeps the account when the password is wrong', async () => {
    mockFetch((call) => {
      if (call.url === ACCOUNT_PATH && call.method === 'DELETE') {
        return jsonResponse(
          {
            code: 'AUTH_INVALID_CREDENTIALS',
            service: 'auth-service',
            trace_id: 'c4d5',
            message: WRONG_PASSWORD_MESSAGE,
          },
          401,
        );
      }

      return profileResponse(call);
    });

    renderApp(PROFILE_PATH);

    await screen.findByRole('heading', { level: 1, name: /Carlos Morales Valenzuela/i });

    await openConfirmation('clave incorrecta');

    await userEvent.click(screen.getByRole('button', { name: 'Eliminar cuenta' }));

    expect(await screen.findByText(WRONG_PASSWORD_MESSAGE)).toBeInTheDocument();

    // The account stays: the session is alive and the customer is still on
    // the profile screen.
    expect(sessionStore.getSession()).not.toBeNull();
    expect(screen.getByTestId('location')).toHaveTextContent(PROFILE_PATH);
  });
});
