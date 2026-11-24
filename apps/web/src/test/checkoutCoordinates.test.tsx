import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { catalogResponse } from './support/catalogMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/** Answer of `POST /api/orders`: the envelope of `orders-service`. */
function orderCreated(total: number): Response {
  return jsonResponse({
    success: true,
    message: 'Pedido creado',
    data: { id: '00000000-0000-0000-0000-00000000002a', total },
  });
}

/** Answer of `GET /api/orders/{id}`: the detail carries the code of the order. */
function orderDetail(code: string): Response {
  return jsonResponse({
    success: true,
    message: 'Pedido encontrado',
    data: {
      id: '00000000-0000-0000-0000-00000000002a',
      code,
      status: 'Nuevo pedido',
      total: 13980,
    },
  });
}

/** Answer of `POST /api/orders/delivery-quote`: the quote of `orders-service`. */
function quoteAnswer(): Response {
  return jsonResponse({
    success: true,
    message: 'Cotización calculada',
    data: {
      subtotal: 7990,
      deliveryFee: 2500,
      total: 10490,
      defaultFee: false,
      vehicle: 'MOTO',
      dimension: 1,
      distanceKm: 1.11,
      dimensionCost: 1000,
      distanceCost: 1500,
    },
  });
}

/** Answer of the geocoder: the first result of the address typed. */
function geocodeAnswer(): Response {
  return jsonResponse([
    {
      lat: '-33.4500',
      lon: '-70.6600',
      display_name: 'Av. Pedro de Valdivia 1234, Providencia, Santiago',
    },
  ]);
}

/**
 * Answers the calls of the checkout: the quote, the created order, the detail
 * that carries its code, the geocoder and the catalog of the marketplace.
 */
function checkoutResponder(
  options: { quote?: Response; created?: Response; geocode?: Response } = {},
): (call: RecordedCall) => Response {
  return (call: RecordedCall) => {
    const { pathname } = new URL(call.url, 'http://flashdrop.test');

    if (pathname === '/api/orders/delivery-quote') {
      return options.quote ?? quoteAnswer();
    }

    if (call.method === 'POST' && pathname === '/api/orders') {
      return options.created ?? orderCreated(13980);
    }

    if (call.method === 'GET' && pathname.startsWith('/api/orders/')) {
      return orderDetail('FD-42');
    }

    if (pathname === '/search') {
      return options.geocode ?? geocodeAnswer();
    }

    return catalogResponse(call);
  };
}

/** Install a browser geolocation that answers with the given position. */
function stubBrowserLocation(latitude: number, longitude: number): void {
  Object.defineProperty(window.navigator, 'geolocation', {
    configurable: true,
    value: {
      getCurrentPosition: (success: PositionCallback): void => {
        success({
          coords: { latitude, longitude },
        } as unknown as GeolocationPosition);
      },
    },
  });
}

/** Leave the browser without the geolocation API, as an old browser does. */
function removeBrowserLocation(): void {
  Reflect.deleteProperty(window.navigator, 'geolocation');
}

/**
 * Checkout of the customer with the destination coordinates (spec S5): the
 * location of the browser, the geocoding of the address typed and the quote
 * of the backend.
 */
describe('checkout with the destination coordinates', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    removeBrowserLocation();
  });

  async function addProduct(name: string): Promise<void> {
    await userEvent.click(
      await screen.findByRole('button', { name: `Añadir ${name} al carrito` }),
    );
  }

  async function resolveBrowserLocation(): Promise<void> {
    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.click(within(cart).getByRole('button', { name: 'Usar mi ubicación' }));

    await waitFor(() =>
      expect(within(cart).getByText(/Ubicación confirmada/)).toBeInTheDocument(),
    );
  }

  it('creates the order with the coordinates of the browser', async () => {
    stubBrowserLocation(-33.44, -70.66);

    const fetchMock = mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await resolveBrowserLocation();

    expect(within(cart).getByLabelText('Distancia estimada (km)')).toBeDisabled();

    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some((call) => call.method === 'POST' && call.url === '/api/orders'),
      ).toBe(true),
    );

    const orderCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/orders',
    );

    expect(orderCall?.body).toEqual({
      address: 'Av. Pedro de Valdivia 1234',
      payment_method: 'Efectivo',
      latitude: -33.44,
      longitude: -70.66,
      items: [{ product_id: 10, quantity: 1 }],
    });
  });

  it('creates the order with the geocoded coordinates when the browser has no location', async () => {
    removeBrowserLocation();

    const fetchMock = mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234, Providencia',
    );

    await userEvent.click(within(cart).getByRole('button', { name: 'Usar mi ubicación' }));

    await waitFor(() =>
      expect(within(cart).getByText(/no permite obtener tu ubicación/)).toBeInTheDocument(),
    );

    await userEvent.click(within(cart).getByRole('button', { name: 'Buscar dirección' }));

    await waitFor(() =>
      expect(within(cart).getByText(/Ubicación confirmada/)).toBeInTheDocument(),
    );

    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some((call) => call.method === 'POST' && call.url === '/api/orders'),
      ).toBe(true),
    );

    const orderCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/orders',
    );

    expect(orderCall?.body).toEqual({
      address: 'Av. Pedro de Valdivia 1234, Providencia',
      payment_method: 'Efectivo',
      latitude: -33.45,
      longitude: -70.66,
      items: [{ product_id: 10, quantity: 1 }],
    });
  });

  it('shows the breakdown of the quote of the backend', async () => {
    stubBrowserLocation(-33.44, -70.66);

    const fetchMock = mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    await resolveBrowserLocation();

    const quote = await screen.findByRole('region', { name: 'Cotización del envío' });

    expect(within(quote).getByText('Subtotal')).toBeInTheDocument();
    expect(within(quote).getByText('$7.990')).toBeInTheDocument();
    expect(within(quote).getByText('Envío')).toBeInTheDocument();
    expect(within(quote).getByText('$2.500')).toBeInTheDocument();
    expect(within(quote).getByText('Total')).toBeInTheDocument();
    expect(within(quote).getByText('$10.490')).toBeInTheDocument();

    const quoteCall = fetchMock.calls.find((call) => call.url === '/api/orders/delivery-quote');

    expect(quoteCall?.method).toBe('POST');
    expect(quoteCall?.body).toEqual({
      items: [{ product_id: 10, quantity: 1 }],
      latitude: -33.44,
      longitude: -70.66,
    });
  });

  it('shows the literal message of the backend when the quote fails', async () => {
    stubBrowserLocation(-33.44, -70.66);

    mockFetch(
      checkoutResponder({
        quote: jsonResponse(
          {
            status: 400,
            error: 'BAD_REQUEST',
            message:
              'El local no tiene ubicacion configurada: no se puede calcular el envio',
          },
          400,
        ),
      }),
    );
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await resolveBrowserLocation();

    await waitFor(() =>
      expect(
        within(cart).getByText(
          'El local no tiene ubicacion configurada: no se puede calcular el envio',
        ),
      ).toBeInTheDocument(),
    );
    expect(within(cart).queryByRole('region', { name: 'Cotización del envío' })).toBeNull();
  });

  it('warns when the address changes after the location was confirmed', async () => {
    stubBrowserLocation(-33.44, -70.66);

    mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await resolveBrowserLocation();

    expect(
      within(cart).queryByText(/Cambiaste la dirección después de confirmar/),
    ).toBeNull();

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      ', Depto 42',
    );

    expect(
      await within(cart).findByText(/Cambiaste la dirección después de confirmar/),
    ).toBeInTheDocument();
  });

  it('creates the order without coordinates when the customer resolves no location', async () => {
    const fetchMock = mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some((call) => call.method === 'POST' && call.url === '/api/orders'),
      ).toBe(true),
    );

    const orderCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/orders',
    );

    expect(orderCall?.body).toEqual({
      address: 'Av. Pedro de Valdivia 1234',
      payment_method: 'Efectivo',
      items: [{ product_id: 10, quantity: 1 }],
    });
    expect(orderCall?.body).not.toHaveProperty('latitude');
    expect(orderCall?.body).not.toHaveProperty('longitude');
    expect(
      fetchMock.calls.some((call) => call.url === '/api/orders/delivery-quote'),
    ).toBe(false);
  });
});
