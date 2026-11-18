import { ApiError } from './ApiError';
import { request } from './client';
import { isRecord } from './errorBody';

/**
 * Destinations of a rating (spec S4).
 *
 * The three values are the ones of the `RatingTarget` enum of
 * `reviews-service`: the store, the courier and the customer of the order.
 * The client never decides who can rate whom — that matrix belongs to the
 * server — it only reads the targets the pending endpoint returns.
 */
export const RATING_TARGET_TYPES = ['TIENDA', 'REPARTIDOR', 'CLIENTE'] as const;

export type RatingTargetType = (typeof RATING_TARGET_TYPES)[number];

/** Narrow an unknown value to a {@link RatingTargetType}. */
export function isRatingTargetType(value: unknown): value is RatingTargetType {
  return typeof value === 'string' && (RATING_TARGET_TYPES as readonly string[]).includes(value);
}

/**
 * Body of `POST /api/reviews`.
 *
 * The id of the destination is **not** part of it: the server derives it
 * from the order (spec S7), so the client sends only the order, the type of
 * the destination, the score and the optional comment.
 */
export interface CreateReviewRequest {
  /** Numeric id of the order the rating belongs to. */
  orderId: number;
  /** Destination of the rating. */
  targetType: RatingTargetType;
  /** Score, from 1 to 5 stars. */
  rating: number;
  /** Optional comment, up to 500 characters. */
  comment?: string;
}

/** A destination the signed in user can still rate. */
export interface PendingReviewTarget {
  /** Kind of the destination. */
  targetType: RatingTargetType;
  /** Numeric id of the destination. */
  targetId: number;
  /**
   * Name the backend sends to show the destination.
   *
   * It is `null` when the answer carries no name for it: the screen then
   * falls back to the name of the kind of destination.
   */
  targetLabel: string | null;
}

/**
 * Name of every kind of destination.
 *
 * It is the fallback the screen uses when the backend does not send a name
 * for the destination: the kind of destination is part of the contract, its
 * name is not.
 */
export const RATING_TARGET_LABELS: Readonly<Record<RatingTargetType, string>> = {
  TIENDA: 'La tienda',
  REPARTIDOR: 'El repartidor',
  CLIENTE: 'El cliente',
};

/** A rating the signed in user already wrote. */
export interface CompletedReview {
  /** Kind of the destination. */
  targetType: RatingTargetType;
  /** Numeric id of the destination. */
  targetId: number;
  /** Score the user gave. */
  rating: number;
  /** Comment the user wrote, when there is one. */
  comment: string | null;
}

/** Answer of `GET /api/reviews/pending/{orderId}`. */
export interface PendingReviewsResponse {
  /** Numeric id of the order. */
  orderId: number;
  /**
   * Status of the order, as the backend spells it.
   *
   * It is `null` when the answer does not carry it.
   */
  orderStatus: string | null;
  /** Destinations the user can still rate. */
  pending: PendingReviewTarget[];
  /** Ratings the user already wrote. */
  completed: CompletedReview[];
}

/** A public rating of a destination. */
export interface PublicReview {
  /** Id of the rating. */
  id: number;
  /** Id of the user that wrote it. */
  authorUserId: number;
  /** Score, from 1 to 5 stars. */
  rating: number;
  /** Comment the user wrote, when there is one. */
  comment: string | null;
  /** Instant the rating was written, as an ISO-8601 offset date time. */
  createdAt: string;
}

/**
 * Count of each score of a destination.
 *
 * The contract does not pin the shape of the distribution, so the reader
 * accepts a map keyed by the score or a list of five numbers and always
 * answers with a map keyed by the score.
 */
export type RatingDistribution = Readonly<Record<string, number>>;

/** Answer of `GET /api/reviews/{targetType}/{targetId}`. */
export interface TargetReviewsResponse {
  /** Kind of the destination. */
  targetType: RatingTargetType;
  /** Numeric id of the destination. */
  targetId: number;
  /** Average of the scores, when the destination has any. */
  average: number | null;
  /** Number of scores behind the average. */
  count: number;
  /** Count of each score. */
  distribution: RatingDistribution;
  /** The public ratings of the destination. */
  reviews: PublicReview[];
}

/** Message of a failed read of the answers of `reviews-service`. */
const UNEXPECTED_SHAPE_MESSAGE = 'El backend devolvió una respuesta con un formato inesperado.';

/** Failure of a read that does not match the contract. */
function unexpectedShape(body?: unknown): ApiError {
  return new ApiError(UNEXPECTED_SHAPE_MESSAGE, { status: 200, kind: 'unknown', body });
}

/**
 * Payload of an answer of `reviews-service`, once the envelope is removed.
 *
 * The contract of the service documents the bare DTO, while the rest of the
 * backend answers with the `{ success, message, data }` envelope. The reader
 * accepts both and never invents a third shape: an answer that is neither is
 * a failed call, not a DTO with empty fields.
 */
type ReviewsPayload = Record<string, unknown> | unknown[];

/**
 * Read the payload of an answer of `reviews-service`.
 *
 * @throws ApiError when the answer is neither the bare DTO nor the envelope.
 */
function readPayload(answer: unknown): ReviewsPayload {
  const payload =
    isRecord(answer) && typeof answer.success === 'boolean' ? (answer.data ?? null) : answer;

  if (isRecord(payload) || Array.isArray(payload)) {
    return payload;
  }

  throw unexpectedShape(answer);
}

/** Read a numeric field, or `null` when the answer does not carry it. */
function readNumber(value: unknown): number | null {
  return typeof value === 'number' && Number.isFinite(value) ? value : null;
}

/** Read a text field, or `null` when the answer does not carry it. */
function readText(value: unknown): string | null {
  return typeof value === 'string' ? value : null;
}

/** Read a destination the user can still rate. */
function readPendingTarget(value: unknown): PendingReviewTarget | null {
  if (!isRecord(value) || !isRatingTargetType(value.targetType)) {
    return null;
  }

  const targetId = readNumber(value.targetId);
  const targetLabel = readText(value.targetLabel);

  if (targetId === null) {
    return null;
  }

  return {
    targetType: value.targetType,
    targetId,
    targetLabel: targetLabel ?? RATING_TARGET_LABELS[value.targetType],
  };
}

/**
 * Read the score of a rating.
 *
 * The service sends the score as a number, and it may also send it wrapped
 * in its own value object; the reader accepts both and never guesses a score
 * it did not read.
 */
function readRating(value: unknown): number | null {
  const direct = readNumber(value);

  if (direct !== null) {
    return direct;
  }

  if (!isRecord(value)) {
    return null;
  }

  for (const key of ['value', 'stars', 'rating'] as const) {
    const wrapped = readNumber(value[key]);

    if (wrapped !== null) {
      return wrapped;
    }
  }

  return null;
}

/** Read a rating the user already wrote. */
function readCompletedReview(value: unknown): CompletedReview | null {
  if (!isRecord(value) || !isRatingTargetType(value.targetType)) {
    return null;
  }

  const targetId = readNumber(value.targetId);
  const rating = readRating(value.rating);

  if (targetId === null || rating === null) {
    return null;
  }

  return {
    targetType: value.targetType,
    targetId,
    rating,
    comment: readText(value.comment),
  };
}

/** Read the pending and completed ratings of one order. */
function readPendingReviews(answer: unknown): PendingReviewsResponse {
  const payload = readPayload(answer);

  if (!isRecord(payload)) {
    throw unexpectedShape(answer);
  }

  const orderId = readNumber(payload.orderId);
  const orderStatus = readText(payload.orderStatus);

  if (orderId === null) {
    throw unexpectedShape(answer);
  }

  if (!Array.isArray(payload.pending) || !Array.isArray(payload.completed)) {
    throw unexpectedShape(answer);
  }

  return {
    orderId,
    orderStatus,
    pending: payload.pending
      .map(readPendingTarget)
      .filter((target): target is PendingReviewTarget => target !== null),
    completed: payload.completed
      .map(readCompletedReview)
      .filter((review): review is CompletedReview => review !== null),
  };
}

/** Read a public rating of a destination. */
function readPublicReview(value: unknown): PublicReview | null {
  if (!isRecord(value)) {
    return null;
  }

  const id = readNumber(value.id);
  const authorUserId = readNumber(value.authorUserId);
  const rating = readRating(value.rating);
  const createdAt = readText(value.createdAt);

  if (id === null || authorUserId === null || rating === null || createdAt === null) {
    return null;
  }

  return {
    id,
    authorUserId,
    rating,
    comment: readText(value.comment),
    createdAt,
  };
}

/** Read the count of each score, whatever shape the backend used. */
function readDistribution(value: unknown): RatingDistribution {
  if (Array.isArray(value)) {
    const counts: Record<string, number> = {};

    value.forEach((entry, index) => {
      const count = readNumber(entry);

      if (count !== null) {
        counts[String(index + 1)] = count;
      }
    });

    return counts;
  }

  if (!isRecord(value)) {
    return {};
  }

  const counts: Record<string, number> = {};

  for (const [score, entry] of Object.entries(value)) {
    const count = readNumber(entry);

    if (count !== null) {
      counts[score] = count;
    }
  }

  return counts;
}

/**
 * Read the public ratings of one destination and their aggregate.
 *
 * The answer carries the list of ratings under `reviews` or under `items`,
 * and the aggregate either at the root of the answer or under `aggregate`.
 * The kind and the id of the destination may not travel: the reader falls
 * back to the ones the client asked for.
 */
function readTargetReviews(
  answer: unknown,
  destination: { targetType: RatingTargetType; targetId: number },
): TargetReviewsResponse {
  const payload = readPayload(answer);

  if (!isRecord(payload)) {
    throw unexpectedShape(answer);
  }

  const items = Array.isArray(payload.reviews)
    ? payload.reviews
    : Array.isArray(payload.items)
      ? payload.items
      : null;

  if (items === null) {
    throw unexpectedShape(answer);
  }

  const aggregate = isRecord(payload.aggregate) ? payload.aggregate : payload;
  const targetType = isRatingTargetType(payload.targetType)
    ? payload.targetType
    : destination.targetType;
  const targetId = readNumber(payload.targetId) ?? destination.targetId;
  const count = readNumber(aggregate.count) ?? items.length;

  return {
    targetType,
    targetId,
    average: readNumber(aggregate.average),
    count,
    distribution: readDistribution(aggregate.distribution),
    reviews: items
      .map(readPublicReview)
      .filter((review): review is PublicReview => review !== null),
  };
}

/**
 * Read the history of the ratings of the signed in user.
 *
 * The answer is a list or a page that carries the list under `items`.
 */
function readMyReviews(answer: unknown): PublicReview[] {
  const payload = readPayload(answer);
  const items = Array.isArray(payload)
    ? payload
    : isRecord(payload) && Array.isArray(payload.items)
      ? payload.items
      : null;

  if (items === null) {
    throw unexpectedShape(answer);
  }

  return items
    .map(readPublicReview)
    .filter((review): review is PublicReview => review !== null);
}

/**
 * Calls of `reviews-service`.
 *
 * The service answers with the bare DTO of the contract, so every call
 * declares `responseShape: 'bare'` and the readers above accept the
 * `{ success, message, data }` envelope the rest of the backend uses.
 */
export const reviewsApi = {
  /**
   * `POST /api/reviews` — writes the rating of one destination of an order.
   *
   * The id of the destination is derived by the server from the order, so
   * the body carries only the order, the type of the destination, the score
   * and the optional comment.
   */
  createReview(review: CreateReviewRequest): Promise<void> {
    return request<unknown>({
      path: '/api/reviews',
      method: 'POST',
      body: review,
      responseShape: 'bare',
    }).then(() => undefined);
  },

  /**
   * `GET /api/reviews/pending/{orderId}` — the destinations the user can
   * still rate in that order, and the ones it already rated.
   */
  getPendingReviews(orderId: number): Promise<PendingReviewsResponse> {
    return request<unknown>({
      path: `/api/reviews/pending/${orderId}`,
      method: 'GET',
      responseShape: 'bare',
    }).then(readPendingReviews);
  },

  /**
   * `GET /api/reviews/{targetType}/{targetId}` — the public ratings of a
   * destination and the aggregate behind them.
   */
  getTargetReviews(
    targetType: RatingTargetType,
    targetId: number,
  ): Promise<TargetReviewsResponse> {
    return request<unknown>({
      path: `/api/reviews/${targetType}/${targetId}`,
      method: 'GET',
      responseShape: 'bare',
    }).then((answer) => readTargetReviews(answer, { targetType, targetId }));
  },

  /**
   * `GET /api/users/me/reviews` — the ratings the signed in user wrote.
   *
   * The endpoint is part of the contract of the service; the app has no
   * screen for it yet, so the call is here for the surface that needs it.
   */
  getMyReviews(): Promise<PublicReview[]> {
    return request<unknown>({
      path: '/api/users/me/reviews',
      method: 'GET',
      responseShape: 'bare',
    }).then(readMyReviews);
  },
};
