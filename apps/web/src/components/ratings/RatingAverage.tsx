import { formatRatingAverage } from '@/lib/format';

import { StarRatingDisplay } from './StarRatingDisplay';

export interface RatingAverageProps {
  /** Average of the scores, when the backend sends it. */
  average: number | null;
  /** Number of scores behind the average. */
  count: number | null;
  /** Whether the stars of the average are shown next to the number. */
  showStars?: boolean;
}

/**
 * Average of the scores of a destination.
 *
 * The component shows nothing when the backend does not send the data: the
 * average of a destination that has no score does not exist yet, and the
 * screen must not invent one.
 */
export function RatingAverage({ average, count, showStars = false }: RatingAverageProps) {
  if (average === null || count === null || count <= 0) {
    return null;
  }

  return (
    <span className="inline-flex items-center gap-1.5 text-sm text-foreground-secondary">
      {showStars ? <StarRatingDisplay value={average} size={14} /> : null}
      <span className="nums-tabular font-semibold text-foreground">
        {formatRatingAverage(average)}
      </span>
      <span className="nums-tabular">
        ({count} {count === 1 ? 'valoración' : 'valoraciones'})
      </span>
    </span>
  );
}
