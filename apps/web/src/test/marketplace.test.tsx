import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { catalogResponse } from './support/catalogMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Marketplace of the customer (spec S8): hero, chips of category, grid of
 * restaurants and popular dishes, with the states of loading, empty and error.
 */
describe('customer marketplace', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders the restaurants and the dishes with the shape of the catalog endpoints', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );
    expect(
      within(restaurants).getByText('Av. Providencia 1234, Providencia, Santiago'),
    ).toBeInTheDocument();
    expect(within(restaurants).getByText('Sakura Nikkei Fusion')).toBeInTheDocument();
    expect(
      within(restaurants).getByText('Tobalaba 4321, Las Condes, Santiago'),
    ).toBeInTheDocument();

    const products = screen.getByRole('region', { name: 'Platos Populares' });

    await waitFor(() =>
      expect(within(products).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument(),
    );
    expect(
      within(products).getByText('Dos hamburguesas de 110g con cheddar fundido y tocino crujiente.'),
    ).toBeInTheDocument();
    expect(within(products).getByText('$7.990')).toBeInTheDocument();
    expect(within(products).getByText('Papas Rústicas Trufadas')).toBeInTheDocument();
    expect(within(products).getByText('$3.490')).toBeInTheDocument();
  });

  it('does not render the dishes the local has withdrawn', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    const products = await screen.findByRole('region', { name: 'Platos Populares' });

    await waitFor(() =>
      expect(within(products).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument(),
    );
    expect(within(products).queryByText('Hamburguesa Clásica')).toBeNull();
  });

  it('filters the dishes by category, changing the query of the request and the rendered list', async () => {
    const fetchMock = mockFetch(catalogResponse);
    renderApp('/');

    await screen.findByText('Doble Smash Cheddar Bacon');

    await userEvent.click(screen.getByRole('button', { name: 'Hamburguesas' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) =>
            call.url.startsWith('/catalog/products') && call.url.includes('categoryId=3'),
        ),
      ).toBe(true),
    );

    const products = screen.getByRole('region', { name: 'Platos Populares' });

    await waitFor(() =>
      expect(within(products).queryByText('Pizza Napolitana Da Luigi')).toBeNull(),
    );
    expect(within(products).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
  });

  it('asks for every dish when the "Todas" chip is selected', async () => {
    const fetchMock = mockFetch(catalogResponse);
    renderApp('/');

    await screen.findByText('Pizza Napolitana Da Luigi');

    await userEvent.click(screen.getByRole('button', { name: 'Hamburguesas' }));
    await waitFor(() =>
      expect(
        fetchMock.calls.some((call) => call.url.includes('categoryId=3')),
      ).toBe(true),
    );

    await userEvent.click(screen.getByRole('button', { name: 'Todas' }));

    await waitFor(() =>
      expect(
        within(screen.getByRole('region', { name: 'Platos Populares' })).getByText(
          'Pizza Napolitana Da Luigi',
        ),
      ).toBeInTheDocument(),
    );
  });

  it('paints a placeholder instead of a broken image when the catalog has no picture', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );
    expect(within(restaurants).getAllByText('Sin imagen').length).toBeGreaterThan(0);
    expect(within(restaurants).getByRole('img', { name: 'Sakura Nikkei Fusion' })).toBeInTheDocument();
  });

  it('shows the skeleton while the catalog answers', async () => {
    mockFetch(() => new Promise<Response>(() => undefined));
    renderApp('/');

    expect(await screen.findByRole('status', { name: 'Cargando restaurantes' })).toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Cargando platos' })).toBeInTheDocument();
  });

  it('shows the empty state when the catalog has no restaurants', async () => {
    mockFetch((call) =>
      call.url.startsWith('/catalog/restaurants')
        ? jsonResponse([])
        : catalogResponse(call),
    );
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    await waitFor(() =>
      expect(within(restaurants).getByText('Todavía no hay locales disponibles')).toBeInTheDocument(),
    );
  });

  it('shows the literal message of the backend when the catalog fails', async () => {
    mockFetch((call) =>
      call.url.startsWith('/catalog/products')
        ? jsonResponse(
            {
              status: 500,
              error: 'INTERNAL_ERROR',
              message: 'No fue posible leer el catálogo de platos',
            },
            500,
          )
        : catalogResponse(call),
    );
    renderApp('/');

    const products = await screen.findByRole('region', { name: 'Platos Populares' });

    await waitFor(() =>
      expect(within(products).getByRole('alert')).toHaveTextContent(
        'No fue posible leer el catálogo de platos',
      ),
    );
  });

  it('never renders ratings, delivery times, shipping costs or the coupon of the day', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    await screen.findByText('Doble Smash Cheddar Bacon');

    const rendered = document.body.textContent ?? '';

    expect(rendered).not.toMatch(/★/);
    expect(rendered).not.toMatch(/FLASH30/i);
    expect(rendered).not.toMatch(/cup[oó]n/i);
    expect(rendered).not.toMatch(/env[ií]o/i);
    expect(rendered).not.toMatch(/rating/i);
    expect(rendered).not.toMatch(/rese[ñn]/i);
    expect(rendered).not.toMatch(/\d+\s*-\s*\d+\s*min/i);
    expect(rendered).not.toMatch(/FlashHour/i);
  });
});
