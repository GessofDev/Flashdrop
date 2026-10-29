import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch, noContentResponse } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Canonical shell of the app (spec S5): the top bar of five zones, the role
 * switcher, the navigation of the active role and the guard of session and
 * role.
 */
describe('canonical shell', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('mounts the top bar and the navigation once, in the layout of the routes', () => {
    signIn({ activeRole: 'CLIENTE' });
    renderApp('/');

    expect(screen.getAllByRole('banner')).toHaveLength(1);
    expect(screen.getAllByRole('navigation')).toHaveLength(1);
    expect(screen.getByRole('group', { name: 'Rol activo' })).toBeInTheDocument();
    expect(screen.getByText('CHILE OPS')).toBeInTheDocument();
  });

  it('changes the navigation and the route when the role changes, without refetching the session', async () => {
    signIn({ activeRole: 'CLIENTE' });
    const fetchMock = mockFetch(() => jsonResponse({}));
    renderApp('/');

    expect(screen.getByRole('link', { name: 'Marketplace & Consumo' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'KDS Pantalla Cocina' })).toBeNull();

    await userEvent.click(screen.getByRole('button', { name: 'Mi Tienda' }));

    expect(screen.getByRole('link', { name: 'KDS Pantalla Cocina' })).toBeInTheDocument();
    expect(screen.queryByRole('link', { name: 'Marketplace & Consumo' })).toBeNull();
    expect(screen.getByTestId('location').textContent).toBe('/tienda/kds');
    expect(screen.getByRole('button', { name: 'Mi Tienda' })).toHaveAttribute('aria-pressed', 'true');

    // The switcher never talks to the backend and never asks for credentials.
    expect(fetchMock.calls.filter((call) => call.url.startsWith('/auth'))).toHaveLength(0);
    expect(screen.queryByLabelText('Contraseña')).toBeNull();
  });

  it('keeps the active role after a reload', async () => {
    signIn({ activeRole: 'CLIENTE' });
    const { unmount } = renderApp('/');

    await userEvent.click(screen.getByRole('button', { name: 'Repartidor' }));

    expect(screen.getByTestId('location').textContent).toBe('/repartidor');

    unmount();
    sessionStore.reload();

    renderApp('/repartidor');

    expect(screen.getByRole('button', { name: 'Repartidor' })).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByRole('link', { name: 'Ruta Activa' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Pedidos Disponibles (FIFO)' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Historial de Entregas' })).toBeInTheDocument();
  });

  it('redirects a route of another role to the home of the active role', () => {
    signIn({ activeRole: 'CLIENTE' });
    renderApp('/tienda/kds');

    expect(screen.getByTestId('location').textContent).toBe('/');
    expect(screen.getByRole('link', { name: 'Marketplace & Consumo' })).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Página no encontrada' })).toBeNull();
  });

  it('redirects a protected route to /login when there is no session', () => {
    renderApp('/pedidos');

    expect(screen.getByTestId('location').textContent).toBe('/login');
    expect(screen.getByRole('button', { name: 'Iniciar Sesión en FlashDrop' })).toBeInTheDocument();
  });

  it('shows the five zones of the top bar with the context of the active role', () => {
    signIn({ activeRole: 'RESTAURANTE' });
    renderApp('/tienda/kds');

    expect(screen.getByText('Local')).toBeInTheDocument();
    expect(screen.getByText('Proveedor')).toBeInTheDocument();
    expect(screen.getByText('La Maestranza - Providencia')).toBeInTheDocument();
    expect(screen.getByText('En Línea • KDS Activo')).toBeInTheDocument();
    expect(screen.getByText('Matías Valenzuela')).toBeInTheDocument();
  });

  it('shows the telemetry of the courier in the top bar', () => {
    signIn({ activeRole: 'REPARTIDOR' });
    renderApp('/repartidor');

    expect(screen.getByText('En Servicio • Activo (Moto)')).toBeInTheDocument();
  });

  it('shows the institutional footer for the customer and the store, and the console footer for the courier', () => {
    signIn({ activeRole: 'CLIENTE' });
    const { unmount } = renderApp('/');

    expect(screen.getByText('Cobertura FlashDrop Chile:')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Descarga la App' })).toBeInTheDocument();

    unmount();
    signIn({ activeRole: 'REPARTIDOR' });
    renderApp('/repartidor');

    expect(screen.queryByText('Cobertura FlashDrop Chile:')).toBeNull();
    expect(screen.getByText('Consola del repartidor')).toBeInTheDocument();
  });

  it('revokes the refresh token and returns to the login when the user leaves', async () => {
    signIn({ activeRole: 'CLIENTE' });
    const fetchMock = mockFetch(() => noContentResponse());
    renderApp('/');

    await userEvent.click(screen.getByRole('button', { name: 'Salir' }));

    await waitFor(() =>
      expect(fetchMock.calls.some((call) => call.url === '/auth/logout')).toBe(true),
    );
    expect(fetchMock.calls.find((call) => call.url === '/auth/logout')?.body).toEqual({
      refreshToken: 'refresh-1',
    });

    await waitFor(() => expect(screen.getByTestId('location').textContent).toBe('/login'));
    expect(sessionStore.getSession()).toBeNull();
  });
});
