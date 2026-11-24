import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { courierMockState, courierResponse } from './support/courierMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Courier delivery confirmation (spec S2, S3 and S4).
 *
 * `orders-service` only reaches ENTREGADO through
 * `POST /api/orders/{id}/deliver`: `PUT /api/orders/{id}/status` is rejected
 * in secure mode. The courier console therefore asks for the six digit code
 * the customer dictates, and sends the public code of the normal mode.
 */
describe('courier delivery confirmation', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'REPARTIDOR' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  function routeOnTheWay(receptionMode: string): Record<string, unknown> {
    return {
      id: 901,
      orderId: 501,
      pickupAddress: 'La Maestranza Burgers, Av. Providencia 1234, Providencia',
      deliveryAddress: 'El Bosque Norte 230, Las Condes',
      distanceKm: 2.2,
      estimatedMinutes: 24,
      status: 'EN_CAMINO',
      createdAt: '2026-10-28T22:15:00Z',
      code: 'FD-50',
      receptionMode,
    };
  }

  it('confirms a normal order with the public code without asking the courier', async () => {
    const state = courierMockState();
    state.routes = [routeOnTheWay('NORMAL')];

    const fetchMock = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar entrega' }));

    await waitFor(() => {
      const delivery = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === '/api/orders/501/deliver',
      );

      expect(delivery?.body).toEqual({ code: '000000' });
    });

    expect(
      fetchMock.calls.some(
        (call) => call.method === 'PUT' && call.url === '/api/orders/501/status',
      ),
    ).toBe(false);
  });

  it('asks the courier for the code of a secure order and sends it', async () => {
    const state = courierMockState();
    state.routes = [routeOnTheWay('SEGURO')];

    const fetchMock = mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar entrega' }));

    const input = await screen.findByLabelText('Código de entrega');

    await userEvent.type(input, '123456');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar entrega con código' }));

    await waitFor(() => {
      const delivery = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === '/api/orders/501/deliver',
      );

      expect(delivery?.body).toEqual({ code: '123456' });
    });

    expect(
      fetchMock.calls.some(
        (call) => call.method === 'PUT' && call.url === '/api/orders/501/status',
      ),
    ).toBe(false);
  });

  it('shows the literal message of the backend when the code is wrong', async () => {
    const state = courierMockState();
    state.routes = [routeOnTheWay('SEGURO')];
    state.deliverResponse = () =>
      jsonResponse(
        {
          status: 422,
          error: 'VALIDATION_ERROR',
          message: 'Codigo incorrecto. Quedan 2 intentos.',
          remainingAttempts: 2,
          codeRenewed: false,
        },
        422,
      );

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor');

    await userEvent.click(await screen.findByRole('button', { name: 'Confirmar entrega' }));

    const input = await screen.findByLabelText('Código de entrega');

    await userEvent.type(input, '000000');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar entrega con código' }));

    expect(await screen.findByText('Codigo incorrecto. Quedan 2 intentos.')).toBeInTheDocument();
  });
});
