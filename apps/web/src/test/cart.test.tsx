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

/** Answer of `POST /api/orders` when the rule of the backend rejects the order. */
function orderRejected(message: string): Response {
  return jsonResponse({ status: 400, error: 'BAD_REQUEST', message }, 400);
}

/**
 * Answer of `GET /api/orders/{id}`: the envelope of `orders-service`.
 *
 * The detail carries the code of the order, which the create endpoint does
 * not answer with.
 */
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

/**
 * Answers the calls of the checkout: the created order, the detail that
 * carries its code and the catalog of the marketplace.
 */
function checkoutResponder(
  options: { created?: Response; detail?: Response } = {},
): (call: RecordedCall) => Response {
  return (call: RecordedCall) => {
    if (call.method === 'POST' && call.url.startsWith('/api/orders')) {
      return options.created ?? orderCreated(13980);
    }

    if (call.method === 'GET' && call.url.startsWith('/api/orders/')) {
      return options.detail ?? orderDetail('FD-42');
    }

    return catalogResponse(call);
  };
}

/**
 * Cart and declarative checkout of the customer (spec S9): local cart,
 * `POST /api/orders` and the total of the backend.
 */
describe('cart and checkout', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  async function addProduct(name: string): Promise<void> {
    await userEvent.click(
      await screen.findByRole('button', { name: `Añadir ${name} al carrito` }),
    );
  }

  it('reflects the quantities and the subtotal of the cart', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    expect(within(cart).getByText('Doble Smash Cheddar Bacon')).toBeInTheDocument();
    expect(within(cart).getByText('$7.990')).toBeInTheDocument();
    expect(within(cart).getByText('Subtotal')).toBeInTheDocument();

    await userEvent.click(
      within(cart).getByRole('button', { name: 'Aumentar cantidad de Doble Smash Cheddar Bacon' }),
    );

    expect(within(cart).getByText('$15.980')).toBeInTheDocument();

    const quantity = within(cart).getByRole('group', {
      name: 'Cantidad de Doble Smash Cheddar Bacon',
    });

    expect(within(quantity).getByText('2')).toBeInTheDocument();

    await userEvent.click(
      within(cart).getByRole('button', { name: 'Disminuir cantidad de Doble Smash Cheddar Bacon' }),
    );

    expect(within(cart).getByText('$7.990')).toBeInTheDocument();
  });

  it('removes a dish from the cart', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.click(
      within(cart).getByRole('button', { name: 'Quitar Doble Smash Cheddar Bacon del carrito' }),
    );

    expect(within(cart).queryByText('Doble Smash Cheddar Bacon')).toBeNull();
    expect(within(cart).getByText('Tu carrito está vacío')).toBeInTheDocument();
  });

  it('shows the literal message of the backend when the cart mixes two restaurants', async () => {
    const fetchMock = mockFetch(
      checkoutResponder({
        created: orderRejected('El pedido debe contener productos de un mismo local'),
      }),
    );
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');
    await addProduct('Pizza Napolitana Da Luigi');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(within(cart).getByRole('alert')).toHaveTextContent(
        'El pedido debe contener productos de un mismo local',
      ),
    );

    const orderCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url.startsWith('/api/orders'),
    );

    expect(orderCall?.method).toBe('POST');
    expect(orderCall?.body).toEqual({
      address: 'Av. Pedro de Valdivia 1234',
      payment_method: 'Efectivo',
      items: [
        { product_id: 10, quantity: 1 },
        { product_id: 11, quantity: 1 },
      ],
    });
  });

  it('shows the literal message of the backend when the cart is empty', async () => {
    const fetchMock = mockFetch(catalogResponse);
    renderApp('/');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(within(cart).getByRole('alert')).toHaveTextContent(
        'El pedido debe contener al menos un producto',
      ),
    );
    expect(fetchMock.calls.some((call) => call.url.startsWith('/api/orders'))).toBe(false);
  });

  it('sends the declared checkout data to the backend', async () => {
    const fetchMock = mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');
    await addProduct('Papas Rústicas Trufadas');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.selectOptions(within(cart).getByLabelText('Método de pago'), 'Transferencia');
    await userEvent.type(within(cart).getByLabelText('Distancia estimada (km)'), '3.2');
    await userEvent.type(within(cart).getByLabelText('Tiempo estimado (min)'), '20');
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(fetchMock.calls.some((call) => call.url.startsWith('/api/orders'))).toBe(true),
    );

    const orderCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url.startsWith('/api/orders'),
    );

    expect(orderCall?.body).toEqual({
      address: 'Av. Pedro de Valdivia 1234',
      payment_method: 'Transferencia',
      distance_km: 3.2,
      estimated_minutes: 20,
      items: [
        { product_id: 10, quantity: 1 },
        { product_id: 12, quantity: 1 },
      ],
    });
  });

  it('shows the total of the backend after the order is created, not the local subtotal', async () => {
    mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');
    await addProduct('Papas Rústicas Trufadas');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    expect(within(cart).getByText('$11.480')).toBeInTheDocument();

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() => expect(within(cart).getByText('$13.980')).toBeInTheDocument());
    expect(within(cart).queryByText('$11.480')).toBeNull();
  });

  it('shows the code the backend answers with and the way to the orders of the customer', async () => {
    mockFetch(checkoutResponder());
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() => expect(within(cart).getByText('#FD-42')).toBeInTheDocument());
    expect(within(cart).getByRole('link', { name: 'Ver mis pedidos' })).toHaveAttribute(
      'href',
      '/pedidos',
    );
  });

  it('does not invent a code when the detail of the order cannot be read', async () => {
    mockFetch(
      checkoutResponder({
        detail: jsonResponse(
          { status: 403, error: 'FORBIDDEN', message: 'No puedes consultar este pedido' },
          403,
        ),
      }),
    );
    renderApp('/');

    await addProduct('Doble Smash Cheddar Bacon');

    const cart = screen.getByRole('complementary', { name: 'Mi Carrito' });

    await userEvent.type(
      within(cart).getByLabelText('Dirección de entrega'),
      'Av. Pedro de Valdivia 1234',
    );
    await userEvent.click(within(cart).getByRole('button', { name: 'Confirmar pedido' }));

    await waitFor(() =>
      expect(within(cart).getByText('No puedes consultar este pedido')).toBeInTheDocument(),
    );
    expect(within(cart).queryByText('#FD-42')).toBeNull();
    expect(
      within(cart).getByText('Identificador: 00000000-0000-0000-0000-00000000002a'),
    ).toBeInTheDocument();
  });
});
