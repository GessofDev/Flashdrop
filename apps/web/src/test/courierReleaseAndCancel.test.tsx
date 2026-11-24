import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { COURIER_ROUTES, courierMockState, courierResponse } from './support/courierMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Courier release and post-pickup cancellation (T9):
 * - Before pickup (`RETIRAR_PEDIDO`), the courier can release an assigned order
 *   with a reason (`POST /api/orders/{orderId}/release`).
 * - After pickup (`EN_CAMINO`), the courier can cancel/reject an order with a
 *   target status and reason (`POST /api/orders/{orderId}/cancel-after-pickup`).
 */
describe('courier release and post-pickup cancellation', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ roles: ['REPARTIDOR'], activeRole: 'REPARTIDOR' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('releases a pre-pickup route with a reason via POST /api/orders/{orderId}/release', async () => {
    const state = courierMockState();

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (call.method === 'POST' && pathname === '/api/orders/501/release') {
        state.routes = [];
        return jsonResponse({ success: true, message: 'Pedido liberado exitosamente', data: null });
      }

      return courierResponse(call, state);
    });

    renderApp('/repartidor');

    const card = await screen.findByRole('article', { name: /Ruta FD-50/i });
    await userEvent.click(within(card).getByRole('button', { name: 'Liberar pedido' }));

    const reasonInput = await screen.findByLabelText('Motivo de liberación');
    await userEvent.type(reasonInput, 'Pinchazo en la moto antes de retirar');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar liberación' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'POST' && call.url === '/api/orders/501/release',
        ),
      ).toBe(true),
    );

    const releaseCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/orders/501/release',
    );
    expect(releaseCall?.body).toEqual({ reason: 'Pinchazo en la moto antes de retirar' });
    expect(await screen.findByRole('status')).toHaveTextContent('Pedido FD-50 liberado');
  });

  it('cancels a post-pickup route with target status and reason via POST /api/orders/{orderId}/cancel-after-pickup', async () => {
    const state = courierMockState();
    state.routes = [
      {
        ...COURIER_ROUTES[0],
        status: 'EN_CAMINO',
      },
    ];

    const fetchMock = mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (call.method === 'POST' && pathname === '/api/orders/501/cancel-after-pickup') {
        state.routes = [];
        return jsonResponse({
          success: true,
          message: 'Pedido anulado exitosamente tras retiro',
          data: null,
        });
      }

      return courierResponse(call, state);
    });

    renderApp('/repartidor');

    const card = await screen.findByRole('article', { name: /Ruta FD-50/i });
    await userEvent.click(within(card).getByRole('button', { name: 'Anular tras retiro' }));

    await userEvent.selectOptions(screen.getByLabelText('Estado final'), 'Rechazado');
    await userEvent.type(
      screen.getByLabelText('Motivo de anulación'),
      'Cliente no responde en el domicilio',
    );
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar anulación' }));

    await waitFor(() =>
      expect(
        fetchMock.calls.some(
          (call) => call.method === 'POST' && call.url === '/api/orders/501/cancel-after-pickup',
        ),
      ).toBe(true),
    );

    const cancelCall = fetchMock.calls.find(
      (call) => call.method === 'POST' && call.url === '/api/orders/501/cancel-after-pickup',
    );
    expect(cancelCall?.body).toEqual({
      status: 'Rechazado',
      reason: 'Cliente no responde en el domicilio',
    });
    expect(await screen.findByRole('status')).toHaveTextContent('Pedido FD-50 anulado');
  });

  it('shows the backend error message when release fails', async () => {
    const state = courierMockState();

    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (call.method === 'POST' && pathname === '/api/orders/501/release') {
        return jsonResponse(
          { success: false, message: 'No puedes liberar un pedido ya retirado', data: null },
          409,
        );
      }

      return courierResponse(call, state);
    });

    renderApp('/repartidor');

    const card = await screen.findByRole('article', { name: /Ruta FD-50/i });
    await userEvent.click(within(card).getByRole('button', { name: 'Liberar pedido' }));

    await userEvent.type(screen.getByLabelText('Motivo de liberación'), 'No llego');
    await userEvent.click(screen.getByRole('button', { name: 'Confirmar liberación' }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No puedes liberar un pedido ya retirado',
    );
  });
});
