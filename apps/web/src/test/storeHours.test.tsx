import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { RESTAURANTS, PRODUCTS } from './support/catalogMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

const MY_RESTAURANT = {
  id: 1,
  name: 'La Maestranza Burgers',
  address: 'Av. Providencia 1234',
  phone: null,
  image: null,
  open: true,
  acceptingOrders: true,
  hours: [{ day: 1, opensAt: '09:00', closesAt: '18:00' }],
};

describe('store hours', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    signIn({ activeRole: 'RESTAURANTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the state and the saved week of the store', async () => {
    mockFetch(() => jsonResponse(MY_RESTAURANT));
    renderApp('/tienda/horario');

    expect(await screen.findByText('Abierto ahora')).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: 'Recibir pedidos' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Lunes' })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: 'Martes' })).not.toBeChecked();
    expect(screen.getByLabelText('Cierre del Lunes')).toHaveValue('18:00');
  });

  it('stops the orders and shows what the backend confirmed', async () => {
    const fetchMock = mockFetch((call) =>
      call.method === 'PUT'
        ? jsonResponse({ ...MY_RESTAURANT, open: false, acceptingOrders: false })
        : jsonResponse(MY_RESTAURANT),
    );
    renderApp('/tienda/horario');

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Recibir pedidos' }));

    expect(await screen.findByText('Cerrado ahora')).toBeInTheDocument();
    const put = fetchMock.calls.find((call) => call.method === 'PUT');
    expect(put?.url).toBe('/api/catalog/my/restaurant/open-status');
    expect(put?.body).toEqual({ acceptingOrders: false });
  });

  it('keeps the switch as it was when the backend rejects the change', async () => {
    mockFetch((call) =>
      call.method === 'PUT'
        ? jsonResponse({ status: 400, error: 'BAD_REQUEST', message: 'No se pudo.' }, 400)
        : jsonResponse(MY_RESTAURANT),
    );
    renderApp('/tienda/horario');

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Recibir pedidos' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('No se pudo.');
    expect(screen.getByRole('checkbox', { name: 'Recibir pedidos' })).toBeChecked();
  });

  it('saves only the days that attend', async () => {
    const fetchMock = mockFetch((call) =>
      jsonResponse(
        call.method === 'PUT'
          ? { ...MY_RESTAURANT, hours: (call.body as { hours: unknown }).hours }
          : MY_RESTAURANT,
      ),
    );
    renderApp('/tienda/horario');

    await userEvent.click(await screen.findByRole('checkbox', { name: 'Viernes' }));
    fireEvent.change(screen.getByLabelText('Apertura del Viernes'), { target: { value: '22:00' } });
    fireEvent.change(screen.getByLabelText('Cierre del Viernes'), { target: { value: '02:00' } });
    await userEvent.click(screen.getByRole('button', { name: 'Guardar horario' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Horario guardado.');
    const put = fetchMock.calls.find((call) => call.method === 'PUT');
    expect(put?.url).toBe('/api/catalog/my/restaurant/hours');
    expect(put?.body).toEqual({
      hours: [
        { day: 1, opensAt: '09:00', closesAt: '18:00' },
        { day: 5, opensAt: '22:00', closesAt: '02:00' },
      ],
    });
  });

  it('has an entry in the navigation of the store', async () => {
    mockFetch(() => jsonResponse(MY_RESTAURANT));
    renderApp('/tienda/horario');

    expect(await screen.findByRole('link', { name: 'Horario & Apertura' })).toBeInTheDocument();
  });
});

describe('closed store seen by the customer', () => {
  const closedCatalog = (url: string): Response => {
    if (url.startsWith('/catalog/restaurants')) {
      return jsonResponse([{ ...RESTAURANTS[0], open: false }, { ...RESTAURANTS[1], open: true }]);
    }

    return jsonResponse(url.startsWith('/catalog/products') ? PRODUCTS : []);
  };

  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('marks the store in the marketplace and blocks only its dishes', async () => {
    mockFetch((call) => closedCatalog(call.url));
    renderApp('/');

    expect((await screen.findAllByText('Cerrado')).length).toBeGreaterThan(0);
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: `Añadir ${PRODUCTS[0]?.name} al carrito` }),
      ).toBeDisabled(),
    );
    expect(
      screen.getByRole('button', { name: `Añadir ${PRODUCTS[1]?.name} al carrito` }),
    ).toBeEnabled();
  });

  it('explains the closure on the page of the store', async () => {
    mockFetch((call) => closedCatalog(call.url));
    renderApp('/restaurante/1');

    expect(await screen.findByText(/Este local está cerrado/)).toBeInTheDocument();
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: `Añadir ${PRODUCTS[0]?.name} al carrito` }),
      ).toBeDisabled(),
    );
  });

  it('keeps the store open when the catalog does not send the state yet', async () => {
    mockFetch((call) =>
      call.url.startsWith('/catalog/restaurants')
        ? jsonResponse(RESTAURANTS)
        : jsonResponse(call.url.startsWith('/catalog/products') ? PRODUCTS : []),
    );
    renderApp('/restaurante/1');

    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: `Añadir ${PRODUCTS[0]?.name} al carrito` }),
      ).toBeEnabled(),
    );
    expect(screen.queryByText(/Este local está cerrado/)).not.toBeInTheDocument();
  });
});
