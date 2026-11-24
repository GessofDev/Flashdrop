import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { ORDER_DETAIL, ORDER_ID, orderDetailResponse, orderListResponse } from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Payment of an order (spec S6).
 *
 * The customer who owns an order has to see how its payment goes and has to
 * be able to open a new attempt when the previous one was rejected. The
 * backend answers with `GET /api/orders/{id}/payments` and
 * `POST /api/orders/{id}/payments`.
 */

/** Answer of `GET /api/orders/{id}/payments`, with the shape of `PaymentView`. */
interface PaymentView {
  /** Label of the status of the order. */
  orderStatus: string;
  /** Name of the `PaymentStatus` of the backend. */
  paymentStatus: string | null;
  /** Id of the last attempt of payment. */
  paymentId: number | null;
  /** Url of the payment session; the backend answers with a fake scheme. */
  paymentUrl: string | null;
  /** Deadline of the payment window, as an ISO-8601 offset date time. */
  paymentExpiresAt: string | null;
}

/** Answer of `POST /api/orders/{id}/payments`, with the shape of `OpenPayment`. */
interface OpenPayment {
  paymentId: number;
  paymentUrl: string;
  paymentExpiresAt: string | null;
}

/** Deadline the backend sent for the rejected attempt, in Chilean time. */
const REJECTED_DEADLINE = '2026-10-28T22:15:00Z';
const REJECTED_DEADLINE_TEXT = '28 oct 2026, 19:15';

/** Deadline of the attempt the retry opens, in Chilean time. */
const OPENED_DEADLINE = '2026-10-28T23:00:00Z';
const OPENED_DEADLINE_TEXT = '28 oct 2026, 20:00';

/** An order waiting for its payment, with a rejected attempt. */
const REJECTED_PAYMENT: PaymentView = {
  orderStatus: 'Pendiente de pago',
  paymentStatus: 'RECHAZADO',
  paymentId: 7,
  paymentUrl: null,
  paymentExpiresAt: REJECTED_DEADLINE,
};

/** The attempt the backend opens when the customer asks for a new one. */
const OPENED_PAYMENT: OpenPayment = {
  paymentId: 9,
  paymentUrl: 'fake://flashdrop-pay/tx-9',
  paymentExpiresAt: OPENED_DEADLINE,
};

/**
 * Answer of the two endpoints of the tracking screen.
 *
 * The detail of the order and its payment live under the same path, so the
 * payment is the one that carries the `/payments` suffix.
 */
function trackingResponse(
  call: RecordedCall,
  options: {
    view: PaymentView;
    opened?: OpenPayment;
    openError?: { status: number; message: string };
  },
): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname.endsWith('/payments')) {
    if (call.method === 'POST') {
      if (options.openError !== undefined) {
        return jsonResponse(
          {
            status: options.openError.status,
            error: 'BAD_REQUEST',
            message: options.openError.message,
          },
          options.openError.status,
        );
      }

      return jsonResponse(
        { success: true, message: 'Pago abierto', data: options.opened ?? OPENED_PAYMENT },
        201,
      );
    }

    return jsonResponse({ success: true, message: 'Pago consultado', data: options.view });
  }

  if (pathname.startsWith('/api/orders/')) {
    return orderDetailResponse({ ...ORDER_DETAIL, status: options.view.orderStatus });
  }

  return orderListResponse();
}

describe('payment of an order', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('asks the backend for the payment of the order when the tracking opens', async () => {
    const fetchMock = mockFetch((call: RecordedCall) =>
      trackingResponse(call, { view: REJECTED_PAYMENT }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    expect(await within(panel).findByText('Pago rechazado')).toBeInTheDocument();
    expect(
      fetchMock.calls.some(
        (call) =>
          call.method === 'GET' && call.url === `/api/orders/${ORDER_ID}/payments`,
      ),
    ).toBe(true);
  });

  it('shows the state of the payment and the deadline the backend answered', async () => {
    mockFetch((call: RecordedCall) => trackingResponse(call, { view: REJECTED_PAYMENT }));
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    expect(await within(panel).findByText('Pago rechazado')).toBeInTheDocument();
    expect(within(panel).getByText(REJECTED_DEADLINE_TEXT)).toBeInTheDocument();
    expect(within(panel).getByText('7')).toBeInTheDocument();
  });

  it.each([
    ['PENDIENTE', 'Pago pendiente'],
    ['APROBADO', 'Pago aprobado'],
    ['RECHAZADO', 'Pago rechazado'],
    ['EXPIRADO', 'Pago expirado'],
  ])('names the state %s of the payment in Spanish', async (status, label) => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        view: { ...REJECTED_PAYMENT, paymentStatus: status },
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    expect(await within(panel).findByText(label)).toBeInTheDocument();
  });

  it('offers the retry while the order waits for its payment and opens a new attempt', async () => {
    const fetchMock = mockFetch((call: RecordedCall) =>
      trackingResponse(call, { view: REJECTED_PAYMENT, opened: OPENED_PAYMENT }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    await userEvent.click(
      await within(panel).findByRole('button', { name: 'Reintentar pago' }),
    );

    await waitFor(() => {
      const open = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === `/api/orders/${ORDER_ID}/payments`,
      );

      expect(open).toBeDefined();
      // The customer always comes from the token: the call carries no user id.
      expect(open?.body ?? null).toBeNull();
    });

    expect(await within(panel).findByText('9')).toBeInTheDocument();
    expect(within(panel).getByText(OPENED_DEADLINE_TEXT)).toBeInTheDocument();
  });

  it('does not offer the retry when the order left the pending payment', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        view: {
          orderStatus: 'Preparando',
          paymentStatus: 'APROBADO',
          paymentId: 7,
          paymentUrl: null,
          paymentExpiresAt: null,
        },
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    await waitFor(() => expect(within(panel).getByText('Pago aprobado')).toBeInTheDocument());

    expect(within(panel).queryByRole('button', { name: 'Reintentar pago' })).toBeNull();
  });

  it('does not offer the retry while a payment is still open', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        view: {
          orderStatus: 'Pendiente de pago',
          paymentStatus: 'PENDIENTE',
          paymentId: 7,
          paymentUrl: 'fake://flashdrop-pay/tx-7',
          paymentExpiresAt: REJECTED_DEADLINE,
        },
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    await waitFor(() => expect(within(panel).getByText('Pago pendiente')).toBeInTheDocument());

    expect(within(panel).queryByRole('button', { name: 'Reintentar pago' })).toBeNull();
  });

  it('shows the literal message of the backend when the retry fails', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        view: REJECTED_PAYMENT,
        openError: { status: 400, message: 'El plazo para pagar este pedido ya vencio' },
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    await userEvent.click(
      await within(panel).findByRole('button', { name: 'Reintentar pago' }),
    );

    expect(await within(panel).findByRole('alert')).toHaveTextContent(
      'El plazo para pagar este pedido ya vencio',
    );
  });

  it('never renders the payment url of the backend as a link', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        view: {
          orderStatus: 'Pendiente de pago',
          paymentStatus: 'PENDIENTE',
          paymentId: 7,
          paymentUrl: 'fake://flashdrop-pay/tx-7',
          paymentExpiresAt: REJECTED_DEADLINE,
        },
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Pago del pedido' });

    await waitFor(() => expect(within(panel).getByText('Pago pendiente')).toBeInTheDocument());

    expect(within(panel).queryByRole('link')).toBeNull();
    expect(document.querySelector('a[href^="fake://"]')).toBeNull();
    expect(document.body.textContent ?? '').not.toContain('fake://');
    expect(document.body.innerHTML).not.toContain('flashdrop-pay');
  });
});
