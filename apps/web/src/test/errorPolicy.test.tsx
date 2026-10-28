import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { act } from 'react';
import { MemoryRouter, useLocation } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError, resolveApiErrorOutcome } from '@/api';
import { SessionRedirectBridge } from '@/app/SessionRedirectBridge';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { notifySessionExpired } from '@/session/sessionEvents';
import { sessionStore } from '@/session/tokenStore';

function LocationProbe() {
  const location = useLocation();

  return <p data-testid="location">{location.pathname}</p>;
}

describe('error policy', () => {
  beforeEach(() => {
    sessionStore.reset();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('redirects to the login route on a 401', () => {
    const outcome = resolveApiErrorOutcome(new ApiError('No autenticado', { status: 401 }));

    expect(outcome).toEqual({ kind: 'redirect-to-login', path: '/login' });
  });

  it('reports a wrong role view on a 403, with the backend message', () => {
    const outcome = resolveApiErrorOutcome(
      new ApiError('No tienes permiso para esta vista de pedidos', { status: 403 }),
    );

    expect(outcome).toEqual({
      kind: 'wrong-role',
      message: 'No tienes permiso para esta vista de pedidos',
    });
  });

  it('reports a retryable conflict on a 409, with the backend message', () => {
    const outcome = resolveApiErrorOutcome(
      new ApiError('El pedido debe contener productos de un mismo local', { status: 409 }),
    );

    expect(outcome).toEqual({
      kind: 'conflict',
      message: 'El pedido debe contener productos de un mismo local',
      canRetry: true,
    });
  });

  it('keeps the backend message of any other status', () => {
    const outcome = resolveApiErrorOutcome(
      new ApiError('Pedido no encontrado', { status: 404 }),
    );

    expect(outcome).toEqual({ kind: 'not-found', message: 'Pedido no encontrado' });
  });
});

describe('session redirect bridge', () => {
  beforeEach(() => {
    sessionStore.reset();
  });

  it('navigates to /login when the session expires', async () => {
    render(
      <MemoryRouter initialEntries={['/pedidos']}>
        <SessionRedirectBridge />
        <LocationProbe />
      </MemoryRouter>,
    );

    expect(screen.getByTestId('location')).toHaveTextContent('/pedidos');

    act(() => {
      notifySessionExpired('/login');
    });

    expect(screen.getByTestId('location')).toHaveTextContent('/login');
  });
});

describe('ApiErrorNotice', () => {
  it('shows the backend message of a conflict and offers the retry', async () => {
    const onRetry = vi.fn();

    render(
      <ApiErrorNotice
        error={new ApiError('El pedido debe contener productos de un mismo local', { status: 409 })}
        onRetry={onRetry}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'El pedido debe contener productos de un mismo local',
    );

    await userEvent.click(screen.getByRole('button', { name: 'Reintentar' }));

    expect(onRetry).toHaveBeenCalledTimes(1);
  });

  it('shows the backend message of a forbidden error without a retry', () => {
    render(
      <ApiErrorNotice
        error={new ApiError('Solo los repartidores pueden ver pedidos disponibles', { status: 403 })}
      />,
    );

    expect(screen.getByRole('alert')).toHaveTextContent(
      'Solo los repartidores pueden ver pedidos disponibles',
    );
    expect(screen.queryByRole('button', { name: 'Reintentar' })).toBeNull();
  });

  it('renders nothing when the app is being redirected to the login route', () => {
    const { container } = render(
      <ApiErrorNotice error={new ApiError('No autenticado', { status: 401 })} />,
    );

    expect(container).toBeEmptyDOMElement();
  });
});
