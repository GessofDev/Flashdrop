import { screen, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { mockFetch } from './support/fetchMock';
import type { RecordedCall, Responder } from './support/fetchMock';
import {
  ORDER_DETAIL,
  ORDER_ID,
  ORDER_LIST,
  orderDetailResponse,
  orderListResponse,
  orderPaymentResponse,
} from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';

/**
 * Reception and cancellation of an order (spec S8).
 *
 * The backend already sends the reception mode of the order, the code the
 * customer dictates to the courier and the reason of an automatic
 * cancellation, both in the detail and in the listing. The web shows what
 * the answer carries and nothing else: a field the backend does not send is
 * not shown, and the app never writes a code, a reason or a date of its own.
 */

/** Code of a secure order, as the backend sends it. */
const DELIVERY_CODE = '482913';

/** Reason code of an automatic cancellation, as the backend sends it. */
const CANCEL_REASON = 'NO_COURIER';

/** Text of the cancellation, ready to show. */
const CANCEL_REASON_MESSAGE = 'No se encontró un repartidor a tiempo';

/** Instant of the cancellation, as the backend sends it. */
const CANCELLED_AT = '2026-11-02T18:30:00Z';

/** The same instant, in the Chilean time zone the app shows. */
const CANCELLED_AT_TEXT = '02 nov 2026, 15:30';

/**
 * Answer of the endpoints of the orders screens.
 *
 * The detail of the order and its payment live under the same path, so the
 * payment is the one that carries the `/payments` suffix.
 */
function ordersResponseWith(detail: unknown, list: unknown = ORDER_LIST): Responder {
  return (call: RecordedCall) => {
    const { pathname } = new URL(call.url, 'http://flashdrop.test');

    if (pathname.endsWith('/payments')) {
      return orderPaymentResponse();
    }

    if (pathname.startsWith('/api/orders/')) {
      return orderDetailResponse(detail);
    }

    return orderListResponse(list);
  };
}

describe('reception and cancellation of an order', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('shows the reception mode the backend sent', async () => {
    mockFetch(ordersResponseWith({ ...ORDER_DETAIL, receptionMode: 'SEGURO' }));
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).getByText('Entrega segura')).toBeInTheDocument();
  });

  it('names the normal reception mode in Spanish', async () => {
    mockFetch(ordersResponseWith({ ...ORDER_DETAIL, receptionMode: 'NORMAL' }));
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).getByText('Entrega normal')).toBeInTheDocument();
  });

  it('shows the verification code of a secure order and how to use it', async () => {
    mockFetch(
      ordersResponseWith({
        ...ORDER_DETAIL,
        receptionMode: 'SEGURO',
        deliveryCode: DELIVERY_CODE,
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).getByText(DELIVERY_CODE)).toBeInTheDocument();
    expect(within(panel).getByText(/dictas al repartidor/i)).toBeInTheDocument();
  });

  it('shows no code when the backend sends none in the normal mode', async () => {
    mockFetch(ordersResponseWith({ ...ORDER_DETAIL, receptionMode: 'NORMAL' }));
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).queryByText(DELIVERY_CODE)).toBeNull();
    expect(within(panel).queryByText('Código de verificación')).toBeNull();
  });

  it('shows no code of a delivered order, because the backend sends none', async () => {
    mockFetch(
      ordersResponseWith({
        ...ORDER_DETAIL,
        status: 'Entregado',
        receptionMode: 'SEGURO',
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).getByText('Entrega segura')).toBeInTheDocument();
    expect(within(panel).queryByText(DELIVERY_CODE)).toBeNull();
    expect(within(panel).queryByText('Código de verificación')).toBeNull();
  });

  it('shows the reason and the date of the cancellation', async () => {
    mockFetch(
      ordersResponseWith({
        ...ORDER_DETAIL,
        status: 'Cancelado',
        receptionMode: 'SEGURO',
        cancelReason: CANCEL_REASON,
        cancelReasonMessage: CANCEL_REASON_MESSAGE,
        cancelledAt: CANCELLED_AT,
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const panel = await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(within(panel).getByText(CANCEL_REASON_MESSAGE)).toBeInTheDocument();
    expect(within(panel).getByText(new RegExp(CANCELLED_AT_TEXT))).toBeInTheDocument();
  });

  it('shows the reason of the cancellation of a cancelled order in the listing', async () => {
    const cancelled = {
      ...(ORDER_LIST[0] as (typeof ORDER_LIST)[number]),
      status: 'Cancelado',
      receptionMode: 'SEGURO',
      cancelReason: CANCEL_REASON,
      cancelReasonMessage: CANCEL_REASON_MESSAGE,
      cancelledAt: CANCELLED_AT,
    };

    mockFetch(() => orderListResponse([cancelled, ORDER_LIST[1]]));
    renderApp('/pedidos');

    const list = await screen.findByRole('list', { name: 'Pedidos del cliente' });

    expect(within(list).getByText(CANCEL_REASON_MESSAGE)).toBeInTheDocument();
  });

  it('never invents a code when the backend sends none', async () => {
    mockFetch(ordersResponseWith({ ...ORDER_DETAIL, receptionMode: 'NORMAL' }));
    renderApp(`/pedidos/${ORDER_ID}`);

    await screen.findByRole('region', { name: 'Recepción del pedido' });

    expect(screen.queryByText(DELIVERY_CODE)).toBeNull();
    expect(document.body.textContent ?? '').not.toMatch(/\b\d{6}\b/);
  });

  it('shows no reception block when the backend sends no reception data', async () => {
    mockFetch(ordersResponseWith({ ...ORDER_DETAIL }));
    renderApp(`/pedidos/${ORDER_ID}`);

    await screen.findByRole('region', { name: 'Resumen del pedido' });

    expect(screen.queryByRole('region', { name: 'Recepción del pedido' })).toBeNull();
  });
});
