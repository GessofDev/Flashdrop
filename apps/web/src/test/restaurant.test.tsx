import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { catalogResponse } from './support/catalogMock';
import { mockFetch } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Selection of a restaurant by the client: the customer picks a local from
 * the marketplace, sees its dishes and adds them to the cart.
 */
describe('restaurant of the client', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('links every restaurant of the marketplace to its own page', async () => {
    signIn({ activeRole: 'CLIENTE' });
    mockFetch(catalogResponse);
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    expect(within(restaurants).getByRole('link', { name: /La Maestranza Burgers/ })).toHaveAttribute(
      'href',
      '/restaurante/1',
    );
    expect(within(restaurants).getByRole('link', { name: /Sakura Nikkei Fusion/ })).toHaveAttribute(
      'href',
      '/restaurante/2',
    );
  });

  it('opens the restaurant and asks the catalog only for its dishes', async () => {
    signIn({ activeRole: 'CLIENTE' });
    const fetchMock = mockFetch(catalogResponse);
    renderApp('/restaurante/1');

    expect(await screen.findByRole('heading', { name: 'La Maestranza Burgers' })).toBeInTheDocument();
    expect(screen.getByText('Av. Providencia 1234, Providencia, Santiago')).toBeInTheDocument();

    const productsCall = fetchMock.calls.find((call) => call.url.includes('/catalog/products'));
    expect(productsCall?.url).toContain('restaurantId=1');

    expect(screen.getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
    expect(screen.getByText('Papas Rústicas Trufadas')).toBeInTheDocument();
    expect(screen.queryByText('Pizza Napolitana Da Luigi')).toBeNull();
  });

  it('adds a dish of the restaurant to the cart', async () => {
    signIn({ activeRole: 'CLIENTE' });
    mockFetch(catalogResponse);
    renderApp('/restaurante/1');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Añadir Doble Smash Cheddar Bacon al carrito' }),
    );

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });
    expect(within(cart).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
  });

  it('sends the customer back to the marketplace', async () => {
    signIn({ activeRole: 'CLIENTE' });
    mockFetch(catalogResponse);
    renderApp('/restaurante/1');

    const back = await screen.findByRole('link', { name: /volver al marketplace/i });
    expect(back).toHaveAttribute('href', '/');
    expect(screen.queryByText('Página no encontrada')).toBeNull();
  });

  it('keeps the route inside the area of the client', async () => {
    signIn({ activeRole: 'RESTAURANTE', roles: ['CLIENTE', 'RESTAURANTE'] });
    mockFetch(catalogResponse);
    renderApp('/restaurante/1');

    expect(screen.getByTestId('location').textContent).toBe('/tienda/kds');
  });

  it('warns when the cart mixes two locals', async () => {
    signIn({ activeRole: 'CLIENTE' });
    mockFetch(catalogResponse);
    renderApp('/');

    await userEvent.click(
      await screen.findByRole('button', { name: 'Añadir Doble Smash Cheddar Bacon al carrito' }),
    );
    await userEvent.click(screen.getByRole('link', { name: /Sakura Nikkei Fusion/ }));
    await userEvent.click(
      await screen.findByRole('button', { name: 'Añadir Pizza Napolitana Da Luigi al carrito' }),
    );

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });
    expect(within(cart).getByText(/un solo local/i)).toBeInTheDocument();
  });
});
