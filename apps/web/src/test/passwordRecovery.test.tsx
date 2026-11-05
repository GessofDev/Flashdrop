import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch, noContentResponse } from './support/fetchMock';
import { renderApp } from './support/renderApp';

/** Generated per run: meets the policy without keeping a credential in the source. */
function validPassword(): string {
  return `Aa1${crypto.randomUUID()}`;
}

async function typeNewPassword(password: string, confirmation = password): Promise<void> {
  await userEvent.type(screen.getByLabelText('Nueva contraseña'), password);
  await userEvent.type(screen.getByLabelText('Repite la contraseña'), confirmation);
  await userEvent.click(screen.getByRole('button', { name: 'Guardar contraseña' }));
}

/** Password recovery: asking for the link and using it. */
describe('password recovery', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('opens from the login without a session', async () => {
    renderApp('/login');

    await userEvent.click(screen.getByRole('link', { name: '¿Olvidaste tu contraseña?' }));

    expect(await screen.findByRole('heading', { name: 'Recupera tu contraseña' })).toBeInTheDocument();
    expect(screen.getByTestId('location')).toHaveTextContent('/recuperar-clave');
  });

  it('asks for the link and answers the same whether or not the account exists', async () => {
    const fetchMock = mockFetch(() => new Response(null, { status: 202 }));
    renderApp('/recuperar-clave');

    await userEvent.type(screen.getByLabelText('Correo electrónico'), ' demo@flashdrop.cl ');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Si el correo está registrado');
    expect(fetchMock.calls[0]?.url).toBe('/auth/password/forgot');
    expect(fetchMock.calls[0]?.body).toEqual({ email: 'demo@flashdrop.cl' });
    expect(fetchMock.calls[0]?.headers.authorization).toBeUndefined();
  });

  it('shows the rate limit message of the backend', async () => {
    mockFetch(() =>
      jsonResponse({ code: 'AUTH_RATE_LIMITED', message: 'Demasiados intentos.' }, 429),
    );
    renderApp('/recuperar-clave');

    await userEvent.type(screen.getByLabelText('Correo electrónico'), 'demo@flashdrop.cl');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar enlace' }));

    expect(await screen.findByRole('alert')).toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });

  it('sends the token of the link with the new password', async () => {
    const fetchMock = mockFetch(() => noContentResponse());
    const password = validPassword();
    renderApp('/recuperar-clave#token=abc123');

    expect(await screen.findByRole('heading', { name: 'Crea tu nueva contraseña' })).toBeInTheDocument();
    await typeNewPassword(password);

    expect(await screen.findByRole('status')).toHaveTextContent('Tu contraseña se actualizó');
    expect(fetchMock.calls[0]?.url).toBe('/auth/password/reset');
    expect(fetchMock.calls[0]?.body).toEqual({ token: 'abc123', newPassword: password });
  });

  it('does not spend the link on a weak password', async () => {
    const fetchMock = mockFetch(() => noContentResponse());
    renderApp('/recuperar-clave#token=abc123');

    await typeNewPassword('corta');

    expect(await screen.findByRole('alert')).toHaveTextContent('al menos 10 caracteres');
    expect(fetchMock.calls).toHaveLength(0);
  });

  it('does not spend the link when the passwords differ', async () => {
    const fetchMock = mockFetch(() => noContentResponse());
    renderApp('/recuperar-clave#token=abc123');

    await typeNewPassword(validPassword(), validPassword());

    expect(await screen.findByRole('alert')).toHaveTextContent('no coinciden');
    expect(fetchMock.calls).toHaveLength(0);
  });

  it('offers a new link when the backend rejects the token', async () => {
    mockFetch(() =>
      jsonResponse(
        {
          code: 'AUTH_RESET_TOKEN_INVALID',
          message: 'El enlace no es válido o ya venció. Solicita uno nuevo.',
        },
        400,
      ),
    );
    renderApp('/recuperar-clave#token=vencido');

    await typeNewPassword(validPassword());

    expect(await screen.findByRole('alert')).toHaveTextContent('ya venció');
    await userEvent.click(screen.getByRole('button', { name: 'Solicitar un enlace nuevo' }));

    await waitFor(() =>
      expect(screen.getByRole('heading', { name: 'Recupera tu contraseña' })).toBeInTheDocument(),
    );
  });
});
