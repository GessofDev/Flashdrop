import { jsonResponse } from './fetchMock';
import type { RecordedCall } from './fetchMock';

/**
 * Answers of `reviews-service` used by the tests of the ratings screens.
 *
 * The shapes are the ones of the contract of the service: the bare DTO of
 * the pending endpoint, the aggregate with its list of public ratings and
 * the rating the create endpoint answers with.
 */

/** Numeric id of the order of the fixtures: the UUID of the orders ends in `2a`. */
export const REVIEW_ORDER_ID = 42;

/** Two destinations the customer can still rate in that order. */
export const PENDING_REVIEWS = {
  orderId: REVIEW_ORDER_ID,
  orderStatus: 'ENTREGADO',
  pending: [
    { targetType: 'REPARTIDOR', targetId: 7, targetLabel: 'Carlos Morales' },
    { targetType: 'TIENDA', targetId: 1, targetLabel: 'La Maestranza Burgers' },
  ],
  completed: [],
};

/** The same order, with the rating the customer already wrote. */
export const COMPLETED_REVIEWS = {
  orderId: REVIEW_ORDER_ID,
  orderStatus: 'ENTREGADO',
  pending: [],
  completed: [
    { targetType: 'REPARTIDOR', targetId: 7, rating: 5, comment: 'Excelente servicio' },
  ],
};

/**
 * The same pending answer without the labels and without the status of the
 * order: it is the shape the service publishes today.
 */
export const PENDING_REVIEWS_WITHOUT_LABELS = {
  orderId: REVIEW_ORDER_ID,
  pending: [
    { targetType: 'REPARTIDOR', targetId: 7 },
    { targetType: 'TIENDA', targetId: 1 },
  ],
  completed: [],
};

/** Public ratings of a store, with the aggregate behind them. */
export const TARGET_REVIEWS = {
  targetType: 'TIENDA',
  targetId: 1,
  average: 4.6,
  count: 12,
  distribution: { '1': 0, '2': 1, '3': 2, '4': 3, '5': 6 },
  reviews: [
    {
      id: 91,
      authorUserId: 42,
      rating: 5,
      comment: 'Excelente servicio',
      createdAt: '2026-10-28T22:15:00Z',
    },
    {
      id: 92,
      authorUserId: 77,
      rating: 4,
      comment: null,
      createdAt: '2026-10-29T18:05:00Z',
    },
  ],
};

/**
 * The public ratings of a store in the page shape the service publishes: the
 * list under `items` and the aggregate under `aggregate`.
 */
export const TARGET_REVIEWS_PAGE = {
  items: TARGET_REVIEWS.reviews,
  page: 0,
  size: 20,
  totalElements: 2,
  totalPages: 1,
  aggregate: {
    average: 4.6,
    count: 12,
    distribution: { '1': 0, '2': 1, '3': 2, '4': 3, '5': 6 },
  },
};

/** Answer of `POST /api/reviews`: the rating the backend created. */
export const CREATED_REVIEW = {
  id: 101,
  orderId: REVIEW_ORDER_ID,
  targetType: 'REPARTIDOR',
  targetId: 7,
  rating: 4,
  comment: 'Muy buena entrega',
  createdAt: '2026-10-30T12:00:00Z',
};

/** Answers the test can replace one by one. */
export interface ReviewsMockState {
  /** Answer of the pending endpoint. */
  pendingResponse?: () => Response;
  /** Answer of the create endpoint. */
  createResponse?: () => Response;
  /** Answer of the endpoint of the public ratings of a destination. */
  targetResponse?: () => Response;
}

/**
 * Answer the endpoints of `reviews-service`.
 *
 * The paths of the service share the prefix `/api/reviews`, so the pending
 * endpoint is read first and the public one after it.
 */
export function reviewsResponse(call: RecordedCall, state: ReviewsMockState = {}): Response {
  const { pathname } = new URL(call.url, 'http://flashdrop.test');

  if (pathname.startsWith('/api/reviews/pending/')) {
    return state.pendingResponse?.() ?? jsonResponse(PENDING_REVIEWS);
  }

  if (pathname === '/api/reviews' && call.method === 'POST') {
    return state.createResponse?.() ?? jsonResponse(CREATED_REVIEW, 201);
  }

  if (pathname.startsWith('/api/reviews/')) {
    return state.targetResponse?.() ?? jsonResponse(TARGET_REVIEWS);
  }

  if (pathname === '/api/users/me/reviews') {
    return jsonResponse(TARGET_REVIEWS.reviews);
  }

  return jsonResponse(
    { status: 404, error: 'NOT_FOUND', message: 'Recurso no encontrado' },
    404,
  );
}
