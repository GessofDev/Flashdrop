import type { PublicReview } from '@/api';
import { formatOrderDate } from '@/lib/format';

import { StarRatingDisplay } from './StarRatingDisplay';

export interface ReviewListProps {
  /** Public ratings of the destination, in the order of the backend. */
  reviews: PublicReview[];
}

/**
 * List of the public ratings of a destination.
 *
 * Every rating shows who wrote it, the score, the comment and the instant.
 * The backend publishes the id of the author and not its name, so the list
 * names the author by that id instead of inventing one.
 *
 * The comment is rendered as plain text: it is never interpreted as markup.
 */
export function ReviewList({ reviews }: ReviewListProps) {
  return (
    <ul aria-label="Reseñas del local" className="flex flex-col gap-4">
      {reviews.map((review) => (
        <li key={review.id}>
          <article
            aria-label={`Reseña de usuario ${review.authorUserId}`}
            className="flex flex-col gap-2 rounded-control bg-canvas p-3"
          >
            <div className="flex flex-wrap items-center gap-x-3 gap-y-1">
              <StarRatingDisplay value={review.rating} />
              <span className="text-sm font-semibold text-foreground">
                Usuario {review.authorUserId}
              </span>
              <time
                dateTime={review.createdAt}
                className="text-xs text-foreground-muted"
              >
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
  );
}
