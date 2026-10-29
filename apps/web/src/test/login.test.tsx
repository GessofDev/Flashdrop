import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';
import { notifySessionExpired } from '@/session/sessionEvents';

import { jsonResponse, mockFetch } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const AUTH_RESULT = {
  userId: 42,
  name: 'Matías Valenzuela',
  email: 'demo@flashdrop.cl',
  roles: ['CLIENTE', 'RESTAURANTE', 'REPARTIDOR'],
  accessToken: 'access-1',
  refreshToken: 'refresh-1',
  expiresInSeconds: 900,
};

async function typeEmail(): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: 'Correo Electrónico' }));
  await userEvent.type(screen.getByLabelText('Correo electrónico'), 'demo@flashdrop.cl');
  await userEvent.type(screen.getByLabelText('Contraseña'), 'Segura1234');
}

async function submit(): Promise<void> {
  await userEvent.click(screen.getByRole('button', { name: 'Iniciar Sesión en FlashDrop' }));
}

/**
 * Unified login of the app (specs S3 and S5): the tabs, the destination
 * module, the identifier of the account and the identity buttons that the
 * backend does not support yet.
 */
describe('unified login', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('calls the login endpoint with the identifier and the password, and stores the token pair in the session', async () => {
    const fetchMock = mockFetch(() => jsonResponse(AUTH_RESULT));
    renderApp('/login');

    await typeEmail();
    await submit();

    await waitFor(() => expect(fetchMock.calls).toHaveLength(1));
    expect(fetchMock.calls[0]?.url).toBe('/auth/login');
    expect(fetchMock.calls[0]?.method).toBe('POST');
    expect(fetchMock.calls[0]?.body).toEqual({
      login: 'demo@flashdrop.cl',
      password: 'Segura1234',
    });

    await waitFor(() =>
      expect(sessionStore.getTokens()).toEqual({
        accessToken: 'access-1',
        refreshToken: 'refresh-1',
        expiresInSeconds: 900,
      }),
    );
    expect(sessionStore.getSession()?.activeRole).toBe('CLIENTE');
  });

  it('accepts a RUT with or without dots and hyphen, and always sends the canonical form', async () => {
    const fetchMock = mockFetch(() => jsonResponse(AUTH_RESULT));
    renderApp('/login');

    const rut = screen.getByLabelText('RUT');
    fireEvent.change(rut, { target: { value: '16482915k' } });

    expect(rut).toHaveValue('16.482.915-K');

    await userEvent.type(screen.getByLabelText('Contraseña'), 'Segura1234');
    await submit();

    await waitFor(() => expect(fetchMock.calls).toHaveLength(1));
    expect(fetchMock.calls[0]?.body).toEqual({
      login: '16.482.915-K',
      password: 'Segura1234',
    });
  });

  it('shows the literal message of the backend when the credentials are rejected', async () => {
    const fetchMock = mockFetch(() =>
      jsonResponse(
        {
          code: 'AUTH_INVALID_CREDENTIALS',
          service: 'auth-service',
          trace_id: 'trace-1',
          message: 'Credenciales inválidas',
        },
        401,
      ),
    );
    renderApp('/login');

    await typeEmail();
    await submit();

    await waitFor(() => expect(screen.getByRole('alert')).toHaveTextContent('Credenciales inválidas'));
    expect(fetchMock.calls).toHaveLength(1);
    expect(sessionStore.getSession()).toBeNull();
  });

  it.each([
    ['Cliente', '/'],
    ['Mi Tienda', '/tienda/kds'],
    ['Repartidor', '/repartidor'],
  ])('lands on the home of the destination %s after the login', async (destination, home) => {
    const fetchMock = mockFetch(() => jsonResponse(AUTH_RESULT));
    renderApp('/login');

    await userEvent.click(screen.getByRole('button', { name: destination }));
    await typeEmail();
    await submit();

    await waitFor(() =>
      expect(fetchMock.calls.some((call) => call.url === '/auth/login')).toBe(true),
    );
    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe(home));
    expect(sessionStore.getSession()?.activeRole).toBe(
      destination === 'Cliente' ? 'CLIENTE' : destination === 'Mi Tienda' ? 'RESTAURANTE' : 'REPARTIDOR',
    );
  });

  it('does not fire any request when the identity buttons are pressed', async () => {
    const fetchMock = mockFetch(() => jsonResponse({}));
    renderApp('/login');

    const claveUnica = screen.getByRole('button', { name: 'Continuar con ClaveÚnica' });
    const google = screen.getByRole('button', { name: 'Google' });
    const apple = screen.getByRole('button', { name: 'Apple ID' });

    expect(claveUnica).toBeDisabled();
    expect(google).toBeDisabled();
    expect(apple).toBeDisabled();

    fireEvent.click(claveUnica);
    fireEvent.click(google);
    fireEvent.click(apple);

    expect(fetchMock.calls).toHaveLength(0);
    expect(screen.getByText(/todavía no están disponibles/)).toBeInTheDocument();
  });

  it('sends the user to the login route when the session expires', () => {
    signIn({ activeRole: 'CLIENTE' });
    renderApp('/');

    act(() => {
      notifySessionExpired('/login');
    });

    expect(screen.getByTestId('location').textContent).toBe('/login');
  });

  it('does not restore the session after a reload when "Recordar mi sesión" is unchecked', async () => {
    mockFetch(() => jsonResponse(AUTH_RESULT));
    const { unmount } = renderApp('/login');

    await userEvent.click(screen.getByLabelText('Recordar mi sesión en este dispositivo'));
    await typeEmail();
    await submit();

    await waitFor(() => expect(sessionStore.getSession()).not.toBeNull());

    unmount();
    sessionStore.reload();

    expect(sessionStore.getSession()).toBeNull();
    expect(window.localStorage.getItem('flashdrop.session.v1')).toBeNull();
  });

  it('registers the account and asks the user to sign in', async () => {
    const fetchMock = mockFetch(() => jsonResponse({ userId: 42 }, 201));
    renderApp('/login');

    await userEvent.click(screen.getByRole('button', { name: 'Crear Cuenta' }));

    await userEvent.type(screen.getByLabelText('Correo electrónico'), 'nuevo@flashdrop.cl');
    await userEvent.type(screen.getByLabelText('Nombre'), 'Matías');
    await userEvent.type(screen.getByLabelText('Apellido'), 'Valenzuela');
    await userEvent.type(screen.getByLabelText('Teléfono'), '+56911112222');
    await userEvent.type(screen.getByLabelText('Contraseña'), 'Segura1234');

    await userEvent.click(screen.getByRole('button', { name: 'Crear Cuenta en FlashDrop' }));

    await waitFor(() => expect(fetchMock.calls).toHaveLength(1));
    expect(fetchMock.calls[0]?.url).toBe('/auth/register');
    expect(fetchMock.calls[0]?.body).toEqual({
      email: 'nuevo@flashdrop.cl',
      password: 'Segura1234',
      rut: null,
      name: 'Matías',
      lastName: 'Valenzuela',
      phone: '+56911112222',
    });
    expect(screen.getByText('Cuenta creada. Ya puedes iniciar sesión.')).toBeInTheDocument();
  });
});
