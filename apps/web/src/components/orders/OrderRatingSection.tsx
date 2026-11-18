import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';

import { ApiError, asApiError, reviewsApi } from '@/api';
import type { CompletedReview, PendingReviewTarget } from '@/api';
import { RATING_TARGET_LABELS } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { StarRatingDisplay, StarRatingInput } from '@/components/ratings';
import { Button, Skeleton } from '@/components/ui';
import { numericOrderId } from '@/lib/orderId';

/** Maximum length of the comment of a rating (spec S5). */
export const MAX_REVIEW_COMMENT_LENGTH = 500;

/** Message shown when the id of the order is not a one the backend can read. */
const UNKNOWN_ORDER_MESSAGE = 'No pudimos identificar el pedido para pedir sus calificaciones.';

/** Key of a destination inside the local state of the section. */
function targetKey(target: { targetType: string; targetId: number }): string {
  return `${target.targetType}:${target.targetId}`;
}

/** What the user chose for one destination. */
interface RatingForm {
  /** Score chosen so far, `null` when nobody chose one yet. */
  rating: number | null;
  /** Comment the user wrote. */
  comment: string;
}

interface SectionState {
  /** Destinations the user can still rate. */
  pending: PendingReviewTarget[];
  /** Ratings the user already wrote. */
  completed: CompletedReview[];
  /** Whether the call is still in flight. */
  loading: boolean;
  /** Failure of the call, with the message of the backend. */
  error: ApiError | null;
}

export interface OrderRatingSectionProps {
  /** Id of the order, as the tracking screen holds it. */
  orderId: string;
}

interface PendingTargetFormProps {
  /** Destination the customer can still rate. */
  target: PendingReviewTarget;
  /** Name the screen shows for the destination. */
  label: string;
  /** What the customer chose for it. */
  form: RatingForm;
  /** Whether the form is being sent. */
  submitting: boolean;
  /** Change the score or the comment of the destination. */
  onChange: (patch: Partial<RatingForm>) => void;
  /** Send the rating of the destination. */
  onSubmit: () => void;
}

/**
 * Form of one destination the customer can still rate.
 *
 * The score goes from one to five stars and the comment is optional, up to
 * 500 characters. The button stays disabled until a score is chosen: the
 * backend rejects a rating without a score, so the form never sends one.
 */
function PendingTargetForm({
  target,
  label,
  form,
  submitting,
  onChange,
  onSubmit,
}: PendingTargetFormProps) {
  const commentId = `review-comment-${target.targetType}-${target.targetId}`;

  return (
    <article
      aria-label={`Calificar ${label}`}
      className="flex flex-col gap-3 rounded-control border border-border bg-canvas p-4"
    >
      <h3 className="font-display text-base font-semibold text-foreground">{label}</h3>

      <StarRatingInput
        value={form.rating}
        label={`Puntaje para ${label}`}
        onChange={(value) => {
          onChange({ rating: value });
        }}
      />

      <div className="flex flex-col gap-1.5">
        <label htmlFor={commentId} className="text-sm font-semibold text-foreground">
          Comentario (opcional)
        </label>
        <textarea
          id={commentId}
          rows={3}
          maxLength={MAX_REVIEW_COMMENT_LENGTH}
          value={form.comment}
          onChange={(event) => {
            onChange({ comment: event.target.value });
          }}
          className="rounded-control border border-border bg-surface p-3 text-sm text-foreground focus-visible:outline-2 focus-visible:outline-primary"
        />
        <span className="nums-tabular text-xs text-foreground-muted">
          {form.comment.length}/{MAX_REVIEW_COMMENT_LENGTH}
        </span>
      </div>

      <div>
        <Button disabled={form.rating === null || submitting} onClick={onSubmit}>
          {submitting ? 'Enviando…' : 'Enviar'}
        </Button>
      </div>
    </article>
  );
}

/**
 * Ratings of a delivered order (spec S3, S5 and S8).
 *
 * The section asks the backend which destinations the customer can still
 * rate and shows one form for each of them: the score goes from one to five
 * stars and the comment is optional, up to 500 characters.
 *
 * The section never decides who can rate whom — that matrix belongs to the
 * server (spec S4) — it shows exactly the destinations the pending endpoint
 * returns, and the ratings already written are shown as they are, without
 * the option of changing them.
 */
export function OrderRatingSection({ orderId }: OrderRatingSectionProps) {
  const numericId = useMemo(() => numericOrderId(orderId), [orderId]);

  const [state, setState] = useState<SectionState>({
    pending: [],
    completed: [],
    loading: true,
    error: null,
  });
  const [forms, setForms] = useState<Record<string, RatingForm>>({});
  const [labels, setLabels] = useState<Record<string, string>>({});
  const [submittingKey, setSubmittingKey] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let active = true;

    if (numericId === null) {
      setState({
        pending: [],
        completed: [],
        loading: false,
        error: new ApiError(UNKNOWN_ORDER_MESSAGE, { status: 404, kind: 'notFound' }),
      });

      return () => {
        active = false;
      };
    }

    setState({ pending: [], completed: [], loading: true, error: null });

    reviewsApi
      .getPendingReviews(numericId)
      .then((answer) => {
        if (!active) {
          return;
        }

        const pendingLabels: Record<string, string> = {};

        for (const target of answer.pending) {
          pendingLabels[targetKey(target)] =
            target.targetLabel ?? RATING_TARGET_LABELS[target.targetType];
        }

        setLabels((current) => ({ ...current, ...pendingLabels }));
        setState({
          pending: answer.pending,
          completed: answer.completed,
          loading: false,
          error: null,
        });
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }

        setState({ pending: [], completed: [], loading: false, error: asApiError(cause) });
      });

    return () => {
      active = false;
    };
  }, [numericId, reloadToken]);

  const updateForm = (key: string, patch: Partial<RatingForm>): void => {
    setForms((current) => ({
      ...current,
      [key]: {
        rating: current[key]?.rating ?? null,
        comment: current[key]?.comment ?? '',
        ...patch,
      },
    }));
  };

  const submit = async (target: PendingReviewTarget): Promise<void> => {
    const key = targetKey(target);
    const form = forms[key];
    const rating = form?.rating ?? null;

    if (numericId === null || rating === null) {
      return;
    }

    const comment = (form?.comment ?? '').trim();

    setSubmittingKey(key);
    setSubmitError(null);
    setNotice(null);

    try {
      await reviewsApi.createReview({
        orderId: numericId,
        targetType: target.targetType,
        rating,
        ...(comment.length > 0 ? { comment } : {}),
      });

      setState((current) => ({
        ...current,
        pending: current.pending.filter((item) => targetKey(item) !== key),
        completed: [
          ...current.completed,
          {
            targetType: target.targetType,
            targetId: target.targetId,
            rating,
            comment: comment.length > 0 ? comment : null,
          },
        ],
      }));

      setNotice('¡Gracias! Guardamos tu opinión.');
    } catch (cause: unknown) {
      setSubmitError(asApiError(cause));
    } finally {
      setSubmittingKey(null);
    }
  };

  let body: ReactNode;

  if (state.loading) {
    body = (
      <div className="mt-4">
        <Skeleton label="Cargando calificaciones" blocks={2} height="h-24" />
      </div>
    );
  } else if (state.error !== null) {
    body = (
      <div className="mt-4">
        <ApiErrorNotice error={state.error} />
      </div>
    );
  } else {
    body = (
      <div className="mt-4 flex flex-col gap-4">
        {state.pending.length === 0 && state.completed.length === 0 ? (
          <p className="text-sm text-foreground-secondary">
            No hay destinos por calificar en este pedido.
          </p>
        ) : null}

        {state.pending.map((target) => {
          const key = targetKey(target);

          return (
            <PendingTargetForm
              key={key}
              target={target}
              label={target.targetLabel ?? RATING_TARGET_LABELS[target.targetType]}
              form={forms[key] ?? { rating: null, comment: '' }}
              submitting={submittingKey === key}
              onChange={(patch) => {
                updateForm(key, patch);
              }}
              onSubmit={() => {
                void submit(target);
              }}
            />
          );
        })}

        {state.completed.length > 0 ? (
          <section aria-labelledby="order-rating-completed-title" className="flex flex-col gap-2">
            <h3
              id="order-rating-completed-title"
              className="font-display text-base font-semibold text-foreground"
            >
              Tus calificaciones
            </h3>
            <ul aria-label="Tus calificaciones" className="flex flex-col gap-3">
              {state.completed.map((review) => {
                const key = targetKey(review);

                return (
                  <li key={key} className="flex flex-col gap-1 rounded-control bg-canvas p-3">
                    <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
                      <span className="text-sm font-semibold text-foreground">
                        {labels[key] ?? RATING_TARGET_LABELS[review.targetType]}
                      </span>
                      <StarRatingDisplay value={review.rating} />
                    </div>
                    {review.comment !== null && review.comment.length > 0 ? (
                      <p className="text-sm text-foreground-secondary">{review.comment}</p>
                    ) : null}
                  </li>
                );
              })}
            </ul>
          </section>
        ) : null}
      </div>
    );
  }

  return (
    <section
      aria-labelledby="order-rating-title"
      className="rounded-container border border-border bg-surface p-5 shadow-card"
    >
      <div className="flex flex-col gap-1">
        <h2 id="order-rating-title" className="font-display text-lg font-bold text-foreground">
          Calificar la entrega
        </h2>
        <p className="text-sm text-foreground-secondary">
          Tu opinión ayuda a los repartidores y a las tiendas de la plataforma.
        </p>
      </div>

      {notice !== null ? (
        <p role="status" className="mt-4 text-sm font-medium text-success">
          {notice}
        </p>
      ) : null}

      {submitError !== null ? (
        <div className="mt-4">
          <ApiErrorNotice
            error={submitError}
            onRetry={() => {
              setReloadToken((token) => token + 1);
            }}
          />
        </div>
      ) : null}

      {body}
    </section>
  );
}
