import { useEffect, useState } from 'react';

import { asApiError, reviewsApi } from '@/api';
import type { ApiError, PublicReview } from '@/api';
import { ApiErrorNotice } from '@/components/errors/ApiErrorNotice';
import { StarRatingDisplay } from '@/components/ratings';
import { Skeleton } from '@/components/ui';
import { formatOrderDate } from '@/lib/format';

/**
 * Customer review history in the profile (T11, spec S12).
 *
 * Calls `GET /api/users/me/reviews` and renders the ratings written by the
 * authenticated user.
 */
export function MyReviewsSection() {
  const [reviews, setReviews] = useState<PublicReview[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<ApiError | null>(null);

  useEffect(() => {
    let active = true;

    reviewsApi
      .getMyReviews()
      .then((items) => {
        if (active) {
          setReviews(items);
          setError(null);
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (!active) {
          return;
        }
        const apiError = asApiError(cause);
        setError(apiError.status === 404 ? null : apiError);
        setLoading(false);
      });

    return () => {
      active = false;
    };
  }, []);

  return (
    <section
      role="region"
      aria-label="Mis reseñas"
      className="flex flex-col gap-4 rounded-container border border-border bg-surface p-6 shadow-card"
    >
      <div className="flex items-center justify-between gap-3">
        <div>
          <h2 className="font-display text-lg font-bold text-foreground">Mis reseñas</h2>
          <p className="text-xs text-foreground-secondary">
            Historial de reseñas que escribiste en FlashDrop.
          </p>
        </div>
        {!loading && error === null ? (
          <span className="nums-tabular text-sm text-foreground-muted">{reviews.length}</span>
        ) : null}
      </div>

      {loading ? (
        <Skeleton label="Cargando tus reseñas" blocks={1} height="h-24" />
      ) : error !== null ? (
        <ApiErrorNotice error={error} />
      ) : reviews.length === 0 ? (
        <p className="text-sm text-foreground-secondary">Todavía no escribiste reseñas.</p>
      ) : (
        <ul aria-label="Listado de mis reseñas" className="flex flex-col gap-3">
          {reviews.map((review) => (
            <li key={review.id}>
              <article className="flex flex-col gap-2 rounded-control border border-border bg-canvas p-4">
                <div className="flex flex-wrap items-center justify-between gap-2">
                  <StarRatingDisplay value={review.rating} />
                  <time dateTime={review.createdAt} className="text-xs text-foreground-muted">
                    {formatOrderDate(review.createdAt)}
                  </time>
                </div>
                {review.comment !== null && review.comment.length > 0 ? (
                  <p className="text-sm text-foreground-secondary">{review.comment}</p>
                ) : null}
              </article>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
