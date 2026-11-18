import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { sessionStore } from '@/session';

import { RESTAURANTS, catalogResponse } from './support/catalogMock';
import { courierMockState, courierResponse } from './support/courierMock';
import { jsonResponse, mockFetch } from './support/fetchMock';
import type { RecordedCall } from './support/fetchMock';
import { ORDER_DETAIL, ORDER_ID, orderDetailResponse, orderListResponse } from './support/ordersMock';
import { renderApp, signIn } from './support/renderApp';
import {
  COMPLETED_REVIEWS,
  PENDING_REVIEWS_WITHOUT_LABELS,
  REVIEW_ORDER_ID,
  TARGET_REVIEWS,
  TARGET_REVIEWS_PAGE,
  reviewsResponse,
} from './support/reviewsMock';
import type { ReviewsMockState } from './support/reviewsMock';

/**
 * Ratings of the customer (spec S3, S5 and S8).
 *
 * The form appears on the tracking of a delivered order and shows the
 * destinations the backend says the customer can still rate. The averages
 * of a store and of a courier appear only when the backend sends them.
 */

/** Answer of the tracking screen of an order, with the answers of the reviews service. */
function trackingResponse(
  call: RecordedCall,
  state: ReviewsMockState = {},
  status = 'Entregado',
): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname.startsWith('/api/reviews')) {
    return reviewsResponse(call, state);
  }

  if (pathname.startsWith('/api/orders/')) {
    return orderDetailResponse({ ...ORDER_DETAIL, status });
  }

  return orderListResponse();
}

describe('ratings of the customer', () => {
  beforeEach(() => {
    sessionStore.reset();
    window.sessionStorage.clear();
    window.localStorage.clear();
    signIn({ activeRole: 'CLIENTE' });
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('offers the destinations the backend says the customer can still rate', async () => {
    mockFetch((call: RecordedCall) => trackingResponse(call));
    renderApp(`/pedidos/${ORDER_ID}`);

    const courierCard = await screen.findByRole('article', { name: 'Calificar Carlos Morales' });
    const section = screen.getByRole('region', { name: 'Calificar la entrega' });

    expect(
      within(section).getByRole('article', { name: 'Calificar La Maestranza Burgers' }),
    ).toBeInTheDocument();

    expect(
      within(courierCard).getByRole('button', { name: 'Calificar con 5 estrellas' }),
    ).toBeInTheDocument();
    expect(
      within(courierCard).getByRole('button', { name: 'Calificar con 1 estrella' }),
    ).toBeInTheDocument();
    expect(within(courierCard).getByLabelText('Comentario (opcional)')).toBeInTheDocument();
    expect(within(courierCard).getByText('0/500')).toBeInTheDocument();
  });

  it('does not offer the rating form while the order is not delivered', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => trackingResponse(call, {}, 'Retirado'));
    renderApp(`/pedidos/${ORDER_ID}`);

    await screen.findByRole('region', { name: 'Resumen del pedido' });

    expect(screen.queryByRole('region', { name: 'Calificar la entrega' })).toBeNull();
    expect(
      fetchMock.calls.some((call) => call.url.startsWith('/api/reviews')),
    ).toBe(false);
  });

  it('sends the score and the comment of the chosen destination', async () => {
    const fetchMock = mockFetch((call: RecordedCall) => trackingResponse(call));
    renderApp(`/pedidos/${ORDER_ID}`);

    const courierCard = await screen.findByRole('article', { name: 'Calificar Carlos Morales' });

    await userEvent.click(
      within(courierCard).getByRole('button', { name: 'Calificar con 4 estrellas' }),
    );
    await userEvent.type(
      within(courierCard).getByLabelText('Comentario (opcional)'),
      'Muy buena entrega',
    );
    await userEvent.click(within(courierCard).getByRole('button', { name: 'Enviar' }));

    await waitFor(() => {
      const created = fetchMock.calls.find(
        (call) => call.method === 'POST' && call.url === '/api/reviews',
      );

      expect(created?.body).toEqual({
        orderId: REVIEW_ORDER_ID,
        targetType: 'REPARTIDOR',
        rating: 4,
        comment: 'Muy buena entrega',
      });
    });

    expect(await screen.findByText('¡Gracias! Guardamos tu opinión.')).toBeInTheDocument();

    const completed = await screen.findByRole('list', { name: 'Tus calificaciones' });

    expect(within(completed).getByText('Carlos Morales')).toBeInTheDocument();
    expect(within(completed).getByText('Muy buena entrega')).toBeInTheDocument();
    expect(within(completed).getByRole('img', { name: '4 de 5 estrellas' })).toBeInTheDocument();
  });

  it('shows the rating already written without the option of changing it', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, { pendingResponse: () => jsonResponse(COMPLETED_REVIEWS) }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    await screen.findByRole('list', { name: 'Tus calificaciones' });

    const section = screen.getByRole('region', { name: 'Calificar la entrega' });

    expect(within(section).getByText('Excelente servicio')).toBeInTheDocument();
    expect(
      within(section).getByRole('img', { name: '5 de 5 estrellas' }),
    ).toBeInTheDocument();
    expect(within(section).queryByRole('button', { name: 'Enviar' })).toBeNull();
  });

  it('shows the literal message of the backend when the pending call fails', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        pendingResponse: () =>
          jsonResponse(
            { status: 403, error: 'FORBIDDEN', message: 'No puedes calificar este pedido' },
            403,
          ),
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    expect(await screen.findByRole('alert')).toHaveTextContent(
      'No puedes calificar este pedido',
    );
  });

  it('shows the average of a store when the catalog sends it', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname === '/catalog/restaurants') {
        return jsonResponse([{ ...RESTAURANTS[0], ratingAverage: 4.6, ratingCount: 12 }]);
      }

      return catalogResponse(call);
    });
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );

    expect(within(restaurants).getByText('4,6')).toBeInTheDocument();
    expect(within(restaurants).getByText('(12 valoraciones)')).toBeInTheDocument();
  });

  it('does not show an average when the catalog does not send it', async () => {
    mockFetch(catalogResponse);
    renderApp('/');

    const restaurants = await screen.findByRole('region', { name: 'Restaurantes' });

    await waitFor(() =>
      expect(within(restaurants).getByText('La Maestranza Burgers')).toBeInTheDocument(),
    );

    expect(within(restaurants).queryByText(/valoraciones/)).toBeNull();
    expect(within(restaurants).queryByText(/\d,\d/)).toBeNull();
  });

  it('shows the public reviews of a store', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname.startsWith('/api/reviews')) {
        return reviewsResponse(call);
      }

      return catalogResponse(call);
    });
    renderApp('/restaurante/1');

    const list = await screen.findByRole('list', { name: 'Reseñas del local' });

    expect(within(list).getByText('Usuario 42')).toBeInTheDocument();
    expect(within(list).getByText('Excelente servicio')).toBeInTheDocument();
    expect(within(list).getByText('28 oct 2026, 19:15')).toBeInTheDocument();
    expect(within(list).getByRole('img', { name: '5 de 5 estrellas' })).toBeInTheDocument();
    expect(within(list).getByRole('img', { name: '4 de 5 estrellas' })).toBeInTheDocument();
  });

  it('names the destination by its kind when the backend sends no label', async () => {
    mockFetch((call: RecordedCall) =>
      trackingResponse(call, {
        pendingResponse: () => jsonResponse(PENDING_REVIEWS_WITHOUT_LABELS),
      }),
    );
    renderApp(`/pedidos/${ORDER_ID}`);

    const section = await screen.findByRole('region', { name: 'Calificar la entrega' });

    expect(
      await screen.findByRole('article', { name: 'Calificar El repartidor' }),
    ).toBeInTheDocument();
    expect(within(section).getByRole('article', { name: 'Calificar La tienda' })).toBeInTheDocument();
  });

  it('shows the average and the list when the service answers with the aggregate of its page', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname.startsWith('/api/reviews')) {
        return jsonResponse(TARGET_REVIEWS_PAGE);
      }

      return catalogResponse(call);
    });
    renderApp('/restaurante/1');

    const list = await screen.findByRole('list', { name: 'Reseñas del local' });

    expect(within(list).getByText('Excelente servicio')).toBeInTheDocument();
    expect(screen.getByText('4,6')).toBeInTheDocument();
    expect(screen.getByText('(12 valoraciones)')).toBeInTheDocument();
  });

  it('says when a store has no reviews yet', async () => {
    mockFetch((call: RecordedCall) => {
      const { pathname } = new URL(call.url, 'http://flashdrop.test');

      if (pathname.startsWith('/api/reviews')) {
        return jsonResponse({ ...TARGET_REVIEWS, average: null, count: 0, reviews: [] });
      }

      return catalogResponse(call);
    });
    renderApp('/restaurante/1');

    expect(await screen.findByText('Este local todavía no tiene reseñas')).toBeInTheDocument();
  });

  it('shows the average of the courier when the delivery service sends it', async () => {
    signIn({ activeRole: 'REPARTIDOR' });

    const state = courierMockState();

    state.claimResponse = () =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'El usuario no tiene perfil de repartidor',
        },
        403,
      );

    state.profileResponse = () =>
      jsonResponse(
        {
          success: true,
          message: 'Delivery person profile created',
          data: {
            id: 7,
            userId: '42',
            vehicle: 'MOTO',
            createdAt: '2026-10-28T22:00:00Z',
            ratingAverage: 4.8,
            ratingCount: 25,
          },
        },
        201,
      );

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tomar 1 pedido' }));

    await screen.findByRole('button', { name: 'Crear perfil de repartidor' });
    await userEvent.click(screen.getByRole('button', { name: 'Crear perfil de repartidor' }));

    expect(await screen.findByText('Valoración del repartidor')).toBeInTheDocument();
    expect(screen.getByText('4,8')).toBeInTheDocument();
    expect(screen.getByText('(25 valoraciones)')).toBeInTheDocument();
  });

  it('does not show an average of the courier when the delivery service does not send it', async () => {
    signIn({ activeRole: 'REPARTIDOR' });

    const state = courierMockState();

    state.claimResponse = () =>
      jsonResponse(
        {
          status: 403,
          error: 'FORBIDDEN',
          message: 'El usuario no tiene perfil de repartidor',
        },
        403,
      );

    mockFetch((call: RecordedCall) => courierResponse(call, state));
    renderApp('/repartidor/pedidos');

    await screen.findByRole('list', { name: 'Pedidos disponibles' });
    await userEvent.click(screen.getByRole('checkbox', { name: 'Seleccionar pedido FD-50' }));
    await userEvent.click(screen.getByRole('button', { name: 'Tomar 1 pedido' }));

    await screen.findByRole('button', { name: 'Crear perfil de repartidor' });
    await userEvent.click(screen.getByRole('button', { name: 'Crear perfil de repartidor' }));

    expect(await screen.findByText('Perfil de repartidor creado. La cola fue actualizada.')).toBeInTheDocument();
    expect(screen.queryByText('Valoración del repartidor')).toBeNull();
  });
});
